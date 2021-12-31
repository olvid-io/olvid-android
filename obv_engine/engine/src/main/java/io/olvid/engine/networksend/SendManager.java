/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.engine.networksend;


import java.sql.SQLException;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.datatypes.containers.MessageToSend;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.metamanager.NetworkSendDelegate;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ObvManager;
import io.olvid.engine.networksend.coordinators.CancelAttachmentUploadCoordinator;
import io.olvid.engine.networksend.coordinators.RefreshOutboxAttachmentSignedUrlCoordinator;
import io.olvid.engine.networksend.coordinators.SendAttachmentCoordinator;
import io.olvid.engine.networksend.coordinators.SendMessageCoordinator;
import io.olvid.engine.networksend.coordinators.SendReturnReceiptCoordinator;
import io.olvid.engine.networksend.databases.MessageHeader;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.databases.ReturnReceipt;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;

public class SendManager implements NetworkSendDelegate, SendManagerSessionFactory, ObvManager {
    private final String engineBaseDirectory;
    private final SendMessageCoordinator sendMessageCoordinator;
    private final SendAttachmentCoordinator sendAttachmentCoordinator;
    private final CancelAttachmentUploadCoordinator cancelAttachmentUploadCoordinator;
    private final RefreshOutboxAttachmentSignedUrlCoordinator refreshOutboxAttachmentSignedUrlCoordinator;
    private final SendReturnReceiptCoordinator sendReturnReceiptCoordinator;

    private CreateSessionDelegate createSessionDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;
    private IdentityDelegate identityDelegate;

    public SendManager(MetaManager metaManager, SSLSocketFactory sslSocketFactory, String engineBaseDirectory, PRNGService prng) {
        this.engineBaseDirectory = engineBaseDirectory;
        this.sendMessageCoordinator = new SendMessageCoordinator(this, sslSocketFactory);
        this.refreshOutboxAttachmentSignedUrlCoordinator = new RefreshOutboxAttachmentSignedUrlCoordinator(this, sslSocketFactory);
        this.sendAttachmentCoordinator = new SendAttachmentCoordinator(this, sslSocketFactory, this.refreshOutboxAttachmentSignedUrlCoordinator);
        this.cancelAttachmentUploadCoordinator = new CancelAttachmentUploadCoordinator(this, sslSocketFactory);
        this.sendReturnReceiptCoordinator = new SendReturnReceiptCoordinator(this, sslSocketFactory, prng);

        metaManager.requestDelegate(this, CreateSessionDelegate.class);
        metaManager.requestDelegate(this, NotificationPostingDelegate.class);
        metaManager.requestDelegate(this, NotificationListeningDelegate.class);
        metaManager.requestDelegate(this, IdentityDelegate.class);
        metaManager.registerImplementedDelegates(this);
    }

    @Override
    public void initialisationComplete() {
        sendMessageCoordinator.initialQueueing();
        sendAttachmentCoordinator.initialQueueing();
        cancelAttachmentUploadCoordinator.initialQueueing();
        sendReturnReceiptCoordinator.initialQueueing();
    }

    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (SendManagerSession sendManagerSession = getSession()) {
            OutboxMessage.createTable(sendManagerSession.session);
            OutboxAttachment.createTable(sendManagerSession.session);
            MessageHeader.createTable(sendManagerSession.session);
            ReturnReceipt.createTable(sendManagerSession.session);
            sendManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to create network fetch databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        OutboxMessage.upgradeTable(session, oldVersion, newVersion);
        OutboxAttachment.upgradeTable(session, oldVersion, newVersion);
        MessageHeader.upgradeTable(session, oldVersion, newVersion);
        ReturnReceipt.upgradeTable(session, oldVersion, newVersion);
    }

    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.refreshOutboxAttachmentSignedUrlCoordinator.setNotificationPostingDelegate(notificationPostingDelegate);
    }

    public void setDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.sendAttachmentCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.cancelAttachmentUploadCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.sendMessageCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.refreshOutboxAttachmentSignedUrlCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
        this.sendReturnReceiptCoordinator.setNotificationListeningDelegate(notificationListeningDelegate);
    }

    public void setDelegate(IdentityDelegate identityDelegate) {
        this.identityDelegate = identityDelegate;
    }


    public void deleteOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        // Delete all OutboxMessage (this deletes MessageHeader and OutboxAttachment)
        OutboxMessage[] outboxMessages = OutboxMessage.getAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        for (OutboxMessage outboxMessage: outboxMessages) {
            outboxMessage.delete();
        }
        // delete all ReturnReceipt
        ReturnReceipt.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
    }

    // region implement NetworkSendDelegate

    public void post(Session session, MessageToSend messageToSend) {
        SendManagerSession sendManagerSession = wrapSession(session);

        OutboxMessage.create(sendManagerSession,
                messageToSend.getOwnedIdentity(),
                messageToSend.getUid(),
                messageToSend.getServer(),
                messageToSend.getEncryptedContent(),
                messageToSend.getEncryptedExtendedContent(),
                messageToSend.isApplicationMessage(),
                messageToSend.isVoipMessage()
        );

        if (messageToSend.getHeaders() != null) {
            for (MessageToSend.Header header: messageToSend.getHeaders()) {
                MessageHeader.create(sendManagerSession,
                        messageToSend.getOwnedIdentity(),
                        messageToSend.getUid(),
                        header.getDeviceUid(),
                        header.getToIdentity(),
                        header.getWrappedMessageKey());
            }
        }

        if (messageToSend.getAttachments() != null) {
            int attachmentNumber = 0;
            for (MessageToSend.Attachment attachment: messageToSend.getAttachments()) {
                OutboxAttachment.create(sendManagerSession,
                        messageToSend.getOwnedIdentity(),
                        messageToSend.getUid(),
                        attachmentNumber,
                        attachment.getUrl(),
                        attachment.isDeleteAfterSend(),
                        attachment.getAttachmentLength(),
                        attachment.getKey());
                attachmentNumber++;
            }
        }
    }

    @Override
    public void cancelAttachmentUpload(Session session, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws SQLException {
        OutboxAttachment outboxAttachment = OutboxAttachment.get(wrapSession(session), ownedIdentity, messageUid, attachmentNumber);
        outboxAttachment.setCancelExternallyRequested();
    }

    @Override
    public boolean isOutboxAttachmentSent(Session session, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws SQLException {
        OutboxAttachment outboxAttachment = OutboxAttachment.get(wrapSession(session), ownedIdentity, messageUid, attachmentNumber);
        return (outboxAttachment == null) || outboxAttachment.isAcknowledged();
    }

    @Override
    public boolean isOutboxMessageSent(Session session, Identity ownedIdentity, UID messageUid) throws SQLException {
        OutboxMessage outboxMessage = OutboxMessage.get(wrapSession(session), ownedIdentity, messageUid);
        return (outboxMessage == null) || outboxMessage.isAcknowledged();
    }

    public void sendReturnReceipt(Session session, Identity ownedIdentity, Identity contactIdentity, UID[] contactDeviceUids, int status, byte[] returnReceiptNonce, AuthEncKey returnReceiptKey, Integer attachmentNumber) {
        // send is auto triggered on insertion commit
        ReturnReceipt.create(wrapSession(session), ownedIdentity, contactIdentity, contactDeviceUids, status, returnReceiptNonce, returnReceiptKey, attachmentNumber);
    }

    @Override
    public void retryScheduledNetworkTasks() {
        cancelAttachmentUploadCoordinator.retryScheduledNetworkTasks();
        refreshOutboxAttachmentSignedUrlCoordinator.retryScheduledNetworkTasks();
        sendAttachmentCoordinator.retryScheduledNetworkTasks();
        sendMessageCoordinator.retryScheduledNetworkTasks();
        sendReturnReceiptCoordinator.retryScheduledNetworkTasks();
    }

    // endregion

    @Override
    public SendManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in SendManager.");
        }
        return new SendManagerSession(createSessionDelegate.getSession(), sendMessageCoordinator, sendAttachmentCoordinator, cancelAttachmentUploadCoordinator, notificationPostingDelegate, sendReturnReceiptCoordinator, identityDelegate, engineBaseDirectory);
    }

    private SendManagerSession wrapSession(Session session) {
        return new SendManagerSession(session, sendMessageCoordinator, sendAttachmentCoordinator, cancelAttachmentUploadCoordinator, notificationPostingDelegate, sendReturnReceiptCoordinator, identityDelegate, engineBaseDirectory);
    }
}
