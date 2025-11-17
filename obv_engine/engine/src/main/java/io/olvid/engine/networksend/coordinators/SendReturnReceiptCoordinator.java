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

package io.olvid.engine.networksend.coordinators;


import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.containers.IdentityAndLong;
import io.olvid.engine.datatypes.containers.StringAndLong;
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
    private final HashMap<String, Queue<IdentityAndLong>> returnReceiptOwnedIdentityAndIdByServer;
    private final NoDuplicateOperationQueue sendReturnReceiptOperationQueue;
    private final ExponentialBackoffRepeatingScheduler<String> scheduler;

    private final HashMap<Identity, List<StringAndLong>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private final NotificationListener notificationListener;

    public SendReturnReceiptCoordinator(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, PRNGService prng) {
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.prng = prng;

        returnReceiptOwnedIdentityAndIdByServer = new HashMap<>();
        sendReturnReceiptOperationQueue = new NoDuplicateOperationQueue();

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();

        notificationListener = new NotificationListener();
    }

    public void startProcessing() {
        sendReturnReceiptOperationQueue.execute(1, "Engine-SendReturnReceiptCoordinator");
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, notificationListener);
    }

    public void initialQueueing() {
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            ReturnReceipt[] returnReceipts = ReturnReceipt.getAll(sendManagerSession);
            for (ReturnReceipt returnReceipt : returnReceipts) {
                queueNewSendReturnReceiptOperation(returnReceipt.getContactIdentity().getServer(), returnReceipt.getOwnedIdentity(), returnReceipt.getId());
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    private void queueNewSendReturnReceiptOperation(String server, Identity ownedIdentity, long returnReceiptId) {
        if (ownedIdentity != null) {
            synchronized (returnReceiptOwnedIdentityAndIdByServer) {
                Queue<IdentityAndLong> queue = returnReceiptOwnedIdentityAndIdByServer.get(server);
                if (queue == null) {
                    queue = new ArrayDeque<>();
                    returnReceiptOwnedIdentityAndIdByServer.put(server, queue);
                }
                queue.add(new IdentityAndLong(ownedIdentity, returnReceiptId));
            }
        }
        UploadReturnReceiptOperation op = new UploadReturnReceiptOperation(sendManagerSessionFactory, sslSocketFactory, server, () -> {
            List<IdentityAndLong> returnReceiptOwnedIdentityAndId = new ArrayList<>();
            synchronized (returnReceiptOwnedIdentityAndIdByServer) {
                Queue<IdentityAndLong> queue = returnReceiptOwnedIdentityAndIdByServer.get(server);
                if (queue != null && !queue.isEmpty()) {
                    do {
                        returnReceiptOwnedIdentityAndId.add(queue.remove());
                        if (returnReceiptOwnedIdentityAndId.size() == Constants.MAX_UPLOAD_RETURN_RECEIPT_BATCH_SIZE) {
                            break;
                        }
                    } while (!queue.isEmpty());
                }
            }
            return returnReceiptOwnedIdentityAndId.toArray(new IdentityAndLong[0]);
        }, this, this);
        sendReturnReceiptOperationQueue.queue(op);
    }

    private void scheduleNewSendReturnReceiptOperation(String server) {
        scheduler.schedule(server, () -> queueNewSendReturnReceiptOperation(server, null, 0), "UploadReturnReceiptOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForIdentityReactivation(Identity ownedIdentity, String server, long id) {
        awaitingIdentityReactivationOperationsLock.lock();
        List<StringAndLong> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingIdentityReactivationOperations.put(ownedIdentity, list);
        }
        list.add(new StringAndLong(server, id));
        awaitingIdentityReactivationOperationsLock.unlock();
    }


    @Override
    public void onFinishCallback(Operation operation) {
        String server = ((UploadReturnReceiptOperation) operation).getServer();
        List<IdentityAndLong> identityInactiveReturnReceiptIds = ((UploadReturnReceiptOperation) operation).getIdentityInactiveReturnReceiptIds();
        scheduler.clearFailedCount(server);

        // if there are still some messages in the queue, reschedule a batch operation
        synchronized (returnReceiptOwnedIdentityAndIdByServer) {
            Queue<IdentityAndLong> queue = returnReceiptOwnedIdentityAndIdByServer.get(server);
            if (queue != null && !queue.isEmpty()) {
                queueNewSendReturnReceiptOperation(server, null, 0);
            }
        }

        // handle message the operations couldn't because of inactive identity
        for (IdentityAndLong identityAndLong : identityInactiveReturnReceiptIds) {
            waitForIdentityReactivation(identityAndLong.identity, server, identityAndLong.lng);
        }
    }

    @Override
    public void onCancelCallback(Operation operation) {
        String server = ((UploadReturnReceiptOperation) operation).getServer();
        IdentityAndLong[] returnReceiptOwnedIdentitiesAndIds = ((UploadReturnReceiptOperation) operation).getReturnReceiptOwnedIdentitiesAndIds();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("UploadReturnReceiptOperation cancelled for reason " + rfc);

        if (returnReceiptOwnedIdentitiesAndIds != null) {
            synchronized (returnReceiptOwnedIdentityAndIdByServer) {
                Queue<IdentityAndLong> queue = returnReceiptOwnedIdentityAndIdByServer.get(server);
                if (queue == null) {
                    queue = new ArrayDeque<>();
                    returnReceiptOwnedIdentityAndIdByServer.put(server, queue);
                }
                queue.addAll(Arrays.asList(returnReceiptOwnedIdentitiesAndIds));
            }
        }
        scheduleNewSendReturnReceiptOperation(server);
    }

    // Notification received from OutboxAttachment database
    @Override
    public void newReturnReceipt(String server, Identity ownedIdentity, long returnReceiptId) {
        queueNewSendReturnReceiptOperation(server, ownedIdentity, returnReceiptId);
    }


    public interface ReturnReceiptBatchProvider {
        IdentityAndLong[] getBatchOFReturnReceiptIds();
    }

    class NotificationListener implements io.olvid.engine.datatypes.NotificationListener {
        @Override
        public void callback(String notificationName, Map<String, Object> userInfo) {
            if (IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS.equals(notificationName)) {
                boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                if (!active) {
                    return;
                }

                awaitingIdentityReactivationOperationsLock.lock();
                List<StringAndLong> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
                if (list != null) {
                    awaitingIdentityReactivationOperations.remove(ownedIdentity);
                    for (StringAndLong serverAndId: list) {
                        if (serverAndId != null) {
                            queueNewSendReturnReceiptOperation(serverAndId.string, ownedIdentity, serverAndId.lng);
                        }
                    }
                }
                awaitingIdentityReactivationOperationsLock.unlock();
            }
        }
    }
}
