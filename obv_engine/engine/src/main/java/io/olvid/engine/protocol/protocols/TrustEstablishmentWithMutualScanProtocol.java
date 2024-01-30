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

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.notifications.ProtocolNotifications;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.MutualScanSignatureReceived;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class TrustEstablishmentWithMutualScanProtocol extends ConcreteProtocol {

    public TrustEstablishmentWithMutualScanProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return TRUST_ESTABLISHMENT_WITH_MUTUAL_SCAN_PROTOCOL_ID;
    }

    // region States

    public static final int WAITING_FOR_CONFIRMATION_STATE_ID = 1;
    public static final int FINISHED_STATE_ID = 2;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{FINISHED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case WAITING_FOR_CONFIRMATION_STATE_ID:
                return WaitingForConfirmationState.class;
            case FINISHED_STATE_ID:
                return FinishedState.class;
            default:
                return null;
        }
    }

    public static class WaitingForConfirmationState extends ConcreteProtocolState {
        private final Identity bobIdentity;

        @SuppressWarnings("unused")
        public WaitingForConfirmationState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_CONFIRMATION_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.bobIdentity = list[0].decodeIdentity();
        }

        public WaitingForConfirmationState(Identity bobIdentity) {
            super(WAITING_FOR_CONFIRMATION_STATE_ID);
            this.bobIdentity = bobIdentity;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[] {
                    Encoded.of(bobIdentity),
            });
        }
    }

    public static class FinishedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public FinishedState(Encoded encodedState) throws Exception {
            super(FINISHED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public FinishedState() {
            super(FINISHED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion

    // region Messages

    public static final int INITIAL_MESSAGE_ID = 0;
    public static final int ALICE_SENDS_SIGNATURE_TO_BOB_MESSAGE_ID = 1;
    public static final int ALICE_PROPAGATES_QR_CODE_MESSAGE_ID = 2;
    public static final int BOB_SENDS_CONFIRMATION_AND_DETAILS_TO_ALICE_MESSAGE_ID = 3;
    public static final int BOB_PROPAGATES_SIGNATURE_MESSAGE_ID = 4;


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case ALICE_SENDS_SIGNATURE_TO_BOB_MESSAGE_ID:
                return AliceSendsSignatureToBobMessage.class;
            case ALICE_PROPAGATES_QR_CODE_MESSAGE_ID:
                return AlicePropagatesQrCodeMessage.class;
            case BOB_SENDS_CONFIRMATION_AND_DETAILS_TO_ALICE_MESSAGE_ID:
                return BobSendsConfirmationAndDetailsToAliceMessage.class;
            case BOB_PROPAGATES_SIGNATURE_MESSAGE_ID:
                return BobPropagatesSignatureMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final byte[] signature;


        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, byte[] signature) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.signature = signature;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.signature = receivedMessage.getInputs()[1].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(signature),
            };
        }
    }



    public static class AliceSendsSignatureToBobMessage extends ConcreteProtocolMessage {
        private final Identity aliceIdentity;
        private final byte[] signature;
        private final String serializedAliceDetails;
        private final UID[] aliceDeviceUids;


        public AliceSendsSignatureToBobMessage(CoreProtocolMessage coreProtocolMessage, Identity aliceIdentity, byte[] signature, String serializedAliceDetails, UID[] aliceDeviceUids) {
            super(coreProtocolMessage);
            this.aliceIdentity = aliceIdentity;
            this.signature = signature;
            this.serializedAliceDetails = serializedAliceDetails;
            this.aliceDeviceUids = aliceDeviceUids;
        }

        @SuppressWarnings("unused")
        public AliceSendsSignatureToBobMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.aliceIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.signature = receivedMessage.getInputs()[1].decodeBytes();
            this.serializedAliceDetails = receivedMessage.getInputs()[2].decodeString();
            this.aliceDeviceUids = receivedMessage.getInputs()[3].decodeUidArray();
        }

        @Override
        public int getProtocolMessageId() {
            return ALICE_SENDS_SIGNATURE_TO_BOB_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(aliceIdentity),
                    Encoded.of(signature),
                    Encoded.of(serializedAliceDetails),
                    Encoded.of(aliceDeviceUids),
            };
        }
    }


    public static class AlicePropagatesQrCodeMessage extends ConcreteProtocolMessage {
        private final Identity bobIdentity;
        private final byte[] signature;


        public AlicePropagatesQrCodeMessage(CoreProtocolMessage coreProtocolMessage, Identity bobIdentity, byte[] signature) {
            super(coreProtocolMessage);
            this.bobIdentity = bobIdentity;
            this.signature = signature;
        }

        @SuppressWarnings("unused")
        public AlicePropagatesQrCodeMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.bobIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.signature = receivedMessage.getInputs()[1].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return ALICE_PROPAGATES_QR_CODE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(bobIdentity),
                    Encoded.of(signature),
            };
        }
    }

    public static class BobSendsConfirmationAndDetailsToAliceMessage extends ConcreteProtocolMessage {
        private final String serializedBobDetails;
        private final UID[] bobDeviceUids;


        public BobSendsConfirmationAndDetailsToAliceMessage(CoreProtocolMessage coreProtocolMessage, String serializedBobDetails, UID[] bobDeviceUids) {
            super(coreProtocolMessage);
            this.serializedBobDetails = serializedBobDetails;
            this.bobDeviceUids = bobDeviceUids;
        }

        @SuppressWarnings("unused")
        public BobSendsConfirmationAndDetailsToAliceMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.serializedBobDetails = receivedMessage.getInputs()[0].decodeString();
            this.bobDeviceUids = receivedMessage.getInputs()[1].decodeUidArray();
        }

        @Override
        public int getProtocolMessageId() {
            return BOB_SENDS_CONFIRMATION_AND_DETAILS_TO_ALICE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(serializedBobDetails),
                    Encoded.of(bobDeviceUids),
            };
        }
    }



    public static class BobPropagatesSignatureMessage extends ConcreteProtocolMessage {
        private final Identity aliceIdentity;
        private final byte[] signature;
        private final String serializedAliceDetails;
        private final UID[] aliceDeviceUids;

        public BobPropagatesSignatureMessage(CoreProtocolMessage coreProtocolMessage, Identity aliceIdentity, byte[] signature, String serializedAliceDetails, UID[] aliceDeviceUids) {
            super(coreProtocolMessage);
            this.aliceIdentity = aliceIdentity;
            this.signature = signature;
            this.serializedAliceDetails = serializedAliceDetails;
            this.aliceDeviceUids = aliceDeviceUids;
        }

        @SuppressWarnings("unused")
        public BobPropagatesSignatureMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.aliceIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.signature = receivedMessage.getInputs()[1].decodeBytes();
            this.serializedAliceDetails = receivedMessage.getInputs()[2].decodeString();
            this.aliceDeviceUids = receivedMessage.getInputs()[3].decodeUidArray();
        }

        @Override
        public int getProtocolMessageId() {
            return BOB_PROPAGATES_SIGNATURE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(aliceIdentity),
                    Encoded.of(signature),
                    Encoded.of(serializedAliceDetails),
                    Encoded.of(aliceDeviceUids),
            };
        }
    }

    // endregion

    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_MESSAGE_ID:
                return new Class[]{
                        AliceSendStep.class,
                        AliceHandlesPropagatedQRCodeStep.class,
                        BobAddsContactAndConfirmsStep.class,
                        BobHandlesPropagatedSignatureStep.class,
                };
            case WAITING_FOR_CONFIRMATION_STATE_ID:
                return new Class[] { AliceAddsContactStep.class };
            case FINISHED_STATE_ID:
            default:
                return new Class[0];
        }
    }



    public static class AliceSendStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public AliceSendStep(InitialProtocolState startState, InitialMessage receivedMessage, TrustEstablishmentWithMutualScanProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // verify the signature
                if (!Signature.verify(Constants.SignatureContext.MUTUAL_SCAN, new Identity[]{getOwnedIdentity(), receivedMessage.contactIdentity}, receivedMessage.contactIdentity, receivedMessage.signature)) {
                    return new FinishedState();
                }
            }

            {
                // send message to Bob
                UID[] deviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                String serializedAliceDetails = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new AliceSendsSignatureToBobMessage(coreProtocolMessage, getOwnedIdentity(), receivedMessage.signature, serializedAliceDetails, deviceUids).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // send propagate messages
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new AlicePropagatesQrCodeMessage(coreProtocolMessage, receivedMessage.contactIdentity, receivedMessage.signature).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            return new WaitingForConfirmationState(receivedMessage.contactIdentity);
        }
    }



    public static class AliceHandlesPropagatedQRCodeStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final AlicePropagatesQrCodeMessage receivedMessage;

        public AliceHandlesPropagatedQRCodeStep(InitialProtocolState startState, AlicePropagatesQrCodeMessage receivedMessage, TrustEstablishmentWithMutualScanProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // verify the signature
                if (!Signature.verify(Constants.SignatureContext.MUTUAL_SCAN, new Identity[]{getOwnedIdentity(), receivedMessage.bobIdentity}, receivedMessage.bobIdentity, receivedMessage.signature)) {
                    return new FinishedState();
                }
            }

            return new WaitingForConfirmationState(receivedMessage.bobIdentity);
        }
    }



    public static class BobAddsContactAndConfirmsStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final AliceSendsSignatureToBobMessage receivedMessage;

        public BobAddsContactAndConfirmsStep(InitialProtocolState startState, AliceSendsSignatureToBobMessage receivedMessage, TrustEstablishmentWithMutualScanProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // verify the signature
                if (!Signature.verify(Constants.SignatureContext.MUTUAL_SCAN, new Identity[]{receivedMessage.aliceIdentity, getOwnedIdentity()}, getOwnedIdentity(), receivedMessage.signature)) {
                    return new FinishedState();
                }
            }

            {
                // verify the signature is fresh
                if (MutualScanSignatureReceived.exists(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature)) {
                    return new FinishedState();
                }
            }

            {
                // store the signature
                if (MutualScanSignatureReceived.create(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature) == null) {
                    return new FinishedState();
                }
            }

            {
                // signature is valid and fresh --> create the contact (if it does not already exists)
                if (!protocolManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.aliceIdentity)) {
                    protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, receivedMessage.aliceIdentity, receivedMessage.serializedAliceDetails, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()), true);
                } else {
                    protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, receivedMessage.aliceIdentity, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()), true);
                }
                for (UID contactDeviceUid : receivedMessage.aliceDeviceUids) {
                    protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.aliceIdentity, contactDeviceUid, false);
                }
            }

            {
                // notify Alice she was added and send her our details
                UID[] deviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                String serializedBobDetails = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(receivedMessage.aliceIdentity, getOwnedIdentity(), receivedMessage.aliceDeviceUids));
                ChannelMessageToSend messageToSend = new BobSendsConfirmationAndDetailsToAliceMessage(coreProtocolMessage, serializedBobDetails, deviceUids).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }


            {
                // propagate the message to other devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new BobPropagatesSignatureMessage(coreProtocolMessage, receivedMessage.aliceIdentity, receivedMessage.signature, receivedMessage.serializedAliceDetails, receivedMessage.aliceDeviceUids).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            {
                // send a notification so the app can automatically open the contact discussion
                protocolManagerSession.session.addSessionCommitListener(() -> {
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_OWNED_IDENTITY_KEY, getOwnedIdentity());
                    userInfo.put(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_CONTACT_IDENTITY_KEY, receivedMessage.aliceIdentity);
                    userInfo.put(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_SIGNATURE_KEY, receivedMessage.signature);
                    protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED, userInfo);
                });
            }

            return new FinishedState();
        }
    }


    public static class BobHandlesPropagatedSignatureStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final BobPropagatesSignatureMessage receivedMessage;

        public BobHandlesPropagatedSignatureStep(InitialProtocolState startState, BobPropagatesSignatureMessage receivedMessage, TrustEstablishmentWithMutualScanProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // verify the signature
                if (!Signature.verify(Constants.SignatureContext.MUTUAL_SCAN, new Identity[]{receivedMessage.aliceIdentity, getOwnedIdentity()}, getOwnedIdentity(), receivedMessage.signature)) {
                    return new FinishedState();
                }
            }

            {
                // verify the signature is fresh
                if (MutualScanSignatureReceived.exists(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature)) {
                    return new FinishedState();
                }
            }

            {
                // store the signature
                if (MutualScanSignatureReceived.create(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature) == null) {
                    return new FinishedState();
                }
            }

            {
                // signature is valid and fresh --> create the contact (if it does not already exists)
                if (!protocolManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.aliceIdentity)) {
                    protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, receivedMessage.aliceIdentity, receivedMessage.serializedAliceDetails, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()), true);
                } else {
                    protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, receivedMessage.aliceIdentity, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()), true);
                }
                for (UID contactDeviceUid : receivedMessage.aliceDeviceUids) {
                    protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.aliceIdentity, contactDeviceUid, false);
                }
            }

            {
                // send a notification so the app can automatically open the contact discussion
                protocolManagerSession.session.addSessionCommitListener(() -> {
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_OWNED_IDENTITY_KEY, getOwnedIdentity());
                    userInfo.put(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_CONTACT_IDENTITY_KEY, receivedMessage.aliceIdentity);
                    userInfo.put(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_SIGNATURE_KEY, receivedMessage.signature);
                    protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED, userInfo);
                });
            }
            return new FinishedState();
        }
    }


    public static class AliceAddsContactStep extends ProtocolStep {
        private final WaitingForConfirmationState startState;
        private final BobSendsConfirmationAndDetailsToAliceMessage receivedMessage;

        public AliceAddsContactStep(WaitingForConfirmationState startState, BobSendsConfirmationAndDetailsToAliceMessage receivedMessage, TrustEstablishmentWithMutualScanProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // Bob added Alice to his contacts --> time for Alice to do the same
                if (!protocolManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.bobIdentity)) {
                    protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, startState.bobIdentity, receivedMessage.serializedBobDetails, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()), true);
                } else {
                    protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, startState.bobIdentity, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()), true);
                }
                for (UID contactDeviceUid : receivedMessage.bobDeviceUids) {
                    protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.bobIdentity, contactDeviceUid, false);
                }
            }

            return new FinishedState();
        }
    }

    // endregion
}
