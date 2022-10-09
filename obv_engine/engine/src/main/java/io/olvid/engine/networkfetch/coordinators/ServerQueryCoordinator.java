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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelServerResponseMessageToSend;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networkfetch.databases.PendingServerQuery;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.operations.ServerQueryOperation;

public class ServerQueryCoordinator implements PendingServerQuery.PendingServerQueryListener, Operation.OnCancelCallback, Operation.OnFinishCallback {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final PRNGService prng;
    private final CreateServerSessionDelegate createServerSessionDelegate;

    private final ExponentialBackoffRepeatingScheduler<UID> scheduler;
    private final NoDuplicateOperationQueue serverQueriesOperationQueue;

    private final HashMap<Identity, List<UID>> awaitingServerSessionOperations;
    private final Lock awaitingServerSessionOperationsLock;
    private final NotificationListener notificationListener;
    private final ServerUserDataCoordinator serverUserDataCoordinator;

    private boolean initialQueueingPerformed = false;
    private final Object lock = new Object();

    private final HashMap<Identity, List<UID>> awaitingIdentityReactivationOperations;
    private final Lock awaitingIdentityReactivationOperationsLock;

    private NotificationListeningDelegate notificationListeningDelegate;

    private ChannelDelegate channelDelegate;

    public ServerQueryCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, PRNGService prng, CreateServerSessionDelegate createServerSessionDelegate, ServerUserDataCoordinator serverUserDataCoordinator) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.prng = prng;
        this.createServerSessionDelegate = createServerSessionDelegate;
        this.serverUserDataCoordinator = serverUserDataCoordinator;

        serverQueriesOperationQueue = new NoDuplicateOperationQueue();
        serverQueriesOperationQueue.execute(1, "Engine-ServerQueryCoordinator");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();

        awaitingServerSessionOperations = new HashMap<>();
        awaitingServerSessionOperationsLock = new ReentrantLock();

        awaitingIdentityReactivationOperations = new HashMap<>();
        awaitingIdentityReactivationOperationsLock = new ReentrantLock();

        notificationListener = new NotificationListener();
    }

    public void initialQueueing() {
        synchronized (lock) {
            if (initialQueueingPerformed) {
                return;
            }
            try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                PendingServerQuery[] pendingServerQueries = PendingServerQuery.getAll(fetchManagerSession);
                for (PendingServerQuery pendingServerQuery: pendingServerQueries) {
                    queueNewServerQueryOperation(pendingServerQuery.getUid());
                }
                initialQueueingPerformed = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, notificationListener);
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, notificationListener);
    }

    public void setChannelDelegate(ChannelDelegate channelDelegate) {
        this.channelDelegate = channelDelegate;
    }

    private void waitForServerSession(Identity identity, UID serverQueryUid) {
        awaitingServerSessionOperationsLock.lock();
        List<UID> list = awaitingServerSessionOperations.get(identity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingServerSessionOperations.put(identity, list);
        }
        list.add(serverQueryUid);
        awaitingServerSessionOperationsLock.unlock();
    }

    private void waitForIdentityReactivation(Identity identity, UID serverQueryUid) {
        awaitingIdentityReactivationOperationsLock.lock();
        List<UID> list = awaitingIdentityReactivationOperations.get(identity);
        if (list == null) {
            list = new ArrayList<>();
            awaitingIdentityReactivationOperations.put(identity, list);
        }
        list.add(serverQueryUid);
        awaitingIdentityReactivationOperationsLock.unlock();
    }

    class NotificationListener implements io.olvid.engine.datatypes.NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            try {
                switch (notificationName) {
                    case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS: {
                        boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                        Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                        if (!active) {
                            break;
                        }

                        awaitingIdentityReactivationOperationsLock.lock();
                        List<UID> serverQueryUids = awaitingIdentityReactivationOperations.get(ownedIdentity);
                        if (serverQueryUids != null) {
                            awaitingIdentityReactivationOperations.remove(ownedIdentity);
                            for (UID serverQueryUid: serverQueryUids) {
                                queueNewServerQueryOperation(serverQueryUid);
                            }
                        }
                        awaitingIdentityReactivationOperationsLock.unlock();
                        break;
                    }
                    case DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED: {
                        Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY);
                        awaitingServerSessionOperationsLock.lock();
                        List<UID> serverQueryUids = awaitingServerSessionOperations.get(ownedIdentity);
                        if (serverQueryUids != null) {
                            awaitingServerSessionOperations.remove(ownedIdentity);
                            for (UID serverQueryUid: serverQueryUids) {
                                queueNewServerQueryOperation(serverQueryUid);
                            }
                        }
                        awaitingServerSessionOperationsLock.unlock();
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void queueNewServerQueryOperation(UID serverQueryUid) {
        ServerQueryOperation op = new ServerQueryOperation(fetchManagerSessionFactory, sslSocketFactory, serverQueryUid, prng,this, this);
        serverQueriesOperationQueue.queue(op);
    }

    private void scheduleNewServerQueryOperation(final UID serverQueryUid) {
        scheduler.schedule(serverQueryUid, () -> queueNewServerQueryOperation(serverQueryUid), "ServerQueryOperation");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }

    @Override
    public void onCancelCallback(Operation operation) {
        UID serverQueryUid = ((ServerQueryOperation) operation).getServerQueryUid();
        ServerQuery serverQuery = ((ServerQueryOperation) operation).getServerQuery();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("ServerQueryOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case ServerQueryOperation.RFC_USER_DATA_TOO_LARGE:
            case ServerQueryOperation.RFC_BAD_ENCODED_SERVER_QUERY: {
                // PendingServerQuery cannot be understood,
                // or the data to send is too large
                // ==> we can delete it from the database
                try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                    PendingServerQuery pendingServerQuery = PendingServerQuery.get(fetchManagerSession, serverQueryUid);
                    if (pendingServerQuery != null) {
                        pendingServerQuery.delete();
                        fetchManagerSession.session.commit();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                break;
            }
            case ServerQueryOperation.RFC_INVALID_SERVER_SESSION: {
                if (serverQuery.getType().getId() == ServerQuery.Type.PUT_USER_DATA_QUERY_ID
                        || serverQuery.getType().getId() == ServerQuery.Type.CREATE_GROUP_BLOB_QUERY_ID) {
                    waitForServerSession(serverQuery.getOwnedIdentity(), serverQueryUid);
                    createServerSessionDelegate.createServerSession(serverQuery.getOwnedIdentity());
                }
                break;
            }
            case ServerQueryOperation.RFC_IDENTITY_IS_INACTIVE: {
                waitForIdentityReactivation(serverQuery.getOwnedIdentity(), serverQueryUid);
                break;
            }
            default:
                // Requeue the operation in the future
                scheduleNewServerQueryOperation(serverQueryUid);
        }
    }

    @Override
    public void onFinishCallback(Operation operation) {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            UID serverQueryUid = ((ServerQueryOperation) operation).getServerQueryUid();
            ServerQuery serverQuery = ((ServerQueryOperation) operation).getServerQuery();
            Encoded serverResponse = ((ServerQueryOperation) operation).getServerResponse();

            scheduler.clearFailedCount(serverQueryUid);

            PendingServerQuery pendingServerQuery = PendingServerQuery.get(fetchManagerSession, serverQueryUid);
            if (pendingServerQuery == null) {
                return;
            }
            // check if the encoded elements are empty --> empty means no associated protocol
            boolean partOfProtocol = true;
            try {
                partOfProtocol = serverQuery.getEncodedElements().decodeList().length != 0;
            } catch (DecodingException e) {
                // do nothing
            }

            if (partOfProtocol) {
                ChannelServerResponseMessageToSend channelServerResponseMessageToSend = new ChannelServerResponseMessageToSend(
                        serverQuery.getOwnedIdentity(),
                        serverResponse,
                        serverQuery.getEncodedElements()
                );
                if (channelDelegate == null) {
                    Logger.e("ServerQueryOperation finished but no ChannelDelegate is set to post the response to.");
                    return;
                }
                try {
                    fetchManagerSession.session.startTransaction();
                    channelDelegate.post(fetchManagerSession.session, channelServerResponseMessageToSend, prng);
                    pendingServerQuery.delete();
                    fetchManagerSession.session.commit();
                } catch (Exception e) {
                    fetchManagerSession.session.rollback();
                }
            } else {
                pendingServerQuery.delete();
                fetchManagerSession.session.commit();
            }

            if (serverQuery.getType().getId() == ServerQuery.Type.PUT_USER_DATA_QUERY_ID) {
                serverUserDataCoordinator.newUserDataUploaded(serverQuery.getOwnedIdentity(), serverQuery.getType().getServerLabel());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // Notifications received from PendingServerQuery database
    @Override
    public void newPendingServerQuery(UID uid) {
        queueNewServerQueryOperation(uid);
    }
}
