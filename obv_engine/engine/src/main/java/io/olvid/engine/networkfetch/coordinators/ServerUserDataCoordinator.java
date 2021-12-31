/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.containers.UserData;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networkfetch.databases.PendingServerQuery;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.operations.DeleteUserDataOperation;
import io.olvid.engine.networkfetch.operations.RefreshUserDataOperation;

public class ServerUserDataCoordinator implements Operation.OnCancelCallback, Operation.OnFinishCallback {
    private final ObjectMapper jsonObjectMapper;
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;
    private final PRNGService prng;

    private final ExponentialBackoffRepeatingScheduler<IdentityAndUid> scheduler;
    private final NoDuplicateOperationQueue deleteUserDataOperationQueue;
    private final NoDuplicateOperationQueue refreshUserDataOperationQueue;

    private final HashMap<Identity, List<UID>> awaitingServerSessionDeleteOperations;
    private final Lock awaitingServerSessionDeleteOperationsLock;
    private final HashMap<Identity, List<UID>> awaitingServerSessionRefreshOperations;
    private final Lock awaitingServerSessionRefreshOperationsLock;
    private final NotificationListener notificationListener;

    private boolean initialQueueingPerformed = false;
    private final Object lock = new Object();

    private NotificationListeningDelegate notificationListeningDelegate;

    public ServerUserDataCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, CreateServerSessionDelegate createServerSessionDelegate, ObjectMapper jsonObjectMapper, PRNGService prng) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;
        this.jsonObjectMapper = jsonObjectMapper;
        this.prng = prng;

        deleteUserDataOperationQueue = new NoDuplicateOperationQueue();
        deleteUserDataOperationQueue.execute(1, "Engine-ServerUserDataCoordinator-delete");
        refreshUserDataOperationQueue = new NoDuplicateOperationQueue();
        refreshUserDataOperationQueue.execute(1, "Engine-ServerUserDataCoordinator-refresh");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        awaitingServerSessionDeleteOperations = new HashMap<>();
        awaitingServerSessionDeleteOperationsLock = new ReentrantLock();
        awaitingServerSessionRefreshOperations = new HashMap<>();
        awaitingServerSessionRefreshOperationsLock = new ReentrantLock();

        notificationListener = new NotificationListener();
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, notificationListener);
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED, notificationListener);
    }


    public void initialQueueing() {
        // check all ServerUserData
        // delete no longer useful ServerUserData, refresh those that need it
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            UserData[] userDataList = fetchManagerSession.identityDelegate.getAllUserData(fetchManagerSession.session);
            for (UserData userData: userDataList) {
                if (userData.groupDetailsOwnerAndUid != null) {
                    GroupInformation groupInformation = fetchManagerSession.identityDelegate.getGroupInformation(fetchManagerSession.session, userData.ownedIdentity, userData.groupDetailsOwnerAndUid);
                    if (groupInformation == null || // group not found
                            !groupInformation.groupOwnerIdentity.equals(userData.ownedIdentity)){ // group not owned
                        queueNewDeleteUserDataOperation(userData.ownedIdentity, userData.label);
                    } else {
                        try {
                            JsonGroupDetailsWithVersionAndPhoto detailsWithVersionAndPhoto = jsonObjectMapper.readValue(groupInformation.serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);
                            if (detailsWithVersionAndPhoto == null || !Arrays.equals(detailsWithVersionAndPhoto.getPhotoServerLabel(), userData.label.getBytes())) {
                                queueNewDeleteUserDataOperation(userData.ownedIdentity, userData.label);
                            } else if (userData.nextRefreshTimestamp < System.currentTimeMillis()) {
                                queueNewRefreshUserDataOperation(userData.ownedIdentity, userData.label);
                            }
                        } catch (Exception e) {
                            queueNewDeleteUserDataOperation(userData.ownedIdentity, userData.label);
                        }
                    }
                } else {
                    JsonIdentityDetailsWithVersionAndPhoto details = fetchManagerSession.identityDelegate.getOwnedIdentityPublishedDetails(fetchManagerSession.session, userData.ownedIdentity);
                    if (details == null || !Arrays.equals(details.getPhotoServerLabel(), userData.label.getBytes())) {
                        queueNewDeleteUserDataOperation(userData.ownedIdentity, userData.label);
                    } else if (userData.nextRefreshTimestamp < System.currentTimeMillis()) {
                        queueNewRefreshUserDataOperation(userData.ownedIdentity, userData.label);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // cleanup downloaded user data dir of orphan files
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            File userDataDir = new File(fetchManagerSession.engineBaseDirectory, Constants.DOWNLOADED_USER_DATA_DIRECTORY);
            String[] userDataFiles = userDataDir.list();
            if (userDataFiles != null) {
                for (String userDataFile: userDataFiles) {
                    int pos = userDataFile.indexOf(".");
                    if (pos != -1) {
                        long expireTimestamp = Long.parseLong(userDataFile.substring(0, pos));
                        if (expireTimestamp > System.currentTimeMillis()) {
                            continue;
                        }
                    }
                    // the . is missing, or the file is expired --> delete it
                    try {
                        File file = new File(userDataDir, userDataFile);
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void queueNewRefreshUserDataOperation(Identity ownedIdentity, UID label) {
        RefreshUserDataOperation op = new RefreshUserDataOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, label, this, this);
        refreshUserDataOperationQueue.queue(op);
    }

    private void scheduleNewRefreshUserDataOperation(Identity ownedIdentity, UID label) {
        scheduler.schedule(new IdentityAndUid(ownedIdentity, label), () -> queueNewRefreshUserDataOperation(ownedIdentity, label), "ServerQueryOperation");
    }


    private void queueNewDeleteUserDataOperation(Identity ownedIdentity, UID label) {
        DeleteUserDataOperation op = new DeleteUserDataOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, label, this, this);
        deleteUserDataOperationQueue.queue(op);
    }

    private void scheduleNewDeleteUserDataOperation(Identity ownedIdentity, UID label) {
        scheduler.schedule(new IdentityAndUid(ownedIdentity, label), () -> queueNewDeleteUserDataOperation(ownedIdentity, label), "ServerQueryOperation");
    }

    private void deleteWaitForServerSession(Identity identity, UID label) {
        awaitingServerSessionDeleteOperationsLock.lock();
        List<UID> list = awaitingServerSessionDeleteOperations.get(identity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingServerSessionDeleteOperations.put(identity, list);
        }
        list.add(label);
        awaitingServerSessionDeleteOperationsLock.unlock();
    }

    private void refreshWaitForServerSession(Identity identity, UID label) {
        awaitingServerSessionRefreshOperationsLock.lock();
        List<UID> list = awaitingServerSessionRefreshOperations.get(identity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingServerSessionRefreshOperations.put(identity, list);
        }
        list.add(label);
        awaitingServerSessionRefreshOperationsLock.unlock();
    }


    @Override
    public void onCancelCallback(Operation operation) {
        if (operation instanceof RefreshUserDataOperation) {
            Identity ownedIdentity = ((RefreshUserDataOperation) operation).getOwnedIdentity();
            UID label = ((RefreshUserDataOperation) operation).getLabel();
            Integer rfc = operation.getReasonForCancel();
            Logger.d("RefreshUserDataOperation cancelled for reason " + rfc);
            if (rfc == null) {
                rfc = Operation.RFC_NULL;
            }
            switch (rfc) {
                case RefreshUserDataOperation.RFC_INVALID_SERVER_SESSION: {
                    refreshWaitForServerSession(ownedIdentity, label);
                    createServerSessionDelegate.createServerSession(ownedIdentity);
                    break;
                }
                case RefreshUserDataOperation.RFC_USER_DATA_DELETED_FROM_SERVER: {
                    // create a new server query, not linked to a protocol
                    try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                        UserData userData = fetchManagerSession.identityDelegate.getUserData(fetchManagerSession.session, ownedIdentity, label);
                        if (userData != null) {
                            String photoUrl;
                            AuthEncKey key;

                            if (userData.groupDetailsOwnerAndUid != null) {
                                JsonGroupDetailsWithVersionAndPhoto json = fetchManagerSession.identityDelegate.getGroupPublishedAndLatestOrTrustedDetails(fetchManagerSession.session, userData.ownedIdentity, userData.groupDetailsOwnerAndUid)[0];
                                photoUrl = json.getPhotoUrl();
                                key = (AuthEncKey) new Encoded(json.getPhotoServerKey()).decodeSymmetricKey();
                            } else {
                                JsonIdentityDetailsWithVersionAndPhoto json = fetchManagerSession.identityDelegate.getOwnedIdentityPublishedDetails(fetchManagerSession.session, userData.ownedIdentity);
                                photoUrl = json.getPhotoUrl();
                                key = (AuthEncKey) new Encoded(json.getPhotoServerKey()).decodeSymmetricKey();
                            }

                            if (photoUrl != null && key != null) {
                                ServerQuery serverQuery = new ServerQuery(
                                        Encoded.of(new Encoded[0]),
                                        ownedIdentity,
                                        ServerQuery.Type.createPutUserDataQuery(ownedIdentity, label, photoUrl, key)
                                );

                                PendingServerQuery.create(fetchManagerSession, serverQuery, prng);
                                fetchManagerSession.session.commit();
                            }
                        }
                    } catch (Exception e) {
                        // do nothing, this will be retried after the next restart
                    }
                    break;
                }
                case RefreshUserDataOperation.RFC_IDENTITY_IS_INACTIVE:
                case RefreshUserDataOperation.RFC_USER_DATA_NOT_FOUND: {
                    break;
                }
                default:
                    // Requeue the operation in the future
                    scheduleNewRefreshUserDataOperation(ownedIdentity, label);
            }
        } else if (operation instanceof DeleteUserDataOperation) {
            Identity ownedIdentity = ((DeleteUserDataOperation) operation).getOwnedIdentity();
            UID label = ((DeleteUserDataOperation) operation).getLabel();
            Integer rfc = operation.getReasonForCancel();
            Logger.i("DeleteUserDataOperation cancelled for reason " + rfc);
            if (rfc == null) {
                rfc = Operation.RFC_NULL;
            }
            switch (rfc) {
                case DeleteUserDataOperation.RFC_INVALID_SERVER_SESSION: {
                    deleteWaitForServerSession(ownedIdentity, label);
                    createServerSessionDelegate.createServerSession(ownedIdentity);
                    break;
                }
                case DeleteUserDataOperation.RFC_IDENTITY_IS_INACTIVE:
                case DeleteUserDataOperation.RFC_USER_DATA_NOT_FOUND: {
                    break;
                }
                default:
                    // Requeue the operation in the future
                    scheduleNewDeleteUserDataOperation(ownedIdentity, label);
            }
        }
    }

    @Override
    public void onFinishCallback(Operation operation) {
        if (operation instanceof RefreshUserDataOperation) {
            Logger.d("RefreshUserDataOperation finished");
            Identity ownedIdentity = ((RefreshUserDataOperation) operation).getOwnedIdentity();
            UID label = ((RefreshUserDataOperation) operation).getLabel();
            scheduler.clearFailedCount(new IdentityAndUid(ownedIdentity, label));
        } else if (operation instanceof DeleteUserDataOperation) {
            Logger.d("DeleteUserDataOperation finished");
            Identity ownedIdentity = ((DeleteUserDataOperation) operation).getOwnedIdentity();
            UID label = ((DeleteUserDataOperation) operation).getLabel();
            scheduler.clearFailedCount(new IdentityAndUid(ownedIdentity, label));
        }
    }


    class NotificationListener implements io.olvid.engine.datatypes.NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            try {
                switch (notificationName) {
                    case DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED: {
                        Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY);
                        awaitingServerSessionDeleteOperationsLock.lock();
                        List<UID> labels = awaitingServerSessionDeleteOperations.get(ownedIdentity);
                        if (labels != null) {
                            awaitingServerSessionDeleteOperations.remove(ownedIdentity);
                            for (UID label: labels) {
                                queueNewDeleteUserDataOperation(ownedIdentity, label);
                            }
                        }
                        awaitingServerSessionDeleteOperationsLock.unlock();

                        awaitingServerSessionRefreshOperationsLock.lock();
                        labels = awaitingServerSessionRefreshOperations.get(ownedIdentity);
                        if (labels != null) {
                            awaitingServerSessionRefreshOperations.remove(ownedIdentity);
                            for (UID label: labels) {
                                queueNewRefreshUserDataOperation(ownedIdentity, label);
                            }
                        }
                        awaitingServerSessionRefreshOperationsLock.unlock();
                        break;
                    }
                    case IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED: {
                        Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_OWNED_IDENTITY_KEY);
                        UID label = (UID) userInfo.get(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_LABEL_KEY);
                        if (ownedIdentity != null && label != null) {
                            queueNewDeleteUserDataOperation(ownedIdentity, label);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
