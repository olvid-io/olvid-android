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
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.PendingDeleteFromServer;
import io.olvid.engine.networkfetch.datatypes.DownloadAttachmentPriorityCategory;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.RefreshInboxAttachmentSignedUrlDelegate;
import io.olvid.engine.networkfetch.operations.DownloadAttachmentOperation;

public class DownloadAttachmentCoordinator implements InboxAttachment.InboxAttachmentListener, Operation.OnCancelCallback {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final RefreshInboxAttachmentSignedUrlDelegate refreshInboxAttachmentSignedUrlDelegate;

    private NotificationListeningDelegate notificationListeningDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    private final ExponentialBackoffRepeatingScheduler<IdentityAndUidAndNumber> scheduler;
    private final PriorityOperationQueue downloadAttachmentOperationWeightQueue; // intended for download of small attachments
    private final PriorityOperationQueue downloadAttachmentOperationTimestampQueue; // intended for download of large attachments

    private final NotificationListener notificationListener;

    private final HashMap<IdentityAndUidAndNumber, AttachmentPriorityInfo> awaitingRefreshedUrlsOperations;
    private final Lock awaitingRefreshedUrlsLock;

    private final HashMap<Identity, List<AttachmentPriorityInfo>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;


    public DownloadAttachmentCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory,
                                         SSLSocketFactory sslSocketFactory,
                                         RefreshInboxAttachmentSignedUrlDelegate refreshInboxAttachmentSignedUrlDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.refreshInboxAttachmentSignedUrlDelegate = refreshInboxAttachmentSignedUrlDelegate;

        downloadAttachmentOperationWeightQueue = new PriorityOperationQueue();
        downloadAttachmentOperationWeightQueue.execute(4, "Engine-DownloadAttachmentCoordinator-weight");

        downloadAttachmentOperationTimestampQueue = new PriorityOperationQueue();
        downloadAttachmentOperationTimestampQueue.execute(4, "Engine-DownloadAttachmentCoordinator-timestamp");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        notificationListener = new NotificationListener();
        awaitingRefreshedUrlsOperations = new HashMap<>();
        awaitingRefreshedUrlsLock = new ReentrantLock();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED, notificationListener);
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, notificationListener);
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void initialQueueing() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            InboxAttachment[] attachmentsToResume = InboxAttachment.getAllAttachmentsToResume(fetchManagerSession);
            for (InboxAttachment inboxAttachment: attachmentsToResume) {
                queueNewDownloadAttachmentOperation(inboxAttachment.getOwnedIdentity(), inboxAttachment.getMessageUid(), inboxAttachment.getAttachmentNumber(), inboxAttachment.getPriorityCategory(), inboxAttachment.getPriority());
            }
            fetchManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void queueNewDownloadAttachmentOperation(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory, long initialPriority) {
        Logger.d("Download attachment coordinator queueing new DownloadAttachmentOperation.");
        DownloadAttachmentOperation op = new DownloadAttachmentOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority, this,null, this);
        switch (priorityCategory) {
            case DownloadAttachmentPriorityCategory.WEIGHT:
                downloadAttachmentOperationWeightQueue.queue(op);
                PriorityOperation lowestPriorityExecutingOperation = downloadAttachmentOperationWeightQueue.getExecutingOperationThatShouldBeCancelledWhenQueueingWithHigherPriority();
                if (lowestPriorityExecutingOperation != null && lowestPriorityExecutingOperation.getPriority() > initialPriority) {
                    Logger.d("Canceling a DownloadAttachmentOperation with lower priority " + lowestPriorityExecutingOperation.getPriority());
                    lowestPriorityExecutingOperation.cancel(DownloadAttachmentOperation.RFC_DOES_NOT_HAVE_THE_HIGHEST_PRIORITY);
                }
                break;
            case DownloadAttachmentPriorityCategory.TIMESTAMP:
                downloadAttachmentOperationTimestampQueue.queue(op);
                lowestPriorityExecutingOperation = downloadAttachmentOperationTimestampQueue.getExecutingOperationThatShouldBeCancelledWhenQueueingWithHigherPriority();
                if (lowestPriorityExecutingOperation != null && lowestPriorityExecutingOperation.getPriority() > initialPriority) {
                    Logger.d("Canceling a DownloadAttachmentOperation with lower priority " + lowestPriorityExecutingOperation.getPriority());
                    lowestPriorityExecutingOperation.cancel(DownloadAttachmentOperation.RFC_DOES_NOT_HAVE_THE_HIGHEST_PRIORITY);
                }
                break;
            default:
                Logger.w("Trying to queue a DownloadAttachmentOperation with unknown priorityCategory " + priorityCategory);
        }
    }

    private void scheduleNewDownloadAttachmentOperationQueueing(final Identity ownedIdentity, final UID messageUid, final int attachmentNumber, final int priorityCategory, final long initialPriority) {
        scheduler.schedule(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), () -> queueNewDownloadAttachmentOperation(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority), "DownloadAttachmentOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    private void waitForRefreshedUrls(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory, long initialPriority) {
        awaitingRefreshedUrlsLock.lock();
        awaitingRefreshedUrlsOperations.put(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), new AttachmentPriorityInfo(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority));
        awaitingRefreshedUrlsLock.unlock();
    }

    private void waitForIdentityReactivation(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory, long initialPriority) {
        awaitingIdentityReactivationOperationsLock.lock();
        List<AttachmentPriorityInfo> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingIdentityReactivationOperations.put(ownedIdentity, list);
        }
        list.add(new AttachmentPriorityInfo(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority));
        awaitingIdentityReactivationOperationsLock.unlock();
    }

    public void resetFailedAttemptCount(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        scheduler.clearFailedCount(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((DownloadAttachmentOperation) operation).getOwnedIdentity();
        UID messageUid = ((DownloadAttachmentOperation) operation).getMessageUid();
        int attachmentNumber = ((DownloadAttachmentOperation) operation).getAttachmentNumber();
        int priorityCategory = ((DownloadAttachmentOperation) operation).getPriorityCategory();
        long initialPriority = ((DownloadAttachmentOperation) operation).getPriority();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("DownloadAttachmentOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case DownloadAttachmentOperation.RFC_DECRYPTION_ERROR:
            case DownloadAttachmentOperation.RFC_INVALID_CHUNK:
            case DownloadAttachmentOperation.RFC_ATTACHMENT_CANNOT_BE_FETCHED:
            case DownloadAttachmentOperation.RFC_UNABLE_TO_WRITE_CHUNK_TO_FILE:
            case DownloadAttachmentOperation.RFC_UPLOAD_CANCELLED_BY_SENDER:
                // We do not try to download the attachment again and mark it for deletion. We notify that the downloadAttachment failed.
                try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                    InboxAttachment attachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
                    if (attachment != null) {
                        fetchManagerSession.session.startTransaction();
                        attachment.markForDeletion();
                        if (attachment.getMessage().canBeDeleted()) {
                            PendingDeleteFromServer.create(fetchManagerSession, ownedIdentity, messageUid);
                        }
                        fetchManagerSession.session.commit();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_OWNED_IDENTITY_KEY, ownedIdentity);
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_UID_KEY, messageUid);
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY, attachmentNumber);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED, userInfo);
                break;
            case DownloadAttachmentOperation.RFC_ATTACHMENT_CANNOT_BE_FOUND:
            case DownloadAttachmentOperation.RFC_FETCH_NOT_REQUESTED:
            case DownloadAttachmentOperation.RFC_MARKED_FOR_DELETION:
                // nothing to do
                break;
            case DownloadAttachmentOperation.RFC_IDENTITY_IS_INACTIVE:
                // wait for identity to become active again
                waitForIdentityReactivation(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority);
                break;
            case DownloadAttachmentOperation.RFC_DOES_NOT_HAVE_THE_HIGHEST_PRIORITY:
                queueNewDownloadAttachmentOperation(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority);

                userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_OWNED_IDENTITY_KEY, ownedIdentity);
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_MESSAGE_UID_KEY, messageUid);
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_ATTACHMENT_NUMBER, attachmentNumber);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED, userInfo);
                break;
            case DownloadAttachmentOperation.RFC_INVALID_SIGNED_URL: {
                waitForRefreshedUrls(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority);
                refreshInboxAttachmentSignedUrlDelegate.refreshInboxAttachmentSignedUrl(ownedIdentity, messageUid, attachmentNumber);
                break;
            }
            case DownloadAttachmentOperation.RFC_DOWNLOAD_PAUSED:
                userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_OWNED_IDENTITY_KEY, ownedIdentity);
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_MESSAGE_UID_KEY, messageUid);
                userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_ATTACHMENT_NUMBER, attachmentNumber);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED, userInfo);
                break;
            case DownloadAttachmentOperation.RFC_NOT_YET_AVAILABLE_ON_SERVER:
            case DownloadAttachmentOperation.RFC_NETWORK_ERROR:
            default:
                scheduleNewDownloadAttachmentOperationQueueing(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority);
        }
    }

    class NotificationListener implements io.olvid.engine.datatypes.NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            switch (notificationName) {
                case DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED: {
                    Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_OWNED_IDENTITY_KEY);
                    UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_MESSAGE_UID_KEY);
                    int attachmentNumber = (int) userInfo.get(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_ATTACHMENT_NUMBER_KEY);
                    awaitingRefreshedUrlsLock.lock();
                    AttachmentPriorityInfo attachmentPriorityInfo = awaitingRefreshedUrlsOperations.get(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
                    if (attachmentPriorityInfo != null) {
                        awaitingRefreshedUrlsOperations.remove(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
                        queueNewDownloadAttachmentOperation(ownedIdentity, messageUid, attachmentNumber, attachmentPriorityInfo.getPriorityCategory(), attachmentPriorityInfo.getInitialPriority());
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
                            queueNewDownloadAttachmentOperation(params.ownedIdentity, params.messageUid, params.attachmentNumber, params.priorityCategory, params.initialPriority);
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
        private final int priorityCategory;
        private final long initialPriority;

        public AttachmentPriorityInfo(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory, long initialPriority) {
            this.ownedIdentity = ownedIdentity;
            this.messageUid = messageUid;
            this.attachmentNumber = attachmentNumber;
            this.priorityCategory = priorityCategory;
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

        public int getPriorityCategory() {
            return priorityCategory;
        }

        public long getInitialPriority() {
            return initialPriority;
        }
    }

    @Override
    public void attachmentDownloadProgressed(Identity ownedIdentity, UID messageUid, int attachmentNumber, float progress) {
        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_OWNED_IDENTITY_KEY, ownedIdentity);
        userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_UID_KEY, messageUid);
        userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
        userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY, progress);
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS, userInfo);
    }

    @Override
    public void attachmentDownloadFinished(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        // Warning, this method is also called by the manager when resendAllDownloadedAttachmentNotifications is called
        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_OWNED_IDENTITY_KEY, ownedIdentity);
        userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_MESSAGE_UID_KEY, messageUid);
        userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_ATTACHMENT_NUMBER_KEY, attachmentNumber);
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED, userInfo);
    }

    @Override
    public void attachmentDownloadWasRequested(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory, long initialPriority) {
        queueNewDownloadAttachmentOperation(ownedIdentity, messageUid, attachmentNumber, priorityCategory, initialPriority);
    }
}
