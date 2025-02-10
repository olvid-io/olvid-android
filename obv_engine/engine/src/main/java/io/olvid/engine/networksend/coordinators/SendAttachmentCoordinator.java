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
import io.olvid.engine.datatypes.PriorityOperation;
import io.olvid.engine.datatypes.PriorityOperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUidAndNumber;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.datatypes.notifications.UploadNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.RefreshOutboxAttachmentSignedUrlDelegate;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;
import io.olvid.engine.networksend.operations.UploadAttachmentCompositeOperation;

public class SendAttachmentCoordinator implements OutboxAttachment.OutboxAttachmentCanBeSentListener, Operation.OnCancelCallback {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final RefreshOutboxAttachmentSignedUrlDelegate refreshOutboxAttachmentSignedUrlDelegate;

    private final PriorityOperationQueue sendAttachmentOperationQueue;
    private final ExponentialBackoffRepeatingScheduler<IdentityAndUidAndNumber> scheduler;

    private NotificationListeningDelegate notificationListeningDelegate;

    private final HashMap<IdentityAndUidAndNumber, Integer> fileFailedAttemptCounts;

    private final NotificationListener notificationListener;
    private final HashMap<IdentityAndUidAndNumber, AttachmentPriorityInfo> awaitingRefreshedUrlsOperations;
    private final Lock awaitingRefreshedUrlsLock;

    private final HashMap<Identity, List<AttachmentPriorityInfo>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;


    public SendAttachmentCoordinator(SendManagerSessionFactory sendManagerSessionFactory,
                                     SSLSocketFactory sslSocketFactory,
                                     RefreshOutboxAttachmentSignedUrlCoordinator refreshOutboxAttachmentSignedUrlCoordinator) {
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.refreshOutboxAttachmentSignedUrlDelegate = refreshOutboxAttachmentSignedUrlCoordinator;

        sendAttachmentOperationQueue = new PriorityOperationQueue();

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        fileFailedAttemptCounts = new HashMap<>();

        notificationListener = new NotificationListener();
        awaitingRefreshedUrlsOperations = new HashMap<>();
        awaitingRefreshedUrlsLock = new ReentrantLock();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();
    }

    public void startProcessing() {
        sendAttachmentOperationQueue.execute(4, "Engine-SendAttachmentCoordinator");
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        this.notificationListeningDelegate.addListener(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED, notificationListener);
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, notificationListener);
    }

    public void initialQueueing() {
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            OutboxMessage[] messages = OutboxMessage.getAll(sendManagerSession);
            for (OutboxMessage message : messages) {
                if (message.getUidFromServer() != null) {
                    for (OutboxAttachment attachment : message.getAttachments()) {
                        if (!attachment.isAcknowledged()) {
                            queueNewSendAttachmentCompositeOperation(attachment.getOwnedIdentity(), attachment.getMessageUid(), attachment.getAttachmentNumber(), attachment.getPriority());

                        }
                    }
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    private void queueNewSendAttachmentCompositeOperation(Identity ownedIdentity, UID messageUid, int attachmentNumber, long initialPriority) {
        Logger.d("Queueing new UploadAttachmentCompositeOperation " + messageUid + "-" + attachmentNumber + " with priority " + initialPriority);
        UploadAttachmentCompositeOperation op = new UploadAttachmentCompositeOperation(sendManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, attachmentNumber, initialPriority, this, null,this);
        sendAttachmentOperationQueue.queue(op);
        PriorityOperation lowestPriorityExecutingOperation = sendAttachmentOperationQueue.getExecutingOperationThatShouldBeCancelledWhenQueueingWithHigherPriority();
        if (lowestPriorityExecutingOperation != null && lowestPriorityExecutingOperation.getPriority() > initialPriority) {
            Logger.d("Canceling a UploadAttachmentCompositeOperation with lower priority " + lowestPriorityExecutingOperation.getPriority());
            lowestPriorityExecutingOperation.cancel(UploadAttachmentCompositeOperation.RFC_DOES_NOT_HAVE_THE_HIGHEST_PRIORITY);
        }
    }

    private void scheduleNewSendAttachmentCompositeOperationQueueing(final Identity ownedIdentity, final UID messageUid, final int attachmentNumber, final long initialPriority) {
        scheduler.schedule(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), () -> queueNewSendAttachmentCompositeOperation(ownedIdentity, messageUid, attachmentNumber, initialPriority), "UploadAttachmentCompositeOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForRefreshedUrls(Identity ownedIdentity, UID messageUid, int attachmentNumber, long initialPriority) {
        awaitingRefreshedUrlsLock.lock();
        awaitingRefreshedUrlsOperations.put(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), new AttachmentPriorityInfo(ownedIdentity, messageUid, attachmentNumber, initialPriority));
        awaitingRefreshedUrlsLock.unlock();
    }

    public void resetFailedAttemptCount(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        scheduler.clearFailedCount(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
    }

    private void waitForIdentityReactivation(Identity ownedIdentity, UID messageUid, int attachmentNumber, long initialPriority) {
        awaitingIdentityReactivationOperationsLock.lock();
        List<AttachmentPriorityInfo> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingIdentityReactivationOperations.put(ownedIdentity, list);
        }
        list.add(new AttachmentPriorityInfo(ownedIdentity, messageUid, attachmentNumber, initialPriority));
        awaitingIdentityReactivationOperationsLock.unlock();
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((UploadAttachmentCompositeOperation) operation).getOwnedIdentity();
        UID messageUid = ((UploadAttachmentCompositeOperation) operation).getMessageUid();
        int attachmentNumber = ((UploadAttachmentCompositeOperation) operation).getAttachmentNumber();
        long priority = ((UploadAttachmentCompositeOperation) operation).getPriority();
        Integer rfc = operation.getReasonForCancel();
        Logger.w("UploadAttachmentCompositeOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case UploadAttachmentCompositeOperation.RFC_ATTACHMENT_FILE_NOT_READABLE: {
                // count the number of failed attempts, cancel the attachment after 8 fails
                Integer failedCount = fileFailedAttemptCounts.get(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
                if (failedCount == null) {
                    failedCount = 0;
                }
                fileFailedAttemptCounts.put(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), failedCount + 1);

                if (failedCount >= 8) {
                    // failed 8 times, mark the attachment for deletion
                    try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
                        OutboxAttachment outboxAttachment = OutboxAttachment.get(sendManagerSession, ownedIdentity, messageUid, attachmentNumber);
                        if (outboxAttachment != null) {
                            outboxAttachment.setCancelExternallyRequested();
                            break;
                        }
                    } catch (SQLException ignored) { }
                }
                scheduleNewSendAttachmentCompositeOperationQueueing(ownedIdentity, messageUid, attachmentNumber, priority);
                break;
            }
            case UploadAttachmentCompositeOperation.RFC_INVALID_SIGNED_URL: {
                waitForRefreshedUrls(ownedIdentity, messageUid, attachmentNumber, priority);
                refreshOutboxAttachmentSignedUrlDelegate.refreshOutboxAttachmentSignedUrl(ownedIdentity, messageUid, attachmentNumber);
                break;
            }
            case UploadAttachmentCompositeOperation.RFC_ATTACHMENT_NOT_FOUND_IN_DATABASE:
            case UploadAttachmentCompositeOperation.RFC_MESSAGE_HAS_NO_UID_FROM_SERVER:
                break;
            case UploadAttachmentCompositeOperation.RFC_IDENTITY_IS_INACTIVE:
                waitForIdentityReactivation(ownedIdentity, messageUid, attachmentNumber, priority);
                break;
            case UploadAttachmentCompositeOperation.RFC_DOES_NOT_HAVE_THE_HIGHEST_PRIORITY:
                queueNewSendAttachmentCompositeOperation(ownedIdentity, messageUid, attachmentNumber, priority);
                break;
            default:
                scheduleNewSendAttachmentCompositeOperationQueueing(ownedIdentity, messageUid, attachmentNumber, priority);
        }
    }
    // Notification received from OutboxAttachment database

    @Override
    public void outboxAttachmentCanBeSent(Identity ownedIdentity, UID messageUid, int attachmentNumber, long initialPriority) {
        queueNewSendAttachmentCompositeOperation(ownedIdentity, messageUid, attachmentNumber, initialPriority);
    }

    class NotificationListener implements io.olvid.engine.datatypes.NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            switch (notificationName) {
                case UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED: {
                    Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_OWNED_IDENTITY_KEY);
                    UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_MESSAGE_UID_KEY);
                    int attachmentNumber = (int) userInfo.get(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_ATTACHMENT_NUMBER_KEY);

                    awaitingRefreshedUrlsLock.lock();
                    AttachmentPriorityInfo attachmentPriorityInfo = awaitingRefreshedUrlsOperations.get(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
                    if (attachmentPriorityInfo != null) {
                        awaitingRefreshedUrlsOperations.remove(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
                        queueNewSendAttachmentCompositeOperation(attachmentPriorityInfo.getOwnedIdentity(), attachmentPriorityInfo.getMessageUid(), attachmentPriorityInfo.getAttachmentNumber(), attachmentPriorityInfo.getInitialPriority());
                    }
                    awaitingRefreshedUrlsLock.unlock();
                    break;
                }
                case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS: {
                    boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                    if (!active) {
                        break;
                    }

                    awaitingIdentityReactivationOperationsLock.lock();
                    List<AttachmentPriorityInfo> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
                    if (list != null) {
                        awaitingIdentityReactivationOperations.remove(ownedIdentity);
                        for (AttachmentPriorityInfo params: list) {
                            queueNewSendAttachmentCompositeOperation(params.ownedIdentity, params.messageUid, params.attachmentNumber, params.initialPriority);
                        }
                    }
                    awaitingIdentityReactivationOperationsLock.unlock();
                    break;
                }
            }
        }
    }

    private static class AttachmentPriorityInfo {
        private final Identity ownedIdentity;
        private final UID messageUid;
        private final int attachmentNumber;
        private final long initialPriority;

        public AttachmentPriorityInfo(Identity ownedIdentity, UID messageUid, int attachmentNumber, long initialPriority) {
            this.ownedIdentity = ownedIdentity;
            this.messageUid = messageUid;
            this.attachmentNumber = attachmentNumber;
            this.initialPriority = initialPriority;
        }

        public Identity getOwnedIdentity() {
            return ownedIdentity;
        }

        public UID getMessageUid() {
            return messageUid;
        }

        public int getAttachmentNumber() {
            return attachmentNumber;
        }

        public long getInitialPriority() {
            return initialPriority;
        }
    }
}
