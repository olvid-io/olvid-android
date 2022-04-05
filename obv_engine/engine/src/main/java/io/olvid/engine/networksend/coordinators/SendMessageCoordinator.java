/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.engine.networksend.coordinators;


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
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;
import io.olvid.engine.networksend.operations.UploadMessageCompositeOperation;

public class SendMessageCoordinator implements OutboxMessage.NewOutboxMessageListener, Operation.OnCancelCallback, Operation.OnFinishCallback {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;

    private NotificationListeningDelegate notificationListeningDelegate;

    private final OperationQueue sendMessageOperationQueue;
    private final ExponentialBackoffRepeatingScheduler<IdentityAndUid> scheduler;

    private boolean initialQueueingPerformed = false;
    private final Object lock = new Object();

    private final HashMap<Identity, List<UID>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private final NotificationListener notificationListener;


    public SendMessageCoordinator(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;

        sendMessageOperationQueue = new OperationQueue(true);
        sendMessageOperationQueue.execute(1, "Engine-SendMessageCoordinator");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();

        notificationListener = new NotificationListener();
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, notificationListener);
    }

    public void initialQueueing() {
        synchronized (lock) {
            if (initialQueueingPerformed) {
                return;
            }
            try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
                OutboxMessage[] outboxMessages = OutboxMessage.getAll(sendManagerSession);
                for (OutboxMessage outboxMessage: outboxMessages) {
                    queueNewSendMessageCompositeOperation(outboxMessage.getOwnedIdentity(), outboxMessage.getUid());
                }
                initialQueueingPerformed = true;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void queueNewSendMessageCompositeOperation(Identity ownedIdentity, UID messageUid) {
        UploadMessageCompositeOperation op = new UploadMessageCompositeOperation(sendManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, this, this);
        sendMessageOperationQueue.queue(op);
    }

    private void scheduleNewSendMessageCompositeOperationQueueing(final Identity ownedIdentity, final UID messageUid) {
        scheduler.schedule(new IdentityAndUid(ownedIdentity, messageUid), () -> queueNewSendMessageCompositeOperation(ownedIdentity, messageUid), "UploadMessageCompositeOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
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
        Identity ownedIdentity = ((UploadMessageCompositeOperation) operation).getOwnedIdentity();
        UID messageUid = ((UploadMessageCompositeOperation) operation).getMessageUid();
        scheduler.clearFailedCount(new IdentityAndUid(ownedIdentity, messageUid));
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((UploadMessageCompositeOperation) operation).getOwnedIdentity();
        UID messageUid = ((UploadMessageCompositeOperation) operation).getMessageUid();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("UploadMessageCompositeOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case UploadMessageCompositeOperation.RFC_MESSAGE_NOT_FOUND_IN_DATABASE:
                // nothing to do
                break;
            case UploadMessageCompositeOperation.RFC_IDENTITY_IS_INACTIVE:
                waitForIdentityReactivation(ownedIdentity, messageUid);
                break;
            default:
                scheduleNewSendMessageCompositeOperationQueueing(ownedIdentity, messageUid);
        }
    }
    // Notification received from OutboxMessage database

    @Override
    public void newMessageToSend(Identity ownedIdentity, UID messageUid) {
        queueNewSendMessageCompositeOperation(ownedIdentity, messageUid);
    }

    class NotificationListener implements io.olvid.engine.datatypes.NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            if (IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS.equals(notificationName)) {
                boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                if (!active) {
                    return;
                }

                awaitingIdentityReactivationOperationsLock.lock();
                List<UID> messageUids = awaitingIdentityReactivationOperations.get(ownedIdentity);
                if (messageUids != null) {
                    awaitingIdentityReactivationOperations.remove(ownedIdentity);
                    for (UID messageUid: messageUids) {
                        queueNewSendMessageCompositeOperation(ownedIdentity, messageUid);
                    }
                }
                awaitingIdentityReactivationOperationsLock.unlock();
            }
        }
    }
}
