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

package io.olvid.engine.protocol.protocols;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvDeviceManagementRequest;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class OwnedDeviceManagementProtocol extends ConcreteProtocol {
    public OwnedDeviceManagementProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return OWNED_DEVICE_MANAGEMENT_PROTOCOL_ID;
    }

    // region States

    public static final int REQUEST_SENT_STATE_ID = 1;
    public static final int RESPONSE_PROCESSED_STATE_ID = 2;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{RESPONSE_PROCESSED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case REQUEST_SENT_STATE_ID:
                return RequestSentState.class;
            case RESPONSE_PROCESSED_STATE_ID:
                return ResponseProcessedState.class;
            default:
                return null;
        }
    }

    public static class RequestSentState extends ConcreteProtocolState {
        public final ObvDeviceManagementRequest deviceManagementRequest;

        @SuppressWarnings("unused")
        public RequestSentState(Encoded encodedState) throws Exception {
            super(REQUEST_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.deviceManagementRequest = ObvDeviceManagementRequest.of(list[0]);
        }

        public RequestSentState(ObvDeviceManagementRequest deviceManagementRequest) {
            super(REQUEST_SENT_STATE_ID);
            this.deviceManagementRequest = deviceManagementRequest;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    deviceManagementRequest.encode(),
            });
        }
    }

    public static class ResponseProcessedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public ResponseProcessedState(Encoded encodedState) throws Exception {
            super(RESPONSE_PROCESSED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public ResponseProcessedState() {
            super(RESPONSE_PROCESSED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion



    // region Messages

    public static final int INITIAL_MESSAGE_ID = 0;
    public static final int SERVER_QUERY_MESSAGE_ID = 1;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case SERVER_QUERY_MESSAGE_ID:
                return ServerQueryMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        public final ObvDeviceManagementRequest deviceManagementRequest;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, ObvDeviceManagementRequest deviceManagementRequest) {
            super(coreProtocolMessage);
            this.deviceManagementRequest = deviceManagementRequest;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            deviceManagementRequest = ObvDeviceManagementRequest.of(receivedMessage.getInputs()[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    deviceManagementRequest.encode(),
            };
        }
    }


    public static class ServerQueryMessage extends ConcreteProtocolMessage {
        public ServerQueryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public ServerQueryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            // uncomment if the query has to return a response
//            if (receivedMessage.getEncodedResponse() == null) {
//                throw new Exception();
//            }
        }

        @Override
        public int getProtocolMessageId() {
            return SERVER_QUERY_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    // endregion





    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{SendRequestStep.class};
            case REQUEST_SENT_STATE_ID:
                return new Class[]{ProcessResponseStateStep.class};
            case RESPONSE_PROCESSED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class SendRequestStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public SendRequestStep(InitialProtocolState startState, InitialMessage receivedMessage, OwnedDeviceManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            ServerQuery.Type serverQueryType;
            switch (receivedMessage.deviceManagementRequest.action) {
                case ObvDeviceManagementRequest.ACTION_SET_NICKNAME: {
                    // pad and encrypt the nickname
                    byte[] encodedDeviceName = Encoded.of(new Encoded[]{
                            Encoded.of(receivedMessage.deviceManagementRequest.nickname.getBytes(StandardCharsets.UTF_8))
                    }).getBytes();

                    byte[] plaintext = new byte[((encodedDeviceName.length - 1) | 127) + 1];
                    System.arraycopy(encodedDeviceName, 0, plaintext, 0, encodedDeviceName.length);

                    EncryptedBytes encryptedDeviceName = Suite.getPublicKeyEncryption(getOwnedIdentity().getEncryptionPublicKey()).encrypt(
                            getOwnedIdentity().getEncryptionPublicKey(),
                            plaintext,
                            Suite.getDefaultPRNGService(0));

                    UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                    serverQueryType = new ServerQuery.DeviceManagementSetNicknameQuery(getOwnedIdentity(), receivedMessage.deviceManagementRequest.getDeviceUid(), encryptedDeviceName, currentDeviceUid.equals(receivedMessage.deviceManagementRequest.getDeviceUid()));
                    break;
                }
                case ObvDeviceManagementRequest.ACTION_DEACTIVATE_DEVICE: {
                    serverQueryType = new ServerQuery.DeviceManagementDeactivateDeviceQuery(getOwnedIdentity(), receivedMessage.deviceManagementRequest.getDeviceUid());
                    break;
                }
                case ObvDeviceManagementRequest.ACTION_SET_UNEXPIRING_DEVICE: {
                    serverQueryType = new ServerQuery.DeviceManagementSetUnexpiringDeviceQuery(getOwnedIdentity(), receivedMessage.deviceManagementRequest.getDeviceUid());
                    break;
                }
                default: {
                    Logger.e("OwnedDeviceManagementProtocol received an invalid ObvDeviceManagementRequest: unknown action");
                    throw new Exception();
                }
            }
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), serverQueryType));
            ChannelMessageToSend messageToSend = new ServerQueryMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new RequestSentState(receivedMessage.deviceManagementRequest);
        }
    }


    public static class ProcessResponseStateStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final RequestSentState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final ServerQueryMessage receivedMessage;

        public ProcessResponseStateStep(RequestSentState startState, ServerQueryMessage receivedMessage, OwnedDeviceManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // after a query is processed by the server, start an OwnedDeviceDiscoveryProtocol
                UID protocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        ConcreteProtocol.OWNED_DEVICE_DISCOVERY_PROTOCOL_ID,
                        protocolInstanceUid);
                ChannelMessageToSend message = new OwnedDeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
            }

            // if the user deactivated a device --> notify all contacts that a device discovery is needed
            if (startState.deviceManagementRequest.action == ObvDeviceManagementRequest.ACTION_DEACTIVATE_DEVICE) {
                SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfoForMultipleIdentities(protocolManagerSession.identityDelegate.getContactsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()), getOwnedIdentity());
                for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(sendChannelInfo,
                                ConcreteProtocol.CONTACT_MANAGEMENT_PROTOCOL_ID,
                                new UID(getPrng()));
                        ChannelMessageToSend message = new ContactManagementProtocol.PerformContactDeviceDiscoveryMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
                    } catch (NoAcceptableChannelException e) {
                        Logger.d("One SendChannelInfo with no channel during OwnedDeviceManagementProtocol.ProcessResponseStateStep");
                    }
                }
            }

            return new ResponseProcessedState();
        }
    }

    // endregion
}
