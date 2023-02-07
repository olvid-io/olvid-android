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

package io.olvid.messenger.webclient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.datatypes.JsonMessage;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.Colissimo;
import io.olvid.messenger.webclient.protobuf.ConnectionColissimoOuterClass.ConnectionColissimo;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

class WebsocketClient extends WebSocketListener {
    private final String serverUrl;
    private final WebClientManager manager;
    private final MessageHandler messageHandler;
    private final OkHttpClient okHttpClient;
    @Nullable private WebSocket webSocket;

    private boolean connected = false;

    public WebsocketClient(String serverUrl, String olvidSessionCookie, @Nullable String awsSessionCookieName, @Nullable String awsSessionCookie, WebClientManager manager) {
        this.serverUrl = serverUrl;
        this.manager = manager;
        this.messageHandler = new MessageHandler(this.manager);
        this.webSocket = null;

        SSLSocketFactory sslSocketFactory = AppSingleton.getSslSocketFactory();

        X509TrustManager trustManager = null;
        if (sslSocketFactory != null) {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                    throw new IllegalStateException("Unexpected default trust managers:"
                            + Arrays.toString(trustManagers));
                }
                trustManager = (X509TrustManager) trustManagers[0];
            } catch (Exception e) {
                Logger.e("Error initializing websocket okHttpClient trustManager");
                e.printStackTrace();
            }
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .pingInterval(Constants.WEBSOCKET_PING_INTERVAL, TimeUnit.MILLISECONDS)
                .connectTimeout(Constants.CONNECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .cookieJar(new WebsocketCookies(serverUrl, olvidSessionCookie, awsSessionCookieName, awsSessionCookie));

        if (trustManager != null) {
            builder.sslSocketFactory(sslSocketFactory, trustManager);
        }

        okHttpClient = builder.build();
        connect();
    }

    private void connect() {
        Logger.i("Initiating webclient websocket connection");
        this.webSocket = okHttpClient.newWebSocket(new Request.Builder().url(serverUrl).build(), this);
    }

    public void close() {
        Logger.i("Closing webclient websocket connection");
        this.connected = false;
        if (this.webSocket != null) {
            this.webSocket.cancel();
        }
        this.manager.handlerWebsocketClosed();
    }

    public void reconnect() {
        this.connected = false;
        if (this.webSocket != null) {
            this.webSocket.cancel();
        }
        connect();
    }

    public boolean isConnected() {
        return connected;
    }


    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        this.connected = true;
        this.manager.handlerWebsocketConnected();
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String message) {
        messageHandler.handle(message);
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        Logger.e("Webclient websocket received binary message!");
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        if (connected) {
            Logger.w("Webclient websocket closed on failure");
            t.printStackTrace();
        }
        close();
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        if(code != -1){
            Logger.w("Webclient websocket closed with exit code " + code + "; reason: " + reason);
        }
        close();
    }


    public boolean registerConnection(String identifier) {
        JsonMessage.RegisterConnection registerConnection;
        String jsonMessageAsString;

        byte[] token = AppSingleton.getEngine().getServerAuthenticationToken(manager.getBytesCurrentOwnedIdentity());
        registerConnection = new JsonMessage.RegisterConnection(identifier, manager.getBytesCurrentOwnedIdentity(), token);
        try {
            jsonMessageAsString = AppSingleton.getJsonObjectMapper().writeValueAsString(registerConnection);
        } catch (JsonProcessingException e) {
            Logger.e("Unable to stringify register connection message");
            return false;
        }
        try {
            if (this.webSocket != null) {
                this.webSocket.send(jsonMessageAsString);
            } else {
                throw new Exception("Webclient websocket is null");
            }
        } catch (Exception e) {
            Logger.e("Unable to post registerConnection message on websocket");
            e.printStackTrace();
            return false;
        }
        Logger.d("Registering connection: " + jsonMessageAsString);
        return true;
    }

    public boolean registerCorresponding(String identifier) {
        JsonMessage.RegisterCorresponding registerCorresponding;
        String jsonMessageAsString;

        registerCorresponding = new JsonMessage.RegisterCorresponding(identifier, Constants.VERSION);
        try {
            jsonMessageAsString = AppSingleton.getJsonObjectMapper().writeValueAsString(registerCorresponding);
        } catch (JsonProcessingException e) {
            Logger.e("Unable to stringify register corresponding message");
            return false;
        }
        try {
            if (this.webSocket != null) {
                this.webSocket.send(jsonMessageAsString);
            } else {
                throw new Exception("Webclient websocket is null");
            }
        } catch (Exception e) {
            Logger.e("Unable to post registerCorresponding message on websocket");
            e.printStackTrace();
            return false;
        }
        Logger.d("Registering corresponding: " + jsonMessageAsString);
        return true;
    }

    // connection colissimo are not encrypted
    public boolean sendConnectionColissimo(ConnectionColissimo connectionColissimo) {
        JsonMessage.Connection connectionMessage;
        String jsonMessageAsString;

        Logger.d("Sending connection colissimo: " + connectionColissimo.getType());
        connectionMessage = new JsonMessage.Connection(connectionColissimo.toByteArray());
        try {
            jsonMessageAsString = AppSingleton.getJsonObjectMapper().writeValueAsString(connectionMessage);
        } catch (JsonProcessingException e) {
            Logger.e("Unable to stringify connection message");
            return (false);
        }
        try {
            if (this.webSocket != null) {
                this.webSocket.send(jsonMessageAsString);
            } else {
                throw new Exception("Webclient websocket is null");
            }
        } catch (Exception e) {
            Logger.e("Unable to post connectionColissimo on websocket");
            e.printStackTrace();
            return (false);
        }
        return (true);
    }

    public boolean sendColissimo(Colissimo colissimo) {
        JsonMessage.Relay relayMessage;
        byte[] encryptedColissimo;
        String jsonMessageString;

        Logger.d("Sending colissimo: " + colissimo.getType());
        encryptedColissimo = this.manager.encrypt(colissimo.toByteArray());
        if (encryptedColissimo == null) {
            Logger.e("Unable to encrypt colissimo");
            return false;
        }
        relayMessage = new JsonMessage.Relay(encryptedColissimo);
        try {
            jsonMessageString = AppSingleton.getJsonObjectMapper().writeValueAsString(relayMessage);
        } catch (JsonProcessingException e) {
            Logger.e("Unable to stringify relay message");
            return false;
        }
        if (this.webSocket != null) {
            this.webSocket.send(jsonMessageString);
            return true;
        }
        return false;
    }

    // this is for this method that we had to patch websocket library: we need to access connection out queue to determine if it is overloaded or not
    public long getConnectionOutputBufferSize() {
        if (this.webSocket != null) {
            return this.webSocket.queueSize();
        } else {
            return 0;
        }
    }

    private static class WebsocketCookies implements CookieJar {
        private final String serverUrl;
        private final String olvidSessionCookie;
        private final String awsSessionCookieName;
        private final String awsSessionCookie;

        WebsocketCookies(String serverUrl, String olvidSessionCookie, String awsSessionCookieName, String awsSessionCookie) {
            this.serverUrl = serverUrl;
            this.olvidSessionCookie = olvidSessionCookie;
            this.awsSessionCookieName = awsSessionCookieName;
            this.awsSessionCookie = awsSessionCookie;
        }

        @Override
        public void saveFromResponse(@NonNull HttpUrl httpUrl, @NonNull List<Cookie> list) {}

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl httpUrl) {
            String hostname = URI.create(serverUrl).getHost();
            ArrayList<Cookie> cookies = new ArrayList<>(2);
            if (hostname == null) {
                return cookies;
            }

            if (olvidSessionCookie != null) {
                Logger.d("WebsocketClient: olvidSession=" + olvidSessionCookie);
                cookies.add(new Cookie.Builder()
                        .name("olvidSession")
                        .value(olvidSessionCookie)
                        .domain(hostname)
                        .build());
            }

            if (awsSessionCookie != null && awsSessionCookieName != null) {
                Logger.d("WebsocketClient: " + awsSessionCookieName + "=" + awsSessionCookie);
                cookies.add(new Cookie.Builder()
                        .name(awsSessionCookieName)
                        .value(awsSessionCookie)
                        .domain(hostname)
                        .build());
            }

            return cookies;
        }
    }
}
