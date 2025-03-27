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

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.PublicKeyEncryption;
import io.olvid.engine.crypto.Suite;
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
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;


public class FullRatchetProtocol extends ConcreteProtocol {
    public FullRatchetProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return FULL_RATCHET_PROTOCOL_ID;
    }


    public static UID computeProtocolUid(Identity aliceIdentity, Identity bobIdentity, UID aliceDeviceUid, UID bobDeviceUid) {
        Seed prngSeed = new Seed(new Seed(aliceIdentity.getBytes()), new Seed(bobIdentity.getBytes()), new Seed(aliceDeviceUid.getBytes()), new Seed(bobDeviceUid.getBytes()));
        PRNG seededPRNG = Suite.getDefaultPRNG(0, prngSeed);
        return new UID(seededPRNG);
    }

    // region States

    public static final int ALICE_WAITING_FOR_K1_STATE_ID = 1;
    public static final int BOB_WAITING_FOR_K2_STATE_ID = 2;
    public static final int ALICE_WAITING_FOR_ACK_STATE_ID = 3;
    public static final int FULL_RATCHET_DONE_STATE_ID = 4;
    public static final int CANCELLED_STATE_ID = 5;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{FULL_RATCHET_DONE_STATE_ID, CANCELLED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case ALICE_WAITING_FOR_K1_STATE_ID:
                return AliceWaitingForK1State.class;
            case BOB_WAITING_FOR_K2_STATE_ID:
                return BobWaitingForK2State.class;
            case ALICE_WAITING_FOR_ACK_STATE_ID:
                return AliceWaitingForAckState.class;
            case FULL_RATCHET_DONE_STATE_ID:
                return FullRatchetDoneState.class;
            case CANCELLED_STATE_ID:
                return CancelledState.class;
            default:
                return null;
        }
    }

    public static class AliceWaitingForK1State extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final EncryptionPrivateKey ephemeralPrivateKey;
        private final long restartCounter;

        public AliceWaitingForK1State(Encoded encodedState) throws Exception {
            super(ALICE_WAITING_FOR_K1_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
            this.ephemeralPrivateKey = (EncryptionPrivateKey) list[2].decodePrivateKey();
            this.restartCounter = list[3].decodeLong();
        }

        AliceWaitingForK1State(Identity contactIdentity, UID contactDeviceUid, EncryptionPrivateKey ephemeralPrivateKey, long restartCounter) {
            super(ALICE_WAITING_FOR_K1_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.ephemeralPrivateKey = ephemeralPrivateKey;
            this.restartCounter = restartCounter;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(ephemeralPrivateKey),
                    Encoded.of(restartCounter),
            });
        }
    }


    public static class BobWaitingForK2State extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final EncryptionPrivateKey ephemeralPrivateKey;
        private final AuthEncKey k1;
        private final long restartCounter;

        public BobWaitingForK2State(Encoded encodedState) throws Exception {
            super(BOB_WAITING_FOR_K2_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 5) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
            this.ephemeralPrivateKey = (EncryptionPrivateKey) list[2].decodePrivateKey();
            this.k1 = (AuthEncKey) list[3].decodeSymmetricKey();
            this.restartCounter = list[4].decodeLong();
        }

        BobWaitingForK2State(Identity contactIdentity, UID contactDeviceUid, EncryptionPrivateKey ephemeralPrivateKey, AuthEncKey k1, long restartCounter) {
            super(BOB_WAITING_FOR_K2_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.ephemeralPrivateKey = ephemeralPrivateKey;
            this.k1 = k1;
            this.restartCounter = restartCounter;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(ephemeralPrivateKey),
                    Encoded.of(k1),
                    Encoded.of(restartCounter),
            });
        }
    }


    public static class AliceWaitingForAckState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final Seed seed;
        private final long restartCounter;

        public AliceWaitingForAckState(Encoded encodedState) throws Exception {
            super(ALICE_WAITING_FOR_ACK_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDeviceUid = list[1].decodeUid();
            this.seed = list[2].decodeSeed();
            this.restartCounter = list[3].decodeLong();
        }

        AliceWaitingForAckState(Identity contactIdentity, UID contactDeviceUid, Seed seed, long restartCounter) {
            super(ALICE_WAITING_FOR_ACK_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
            this.seed = seed;
            this.restartCounter = restartCounter;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
                    Encoded.of(seed),
                    Encoded.of(restartCounter),
            });
        }
    }

    public static class FullRatchetDoneState extends ConcreteProtocolState {
        public FullRatchetDoneState(Encoded encodedState) throws Exception {
            super(FULL_RATCHET_DONE_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }
        FullRatchetDoneState() {
            super(FULL_RATCHET_DONE_STATE_ID);
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
        CancelledState() {
            super(CANCELLED_STATE_ID);
        }
        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }


    // if receiving an initial message while in waiting_for_k1 state, resend a new ephemeral key, increment the internal full ratchet counter
    // if receiving an ephemeral key while in waiting_for_k2 state, resend a new ephemeral key, use the new internal full ratchet counter
    // once you get the ack, you can change the send seed
    // endregion

    // region Messages

    static final int INITIAL_MESSAGE_ID = 0;
    static final int ALICE_EPHEMERAL_KEY_MESSAGE_ID = 1;
    static final int BOB_EPHEMERAL_KEY_AND_K1_MESSAGE_ID = 2;
    static final int ALICE_K2_MESSAGE_ID = 3;
    static final int BOB_ACK_MESSAGE_ID = 4;


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case ALICE_EPHEMERAL_KEY_MESSAGE_ID:
                return AliceEphemeralKeyMessage.class;
            case BOB_EPHEMERAL_KEY_AND_K1_MESSAGE_ID:
                return BobEphemeralKeyAndK1Message.class;
            case ALICE_K2_MESSAGE_ID:
                return AliceK2Message.class;
            case BOB_ACK_MESSAGE_ID:
                return BobAckMessage.class;
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

    public static class AliceEphemeralKeyMessage extends ConcreteProtocolMessage {
        private final EncryptionPublicKey contactEphemeralPublicKey;
        private final long restartCounter;

        public AliceEphemeralKeyMessage(CoreProtocolMessage coreProtocolMessage, EncryptionPublicKey contactEphemeralPublicKey, long restartCounter) {
            super(coreProtocolMessage);
            this.contactEphemeralPublicKey = contactEphemeralPublicKey;
            this.restartCounter = restartCounter;
        }

        public AliceEphemeralKeyMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactEphemeralPublicKey = (EncryptionPublicKey) receivedMessage.getInputs()[0].decodePublicKey();
            this.restartCounter = receivedMessage.getInputs()[1].decodeLong();
        }

        @Override
        public int getProtocolMessageId() {
            return ALICE_EPHEMERAL_KEY_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactEphemeralPublicKey),
                    Encoded.of(restartCounter),
            };
        }
    }

    public static class BobEphemeralKeyAndK1Message extends ConcreteProtocolMessage {
        private final EncryptionPublicKey contactEphemeralPublicKey;
        private final EncryptedBytes c1;
        private final long restartCounter;

        public BobEphemeralKeyAndK1Message(CoreProtocolMessage coreProtocolMessage, EncryptionPublicKey contactEphemeralPublicKey, EncryptedBytes c1, long restartCounter) {
            super(coreProtocolMessage);
            this.contactEphemeralPublicKey = contactEphemeralPublicKey;
            this.c1 = c1;
            this.restartCounter = restartCounter;
        }

        public BobEphemeralKeyAndK1Message(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            this.contactEphemeralPublicKey = (EncryptionPublicKey) receivedMessage.getInputs()[0].decodePublicKey();
            this.c1 = receivedMessage.getInputs()[1].decodeEncryptedData();
            this.restartCounter = receivedMessage.getInputs()[2].decodeLong();
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
                    Encoded.of(restartCounter),
            };
        }
    }

    public static class AliceK2Message extends ConcreteProtocolMessage {
        private final EncryptedBytes c2;
        private final long restartCounter;

        public AliceK2Message(CoreProtocolMessage coreProtocolMessage, EncryptedBytes c2, long restartCounter) {
            super(coreProtocolMessage);
            this.c2 = c2;
            this.restartCounter = restartCounter;
        }

        public AliceK2Message(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.c2 = receivedMessage.getInputs()[0].decodeEncryptedData();
            this.restartCounter = receivedMessage.getInputs()[1].decodeLong();
        }

        @Override
        public int getProtocolMessageId() {
            return ALICE_K2_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(c2),
                    Encoded.of(restartCounter),
            };
        }
    }

    public static class BobAckMessage extends ConcreteProtocolMessage {
        private final long restartCounter;

        public BobAckMessage(CoreProtocolMessage coreProtocolMessage, long restartCounter) {
            super(coreProtocolMessage);
            this.restartCounter = restartCounter;
        }

        public BobAckMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.restartCounter = receivedMessage.getInputs()[0].decodeLong();
        }

        @Override
        public int getProtocolMessageId() {
            return BOB_ACK_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(restartCounter),
            };
        }
    }

    // endregion


    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{AliceSendEphemeralKeyStep.class, BobSendEphemeralKeyAndK1Step.class};
            case ALICE_WAITING_FOR_K1_STATE_ID:
                return new Class[]{AliceRecoverK1AndSendK2Step.class, AliceResendEphemeralKeyStep.class};
            case BOB_WAITING_FOR_K2_STATE_ID:
                return new Class[]{BobRecoverK2ToUpdateReceiveSeedAndSendAckStep.class, BobSendEphemeralKeyAndK1Step.class};
            case ALICE_WAITING_FOR_ACK_STATE_ID:
                return new Class[]{AliceUpdateSendSeedStep.class, AliceResendEphemeralKeyStep.class};
            case FULL_RATCHET_DONE_STATE_ID:
            default:
                return new Class[0];
        }
    }


    public static class AliceSendEphemeralKeyStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public AliceSendEphemeralKeyStep(InitialProtocolState startState, InitialMessage receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // generate a random 5-byte nonce as the heavy weight bits of the restart counter
            //   --> this prevents reusing a message from an old run of the protocol in a newer run (this is required because the prtocolUid is deterministic)
            byte[] bytes = getPrng().bytes(5);
            long restartCounter = 0;
            for (int i=0; i<5; i++) {
                restartCounter = restartCounter << 8;
                restartCounter += bytes[i] & 0xff;
            }
            restartCounter = restartCounter << 23; // the MSb is 0, 40 bits of nonce, 23 bits for the actual restartCounter

            KeyPair keyPair = Suite.generateEncryptionKeyPair(getOwnedIdentity().getEncryptionPublicKey().getAlgorithmImplementation(), getPrng());
            if (keyPair == null) {
                throw new Exception();
            }

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[]{receivedMessage.contactDeviceUid}, true), getProtocolId(), getProtocolInstanceUid(), true, false);
            ChannelMessageToSend messageToSend = new AliceEphemeralKeyMessage(coreProtocolMessage, (EncryptionPublicKey) keyPair.getPublicKey(), restartCounter).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new AliceWaitingForK1State(receivedMessage.contactIdentity, receivedMessage.contactDeviceUid, (EncryptionPrivateKey) keyPair.getPrivateKey(), restartCounter);
        }
    }


    public static class AliceResendEphemeralKeyStep extends ProtocolStep {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;
        private final long previousRestartCounter;
        private final InitialMessage receivedMessage;

        public AliceResendEphemeralKeyStep(AliceWaitingForK1State startState, InitialMessage receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.contactIdentity = startState.contactIdentity;
            this.contactDeviceUid = startState.contactDeviceUid;
            this.previousRestartCounter = startState.restartCounter;
            this.receivedMessage = receivedMessage;
        }

        public AliceResendEphemeralKeyStep(AliceWaitingForAckState startState, InitialMessage receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.contactIdentity = startState.contactIdentity;
            this.contactDeviceUid = startState.contactDeviceUid;
            this.previousRestartCounter = startState.restartCounter;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();
            if (!receivedMessage.contactDeviceUid.equals(contactDeviceUid) ||
                    !receivedMessage.contactIdentity.equals(contactIdentity)) {
                throw new Exception();
            }

            long restartCounter = previousRestartCounter + 1;

            KeyPair keyPair = Suite.generateEncryptionKeyPair(getOwnedIdentity().getEncryptionPublicKey().getAlgorithmImplementation(), getPrng());
            if (keyPair == null) {
                throw new Exception();
            }

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[]{receivedMessage.contactDeviceUid}, true), getProtocolId(), getProtocolInstanceUid(), true, false);
            ChannelMessageToSend messageToSend = new AliceEphemeralKeyMessage(coreProtocolMessage, (EncryptionPublicKey) keyPair.getPublicKey(), restartCounter).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new AliceWaitingForK1State(receivedMessage.contactIdentity, receivedMessage.contactDeviceUid, (EncryptionPrivateKey) keyPair.getPrivateKey(), restartCounter);
        }
    }


    public static class BobSendEphemeralKeyAndK1Step extends ProtocolStep {
        private final BobWaitingForK2State previousState;
        private final AliceEphemeralKeyMessage receivedMessage;

        public BobSendEphemeralKeyAndK1Step(InitialProtocolState startState, AliceEphemeralKeyMessage receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.previousState = null;
            this.receivedMessage = receivedMessage;
        }

        public BobSendEphemeralKeyAndK1Step(BobWaitingForK2State startState, AliceEphemeralKeyMessage receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.contactDeviceUid, startState.contactIdentity), receivedMessage, protocol);
            this.previousState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (previousState != null) {
                if ((receivedMessage.restartCounter >> 23) == (previousState.restartCounter >> 23) // nonce part of the restart counter are the same
                        && (receivedMessage.restartCounter <= previousState.restartCounter)) {     // counter is smaller --> this is an old message of the same run --> ignore it
                    return previousState;
                }
            }

            UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

            if (!getProtocolInstanceUid().equals(computeProtocolUid(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid(), currentDeviceUid))) {
                // the protocolInstanceUid does not match what it should be --> Abort !
                return new CancelledState();
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

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), getOwnedIdentity(), new UID[]{receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid()}, true));
            ChannelMessageToSend messageToSend = new BobEphemeralKeyAndK1Message(coreProtocolMessage, (EncryptionPublicKey) keyPair.getPublicKey(), c1, receivedMessage.restartCounter).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new BobWaitingForK2State(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid(), (EncryptionPrivateKey) keyPair.getPrivateKey(), k1, receivedMessage.restartCounter);
        }
    }


    public static class AliceRecoverK1AndSendK2Step extends ProtocolStep {
        private final AliceWaitingForK1State startState;
        private final BobEphemeralKeyAndK1Message receivedMessage;

        public AliceRecoverK1AndSendK2Step(AliceWaitingForK1State startState, BobEphemeralKeyAndK1Message receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.contactDeviceUid, startState.contactIdentity), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // verify that the restartCounter matches or ignore the message
            if (receivedMessage.restartCounter != startState.restartCounter) {
                return startState;
            }

            // recover k1
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


            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(startState.contactIdentity, getOwnedIdentity(), new UID[]{startState.contactDeviceUid}, true), getProtocolId(), getProtocolInstanceUid(), true, false);
            ChannelMessageToSend messageToSend = new AliceK2Message(coreProtocolMessage, c2, startState.restartCounter).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new AliceWaitingForAckState(startState.contactIdentity, startState.contactDeviceUid, seed, startState.restartCounter);
        }
    }


    public static class BobRecoverK2ToUpdateReceiveSeedAndSendAckStep extends ProtocolStep {
        private final BobWaitingForK2State startState;
        private final AliceK2Message receivedMessage;

        public BobRecoverK2ToUpdateReceiveSeedAndSendAckStep(BobWaitingForK2State startState, AliceK2Message receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.contactDeviceUid, startState.contactIdentity), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // verify that the restartCounter matches or ignore the message
            if (receivedMessage.restartCounter != startState.restartCounter) {
                return startState;
            }

            // recover k2
            PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(startState.ephemeralPrivateKey);
            AuthEncKey k2 = publicKeyEncryption.kemDecrypt(startState.ephemeralPrivateKey, receivedMessage.c2);
            if (k2 == null) {
                Logger.e("Could not recover k2.");
                return new CancelledState();
            }

            Seed seed = Seed.of(startState.k1, k2);

            protocolManagerSession.channelDelegate.updateObliviousChannelReceiveSeed(protocolManagerSession.session, getOwnedIdentity(), startState.contactDeviceUid, startState.contactIdentity, seed, 0);

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(startState.contactIdentity, getOwnedIdentity(), new UID[]{startState.contactDeviceUid}, true));
            ChannelMessageToSend messageToSend = new BobAckMessage(coreProtocolMessage, startState.restartCounter).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new FullRatchetDoneState();
        }
    }


    public static class AliceUpdateSendSeedStep extends ProtocolStep {
        private final AliceWaitingForAckState startState;
        private final BobAckMessage receivedMessage;

        public AliceUpdateSendSeedStep(AliceWaitingForAckState startState, BobAckMessage receivedMessage, FullRatchetProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createObliviousChannelInfo(startState.contactDeviceUid, startState.contactIdentity), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // verify that the restartCounter matches or ignore the message
            if (receivedMessage.restartCounter != startState.restartCounter) {
                return startState;
            }

            protocolManagerSession.channelDelegate.updateObliviousChannelSendSeed(protocolManagerSession.session, getOwnedIdentity(), startState.contactDeviceUid, startState.contactIdentity, startState.seed, 0);

            return new FullRatchetDoneState();
        }
    }



    // endregion
}
