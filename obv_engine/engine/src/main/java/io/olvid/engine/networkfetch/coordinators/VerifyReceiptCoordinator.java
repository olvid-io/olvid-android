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

package io.olvid.engine.networkfetch.coordinators;


import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.operations.VerifyReceiptOperation;

public class VerifyReceiptCoordinator implements Operation.OnCancelCallback {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;

    private final OperationQueue verifyReceiptOperationQueue;

    private NotificationListeningDelegate notificationListeningDelegate;

    private final HashMap<Identity, String> awaitingServerSessionOperations;
    private final Lock awaitingServerSessionOperationsLock;
    private final ServerSessionCreatedNotificationListener serverSessionCreatedNotificationListener;


    public VerifyReceiptCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, CreateServerSessionDelegate createServerSessionDelegate) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;

        verifyReceiptOperationQueue = new OperationQueue(true);

        awaitingServerSessionOperations = new HashMap<>();
        awaitingServerSessionOperationsLock = new ReentrantLock();
        serverSessionCreatedNotificationListener = new ServerSessionCreatedNotificationListener();
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, serverSessionCreatedNotificationListener);
    }

    private void queueNewVerifyReceiptOperation(Identity ownedIdentity, String storeToken) {
        VerifyReceiptOperation op = new VerifyReceiptOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, storeToken, this);
        verifyReceiptOperationQueue.queue(op);
    }

    public void startProcessing() {
        verifyReceiptOperationQueue.execute(1, "Engine-VerifyReceiptCoordinator");
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Identity ownedIdentity = ((VerifyReceiptOperation) operation).getOwnedIdentity();
        String storeToken = ((VerifyReceiptOperation) operation).getStoreToken();
        Integer rfc = operation.getReasonForCancel();
        Logger.i("VerifyReceiptOperation cancelled for reason " + rfc);
        if (rfc == null) {
            rfc = Operation.RFC_NULL;
        }
        switch (rfc) {
            case VerifyReceiptOperation.RFC_INVALID_SERVER_SESSION:
                if (ownedIdentity != null) {
                    waitForServerSession(ownedIdentity, storeToken);
                    createServerSessionDelegate.createServerSession(ownedIdentity);
                }
                break;
        }
    }


    private void waitForServerSession(Identity identity, String storeToken) {
        awaitingServerSessionOperationsLock.lock();
        awaitingServerSessionOperations.put(identity, storeToken);
        awaitingServerSessionOperationsLock.unlock();
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
            Identity ownedIdentity = (Identity) identityObject;
            awaitingServerSessionOperationsLock.lock();
            String storeToken = awaitingServerSessionOperations.get(ownedIdentity);
            if (storeToken != null) {
                awaitingServerSessionOperations.remove(ownedIdentity);
                queueNewVerifyReceiptOperation(ownedIdentity, storeToken);
            }
            awaitingServerSessionOperationsLock.unlock();
        }
    }


    public void verifyReceipt(Identity ownedIdentity, String storeToken) {
        queueNewVerifyReceiptOperation(ownedIdentity, storeToken);
    }
}
