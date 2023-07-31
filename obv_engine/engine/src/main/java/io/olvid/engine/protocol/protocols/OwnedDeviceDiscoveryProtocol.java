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

package io.olvid.engine.protocol.protocols;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.EmptyProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class OwnedDeviceDiscoveryProtocol extends ConcreteProtocol {
    public OwnedDeviceDiscoveryProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return OWNED_DEVICE_DISCOVERY_PROTOCOL_ID;
    }

    // region States

    public static final int REQUEST_SENT_STATE_ID = 1;
    public static final int RESPONSE_PROCESSED_STATE_ID = 2;
    public static final int CANCELLED_STATE_ID = 3;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{CANCELLED_STATE_ID, RESPONSE_PROCESSED_STATE_ID};
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
            case CANCELLED_STATE_ID:
                return CancelledState.class;
            default:
                return null;
        }
    }

    public static class RequestSentState extends ConcreteProtocolState {
        public RequestSentState(Encoded encodedState) throws Exception {
            super(REQUEST_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public RequestSentState() {
            super(REQUEST_SENT_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    public static class ResponseProcessedState extends ConcreteProtocolState {
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

    public static class CancelledState extends ConcreteProtocolState {
        public CancelledState(Encoded encodedState) throws Exception {
            super(CANCELLED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public CancelledState() {
            super(CANCELLED_STATE_ID);
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
    public static final int TRIGGER_OWNED_DEVICE_DISCOVERY_MESSAGE_ID = 2;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case SERVER_QUERY_MESSAGE_ID:
                return ServerQueryMessage.class;
            case TRIGGER_OWNED_DEVICE_DISCOVERY_MESSAGE_ID:
                return TriggerOwnedDeviceDiscoveryMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        public InitialMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class ServerQueryMessage extends ConcreteProtocolMessage {
        private final EncryptedBytes encryptedOwnedDeviceList;

        public ServerQueryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            encryptedOwnedDeviceList = null;
        }

        @SuppressWarnings("unused")
        public ServerQueryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            encryptedOwnedDeviceList = receivedMessage.getEncodedResponse().decodeEncryptedData();
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

    public static class TriggerOwnedDeviceDiscoveryMessage extends EmptyProtocolMessage {
        public TriggerOwnedDeviceDiscoveryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        public TriggerOwnedDeviceDiscoveryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return TRIGGER_OWNED_DEVICE_DISCOVERY_MESSAGE_ID;
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
            case CANCELLED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class SendRequestStep extends ProtocolStep {
        private final InitialProtocolState startState;

        public SendRequestStep(InitialProtocolState startState, InitialMessage receivedMessage, OwnedDeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
        }

        public SendRequestStep(InitialProtocolState startState, TriggerOwnedDeviceDiscoveryMessage receivedMessage, OwnedDeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createOwnedDeviceDiscoveryQuery(getOwnedIdentity())));
            ChannelMessageToSend messageToSend = new ServerQueryMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new RequestSentState();
        }
    }


    public static class ProcessResponseStateStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final RequestSentState startState;
        private final ServerQueryMessage receivedMessage;

        public ProcessResponseStateStep(RequestSentState startState, ServerQueryMessage receivedMessage, OwnedDeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // decrypt the received device list
            byte[] decryptedPayload = protocolManagerSession.encryptionForIdentityDelegate.decrypt(protocolManagerSession.session, receivedMessage.encryptedOwnedDeviceList, getOwnedIdentity());
            if (decryptedPayload == null) {
                Logger.w("Unable to DECRYPT received OwnedDeviceDiscoveryProtocol payload (or expired query)!");
                return new CancelledState();
            }

            // we ignore the multi-device boolean received from the server --> it is only used when querying outside the protocol
            HashMap<UID, ObvOwnedDevice.ServerDeviceInfo> serverOwnedDevices = new HashMap<>();
            try {
                HashMap<DictionaryKey, Encoded> map = new Encoded(decryptedPayload).decodeDictionary();

                Encoded[] encodedDevices = map.get(new DictionaryKey("dev")).decodeList();
                for (Encoded encodedDevice : encodedDevices) {
                    HashMap<DictionaryKey, Encoded> deviceMap = encodedDevice.decodeDictionary();
                    UID deviceUid = deviceMap.get(new DictionaryKey("uid")).decodeUid();

                    Encoded encodedExpiration = deviceMap.get(new DictionaryKey("exp"));
                    Long expirationTimestamp = encodedExpiration == null ? null : encodedExpiration.decodeLong();

                    Encoded encodedRegistration = deviceMap.get(new DictionaryKey("reg"));
                    Long lastRegistrationTimestamp = encodedRegistration == null ? null : encodedRegistration.decodeLong();

                    Encoded encodedName = deviceMap.get(new DictionaryKey("name"));
                    String deviceName = null;
                    if (encodedName != null) {
                        try {
                            byte[] plaintext = protocolManagerSession.encryptionForIdentityDelegate.decrypt(protocolManagerSession.session, encodedName.decodeEncryptedData(), getOwnedIdentity());
                            byte[] bytesDeviceName = new Encoded(plaintext).decodeListWithPadding()[0].decodeBytes();
                            if (bytesDeviceName.length != 0) {
                                deviceName = new String(bytesDeviceName, StandardCharsets.UTF_8);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    serverOwnedDevices.put(deviceUid, new ObvOwnedDevice.ServerDeviceInfo(deviceName, expirationTimestamp, lastRegistrationTimestamp));
                }
            } catch (Exception e) {
                Logger.w("Unable to DECODE received OwnedDeviceDiscoveryProtocol payload!");
                return new CancelledState();
            }

            List<ObvOwnedDevice> oldOwnedDevices = protocolManagerSession.identityDelegate.getDevicesOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

            for (ObvOwnedDevice oldDevice : oldOwnedDevices) {
                UID ownedDeviceUid = new UID(oldDevice.bytesDeviceUid);
                ObvOwnedDevice.ServerDeviceInfo serverDeviceInfo = serverOwnedDevices.remove(ownedDeviceUid);
                if (serverDeviceInfo == null) {
                    // device was removed from the server
                    if (oldDevice.currentDevice) {
                        // our current device was removed! Do not deactivate it yet, but force a registerPushNotification so it gets deactivated if it should be
                        protocolManagerSession.pushNotificationDelegate.forceRegisterPushNotification(getOwnedIdentity());
                    } else {
                        // a deviceUid was removed --> delete the channel and the deviceUid
                        protocolManagerSession.channelDelegate.deleteObliviousChannelIfItExists(protocolManagerSession.session, getOwnedIdentity(), ownedDeviceUid, getOwnedIdentity());
                        protocolManagerSession.identityDelegate.removeDeviceForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), ownedDeviceUid);
                    }
                } else {
                    // the device exists both locally and on the server --> check what has changed
                    if (!Objects.equals(oldDevice.serverDeviceInfo, serverDeviceInfo)) {
                        protocolManagerSession.identityDelegate.updateOwnedDevice(protocolManagerSession.session, getOwnedIdentity(), ownedDeviceUid, serverDeviceInfo.displayName, serverDeviceInfo.expirationTimestamp, serverDeviceInfo.lastRegistrationTimestamp);
                    }
                }
            }

            // now create all new server devices locally
            for (Map.Entry<UID, ObvOwnedDevice.ServerDeviceInfo> entry : serverOwnedDevices.entrySet()) {
                protocolManagerSession.identityDelegate.addDeviceForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), entry.getKey(), entry.getValue().displayName, entry.getValue().expirationTimestamp, entry.getValue().lastRegistrationTimestamp, false);
            }

            return new ResponseProcessedState();
        }
    }

    // endregion
}
