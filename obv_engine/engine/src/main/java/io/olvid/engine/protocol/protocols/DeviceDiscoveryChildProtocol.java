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

package io.olvid.engine.protocol.protocols;


import com.fasterxml.jackson.databind.ObjectMapper;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class DeviceDiscoveryChildProtocol extends ConcreteProtocol {
    public DeviceDiscoveryChildProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return DEVICE_DISCOVERY_CHILD_PROTOCOL_ID;
    }





    // region States

//    public static final int REQUEST_SENT_STATE_ID = 1;
    public static final int DEVICE_UIDS_RECEIVED_STATE_ID = 2;
//    public static final int DEVICE_UIDS_SENT_STATE_ID = 3;
    public static final int SERVER_REQUEST_SENT_STATE_ID = 4;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{DEVICE_UIDS_RECEIVED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case DEVICE_UIDS_RECEIVED_STATE_ID:
                return DeviceUidsReceivedState.class;
            case SERVER_REQUEST_SENT_STATE_ID:
                return ServerRequestSentState.class;
            default:
                return null;
        }
    }

    public static class DeviceUidsReceivedState extends ConcreteProtocolState {
        private final Identity remoteIdentity;
        private final UID[] deviceUids;

        public DeviceUidsReceivedState(Encoded encodedState) throws Exception{
            super(DEVICE_UIDS_RECEIVED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.remoteIdentity = list[0].decodeIdentity();
            this.deviceUids = list[1].decodeUidArray();
        }

        public DeviceUidsReceivedState(Identity remoteIdentity, UID[] deviceUids) {
            super(DEVICE_UIDS_RECEIVED_STATE_ID);
            this.remoteIdentity = remoteIdentity;
            this.deviceUids = deviceUids;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(remoteIdentity),
                    Encoded.of(deviceUids)
            });
        }

        public UID[] getDeviceUids() {
            return deviceUids;
        }
        public Identity getRemoteIdentity() {
            return remoteIdentity;
        }
    }

    public static class ServerRequestSentState extends ConcreteProtocolState {
        private final Identity remoteIdentity;

        @SuppressWarnings("unused")
        public ServerRequestSentState(Encoded encodedState) throws Exception {
            super(SERVER_REQUEST_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.remoteIdentity = list[0].decodeIdentity();
        }

        public ServerRequestSentState(Identity remoteIdentity) {
            super(SERVER_REQUEST_SENT_STATE_ID);
            this.remoteIdentity = remoteIdentity;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(remoteIdentity)
            });
        }
    }



    // endregion




    // region Messages

    public static final int INITIAL_MESSAGE_ID = 0;
//    public static final int FROM_ALICE_MESSAGE_ID = 1;
//    public static final int FROM_BOB_MESSAGE_ID = 2;
    @SuppressWarnings("CommentedOutCode")
    public static final int SERVER_QUERY_MESSAGE_ID = 3;

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
        private final Identity remoteIdentity;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity remoteIdentity) {
            super(coreProtocolMessage);
            this.remoteIdentity = remoteIdentity;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.remoteIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{Encoded.of(remoteIdentity)};
        }
    }

    public static class ServerQueryMessage extends ConcreteProtocolMessage {
        private final UID[] deviceUids;

        public ServerQueryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            deviceUids = null;
        }

        @SuppressWarnings("unused")
        public ServerQueryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            deviceUids = receivedMessage.getEncodedResponse().decodeUidArray();
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
            case SERVER_REQUEST_SENT_STATE_ID:
                return new Class[]{ProcessDeviceUidsStep.class};
            case DEVICE_UIDS_RECEIVED_STATE_ID:
            default:
                return new Class[0];
        }
    }


    public static class SendRequestStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public SendRequestStep(InitialProtocolState startState, InitialMessage receivedMessage, DeviceDiscoveryChildProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ServerRequestSentState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createDeviceDiscoveryQuery(receivedMessage.remoteIdentity)));
            ChannelMessageToSend messageToSend = new ServerQueryMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return new ServerRequestSentState(receivedMessage.remoteIdentity);
        }
    }

    public static class ProcessDeviceUidsStep extends ProtocolStep {
        private final ServerRequestSentState startState;
        private final ServerQueryMessage receivedMessage;

        public ProcessDeviceUidsStep(ServerRequestSentState startState, ServerQueryMessage receivedMessage, DeviceDiscoveryChildProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            return new DeviceUidsReceivedState(startState.remoteIdentity, receivedMessage.deviceUids);
        }
    }

    // endregion
}