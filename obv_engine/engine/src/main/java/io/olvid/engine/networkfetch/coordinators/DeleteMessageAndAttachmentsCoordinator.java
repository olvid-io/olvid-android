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

package io.olvid.engine.networkfetch.coordinators;


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
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networkfetch.databases.PendingDeleteFromServer;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.operations.DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation;

public class DeleteMessageAndAttachmentsCoordinator implements Operation.OnCancelCallback, PendingDeleteFromServer.PendingDeleteFromServerListener, Operation.OnFinishCallback {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;

    private NotificationListeningDelegate notificationListeningDelegate;

    private final ExponentialBackoffRepeatingScheduler<IdentityAndUid> scheduler;
    private final NoDuplicateOperationQueue deleteMessageAndAttachmentsFromServerOperationQueue;

    private final HashMap<Identity, List<UID>> awaitingServerSessionOperations;
    private final Lock awaitingServerSessionOperationsLock;
    private final ServerSessionCreatedNotificationListener serverSessionCreatedNotificationListener;

    private boolean initialQueueingPerformed = false;
    private final Object lock = new Object();

    public DeleteMessageAndAttachmentsCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory,
                                                  SSLSocketFactory sslSocketFactory,
                                                  CreateServerSessionDelegate createServerSessionDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;

        deleteMessageAndAttachmentsFromServerOperationQueue = new NoDuplicateOperationQueue();
        deleteMessageAndAttachmentsFromServerOperationQueue.execute(1, "Engine-DeleteMessageAndAttachmentsCoordinator");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();
        awaitingServerSessionOperations = new HashMap<>();
        awaitingServerSessionOperationsLock = new ReentrantLock();

        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        serverSessionCreatedNotificationListener = new ServerSessionCreatedNotificationListener();
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, serverSessionCreatedNotificationListener);
    }

    public void initialQueueing() {
        synchronized (lock) {
            if (initialQueueingPerformed) {
                return;
            }
            try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                PendingDeleteFromServer[] pendingDeletes = PendingDeleteFromServer.getAll(fetchManagerSession);
                for (PendingDeleteFromServer pendingDelete: pendingDeletes) {
                    queueNewDeleteMessageAndAttachmentsFromServerOperation(pendingDelete.getOwnedIdentity(), pendingDelete.getMessageUid());
                }
                initialQueueingPerformed = true;
            } catch (Exception e) {
                e.printStackTrace();
                // Fail silently: an exception is supposed to occur if the CreateSessionDelegate of the sendManagerSessionFactory is not set yet.
            }
        }
    }

    private void queueNewDeleteMessageAndAttachmentsFromServerOperation(Identity ownedIdentity, UID messageUid) {
        DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation op = new DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, this, this);
        deleteMessageAndAttachmentsFromServerOperationQueue.queue(op);
    }

    private void scheduleNewDeleteMessageAndAttachmentsFromServerOperationQueueing(final Identity ownedIdentity, final UID messageUid) {
        scheduler.schedule(new IdentityAndUid(ownedIdentity, messageUid), () -> queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, messageUid), "DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForServerSession(Identity identity, UID messageUid) {
        awaitingServerSessionOperationsLock.lock();
        List<UID> list = awaitingServerSessionOperations.get(identity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingServerSessionOperations.put(identity, list);
        }
        list.add(messageUid);
        awaitingServerSessionOperationsLock.unlock();
    }


    @Override
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation) operation).getOwnedIdentity();
        UID messageUid = ((DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation) operation).getMessageUid();
        scheduler.clearFailedCount(new IdentityAndUid(ownedIdentity, messageUid));
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation) operation).getOwnedIdentity();
        UID messageUid = ((DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation) operation).getMessageUid();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation.RFC_MESSAGE_AND_ATTACHMENTS_CANNOT_BE_DELETED:
                break;
            case DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation.RFC_INVALID_SERVER_SESSION:
                if (ownedIdentity != null) {
                    waitForServerSession(ownedIdentity, messageUid);
                    createServerSessionDelegate.createServerSession(ownedIdentity);
                }
                break;
            default:
                if (ownedIdentity != null) {
                    scheduleNewDeleteMessageAndAttachmentsFromServerOperationQueueing(ownedIdentity, messageUid);
                }
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
            List<UID> messageUids = awaitingServerSessionOperations.get(ownedIdentity);
            if (messageUids != null) {
                awaitingServerSessionOperations.remove(ownedIdentity);
                for (UID messageUid: messageUids) {
                    queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, messageUid);
                }
            }
            awaitingServerSessionOperationsLock.unlock();
        }
    }


    // Notifications received from PendingDeleteFromServer database
    @Override
    public void newPendingDeleteFromServer(Identity ownedIdentity, UID messageUid) {
        queueNewDeleteMessageAndAttachmentsFromServerOperation(ownedIdentity, messageUid);
    }
}
