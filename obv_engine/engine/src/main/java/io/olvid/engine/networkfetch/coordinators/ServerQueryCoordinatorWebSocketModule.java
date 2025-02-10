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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelServerResponseMessageToSend;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.networkfetch.databases.PendingServerQuery;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ServerQueryCoordinatorWebSocketModule {
    private static final int ERROR_CODE_GENERAL_ERROR = -1;
    private static final int ERROR_CODE_UNKNOWN_SESSION_NUMBER = 1;
    private static final int ERROR_CODE_OTHER_DISCONNECTED = 2;
    private static final int ERROR_CODE_PAYLOAD_TOO_LARGE = 3;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final ObjectMapper jsonObjectMapper;
    private final PRNGService prng;
    private final HashMap<UID, WebSocketClientAndServerQuery> webSocketsMap;
    private final NoExceptionSingleThreadExecutor executor;
    private ChannelDelegate channelDelegate;


    public ServerQueryCoordinatorWebSocketModule(FetchManagerSessionFactory fetchManagerSessionFactory,
                                                 SSLSocketFactory sslSocketFactory,
                                                 ObjectMapper jsonObjectMapper,
                                                 PRNGService prng) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.jsonObjectMapper = jsonObjectMapper;
        this.prng = prng;
        this.webSocketsMap = new HashMap<>();
        this.executor = new NoExceptionSingleThreadExecutor("ServerQueryCoordinatorWebSocketModule executor");
    }

    public void setChannelDelegate(ChannelDelegate channelDelegate) {
        this.channelDelegate = channelDelegate;
    }

    public void handleServerQuery(PendingServerQuery pendingServerQuery, boolean calledFromOnOpen) {
        executor.execute(() -> {
            ServerQuery serverQuery;
            try {
                serverQuery = ServerQuery.of(pendingServerQuery.getEncodedQuery());
            } catch (DecodingException e) {
                Logger.x(e);
                return;
            }

            final UID protocolInstanceUid;
            //noinspection CommentedOutCode
            try {
                Encoded[] listOfEncoded = serverQuery.getEncodedElements().decodeList();
                protocolInstanceUid = listOfEncoded[1].decodeUid();
/*
            int protocolId = (int) listOfEncoded[0].decodeLong();
            protocolInstanceUid = listOfEncoded[1].decodeUid();
            int protocolMessageId = (int) listOfEncoded[2].decodeLong();
            Encoded[] inputs = listOfEncoded[3].decodeList();
*/
            } catch (ArrayIndexOutOfBoundsException | DecodingException e) {
                // we cannot respond to the protocol for a proper fail, so we simply ignore the message
                Logger.e("ServerQueryCoordinatorWebSocketModule.handlePendingServerQuery() failed to decode received serverQuery");
                return;
            }

            try {
                WebSocketClientAndServerQuery webSocketClientAndQuery = webSocketsMap.get(protocolInstanceUid);
                if (webSocketClientAndQuery == null) {
                    // only the source and target message can trigger a websocket connection
                    if (serverQuery.getType().getId() != ServerQuery.TypeId.TRANSFER_SOURCE_QUERY_ID
                            && serverQuery.getType().getId() != ServerQuery.TypeId.TRANSFER_TARGET_QUERY_ID) {
                        sendFailedServerQueryResponse(pendingServerQuery);
                        return;
                    }

                    // we do not handle the message yet, once the websocket is connected, it will be automatically handled
                    WebSocketClient webSocketClient = new WebSocketClient(protocolInstanceUid);
                    webSocketClientAndQuery = new WebSocketClientAndServerQuery(webSocketClient, pendingServerQuery);
                    webSocketsMap.put(protocolInstanceUid, webSocketClientAndQuery);
                    webSocketClientAndQuery.webSocketClient.connect();
                    return;
                }

                if (!calledFromOnOpen) {
                    if (webSocketClientAndQuery.pendingServerQuery != null) {
                        try {
                            // check if the existing pendingServerQuery is a noResponseExpected relay message
                            ServerQuery previousServerQuery = ServerQuery.of(webSocketClientAndQuery.pendingServerQuery.getEncodedQuery());
                            if (previousServerQuery.getType().getId() == ServerQuery.TypeId.TRANSFER_RELAY_QUERY_ID
                                 && ((ServerQuery.TransferRelayQuery) previousServerQuery.getType()).noResponseExpected) {
                                // in that case, delete the previous pendingServerQuery
                                try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                                    PendingServerQuery rePendingServerQuery = PendingServerQuery.get(fetchManagerSession, webSocketClientAndQuery.pendingServerQuery.getUid());
                                    if (rePendingServerQuery != null) {
                                        rePendingServerQuery.delete();
                                        fetchManagerSession.session.commit();
                                    }
                                }
                            } else {
                                // we have a message to send but never responded to the previous message --> this should never happen!
                                failProtocol(protocolInstanceUid);
                                return;
                            }
                        } catch (Exception ignored) { }
                    }

                    webSocketClientAndQuery.pendingServerQuery = pendingServerQuery;
                }

                if (webSocketClientAndQuery.webSocketClient.connectionStatus != WebSocketClient.ConnectionStatus.CONNECTED) {
                    // we received a message to send before the websocket ever got a chance to connect --> this should never happen!
                    failProtocol(protocolInstanceUid);
                    return;
                }

                switch (serverQuery.getType().getId()) {
                    case TRANSFER_SOURCE_QUERY_ID: {
                        JsonRequestSource request = new JsonRequestSource();
                        request.action = "source";

                        webSocketClientAndQuery.webSocketClient.send(jsonObjectMapper.writeValueAsString(request));
                        break;
                    }
                    case TRANSFER_TARGET_QUERY_ID: {
                        ServerQuery.TransferTargetQuery transferTargetQuery = (ServerQuery.TransferTargetQuery) serverQuery.getType();
                        JsonRequestTarget request = new JsonRequestTarget();
                        request.action = "target";
                        request.sessionNumber = transferTargetQuery.sessionNumber;
                        request.payload = transferTargetQuery.payload;

                        webSocketClientAndQuery.webSocketClient.send(jsonObjectMapper.writeValueAsString(request));
                        break;
                    }
                    case TRANSFER_RELAY_QUERY_ID: {
                        ServerQuery.TransferRelayQuery transferRelayQuery = (ServerQuery.TransferRelayQuery) serverQuery.getType();
                        JsonRequestRelay request = new JsonRequestRelay();
                        request.action = "relay";
                        request.relayConnectionId = transferRelayQuery.connectionIdentifier;
                        request.payload = transferRelayQuery.payload;

                        if (request.payload.length > Constants.TRANSFER_MAX_PAYLOAD_SIZE) {
                            int totalFragments = ((transferRelayQuery.payload.length - 1) / Constants.TRANSFER_MAX_PAYLOAD_SIZE) + 1;
                            request.totalFragments = totalFragments;
                            for (int i=0; i<totalFragments; i++) {
                                request.fragmentNumber = i;
                                request.payload = Arrays.copyOfRange(transferRelayQuery.payload, i* Constants.TRANSFER_MAX_PAYLOAD_SIZE, Math.min((i+1)* Constants.TRANSFER_MAX_PAYLOAD_SIZE, transferRelayQuery.payload.length));
                                webSocketClientAndQuery.webSocketClient.send(jsonObjectMapper.writeValueAsString(request));
                            }
                        } else {
                            webSocketClientAndQuery.webSocketClient.send(jsonObjectMapper.writeValueAsString(request));
                        }
                        break;
                    }
                    case TRANSFER_WAIT_QUERY_ID: {
                        String pendingMessage = webSocketClientAndQuery.webSocketClient.messageWaitingForWait;
                        if (pendingMessage != null) {
                            webSocketClientAndQuery.webSocketClient.messageWaitingForWait = null;
                            webSocketClientAndQuery.webSocketClient.onMessage(pendingMessage);
                        }
                        break;
                    }
                    case TRANSFER_CLOSE_QUERY_ID: {
                        ServerQuery.TransferCloseQuery transferCloseQuery = (ServerQuery.TransferCloseQuery) serverQuery.getType();

                        // close the websocket
                        if (transferCloseQuery.abort) {
                            webSocketClientAndQuery.webSocketClient.webSocket.cancel();
                        } else {
                            webSocketClientAndQuery.webSocketClient.webSocket.close(1000, "");
                        }
                        webSocketsMap.remove(protocolInstanceUid);
                        break;
                    }
                    case DEVICE_DISCOVERY_QUERY_ID:
                    case PUT_USER_DATA_QUERY_ID:
                    case GET_USER_DATA_QUERY_ID:
                    case CHECK_KEYCLOAK_REVOCATION_QUERY_ID:
                    case CREATE_GROUP_BLOB_QUERY_ID:
                    case GET_GROUP_BLOB_QUERY_ID:
                    case LOCK_GROUP_BLOB_QUERY_ID:
                    case UPDATE_GROUP_BLOB_QUERY_ID:
                    case PUT_GROUP_LOG_QUERY_ID:
                    case DELETE_GROUP_BLOB_QUERY_ID:
                    case GET_KEYCLOAK_DATA_QUERY_ID:
                    case OWNED_DEVICE_DISCOVERY_QUERY_ID:
                    case DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID:
                    case DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID:
                    case DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID:
                    case REGISTER_API_KEY_QUERY_ID:
                    case UPLOAD_PRE_KEY_QUERY_ID:
                    default: {
                        Logger.e("ServerQueryCoordinatorWebSocketModule.handlePendingServerQuery() received serverQuery with type " + serverQuery.getType().getId());
                        failProtocol(protocolInstanceUid);
                        break;
                    }
                }
            } catch (Exception e) {
                Logger.x(e);
                failProtocol(protocolInstanceUid);
            }
        });
    }


    private void failProtocol(UID protocolInstanceUid) {
        Logger.i("ServerQueryCoordinatorWebSocketModule.failProtocol called");
        WebSocketClientAndServerQuery clientAndQuery = webSocketsMap.get(protocolInstanceUid);
        if (clientAndQuery != null) {
            webSocketsMap.remove(protocolInstanceUid);
            if (clientAndQuery.webSocketClient.connectionStatus != WebSocketClient.ConnectionStatus.DISCONNECTED && clientAndQuery.webSocketClient.webSocket != null) {
                clientAndQuery.webSocketClient.webSocket.cancel();
            }
            if (clientAndQuery.pendingServerQuery != null) {
                sendFailedServerQueryResponse(clientAndQuery.pendingServerQuery);
            }
        }
    }

    private void sendFailedServerQueryResponse(PendingServerQuery pendingServerQuery) {
        // we notify the protocol the request failed by sending a null response
        sendServerQueryResponse(pendingServerQuery, null);
    }

    private void sendServerQueryResponse(PendingServerQuery pendingServerQuery, String response) {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            boolean success = false;
            try {
                ServerQuery serverQuery = ServerQuery.of(pendingServerQuery.getEncodedQuery());
                fetchManagerSession.session.startTransaction();
                if (serverQuery.getType().getId() != ServerQuery.TypeId.TRANSFER_CLOSE_QUERY_ID) {
                    ChannelServerResponseMessageToSend channelServerResponseMessageToSend = new ChannelServerResponseMessageToSend(
                            serverQuery.getOwnedIdentity(),
                            response == null ? null : Encoded.of(response),
                            serverQuery.getEncodedElements()
                    );
                    channelDelegate.post(fetchManagerSession.session, channelServerResponseMessageToSend, prng);
                }
                PendingServerQuery rePendingServerQuery = PendingServerQuery.get(fetchManagerSession, pendingServerQuery.getUid());
                if (rePendingServerQuery != null) {
                    rePendingServerQuery.delete();
                }
                success = true;
            } catch (Exception e) {
                Logger.x(e);
            } finally {
                if (success) {
                    fetchManagerSession.session.commit();
                } else {
                    fetchManagerSession.session.rollback();
                }
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    public class WebSocketClient extends WebSocketListener {
        OkHttpClient okHttpClient;
        private WebSocket webSocket;
        private ConnectionStatus connectionStatus;
        private final UID protocolInstanceUid;

        private String messageWaitingForWait;

        WebSocketClient(UID protocolInstanceUid) {
            this.okHttpClient = WebsocketCoordinator.initializeOkHttpClientForWebSocket(sslSocketFactory);
            this.protocolInstanceUid = protocolInstanceUid;
            this.connectionStatus = ConnectionStatus.INITIALIZING;
            this.webSocket = null;
        }

        public void connect() {
            this.webSocket = okHttpClient.newWebSocket(new Request.Builder().url(Constants.TRANSFER_WS_SERVER_URL).build(), this);
        }

        public void send(String message) {
            webSocket.send(message);
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            executor.execute(() -> {
                // once connected, process the pending message
                connectionStatus = ConnectionStatus.CONNECTED;
                WebSocketClientAndServerQuery clientAndQuery = webSocketsMap.get(protocolInstanceUid);
                if (clientAndQuery != null && clientAndQuery.pendingServerQuery != null) {
                    try {
                        handleServerQuery(clientAndQuery.pendingServerQuery, true);
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                }
            });
        }

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            if (code != 1000) {
                executor.execute(this::closeAndAbort);
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            Logger.x(t);
            executor.execute(this::closeAndAbort);
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            onMessage(text);
        }

        private void onMessage(String text) {
            // ignore empty messages
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            executor.execute(() -> {
                // first, try to parse error messages
                try {
                    JsonResponseFail fail = jsonObjectMapper.readValue(text, JsonResponseFail.class);
                    switch (fail.errorCode) {
                        case ERROR_CODE_UNKNOWN_SESSION_NUMBER:
                        case ERROR_CODE_OTHER_DISCONNECTED:
                        case ERROR_CODE_PAYLOAD_TOO_LARGE:
                        case ERROR_CODE_GENERAL_ERROR: {
                            // for now we do not care what type of error we receive, but in the future we may want
                            // to inform the user about what went wrong and then need to differentiate based on error code
                            failProtocol(protocolInstanceUid);
                            return;
                        }
                    }
                } catch (Exception ignored) {
                }

                WebSocketClientAndServerQuery clientAndQuery = webSocketsMap.get(protocolInstanceUid);

                if (clientAndQuery == null) {
                    // protocol ended, we can ignore the message
                    return;
                }
                PendingServerQuery pendingServerQuery = clientAndQuery.pendingServerQuery;
                if (pendingServerQuery == null) {
                    // we don't have a server query to respond to yet, put the message on hold
                    messageWaitingForWait = text;
                    return;
                }

                // check if the message was truncated or not
                try {
                    JsonResponse fragmentedResponse = jsonObjectMapper.readValue(text, JsonResponse.class);
                    if (fragmentedResponse.totalFragments != null 
                            && fragmentedResponse.fragmentNumber != null 
                            && fragmentedResponse.fragmentNumber >= 0 
                            && fragmentedResponse.fragmentNumber < fragmentedResponse.totalFragments) {
                        // we have a fragmented response --> store it in the clientAndQuery
                        if (clientAndQuery.fragmentedResponse == null) {
                            clientAndQuery.fragmentedResponse = new HashMap<>();
                        }
                        clientAndQuery.fragmentedResponse.put(fragmentedResponse.fragmentNumber, fragmentedResponse);
                        if (clientAndQuery.fragmentedResponse.size() == fragmentedResponse.totalFragments) {
                            // we have all the fragments, reconstruct them
                            JsonResponse reconstructedResponse = new JsonResponse();
                            int payloadSize = 0;
                            for (int i = 0; i < fragmentedResponse.totalFragments; i++) {
                                payloadSize += clientAndQuery.fragmentedResponse.get(i).payload.length;
                            }
                            reconstructedResponse.otherConnectionId = clientAndQuery.fragmentedResponse.get(0).otherConnectionId;
                            reconstructedResponse.payload = new byte[payloadSize];
                            int offset = 0;
                            for (int i = 0; i < fragmentedResponse.totalFragments; i++) {
                                JsonResponse fragment = clientAndQuery.fragmentedResponse.get(i);
                                if (!Objects.equals(reconstructedResponse.otherConnectionId, fragment.otherConnectionId)) {
                                    throw new Exception("otherConnectionId mismatch");
                                }
                                System.arraycopy(fragment.payload, 0, reconstructedResponse.payload, offset, fragment.payload.length);
                                offset += fragment.payload.length;
                            }
                            clientAndQuery.fragmentedResponse = null;
                            clientAndQuery.pendingServerQuery = null;
                            sendServerQueryResponse(pendingServerQuery, jsonObjectMapper.writeValueAsString(reconstructedResponse));
                        }
                        return;
                    }
                } catch (Exception e) {
                    // in case of exception when parsing, ignore it and simply forward the response to the protocol
                    Logger.x(e);
                }

                // remove the pendingServerQuery
                clientAndQuery.pendingServerQuery = null;

                // We do not parse the response here, the protocol will take care of this
                sendServerQueryResponse(pendingServerQuery, text);
            });
        }

        private void closeAndAbort() {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            failProtocol(protocolInstanceUid);
        }

        public enum ConnectionStatus {
            INITIALIZING,
            CONNECTED,
            DISCONNECTED,
        }
    }

    public static final class WebSocketClientAndServerQuery {
        public final WebSocketClient webSocketClient;
        public PendingServerQuery pendingServerQuery; // null after we have replied to a message
        public HashMap<Integer, JsonResponse> fragmentedResponse;

        public WebSocketClientAndServerQuery(WebSocketClient webSocketClient, PendingServerQuery pendingServerQuery) {
            this.webSocketClient = webSocketClient;
            this.pendingServerQuery = pendingServerQuery;
            this.fragmentedResponse = null;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonResponseFail {
        public int errorCode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonRequestSource {
       public String action;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonRequestTarget {
        public String action;
        public long sessionNumber;
        public byte[] payload;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonRequestRelay {
        public String action;
        public String relayConnectionId;
        public byte[] payload;
        public Integer fragmentNumber;
        public Integer totalFragments;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonResponse {
        public String otherConnectionId;
        public byte[] payload;
        public Integer fragmentNumber;
        public Integer totalFragments;
    }
}
