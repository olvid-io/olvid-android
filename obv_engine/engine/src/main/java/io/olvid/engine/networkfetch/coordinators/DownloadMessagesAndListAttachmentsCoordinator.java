/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.engine.networkfetch.coordinators;


import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ProcessDownloadedMessageDelegate;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.DownloadMessagesAndListAttachmentsDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.RegisterServerPushNotificationDelegate;
import io.olvid.engine.networkfetch.operations.DownloadMessagesAndListAttachmentsOperation;
import io.olvid.engine.networkfetch.operations.ProcessPreKeyMessagesForNewContactOperation;
import io.olvid.engine.networkfetch.operations.ProcessWebsocketReceivedMessageOperation;

public class DownloadMessagesAndListAttachmentsCoordinator implements Operation.OnCancelCallback, DownloadMessagesAndListAttachmentsDelegate, InboxMessage.InboxMessageListener, Operation.OnFinishCallback, NotificationListener {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;
    private RegisterServerPushNotificationDelegate registerServerPushNotificationDelegate;

    private final ExponentialBackoffRepeatingScheduler<Identity> scheduler;
    private final NoDuplicateOperationQueue downloadMessagesAndListAttachmentsOperationQueue;

    private ProcessDownloadedMessageDelegate processDownloadedMessageDelegate;

    private final HashMap<Identity, UID> awaitingServerSessionOperations;
    private final Lock awaitingServerSessionOperationsLock;
    private final AwaitingNotificationListener awaitingNotificationListener;

    private NotificationListeningDelegate notificationListeningDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    public DownloadMessagesAndListAttachmentsCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory,
                                                         SSLSocketFactory sslSocketFactory,
                                                         CreateServerSessionDelegate createServerSessionDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;

        downloadMessagesAndListAttachmentsOperationQueue = new NoDuplicateOperationQueue();
        downloadMessagesAndListAttachmentsOperationQueue.execute(1, "Engine-DownloadMessagesAndListAttachmentsCoordinator");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();
        awaitingServerSessionOperations = new HashMap<>();
        awaitingServerSessionOperationsLock = new ReentrantLock();

        awaitingNotificationListener = new AwaitingNotificationListener();
    }

    public void setRegisterServerPushNotificationDelegate(RegisterServerPushNotificationDelegate registerServerPushNotificationDelegate) {
        this.registerServerPushNotificationDelegate = registerServerPushNotificationDelegate;
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, awaitingNotificationListener);
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY, this);
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setProcessDownloadedMessageDelegate(ProcessDownloadedMessageDelegate processDownloadedMessageDelegate) {
        this.processDownloadedMessageDelegate = processDownloadedMessageDelegate;
    }

    public void initialQueueing() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            // no longer download messages at startup, we download after a successful push notification registration

            // retry processing messages that were downloaded but never decrypted nor marked for deletion
            InboxMessage[] unprocessedMessages = InboxMessage.getUnprocessedMessages(fetchManagerSession);
            for (InboxMessage inboxMessage : unprocessedMessages) {
                messageWasDownloaded(inboxMessage.getNetworkReceivedMessage());
            }

            // resend notifications for decrypted messages not yet marked for deletion
            InboxMessage[] decryptedInboxMessages = InboxMessage.getDecryptedMessages(fetchManagerSession);
            for (InboxMessage inboxMessage: decryptedInboxMessages) {
                messageDecrypted(inboxMessage.getOwnedIdentity(), inboxMessage.getUid());
            }

            // check if any message marked for deletion can be deleted
            InboxMessage[] markedForDeletionMessages = InboxMessage.getMarkedForDeletionMessages(fetchManagerSession);
            for (InboxMessage inboxMessage: markedForDeletionMessages) {
                if (inboxMessage.canBeDeleted()) {
                    fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeDeletedFromServer(inboxMessage.getOwnedIdentity(), inboxMessage.getUid());
                }
            }

            //delete pre key messages without contact that are more than 2 weeks old
            List<InboxMessage> expiredMessages = InboxMessage.getExpiredPendingPreKeyMessages(fetchManagerSession, System.currentTimeMillis() - Constants.PRE_KEY_INBOX_NO_CONTACT_DURATION);
            if (!expiredMessages.isEmpty()) {
                for (InboxMessage inboxMessage : expiredMessages) {
                    inboxMessage.markForDeletion();
                    for (InboxAttachment inboxAttachment: inboxMessage.getAttachments()) {
                        inboxAttachment.markForDeletion();
                    }
                    fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeDeletedFromServer(inboxMessage.getOwnedIdentity(), inboxMessage.getUid());
                }
                fetchManagerSession.session.commit();
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    private void queueNewDownloadMessagesAndListAttachmentsOperation(Identity identity, UID deviceUid) {
        DownloadMessagesAndListAttachmentsOperation op = new DownloadMessagesAndListAttachmentsOperation(fetchManagerSessionFactory, sslSocketFactory, identity, deviceUid, this, this);
        downloadMessagesAndListAttachmentsOperationQueue.queue(op);
    }

    private void scheduleNewDownloadMessagesAndListAttachmentsOperationQueueing(final Identity identity, final UID deviceUid) {
        scheduler.schedule(identity, () -> queueNewDownloadMessagesAndListAttachmentsOperation(identity, deviceUid), "DownloadMessagesAndListAttachmentsOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForServerSession(Identity identity, UID deviceUid) {
        awaitingServerSessionOperationsLock.lock();
        awaitingServerSessionOperations.put(identity, deviceUid);
        awaitingServerSessionOperationsLock.unlock();
    }

    // region implement InboxMessageListener

    @Override
    public void messageWasDownloaded(NetworkReceivedMessage networkReceivedMessage) {
        if (processDownloadedMessageDelegate == null) {
            Logger.w("A message was downloaded but no ProcessDownloadedMessageDelegate is set yet.");
            return;
        }
        this.processDownloadedMessageDelegate.processDownloadedMessage(networkReceivedMessage);
    }

    @Override
    public void messageDecrypted(Identity ownedIdentity, UID uid) {
        if (notificationPostingDelegate != null) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED_UID_KEY, uid);
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED, userInfo);
        }
    }

    // endregion

    class AwaitingNotificationListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            switch (notificationName) {
                case DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED: {
                    Object identityObject = userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY);
                    if (!(identityObject instanceof Identity)) {
                        break;
                    }
                    Identity identity = (Identity) identityObject;
                    awaitingServerSessionOperationsLock.lock();
                    UID deviceUid = awaitingServerSessionOperations.get(identity);
                    if (deviceUid != null) {
                        awaitingServerSessionOperations.remove(identity);
                        queueNewDownloadMessagesAndListAttachmentsOperation(identity, deviceUid);
                    }
                    awaitingServerSessionOperationsLock.unlock();
                }
            }
        }
    }

    @Override
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((DownloadMessagesAndListAttachmentsOperation) operation).getOwnedIdentity();
        UID deviceUid = ((DownloadMessagesAndListAttachmentsOperation) operation).getDeviceUid();
        boolean listingTruncated = ((DownloadMessagesAndListAttachmentsOperation) operation).getListingTruncated();
        scheduler.clearFailedCount(ownedIdentity);

        if (listingTruncated) {
            // if listing was truncated --> trigger a new list in 10 seconds, once messages are processed and deleted from server
            scheduler.schedule(ownedIdentity, () -> queueNewDownloadMessagesAndListAttachmentsOperation(ownedIdentity, deviceUid), "DownloadMessagesAndListAttachmentsOperation [relist]", Constants.RELIST_DELAY);
        } else {
            fetchManagerSessionFactory.markOwnedIdentityAsUpToDate(ownedIdentity);
        }

        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLLED_OWNED_IDENTITY_KEY, ownedIdentity);
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLLED_SUCCESS_KEY, true);
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLLED_TRUNCATED_KEY, listingTruncated);
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_SERVER_POLLED, userInfo);
    }

    @Override
    public void onCancelCallback(Operation operation) {
        if (operation instanceof DownloadMessagesAndListAttachmentsOperation) {
            Identity identity = ((DownloadMessagesAndListAttachmentsOperation) operation).getOwnedIdentity();
            UID deviceUid = ((DownloadMessagesAndListAttachmentsOperation) operation).getDeviceUid();
            Integer rfc = operation.getReasonForCancel();
            Logger.i("DownloadMessagesAndListAttachmentsOperation cancelled for reason " + rfc);
            if (rfc == null) {
                rfc = Operation.RFC_NULL;
            }
            switch (rfc) {
                case DownloadMessagesAndListAttachmentsOperation.RFC_INVALID_SERVER_SESSION: {
                    waitForServerSession(identity, deviceUid);
                    createServerSessionDelegate.createServerSession(identity);
                    break;
                }
                case DownloadMessagesAndListAttachmentsOperation.RFC_DEVICE_NOT_REGISTERED: {
                    if (registerServerPushNotificationDelegate != null) {
                        registerServerPushNotificationDelegate.registerServerPushNotification(identity, false);
                    } else {
                        Logger.e("Recieved a DEVICE_NOT_REGISTERED error from the server and registerServerPushNotificationDelegate was not initialized");
                    }
                    break;
                }
                case DownloadMessagesAndListAttachmentsOperation.RFC_IDENTITY_IS_INACTIVE: {
                    // no need to wait for identity to become active as registration will trigger a download
                    break;
                }
                default:
                    scheduleNewDownloadMessagesAndListAttachmentsOperationQueueing(identity, deviceUid);

                    // notify polling failed
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLLED_OWNED_IDENTITY_KEY, identity);
                    userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLLED_SUCCESS_KEY, false);
                    userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLLED_TRUNCATED_KEY, false);
                    notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_SERVER_POLLED, userInfo);
            }
        } else if (operation instanceof ProcessWebsocketReceivedMessageOperation) {
            Identity identity = ((ProcessWebsocketReceivedMessageOperation) operation).getOwnedIdentity();
            UID deviceUid = ((ProcessWebsocketReceivedMessageOperation) operation).getDeviceUid();
            Logger.i("ProcessWebsocketReceivedMessageOperation cancelled");

            // processing of websocket received message failed --> revert to download and list
            scheduleNewDownloadMessagesAndListAttachmentsOperationQueueing(identity, deviceUid);
        }
    }



    @Override
    public void downloadMessagesAndListAttachments(Identity identity, UID deviceUid) {
        queueNewDownloadMessagesAndListAttachmentsOperation(identity, deviceUid);
    }

    @Override
    public void processWebsocketDownloadedMessage(Identity identity, UID deviceUid, byte[] messagePayload) {
        ProcessWebsocketReceivedMessageOperation op = new ProcessWebsocketReceivedMessageOperation(fetchManagerSessionFactory, identity, deviceUid, messagePayload, null, this);
        downloadMessagesAndListAttachmentsOperationQueue.queue(op);
    }


    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        if (IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY.equals(notificationName)) {
            try {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_CONTACT_IDENTITY_KEY);
                boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_ACTIVE_KEY);

                if (active) {
                    ProcessPreKeyMessagesForNewContactOperation op = new ProcessPreKeyMessagesForNewContactOperation(fetchManagerSessionFactory, ownedIdentity, contactIdentity, null, null);
                    downloadMessagesAndListAttachmentsOperationQueue.queue(op);
                }
            } catch (Exception e) {
                Logger.x(e);
            }
        }
    }
}
