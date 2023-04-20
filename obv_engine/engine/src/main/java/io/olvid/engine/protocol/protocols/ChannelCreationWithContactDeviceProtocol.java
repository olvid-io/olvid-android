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

import java.sql.SQLException;

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
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class ChannelCreationWithContactDeviceProtocol extends ConcreteProtocol {
    public ChannelCreationWithContactDeviceProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID;
    }




    //region States

    static final int CANCELLED_STATE_ID = 1;
    static final int PING_SENT_STATE_ID = 2;
    static final int WAITING_FOR_K1_STATE_ID = 3;
    static final int WAITING_FOR_K2_STATE_ID = 4;
    static final int WAIT_FOR_FIRST_ACK_STATE_ID = 5;
    static final int WAIT_UNTIL_CONTACT_IS_TRUSTED_STATE_ID = 6;
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
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final EncryptionPrivateKey ephemeralPrivateKey;

        public WaitingForK1State(Encoded encodedState) throws Exception {
            super(WAITING_FOR_K1_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 3) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
            this.ephemeralPrivateKey = (EncryptionPrivateKey) list[2].decodePrivateKey();
        }

        WaitingForK1State(Identity contactIdentity, UID contactDeviceUid, EncryptionPrivateKey ephemeralPrivateKey) {
            super(WAITING_FOR_K1_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.ephemeralPrivateKey = ephemeralPrivateKey;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(ephemeralPrivateKey),
            });
        }
    }


    public static class WaitingForK2State extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final EncryptionPrivateKey ephemeralPrivateKey;
        private final AuthEncKey k1;

        public WaitingForK2State(Encoded encodedState) throws Exception {
            super(WAITING_FOR_K2_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
            this.ephemeralPrivateKey = (EncryptionPrivateKey) list[2].decodePrivateKey();
            this.k1 = (AuthEncKey) list[3].decodeSymmetricKey();
        }

        WaitingForK2State(Identity contactIdentity, UID contactDeviceUid, EncryptionPrivateKey ephemeralPrivateKey, AuthEncKey k1) {
            super(WAITING_FOR_K2_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.ephemeralPrivateKey = ephemeralPrivateKey;
            this.k1 = k1;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(ephemeralPrivateKey),
                    Encoded.of(k1),
            });
        }
    }


    public static class WaitForFirstAckState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;

        public WaitForFirstAckState(Encoded encodedState) throws Exception {
            super(WAIT_FOR_FIRST_ACK_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
        }

        WaitForFirstAckState(Identity contactIdentity, UID contactDeviceUid) {
            super(WAIT_FOR_FIRST_ACK_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
            });
        }
    }


    public static class WaitUntilContactIsTrustedState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final Seed seed;

        public WaitUntilContactIsTrustedState(Encoded encodedState) throws Exception {
            super(WAIT_UNTIL_CONTACT_IS_TRUSTED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 3) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
            this.seed = list[2].decodeSeed();
        }

        WaitUntilContactIsTrustedState(Identity contactIdentity, UID contactDeviceUid, Seed seed) {
            super(WAIT_UNTIL_CONTACT_IS_TRUSTED_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.seed = seed;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(seed),
            });
        }
    }


    public static class WaitForSecondAckState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;

        public WaitForSecondAckState(Encoded encodedState) throws Exception {
            super(WAIT_FOR_SECOND_ACK_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
        }

        WaitForSecondAckState(Identity contactIdentity, UID contactDeviceUid) {
            super(WAIT_FOR_SECOND_ACK_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
            });
        }
    }


    public static class ChannelConfirmedState extends ConcreteProtocolState {
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
        private final Identity contactIdentity;
        private final UID contactDeviceUid;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, UID contactDeviceUid) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactDeviceUid = receivedMessage.getInputs()[1].decodeUid();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
            };
        }
    }


    public static class PingMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final byte[] signature;

        public PingMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, UID contactDeviceUid, byte[] signature) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.signature = signature;
        }

        public PingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactDeviceUid = receivedMessage.getInputs()[1].decodeUid();
            this.signature = receivedMessage.getInputs()[2].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return PING_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(signature),
            };
        }
    }


    public static class AliceIdentityAndEphemeralKeyMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final byte[] signature;
        private final EncryptionPublicKey contactEphemeralPublicKey;

        public AliceIdentityAndEphemeralKeyMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, UID contactDeviceUid, byte[] signature, EncryptionPublicKey contactEphemeralPublicKey) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.signature = signature;
            this.contactEphemeralPublicKey = contactEphemeralPublicKey;
        }

        public AliceIdentityAndEphemeralKeyMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactDeviceUid = receivedMessage.getInputs()[1].decodeUid();
            this.signature = receivedMessage.getInputs()[2].decodeBytes();
            this.contactEphemeralPublicKey = (EncryptionPublicKey) receivedMessage.getInputs()[3].decodePublicKey();
        }

        @Override
        public int getProtocolMessageId() {
            return ALICE_IDENTITY_AND_EPHEMERAL_KEY_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(signature),
                    Encoded.of(contactEphemeralPublicKey),
            };
        }
    }


    public static class BobEphemeralKeyAndK1Message extends ConcreteProtocolMessage {
        private final EncryptionPublicKey contactEphemeralPublicKey;
        private final EncryptedBytes c1;

        BobEphemeralKeyAndK1Message(CoreProtocolMessage coreProtocolMessage, EncryptionPublicKey contactEphemeralPublicKey, EncryptedBytes c1) {
            super(coreProtocolMessage);
            this.contactEphemeralPublicKey = contactEphemeralPublicKey;
            this.c1 = c1;
        }

        public BobEphemeralKeyAndK1Message(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactEphemeralPublicKey = (EncryptionPublicKey) receivedMessage.getInputs()[0].decodePublicKey();
            this.c1 = receivedMessage.getInputs()[1].decodeEncryptedData();
        }

        @Override
        public int getProtocolMessageId() {
            return BOB_EPHEMERAL_KEY_AND_K1_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactEphemeralPublicKey),
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
        private final String contactSerializedIdentityWithVersionAndPhoto;

        FirstAckMessage(CoreProtocolMessage coreProtocolMessage, String contactSerializedIdentityWithVersionAndPhoto) {
            super(coreProtocolMessage);
            this.contactSerializedIdentityWithVersionAndPhoto = contactSerializedIdentityWithVersionAndPhoto;
        }

        public FirstAckMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactSerializedIdentityWithVersionAndPhoto = receivedMessage.getInputs()[0].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return FIRST_ACK_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactSerializedIdentityWithVersionAndPhoto),
            };
        }
    }


    public static class SecondAckMessage extends ConcreteProtocolMessage {
        private final String contactSerializedIdentityWithVersionAndPhoto;

        SecondAckMessage(CoreProtocolMessage coreProtocolMessage, String contactSerializedIdentityWithVersionAndPhoto) {
            super(coreProtocolMessage);
            this.contactSerializedIdentityWithVersionAndPhoto = contactSerializedIdentityWithVersionAndPhoto;
        }

        public SecondAckMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactSerializedIdentityWithVersionAndPhoto = receivedMessage.getInputs()[0].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return SECOND_ACK_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactSerializedIdentityWithVersionAndPhoto),
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
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public SendPingStep(InitialProtocolState startState, InitialMessage receivedMessage, ChannelCreationWithContactDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // check that the contactIdentity in the receivedMessage is indeed trusted by the ownedIdentity running the protocol
            if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                Logger.w("Trying to run a ChannelCreationWithContactDeviceProtocol with an untrusted or revoked ContactIdentity");
                return new CancelledState();
            }

            // clean any ongoing instance of this protocol
            ChannelCreationProtocolInstance channelCreationProtocolInstance = null;
            try {
                channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(
                        protocolManagerSession,
                        receivedMessage.contactDeviceUid,
                        receivedMessage.contactIdentity,
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
                    receivedMessage.contactDeviceUid,
                    receivedMessage.contactIdentity);

            // send a signed ping proving you trust the contact and have no channel with him
            UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            if (currentDeviceUid == null) {
                return new CancelledState();
            }

            byte[] signature = protocolManagerSession.identityDelegate.signChannel(
                    protocolManagerSession.session,
                    Constants.SignatureContext.CHANNEL_CREATION,
                    receivedMessage.contactIdentity,
                    receivedMessage.contactDeviceUid,
                    getOwnedIdentity(),
                    currentDeviceUid,
                    getPrng()
            );

            // send the ping containing the signature
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[]{receivedMessage.contactDeviceUid}));
            ChannelMessageToSend messageToSend = new PingMessage(coreProtocolMessage, getOwnedIdentity(), currentDeviceUid, signature).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new PingSentState();
        }
    }



    public static class SendPingOrEphemeralKeyStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final PingMessage receivedMessage;

        public SendPingOrEphemeralKeyStep(InitialProtocolState startState, PingMessage receivedMessage, ChannelCreationWithContactDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // check that the contactIdentity in the receivedMessage is indeed trusted by the ownedIdentity running the protocol
            if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                Logger.w("Received a ping for a ChannelCreationWithContactDeviceProtocol from an untrusted or revoked ContactIdentity");
                return new CancelledState();
            }

            // verify the signature in the PingMessage
            UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            if (currentDeviceUid == null) {
                return new CancelledState();
            }

            boolean signatureIsValid = Signature.verify(
                    Constants.SignatureContext.CHANNEL_CREATION,
                    currentDeviceUid,
                    receivedMessage.contactDeviceUid,
                    getOwnedIdentity(),
                    receivedMessage.contactIdentity,
                    receivedMessage.contactIdentity,
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

            // Signature is valid! The contact does not have a channel and trusts us
            {
                // clean any ongoing instance of this protocol
                ChannelCreationProtocolInstance channelCreationProtocolInstance = null;
                try {
                    channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(
                            protocolManagerSession,
                            receivedMessage.contactDeviceUid,
                            receivedMessage.contactIdentity,
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
                        receivedMessage.contactDeviceUid,
                        receivedMessage.contactIdentity);
            }



            // Compute a signature to prove we trust the contact and don't have any channel/ongoing protocol with him

            // only rewrite the end of the prefix
            byte[] signature = protocolManagerSession.identityDelegate.signChannel(
                    protocolManagerSession.session,
                    Constants.SignatureContext.CHANNEL_CREATION,
                    receivedMessage.contactIdentity,
                    receivedMessage.contactDeviceUid,
                    getOwnedIdentity(),
                    currentDeviceUid,
                    getPrng()
            );



            // If we are in charge (small deviceUid), send an ephemeral key
            // otherwise simply send a ping back
            int compare = currentDeviceUid.compareTo(receivedMessage.contactDeviceUid);
            if (compare == 0) {
                compare = getOwnedIdentity().compareTo(receivedMessage.contactIdentity);
            }
            if (compare >= 0) {
                // Not in charge

                // send the ping containing the signature
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[]{receivedMessage.contactDeviceUid}));
                ChannelMessageToSend messageToSend = new PingMessage(coreProtocolMessage, getOwnedIdentity(), currentDeviceUid, signature).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new PingSentState();
            } else {
                // In charge
                // Create a new ChannelCreationProtocolInstance
                ChannelCreationProtocolInstance channelCreationProtocolInstance = ChannelCreationProtocolInstance.create(
                        protocolManagerSession,
                        receivedMessage.contactDeviceUid,
                        receivedMessage.contactIdentity,
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

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[]{receivedMessage.contactDeviceUid}));
                ChannelMessageToSend messageToSend = new AliceIdentityAndEphemeralKeyMessage(coreProtocolMessage, getOwnedIdentity(), currentDeviceUid, signature, (EncryptionPublicKey) keyPair.getPublicKey()).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new WaitingForK1State(receivedMessage.contactIdentity, receivedMessage.contactDeviceUid, (EncryptionPrivateKey) keyPair.getPrivateKey());
            }
        }
    }


    public static class SendEphemeralKeyAndK1Step extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final InitialProtocolState startState;
        private final AliceIdentityAndEphemeralKeyMessage receivedMessage;

        public SendEphemeralKeyAndK1Step(InitialProtocolState startState, AliceIdentityAndEphemeralKeyMessage receivedMessage, ChannelCreationWithContactDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // check that the contactIdentity in the receivedMessage is indeed trusted by the ownedIdentity running the protocol
                if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                    Logger.w("Received a ping for a ChannelCreationWithContactDeviceProtocol from an untrusted ContactIdentity");
                    return new CancelledState();
                }

                // verify the signature in the AliceIdentityAndEphemeralKeyMessage
                UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                if (currentDeviceUid == null) {
                    return new CancelledState();
                }

                boolean signatureIsValid = Signature.verify(
                        Constants.SignatureContext.CHANNEL_CREATION,
                        currentDeviceUid,
                        receivedMessage.contactDeviceUid,
                        getOwnedIdentity(),
                        receivedMessage.contactIdentity,
                        receivedMessage.contactIdentity,
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
                            receivedMessage.contactDeviceUid,
                            receivedMessage.contactIdentity,
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
                            CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID,
                            childProtocolInstanceUid,
                            false
                    );
                    ChannelMessageToSend messageToSend = new ChannelCreationWithContactDeviceProtocol.InitialMessage(coreProtocolMessage, receivedMessage.contactIdentity, receivedMessage.contactDeviceUid).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                    return new CancelledState();
                } else {
                    // No previous instance of the protocol exists, create one
                    channelCreationProtocolInstance = ChannelCreationProtocolInstance.create(
                            protocolManagerSession,
                            receivedMessage.contactDeviceUid,
                            receivedMessage.contactIdentity,
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
            PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(receivedMessage.contactEphemeralPublicKey);
            CiphertextAndKey ciphertextAndKey = publicKeyEncryption.kemEncrypt(receivedMessage.contactEphemeralPublicKey, getPrng());
            AuthEncKey k1 = ciphertextAndKey.getKey();
            EncryptedBytes c1 = ciphertextAndKey.getCiphertext();

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[]{receivedMessage.contactDeviceUid}));
            ChannelMessageToSend messageToSend = new BobEphemeralKeyAndK1Message(coreProtocolMessage, (EncryptionPublicKey) keyPair.getPublicKey(), c1).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitingForK2State(receivedMessage.contactIdentity, receivedMessage.contactDeviceUid, (EncryptionPrivateKey) keyPair.getPrivateKey(), k1);
        }
    }


    public static class RecoverK1AndSendK2AndCreateChannelStep extends ProtocolStep {
        private final WaitingForK1State startState;
        private final BobEphemeralKeyAndK1Message receivedMessage;

        public RecoverK1AndSendK2AndCreateChannelStep(WaitingForK1State startState, BobEphemeralKeyAndK1Message receivedMessage, ChannelCreationWithContactDeviceProtocol protocol) throws Exception {
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
            publicKeyEncryption = Suite.getPublicKeyEncryption(receivedMessage.contactEphemeralPublicKey);
            CiphertextAndKey ciphertextAndKey = publicKeyEncryption.kemEncrypt(receivedMessage.contactEphemeralPublicKey, getPrng());
            AuthEncKey k2 = ciphertextAndKey.getKey();
            EncryptedBytes c2 = ciphertextAndKey.getCiphertext();

            Seed seed = Seed.of(k1, k2);


            // check the contact is not revoked
            if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                Logger.e("Channel creation with revoked contact aborted");
                return new CancelledState();
            }

            // add the contact deviceUid if not already there --> we no longer trigger a device discovery
            try {
                protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, startState.contactDeviceUid);
            } catch (Exception e) {
                Logger.w("Exception when adding a contact device");
            }

            // if there is already a channel, we have a problem! Abort the protocol and restart from scratch
            if (protocolManagerSession.channelDelegate.checkIfObliviousChannelExists(protocolManagerSession.session, getOwnedIdentity(), startState.contactDeviceUid, startState.contactIdentity)) {
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new ChannelCreationWithContactDeviceProtocol.InitialMessage(coreProtocolMessage, startState.contactIdentity, startState.contactDeviceUid).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new CancelledState();
            }

            // create the channel
            protocolManagerSession.channelDelegate.createObliviousChannel(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    startState.contactDeviceUid,
                    startState.contactIdentity,
                    seed,
                    0
            );

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), new UID[]{startState.contactDeviceUid}));
            ChannelMessageToSend messageToSend = new K2Message(coreProtocolMessage, c2).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitForFirstAckState(startState.contactIdentity, startState.contactDeviceUid);
        }
    }


    public static class RecoverK2CreateChannelAndSendAckStep extends ProtocolStep {
        private final WaitingForK2State startState;
        private final K2Message receivedMessage;

        public RecoverK2CreateChannelAndSendAckStep(WaitingForK2State startState, K2Message receivedMessage, ChannelCreationWithContactDeviceProtocol protocol) throws Exception {
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

            // check the contact is not revoked
            if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                Logger.e("Channel creation with revoked contact aborted");
                return new CancelledState();
            }

            // Add the contactDeviceUid to the contactIdentity if needed --> we no longer trigger a device discovery
            try {
                protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, startState.contactDeviceUid);
            } catch (Exception e) {
                Logger.w("Exception when adding a contact device");
            }

            // if there is already a channel, we have a problem! Abort the protocol and restart from scratch
            if (protocolManagerSession.channelDelegate.checkIfObliviousChannelExists(protocolManagerSession.session, getOwnedIdentity(), startState.contactDeviceUid, startState.contactIdentity)) {
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new ChannelCreationWithContactDeviceProtocol.InitialMessage(coreProtocolMessage, startState.contactIdentity, startState.contactDeviceUid).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new CancelledState();
            }


            Seed seed = Seed.of(startState.k1, k2);
            protocolManagerSession.channelDelegate.createObliviousChannel(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    startState.contactDeviceUid,
                    startState.contactIdentity,
                    seed,
                    0
            );

            String serializedDetailsWithVersionAndPhoto = "";
            try {
                JsonIdentityDetailsWithVersionAndPhoto ownedDetailsWithVersionAndPhoto = protocolManagerSession.identityDelegate.getOwnedIdentityPublishedAndLatestDetails(protocolManagerSession.session, getOwnedIdentity())[0];
                serializedDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().writeValueAsString(ownedDetailsWithVersionAndPhoto);
            } catch (Exception e) {
                e.printStackTrace();
            }

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(
                    startState.contactIdentity,
                    getOwnedIdentity(),
                    new UID[]{startState.contactDeviceUid},
                    false
            ));
            ChannelMessageToSend messageToSend = new FirstAckMessage(coreProtocolMessage, serializedDetailsWithVersionAndPhoto).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitForSecondAckState(startState.contactIdentity, startState.contactDeviceUid);
        }
    }


    public static class ConfirmChannelAndSendAckStep extends ProtocolStep {
        private final WaitForFirstAckState startState;
        private final FirstAckMessage receivedMessage;

        public ConfirmChannelAndSendAckStep(WaitForFirstAckState startState, FirstAckMessage receivedMessage, ChannelCreationWithContactDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.contactDeviceUid, startState.contactIdentity), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // update the publishedContactDetails with what we just received
                try {
                    JsonIdentityDetailsWithVersionAndPhoto contactDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.contactSerializedIdentityWithVersionAndPhoto, JsonIdentityDetailsWithVersionAndPhoto.class);
                    if (contactDetailsWithVersionAndPhoto != null) {
                        protocolManagerSession.identityDelegate.setContactPublishedDetails(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), contactDetailsWithVersionAndPhoto, true);
                        JsonIdentityDetailsWithVersionAndPhoto[] jsons = protocolManagerSession.identityDelegate.getContactPublishedAndTrustedDetails(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity);
                        JsonIdentityDetailsWithVersionAndPhoto newDetails = jsons[0];
                        if (newDetails.getPhotoUrl() == null && newDetails.getPhotoServerKey() != null && newDetails.getPhotoServerLabel() != null) {
                            // we need to download a photo
                            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                    DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID,
                                    new UID(getPrng()),
                                    false
                            );
                            ChannelMessageToSend messageToSend = new DownloadIdentityPhotoChildProtocol.InitialMessage(coreProtocolMessage, startState.contactIdentity, protocol.getJsonObjectMapper().writeValueAsString(newDetails)).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            {
                // We received a message on the obliviousChannel, so we can confirm it
                // we do this after updating the details so that if the contact was to lose their "keycloak certified" status, we know messages in keycloak groups are not sent
                protocolManagerSession.channelDelegate.confirmObliviousChannel(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        startState.contactDeviceUid,
                        startState.contactIdentity
                );
            }

            {
                // send this device capabilities to contact
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new DeviceCapabilitiesDiscoveryProtocol.InitialSingleContactDeviceMessage(coreProtocolMessage, startState.contactIdentity, startState.contactDeviceUid, false).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // Delete the ChannelCreationProtocolInstance
                try {
                    ChannelCreationProtocolInstance channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(protocolManagerSession, startState.contactDeviceUid, startState.contactIdentity, getOwnedIdentity());
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
                    e.printStackTrace();
                }

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(
                        startState.contactIdentity,
                        getOwnedIdentity(),
                        new UID[]{startState.contactDeviceUid},
                        true
                ));
                ChannelMessageToSend messageToSend = new SecondAckMessage(coreProtocolMessage, serializedDetailsWithVersionAndPhoto).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // make sure we agree on our mutual oneToOne status
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        ONE_TO_ONE_CONTACT_INVITATION_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new OneToOneContactInvitationProtocol.InitiateOneToOneStatusSyncWithOneContactMessage(coreProtocolMessage, startState.contactIdentity).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new ChannelConfirmedState();
        }
    }

    public static class ConfirmChannelStep extends ProtocolStep {
        private final WaitForSecondAckState startState;
        private final SecondAckMessage receivedMessage;

        public ConfirmChannelStep(WaitForSecondAckState startState, SecondAckMessage receivedMessage, ChannelCreationWithContactDeviceProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.contactDeviceUid, startState.contactIdentity), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();


            // update the publishedContactDetails with what we just received
            {
                try {
                    JsonIdentityDetailsWithVersionAndPhoto contactDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.contactSerializedIdentityWithVersionAndPhoto, JsonIdentityDetailsWithVersionAndPhoto.class);
                    if (contactDetailsWithVersionAndPhoto != null) {
                        protocolManagerSession.identityDelegate.setContactPublishedDetails(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), contactDetailsWithVersionAndPhoto, true);
                        JsonIdentityDetailsWithVersionAndPhoto[] jsons = protocolManagerSession.identityDelegate.getContactPublishedAndTrustedDetails(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity);
                        JsonIdentityDetailsWithVersionAndPhoto newDetails = jsons[0];
                        if (newDetails.getPhotoUrl() == null && newDetails.getPhotoServerKey() != null && newDetails.getPhotoServerLabel() != null) {
                            // we need to download a photo
                            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                    DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID,
                                    new UID(getPrng()),
                                    false
                            );
                            ChannelMessageToSend messageToSend = new DownloadIdentityPhotoChildProtocol.InitialMessage(coreProtocolMessage, startState.contactIdentity, protocol.getJsonObjectMapper().writeValueAsString(newDetails)).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            {
                // we can confirm the obliviousChannel
                // we do this after updating the details so that if the contact was to lose their "keycloak certified" status, we know messages in keycloak groups are not sent
                protocolManagerSession.channelDelegate.confirmObliviousChannel(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        startState.contactDeviceUid,
                        startState.contactIdentity
                );
            }

            // send this device capabilities to contact
            {
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new DeviceCapabilitiesDiscoveryProtocol.InitialSingleContactDeviceMessage(coreProtocolMessage, startState.contactIdentity, startState.contactDeviceUid, false).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }


            // Delete the ChannelCreationProtocolInstance
            try {
                ChannelCreationProtocolInstance channelCreationProtocolInstance = ChannelCreationProtocolInstance.get(protocolManagerSession, startState.contactDeviceUid, startState.contactIdentity, getOwnedIdentity());
                channelCreationProtocolInstance.delete();
            } catch (Exception e) {
                Logger.w("Exception when deleting a ChannelCreationProtocolInstance");
            }

            return new ChannelConfirmedState();
        }
    }

    //endregion

}
