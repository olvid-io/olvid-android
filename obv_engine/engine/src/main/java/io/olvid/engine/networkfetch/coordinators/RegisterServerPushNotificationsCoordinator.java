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


import java.util.HashMap;
import java.util.HashSet;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.PushNotificationConfiguration;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.DownloadMessagesAndListAttachmentsDelegate;
import io.olvid.engine.networkfetch.datatypes.RegisterServerPushNotificationDelegate;
import io.olvid.engine.networkfetch.operations.RegisterPushNotificationOperation;

public class RegisterServerPushNotificationsCoordinator implements RegisterServerPushNotificationDelegate, PushNotificationConfiguration.NewPushNotificationConfigurationListener, Operation.OnCancelCallback, Operation.OnFinishCallback {

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;
    private final DownloadMessagesAndListAttachmentsDelegate downloadMessagesAndListAttachmentsDelegate;

    private final ExponentialBackoffRepeatingScheduler<Identity> scheduler;
    private final NoDuplicateOperationQueue registerPushNotificationOperationQueue;

    private final HashSet<Identity> awaitingServerSessionOperations;
    private final Object awaitingServerSessionOperationsLock;
    private final ServerSessionCreatedNotificationListener serverSessionCreatedNotificationListener;

    private final HashMap<UID, IdentityAndUid> androidIdentityMaskingUids;

    private NotificationListeningDelegate notificationListeningDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    private boolean initialQueueingPerformed = false;
    private final Object lock = new Object();

    public RegisterServerPushNotificationsCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory,
                                                      SSLSocketFactory sslSocketFactory,
                                                      CreateServerSessionDelegate createServerSessionDelegate,
                                                      DownloadMessagesAndListAttachmentsDelegate downloadMessagesAndListAttachmentsDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;
        this.downloadMessagesAndListAttachmentsDelegate = downloadMessagesAndListAttachmentsDelegate;

        registerPushNotificationOperationQueue = new NoDuplicateOperationQueue();
        registerPushNotificationOperationQueue.execute(1, "Engine-RegisterServerPushNotificationsCoordinator");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();
        awaitingServerSessionOperations = new HashSet<>();
        awaitingServerSessionOperationsLock = new Object();

        androidIdentityMaskingUids = new HashMap<>();

        serverSessionCreatedNotificationListener = new ServerSessionCreatedNotificationListener();
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, serverSessionCreatedNotificationListener);
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }


    public void initialQueueing() {
        synchronized (lock) {
            if (initialQueueingPerformed) {
                return;
            }
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
                            registerServerPushNotification(pushNotificationConfiguration.getOwnedIdentity());
                            break;
                        case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_NONE:
                            registerServerPushNotification(pushNotificationConfiguration.getOwnedIdentity());
                            break;
                    }
                }
                initialQueueingPerformed = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        if (deviceUid != null) {
            // after a registration, always start a downloadMessages
            downloadMessagesAndListAttachmentsDelegate.downloadMessagesAndListAttachments(ownedIdentity, deviceUid);
        }
        if (notificationPostingDelegate != null) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED_OWNED_IDENTITY_KEY, ownedIdentity);
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED, userInfo);
        } else {
            Logger.e("NotificationPostingDelegate not set in RegisterServerPushNotificationsCoordinator");
        }
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity identity = ((RegisterPushNotificationOperation) operation).getOwnedIdentity();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("RegisterPushNotificationOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case RegisterPushNotificationOperation.RFC_INVALID_SERVER_SESSION: {
                waitForServerSession(identity);
                createServerSessionDelegate.createServerSession(identity);
                break;
            }
            case RegisterPushNotificationOperation.RFC_ANOTHER_DEVICE_IS_ALREADY_REGISTERED: {
                break;
            }
            default: {
                scheduleNewRegisterPushNotificationOperationQueueing(identity);
            }
        }
    }

    private void waitForServerSession(Identity identity) {
        synchronized (awaitingServerSessionOperationsLock) {
            awaitingServerSessionOperations.add(identity);
        }
    }

    @Override
    public void registerServerPushNotification(Identity identity) {
        queueNewRegisterPushNotificationOperation(identity);
    }

    @Override
    public void newPushNotificationConfiguration(Identity identity, UID deviceUid, PushNotificationTypeAndParameters pushNotificationTypeAndParameters) {
        switch (pushNotificationTypeAndParameters.pushNotificationType) {
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_ANDROID:
                storeAndroidIdentityMaskingUid(identity, deviceUid, pushNotificationTypeAndParameters.identityMaskingUid);
                registerServerPushNotification(identity);
                break;
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_NONE:
                registerServerPushNotification(identity);
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
                downloadMessagesAndListAttachmentsDelegate.downloadMessagesAndListAttachments(identityAndUid.ownedIdentity, identityAndUid.uid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            Identity identity = (Identity) identityObject;
            synchronized (awaitingServerSessionOperationsLock) {
                if (awaitingServerSessionOperations.contains(identity)) {
                    awaitingServerSessionOperations.remove(identity);
                    queueNewRegisterPushNotificationOperation(identity);
                }
            }
        }
    }
}
