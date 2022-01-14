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

package io.olvid.engine.networkfetch.coordinators;


import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.SolveChallengeDelegate;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.operations.CreateServerSessionCompositeOperation;
import io.olvid.engine.networkfetch.operations.QueryApiKeyStatusOperation;

public class CreateServerSessionCoordinator implements Operation.OnFinishCallback, Operation.OnCancelCallback, CreateServerSessionDelegate {
    private final ExponentialBackoffRepeatingScheduler<Identity> scheduler;
    private final NoDuplicateOperationQueue createServerSessionOperationQueue;
    private final OperationQueue queryApiKeyStatusOperationQueue;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private SolveChallengeDelegate solveChallengeDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    public CreateServerSessionCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.solveChallengeDelegate = null;

        scheduler = new ExponentialBackoffRepeatingScheduler<>();
        createServerSessionOperationQueue = new NoDuplicateOperationQueue();
        createServerSessionOperationQueue.execute(1, "Engine-CreateServerSessionCoordinator");

        queryApiKeyStatusOperationQueue = new OperationQueue(true);
        queryApiKeyStatusOperationQueue.execute(1, "Engine-CreateServerSessionCoordinator-QueryApiKeyStatus");
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setSolveChallengeDelegate(SolveChallengeDelegate solveChallengeDelegate) {
        this.solveChallengeDelegate = solveChallengeDelegate;
    }

    public void initialQueueing() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            for (ServerSession serverSession: ServerSession.getAll(fetchManagerSession)) {
                if (!fetchManagerSession.identityDelegate.isOwnedIdentity(fetchManagerSession.session, serverSession.getOwnedIdentity())) {
                    // owned identity does not exist --> delete the session
                    serverSession.delete();
                } else {
                    // post notification of apiKey status
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY, serverSession.getOwnedIdentity());
                    userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_STATUS_KEY, serverSession.getApiKeyStatus());
                    userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_PERMISSIONS_KEY, serverSession.getPermissions());
                    userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_EXPIRATION_TIMESTAMP_KEY, serverSession.getApiKeyExpirationTimestamp());
                    notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, userInfo);
                }
            }
            fetchManagerSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void queueNewQueryApiKeyStatusOperation(final Identity ownedIdentity, final UUID apiKey) {
        QueryApiKeyStatusOperation op = new QueryApiKeyStatusOperation(sslSocketFactory, ownedIdentity, apiKey, (Operation operation) -> {
            if (notificationPostingDelegate == null) {
                Logger.e("NotificationPostingDelegate not set onFinishCallback of QueryApiKeyStatusOperation.");
                return;
            }
            ServerSession.ApiKeyStatus apiKeyStatus = ((QueryApiKeyStatusOperation) operation).getApiKeyStatus();
            List<ServerSession.Permission> permissions = ((QueryApiKeyStatusOperation) operation).getPermissions();
            long apiKeyExpirationTimestamp = ((QueryApiKeyStatusOperation) operation).getApiKeyExpirationTimestamp();

            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY, apiKey);
            userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, apiKeyStatus);
            userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY, permissions);
            userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY, apiKeyExpirationTimestamp);
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS, userInfo);

        }, operation -> {
            if (notificationPostingDelegate == null) {
                Logger.e("NotificationPostingDelegate not set onCancelCallback of QueryApiKeyStatusOperation.");
                return;
            }
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY, apiKey );
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED, userInfo);
        });
        queryApiKeyStatusOperationQueue.queue(op);
    }

    private void queueNewCreateServerSessionCompositeOperation(Identity ownedIdentity) {
        if (solveChallengeDelegate == null) {
            Logger.e("The SolveChallengeDelegate is not set in the CreateServerSessionCoordinator. Unable to queue a new CreateServerSessionCompositeOperation.");
            return;
        }
        CreateServerSessionCompositeOperation op = new CreateServerSessionCompositeOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, solveChallengeDelegate, this, this);
        createServerSessionOperationQueue.queue(op);
    }

    private void scheduleNewCreateServerSessionCompositeOperationQueueing(final Identity ownedIdentity) {
        scheduler.schedule(ownedIdentity, () -> queueNewCreateServerSessionCompositeOperation(ownedIdentity), "CreateServerSessionCompositeOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    @Override
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((CreateServerSessionCompositeOperation) operation).getOwnedIdentity();
        ServerSession.ApiKeyStatus apiKeyStatus = ((CreateServerSessionCompositeOperation) operation).getApiKeyStatus();
        List<ServerSession.Permission> permissions = ((CreateServerSessionCompositeOperation) operation).getPermissions();
        long apiKeyExpirationTimestamp = ((CreateServerSessionCompositeOperation) operation).getApiKeyExpirationTimestamp();

        scheduler.clearFailedCount(ownedIdentity);
        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY, ownedIdentity);
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_STATUS_KEY, apiKeyStatus);
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_PERMISSIONS_KEY, permissions);
        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_EXPIRATION_TIMESTAMP_KEY, apiKeyExpirationTimestamp);
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, userInfo);
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((CreateServerSessionCompositeOperation) operation).getOwnedIdentity();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("CreateServerSessionCompositeOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case CreateServerSessionCompositeOperation.RFC_SESSION_CANNOT_BE_FOUND:
                queueNewCreateServerSessionCompositeOperation(ownedIdentity);
                break;
            case CreateServerSessionCompositeOperation.RFC_IDENTITY_NOT_FOUND:
                break;
//            case CreateServerSessionCompositeOperation.RFC_API_KEY_REJECTED: {
//                HashMap<String, Object> userInfo = new HashMap<>();
//                userInfo.put(DownloadNotifications.NOTIFICATION_API_KEY_REJECTED_BY_SERVER_IDENTITY_KEY, ownedIdentity);
//                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_API_KEY_REJECTED_BY_SERVER, userInfo);
//                scheduleNewCreateServerSessionCompositeOperationQueueing(ownedIdentity);
//                break;
//            }
            case CreateServerSessionCompositeOperation.RFC_INVALID_SESSION:
            default:
                scheduleNewCreateServerSessionCompositeOperationQueueing(ownedIdentity);
        }
    }


    @Override
    public void createServerSession(Identity ownedIdentity) {
        queueNewCreateServerSessionCompositeOperation(ownedIdentity);
    }

}
