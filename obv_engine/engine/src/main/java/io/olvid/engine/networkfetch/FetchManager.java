/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
 *  
 *  This file is part of Olvid for Android.
 *  
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *  
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.engine.networkfetch;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.AttachmentKeyAndMetadata;
import io.olvid.engine.datatypes.containers.DecryptedApplicationMessage;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.datatypes.containers.ReceivedAttachment;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NetworkFetchDelegate;
import io.olvid.engine.metamanager.ObvManager;
import io.olvid.engine.metamanager.ProcessDownloadedMessageDelegate;
import io.olvid.engine.metamanager.PushNotificationDelegate;
import io.olvid.engine.metamanager.SolveChallengeDelegate;
import io.olvid.engine.networkfetch.coordinators.CreateServerSessionCoordinator;
import io.olvid.engine.networkfetch.coordinators.DeleteMessageAndAttachmentsCoordinator;
import io.olvid.engine.networkfetch.coordinators.DownloadAttachmentCoordinator;
import io.olvid.engine.networkfetch.coordinators.DownloadMessageExtendedPayloadCoordinator;
import io.olvid.engine.networkfetch.coordinators.DownloadMessagesAndListAttachmentsCoordinator;
import io.olvid.engine.networkfetch.coordinators.FreeTrialCoordinator;
import io.olvid.engine.networkfetch.coordinators.GetTurnCredentialsCoordinator;
import io.olvid.engine.networkfetch.coordinators.RefreshInboxAttachmentSignedUrlCoordinator;
import io.olvid.engine.networkfetch.coordinators.RegisterServerPushNotificationsCoordinator;
import io.olvid.engine.networkfetch.coordinators.ServerQueryCoordinator;
import io.olvid.engine.networkfetch.coordinators.ServerUserDataCoordinator;
import io.olvid.engine.networkfetch.coordinators.VerifyReceiptCoordinator;
import io.olvid.engine.networkfetch.coordinators.WebsocketCoordinator;
import io.olvid.engine.networkfetch.coordinators.WellKnownCoordinator;
import io.olvid.engine.networkfetch.databases.CachedWellKnown;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.databases.PendingDeleteFromServer;
import io.olvid.engine.networkfetch.databases.PendingServerQuery;
import io.olvid.engine.networkfetch.databases.PushNotificationConfiguration;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class FetchManager implements FetchManagerSessionFactory, NetworkFetchDelegate, PushNotificationDelegate, ObvManager {
    private final String engineBaseDirectory;
    private final PRNGService prng;
    private final CreateServerSessionCoordinator createServerSessionCoordinator;
    private final RefreshInboxAttachmentSignedUrlCoordinator refreshInboxAttachmentSignedUrlCoordinator;
    private final DownloadAttachmentCoordinator downloadAttachmentCoordinator;
    private final DownloadMessagesAndListAttachmentsCoordinator downloadMessagesAndListAttachmentsCoordinator;
    private final DownloadMessageExtendedPayloadCoordinator downloadMessageExtendedPayloadCoordinator;
    private final DeleteMessageAndAttachmentsCoordinator deleteMessageAndAttachmentsCoordinator;
    private final RegisterServerPushNotificationsCoordinator serverPushNotificationsCoordinator;
    private final WebsocketCoordinator websocketCoordinator;
    private final ServerQueryCoordinator serverQueryCoordinator;
    private final ServerUserDataCoordinator serverUserDataCoordinator;
    private final GetTurnCredentialsCoordinator getTurnCredentialsCoordinator;
    private final FreeTrialCoordinator freeTrialCoordinator;
    private final VerifyReceiptCoordinator verifyReceiptCoordinator;
    private final WellKnownCoordinator wellKnownCoordinator;
    private NotificationPostingDelegate notificationPostingDelegate;
    private IdentityDelegate identityDelegate;

    private CreateSessionDelegate createSessionDelegate;

    public FetchManager(MetaManager metaManager, SSLSocketFactory sslSocketFactory, String engineBaseDirectory, PRNGService prng, ObjectMapper jsonObjectMapper) {
        this.engineBaseDirectory = engineBaseDirectory;
        this.prng = prng;
        this.createServerSessionCoordinator = new CreateServerSessionCoordinator(this, sslSocketFactory);
        this.refreshInboxAttachmentSignedUrlCoordinator = new RefreshInboxAttachmentSignedUrlCoordinator(this, sslSocketFactory);
        this.downloadAttachmentCoordinator = new DownloadAttachmentCoordinator(this, sslSocketFactory, this.refreshInboxAttachmentSignedUrlCoordinator);
        this.downloadMessagesAndListAttachmentsCoordinator = new DownloadMessagesAndListAttachmentsCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.downloadMessageExtendedPayloadCoordinator = new DownloadMessageExtendedPayloadCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.deleteMessageAndAttachmentsCoordinator = new DeleteMessageAndAttachmentsCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.serverPushNotificationsCoordinator = new RegisterServerPushNotificationsCoordinator(this, sslSocketFactory, createServerSessionCoordinator, downloadMessagesAndListAttachmentsCoordinator);
        this.downloadMessagesAndListAttachmentsCoordinator.setRegisterServerPushNotificationDelegate(this.serverPushNotificationsCoordinator);
        this.serverUserDataCoordinator = new ServerUserDataCoordinator(this, sslSocketFactory, createServerSessionCoordinator, jsonObjectMapper, prng);
        this.serverQueryCoordinator = new ServerQueryCoordinator(this, sslSocketFactory, prng, createServerSessionCoordinator, serverUserDataCoordinator);
        this.freeTrialCoordinator = new FreeTrialCoordinator(this, sslSocketFactory);
        this.verifyReceiptCoordinator = new VerifyReceiptCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.wellKnownCoordinator = new WellKnownCoordinator(this, sslSocketFactory, jsonObjectMapper);
        this.websocketCoordinator = new WebsocketCoordinator(this, sslSocketFactory, createServerSessionCoordinator, downloadMessagesAndListAttachmentsCoordinator, wellKnownCoordinator, jsonObjectMapper);
        this.getTurnCredentialsCoordinator = new GetTurnCredentialsCoordinator(this, sslSocketFactory, createServerSessionCoordinator, wellKnownCoordinator);

        metaManager.requestDelegate(this, CreateSessionDelegate.class);
        metaManager.requestDelegate(this, SolveChallengeDelegate.class);
        metaManager.requestDelegate(this, ProcessDownloadedMessageDelegate.class);
        metaManager.requestDelegate(this, NotificationListeningDelegate.class);
        metaManager.requestDelegate(this, NotificationPostingDelegate.class);
        metaManager.requestDelegate(this, ChannelDelegate.class);
        metaManager.requestDelegate(this, IdentityDelegate.class);
        metaManager.registerImplementedDelegates(this);
    }

    // region setDelegates

    @Override
    public void initialisationComplete() {
        wellKnownCoordinator.initialQueueing();
        deleteMessageAndAttachmentsCoordinator.initialQueueing();
        serverPushNotificationsCoordinator.initialQueueing();
        downloadAttachmentCoordinator.initialQueueing();
        serverQueryCoordinator.initialQueueing();
        downloadMessagesAndListAttachmentsCoordinator.initialQueueing();
        downloadMessageExtendedPayloadCoordinator.initialQueueing();
        websocketCoordinator.initialQueueing();
        serverUserDataCoordinator.initialQueueing();
        createServerSessionCoordinator.initialQueueing();
    }

    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (FetchManagerSession fetchManagerSession = getSession()) {
            CachedWellKnown.createTable(fetchManagerSession.session);
            ServerSession.createTable(fetchManagerSession.session);
            PushNotificationConfiguration.createTable(fetchManagerSession.session);
            PendingDeleteFromServer.createTable(fetchManagerSession.session);
            InboxMessage.createTable(fetchManagerSession.session);
            InboxAttachment.createTable(fetchManagerSession.session);
            PendingServerQuery.createTable(fetchManagerSession.session);
            fetchManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to createCurrentDevice network fetch databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        CachedWellKnown.upgradeTable(session, oldVersion, newVersion);
        ServerSession.upgradeTable(session, oldVersion, newVersion);
        PushNotificationConfiguration.upgradeTable(session, oldVersion, newVersion);
        PendingDeleteFromServer.upgradeTable(session, oldVersion, newVersion);
        InboxMessage.upgradeTable(session, oldVersion, newVersion);
        InboxAttachment.upgradeTable(session, oldVersion, newVersion);
        PendingServerQuery.upgradeTable(session, oldVersion, newVersion);
    }


    public void setDelegate(SolveChallengeDelegate solveChallengeDelegate) {
        this.createServerSessionCoordinator.setSolveChallengeDelegate(solveChallengeDelegate);
    }

    public void setDelegate(ProcessDownloadedMessageDelegate processDownloadedMessageDelegate) {
        this.downloadMessagesAndListAttachmentsCoordinator.setProcessDownloadedMessageDelegate(processDownloadedMessageDelegate);
    }

    public void setDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.serverPushNotificationsCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.websocketCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.downloadMessagesAndListAttachmentsCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.downloadMessageExtendedPayloadCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.downloadAttachmentCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.deleteMessageAndAttachmentsCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.serverQueryCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.refreshInboxAttachmentSignedUrlCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.serverUserDataCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.verifyReceiptCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
    }

    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.serverPushNotificationsCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.downloadAttachmentCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.createServerSessionCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.refreshInboxAttachmentSignedUrlCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.downloadMessagesAndListAttachmentsCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.downloadMessageExtendedPayloadCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.websocketCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.wellKnownCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.getTurnCredentialsCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
    }

    public void setDelegate(ChannelDelegate channelDelegate) {
        this.serverQueryCoordinator.setChannelDelegate(channelDelegate);
    }

    public void setDelegate(IdentityDelegate identityDelegate) {
        this.identityDelegate = identityDelegate;
    }

    // endregion

    public void deleteOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        // Delete all InboxMessage (this deletes InboxAttachment)
        InboxMessage[] inboxMessages = InboxMessage.getAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        for (InboxMessage inboxMessage: inboxMessages) {
            inboxMessage.delete();
        }
        PendingDeleteFromServer.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        for (PendingServerQuery pendingServerQuery: PendingServerQuery.getAll(wrapSession(session))) {
            try {
                ServerQuery serverQuery = ServerQuery.of(pendingServerQuery.getEncodedQuery());
                if (ownedIdentity.equals(serverQuery.getOwnedIdentity())) {
                    pendingServerQuery.delete();
                }
            } catch (DecodingException e) {
                // bad encoded query --> delete it
                pendingServerQuery.delete();
            }
        }
        PushNotificationConfiguration.deleteForOwnedIdentity(wrapSession(session), ownedIdentity);
        ServerSession.deleteForIdentity(wrapSession(session), ownedIdentity);
    }

    // region FetchManagerSessionFactory

    @Override
    public FetchManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in FetchManager.");
        }
        return new FetchManagerSession(createSessionDelegate.getSession(),
                downloadMessagesAndListAttachmentsCoordinator,
                downloadMessageExtendedPayloadCoordinator,
                downloadAttachmentCoordinator,
                deleteMessageAndAttachmentsCoordinator,
                serverPushNotificationsCoordinator,
                serverQueryCoordinator,
                identityDelegate,
                engineBaseDirectory,
                notificationPostingDelegate,
                createServerSessionCoordinator);
    }

    private FetchManagerSession wrapSession(Session session) {
        return new FetchManagerSession(session,
                downloadMessagesAndListAttachmentsCoordinator,
                downloadMessageExtendedPayloadCoordinator,
                downloadAttachmentCoordinator,
                deleteMessageAndAttachmentsCoordinator,
                serverPushNotificationsCoordinator,
                serverQueryCoordinator,
                identityDelegate,
                engineBaseDirectory,
                notificationPostingDelegate,
                createServerSessionCoordinator);
    }

    // endregion

    // region implement NetworkFetchDelegate

    @Override
    public void downloadMessages(Identity ownedIdentity, UID deviceUid) {
        downloadMessagesAndListAttachmentsCoordinator.downloadMessagesAndListAttachments(ownedIdentity, deviceUid);
    }

    @Override
    public DecryptedApplicationMessage getMessage(Identity ownedIdentity, UID messageUid) {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
            if (inboxMessage == null) {
                return null;
            }
            return inboxMessage.getDecryptedApplicationMessage();
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public boolean canAllAttachmentsBeDownloaded(Identity ownedIdentity, UID messageUid) throws SQLException {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            InboxAttachment[] attachments = InboxAttachment.getAll(fetchManagerSession, ownedIdentity, messageUid);
            for (InboxAttachment attachment: attachments) {
                if (attachment.cannotBeFetched()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public void setAttachmentKeyAndMetadataAndMessagePayload(Session session, Identity ownedIdentity, UID messageUid, Identity remoteIdentity, AttachmentKeyAndMetadata[] attachmentKeyAndMetadata, byte[] messagePayload, AuthEncKey extendedPayloadKey) throws Exception {
        if (attachmentKeyAndMetadata == null) {
            Logger.e("FetchManager is trying to setAttachmentKeyAndMetadataAndMessagePayload with a null attachmentKeyAndMetadata.");
            throw new Exception();
        }
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
        if (inboxMessage == null) {
            Logger.e("FetchManager is trying to setAttachmentKeyAndMetadataAndMessagePayload for an non-existing messageUid.");
            throw new Exception();
        }
        InboxAttachment[] attachments = inboxMessage.getAttachments();
        if (attachments.length != attachmentKeyAndMetadata.length) {
            Logger.e("Attachment count mismatch between message and attachmentKeyAndMetadata in setAttachmentKeyAndMetadataAndMessagePayload.");
            throw new Exception();
        }
        Logger.d("Setting attachmentKeyAndMetadata for " + attachments.length + " attachments.");
        for (int i=0; i<attachments.length; i++) {
            attachments[i].setKeyAndMetadata(attachmentKeyAndMetadata[i].getKey(),
                    attachmentKeyAndMetadata[i].getMetadata());
        }
        inboxMessage.setPayloadAndFromIdentity(messagePayload, remoteIdentity, extendedPayloadKey);
    }



    @Override
    public void downloadAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory) {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            InboxAttachment inboxAttachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
            if (inboxAttachment == null) {
                Logger.e("FetchManager received a downloadAttachment request for an unknown attachment " + messageUid + "-" + attachmentNumber);
                return;
            }
            if (inboxAttachment.cannotBeFetched()) {
                Logger.e("FetchManager received a downloadAttachment request for an attachment that cannot be fetched " + messageUid + "-" + attachmentNumber);
                return;
            }
            inboxAttachment.requestDownload(priorityCategory);
            fetchManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.e("FetchManager was unable to downloadAttachment " + messageUid + "-" + attachmentNumber);
        }
    }

    @Override
    public void pauseDownloadAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            InboxAttachment inboxAttachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
            if (inboxAttachment == null) {
                Logger.e("FetchManager received a pauseDownloadAttachment request for an unknown attachment " + messageUid + "-" + attachmentNumber);
                return;
            }
            inboxAttachment.pauseDownload();
            fetchManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.e("FetchManager was unable to pauseDownloadAttachment " + messageUid + "-" + attachmentNumber);
        }
    }

    @Override
    public ReceivedAttachment getAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            InboxAttachment inboxAttachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
            if (inboxAttachment == null) {
                Logger.e("FetchManager received a getAttachment request for an unknown attachment " + messageUid + "-" + attachmentNumber);
                return null;
            }
            if (inboxAttachment.cannotBeFetched()) {
                Logger.e("FetchManager received a getAttachment request for an attachment not yet ready " + messageUid + "-" + attachmentNumber);
                return null;
            }
            return new ReceivedAttachment(
                    inboxAttachment.getOwnedIdentity(),
                    inboxAttachment.getMessageUid(),
                    inboxAttachment.getAttachmentNumber(),
                    inboxAttachment.getMetadata(),
                    inboxAttachment.getUrl(),
                    inboxAttachment.getPlaintextExpectedLength(),
                    inboxAttachment.getPlaintextReceivedLength(),
                    inboxAttachment.isDownloadRequested());
        } catch (SQLException e) {
            Logger.e("FetchManager was unable to getAttachment " + messageUid + "-" + attachmentNumber);
            return null;
        }
    }

    @Override
    public ReceivedAttachment[] getMessageAttachments(Identity ownedIdentity, UID messageUid) {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            InboxAttachment[] inboxAttachments = InboxAttachment.getAll(fetchManagerSession, ownedIdentity, messageUid);
            if (inboxAttachments == null) {
                Logger.e("FetchManager received a getAttachment request for an unknown attachment " + messageUid);
                return null;
            }
            ReceivedAttachment[] receivedAttachments = new ReceivedAttachment[inboxAttachments.length];
            for (int i=0; i<inboxAttachments.length; i++) {
                InboxAttachment inboxAttachment = inboxAttachments[i];
                if (inboxAttachment.cannotBeFetched()) {
                    Logger.e("FetchManager received a getAttachment request for an attachment not yet ready " + messageUid + "-" + i);
                    return null;
                }
                receivedAttachments[i] = new ReceivedAttachment(
                        inboxAttachment.getOwnedIdentity(),
                        inboxAttachment.getMessageUid(),
                        inboxAttachment.getAttachmentNumber(),
                        inboxAttachment.getMetadata(),
                        inboxAttachment.getUrl(),
                        inboxAttachment.getPlaintextExpectedLength(),
                        inboxAttachment.getPlaintextReceivedLength(),
                        inboxAttachment.isDownloadRequested());
            }
            return  receivedAttachments;
        } catch (SQLException e) {
            Logger.e("FetchManager was unable to getAttachments " + messageUid);
            return null;
        }
    }

    @Override
    public boolean isInboxAttachmentReceived(Session session, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws Exception {
        InboxAttachment inboxAttachment = InboxAttachment.get(wrapSession(session), ownedIdentity, messageUid, attachmentNumber);
        return (inboxAttachment == null) || (inboxAttachment.getExpectedLength() == inboxAttachment.getReceivedLength());
    }

    // This method marks a message and all its attachments for deletion
    @Override
    public void deleteMessageAndAttachments(Session session, Identity ownedIdentity, UID messageUid) {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
        if (inboxMessage == null) {
            return;
        }
        inboxMessage.markForDeletion();
        for (InboxAttachment inboxAttachment: inboxMessage.getAttachments()) {
            inboxAttachment.markForDeletion();
        }
        PendingDeleteFromServer.create(fetchManagerSession, ownedIdentity, messageUid);
    }

    // This method marks a message for deletion and creates a PendingDeleteFromServer if needed
    @Override
    public void deleteMessage(Session session, Identity ownedIdentity, UID messageUid) {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
        if (inboxMessage == null) {
            return;
        }
        inboxMessage.markForDeletion();
        if (inboxMessage.canBeDeleted()) {
            PendingDeleteFromServer.create(fetchManagerSession, ownedIdentity, messageUid);
        }
    }

    // This method marks an attachment for deletion and creates a PendingDeleteFromServer if needed
    @Override
    public void deleteAttachment(Session session, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws SQLException {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxAttachment inboxAttachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
        if (inboxAttachment == null) {
            return;
        }
        inboxAttachment.markForDeletion();
        if (inboxAttachment.getMessage().canBeDeleted()) {
            PendingDeleteFromServer.create(fetchManagerSession, ownedIdentity, messageUid);
        }
    }

    @Override
    public void resendAllDownloadedAttachmentNotifications() throws Exception {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            InboxAttachment[] inboxAttachments = InboxAttachment.getAllDownloaded(fetchManagerSession);
            for (InboxAttachment inboxAttachment: inboxAttachments) {
                downloadAttachmentCoordinator.attachmentDownloadFinished(inboxAttachment.getOwnedIdentity(), inboxAttachment.getMessageUid(), inboxAttachment.getAttachmentNumber());
            }
        }
    }

    @Override
    public void createPendingServerQuery(Session session, ServerQuery serverQuery) throws Exception {
        PendingServerQuery pendingServerQuery = PendingServerQuery.create(wrapSession(session), serverQuery, prng);
        if (pendingServerQuery == null) {
            throw new Exception();
        }
    }


    public void deleteExistingServerSessionAndCreateANewOne(Session session, Identity ownedIdentity) {
        ServerSession.deleteForIdentity(wrapSession(session), ownedIdentity);
        createServerSessionCoordinator.createServerSession(ownedIdentity);
    }

    public void connectWebsockets(String os, String osVersion, int appBuild, String appVersion) {
        if ("javax.net.ssl.HttpsURLConnection.DefaultHostnameVerifier".equals(HttpsURLConnection.getDefaultHostnameVerifier().getClass().getCanonicalName())) {
            Logger.w("WARNING: default HostnameVerifier not set. Websocket connection will most probably fail.\n\tYou may want to consider using OkHttp's HostnameVerifier as the default with:\n\t\tHttpsURLConnection.setDefaultHostnameVerifier(OkHostnameVerifier.INSTANCE);");
        }
        websocketCoordinator.connectWebsockets(os, osVersion, appBuild, appVersion);
    }

    public void disconnectWebsockets() {
        websocketCoordinator.disconnectWebsockets();
    }

    public void pingWebsocket (Identity ownedIdentity) {
        websocketCoordinator.pingWebsocket(ownedIdentity);
    }

    @Override
    public byte[] getServerAuthenticationToken(Identity ownedIdentity) {
        try (FetchManagerSession fetchManagerSession = getSession()) {
            return ServerSession.getToken(fetchManagerSession, ownedIdentity);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void retryScheduledNetworkTasks() {
        createServerSessionCoordinator.retryScheduledNetworkTasks();
        deleteMessageAndAttachmentsCoordinator.retryScheduledNetworkTasks();
        downloadAttachmentCoordinator.retryScheduledNetworkTasks();
        downloadMessagesAndListAttachmentsCoordinator.retryScheduledNetworkTasks();
        downloadMessageExtendedPayloadCoordinator.retryScheduledNetworkTasks();
        refreshInboxAttachmentSignedUrlCoordinator.retryScheduledNetworkTasks();
        serverPushNotificationsCoordinator.retryScheduledNetworkTasks();
        serverQueryCoordinator.retryScheduledNetworkTasks();
        websocketCoordinator.retryScheduledNetworkTasks();
    }

    @Override
    public void getTurnCredentials(Identity ownedIdentity, UUID callUuid, String username1, String username2) {
        getTurnCredentialsCoordinator.getTurnCredentials(ownedIdentity, callUuid, username1, username2);
    }

    @Override
    public void queryApiKeyStatus(Identity ownedIdentity, UUID apiKey) {
        createServerSessionCoordinator.queueNewQueryApiKeyStatusOperation(ownedIdentity, apiKey);
    }

    @Override
    public void queryFreeTrial(Identity ownedIdentity) {
        freeTrialCoordinator.queryFreeTrial(ownedIdentity);
    }

    @Override
    public void startFreeTrial(Identity ownedIdentity) {
        freeTrialCoordinator.startFreeTrial(ownedIdentity);
    }

    @Override
    public void verifyReceipt(Identity ownedIdentity, String storeToken) {
        verifyReceiptCoordinator.verifyReceipt(ownedIdentity, storeToken);
    }

    @Override
    public void queryServerWellKnown(String server) {
        wellKnownCoordinator.queueNewWellKnownDownloadOperation(server);
    }

    @Override
    public String getOsmServerUrl(String server) {
        try {
            return wellKnownCoordinator.getOsmUrl(server);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public String getAddressServerUrl(String server) {
        try {
            return wellKnownCoordinator.getAddressUrl(server);
        } catch (Exception ignored) {
            return null;
        }
    }

    // endregion

    // region implement PushNotificationDelegate

    @Override
    public void registerPushNotificationIfConfigurationChanged(Session session, Identity identity, UID deviceUid, PushNotificationTypeAndParameters newPushParameters) throws SQLException {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        PushNotificationConfiguration pushNotificationConfiguration = PushNotificationConfiguration.get(fetchManagerSession, identity);
        if (pushNotificationConfiguration != null) {
            if (pushNotificationConfiguration.getDeviceUid().equals(deviceUid)) {
                PushNotificationTypeAndParameters oldPushParameters = pushNotificationConfiguration.getPushNotificationTypeAndParameters();
                if (oldPushParameters.equals(newPushParameters)) {
                    // when parameters are equal, we only replace them in DB if it changed from a no-kick to a kick
                    if (oldPushParameters.kickOtherDevices || !newPushParameters.kickOtherDevices) {
                        return;
                    } else {
                        // we are simply going into kick mode, no other change, so no need to change the maskingUid
                        newPushParameters.identityMaskingUid = oldPushParameters.identityMaskingUid;
                    }
                } else {
                    // token has changed, or notification type has changed
                    // we still need to preserve the kickOtherDevices parameter
                    if (oldPushParameters.kickOtherDevices) {
                        newPushParameters.kickOtherDevices = true;
                    }
                }
            }
            pushNotificationConfiguration.delete();
        }
        if (PushNotificationConfiguration.create(fetchManagerSession, identity, deviceUid, newPushParameters) == null) {
            throw new SQLException();
        }
    }

    @Override
    public void unregisterPushNotification(Session session, Identity identity)  throws SQLException {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        PushNotificationConfiguration pushNotificationConfiguration = PushNotificationConfiguration.get(fetchManagerSession, identity);
        if (pushNotificationConfiguration != null) {
            pushNotificationConfiguration.delete();
        }
    }


    @Override
    public void processAndroidPushNotification(String maskingUidString) {
        serverPushNotificationsCoordinator.processAndroidPushNotification(maskingUidString);
    }

    @Override
    public void forceRegisterPushNotification(Identity ownedIdentity) {
        serverPushNotificationsCoordinator.registerServerPushNotification(ownedIdentity);
    }

    @Override
    public Identity getOwnedIdentityFromMaskingUid(String maskingUidString) {
        return serverPushNotificationsCoordinator.getOwnedIdentityFromMaskingUid(maskingUidString);
    }

    // endregion

    public void deleteReturnReceipt(Identity ownedIdentity, byte[] serverUid) {
        websocketCoordinator.deleteReturnReceipt(ownedIdentity, serverUid);
    }
}
