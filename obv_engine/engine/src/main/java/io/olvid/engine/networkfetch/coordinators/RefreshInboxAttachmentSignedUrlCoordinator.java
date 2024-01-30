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
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUidAndNumber;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.networkfetch.databases.PendingDeleteFromServer;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.RefreshInboxAttachmentSignedUrlDelegate;
import io.olvid.engine.networkfetch.operations.RefreshInboxAttachmentSignedUrlOperation;

public class RefreshInboxAttachmentSignedUrlCoordinator implements Operation.OnFinishCallback, Operation.OnCancelCallback, RefreshInboxAttachmentSignedUrlDelegate {
    private final ExponentialBackoffRepeatingScheduler<IdentityAndUidAndNumber> scheduler;
    private final NoDuplicateOperationQueue refreshInboxAttachmentSignedUrlOperationQueue;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private NotificationPostingDelegate notificationPostingDelegate;

    private final HashMap<Identity, List<IdentityAndUidAndNumber>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;
    private final AwaitingIdentityReactivationNotificationListener awaitingIdentityReactivationNotificationListener;

    private NotificationListeningDelegate notificationListeningDelegate;

    public RefreshInboxAttachmentSignedUrlCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;

        refreshInboxAttachmentSignedUrlOperationQueue = new NoDuplicateOperationQueue();
        refreshInboxAttachmentSignedUrlOperationQueue.execute(1, "Engine-RefreshInboxAttachmentSignedUrlCoordinator");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();
        awaitingIdentityReactivationNotificationListener = new AwaitingIdentityReactivationNotificationListener();
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_PUSH_NOTIFICATION_REGISTERED
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, awaitingIdentityReactivationNotificationListener);
    }

    private void queueNewRefreshInboxAttachmentSignedUrlOperation(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        RefreshInboxAttachmentSignedUrlOperation op = new RefreshInboxAttachmentSignedUrlOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid, attachmentNumber, this, this);
        refreshInboxAttachmentSignedUrlOperationQueue.queue(op);
    }

    private void scheduleNewRefreshInboxAttachmentSignedUrlOperationQueueing(final Identity ownedIdentity, final UID messageUid, final int attachmentNumber) {
        scheduler.schedule(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber), () -> queueNewRefreshInboxAttachmentSignedUrlOperation(ownedIdentity, messageUid, attachmentNumber), "RefreshInboxAttachmentSignedUrlOperation");
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
        Identity ownedIdentity = ((RefreshInboxAttachmentSignedUrlOperation) operation).getOwnedIdentity();
        UID messageUid = ((RefreshInboxAttachmentSignedUrlOperation) operation).getMessageUid();
        int attachmentNumber = ((RefreshInboxAttachmentSignedUrlOperation) operation).getAttachmentNumber();
        scheduler.clearFailedCount(new IdentityAndUidAndNumber(ownedIdentity, messageUid, attachmentNumber));

        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_OWNED_IDENTITY_KEY, ownedIdentity);
        userInfo.put(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_MESSAGE_UID_KEY, messageUid);
        userInfo.put(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_ATTACHMENT_NUMBER_KEY, attachmentNumber);
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED, userInfo);
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((RefreshInboxAttachmentSignedUrlOperation) operation).getOwnedIdentity();
        UID messageUid = ((RefreshInboxAttachmentSignedUrlOperation) operation).getMessageUid();
        int attachmentNumber = ((RefreshInboxAttachmentSignedUrlOperation) operation).getAttachmentNumber();

        Integer rfc = operation.getReasonForCancel();
        Logger.i("RefreshInboxAttachmentSignedUrlOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case RefreshInboxAttachmentSignedUrlOperation.RFC_ATTACHMENT_NOT_FOUND:
                break;
            case RefreshInboxAttachmentSignedUrlOperation.RFC_IDENTITY_IS_INACTIVE:
                waitForIdentityReactivation(ownedIdentity, messageUid, attachmentNumber);
                break;
            case RefreshInboxAttachmentSignedUrlOperation.RFC_DELETED_FROM_SERVER:
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
            default:
                scheduleNewRefreshInboxAttachmentSignedUrlOperationQueueing(ownedIdentity, messageUid, attachmentNumber);
        }
    }


    @Override
    public void refreshInboxAttachmentSignedUrl(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        queueNewRefreshInboxAttachmentSignedUrlOperation(ownedIdentity, messageUid, attachmentNumber);
    }

    class AwaitingIdentityReactivationNotificationListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            switch (notificationName) {
                case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS: {
                    try {
                        boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                        Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                        if (!active) {
                            return;
                        }

                        awaitingIdentityReactivationOperationsLock.lock();
                        List<IdentityAndUidAndNumber> list = awaitingIdentityReactivationOperations.get(ownedIdentity);
                        if (list != null) {
                            awaitingIdentityReactivationOperations.remove(ownedIdentity);
                            for (IdentityAndUidAndNumber params : list) {
                                queueNewRefreshInboxAttachmentSignedUrlOperation(params.ownedIdentity, params.uid, params.attachmentNumber);
                            }
                        }
                        awaitingIdentityReactivationOperationsLock.unlock();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
