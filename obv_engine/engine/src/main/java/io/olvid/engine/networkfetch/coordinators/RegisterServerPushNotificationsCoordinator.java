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


import java.util.HashMap;
import java.util.HashSet;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.containers.OwnedIdentitySynchronizationStatus;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.PushNotificationConfiguration;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.DownloadMessagesAndListAttachmentsDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.RegisterServerPushNotificationDelegate;
import io.olvid.engine.networkfetch.operations.RegisterPushNotificationOperation;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

public class RegisterServerPushNotificationsCoordinator implements RegisterServerPushNotificationDelegate, PushNotificationConfiguration.NewPushNotificationConfigurationListener, Operation.OnCancelCallback, Operation.OnFinishCallback {

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;
    private final DownloadMessagesAndListAttachmentsDelegate downloadMessagesAndListAttachmentsDelegate;

    private final ExponentialBackoffRepeatingScheduler<Identity> scheduler;
    private final NoDuplicateOperationQueue registerPushNotificationOperationQueue;

    private final ServerSessionCreatedNotificationListener serverSessionCreatedNotificationListener;

    private final HashMap<UID, IdentityAndUid> androidIdentityMaskingUids;
    private final HashSet<Identity> ownedIdentitiesThatNeedAnOwnedDeviceDiscovery;

    private NotificationListeningDelegate notificationListeningDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;
    private ProtocolStarterDelegate protocolStarterDelegate;

    public RegisterServerPushNotificationsCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory,
                                                      SSLSocketFactory sslSocketFactory,
                                                      CreateServerSessionDelegate createServerSessionDelegate,
                                                      DownloadMessagesAndListAttachmentsDelegate downloadMessagesAndListAttachmentsDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;
        this.downloadMessagesAndListAttachmentsDelegate = downloadMessagesAndListAttachmentsDelegate;

        registerPushNotificationOperationQueue = new NoDuplicateOperationQueue();

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        androidIdentityMaskingUids = new HashMap<>();
        ownedIdentitiesThatNeedAnOwnedDeviceDiscovery = new HashSet<>();

        serverSessionCreatedNotificationListener = new ServerSessionCreatedNotificationListener();
    }

    public void startProcessing() {
        registerPushNotificationOperationQueue.execute(1, "Engine-RegisterServerPushNotificationsCoordinator");
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, serverSessionCreatedNotificationListener);
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setProtocolStarterDelegate(ProtocolStarterDelegate protocolStarterDelegate) {
        this.protocolStarterDelegate = protocolStarterDelegate;
    }



    public void initialQueueing() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            PushNotificationConfiguration[] pushNotificationConfigurations = PushNotificationConfiguration.getAll(fetchManagerSession);
            for (PushNotificationConfiguration pushNotificationConfiguration : pushNotificationConfigurations) {
                // check that the corresponding owned Identity still exists --> delete otherwise
                if (!fetchManagerSession.identityDelegate.isOwnedIdentity(fetchManagerSession.session, pushNotificationConfiguration.getOwnedIdentity())) {
                    PushNotificationConfiguration.deleteForOwnedIdentity(fetchManagerSession, pushNotificationConfiguration.getOwnedIdentity());
                    fetchManagerSession.session.commit();
                    continue;
                }

                switch (pushNotificationConfiguration.getPushNotificationType()) {
                    case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_ANDROID:
                        storeAndroidIdentityMaskingUid(pushNotificationConfiguration.getOwnedIdentity(), pushNotificationConfiguration.getDeviceUid(), pushNotificationConfiguration.getIdentityMaskingUid());
                        registerServerPushNotification(pushNotificationConfiguration.getOwnedIdentity(), false);
                        break;
                    case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_ANDROID:
                    case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_WINDOWS:
                    case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_LINUX:
                    case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_DAEMON:
                        registerServerPushNotification(pushNotificationConfiguration.getOwnedIdentity(), false);
                        break;
                }
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }


    private void queueNewRegisterPushNotificationOperation(Identity ownedIdentity) {
        RegisterPushNotificationOperation op = new RegisterPushNotificationOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, this, this);
        registerPushNotificationOperationQueue.queue(op);
    }

    private void scheduleNewRegisterPushNotificationOperationQueueing(final Identity identity) {
        scheduler.schedule(identity, () -> queueNewRegisterPushNotificationOperation(identity), "RegisterPushNotificationOperation");
    }



    @Override
    public void onFinishCallback(Operation operation) {
        Identity ownedIdentity = ((RegisterPushNotificationOperation) operation).getOwnedIdentity();
        UID deviceUid = ((RegisterPushNotificationOperation) operation).getDeviceUid();
        scheduler.clearFailedCount(ownedIdentity);
        // sync is not needed after a register
//        if (deviceUid != null) {
//            // after a registration, always start a downloadMessages
//            downloadMessagesAndListAttachmentsDelegate.downloadMessagesAndListAttachments(ownedIdentity, deviceUid);
//        }
        if (notificationPostingDelegate != null) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED_OWNED_IDENTITY_KEY, ownedIdentity);
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED, userInfo);
        } else {
            Logger.e("NotificationPostingDelegate not set in RegisterServerPushNotificationsCoordinator");
        }
        synchronized (ownedIdentitiesThatNeedAnOwnedDeviceDiscovery) {
            if (protocolStarterDelegate != null && ownedIdentitiesThatNeedAnOwnedDeviceDiscovery.remove(ownedIdentity)) {
                try {
                    protocolStarterDelegate.startOwnedDeviceDiscoveryProtocol(ownedIdentity);
                } catch (Exception ignored) { }
            }
        }
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((RegisterPushNotificationOperation) operation).getOwnedIdentity();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("RegisterPushNotificationOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case RegisterPushNotificationOperation.RFC_INVALID_SERVER_SESSION: {
                createServerSessionDelegate.createServerSession(ownedIdentity);
                break;
            }
            case RegisterPushNotificationOperation.RFC_ANOTHER_DEVICE_IS_ALREADY_REGISTERED:
            case RegisterPushNotificationOperation.RFC_PUSH_NOTIFICATION_CONFIGURATION_NOT_FOUND: {
                break;
            }
            case RegisterPushNotificationOperation.RFC_DEVICE_UID_TO_REPLACE_NOT_FOUND: {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE_OWNED_IDENTITY_KEY, ownedIdentity);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE, userInfo);
                break;
            }
            default: {
                scheduleNewRegisterPushNotificationOperationQueueing(ownedIdentity);
            }
        }
    }

    @Override
    public void registerServerPushNotification(Identity identity, boolean triggerAnOwnedDeviceDiscoveryWhenFinished) {
        if (triggerAnOwnedDeviceDiscoveryWhenFinished) {
            synchronized (ownedIdentitiesThatNeedAnOwnedDeviceDiscovery) {
                ownedIdentitiesThatNeedAnOwnedDeviceDiscovery.add(identity);
            }
        }
        queueNewRegisterPushNotificationOperation(identity);
    }

    @Override
    public void newPushNotificationConfiguration(Identity identity, UID deviceUid, PushNotificationTypeAndParameters pushNotificationTypeAndParameters) {
        switch (pushNotificationTypeAndParameters.pushNotificationType) {
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_ANDROID:
                storeAndroidIdentityMaskingUid(identity, deviceUid, pushNotificationTypeAndParameters.identityMaskingUid);
                registerServerPushNotification(identity, false);
                break;
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_ANDROID:
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_WINDOWS:
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_LINUX:
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_DAEMON:
                registerServerPushNotification(identity, false);
                break;
        }
    }

    public void processAndroidPushNotification(String androidMaskingUidString) {
        if (androidMaskingUidString == null) {
            return;
        }
        try {
            UID androidIdentityMaskingUid = new UID(androidMaskingUidString);
            IdentityAndUid identityAndUid = androidIdentityMaskingUids.get(androidIdentityMaskingUid);
            if (identityAndUid != null) {
                fetchManagerSessionFactory.markOwnedIdentityAsNotUpToDate(identityAndUid.ownedIdentity, OwnedIdentitySynchronizationStatus.OTHER_SYNC_IN_PROGRESS);
                downloadMessagesAndListAttachmentsDelegate.downloadMessagesAndListAttachments(identityAndUid.ownedIdentity, identityAndUid.uid);
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    public Identity getOwnedIdentityFromMaskingUid(String androidMaskingUidString) {
        if (androidMaskingUidString != null) {
            try {
                UID androidIdentityMaskingUid = new UID(androidMaskingUidString);
                IdentityAndUid identityAndUid = androidIdentityMaskingUids.get(androidIdentityMaskingUid);
                if (identityAndUid != null) {
                    return identityAndUid.ownedIdentity;
                }
            } catch (Exception e) {
                Logger.x(e);
            }
        }
        return null;
    }

    private void storeAndroidIdentityMaskingUid(Identity identity, UID deviceUid, UID identityMaskingUid) {
        androidIdentityMaskingUids.put(identityMaskingUid, new IdentityAndUid(identity, deviceUid));
    }


    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
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

            // always do a register after a new client session, we no longer keep a list a awaiting identities
            queueNewRegisterPushNotificationOperation((Identity) identityObject);
        }
    }
}
