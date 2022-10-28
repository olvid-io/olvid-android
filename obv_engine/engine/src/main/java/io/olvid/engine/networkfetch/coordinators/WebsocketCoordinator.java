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


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.PingFrame;
import org.java_websocket.handshake.ServerHandshake;
import net.iharder.Base64;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.ExponentialBackoffRepeatingScheduler;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.CreateServerSessionDelegate;
import io.olvid.engine.networkfetch.datatypes.DownloadMessagesAndListAttachmentsDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.WellKnownCacheDelegate;

public class WebsocketCoordinator implements Operation.OnCancelCallback {

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final CreateServerSessionDelegate createServerSessionDelegate;
    private final DownloadMessagesAndListAttachmentsDelegate downloadMessagesAndListAttachmentsDelegate;
    private final WellKnownCacheDelegate wellKnownCacheDelegate;
    private final ObjectMapper jsonObjectMapper;

    private boolean initialQueueingPerformed = false;
    private final Map<String, List<IdentityAndUid>> ownedIdentityAndUidsByServer;
    private final Map<Identity, UID> ownedIdentityCurrentDeviceUids;
    private final Map<Identity, byte[]> ownedIdentityServerSessionTokens;
    private final Object ownedIdentityAndUidsLock = new Object();

    private final Map<String, WebSocketClient> existingWebsockets;

    private final ExponentialBackoffRepeatingScheduler<String> scheduler;
    private final NoDuplicateOperationQueue websocketCreationOperationQueue;
    private final NoDuplicateOperationQueue identityRegistrationOperationQueue;

    private final HashSet<Identity> awaitingServerSessionIdentities;
    private final Object awaitingServerSessionIdentitiesLock;
    private final ServerSessionCreatedNotificationListener serverSessionCreatedNotificationListener;
    private final OwnedIdentityListUpdatedNotificationListener ownedIdentityListUpdatedNotificationListener;
    private final WellKnownCacheNotificationListener wellKnownCacheNotificationListener;

    private NotificationListeningDelegate notificationListeningDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    private boolean doConnect = false;

    private String os;
    private String osVersion;
    private int appBuild;
    private String appVersion;

    public WebsocketCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory,
                                SSLSocketFactory sslSocketFactory,
                                CreateServerSessionDelegate createServerSessionDelegate,
                                DownloadMessagesAndListAttachmentsDelegate downloadMessagesAndListAttachmentsDelegate,
                                WellKnownCacheDelegate wellKnownCacheDelegate,
                                ObjectMapper jsonObjectMapper) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.createServerSessionDelegate = createServerSessionDelegate;
        this.downloadMessagesAndListAttachmentsDelegate = downloadMessagesAndListAttachmentsDelegate;
        this.wellKnownCacheDelegate = wellKnownCacheDelegate;
        this.jsonObjectMapper = jsonObjectMapper;

        websocketCreationOperationQueue = new NoDuplicateOperationQueue();
        websocketCreationOperationQueue.execute(1, "Engine-WebsocketCoordinator-create");

        identityRegistrationOperationQueue = new NoDuplicateOperationQueue();
        identityRegistrationOperationQueue.execute(1, "Engine-WebsocketCoordinator-register");

        scheduler = new ExponentialBackoffRepeatingScheduler<>();
        awaitingServerSessionIdentities = new HashSet<>();
        awaitingServerSessionIdentitiesLock = new Object();

        serverSessionCreatedNotificationListener = new ServerSessionCreatedNotificationListener();
        ownedIdentityListUpdatedNotificationListener = new OwnedIdentityListUpdatedNotificationListener();
        wellKnownCacheNotificationListener = new WellKnownCacheNotificationListener();

        ownedIdentityAndUidsByServer = new HashMap<>();
        ownedIdentityCurrentDeviceUids = new HashMap<>();
        ownedIdentityServerSessionTokens = new HashMap<>();
        existingWebsockets = new HashMap<>();
    }

    public void setNotificationListeningDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        // register to NotificationCenter for NOTIFICATION_SERVER_SESSION_CREATED
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED, serverSessionCreatedNotificationListener);
        // register to NotificationCenter for NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED and NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED, ownedIdentityListUpdatedNotificationListener);
        this.notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, ownedIdentityListUpdatedNotificationListener);

        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_WELL_KNOWN_CACHE_INITIALIZED, wellKnownCacheNotificationListener);
        this.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED, wellKnownCacheNotificationListener);
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void initialQueueing() {
        synchronized (ownedIdentityAndUidsLock) {
            if (initialQueueingPerformed) {
                return;
            }
            try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                ownedIdentityAndUidsByServer.clear();
                ownedIdentityCurrentDeviceUids.clear();
                Identity[] ownedIdentities = fetchManagerSession.identityDelegate.getOwnedIdentities(fetchManagerSession.session);
                for (Identity ownedIdentity: ownedIdentities) {
                    if (!fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity)) {
                        continue;
                    }
                    UID deviceUid = fetchManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(fetchManagerSession.session, ownedIdentity);
                    ownedIdentityCurrentDeviceUids.put(ownedIdentity, deviceUid);
                    String server = ownedIdentity.getServer();
                    List<IdentityAndUid> identityAndUids = ownedIdentityAndUidsByServer.get(server);
                    if (identityAndUids == null) {
                        identityAndUids = new ArrayList<>();
                        ownedIdentityAndUidsByServer.put(server, identityAndUids);
                    }
                    identityAndUids.add(new IdentityAndUid(ownedIdentity, deviceUid));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            initialQueueingPerformed = true;
        }
        resetWebsockets();
    }

    public void connectWebsockets(String os, String osVersion, int appBuild, String appVersion) {
        doConnect = true;
        this.os = os;
        this.osVersion = osVersion;
        this.appBuild = appBuild;
        this.appVersion = appVersion;

        internalConnectWebsockets();
    }

    public void disconnectWebsockets() {
        doConnect = false;
        internalDisconnectWebsockets();
    }

    // this sends a ping for the current ownedIdentity websocket and returns a ping latency inside a notification
    public void pingWebsocket(Identity ownedIdentity) {
        String server = ownedIdentity.getServer();
        WebSocketClient webSocketClient = existingWebsockets.get(server);
        if (webSocketClient != null) {
            if (webSocketClient.isOpen()) {
                webSocketClient.sendPing();
            }
        }
    }

    private void resetWebsockets() {
        internalDisconnectWebsockets();
        internalConnectWebsockets();
    }

    private void internalDisconnectWebsockets() {
        List<WebSocketClient> webSocketClients = new ArrayList<>(existingWebsockets.values());
        for (WebSocketClient webSocketClient: webSocketClients) {
            webSocketClient.closeConnection(0, null);
        }
    }

    private void internalConnectWebsockets() {
        if (!doConnect) {
            return;
        }
        synchronized (ownedIdentityAndUidsLock) {
            for (String server: ownedIdentityAndUidsByServer.keySet()) {
                queueWebsocketCreationOperation(server);
            }
        }
    }

    private void queueWebsocketCreationOperation(String server) {
        websocketCreationOperationQueue.queue(new WebsocketCreationOperation(server, this));
    }

    private void queueIdentityRegistrationOperation(Identity identity, UID deviceUid) {
        identityRegistrationOperationQueue.queue(new IdentityRegistrationOperation(identity, deviceUid, this));
    }

    private void scheduleNewWebsocketCreationQueueing(final String server) {
        scheduler.schedule(server, () -> queueWebsocketCreationOperation(server), "Websocket Connection");
    }

    public void retryScheduledNetworkTasks() {
        scheduler.retryScheduledRunnables();
    }


    @Override
    public void onCancelCallback(Operation operation) {
        if (operation instanceof  WebsocketCreationOperation) {
            String server = ((WebsocketCreationOperation) operation).getServer();
            Integer rfc = operation.getReasonForCancel();
            Logger.i("WebsocketCreationOperation cancelled for reason " + rfc);
            if (rfc == null) {
                rfc = Operation.RFC_NULL;
            }
            switch (rfc) {
                case WebsocketCreationOperation.RFC_WEBSOCKET_ALREADY_EXISTS:
                case WebsocketCreationOperation.RFC_NO_KNOWN_WS_SERVER_FOR_SERVER:
                    break;
                case WebsocketCreationOperation.RFC_SSL_HOSTNAME_VERIFICATION_ERROR:
                case WebsocketCreationOperation.RFC_WELL_KNOWN_NOT_CACHED_YET:
                default:
                    scheduleNewWebsocketCreationQueueing(server);
            }
        } else if (operation instanceof IdentityRegistrationOperation) {
            Identity identity = ((IdentityRegistrationOperation) operation).getIdentity();
            Integer rfc = operation.getReasonForCancel();
            Logger.i("IdentityRegistrationOperation cancelled for reason " + rfc);
            if (rfc == null) {
                rfc = Operation.RFC_NULL;
            }
            switch (rfc) {
                case IdentityRegistrationOperation.RFC_WEBSOCKET_NOT_CONNECTED:
                case IdentityRegistrationOperation.RFC_WEBSOCKET_NOT_FOUND: {
                    resetWebsockets();
                    break;
                }
                case IdentityRegistrationOperation.RFC_NO_VALID_SERVER_SESSION: {
                    synchronized (awaitingServerSessionIdentitiesLock) {
                        awaitingServerSessionIdentities.add(identity);
                    }
                    createServerSessionDelegate.createServerSession(identity);
                    break;
                }
                default:
                    // What should we do? nothing
                    break;
            }
        }
    }

    private class ServerSessionCreatedNotificationListener implements NotificationListener {
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
            synchronized (awaitingServerSessionIdentitiesLock) {
                if (awaitingServerSessionIdentities.contains(identity)) {
                    UID deviceUid = ownedIdentityCurrentDeviceUids.get(identity);
                    if (deviceUid != null) {
                        queueIdentityRegistrationOperation(identity, deviceUid);
                    }
                    awaitingServerSessionIdentities.remove(identity);
                }
            }
        }
    }

    private class OwnedIdentityListUpdatedNotificationListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            synchronized (ownedIdentityAndUidsLock) {
                try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                    ownedIdentityAndUidsByServer.clear();
                    ownedIdentityCurrentDeviceUids.clear();
                    Identity[] ownedIdentities = fetchManagerSession.identityDelegate.getOwnedIdentities(fetchManagerSession.session);
                    for (Identity ownedIdentity: ownedIdentities) {
                        if (!fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity)) {
                            continue;
                        }
                        UID deviceUid = fetchManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(fetchManagerSession.session, ownedIdentity);
                        ownedIdentityCurrentDeviceUids.put(ownedIdentity, deviceUid);
                        String server = ownedIdentity.getServer();
                        List<IdentityAndUid> identityAndUids = ownedIdentityAndUidsByServer.get(server);
                        if (identityAndUids == null) {
                            identityAndUids = new ArrayList<>();
                            ownedIdentityAndUidsByServer.put(server, identityAndUids);
                        }
                        identityAndUids.add(new IdentityAndUid(ownedIdentity, deviceUid));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            resetWebsockets();
        }
    }

    private class WellKnownCacheNotificationListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            switch (notificationName) {
                case DownloadNotifications.NOTIFICATION_WELL_KNOWN_CACHE_INITIALIZED:
                case DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED: {
                    // maybe one day we can do something more subtle, but for now, we simply reconnect all websockets when an url might have changed
                    resetWebsockets();
                    break;
                }
            }
        }
    }

    private static class IdentityAndUid {
        public final Identity identity;
        public final UID deviceUid;

        IdentityAndUid(Identity identity, UID deviceUid) {
            this.identity = identity;
            this.deviceUid = deviceUid;
        }
    }

    private class WebsocketCreationOperation extends Operation {
        static final int RFC_WEBSOCKET_ALREADY_EXISTS = 1;
        static final int RFC_NO_KNOWN_WS_SERVER_FOR_SERVER = 2;
        static final int RFC_SSL_HOSTNAME_VERIFICATION_ERROR = 3;
        static final int RFC_WELL_KNOWN_NOT_CACHED_YET = 4;

        private final String server;

        private WebsocketCreationOperation(String server, OnCancelCallback onCancelCallback) {
            super(new UID(Suite.getHash(Hash.SHA256).digest(server.getBytes())), null, onCancelCallback);
            this.server = server;
        }

        public String getServer() {
            return server;
        }

        @Override
        public void doCancel() {
            // nothing to do
        }

        @Override
        public void doExecute() {
            boolean finished = false;
            try {
                if (!doConnect) {
                    finished = true;
                    return;
                }
                if (existingWebsockets.containsKey(server)) {
                    cancel(RFC_WEBSOCKET_ALREADY_EXISTS);
                    return;
                }
                // create the websocket connection
                URI wsUri;
                try {
                    wsUri = wellKnownCacheDelegate.getWsUri(server);
                } catch (WellKnownCoordinator.NotCachedException e) {
                    cancel(RFC_WELL_KNOWN_NOT_CACHED_YET);
                    return;
                }
                if (wsUri == null) {
                    cancel(RFC_NO_KNOWN_WS_SERVER_FOR_SERVER);
                    return;
                }
                WebSocketClient webSocketClient = new WebSocketClient(server, wsUri);
                webSocketClient.setSocketFactory(sslSocketFactory);
                webSocketClient.connectBlocking(WebSocketClient.CONNECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                try {
                    // hack to check whether SSL supports SNI (not supported on Android < 25)
                    Class.forName("javax.net.ssl.SNIHostName");
                    // verify the hostname in the SSL connection
                    HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
                    SSLSocket socket = (SSLSocket) webSocketClient.getSocket();
                    SSLSession s = socket.getSession();
                    if (!hv.verify(wsUri.getHost(), s)) {
                        webSocketClient.close();
                        cancel(RFC_SSL_HOSTNAME_VERIFICATION_ERROR);
                        try {
                            Logger.e("Websocket hostname verification error: expected " + wsUri.getHost() + ", found " + s.getPeerPrincipal());
                        } catch (SSLPeerUnverifiedException sslEx) {
                            // do nothing special, this occurs when there is no connection
                        }
                        return;
                    }
                } catch (ClassNotFoundException e) {
                    // SNI not supported --> hostname verification will fail, so don't do it!
                }
                if (webSocketClient.connectionException != null) {
                    throw webSocketClient.connectionException;
                }

                if (!doConnect) {
                    webSocketClient.closeConnection(0, null);
                }
                finished = true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (finished) {
                    setFinished();
                } else {
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        }
    }

    private class IdentityRegistrationOperation extends Operation {
        static final int RFC_WEBSOCKET_NOT_FOUND = 1;
        static final int RFC_WEBSOCKET_NOT_CONNECTED = 2;
        static final int RFC_NO_VALID_SERVER_SESSION = 3;

        private final Identity identity;
        private final UID deviceUid;

        private IdentityRegistrationOperation(Identity identity, UID deviceUid, OnCancelCallback onCancelCallback) {
            super(identity.computeUniqueUid(), null, onCancelCallback);
            this.identity = identity;
            this.deviceUid = deviceUid;
        }

        public Identity getIdentity() {
            return identity;
        }

        public UID getDeviceUid() {
            return deviceUid;
        }

        @Override
        public void doCancel() {
            // nothing to do
        }

        @Override
        public void doExecute() {
            boolean finished = false;
            try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()){
                try {
                    WebSocketClient webSocketClient = existingWebsockets.get(identity.getServer());
                    if (webSocketClient == null) {
                        cancel(RFC_WEBSOCKET_NOT_FOUND);
                        return;
                    }
                    if (!webSocketClient.isOpen()) {
                        cancel(RFC_WEBSOCKET_NOT_CONNECTED);
                        return;
                    }

                    byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, identity);
                    if (serverSessionToken == null) {
                        cancel(RFC_NO_VALID_SERVER_SESSION);
                        return;
                    }
                    ownedIdentityServerSessionTokens.put(identity, serverSessionToken);

                    Map<String, Object> messageMap = new HashMap<>();
                    messageMap.put("action", "register");
                    messageMap.put("identity", Base64.encodeBytes(identity.getBytes()));
                    messageMap.put("deviceUid", Base64.encodeBytes(deviceUid.getBytes()));
                    messageMap.put("token", Base64.encodeBytes(serverSessionToken));
                    if (os != null && osVersion != null && appBuild != 0 && appVersion != null) {
                        messageMap.put("os", os);
                        messageMap.put("osVersion", osVersion);
                        messageMap.put("appBuild", appBuild);
                        messageMap.put("appVersion", appVersion);
                    }

                    webSocketClient.send(jsonObjectMapper.writeValueAsString(messageMap));
                    finished = true;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (finished) {
                        setFinished();
                    } else {
                        if (hasNoReasonForCancel()) {
                            cancel(null);
                        }
                        processCancel();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                cancel(null);
                processCancel();
            }
        }
    }

    public void deleteReturnReceipt(Identity ownedIdentity, byte[] serverUid) {
        String server = ownedIdentity.getServer();
        WebSocketClient webSocketClient = existingWebsockets.get(server);
        if (webSocketClient != null) {
            try {
                Map<String, String> messageMap = new HashMap<>();
                messageMap.put("action", "delete_return_receipt");
                messageMap.put("serverUid", Base64.encodeBytes(serverUid));
                webSocketClient.send(jsonObjectMapper.writeValueAsString(messageMap));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private class WebSocketClient extends org.java_websocket.client.WebSocketClient {
        static final int CONNECTION_TIMEOUT_MILLIS = 5000;
        private final String server;

        private boolean thisWebsocketDidConnect = false;
        private Exception connectionException = null;

        private final AtomicLong pingCounter = new AtomicLong(0);
        private long lastPingCounter = -1;
        private long lastPingTimestamp = -1;

        WebSocketClient(String server, URI serverUri) {
            super(serverUri);
            this.server = server;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            existingWebsockets.put(server, this);
            thisWebsocketDidConnect = true;
            Logger.d("Websocket connected to " + uri.toString());
            if (notificationPostingDelegate != null) {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY, 1);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED, userInfo);
            }

            List<IdentityAndUid> identityAndUids = ownedIdentityAndUidsByServer.get(server);
            if (identityAndUids != null) {
                for (IdentityAndUid identityAndUid: identityAndUids) {
                    queueIdentityRegistrationOperation(identityAndUid.identity, identityAndUid.deviceUid);
                }
            }
            sendPing();
        }

        @Override
        public void onMessage(String message) {
            // we received a message, so the connection is functioning properly, we can reset the connection failed count
            scheduler.clearFailedCount(server);

            if (notificationPostingDelegate != null) {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY, 2);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED, userInfo);
            }

            Logger.d("Websocket received message " + message);
            Map<String, Object> receivedMessage;
            String action;
            try {
                receivedMessage = jsonObjectMapper.readValue(message, new TypeReference<>() {});
                action = (String) receivedMessage.get("action");
            } catch (Exception e) {
                Logger.i("Unable to parse websocket JSON message " + message);
                return;
            }
            if (action != null) {
                switch (action) {
                    case "register": {
                        Object identityObject = receivedMessage.get("identity");
                        if (!(identityObject instanceof String)) {
                            break;
                        }
                        try {
                            Identity identity = Identity.of(Base64.decode((String) identityObject));
                            if (!ownedIdentityCurrentDeviceUids.containsKey(identity)) {
                                // server sent an unknown identity!
                                break;
                            }
                            if (!receivedMessage.containsKey("err")) {
                                Logger.d("Successfully registered identity on websocket");
                                break;
                            }

                            Object errObject = receivedMessage.get("err");
                            int err = 255;
                            if (errObject instanceof Integer) {
                                err = (int) errObject;
                            }
                            switch ((byte) err) {
                                case ServerMethod.INVALID_SESSION: {
                                    if (ownedIdentityServerSessionTokens.get(identity) != null) {
                                        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                                            ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, ownedIdentityServerSessionTokens.get(identity), identity);
                                            fetchManagerSession.session.commit();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    synchronized (awaitingServerSessionIdentitiesLock) {
                                        awaitingServerSessionIdentities.add(identity);
                                    }
                                    createServerSessionDelegate.createServerSession(identity);
                                    break;
                                }
                                default: {
                                    // general error
                                    break;
                                }
                            }
                        } catch (IOException | DecodingException e) {
                            Logger.d("Error decoding identity");
                            e.printStackTrace();
                        }
                        break;
                    }
                    case "message": {
                        Object identityObject = receivedMessage.get("identity");
                        if (!(identityObject instanceof String)) {
                            break;
                        }
                        try {
                            Identity identity = Identity.of(Base64.decode((String) identityObject));
                            UID deviceUid = ownedIdentityCurrentDeviceUids.get(identity);
                            if (deviceUid == null) {
                                // server sent an unknown identity!
                                break;
                            }
                            Object messageObject = receivedMessage.get("message");
                            if (messageObject instanceof String) {
                                try {
                                    byte[] messagePayload = Base64.decode((String) messageObject);
                                    downloadMessagesAndListAttachmentsDelegate.processWebsocketDownloadedMessage(identity, deviceUid, messagePayload);
                                    break;
                                } catch (Exception e) {
                                    // if base64 decoding fails, revert to usual list
                                }
                            }
                            downloadMessagesAndListAttachmentsDelegate.downloadMessagesAndListAttachments(identity, deviceUid);
                        } catch (IOException | DecodingException e) {
                            Logger.d("Error decoding identity");
                            e.printStackTrace();
                        }
                        break;
                    }
                    case "return_receipt": {
                        Object identityObject = receivedMessage.get("identity");
                        Object serverUidObject = receivedMessage.get("serverUid");
                        Object nonceObject = receivedMessage.get("nonce");
                        Object encryptedPayloadObject = receivedMessage.get("encryptedPayload");
                        Object timestampObject = receivedMessage.get("timestamp");
                        if (identityObject != null && serverUidObject != null && nonceObject != null && encryptedPayloadObject != null && timestampObject != null) {
                            try {
                                Identity identity = Identity.of(Base64.decode((String) identityObject));
                                byte[] serverUid = Base64.decode((String) serverUidObject);
                                byte[] nonce = Base64.decode((String) nonceObject);
                                byte[] encryptedPayload = Base64.decode((String) encryptedPayloadObject);
                                long timestamp = (long) timestampObject;

                                if (notificationPostingDelegate != null) {
                                    HashMap<String, Object> userInfo = new HashMap<>();
                                    userInfo.put(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_OWNED_IDENTITY_KEY, identity);
                                    userInfo.put(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY, serverUid);
                                    userInfo.put(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_NONCE_KEY, nonce);
                                    userInfo.put(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY, encryptedPayload);
                                    userInfo.put(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY, timestamp);

                                    notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED, userInfo);
                                }
                            } catch (Exception e) {
                                Logger.d("Error parsing return receipt");
                                e.printStackTrace();
                            }
                        }
                        break;
                    }
                    case "push_topic": {
                        Object pushTopicObject = receivedMessage.get("topic");
                        if (pushTopicObject != null) {
                            try {
                                String pushTopic = (String) pushTopicObject;
                                if (notificationPostingDelegate != null) {
                                    HashMap<String, Object> userInfo = new HashMap<>();
                                    userInfo.put(DownloadNotifications.NOTIFICATION_PUSH_TOPIC_NOTIFIED_TOPIC_KEY, pushTopic);

                                    notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_PUSH_TOPIC_NOTIFIED, userInfo);
                                }
                            } catch (Exception e) {
                                Logger.d("Error parsing push topic");
                                e.printStackTrace();
                            }
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public PingFrame onPreparePing(WebSocket conn) {
            PingFrame pingFrame = new PingFrame();
            long counter = pingCounter.incrementAndGet();
            long timestamp = System.currentTimeMillis();
            if (lastPingCounter != -1) {
                if (timestamp - lastPingTimestamp > 5_000 && notificationPostingDelegate != null) {
                    notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_PING_LOST, new HashMap<>());
                }
            }
            lastPingCounter = counter;
            lastPingTimestamp = timestamp;
            Encoded encoded = Encoded.of(new Encoded[] {
                    Encoded.of(counter),
                    Encoded.of(System.currentTimeMillis()),
            });
            pingFrame.setPayload(ByteBuffer.wrap(encoded.getBytes()));
            return pingFrame;
        }

        @Override
        public void onWebsocketPong(WebSocket conn, Framedata f) {
            ByteBuffer buffer = f.getPayloadData();
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);
            long counter = -1;
            long timestamp = -1;
            try {
                Encoded[] encodeds = new Encoded(payload).decodeList();
                counter = encodeds[0].decodeLong();
                timestamp = encodeds[1].decodeLong();
            } catch (Exception ignored) {
                // this is treated after
            }
            if (notificationPostingDelegate != null) {
                if (counter == lastPingCounter && timestamp != -1) {
                    lastPingCounter = -1;
                    long delay = System.currentTimeMillis() - timestamp;
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(DownloadNotifications.NOTIFICATION_PING_RECEIVED_DELAY_KEY, delay);
                    notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_PING_RECEIVED, userInfo);
                }
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY, 2);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED, userInfo);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            if (notificationPostingDelegate != null) {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY, 0);
                notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED, userInfo);
            }
            existingWebsockets.remove(server);
            if (thisWebsocketDidConnect) {
                if (remote) {
                    Logger.d("Websocket remotely disconnected from " + uri.toString());
                } else {
                    Logger.d("Websocket locally disconnected from " + uri.toString());
                }
                if (doConnect) {
                    scheduleNewWebsocketCreationQueueing(server);
                }
            }
        }

        @Override
        public void onError(Exception ex) {
            if (!thisWebsocketDidConnect) {
                connectionException = ex;
            } else {
                Logger.w("Websocket exception");
                ex.printStackTrace();
            }
        }


        // since WeSocket 1.5.0 the native method uses sslParameters.setEndpointIdentificationAlgorithm which is not supported for Android API level < 24
        //  --> we do the check manually
        @Override
        protected void onSetSSLParameters(SSLParameters sslParameters) { }
    }
}
