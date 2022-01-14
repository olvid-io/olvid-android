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
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.WellKnownCacheDelegate;
import io.olvid.engine.networkfetch.operations.GetTurnCredentialsOperation;

public class GetTurnCredentialsCoordinator implements Operation.OnFinishCallback, Operation.OnCancelCallback {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final WellKnownCacheDelegate wellKnownCacheDelegate;
    private final CreateServerSessionDelegate createServerSessionDelegate;
    private final OperationQueue getTurnCredentialsOperationQueue;
    private NotificationPostingDelegate notificationPostingDelegate;

    public GetTurnCredentialsCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, CreateServerSessionDelegate createServerSessionDelegate, WellKnownCacheDelegate wellKnownCacheDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;
        this.wellKnownCacheDelegate = wellKnownCacheDelegate;

        getTurnCredentialsOperationQueue = new OperationQueue(true);
        getTurnCredentialsOperationQueue.execute(1, "Engine-GetTurnCredentialsCoordinator");
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    private void queueNewGetTurnCredentialsOperation(Identity ownedIdentity, UUID callUuid, String username1, String username2) {
        GetTurnCredentialsOperation op = new GetTurnCredentialsOperation(fetchManagerSessionFactory, sslSocketFactory, wellKnownCacheDelegate, ownedIdentity, callUuid, username1, username2, this, this);
        getTurnCredentialsOperationQueue.queue(op);
    }

    public void getTurnCredentials(Identity ownedIdentity, UUID callUuid, String username1, String username2) {
        queueNewGetTurnCredentialsOperation(ownedIdentity, callUuid, username1, username2);
    }


    @Override
    public void onFinishCallback(Operation operation) {
        if (!(operation instanceof GetTurnCredentialsOperation)) {
            return;
        }
        GetTurnCredentialsOperation getTurnCredentialsOperation = (GetTurnCredentialsOperation) operation;

        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY, getTurnCredentialsOperation.getOwnedIdentity());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY, getTurnCredentialsOperation.getCallUuid());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY, getTurnCredentialsOperation.getExpiringUsername1());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY, getTurnCredentialsOperation.getPassword1());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY, getTurnCredentialsOperation.getExpiringUsername2());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY, getTurnCredentialsOperation.getPassword2());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_SERVERS_KEY, getTurnCredentialsOperation.getTurnServers());

        if (notificationPostingDelegate != null) {
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED, userInfo);
        }
    }

    @Override
    public void onCancelCallback(Operation operation) {
        if (!(operation instanceof GetTurnCredentialsOperation)) {
            return;
        }
        GetTurnCredentialsOperation getTurnCredentialsOperation = (GetTurnCredentialsOperation) operation;
        Integer rfc = getTurnCredentialsOperation.getReasonForCancel();
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        final DownloadNotifications.TurnCredentialsFailedReason failedReason;
        switch (rfc) {
            case GetTurnCredentialsOperation.RFC_INVALID_SERVER_SESSION:
                createServerSessionDelegate.createServerSession(getTurnCredentialsOperation.getOwnedIdentity());
                failedReason = DownloadNotifications.TurnCredentialsFailedReason.BAD_SERVER_SESSION;
                break;
            case GetTurnCredentialsOperation.RFC_PERMISSION_DENIED:
                failedReason = DownloadNotifications.TurnCredentialsFailedReason.PERMISSION_DENIED;
                break;
            case GetTurnCredentialsOperation.RFC_SERVER_DOES_NOT_SUPPORT_CALLS:
                failedReason = DownloadNotifications.TurnCredentialsFailedReason.CALLS_NOT_SUPPORTED_ON_SERVER;
                break;
            case GetTurnCredentialsOperation.RFC_WELL_KNOWN_NOT_CACHED:
            case GetTurnCredentialsOperation.RFC_NULL:
            default:
                failedReason = DownloadNotifications.TurnCredentialsFailedReason.UNABLE_TO_CONTACT_SERVER;
                break;
        }
        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY, getTurnCredentialsOperation.getOwnedIdentity());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_CALL_UUID_KEY, getTurnCredentialsOperation.getCallUuid());
        userInfo.put(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_REASON_KEY, failedReason);
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED, userInfo);
    }
}
