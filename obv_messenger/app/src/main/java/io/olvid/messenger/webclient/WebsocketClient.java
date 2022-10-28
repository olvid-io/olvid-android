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

package io.olvid.messenger.webclient;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.framing.ContinuousFrame;
import org.java_websocket_olvid.framing.DataFrame;
import org.java_websocket_olvid.framing.Framedata;
import org.java_websocket_olvid.framing.TextFrame;
import org.java_websocket_olvid.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.datatypes.JsonMessage;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.Colissimo;
import io.olvid.messenger.webclient.protobuf.ConnectionColissimoOuterClass.ConnectionColissimo;

class WebsocketClient extends WebSocketClient {
    private final WebClientManager manager;
    private final MessageHandler messageHandler;

    public WebsocketClient(URI serverUri, WebClientManager manager) {
        super(serverUri);
        this.manager = manager;
        this.messageHandler = new MessageHandler(this.manager);
        this.setSocketFactory(AppSingleton.getSslSocketFactory());

        // if not using wss protocol ignore host verification
        if (!Pattern.matches("^wss.+", serverUri.toString())) {
            this.connect();
        // manual certificate verification
        } else {
            try {
                this.connectBlocking(Constants.CONNECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                try {
                    // hack to check whether SSL supports SNI (not supported on Android < 25)
                    Class.forName("javax.net.ssl.SNIHostName");
                    // verify the hostname in the SSL connection
                    HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
                    SSLSocket socket = (SSLSocket) getSocket();
                    SSLSession s = socket.getSession();
                    if (!hv.verify(serverUri.getHost(), s)) {
                        this.manager.handlerWebsocketError();
                        Logger.e("Websocket hostname verification error: expected " + serverUri.getHost() + ", found " + s.getPeerPrincipal());
                        close();
                    }
                } catch (ClassNotFoundException e) {
                    // SNI not supported --> hostname verification will fail, so don't do it!
                }
            } catch (InterruptedException | SSLPeerUnverifiedException e) {
                // do nothing
            }
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        this.manager.handlerWebsocketConnected();
    }

    @Override
    public void onMessage(String message) { messageHandler.handle(message); }

    @Override
    public void onError(Exception ex) { this.manager.handlerWebsocketError(); }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if(code != -1){
            Logger.w("Websocket closed with exit code " + code + "; reason: " + reason);
        }
        this.manager.handlerWebsocketClosed();
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
            this.send(jsonMessageAsString);
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
            this.send(jsonMessageAsString);
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
            this.send(jsonMessageAsString);
        }
        catch (Exception e) {
            Logger.e("Unable to post connectionColissimo on websocket");
            e.printStackTrace();
            return (false);
        }
        return (true);
    }

    public boolean sendColissimo(Colissimo colissimo) {
        JsonMessage.Relay relayMessage;
        byte[] encryptedColissimo;
        byte[] jsonMessageBytes;

        Logger.d("Sending colissimo: " + colissimo.getType());
        encryptedColissimo = this.manager.encrypt(colissimo.toByteArray());
        if (encryptedColissimo == null) {
            Logger.e("Unable to encrypt colissimo");
            return false;
        }
        relayMessage = new JsonMessage.Relay(encryptedColissimo);
        try {
            jsonMessageBytes = AppSingleton.getJsonObjectMapper().writeValueAsBytes(relayMessage);
        } catch (JsonProcessingException e) {
            Logger.e("Unable to stringify relay message");
            return false;
        }
        // split messages into frames for aws server that limit message size
        try {
            if (jsonMessageBytes.length > Constants.MAX_FRAME_SIZE * Constants.MAX_FRAME_COUNT) {
                // frame too long for AWS
                Logger.e("Error, trying to send a colissimo over 128k");
                throw new Exception();
            } else {
                List<Framedata> frames = new ArrayList<>();
                int offset = 0;
                while (offset < jsonMessageBytes.length) {
                    int frameLength = Math.min(jsonMessageBytes.length - offset, Constants.MAX_FRAME_SIZE);
                    DataFrame frame;
                    if (offset == 0) { // first frame sets the type of the multi-frame message
                        frame = new TextFrame();
                    } else {
                        frame = new ContinuousFrame();
                    }
                    frame.setPayload(ByteBuffer.wrap(Arrays.copyOfRange(jsonMessageBytes, offset, offset + frameLength)));
                    frame.setTransferemasked(true);
                    offset += frameLength;

                    frame.setFin(offset == jsonMessageBytes.length); // only the last frame must be tagged as Fin
                    frames.add(frame);
                }
                this.sendFrame(frames);
            }
        } catch (Exception e) {
            Logger.e("Unable to post colissimo on websocket");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    // since WeSocket 1.5.0 the native method uses sslParameters.setEndpointIdentificationAlgorithm which is not supported for Android API level < 24
    //  --> we do the check manually
    @Override
    protected void onSetSSLParameters(SSLParameters sslParameters) { }

    // this is for this method that we had to patch websocket library: we need to access connection out queue to determine if it is overloaded or not
    public int getConnectionOutputBufferSize() {
        return this.engine.outQueue.size();
    }
}
