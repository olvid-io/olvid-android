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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.operations.DownloadMessagesExtendedPayloadOperation;

public class DownloadMessageExtendedPayloadCoordinator implements Operation.OnCancelCallback, Operation.OnFinishCallback, InboxMessage.ExtendedPayloadListener {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;

    private final ExponentialBackoffRepeatingScheduler<Identity> scheduler;
    private final NoDuplicateOperationQueue downloadMessagesExtendedPayloadOperationQueue;

    private final AwaitingNotificationListener awaitingNotificationListener;

    private final HashMap<Identity, UID> awaitingServerSessionOperations;
    private final Lock awaitingServerSessionOperationsLock;

    private final HashMap<Identity, List<UID>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private NotificationListeningDelegate notificationListeningDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    public DownloadMessageExtendedPayloadCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, CreateServerSessionDelegate createServerSessionDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;

        downloadMessagesExtendedPayloadOperationQueue = new NoDuplicateOperationQueue();

        scheduler = new ExponentialBackoffRepeatingScheduler<>();
        awaitingNotificationListener = new AwaitingNotificationListener();

        awaitingServerSessionOperations = new HashMap<>();
        awaitingServerSessionOperationsLock = new ReentrantLock();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();
    }

    public void startProcessing() {
        downloadMessagesExtendedPayloadOperationQueue.execute(1, "Engine-DownloadMessagesExtendedPayloadCoordinator");
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, awaitingNotificationListener);
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, awaitingNotificationListener);
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }


    public void initialQueueing() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            InboxMessage[] extendedPayloadInboxMessages = InboxMessage.getExtendedPayloadMessages(fetchManagerSession);
            for (InboxMessage inboxMessage: extendedPayloadInboxMessages) {
                messageExtendedPayloadDownloaded(inboxMessage.getOwnedIdentity(), inboxMessage.getUid(), inboxMessage.getExtendedPayload());
            }

            InboxMessage[] missingExtendedPayloadInboxMessages = InboxMessage.getMissingExtendedPayloadMessages(fetchManagerSession);
            for (InboxMessage inboxMessage: missingExtendedPayloadInboxMessages) {
                messageHasExtendedPayloadToDownload(inboxMessage.getOwnedIdentity(), inboxMessage.getUid());
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    @Override
    public void messageHasExtendedPayloadToDownload(Identity ownedIdentity, UID uid) {
        queueNewDownloadMessagesExtendedPayloadOperation(ownedIdentity, uid);
    }

    @Override
    public void messageExtendedPayloadDownloaded(Identity ownedIdentity, UID uid, byte[] extendedPayload) {
        if (notificationPostingDelegate != null) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_UID_KEY, uid);
            userInfo.put(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY, extendedPayload);
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED, userInfo);
        }
    }




    private void queueNewDownloadMessagesExtendedPayloadOperation(Identity identity, UID messageUid) {
        DownloadMessagesExtendedPayloadOperation op = new DownloadMessagesExtendedPayloadOperation(fetchManagerSessionFactory, sslSocketFactory, identity, messageUid, this, this);
        downloadMessagesExtendedPayloadOperationQueue.queue(op);
    }

    private void scheduleNewDownloadMessagesExtendedPayloadOperationQueueing(final Identity identity, final UID messageUid) {
        scheduler.schedule(identity, () -> queueNewDownloadMessagesExtendedPayloadOperation(identity, messageUid), "DownloadMessagesExtendedPayloadOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForServerSession(Identity identity, UID deviceUid) {
        awaitingServerSessionOperationsLock.lock();
        awaitingServerSessionOperations.put(identity, deviceUid);
        awaitingServerSessionOperationsLock.unlock();
    }

    private void waitForIdentityReactivation(Identity ownedIdentity, UID messageUid) {
        awaitingIdentityReactivationOperationsLock.lock();
        List<UID> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingIdentityReactivationOperations.put(ownedIdentity, list);
        }
        list.add(messageUid);
        awaitingIdentityReactivationOperationsLock.unlock();
    }


    @Override
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((DownloadMessagesExtendedPayloadOperation) operation).getOwnedIdentity();
        scheduler.clearFailedCount(ownedIdentity);
    }

    @Override
    public void onCancelCallback(Operation operation) {
        if (operation instanceof DownloadMessagesExtendedPayloadOperation) {
            Identity ownedIdentity = ((DownloadMessagesExtendedPayloadOperation) operation).getOwnedIdentity();
            UID messageUid = ((DownloadMessagesExtendedPayloadOperation) operation).getMessageUid();
            Integer rfc = operation.getReasonForCancel();
            Logger.i("DownloadMessagesExtendedPayloadOperation cancelled for reason " + rfc);
            if (rfc == null) {
                rfc = Operation.RFC_NULL;
            }
            switch (rfc) {
                case DownloadMessagesExtendedPayloadOperation.RFC_EXTENDED_PAYLOAD_UNAVAILABLE_OR_INVALID: {
                    try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                        // mark the message as not having an extended payload
                        InboxMessage.clearExtendedPayload(fetchManagerSession, ownedIdentity, messageUid);
                    } catch (SQLException e) {
                        // do nothing
                    }
                    break;
                }
                case DownloadMessagesExtendedPayloadOperation.RFC_IDENTITY_IS_INACTIVE: {
                    // wait for identity to become active again
                    waitForIdentityReactivation(ownedIdentity, messageUid);
                    break;
                }
                case DownloadMessagesExtendedPayloadOperation.RFC_INVALID_SERVER_SESSION: {
                    waitForServerSession(ownedIdentity, messageUid);
                    createServerSessionDelegate.createServerSession(ownedIdentity);
                    break;
                }
                case DownloadMessagesExtendedPayloadOperation.RFC_MESSAGE_CANNOT_BE_FOUND: {
                    // message not found in Inbox, do nothing
                    break;
                }
                case DownloadMessagesExtendedPayloadOperation.RFC_NETWORK_ERROR:
                default: {
                    scheduleNewDownloadMessagesExtendedPayloadOperationQueueing(ownedIdentity, messageUid);
                    break;
                }
            }
        }
    }



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
                    UID messageUid = awaitingServerSessionOperations.get(identity);
                    if (messageUid != null) {
                        awaitingServerSessionOperations.remove(identity);
                        queueNewDownloadMessagesExtendedPayloadOperation(identity, messageUid);
                    }
                    awaitingServerSessionOperationsLock.unlock();
                    break;
                }
                case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS: {
                    boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                    if (!active) {
                        break;
                    }

                    awaitingIdentityReactivationOperationsLock.lock();
                    List<UID> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
                    if (list != null) {
                        awaitingIdentityReactivationOperations.remove(ownedIdentity);
                        for (UID messageUid: list) {
                            queueNewDownloadMessagesExtendedPayloadOperation(ownedIdentity, messageUid);
                        }
                    }
                    awaitingIdentityReactivationOperationsLock.unlock();
                    break;
                }

            }
        }
    }
}
