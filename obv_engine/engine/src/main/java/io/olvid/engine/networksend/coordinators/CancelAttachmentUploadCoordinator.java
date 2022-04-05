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
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUidAndNumber;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;
import io.olvid.engine.networksend.operations.CancelAttachmentUploadCompositeOperation;

public class CancelAttachmentUploadCoordinator implements OutboxAttachment.OutboxAttachmentCancelRequestedListener, Operation.OnCancelCallback, Operation.OnFinishCallback {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;

    private NotificationListeningDelegate notificationListeningDelegate;

    private final NoDuplicateOperationQueue cancelAttachmentUploadOperationQueue;
    private final ExponentialBackoffRepeatingScheduler<IdentityAndUidAndNumber> scheduler;
    private boolean initialQueueingPerformed = false;
    private final Object lock = new Object();

    private final HashMap<Identity, List<IdentityAndUidAndNumber>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private final NotificationListener notificationListener;


    public CancelAttachmentUploadCoordinator(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;

        cancelAttachmentUploadOperationQueue = new NoDuplicateOperationQueue();
        cancelAttachmentUploadOperationQueue.execute(1, "Engine-CancelAttachmentUploadCoordinator");

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
                OutboxAttachment[] outboxAttachments = OutboxAttachment.getAllToCancel(sendManagerSession);
                for (OutboxAttachment attachment: outboxAttachments) {
                    queueNewCancelAttachmentUploadCompositeOperation(attachment.getOwnedIdentity(), attachment.getMessageUid(), attachment.getAttachmentNumber());
                }
                initialQueueingPerformed = true;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void queueNewCancelAttachmentUploadCompositeOperation(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        Logger.d("Queueing new CancelAttachmentUploadCompositeOperation " + messageUid + "-" + attachmentNumber);
        CancelAttachmentUploadCompositeOperation op = new CancelAttachmentUploadCompositeOperation(sendManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, attachmentNumber, this, this);
        cancelAttachmentUploadOperationQueue.queue(op);
    }

    private void scheduleNewCancelAttachmentUploadCompositeOperationQueueing(final Identity ownedIdentity, final UID messageUid, final int attachmentNumber) {
        scheduler.schedule(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), () -> queueNewCancelAttachmentUploadCompositeOperation(ownedIdentity, messageUid, attachmentNumber), "CancelAttachmentUploadCompositeOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForIdentityReactivation(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        awaitingIdentityReactivationOperationsLock.lock();
        List<IdentityAndUidAndNumber> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingIdentityReactivationOperations.put(ownedIdentity, list);
        }
        list.add(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
        awaitingIdentityReactivationOperationsLock.unlock();
    }


    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((CancelAttachmentUploadCompositeOperation) operation).getOwnedIdentity();
        UID messageUid = ((CancelAttachmentUploadCompositeOperation) operation).getMessageUid();
        int attachmentNumber = ((CancelAttachmentUploadCompositeOperation) operation).getAttachmentNumber();
        Integer rfc = operation.getReasonForCancel();
        Logger.w("CancelAttachmentUploadCompositeOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case CancelAttachmentUploadCompositeOperation.RFC_ATTACHMENT_NOT_FOUND_IN_DATABASE:
                // nothing to do
                break;
            case CancelAttachmentUploadCompositeOperation.RFC_IDENTITY_IS_INACTIVE:
                waitForIdentityReactivation(ownedIdentity, messageUid, attachmentNumber);
                break;
            default:
                scheduleNewCancelAttachmentUploadCompositeOperationQueueing(ownedIdentity, messageUid, attachmentNumber);
        }
    }

    @Override
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((CancelAttachmentUploadCompositeOperation) operation).getOwnedIdentity();
        UID messageUid = ((CancelAttachmentUploadCompositeOperation) operation).getMessageUid();
        int attachmentNumber = ((CancelAttachmentUploadCompositeOperation) operation).getAttachmentNumber();

        scheduler.clearFailedCount(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
    }

    // Notification received from OutboxAttachment database

    @Override
    public void outboxAttachmentCancelRequested(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        queueNewCancelAttachmentUploadCompositeOperation(ownedIdentity, messageUid, attachmentNumber);
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
                List<IdentityAndUidAndNumber> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
                if (list != null) {
                    awaitingIdentityReactivationOperations.remove(ownedIdentity);
                    for (IdentityAndUidAndNumber params: list) {
                        queueNewCancelAttachmentUploadCompositeOperation(params.ownedIdentity, params.uid, params.attachmentNumber);
                    }
                }
                awaitingIdentityReactivationOperationsLock.unlock();
            }
        }
    }
}
