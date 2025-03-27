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

package io.olvid.engine.networkfetch.coordinators;


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
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUidAndBoolean;
import io.olvid.engine.datatypes.containers.UidAndBoolean;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.MessageBatchProvider;
import io.olvid.engine.networkfetch.operations.DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation;

public class DeleteMessageAndAttachmentsCoordinator implements Operation.OnCancelCallback, InboxMessage.MarkAsListedAndDeleteOnServerListener, MessageBatchProvider, Operation.OnFinishCallback {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;


    private final ExponentialBackoffRepeatingScheduler<Identity> scheduler;
    private final HashMap<Identity, Queue<UidAndBoolean>> messageUidsToDeleteByOwnedIdentity;
    private final NoDuplicateOperationQueue deleteMessageAndAttachmentsFromServerOperationQueue;

    private final HashMap<Identity, List<IdentityAndUidAndBoolean>> awaitingServerSessionOperations;
    private final Lock awaitingServerSessionOperationsLock;
    private final ServerSessionCreatedNotificationListener serverSessionCreatedNotificationListener;


    public DeleteMessageAndAttachmentsCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory,
                                                  SSLSocketFactory sslSocketFactory,
                                                  CreateServerSessionDelegate createServerSessionDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;

        deleteMessageAndAttachmentsFromServerOperationQueue = new NoDuplicateOperationQueue();

        messageUidsToDeleteByOwnedIdentity = new HashMap<>();

        scheduler = new ExponentialBackoffRepeatingScheduler<>();
        awaitingServerSessionOperations = new HashMap<>();
        awaitingServerSessionOperationsLock = new ReentrantLock();

        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        serverSessionCreatedNotificationListener = new ServerSessionCreatedNotificationListener();
    }


    public void startProcessing() {
        deleteMessageAndAttachmentsFromServerOperationQueue.execute(1, "Engine-DeleteMessageAndAttachmentsCoordinator");
    }


    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, serverSessionCreatedNotificationListener);
    }


    private void queueNewDeleteMessageAndAttachmentsFromServerOperation(Identity ownedIdentity, UID messageUid, boolean markAsListed) {
        if (messageUid != null) {
            synchronized (messageUidsToDeleteByOwnedIdentity) {
                Queue<UidAndBoolean> queue = messageUidsToDeleteByOwnedIdentity.get(ownedIdentity);
                if (queue == null) {
                    queue = new ArrayDeque<>();
                    messageUidsToDeleteByOwnedIdentity.put(ownedIdentity, queue);
                }
                queue.add(new UidAndBoolean(messageUid, markAsListed));
            }
        }
        DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation op = new DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, this, this, this);
        deleteMessageAndAttachmentsFromServerOperationQueue.queue(op);
    }

    @Override
    public UidAndBoolean[] getBatchOFMessageUids(Identity ownedIdentity) {
        List<UidAndBoolean> messageUidsAndMarkAsListed = new ArrayList<>();
        synchronized (messageUidsToDeleteByOwnedIdentity) {
            Queue<UidAndBoolean> queue = messageUidsToDeleteByOwnedIdentity.get(ownedIdentity);
            if (queue != null && !queue.isEmpty()) {
                do {
                    messageUidsAndMarkAsListed.add(queue.remove());
                    if (messageUidsAndMarkAsListed.size() == Constants.MAX_DELETE_MESSAGE_ON_SERVER_BATCH_SIZE) {
                        break;
                    }
                } while (!queue.isEmpty());
            }
        }
        return messageUidsAndMarkAsListed.toArray(new UidAndBoolean[0]);
    }

    private void scheduleNewDeleteMessageAndAttachmentsFromServerOperationQueueing(final Identity ownedIdentity) {
        scheduler.schedule(ownedIdentity, () -> queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, null, false), "DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForServerSession(Identity ownedIdentity, UID messageUid, boolean markAsListed) {
        awaitingServerSessionOperationsLock.lock();
        List<IdentityAndUidAndBoolean> list = awaitingServerSessionOperations.get(ownedIdentity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingServerSessionOperations.put(ownedIdentity, list);
        }
        list.add(new IdentityAndUidAndBoolean(ownedIdentity, messageUid, markAsListed));
        awaitingServerSessionOperationsLock.unlock();
    }


    @Override
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation) operation).getOwnedIdentity();
        scheduler.clearFailedCount(ownedIdentity);

        // if there are still some messages in the queue, reschedule a batch operation
        synchronized (messageUidsToDeleteByOwnedIdentity) {
            Queue<UidAndBoolean> queue = messageUidsToDeleteByOwnedIdentity.get(ownedIdentity);
            if (queue != null && !queue.isEmpty()) {
                queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, null, false);
            }
        }
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation) operation).getOwnedIdentity();
        UidAndBoolean[] messageUidsAndMarkAsListed = ((DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation) operation).getMessageUidsAndMarkAsListed();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (rfc) {
            case DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation.RFC_INVALID_SERVER_SESSION:
                if (messageUidsAndMarkAsListed != null) {
                    for (UidAndBoolean uidAndBoolean : messageUidsAndMarkAsListed) {
                        waitForServerSession(ownedIdentity, uidAndBoolean.uid, uidAndBoolean.bool);
                    }
                }
                createServerSessionDelegate.createServerSession(ownedIdentity);
                break;
            default:
                // re-add all messageUids to the queue
                if (messageUidsAndMarkAsListed != null) {
                    synchronized (messageUidsToDeleteByOwnedIdentity) {
                        Queue<UidAndBoolean> queue = messageUidsToDeleteByOwnedIdentity.get(ownedIdentity);
                        if (queue == null) {
                            queue = new ArrayDeque<>();
                            messageUidsToDeleteByOwnedIdentity.put(ownedIdentity, queue);
                        }
                        queue.addAll(Arrays.asList(messageUidsAndMarkAsListed));
                    }
                }
                scheduleNewDeleteMessageAndAttachmentsFromServerOperationQueueing(ownedIdentity);
        }
    }

    class ServerSessionCreatedNotificationListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            if (!notificationName.equals(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED)) {
                return;
            }
            Object identityObject = userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY);
            if (!(identityObject instanceof Identity)) {
                return;
            }
            Identity ownedIdentity = (Identity) identityObject;
            awaitingServerSessionOperationsLock.lock();
            List<IdentityAndUidAndBoolean> messageUids = awaitingServerSessionOperations.get(ownedIdentity);
            if (messageUids != null) {
                awaitingServerSessionOperations.remove(ownedIdentity);
                for (IdentityAndUidAndBoolean triple: messageUids) {
                    queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, triple.uid, triple.bool);
                }
            }
            awaitingServerSessionOperationsLock.unlock();
        }
    }


    // Notifications received from MessageInbox when the message and its attachments can be deleted
    @Override
    public void messageCanBeDeletedFromServer(Identity ownedIdentity, UID messageUid) {
        queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, messageUid, false);
    }

    // Notifications received from MessageInbox when the payload is set and there are some attachments
    @Override
    public void messageCanBeMarkedAsListedOnServer(Identity ownedIdentity, UID messageUid) {
        queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, messageUid, true);
    }

}
