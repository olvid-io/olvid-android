/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.AttachmentKeyAndMetadata;
import io.olvid.engine.datatypes.containers.DecryptedApplicationMessage;
import io.olvid.engine.datatypes.containers.OwnedIdentitySynchronizationStatus;
import io.olvid.engine.datatypes.containers.ReceivedAttachment;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.engine.types.JsonOsmStyle;
import io.olvid.engine.engine.types.ObvMessage;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NetworkFetchDelegate;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
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
import io.olvid.engine.networkfetch.databases.PendingServerQuery;
import io.olvid.engine.networkfetch.databases.PushNotificationConfiguration;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

public class FetchManager implements FetchManagerSessionFactory, NetworkFetchDelegate, PushNotificationDelegate, ObvManager {
    private final String engineBaseDirectory;
    private final PRNGService prng;
    private final CreateServerSessionCoordinator createServerSessionCoordinator;
    private final RefreshInboxAttachmentSignedUrlCoordinator refreshInboxAttachmentSignedUrlCoordinator;
    private final DownloadAttachmentCoordinator downloadAttachmentCoordinator;
    private final DownloadMessagesAndListAttachmentsCoordinator downloadMessagesAndListAttachmentsCoordinator;
    private final DownloadMessageExtendedPayloadCoordinator downloadMessageExtendedPayloadCoordinator;
    private final DeleteMessageAndAttachmentsCoordinator deleteMessageAndAttachmentsCoordinator;
    private final RegisterServerPushNotificationsCoordinator registerServerPushNotificationsCoordinator;
    private final WebsocketCoordinator websocketCoordinator;
    private final ServerQueryCoordinator serverQueryCoordinator;
    private final ServerUserDataCoordinator serverUserDataCoordinator;
    private final GetTurnCredentialsCoordinator getTurnCredentialsCoordinator;
    private final FreeTrialCoordinator freeTrialCoordinator;
    private final VerifyReceiptCoordinator verifyReceiptCoordinator;
    private final WellKnownCoordinator wellKnownCoordinator;
    private NotificationPostingDelegate notificationPostingDelegate;
    private IdentityDelegate identityDelegate;
    private ProcessDownloadedMessageDelegate processDownloadedMessageDelegate;
    private CreateSessionDelegate createSessionDelegate;

    private final HashSet<Identity> ownedIdentitiesUpToDateRegardingServerListing;

    public FetchManager(MetaManager metaManager, SSLSocketFactory sslSocketFactory, String engineBaseDirectory, PRNGService prng, ObjectMapper jsonObjectMapper) {
        this.engineBaseDirectory = engineBaseDirectory;
        this.prng = prng;
        this.createServerSessionCoordinator = new CreateServerSessionCoordinator(this, sslSocketFactory);
        this.refreshInboxAttachmentSignedUrlCoordinator = new RefreshInboxAttachmentSignedUrlCoordinator(this, sslSocketFactory);
        this.downloadAttachmentCoordinator = new DownloadAttachmentCoordinator(this, sslSocketFactory, this.refreshInboxAttachmentSignedUrlCoordinator);
        this.downloadMessagesAndListAttachmentsCoordinator = new DownloadMessagesAndListAttachmentsCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.downloadMessageExtendedPayloadCoordinator = new DownloadMessageExtendedPayloadCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.deleteMessageAndAttachmentsCoordinator = new DeleteMessageAndAttachmentsCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.registerServerPushNotificationsCoordinator = new RegisterServerPushNotificationsCoordinator(this, sslSocketFactory, createServerSessionCoordinator, downloadMessagesAndListAttachmentsCoordinator);
        this.downloadMessagesAndListAttachmentsCoordinator.setRegisterServerPushNotificationDelegate(this.registerServerPushNotificationsCoordinator);
        this.serverUserDataCoordinator = new ServerUserDataCoordinator(this, sslSocketFactory, createServerSessionCoordinator, jsonObjectMapper, prng);
        this.serverQueryCoordinator = new ServerQueryCoordinator(this, sslSocketFactory, prng, createServerSessionCoordinator, serverUserDataCoordinator, jsonObjectMapper);
        this.freeTrialCoordinator = new FreeTrialCoordinator(this, sslSocketFactory);
        this.verifyReceiptCoordinator = new VerifyReceiptCoordinator(this, sslSocketFactory, createServerSessionCoordinator);
        this.wellKnownCoordinator = new WellKnownCoordinator(this, sslSocketFactory, jsonObjectMapper);
        this.websocketCoordinator = new WebsocketCoordinator(this, sslSocketFactory, createServerSessionCoordinator, downloadMessagesAndListAttachmentsCoordinator, wellKnownCoordinator, jsonObjectMapper);
        this.getTurnCredentialsCoordinator = new GetTurnCredentialsCoordinator(this, sslSocketFactory, createServerSessionCoordinator, wellKnownCoordinator);

        ownedIdentitiesUpToDateRegardingServerListing = new HashSet<>();

        metaManager.requestDelegate(this, CreateSessionDelegate.class);
        metaManager.requestDelegate(this, SolveChallengeDelegate.class);
        metaManager.requestDelegate(this, ProcessDownloadedMessageDelegate.class);
        metaManager.requestDelegate(this, NotificationListeningDelegate.class);
        metaManager.requestDelegate(this, NotificationPostingDelegate.class);
        metaManager.requestDelegate(this, ChannelDelegate.class);
        metaManager.requestDelegate(this, IdentityDelegate.class);
        metaManager.requestDelegate(this, ProtocolStarterDelegate.class);
        metaManager.registerImplementedDelegates(this);
    }

    // region setDelegates

    @Override
    public int initialQueueingPriority() {
        return 0;
    }

    @Override
    public void initialisationComplete() {
        // we optimize the initial queueing order so web sockets connect as soon as possible and messages are listed soon too.
        wellKnownCoordinator.initialQueueing();
        websocketCoordinator.initialQueueing();
        downloadMessagesAndListAttachmentsCoordinator.initialQueueing();
        registerServerPushNotificationsCoordinator.initialQueueing();
        downloadAttachmentCoordinator.initialQueueing();
        serverQueryCoordinator.initialQueueing();
        downloadMessageExtendedPayloadCoordinator.initialQueueing();
        serverUserDataCoordinator.initialQueueing();
        createServerSessionCoordinator.initialQueueing();
    }

    public void startProcessing() {
        createServerSessionCoordinator.startProcessing();
        deleteMessageAndAttachmentsCoordinator.startProcessing();
        downloadAttachmentCoordinator.startProcessing();
        downloadMessageExtendedPayloadCoordinator.startProcessing();
        downloadMessagesAndListAttachmentsCoordinator.startProcessing();
        freeTrialCoordinator.startProcessing();
        getTurnCredentialsCoordinator.startProcessing();
        refreshInboxAttachmentSignedUrlCoordinator.startProcessing();
        registerServerPushNotificationsCoordinator.startProcessing();
        serverQueryCoordinator.startProcessing();
        serverUserDataCoordinator.startProcessing();
        verifyReceiptCoordinator.startProcessing();
        websocketCoordinator.startProcessing();
        wellKnownCoordinator.startProcessing();
    }

    @SuppressWarnings("unused")
    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (FetchManagerSession fetchManagerSession = getSession()) {
            CachedWellKnown.createTable(fetchManagerSession.session);
            ServerSession.createTable(fetchManagerSession.session);
            PushNotificationConfiguration.createTable(fetchManagerSession.session);
            InboxMessage.createTable(fetchManagerSession.session);
            InboxAttachment.createTable(fetchManagerSession.session);
            PendingServerQuery.createTable(fetchManagerSession.session);
            fetchManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.x(e);
            throw new RuntimeException("Unable to createCurrentDevice network fetch databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        CachedWellKnown.upgradeTable(session, oldVersion, newVersion);
        ServerSession.upgradeTable(session, oldVersion, newVersion);
        PushNotificationConfiguration.upgradeTable(session, oldVersion, newVersion);
        InboxMessage.upgradeTable(session, oldVersion, newVersion);
        InboxAttachment.upgradeTable(session, oldVersion, newVersion);
        PendingServerQuery.upgradeTable(session, oldVersion, newVersion);
        if (oldVersion < 40 && newVersion >= 40) {
            Logger.d("DROPPING `pending_delete_from_server` DATABASE FOR VERSION 40");
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE `pending_delete_from_server`");
            }
        }
    }


    @SuppressWarnings("unused")
    public void setDelegate(SolveChallengeDelegate solveChallengeDelegate) {
        this.createServerSessionCoordinator.setSolveChallengeDelegate(solveChallengeDelegate);
    }

    @SuppressWarnings("unused")
    public void setDelegate(ProcessDownloadedMessageDelegate processDownloadedMessageDelegate) {
        this.processDownloadedMessageDelegate = processDownloadedMessageDelegate;
        this.downloadMessagesAndListAttachmentsCoordinator.setProcessDownloadedMessageDelegate(processDownloadedMessageDelegate);
    }

    @SuppressWarnings("unused")
    public void setDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.registerServerPushNotificationsCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
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

    @SuppressWarnings("unused")
    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.registerServerPushNotificationsCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.downloadAttachmentCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.createServerSessionCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.refreshInboxAttachmentSignedUrlCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.downloadMessagesAndListAttachmentsCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.downloadMessageExtendedPayloadCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.websocketCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.wellKnownCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
        this.getTurnCredentialsCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
    }

    @SuppressWarnings("unused")
    public void setDelegate(ChannelDelegate channelDelegate) {
        this.serverQueryCoordinator.setChannelDelegate(channelDelegate);
    }

    @SuppressWarnings("unused")
    public void setDelegate(IdentityDelegate identityDelegate) {
        this.identityDelegate = identityDelegate;
    }

    @SuppressWarnings("unused")
    public void setDelegate(ProtocolStarterDelegate protocolStarterDelegate) {
        this.websocketCoordinator.setProtocolStarterDelegate(protocolStarterDelegate);
        this.registerServerPushNotificationsCoordinator.setProtocolStarterDelegate(protocolStarterDelegate);
    }

    // endregion

    public void deleteOwnedIdentity(Session session, Identity ownedIdentity, boolean doNotDeleteServerSession) throws SQLException {
        // Delete all InboxMessage (this deletes InboxAttachment)
        InboxMessage[] inboxMessages = InboxMessage.getAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        for (InboxMessage inboxMessage: inboxMessages) {
            inboxMessage.delete();
        }
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
        if (doNotDeleteServerSession) {
            ServerSession.deleteForIdentity(wrapSession(session), ownedIdentity);
        }
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
                deleteMessageAndAttachmentsCoordinator,
                downloadAttachmentCoordinator,
//                deleteMessageAndAttachmentsCoordinator,
                registerServerPushNotificationsCoordinator,
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
                deleteMessageAndAttachmentsCoordinator,
                downloadAttachmentCoordinator,
//                deleteMessageAndAttachmentsCoordinator,
                registerServerPushNotificationsCoordinator,
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
        markOwnedIdentityAsNotUpToDate(ownedIdentity, OwnedIdentitySynchronizationStatus.MANUAL_SYNC_IN_PROGRESS);
        downloadMessagesAndListAttachmentsCoordinator.downloadMessagesAndListAttachments(ownedIdentity, deviceUid);

        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED_OWNED_IDENTITY_KEY, ownedIdentity);
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED_USER_INITIATED_KEY, true);
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED, userInfo);
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
    public void setAttachmentKeyAndMetadataAndMessagePayload(Session session, Identity ownedIdentity, UID messageUid, Identity remoteIdentity, UID remoteDeviceUid, AttachmentKeyAndMetadata[] attachmentKeyAndMetadata, byte[] messagePayload, AuthEncKey extendedPayloadKey) throws Exception {
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
        inboxMessage.setPayloadAndFromIdentity(messagePayload, remoteIdentity, remoteDeviceUid, extendedPayloadKey, attachments);
        // just in case, also mark recentlyOnline as true (otherwise, a contact could remain not recently online until a contact discovery)
        identityDelegate.setContactRecentlyOnline(session, ownedIdentity, remoteIdentity, true);
    }

    @Override
    public void setInboxMessageFromIdentityForMissingPreKeyContact(Session session, Identity ownedIdentity, UID messageUid, Identity contactIdentity) throws Exception {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
        if (inboxMessage == null) {
            Logger.e("FetchManager is trying to setInboxMessageFromIdentityForMissingPreKeyContact for an non-existing messageUid.");
            throw new Exception();
        }
        inboxMessage.setFromIdentityForMissingPreKeyContact(contactIdentity);
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
    public boolean isInboxAttachmentReceived(Session session, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws Exception {
        InboxAttachment inboxAttachment = InboxAttachment.get(wrapSession(session), ownedIdentity, messageUid, attachmentNumber);
        return (inboxAttachment == null) || (inboxAttachment.getExpectedLength() == inboxAttachment.getReceivedLength());
    }


    // this method is called when a received message cannot be decrypted.
    // If we are still listing messages on the server, we may be late on self-ratchet so we simply postpone the processing of this message by doing nothing :)
    @Override
    public void messageCannotBeDecrypted(Session session, Identity ownedIdentity, UID messageUid) {
        synchronized (ownedIdentitiesUpToDateRegardingServerListing) {
            if (ownedIdentitiesUpToDateRegardingServerListing.contains(ownedIdentity)) {
                deleteMessageAndAttachments(session, ownedIdentity, messageUid);
            }
        }
    }

    @Override
    public void markOwnedIdentityAsUpToDate(Identity ownedIdentity) {
        synchronized (ownedIdentitiesUpToDateRegardingServerListing) {
            // mark the identity as up to date
            ownedIdentitiesUpToDateRegardingServerListing.add(ownedIdentity);

            // notify app that syncing is finished
            if (notificationPostingDelegate != null) {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_OWNED_IDENTITY_KEY, ownedIdentity);
                userInfo.put(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_STATUS_KEY, OwnedIdentitySynchronizationStatus.SYNCHRONIZED);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER, userInfo);
            }

            // reprocess all unprocessed messages
            try (FetchManagerSession fetchManagerSession = getSession()) {
                // retry processing messages that were downloaded but never decrypted nor marked for deletion
                InboxMessage[] unprocessedMessages = InboxMessage.getUnprocessedMessagesForOwnedIdentity(fetchManagerSession, ownedIdentity);
                for (InboxMessage inboxMessage : unprocessedMessages) {
                    processDownloadedMessageDelegate.processDownloadedMessage(inboxMessage.getNetworkReceivedMessage());
                }
            } catch (SQLException ignored) { }
        }
    }

    @Override
    public void markOwnedIdentityAsNotUpToDate(Identity ownedIdentity, OwnedIdentitySynchronizationStatus synchronizationStatus) {
        synchronized (ownedIdentitiesUpToDateRegardingServerListing) {
            // notify app that syncing is in progress, but only if it was in sync
            ownedIdentitiesUpToDateRegardingServerListing.remove(ownedIdentity);

            if (notificationPostingDelegate != null && synchronizationStatus != OwnedIdentitySynchronizationStatus.SYNCHRONIZED) {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_OWNED_IDENTITY_KEY, ownedIdentity);
                userInfo.put(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_STATUS_KEY, synchronizationStatus);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER, userInfo);
            }
        }
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
        session.addSessionCommitListener(() -> fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeDeletedFromServer(ownedIdentity, messageUid));
    }

    // This method marks a message for deletion and queues the operation to delete it from server
    @Override
    public void deleteMessage(Session session, Identity ownedIdentity, UID messageUid) {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
        if (inboxMessage == null) {
            return;
        }
        inboxMessage.markForDeletion();
        if (inboxMessage.canBeDeleted()) {
            session.addSessionCommitListener(() -> fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeDeletedFromServer(ownedIdentity, messageUid));
        }
    }

    // This method marks an attachment for deletion and queues the operation to delete it from server
    @Override
    public void deleteAttachment(Session session, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws SQLException {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxAttachment inboxAttachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
        if (inboxAttachment == null) {
            return;
        }
        inboxAttachment.markForDeletion();
        if (inboxAttachment.getMessage().canBeDeleted()) {
            fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeDeletedFromServer(ownedIdentity, messageUid);
        }
    }

    @Override
    public void markMessageAsOnHold(Session session, Identity ownedIdentity, UID messageUid) throws SQLException {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
        if (inboxMessage == null) {
            return;
        }
        inboxMessage.markAsOnHold();
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
    public ObvMessage getOnHoldMessage(Session session, Identity ownedIdentity, UID messageUid) throws Exception {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
        if (inboxMessage == null) {
            throw new Exception("Message not found in Inbox");
        }
        InboxAttachment[] attachments = inboxMessage.getAttachments();
        ReceivedAttachment[] receivedAttachments = new ReceivedAttachment[attachments.length];
        for (int i = 0; i < attachments.length; i++) {
            receivedAttachments[i] = new ReceivedAttachment(
                    attachments[i].getOwnedIdentity(),
                    attachments[i].getMessageUid(),
                    attachments[i].getAttachmentNumber(),
                    attachments[i].getMetadata(),
                    attachments[i].getUrl(),
                    attachments[i].getPlaintextExpectedLength(),
                    attachments[i].getPlaintextReceivedLength(),
                    attachments[i].isDownloadRequested());
        }
        return new ObvMessage(inboxMessage.getDecryptedApplicationMessage(), receivedAttachments);
    }

    @Override
    public void createPendingServerQuery(Session session, ServerQuery serverQuery) throws Exception {
        PendingServerQuery pendingServerQuery = PendingServerQuery.create(wrapSession(session), serverQuery, prng);
        if (pendingServerQuery == null) {
            throw new Exception();
        }
    }


    public void deleteExistingServerSession(Session session, Identity ownedIdentity, boolean createNewSession) {
        ServerSession.deleteForIdentity(wrapSession(session), ownedIdentity);
        if (createNewSession) {
            createServerSessionCoordinator.createServerSession(ownedIdentity);
        }
    }

    public void createServerSession(Identity ownedIdentity) {
        createServerSessionCoordinator.createServerSession(ownedIdentity);
    }

    public void connectWebsockets(boolean relyOnWebsocketForNetworkDetection, String os, String osVersion, int appBuild, String appVersion) {
        if ("javax.net.ssl.HttpsURLConnection.DefaultHostnameVerifier".equals(HttpsURLConnection.getDefaultHostnameVerifier().getClass().getCanonicalName())) {
            Logger.w("WARNING: default HostnameVerifier not set. Websocket connection will most probably fail.\n\tYou may want to consider using OkHttp's HostnameVerifier as the default with:\n\t\tHttpsURLConnection.setDefaultHostnameVerifier(OkHostnameVerifier.INSTANCE);");
        }
        websocketCoordinator.connectWebsockets(relyOnWebsocketForNetworkDetection, os, osVersion, appBuild, appVersion);
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
            Logger.x(e);
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
        registerServerPushNotificationsCoordinator.retryScheduledNetworkTasks();
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
    public List<JsonOsmStyle> getOsmStyles(String server) {
        try {
            return wellKnownCoordinator.getOsmStyles(server);
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
    public void registerPushNotificationIfConfigurationChanged(Session session, Identity ownedIdentity, UID currentDeviceUid, PushNotificationTypeAndParameters newPushParameters) throws SQLException {
        FetchManagerSession fetchManagerSession = wrapSession(session);
        PushNotificationConfiguration pushNotificationConfiguration = PushNotificationConfiguration.get(fetchManagerSession, ownedIdentity);
        if (pushNotificationConfiguration != null) {
            if (pushNotificationConfiguration.getDeviceUid().equals(currentDeviceUid)) {
                PushNotificationTypeAndParameters oldPushParameters = pushNotificationConfiguration.getPushNotificationTypeAndParameters();
                if (oldPushParameters.sameTypeAndToken(newPushParameters)) {
                    // when parameters are equal, we only replace them in DB if it changed from a no-reactivate to a reactivate, or if the deviceUidToReplace has changed
                    if ((!oldPushParameters.reactivateCurrentDevice && newPushParameters.reactivateCurrentDevice) || (oldPushParameters.reactivateCurrentDevice && newPushParameters.reactivateCurrentDevice && !Objects.equals(oldPushParameters.deviceUidToReplace, newPushParameters.deviceUidToReplace))) {
                        // tokens are the same, no need to change identityMaskingUid
                        newPushParameters.identityMaskingUid = oldPushParameters.identityMaskingUid;
                    } else {
                        return;
                    }
                } else {
                    // token has changed, or notification type has changed
                    // we still need to preserve the reactivateCurrentDevice and deviceUidToReplace parameters
                    if (oldPushParameters.reactivateCurrentDevice && !newPushParameters.reactivateCurrentDevice) {
                        newPushParameters.reactivateCurrentDevice = true;
                        newPushParameters.deviceUidToReplace = oldPushParameters.deviceUidToReplace;
                    }
                }
            }
            pushNotificationConfiguration.delete();
        }
        if (PushNotificationConfiguration.create(fetchManagerSession, ownedIdentity, currentDeviceUid, newPushParameters) == null) {
            throw new SQLException();
        }
    }

    @Override
    public void processAndroidPushNotification(String maskingUidString) {
        registerServerPushNotificationsCoordinator.processAndroidPushNotification(maskingUidString);
    }

    @Override
    public void forceRegisterPushNotification(Identity ownedIdentity, boolean triggerAnOwnedDeviceDiscoveryWhenFinished) {
        registerServerPushNotificationsCoordinator.registerServerPushNotification(ownedIdentity, triggerAnOwnedDeviceDiscoveryWhenFinished);
    }

    @Override
    public Identity getOwnedIdentityFromMaskingUid(String maskingUidString) {
        return registerServerPushNotificationsCoordinator.getOwnedIdentityFromMaskingUid(maskingUidString);
    }

    // endregion

    public void deleteReturnReceipt(Identity ownedIdentity, byte[] serverUid) {
        websocketCoordinator.deleteReturnReceipt(ownedIdentity, serverUid);
    }
}
