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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUidAndNumber;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.datatypes.notifications.UploadNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.datatypes.RefreshOutboxAttachmentSignedUrlDelegate;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;
import io.olvid.engine.networksend.operations.RefreshOutboxAttachmentSignedUrlOperation;

public class RefreshOutboxAttachmentSignedUrlCoordinator implements Operation.OnFinishCallback, Operation.OnCancelCallback, RefreshOutboxAttachmentSignedUrlDelegate {
    private final ExponentialBackoffRepeatingScheduler<IdentityAndUidAndNumber> scheduler;
    private final NoDuplicateOperationQueue refreshOutboxAttachmentSignedUrlOperationQueue;

    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private NotificationPostingDelegate notificationPostingDelegate;

    private final HashMap<Identity, List<IdentityAndUidAndNumber>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private final HashMap<IdentityAndUidAndNumber, Long> lastUrlRefreshTimestamps;

    private final NotificationListener notificationListener;


    public RefreshOutboxAttachmentSignedUrlCoordinator(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.lastUrlRefreshTimestamps = new HashMap<>();

        refreshOutboxAttachmentSignedUrlOperationQueue = new NoDuplicateOperationQueue();

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();

        notificationListener = new NotificationListener();
    }

    public void startProcessing() {
        refreshOutboxAttachmentSignedUrlOperationQueue.execute(1, "Engine-RefreshOutboxAttachmentSignedUrlCoordinator");
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        // register to NotificationCenter for NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS
        notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, notificationListener);
    }

    private void queueNewRefreshOutboxAttachmentSignedUrlOperation(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        synchronized (lastUrlRefreshTimestamps) {
            lastUrlRefreshTimestamps.put(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), System.currentTimeMillis());
        }
        RefreshOutboxAttachmentSignedUrlOperation op = new RefreshOutboxAttachmentSignedUrlOperation(sendManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, attachmentNumber, this, this);
        refreshOutboxAttachmentSignedUrlOperationQueue.queue(op);
    }

    private void scheduleNewRefreshOutboxAttachmentSignedUrlOperationQueueing(final Identity ownedIdentity, final UID messageUid, final int attachmentNumber) {
        scheduler.schedule(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), () -> queueNewRefreshOutboxAttachmentSignedUrlOperation(ownedIdentity, messageUid, attachmentNumber), "RefreshOutboxAttachmentSignedUrlOperation");
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
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((RefreshOutboxAttachmentSignedUrlOperation) operation).getOwnedIdentity();
        UID messageUid = ((RefreshOutboxAttachmentSignedUrlOperation) operation).getMessageUid();
        int attachmentNumber = ((RefreshOutboxAttachmentSignedUrlOperation) operation).getAttachmentNumber();
        scheduler.clearFailedCount(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));

        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_OWNED_IDENTITY_KEY, ownedIdentity);
        userInfo.put(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_MESSAGE_UID_KEY, messageUid);
        userInfo.put(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_ATTACHMENT_NUMBER_KEY, attachmentNumber);
        notificationPostingDelegate.postNotification(UploadNotifications.NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED, userInfo);
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((RefreshOutboxAttachmentSignedUrlOperation) operation).getOwnedIdentity();
        UID messageUid = ((RefreshOutboxAttachmentSignedUrlOperation) operation).getMessageUid();
        int attachmentNumber = ((RefreshOutboxAttachmentSignedUrlOperation) operation).getAttachmentNumber();

        Integer rfc = operation.getReasonForCancel();
        Logger.i("RefreshOutboxAttachmentSignedUrlOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case RefreshOutboxAttachmentSignedUrlOperation.RFC_ATTACHMENT_NOT_FOUND:
                // nothing to do
                break;
            case RefreshOutboxAttachmentSignedUrlOperation.RFC_IDENTITY_IS_INACTIVE:
                waitForIdentityReactivation(ownedIdentity, messageUid, attachmentNumber);
                break;
            case RefreshOutboxAttachmentSignedUrlOperation.RFC_INVALID_NONCE:
                // if the nonce is invalid, we will never be able to refresh urls and to finish uploading the attachment
                //   --> we do as if the upload was finished to delete the outboxMessage/outboxAttachment
            case RefreshOutboxAttachmentSignedUrlOperation.RFC_DELETED_FROM_SERVER: {
                try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
                    sendManagerSession.session.startTransaction();
                    OutboxAttachment attachment = OutboxAttachment.get(sendManagerSession, ownedIdentity, messageUid, attachmentNumber);
                    if (attachment != null) {
                        // Attachment no longer exists on the server. No point in continuing the upload, so simply mark the attachment as completely uploaded
                        attachment.setAcknowledgedChunkCount(attachment.getNumberOfChunks());
                    }
                    sendManagerSession.session.commit();
                } catch (SQLException e) {
                    Logger.x(e);
                }
                break;
            }
            default:
                scheduleNewRefreshOutboxAttachmentSignedUrlOperationQueueing(ownedIdentity, messageUid, attachmentNumber);
        }
    }


    @Override
    public void refreshOutboxAttachmentSignedUrl(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        synchronized (lastUrlRefreshTimestamps) {
            Long timestamp = lastUrlRefreshTimestamps.get(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));
            if (timestamp != null && System.currentTimeMillis() - timestamp < Constants.MINIMUM_URL_REFRESH_INTERVAL) {
                long delay = Constants.MINIMUM_URL_REFRESH_INTERVAL - (System.currentTimeMillis() - timestamp);
                scheduler.schedule(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), () -> queueNewRefreshOutboxAttachmentSignedUrlOperation(ownedIdentity, messageUid, attachmentNumber), "too frequent RefreshOutboxAttachmentSignedUrlOperation", delay);
            } else {
                queueNewRefreshOutboxAttachmentSignedUrlOperation(ownedIdentity, messageUid, attachmentNumber);
            }
        }
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
                        queueNewRefreshOutboxAttachmentSignedUrlOperation(params.ownedIdentity, params.uid, params.attachmentNumber);
                    }
                }
                awaitingIdentityReactivationOperationsLock.unlock();
            }
        }
    }
}
