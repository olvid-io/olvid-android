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
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networksend.databases.ReturnReceipt;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;
import io.olvid.engine.networksend.operations.UploadReturnReceiptOperation;

public class SendReturnReceiptCoordinator implements ReturnReceipt.NewReturnReceiptListener, Operation.OnCancelCallback, Operation.OnFinishCallback {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private NotificationListeningDelegate notificationListeningDelegate;

    private final PRNGService prng;
    private final NoDuplicateOperationQueue sendReturnReceiptOperationQueue;
    private final ExponentialBackoffRepeatingScheduler<Long> scheduler;
    private boolean initialQueueingPerformed = false;
    private final Object lock = new Object();

    private final HashMap<Identity, List<Long>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private final NotificationListener notificationListener;

    public SendReturnReceiptCoordinator(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, PRNGService prng) {
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.prng = prng;

        sendReturnReceiptOperationQueue = new NoDuplicateOperationQueue();
        sendReturnReceiptOperationQueue.execute(1, "Engine-SendReturnReceiptCoordinator");

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
                ReturnReceipt[] returnReceipts = ReturnReceipt.getAll(sendManagerSession);
                for (ReturnReceipt returnReceipt: returnReceipts) {
                    queueNewSendReturnReceiptOperation(returnReceipt.getOwnedIdentity(), returnReceipt.getId());
                }
                initialQueueingPerformed = true;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void queueNewSendReturnReceiptOperation(Identity ownedIdentity, long returnReceiptId) {
        Logger.d("Queueing new UploadReturnReceiptOperation " + returnReceiptId);
        UploadReturnReceiptOperation op = new UploadReturnReceiptOperation(sendManagerSessionFactory, sslSocketFactory, ownedIdentity, returnReceiptId, prng, this, this);
        sendReturnReceiptOperationQueue.queue(op);
    }

    private void scheduleNewSendReturnReceiptOperation(final Identity ownedIdentity, final long returnReceiptId) {
        scheduler.schedule(returnReceiptId, () -> queueNewSendReturnReceiptOperation(ownedIdentity, returnReceiptId), "UploadReturnReceiptOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForIdentityReactivation(Identity ownedIdentity, long id) {
        awaitingIdentityReactivationOperationsLock.lock();
        List<Long> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingIdentityReactivationOperations.put(ownedIdentity, list);
        }
        list.add(id);
        awaitingIdentityReactivationOperationsLock.unlock();
    }


    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((UploadReturnReceiptOperation) operation).getOwnedIdentity();
        long id = ((UploadReturnReceiptOperation) operation).getReturnReceiptId();
        Integer rfc = operation.getReasonForCancel();
        Logger.w("UploadReturnReceiptOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case UploadReturnReceiptOperation.RFC_RETURN_RECEIPT_NOT_FOUND:
                // nothing to do
                break;
            case UploadReturnReceiptOperation.RFC_IDENTITY_IS_INACTIVE:
                waitForIdentityReactivation(ownedIdentity, id);
                break;
            default:
                scheduleNewSendReturnReceiptOperation(ownedIdentity, id);
        }
    }

    @Override
    public void onFinishCallback(Operation operation) {
        Logger.d("UploadReturnReceipt finished");
        long returnReceiptId = ((UploadReturnReceiptOperation) operation).getReturnReceiptId();
        scheduler.clearFailedCount(returnReceiptId);
    }

    // Notification received from OutboxAttachment database

    @Override
    public void newReturnReceipt(Identity ownedIdentity, long returnReceiptId) {
        queueNewSendReturnReceiptOperation(ownedIdentity, returnReceiptId);
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
                List<Long> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
                if (list != null) {
                    awaitingIdentityReactivationOperations.remove(ownedIdentity);
                    for (Long id: list) {
                        if (id != null) {
                            queueNewSendReturnReceiptOperation(ownedIdentity, id);
                        }
                    }
                }
                awaitingIdentityReactivationOperationsLock.unlock();
            }
        }
    }
}
