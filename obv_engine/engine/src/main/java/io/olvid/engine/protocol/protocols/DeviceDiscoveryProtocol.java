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

import java.util.HashMap;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.PreKeyBlobOnServer;
import io.olvid.engine.datatypes.containers.PreKey;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.UidAndPreKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.LinkBetweenProtocolInstances;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.ChildToParentProtocolMessageInputs;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class DeviceDiscoveryProtocol extends ConcreteProtocol {
    public DeviceDiscoveryProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return DEVICE_DISCOVERY_PROTOCOL_ID;
    }

    // region States

    public static final int WAITING_FOR_CHILD_PROTOCOL_STATE_ID = 1;
    public static final int CHILD_PROTOCOL_OUTPUT_PROCESSED_STATE_ID = 2;
    public static final int CANCELLED_STATE_ID = 3;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{CANCELLED_STATE_ID, CHILD_PROTOCOL_OUTPUT_PROCESSED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case WAITING_FOR_CHILD_PROTOCOL_STATE_ID:
                return WaitingForChildProtocolState.class;
            case CHILD_PROTOCOL_OUTPUT_PROCESSED_STATE_ID:
                return ChildProtocolStateProcessedState.class;
            case CANCELLED_STATE_ID:
                return CancelledState.class;
            default:
                return null;
        }
    }

    public static class WaitingForChildProtocolState extends ConcreteProtocolState {
        private final Identity contactIdentity;

        @SuppressWarnings("unused")
        public WaitingForChildProtocolState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_CHILD_PROTOCOL_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
        }

        public WaitingForChildProtocolState(Identity contactIdentity) {
            super(WAITING_FOR_CHILD_PROTOCOL_STATE_ID);
            this.contactIdentity = contactIdentity;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity)
            });
        }
    }

    public static class ChildProtocolStateProcessedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public ChildProtocolStateProcessedState(Encoded encodedState) throws Exception {
            super(CANCELLED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public ChildProtocolStateProcessedState() {
            super(CHILD_PROTOCOL_OUTPUT_PROCESSED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    public static class CancelledState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
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
    public static final int CHILD_PROTOCOL_REACHED_EXPECTED_STATE_MESSAGE_ID = 1;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case CHILD_PROTOCOL_REACHED_EXPECTED_STATE_MESSAGE_ID:
                return ChildProtocolReachedExpectedStateMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity)
            };
        }
    }

    public static class ChildProtocolReachedExpectedStateMessage extends ConcreteProtocolMessage {
        private final ChildToParentProtocolMessageInputs childToParentProtocolMessageInputs;

        public ChildProtocolReachedExpectedStateMessage(CoreProtocolMessage coreProtocolMessage, ChildToParentProtocolMessageInputs childToParentProtocolMessageInputs) {
            super(coreProtocolMessage);
            this.childToParentProtocolMessageInputs = childToParentProtocolMessageInputs;
        }

        public ChildProtocolReachedExpectedStateMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            childToParentProtocolMessageInputs = new ChildToParentProtocolMessageInputs(receivedMessage.getInputs());
        }

        @Override
        public int getProtocolMessageId() {
            return CHILD_PROTOCOL_REACHED_EXPECTED_STATE_MESSAGE_ID;
        }

        // not used for this type of message
        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }

        public DeviceDiscoveryChildProtocol.DeviceUidsReceivedState getDeviceUidsReceivedState() {
            try {
                return new DeviceDiscoveryChildProtocol.DeviceUidsReceivedState(childToParentProtocolMessageInputs.getChildProtocolEncodedState());
            } catch (Exception e) {
                return null;
            }
        }
    }


    // endregion


    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{StartChildProtocolStep.class};
            case WAITING_FOR_CHILD_PROTOCOL_STATE_ID:
                return new Class[]{ProcessChildProtocolStateStep.class};
            case CHILD_PROTOCOL_OUTPUT_PROCESSED_STATE_ID:
            case CANCELLED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class StartChildProtocolStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public StartChildProtocolStep(InitialProtocolState startState, InitialMessage receivedMessage, DeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                Logger.i("Trying to run a DeviceDiscoveryProtocol with an unknown or revoked contactIdentity");
                return new CancelledState();
            }

            UID childProtocolInstanceUid = new UID(getPrng());
            LinkBetweenProtocolInstances.create(
                    protocolManagerSession,
                    childProtocolInstanceUid,
                    getOwnedIdentity(),
                    DeviceDiscoveryChildProtocol.DEVICE_UIDS_RECEIVED_STATE_ID,
                    getProtocolInstanceUid(),
                    getProtocolId(),
                    CHILD_PROTOCOL_REACHED_EXPECTED_STATE_MESSAGE_ID
            );
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                    DEVICE_DISCOVERY_CHILD_PROTOCOL_ID,
                    childProtocolInstanceUid,
                    false
            );
            ChannelMessageToSend messageToSend = new DeviceDiscoveryChildProtocol.InitialMessage(coreProtocolMessage, receivedMessage.contactIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitingForChildProtocolState(receivedMessage.contactIdentity);
        }
    }

    public static class ProcessChildProtocolStateStep extends ProtocolStep {
        private final WaitingForChildProtocolState startState;
        private final ChildProtocolReachedExpectedStateMessage receivedMessage;

        public ProcessChildProtocolStateStep(WaitingForChildProtocolState startState, ChildProtocolReachedExpectedStateMessage receivedMessage, DeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            DeviceDiscoveryChildProtocol.DeviceUidsReceivedState deviceUidsReceivedState = receivedMessage.getDeviceUidsReceivedState();

            Identity receivedContactIdentity = deviceUidsReceivedState.getRemoteIdentity();
            if (!receivedContactIdentity.equals(startState.contactIdentity)) {
                Logger.w("Received UID from another remoteIdentity!");
                return new CancelledState();
            }

            if (deviceUidsReceivedState.getDeviceUidsAndPreKeys().length == 0 && deviceUidsReceivedState.getServerTimestamp() == 0) {
                Logger.w("Device discovery query expired.");
                return new CancelledState();
            }

            HashMap<UID, Encoded> newContactDevicesAndPreKeys = new HashMap<>();
            for (HashMap<DictionaryKey, Encoded> deviceUidAndPreKey : deviceUidsReceivedState.getDeviceUidsAndPreKeys()) {
                try {
                    Encoded encodedDeviceUid = deviceUidAndPreKey.get(new DictionaryKey("uid"));
                    Encoded encodedSignedPreKey = deviceUidAndPreKey.get(new DictionaryKey("prk"));
                    if (encodedDeviceUid != null) {
                        UID deviceUid = encodedDeviceUid.decodeUid();
                        newContactDevicesAndPreKeys.put(deviceUid, encodedSignedPreKey);
                    }
                } catch (Exception e) {
                    Logger.i("Malformed server response id device discovery");
                    e.printStackTrace();
                }
            }

            for (UidAndPreKey oldUidAndPreKey: protocolManagerSession.identityDelegate.getDeviceUidsAndPreKeysOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedContactIdentity)) {
                boolean stillExists = newContactDevicesAndPreKeys.containsKey(oldUidAndPreKey.uid);
                Encoded encodedSignedPreKey = newContactDevicesAndPreKeys.remove(oldUidAndPreKey.uid);
                if (stillExists) {
                    // check if the preKey should be updated

                    final PreKeyBlobOnServer newPreKey;
                    final boolean preKeyChanged;

                    if (encodedSignedPreKey != null) {
                        // there is a preKey on the server, check if it changed and has a valid signature
                        PreKeyBlobOnServer preKeyBlob = PreKeyBlobOnServer.verifySignatureAndDecode(encodedSignedPreKey, receivedContactIdentity, oldUidAndPreKey.uid, deviceUidsReceivedState.getServerTimestamp());
                        if (preKeyBlob != null &&
                                (oldUidAndPreKey.preKey == null || (!preKeyBlob.preKey.keyId.equals(oldUidAndPreKey.preKey.keyId) && oldUidAndPreKey.preKey.expirationTimestamp < preKeyBlob.preKey.expirationTimestamp))) {
                            newPreKey = preKeyBlob;
                            preKeyChanged = true;
                        } else {
                            newPreKey = null;
                            preKeyChanged = false;
                        }
                    } else if (oldUidAndPreKey.preKey != null) {
                        // the preKey was removed!
                        Logger.w("A contact preKey was removed from the server, this should never happen...");
                        newPreKey = null;
                        preKeyChanged = true;
                    } else {
                        newPreKey = null;
                        preKeyChanged = false;
                    }

                    if (preKeyChanged) {
                        protocolManagerSession.identityDelegate.updateContactDevicePreKey(protocolManagerSession.session, getOwnedIdentity(), receivedContactIdentity, oldUidAndPreKey.uid, newPreKey);
                    }
                } else {
                    // a deviceUid was removed --> delete the channel and the deviceUid
                    protocolManagerSession.channelDelegate.deleteObliviousChannelIfItExists(protocolManagerSession.session, getOwnedIdentity(), oldUidAndPreKey.uid, receivedContactIdentity);
                    protocolManagerSession.identityDelegate.removeDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedContactIdentity, oldUidAndPreKey.uid);
                }
            }

            for (Map.Entry<UID, Encoded> entry: newContactDevicesAndPreKeys.entrySet()) {
                // a new deviceUid was found --> add it, this will trigger the channel creation
                PreKeyBlobOnServer preKeyBlob = entry.getValue() == null ? null : PreKeyBlobOnServer.verifySignatureAndDecode(entry.getValue(), receivedContactIdentity, entry.getKey(), deviceUidsReceivedState.getServerTimestamp());
                protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedContactIdentity, entry.getKey(), preKeyBlob,  false);
            }

            // update the recently online status of the contact
            protocolManagerSession.identityDelegate.setContactRecentlyOnline(protocolManagerSession.session, getOwnedIdentity(), receivedContactIdentity, deviceUidsReceivedState.isRecentlyOnline());

            if (deviceUidsReceivedState.getServerTimestamp() != 0) {
                // delete expired pre keys (for the contact's server)
                protocolManagerSession.identityDelegate.expireContactAndOwnedPreKeys(protocolManagerSession.session, getOwnedIdentity(), receivedContactIdentity.getServer(), deviceUidsReceivedState.getServerTimestamp());
            }

            return new ChildProtocolStateProcessedState();
        }
    }

    // endregion
}
