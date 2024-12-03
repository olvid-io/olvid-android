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

package io.olvid.engine.networksend.coordinators;


import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.containers.StringAndBoolean;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;
import io.olvid.engine.networksend.operations.BatchUploadMessagesCompositeOperation;
import io.olvid.engine.networksend.operations.UploadMessageCompositeOperation;

public class SendMessageCoordinator implements OutboxMessage.NewOutboxMessageListener {

    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;

    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListeningDelegate notificationListeningDelegate;

    private final OperationQueue sendMessageWithAttachmentOperationQueue;
    private final ExponentialBackoffRepeatingScheduler<IdentityAndUid> scheduler;

    private final HashMap<String, Queue<IdentityAndUid>> userContentMessageUidsByServer;
    private final NoDuplicateOperationQueue batchSendUserContentMessageOperationQueue;
    private final HashMap<String, Queue<IdentityAndUid>> protocolMessageUidsByServer;
    private final NoDuplicateOperationQueue batchSendProtocolMessageOperationQueue;
    private final ExponentialBackoffRepeatingScheduler<StringAndBoolean> batchScheduler;

    private final HashMap<Identity, List<UID>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private final NotificationListener notificationListener;


    public SendMessageCoordinator(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;

        sendMessageWithAttachmentOperationQueue = new OperationQueue(true);

        scheduler = new ExponentialBackoffRepeatingScheduler<>();


        userContentMessageUidsByServer = new HashMap<>();
        batchSendUserContentMessageOperationQueue = new NoDuplicateOperationQueue();

        protocolMessageUidsByServer = new HashMap<>();
        batchSendProtocolMessageOperationQueue = new NoDuplicateOperationQueue();

        batchScheduler = new ExponentialBackoffRepeatingScheduler<>();


        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();

        notificationListener = new NotificationListener();
    }

    public void startProcessing() {
        sendMessageWithAttachmentOperationQueue.execute(1, "Engine-SendMessageCoordinator-WithAttachment");
        batchSendUserContentMessageOperationQueue.execute(1, "Engine-SendMessageCoordinator-WithUserContent");
        batchSendProtocolMessageOperationQueue.execute(1, "Engine-SendMessageCoordinator-Protocol");
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, notificationListener);
    }

    public void initialQueueing() {
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            OutboxMessage[] outboxMessages = OutboxMessage.getAll(sendManagerSession);
            for (OutboxMessage outboxMessage : outboxMessages) {
                queueNewSendMessageCompositeOperation(outboxMessage.getServer(), outboxMessage.getOwnedIdentity(), outboxMessage.getUid(), outboxMessage.getAttachments().length != 0, outboxMessage.isApplicationMessage());
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    private void queueNewSendMessageCompositeOperation(String server, Identity ownedIdentity, UID messageUid, boolean hasAttachment, boolean hasUserContent) {
        if (hasAttachment || server == null) {
            UploadMessageCompositeOperation op = new UploadMessageCompositeOperation(sendManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, this::onFinishCallbackWithAttachment, this::onCancelCallbackWithAttachment);
            sendMessageWithAttachmentOperationQueue.queue(op);
        } else if (hasUserContent) {
            if (ownedIdentity != null && messageUid != null) {
                synchronized (userContentMessageUidsByServer) {
                    Queue<IdentityAndUid> queue = userContentMessageUidsByServer.get(server);
                    if (queue == null) {
                        queue = new ArrayDeque<>();
                        userContentMessageUidsByServer.put(server, queue);
                    }
                    queue.add(new IdentityAndUid(ownedIdentity, messageUid));
                }
            }
            BatchUploadMessagesCompositeOperation op = new BatchUploadMessagesCompositeOperation(sendManagerSessionFactory, sslSocketFactory, server, true, () -> {
                List<IdentityAndUid> messageIdentitiesAndUids = new ArrayList<>();
                synchronized (userContentMessageUidsByServer) {
                    Queue<IdentityAndUid> queue = userContentMessageUidsByServer.get(server);
                    if (queue != null && !queue.isEmpty()) {
                        do {
                            messageIdentitiesAndUids.add(queue.remove());
                            if (messageIdentitiesAndUids.size() == Constants.MAX_UPLOAD_MESSAGE_BATCH_SIZE) {
                                break;
                            }
                        } while (!queue.isEmpty());
                    }
                }
                return messageIdentitiesAndUids.toArray(new IdentityAndUid[0]);
            }, this::onFinishCallbackUserContent, this::onCancelCallbackUserContent);
            batchSendUserContentMessageOperationQueue.queue(op);
        } else {
            if (ownedIdentity != null && messageUid != null) {
                synchronized (protocolMessageUidsByServer) {
                    Queue<IdentityAndUid> queue = protocolMessageUidsByServer.get(server);
                    if (queue == null) {
                        queue = new ArrayDeque<>();
                        protocolMessageUidsByServer.put(server, queue);
                    }
                    queue.add(new IdentityAndUid(ownedIdentity, messageUid));
                }
            }
            BatchUploadMessagesCompositeOperation op = new BatchUploadMessagesCompositeOperation(sendManagerSessionFactory, sslSocketFactory, server, false, () -> {
                List<IdentityAndUid> messageIdentitiesAndUids = new ArrayList<>();
                synchronized (protocolMessageUidsByServer) {
                    Queue<IdentityAndUid> queue = protocolMessageUidsByServer.get(server);
                    if (queue != null && !queue.isEmpty()) {
                        do {
                            messageIdentitiesAndUids.add(queue.remove());
                            if (messageIdentitiesAndUids.size() == Constants.MAX_UPLOAD_MESSAGE_BATCH_SIZE) {
                                break;
                            }
                        } while (!queue.isEmpty());
                    }
                }
                return messageIdentitiesAndUids.toArray(new IdentityAndUid[0]);
            }, this::onFinishCallbackProtocol, this::onCancelCallbackProtocol);
            batchSendProtocolMessageOperationQueue.queue(op);
        }
    }

    private void scheduleNewSendMessageCompositeOperationQueueing(final Identity ownedIdentity, final UID messageUid) {
        scheduler.schedule(
                new IdentityAndUid(ownedIdentity, messageUid),
                () -> queueNewSendMessageCompositeOperation(null, ownedIdentity, messageUid, true, true),
                "UploadMessageCompositeOperation");
    }

    private void scheduleNewBatchSendMessageCompositeOperationQueueing(String server, boolean hasUserContent) {
        batchScheduler.schedule(
                new StringAndBoolean(server, hasUserContent),
                () -> queueNewSendMessageCompositeOperation(server, null, null, false, hasUserContent),
                "BatchUploadMessagesCompositeOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
        batchScheduler.retryScheduledRunnables();
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



    public void onFinishCallbackProtocol(Operation operation) {
        String server = ((BatchUploadMessagesCompositeOperation) operation).getServer();
        List<IdentityAndUid> identityInactiveMessageUids = ((BatchUploadMessagesCompositeOperation) operation).getIdentityInactiveMessageUids();
        batchScheduler.clearFailedCount(new StringAndBoolean(server, false));

        // if there are still some messages in the queue, reschedule a batch operation
        synchronized (protocolMessageUidsByServer) {
            Queue<IdentityAndUid> queue = protocolMessageUidsByServer.get(server);
            if (queue != null && !queue.isEmpty()) {
                queueNewSendMessageCompositeOperation(server, null, null, false, false);
            }
        }

        // handle message the operations couldn't because of inactive identity
        for (IdentityAndUid identityAndUid : identityInactiveMessageUids) {
            waitForIdentityReactivation(identityAndUid.ownedIdentity, identityAndUid.uid);
        }
    }

    public void onCancelCallbackProtocol(Operation operation) {
        String server = ((BatchUploadMessagesCompositeOperation) operation).getServer();
        IdentityAndUid[] identityAndMessageUids = ((BatchUploadMessagesCompositeOperation) operation).getMessageIdentitiesAndUids();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("BatchUploadMessagesCompositeOperation (protocol) cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (rfc) {
            case BatchUploadMessagesCompositeOperation.RFC_BATCH_TOO_LARGE:
                if (identityAndMessageUids != null) {
                    // if the payload is too large when batching, queue each message individually
                    for (IdentityAndUid identityAndMessageUid : identityAndMessageUids) {
                        queueNewSendMessageCompositeOperation(null, identityAndMessageUid.ownedIdentity, identityAndMessageUid.uid, true, true);
                    }
                }
                break;
            default:
                // re-add all messageUids to the queue and schedule a new operation later
                if (identityAndMessageUids != null) {
                    synchronized (protocolMessageUidsByServer) {
                        Queue<IdentityAndUid> queue = protocolMessageUidsByServer.get(server);
                        if (queue == null) {
                            queue = new ArrayDeque<>();
                            protocolMessageUidsByServer.put(server, queue);
                        }
                        queue.addAll(Arrays.asList(identityAndMessageUids));
                    }
                }
                scheduleNewBatchSendMessageCompositeOperationQueueing(server, false);
        }
    }

    public void onFinishCallbackUserContent(Operation operation) {
        String server = ((BatchUploadMessagesCompositeOperation) operation).getServer();
        List<IdentityAndUid> identityInactiveMessageUids = ((BatchUploadMessagesCompositeOperation) operation).getIdentityInactiveMessageUids();
        batchScheduler.clearFailedCount(new StringAndBoolean(server, true));

        // if there are still some messages in the queue, reschedule a batch operation
        synchronized (userContentMessageUidsByServer) {
            Queue<IdentityAndUid> queue = userContentMessageUidsByServer.get(server);
            if (queue != null && !queue.isEmpty()) {
                queueNewSendMessageCompositeOperation(server, null, null, false, true);
            }
        }

        // handle message the operations couldn't because of inactive identity
        for (IdentityAndUid identityAndUid : identityInactiveMessageUids) {
            waitForIdentityReactivation(identityAndUid.ownedIdentity, identityAndUid.uid);
        }
    }

    public void onCancelCallbackUserContent(Operation operation) {
        String server = ((BatchUploadMessagesCompositeOperation) operation).getServer();
        IdentityAndUid[] identityAndMessageUids = ((BatchUploadMessagesCompositeOperation) operation).getMessageIdentitiesAndUids();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("BatchUploadMessagesCompositeOperation (user content) cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (rfc) {
            case BatchUploadMessagesCompositeOperation.RFC_BATCH_TOO_LARGE:
                if (identityAndMessageUids != null) {
                    // if the payload is too large when batching, queue each message individually
                    for (IdentityAndUid identityAndMessageUid : identityAndMessageUids) {
                        queueNewSendMessageCompositeOperation(null, identityAndMessageUid.ownedIdentity, identityAndMessageUid.uid, true, true);
                    }
                }
                break;
            default:
                // re-add all messageUids to the queue
                if (identityAndMessageUids != null) {
                    synchronized (userContentMessageUidsByServer) {
                        Queue<IdentityAndUid> queue = userContentMessageUidsByServer.get(server);
                        if (queue == null) {
                            queue = new ArrayDeque<>();
                            userContentMessageUidsByServer.put(server, queue);
                        }
                        queue.addAll(Arrays.asList(identityAndMessageUids));
                    }
                }
                scheduleNewBatchSendMessageCompositeOperationQueueing(server, true);
        }
    }

    public void onFinishCallbackWithAttachment(Operation operation) {
        Identity ownedIdentity = ((UploadMessageCompositeOperation) operation).getOwnedIdentity();
        UID messageUid = ((UploadMessageCompositeOperation) operation).getMessageUid();
        scheduler.clearFailedCount(new IdentityAndUid(ownedIdentity, messageUid));
    }

    public void onCancelCallbackWithAttachment(Operation operation) {
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
    public void newMessageToSend(String server, Identity ownedIdentity, UID messageUid, boolean hasAttachment, boolean hasUserContent) {
        queueNewSendMessageCompositeOperation(server, ownedIdentity, messageUid, hasAttachment, hasUserContent);
    }

    public interface MessageBatchProvider {
        IdentityAndUid[] getBatchOFMessageUids();
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
                        // if unsure, queue in the traditional message upload queue, even if there is no attachment
                        queueNewSendMessageCompositeOperation(null, ownedIdentity, messageUid, true, true);
                    }
                }
                awaitingIdentityReactivationOperationsLock.unlock();
            }
        }
    }
}
