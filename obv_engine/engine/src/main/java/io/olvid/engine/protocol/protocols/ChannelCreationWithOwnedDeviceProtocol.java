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

package io.olvid.engine.protocol.protocols;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.PublicKeyEncryption;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.CiphertextAndKey;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.KeyPair;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.protocol.databases.ChannelCreationPingSignatureReceived;
import io.olvid.engine.protocol.databases.ChannelCreationProtocolInstance;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class ChannelCreationWithOwnedDeviceProtocol extends ConcreteProtocol {
    public ChannelCreationWithOwnedDeviceProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID;
    }




    //region States

    static final int CANCELLED_STATE_ID = 1;
    static final int PING_SENT_STATE_ID = 2;
    static final int WAITING_FOR_K1_STATE_ID = 3;
    static final int WAITING_FOR_K2_STATE_ID = 4;
    static final int WAIT_FOR_FIRST_ACK_STATE_ID = 5;
    static final int WAIT_FOR_SECOND_ACK_STATE_ID = 7;
    static final int CHANNEL_CONFIRMED_STATE_ID = 8;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{CANCELLED_STATE_ID, CHANNEL_CONFIRMED_STATE_ID, PING_SENT_STATE_ID};
    }

    @Override
    public Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case CANCELLED_STATE_ID:
                return CancelledState.class;
            case PING_SENT_STATE_ID:
                return PingSentState.class;
            case WAITING_FOR_K1_STATE_ID:
                return WaitingForK1State.class;
            case WAITING_FOR_K2_STATE_ID:
                return WaitingForK2State.class;
            case WAIT_FOR_FIRST_ACK_STATE_ID:
                return WaitForFirstAckState.class;
            case WAIT_FOR_SECOND_ACK_STATE_ID:
                return WaitForSecondAckState.class;
            case CHANNEL_CONFIRMED_STATE_ID:
                return ChannelConfirmedState.class;
            default:
                return null;
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
        CancelledState() {
            super(CANCELLED_STATE_ID);
        }
        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }


    public static class PingSentState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public PingSentState(Encoded encodedState) throws Exception {
            super(PING_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }
        PingSentState() {
            super(PING_SENT_STATE_ID);
        }
        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }


    public static class WaitingForK1State extends ConcreteProtocolState {
        private final UID remoteDeviceUid;
        private final EncryptionPrivateKey ephemeralPrivateKey;

        @SuppressWarnings("unused")
        public WaitingForK1State(Encoded encodedState) throws Exception {
            super(WAITING_FOR_K1_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.remoteDeviceUid = list[0].decodeUid();
            this.ephemeralPrivateKey = (EncryptionPrivateKey) list[1].decodePrivateKey();
        }

        WaitingForK1State(UID remoteDeviceUid, EncryptionPrivateKey ephemeralPrivateKey) {
            super(WAITING_FOR_K1_STATE_ID);
            this.remoteDeviceUid = remoteDeviceUid;
            this.ephemeralPrivateKey = ephemeralPrivateKey;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(remoteDeviceUid),
                    Encoded.of(ephemeralPrivateKey),
            });
        }
    }


    public static class WaitingForK2State extends ConcreteProtocolState {
        private final UID remoteDeviceUid;
        private final EncryptionPrivateKey ephemeralPrivateKey;
        private final AuthEncKey k1;

        @SuppressWarnings("unused")
        public WaitingForK2State(Encoded encodedState) throws Exception {
            super(WAITING_FOR_K2_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 3) {
                throw new Exception();
            }
            this.remoteDeviceUid = list[0].decodeUid();
            this.ephemeralPrivateKey = (EncryptionPrivateKey) list[1].decodePrivateKey();
            this.k1 = (AuthEncKey) list[2].decodeSymmetricKey();
        }

        WaitingForK2State(UID remoteDeviceUid, EncryptionPrivateKey ephemeralPrivateKey, AuthEncKey k1) {
            super(WAITING_FOR_K2_STATE_ID);
            this.remoteDeviceUid = remoteDeviceUid;
            this.ephemeralPrivateKey = ephemeralPrivateKey;
            this.k1 = k1;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(remoteDeviceUid),
                    Encoded.of(ephemeralPrivateKey),
                    Encoded.of(k1),
            });
        }
    }


    public static class WaitForFirstAckState extends ConcreteProtocolState {
        private final UID remoteDeviceUid;

        @SuppressWarnings("unused")
        public WaitForFirstAckState(Encoded encodedState) throws Exception {
            super(WAIT_FOR_FIRST_ACK_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.remoteDeviceUid = list[0].decodeUid();
        }

        WaitForFirstAckState(UID remoteDeviceUid) {
            super(WAIT_FOR_FIRST_ACK_STATE_ID);
            this.remoteDeviceUid = remoteDeviceUid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(remoteDeviceUid),
            });
        }
    }
    public static class WaitForSecondAckState extends ConcreteProtocolState {
        private final UID remoteDeviceUid;

        @SuppressWarnings("unused")
        public WaitForSecondAckState(Encoded encodedState) throws Exception {
            super(WAIT_FOR_SECOND_ACK_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.remoteDeviceUid = list[0].decodeUid();
        }

        WaitForSecondAckState(UID remoteDeviceUid) {
            super(WAIT_FOR_SECOND_ACK_STATE_ID);
            this.remoteDeviceUid = remoteDeviceUid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(remoteDeviceUid),
            });
        }
    }


    public static class ChannelConfirmedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public ChannelConfirmedState(Encoded encodedState) throws Exception {
            super(CHANNEL_CONFIRMED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        ChannelConfirmedState() {
            super(CHANNEL_CONFIRMED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    //endregion









    // region Messages

    static final int INITIAL_MESSAGE_ID = 0;
    static final int PING_MESSAGE_ID = 1;
    static final int ALICE_IDENTITY_AND_EPHEMERAL_KEY_MESSAGE_ID = 2;
    static final int BOB_EPHEMERAL_KEY_AND_K1_MESSAGE_ID = 3;
    static final int K2_MESSAGE_ID = 4;
    static final int FIRST_ACK_MESSAGE_ID = 5;
    static final int SECOND_ACK_MESSAGE_ID = 6;


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case PING_MESSAGE_ID:
                return PingMessage.class;
            case ALICE_IDENTITY_AND_EPHEMERAL_KEY_MESSAGE_ID:
                return AliceIdentityAndEphemeralKeyMessage.class;
            case BOB_EPHEMERAL_KEY_AND_K1_MESSAGE_ID:
                return BobEphemeralKeyAndK1Message.class;
            case K2_MESSAGE_ID:
                return K2Message.class;
            case FIRST_ACK_MESSAGE_ID:
                return FirstAckMessage.class;
            case SECOND_ACK_MESSAGE_ID:
                return SecondAckMessage.class;
            default:
                return null;
        }
    }


    public static class InitialMessage extends ConcreteProtocolMessage {
        private final UID remoteDeviceUid;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, UID remoteDeviceUid) {
            super(coreProtocolMessage);
            this.remoteDeviceUid = remoteDeviceUid;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.remoteDeviceUid = receivedMessage.getInputs()[0].decodeUid();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(remoteDeviceUid),
            };
        }
    }


    public static class PingMessage extends ConcreteProtocolMessage {
        private final UID remoteDeviceUid;
        private final byte[] signature;

        public PingMessage(CoreProtocolMessage coreProtocolMessage, UID remoteDeviceUid, byte[] signature) {
            super(coreProtocolMessage);
            this.remoteDeviceUid = remoteDeviceUid;
            this.signature = signature;
        }

        @SuppressWarnings("unused")
        public PingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.remoteDeviceUid = receivedMessage.getInputs()[0].decodeUid();
            this.signature = receivedMessage.getInputs()[1].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return PING_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(remoteDeviceUid),
                    Encoded.of(signature),
            };
        }
    }


    public static class AliceIdentityAndEphemeralKeyMessage extends ConcreteProtocolMessage {
        private final UID remoteDeviceUid;
        private final byte[] signature;
        private final EncryptionPublicKey remoteEphemeralPublicKey;

        public AliceIdentityAndEphemeralKeyMessage(CoreProtocolMessage coreProtocolMessage, UID remoteDeviceUid, byte[] signature, EncryptionPublicKey remoteEphemeralPublicKey) {
            super(coreProtocolMessage);
            this.remoteDeviceUid = remoteDeviceUid;
            this.signature = signature;
            this.remoteEphemeralPublicKey = remoteEphemeralPublicKey;
        }

        @SuppressWarnings("unused")
        public AliceIdentityAndEphemeralKeyMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            this.remoteDeviceUid = receivedMessage.getInputs()[0].decodeUid();
            this.signature = receivedMessage.getInputs()[1].decodeBytes();
            this.remoteEphemeralPublicKey = (EncryptionPublicKey) receivedMessage.getInputs()[2].decodePublicKey();
        }

        @Override
        public int getProtocolMessageId() {
            return ALICE_IDENTITY_AND_EPHEMERAL_KEY_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(remoteDeviceUid),
                    Encoded.of(signature),
                    Encoded.of(remoteEphemeralPublicKey),
            };
        }
    }


    public static class BobEphemeralKeyAndK1Message extends ConcreteProtocolMessage {
        private final EncryptionPublicKey remoteEphemeralPublicKey;
        private final EncryptedBytes c1;

        BobEphemeralKeyAndK1Message(CoreProtocolMessage coreProtocolMessage, EncryptionPublicKey remoteEphemeralPublicKey, EncryptedBytes c1) {
            super(coreProtocolMessage);
            this.remoteEphemeralPublicKey = remoteEphemeralPublicKey;
            this.c1 = c1;
        }

        @SuppressWarnings("unused")
        public BobEphemeralKeyAndK1Message(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.remoteEphemeralPublicKey = (EncryptionPublicKey) receivedMessage.getInputs()[0].decodePublicKey();
            this.c1 = receivedMessage.getInputs()[1].decodeEncryptedData();
        }

        @Override
        public int getProtocolMessageId() {
            return BOB_EPHEMERAL_KEY_AND_K1_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(remoteEphemeralPublicKey),
                    Encoded.of(c1),
            };
        }
    }


    public static class K2Message extends ConcreteProtocolMessage {
        private final EncryptedBytes c2;

        K2Message(CoreProtocolMessage coreProtocolMessage, EncryptedBytes c2) {
            super(coreProtocolMessage);
            this.c2 = c2;
        }

        @SuppressWarnings("unused")
        public K2Message(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.c2 = receivedMessage.getInputs()[0].decodeEncryptedData();
        }

        @Override
        public int getProtocolMessageId() {
            return K2_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(c2),
            };
        }
    }


    public static class FirstAckMessage extends ConcreteProtocolMessage {
        private final String remoteSerializedIdentityWithVersionAndPhoto;

        FirstAckMessage(CoreProtocolMessage coreProtocolMessage, String remoteSerializedIdentityWithVersionAndPhoto) {
            super(coreProtocolMessage);
            this.remoteSerializedIdentityWithVersionAndPhoto = remoteSerializedIdentityWithVersionAndPhoto;
        }

        @SuppressWarnings("unused")
        public FirstAckMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.remoteSerializedIdentityWithVersionAndPhoto = receivedMessage.getInputs()[0].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return FIRST_ACK_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(remoteSerializedIdentityWithVersionAndPhoto),
            };
        }
    }


    public static class SecondAckMessage extends ConcreteProtocolMessage {
        private final String remoteSerializedIdentityWithVersionAndPhoto;

        SecondAckMessage(CoreProtocolMessage coreProtocolMessage, String remoteSerializedIdentityWithVersionAndPhoto) {
            super(coreProtocolMessage);
            this.remoteSerializedIdentityWithVersionAndPhoto = remoteSerializedIdentityWithVersionAndPhoto;
        }

        @SuppressWarnings("unused")
        public SecondAckMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.remoteSerializedIdentityWithVersionAndPhoto = receivedMessage.getInputs()[0].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return SECOND_ACK_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(remoteSerializedIdentityWithVersionAndPhoto),
            };
        }
    }

    //endregion









    //region Steps

    @Override
    public Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{SendPingStep.class, SendPingOrEphemeralKeyStep.class, SendEphemeralKeyAndK1Step.class};
            case WAITING_FOR_K1_STATE_ID:
                return new Class[]{RecoverK1AndSendK2AndCreateChannelStep.class};
            case WAITING_FOR_K2_STATE_ID:
                return new Class[]{RecoverK2CreateChannelAndSendAckStep.class};
            case WAIT_FOR_FIRST_ACK_STATE_ID:
                return new Class[]{ConfirmChannelAndSendAckStep.class};
            case WAIT_FOR_SECOND_ACK_STATE_ID:
                return new Class[]{ConfirmChannelStep.class};
            case CANCELLED_STATE_ID:
            case PING_SENT_STATE_ID:
            case CHANNEL_CONFIRMED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class SendPingStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public SendPingStep(InitialProtocolState startState, InitialMessage receivedMessage, ChannelCreationWithOwnedDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // check that the remoteDeviceUid in the receivedMessage is not our currentDeviceUid
            UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            if (currentDeviceUid == null) {
                return new CancelledState();
            }
            if (Objects.equals(currentDeviceUid, receivedMessage.remoteDeviceUid)) {
                Logger.w("Trying to run a ChannelCreationWithOwnedDeviceProtocol with our currentDeviceUid");
                return new CancelledState();
            }

            // clean any ongoing instance of this protocol
            ChannelCreationProtocolInstance channelCreationProtocolInstance = null;
            try {
                channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(
                        protocolManagerSession,
                        receivedMessage.remoteDeviceUid,
                        getOwnedIdentity(),
                        getOwnedIdentity()
                );
            } catch (SQLException ignored) {}
            if (channelCreationProtocolInstance != null) {
                channelCreationProtocolInstance.delete();
                protocolManagerSession.protocolDelegate.abortProtocol(protocolManagerSession.session, channelCreationProtocolInstance.getProtocolInstanceUid(), getOwnedIdentity());
            }

            // clear any already created ObliviousChannel
            protocolManagerSession.channelDelegate.deleteObliviousChannelIfItExists(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    receivedMessage.remoteDeviceUid,
                    getOwnedIdentity());

            // send a signed ping
            byte[] signature = protocolManagerSession.identityDelegate.signChannel(
                    protocolManagerSession.session,
                    Constants.SignatureContext.CHANNEL_CREATION,
                    getOwnedIdentity(),
                    receivedMessage.remoteDeviceUid,
                    getOwnedIdentity(),
                    currentDeviceUid,
                    getPrng()
            );


            // send the ping containing the signature
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(getOwnedIdentity(), getOwnedIdentity(), new UID[]{receivedMessage.remoteDeviceUid}));
            ChannelMessageToSend messageToSend = new PingMessage(coreProtocolMessage, currentDeviceUid, signature).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            protocolManagerSession.identityDelegate.setLatestChannelCreationPingTimestampForOwnedDevice(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.remoteDeviceUid, System.currentTimeMillis());

            return new PingSentState();
        }
    }



    public static class SendPingOrEphemeralKeyStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final PingMessage receivedMessage;

        public SendPingOrEphemeralKeyStep(InitialProtocolState startState, PingMessage receivedMessage, ChannelCreationWithOwnedDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // check that the remoteDeviceUid in the receivedMessage is not our currentDeviceUid
            UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            if (currentDeviceUid == null) {
                return new CancelledState();
            }
            if (Objects.equals(currentDeviceUid, receivedMessage.remoteDeviceUid)) {
                Logger.w("Received a ping for a ChannelCreationWithOwnedDeviceProtocol with our currentDeviceUid");
                return new CancelledState();
            }

            // verify the signature in the PingMessage
            boolean signatureIsValid = Signature.verify(
                    Constants.SignatureContext.CHANNEL_CREATION,
                    currentDeviceUid,
                    receivedMessage.remoteDeviceUid,
                    getOwnedIdentity(),
                    getOwnedIdentity(),
                    getOwnedIdentity(),
                    receivedMessage.signature
            );

            if (!signatureIsValid) {
                return new CancelledState();
            }

            if (ChannelCreationPingSignatureReceived.exists(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature)) {
                // we already received a ping with the same signature!
                return new CancelledState();
            } else {
                // store the signature to prevent future replay
                ChannelCreationPingSignatureReceived.create(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature);
            }

            // Signature is valid! The other device does not have a channel
            {
                // clean any ongoing instance of this protocol
                ChannelCreationProtocolInstance channelCreationProtocolInstance = null;
                try {
                    channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(
                            protocolManagerSession,
                            receivedMessage.remoteDeviceUid,
                            getOwnedIdentity(),
                            getOwnedIdentity()
                    );
                } catch (SQLException ignored) {}
                if (channelCreationProtocolInstance != null) {
                    channelCreationProtocolInstance.delete();
                    protocolManagerSession.protocolDelegate.abortProtocol(protocolManagerSession.session, channelCreationProtocolInstance.getProtocolInstanceUid(), getOwnedIdentity());
                }

                // clear any already created ObliviousChannel
                protocolManagerSession.channelDelegate.deleteObliviousChannelIfItExists(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        receivedMessage.remoteDeviceUid,
                        getOwnedIdentity());
            }



            // Compute a signature to prove we don't have any channel/ongoing protocol with the other device
            byte[] signature = protocolManagerSession.identityDelegate.signChannel(
                    protocolManagerSession.session,
                    Constants.SignatureContext.CHANNEL_CREATION,
                    getOwnedIdentity(),
                    receivedMessage.remoteDeviceUid,
                    getOwnedIdentity(),
                    currentDeviceUid,
                    getPrng()
            );



            // If we are in charge (small deviceUid), send an ephemeral key
            // otherwise simply send a ping back
            int compare = currentDeviceUid.compareTo(receivedMessage.remoteDeviceUid);
            if (compare >= 0) {
                // Not in charge

                // send the ping containing the signature
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(getOwnedIdentity(), getOwnedIdentity(), new UID[]{receivedMessage.remoteDeviceUid}));
                ChannelMessageToSend messageToSend = new PingMessage(coreProtocolMessage, currentDeviceUid, signature).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                protocolManagerSession.identityDelegate.setLatestChannelCreationPingTimestampForOwnedDevice(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.remoteDeviceUid, System.currentTimeMillis());

                return new PingSentState();
            } else {
                // In charge
                // Create a new ChannelCreationProtocolInstance
                ChannelCreationProtocolInstance channelCreationProtocolInstance = ChannelCreationProtocolInstance.create(
                        protocolManagerSession,
                        receivedMessage.remoteDeviceUid,
                        getOwnedIdentity(),
                        getOwnedIdentity(),
                        getProtocolInstanceUid()
                );
                if (channelCreationProtocolInstance == null) {
                    throw new Exception();
                }

                KeyPair keyPair = Suite.generateEncryptionKeyPair(getOwnedIdentity().getEncryptionPublicKey().getAlgorithmImplementation(), getPrng());
                if (keyPair == null) {
                    throw new Exception();
                }

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(getOwnedIdentity(), getOwnedIdentity(), new UID[]{receivedMessage.remoteDeviceUid}));
                ChannelMessageToSend messageToSend = new AliceIdentityAndEphemeralKeyMessage(coreProtocolMessage, currentDeviceUid, signature, (EncryptionPublicKey) keyPair.getPublicKey()).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new WaitingForK1State(receivedMessage.remoteDeviceUid, (EncryptionPrivateKey) keyPair.getPrivateKey());
            }
        }
    }


    public static class SendEphemeralKeyAndK1Step extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final InitialProtocolState startState;
        private final AliceIdentityAndEphemeralKeyMessage receivedMessage;

        public SendEphemeralKeyAndK1Step(InitialProtocolState startState, AliceIdentityAndEphemeralKeyMessage receivedMessage, ChannelCreationWithOwnedDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // check that the remoteDeviceUid in the receivedMessage is not our currentDeviceUid
                UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                if (currentDeviceUid == null) {
                    return new CancelledState();
                }
                if (Objects.equals(currentDeviceUid, receivedMessage.remoteDeviceUid)) {
                    Logger.w("Received a ping for a ChannelCreationWithOwnedDeviceProtocol with our currentDeviceUid");
                    return new CancelledState();
                }

                boolean signatureIsValid = Signature.verify(
                        Constants.SignatureContext.CHANNEL_CREATION,
                        currentDeviceUid,
                        receivedMessage.remoteDeviceUid,
                        getOwnedIdentity(),
                        getOwnedIdentity(),
                        getOwnedIdentity(),
                        receivedMessage.signature
                );

                if (!signatureIsValid) {
                    return new CancelledState();
                }

                if (ChannelCreationPingSignatureReceived.exists(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature)) {
                    // we already received a ping with the same signature!
                    return new CancelledState();
                } else {
                    // store the signature to prevent future replay
                    ChannelCreationPingSignatureReceived.create(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature);
                }
            }


            {
                // check whether there already is an instance of this protocol running
                ChannelCreationProtocolInstance channelCreationProtocolInstance = null;
                try {
                    channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(
                            protocolManagerSession,
                            receivedMessage.remoteDeviceUid,
                            getOwnedIdentity(),
                            getOwnedIdentity()
                    );
                } catch (SQLException ignored) {}
                if (channelCreationProtocolInstance != null) {
                    // an instance already exists, abort it, terminate this protocol, and restart it with a fresh ping
                    channelCreationProtocolInstance.delete();
                    protocolManagerSession.protocolDelegate.abortProtocol(protocolManagerSession.session, channelCreationProtocolInstance.getProtocolInstanceUid(), getOwnedIdentity());


                    UID childProtocolInstanceUid = new UID(getPrng());
                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                            CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID,
                            childProtocolInstanceUid);
                    ChannelMessageToSend messageToSend = new ChannelCreationWithOwnedDeviceProtocol.InitialMessage(coreProtocolMessage, receivedMessage.remoteDeviceUid).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                    return new CancelledState();
                } else {
                    // No previous instance of the protocol exists, create one
                    channelCreationProtocolInstance = ChannelCreationProtocolInstance.create(
                            protocolManagerSession,
                            receivedMessage.remoteDeviceUid,
                            getOwnedIdentity(),
                            getOwnedIdentity(),
                            getProtocolInstanceUid()
                    );
                    if (channelCreationProtocolInstance == null) {
                        throw new Exception();
                    }
                }
            }

            KeyPair keyPair = Suite.generateEncryptionKeyPair(getOwnedIdentity().getEncryptionPublicKey().getAlgorithmImplementation(), getPrng());
            if (keyPair == null) {
                throw new Exception();
            }

            // compute k1
            PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(receivedMessage.remoteEphemeralPublicKey);
            CiphertextAndKey ciphertextAndKey = publicKeyEncryption.kemEncrypt(receivedMessage.remoteEphemeralPublicKey, getPrng());
            AuthEncKey k1 = ciphertextAndKey.getKey();
            EncryptedBytes c1 = ciphertextAndKey.getCiphertext();

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(getOwnedIdentity(), getOwnedIdentity(), new UID[]{receivedMessage.remoteDeviceUid}));
            ChannelMessageToSend messageToSend = new BobEphemeralKeyAndK1Message(coreProtocolMessage, (EncryptionPublicKey) keyPair.getPublicKey(), c1).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitingForK2State(receivedMessage.remoteDeviceUid, (EncryptionPrivateKey) keyPair.getPrivateKey(), k1);
        }
    }


    public static class RecoverK1AndSendK2AndCreateChannelStep extends ProtocolStep {
        private final WaitingForK1State startState;
        private final BobEphemeralKeyAndK1Message receivedMessage;

        public RecoverK1AndSendK2AndCreateChannelStep(WaitingForK1State startState, BobEphemeralKeyAndK1Message receivedMessage, ChannelCreationWithOwnedDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(startState.ephemeralPrivateKey);
            AuthEncKey k1 = publicKeyEncryption.kemDecrypt(startState.ephemeralPrivateKey, receivedMessage.c1);
            if (k1 == null) {
                Logger.e("Could not recover k1.");
                return new CancelledState();
            }

            // compute k2
            publicKeyEncryption = Suite.getPublicKeyEncryption(receivedMessage.remoteEphemeralPublicKey);
            CiphertextAndKey ciphertextAndKey = publicKeyEncryption.kemEncrypt(receivedMessage.remoteEphemeralPublicKey, getPrng());
            AuthEncKey k2 = ciphertextAndKey.getKey();
            EncryptedBytes c2 = ciphertextAndKey.getCiphertext();

            Seed seed = Seed.of(k1, k2);

            // add the contact deviceUid if not already there
            try {
                protocolManagerSession.identityDelegate.addDeviceForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.remoteDeviceUid, null, null, null, null, true);

                // trigger an owned device discovery
                UID protocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        ConcreteProtocol.OWNED_DEVICE_DISCOVERY_PROTOCOL_ID,
                        protocolInstanceUid);
                ChannelMessageToSend message = new OwnedDeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
            } catch (Exception e) {
                Logger.w("Exception when adding an owned device");
            }

            // if there is already a channel, we have a problem! Abort the protocol and restart from scratch
            if (protocolManagerSession.channelDelegate.checkIfObliviousChannelExists(protocolManagerSession.session, getOwnedIdentity(), startState.remoteDeviceUid, getOwnedIdentity())) {
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID,
                        childProtocolInstanceUid);
                ChannelMessageToSend messageToSend = new ChannelCreationWithOwnedDeviceProtocol.InitialMessage(coreProtocolMessage, startState.remoteDeviceUid).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new CancelledState();
            }

            // create the channel
            protocolManagerSession.channelDelegate.createObliviousChannel(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    startState.remoteDeviceUid,
                    getOwnedIdentity(),
                    seed,
                    0
            );

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(getOwnedIdentity(), getOwnedIdentity(), new UID[]{startState.remoteDeviceUid}));
            ChannelMessageToSend messageToSend = new K2Message(coreProtocolMessage, c2).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitForFirstAckState(startState.remoteDeviceUid);
        }
    }


    public static class RecoverK2CreateChannelAndSendAckStep extends ProtocolStep {
        private final WaitingForK2State startState;
        private final K2Message receivedMessage;

        public RecoverK2CreateChannelAndSendAckStep(WaitingForK2State startState, K2Message receivedMessage, ChannelCreationWithOwnedDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(startState.ephemeralPrivateKey);
            AuthEncKey k2 = publicKeyEncryption.kemDecrypt(startState.ephemeralPrivateKey, receivedMessage.c2);
            if (k2 == null) {
                Logger.e("Could not recover k2.");
                return new CancelledState();
            }

            // Add the otherDeviceUid to the contactIdentity if needed --> we no longer trigger a device discovery
            try {
                protocolManagerSession.identityDelegate.addDeviceForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.remoteDeviceUid, null, null, null, null, true);

                // trigger an owned device discovery
                UID protocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        ConcreteProtocol.OWNED_DEVICE_DISCOVERY_PROTOCOL_ID,
                        protocolInstanceUid);
                ChannelMessageToSend message = new OwnedDeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
            } catch (Exception e) {
                Logger.w("Exception when adding an owned device");
            }

            // if there is already a channel, we have a problem! Abort the protocol and restart from scratch
            if (protocolManagerSession.channelDelegate.checkIfObliviousChannelExists(protocolManagerSession.session, getOwnedIdentity(), startState.remoteDeviceUid, getOwnedIdentity())) {
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID,
                        childProtocolInstanceUid);
                ChannelMessageToSend messageToSend = new ChannelCreationWithOwnedDeviceProtocol.InitialMessage(coreProtocolMessage, startState.remoteDeviceUid).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new CancelledState();
            }


            Seed seed = Seed.of(startState.k1, k2);
            protocolManagerSession.channelDelegate.createObliviousChannel(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    startState.remoteDeviceUid,
                    getOwnedIdentity(),
                    seed,
                    0
            );

            String serializedDetailsWithVersionAndPhoto = "";
            try {
                JsonIdentityDetailsWithVersionAndPhoto ownedDetailsWithVersionAndPhoto = protocolManagerSession.identityDelegate.getOwnedIdentityPublishedAndLatestDetails(protocolManagerSession.session, getOwnedIdentity())[0];
                serializedDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().writeValueAsString(ownedDetailsWithVersionAndPhoto);
            } catch (Exception e) {
                Logger.x(e);
            }

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(
                    getOwnedIdentity(),
                    getOwnedIdentity(),
                    new UID[]{startState.remoteDeviceUid},
                    false
            ));
            ChannelMessageToSend messageToSend = new FirstAckMessage(coreProtocolMessage, serializedDetailsWithVersionAndPhoto).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitForSecondAckState(startState.remoteDeviceUid);
        }
    }


    public static class ConfirmChannelAndSendAckStep extends ProtocolStep {
        private final WaitForFirstAckState startState;
        private final FirstAckMessage receivedMessage;

        public ConfirmChannelAndSendAckStep(WaitForFirstAckState startState, FirstAckMessage receivedMessage, ChannelCreationWithOwnedDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.remoteDeviceUid, protocol.ownedIdentity), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // update the publishedContactDetails with what we just received
                try {
                    JsonIdentityDetailsWithVersionAndPhoto ownDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.remoteSerializedIdentityWithVersionAndPhoto, JsonIdentityDetailsWithVersionAndPhoto.class);
                    if (ownDetailsWithVersionAndPhoto != null) {
                        boolean photoDownloadNeeded = protocolManagerSession.identityDelegate.setOwnedIdentityDetailsFromOtherDevice(protocolManagerSession.session, getOwnedIdentity(), ownDetailsWithVersionAndPhoto);

                        if (photoDownloadNeeded) {
                            // we need to download a photo
                            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                    DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID,
                                    new UID(getPrng()));
                            ChannelMessageToSend messageToSend = new DownloadIdentityPhotoChildProtocol.InitialMessage(coreProtocolMessage, getOwnedIdentity(), receivedMessage.remoteSerializedIdentityWithVersionAndPhoto).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        }
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
            }

            {
                // We received a message on the obliviousChannel, so we can confirm it
                protocolManagerSession.channelDelegate.confirmObliviousChannel(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        startState.remoteDeviceUid,
                        getOwnedIdentity()
                );
            }

            {
                // send this device capabilities to other device
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID,
                        childProtocolInstanceUid);
                ChannelMessageToSend messageToSend = new DeviceCapabilitiesDiscoveryProtocol.InitialSingleOwnedDeviceMessage(coreProtocolMessage, startState.remoteDeviceUid, false).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // Delete the ChannelCreationProtocolInstance
                try {
                    ChannelCreationProtocolInstance channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(protocolManagerSession, startState.remoteDeviceUid, getOwnedIdentity(), getOwnedIdentity());
                    channelCreationProtocolInstance.delete();
                } catch (Exception e) {
                    Logger.w("Exception when deleting a ChannelCreationProtocolInstance");
                }
            }

            {
                // send Ack message to Bob
                String serializedDetailsWithVersionAndPhoto = "";
                try {
                    JsonIdentityDetailsWithVersionAndPhoto ownedDetailsWithVersionAndPhoto = protocolManagerSession.identityDelegate.getOwnedIdentityPublishedAndLatestDetails(protocolManagerSession.session, getOwnedIdentity())[0];
                    serializedDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().writeValueAsString(ownedDetailsWithVersionAndPhoto);
                } catch (Exception e) {
                    Logger.x(e);
                }

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(
                        getOwnedIdentity(),
                        getOwnedIdentity(),
                        new UID[]{startState.remoteDeviceUid},
                        true
                ));
                ChannelMessageToSend messageToSend = new SecondAckMessage(coreProtocolMessage, serializedDetailsWithVersionAndPhoto).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

//            {
//                // initiate a device synchronization protocol
//                UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
//                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
//                        ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                        SynchronizationProtocol.computeOngoingProtocolInstanceUid(getOwnedIdentity(), currentDeviceUid, startState.remoteDeviceUid),
//                        false);
//                ChannelMessageToSend message = new SynchronizationProtocol.InitiateSyncMessage(coreProtocolMessage, startState.remoteDeviceUid).generateChannelProtocolMessageToSend();
//                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
//            }

            return new ChannelConfirmedState();
        }
    }

    public static class ConfirmChannelStep extends ProtocolStep {
        private final WaitForSecondAckState startState;
        private final SecondAckMessage receivedMessage;

        public ConfirmChannelStep(WaitForSecondAckState startState, SecondAckMessage receivedMessage, ChannelCreationWithOwnedDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.remoteDeviceUid, protocol.ownedIdentity), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();


            // update the publishedContactDetails with what we just received
            {
                // update the publishedContactDetails with what we just received
                try {
                    JsonIdentityDetailsWithVersionAndPhoto ownDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.remoteSerializedIdentityWithVersionAndPhoto, JsonIdentityDetailsWithVersionAndPhoto.class);
                    if (ownDetailsWithVersionAndPhoto != null) {
                        boolean photoDownloadNeeded = protocolManagerSession.identityDelegate.setOwnedIdentityDetailsFromOtherDevice(protocolManagerSession.session, getOwnedIdentity(), ownDetailsWithVersionAndPhoto);

                        if (photoDownloadNeeded) {
                            // we need to download a photo
                            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                    DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID,
                                    new UID(getPrng()));
                            ChannelMessageToSend messageToSend = new DownloadIdentityPhotoChildProtocol.InitialMessage(coreProtocolMessage, getOwnedIdentity(), receivedMessage.remoteSerializedIdentityWithVersionAndPhoto).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        }
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
            }

            {
                // we can confirm the obliviousChannel
                protocolManagerSession.channelDelegate.confirmObliviousChannel(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        startState.remoteDeviceUid,
                        getOwnedIdentity()
                );
            }

            {
                // send this device capabilities to other device
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID,
                        childProtocolInstanceUid);
                ChannelMessageToSend messageToSend = new DeviceCapabilitiesDiscoveryProtocol.InitialSingleOwnedDeviceMessage(coreProtocolMessage, startState.remoteDeviceUid, false).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }


            // Delete the ChannelCreationProtocolInstance
            try {
                ChannelCreationProtocolInstance channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(protocolManagerSession, startState.remoteDeviceUid, getOwnedIdentity(), getOwnedIdentity());
                channelCreationProtocolInstance.delete();
            } catch (Exception e) {
                Logger.w("Exception when deleting a ChannelCreationProtocolInstance");
            }

//            {
//                // initiate a device synchronization protocol
//                UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
//                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
//                        ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                        SynchronizationProtocol.computeOngoingProtocolInstanceUid(getOwnedIdentity(), currentDeviceUid, startState.remoteDeviceUid),
//                        false);
//                ChannelMessageToSend message = new SynchronizationProtocol.InitiateSyncMessage(coreProtocolMessage, startState.remoteDeviceUid).generateChannelProtocolMessageToSend();
//                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
//            }

            return new ChannelConfirmedState();
        }
    }

    //endregion

}
