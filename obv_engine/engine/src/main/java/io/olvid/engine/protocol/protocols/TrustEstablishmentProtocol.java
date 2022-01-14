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

import java.util.Arrays;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Commitment;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.SAS;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.OneWayDialogProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class TrustEstablishmentProtocol extends ConcreteProtocol {
    public TrustEstablishmentProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return TRUST_ESTABLISHMENT_PROTOCOL_ID;
    }







    // region States

    // Alice's side
    static final int WAITING_FOR_SEED_STATE_ID = 1;
    // Bob's side
    static final int WAITING_FOR_CONFIRMATION_STATE_ID = 2;
    static final int CANCELLED_STATE_ID = 3;
    static final int WAITING_FOR_DECOMMITMENT_STATE_ID = 4;
    // Alice and Bob's side
    static final int WAITING_FOR_USER_SAS_STATE_ID = 5;
    static final int CONTACT_IDENTITY_TRUSTED_STATE_ID = 6;
    static final int MUTUAL_TRUST_CONFIRMED_STATE_ID = 7;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{CANCELLED_STATE_ID, MUTUAL_TRUST_CONFIRMED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case WAITING_FOR_SEED_STATE_ID:
                return WaitingForSeedState.class;
            case WAITING_FOR_CONFIRMATION_STATE_ID:
                return WaitingForConfirmationState.class;
            case CANCELLED_STATE_ID:
                return CancelledState.class;
            case WAITING_FOR_DECOMMITMENT_STATE_ID:
                return WaitingForDecommitmentState.class;
            case WAITING_FOR_USER_SAS_STATE_ID:
                return WaitingForUserSasState.class;
            case CONTACT_IDENTITY_TRUSTED_STATE_ID:
                return ContactIdentityTrustedState.class;
            case MUTUAL_TRUST_CONFIRMED_STATE_ID:
                return MutualTrustConfirmedState.class;
            default:
                return null;
        }
    }

    public static class WaitingForSeedState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactDisplayName;
        private final byte[] decommitment;
        private final Seed seedForSas;
        private final UUID dialogUuid;

        public WaitingForSeedState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_SEED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 5) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactDisplayName = list[1].decodeString();
            this.decommitment = list[2].decodeBytes();
            this.seedForSas = list[3].decodeSeed();
            this.dialogUuid = list[4].decodeUuid();
        }

        WaitingForSeedState(Identity contactIdentity, String contactDisplayName, byte[] decommitment, Seed seedForSas, UUID dialogUuid) {
            super(WAITING_FOR_SEED_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactDisplayName = contactDisplayName;
            this.decommitment = decommitment;
            this.seedForSas = seedForSas;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDisplayName),
                    Encoded.of(decommitment),
                    Encoded.of(seedForSas),
                    Encoded.of(dialogUuid),
            });
        }
    }


    public static class WaitingForConfirmationState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final UID[] contactDeviceUids;
        private final byte[] commitment;
        private final UUID dialogUuid;

        public WaitingForConfirmationState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_CONFIRMATION_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 5) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactSerializedDetails = list[1].decodeString();
            this.contactDeviceUids = list[2].decodeUidArray();
            this.commitment = list[3].decodeBytes();
            this.dialogUuid = list[4].decodeUuid();
        }

        WaitingForConfirmationState(Identity contactIdentity, String contactSerializedDetails, UID[] contactDeviceUids, byte[] commitment, UUID dialogUuid) {
            super(WAITING_FOR_CONFIRMATION_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.contactDeviceUids = contactDeviceUids;
            this.commitment = commitment;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(commitment),
                    Encoded.of(dialogUuid)
            });
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

    public static class WaitingForDecommitmentState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final UID[] contactDeviceUids;
        private final byte[] commitment;
        private final Seed seedForSas;
        private final UUID dialogUuid;

        public WaitingForDecommitmentState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_DECOMMITMENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 6) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactSerializedDetails = list[1].decodeString();
            this.contactDeviceUids = list[2].decodeUidArray();
            this.commitment = list[3].decodeBytes();
            this.seedForSas = list[4].decodeSeed();
            this.dialogUuid = list[5].decodeUuid();
        }

        WaitingForDecommitmentState(Identity contactIdentity, String contactSerializedDetails, UID[] contactDeviceUids, byte[] commitment, Seed seedForSas, UUID dialogUuid) {
            super(WAITING_FOR_DECOMMITMENT_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.contactDeviceUids = contactDeviceUids;
            this.commitment = commitment;
            this.seedForSas = seedForSas;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(commitment),
                    Encoded.of(seedForSas),
                    Encoded.of(dialogUuid),
            });
        }
    }

    public static class WaitingForUserSasState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final UID[] contactDeviceUids;
        private final Seed seedForSas;
        private final Seed contactSeedForSas;
        private final UUID dialogUuid;

        public WaitingForUserSasState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_USER_SAS_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 6) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactSerializedDetails = list[1].decodeString();
            this.contactDeviceUids = list[2].decodeUidArray();
            this.seedForSas = list[3].decodeSeed();
            this.contactSeedForSas = list[4].decodeSeed();
            this.dialogUuid = list[5].decodeUuid();
        }

        WaitingForUserSasState(Identity contactIdentity, String contactSerializedDetails, UID[] contactDeviceUids, Seed seedForSas, Seed contactSeedForSas, UUID dialogUuid) {
            super(WAITING_FOR_USER_SAS_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.contactDeviceUids = contactDeviceUids;
            this.seedForSas = seedForSas;
            this.contactSeedForSas = contactSeedForSas;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(seedForSas),
                    Encoded.of(contactSeedForSas),
                    Encoded.of(dialogUuid)
            });
        }
    }

    public static class ContactIdentityTrustedState extends ConcreteProtocolState {
        private final String contactSerializedDetails;
        private final Identity contactIdentity;
        private final UUID dialogUuid;

        public ContactIdentityTrustedState(Encoded encodedState) throws Exception {
            super(CONTACT_IDENTITY_TRUSTED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 3) {
                throw new Exception();
            }
            this.contactSerializedDetails = list[0].decodeString();
            this.contactIdentity = list[1].decodeIdentity();
            this.dialogUuid = list[2].decodeUuid();
        }

        ContactIdentityTrustedState(String contactSerializedDetails, Identity contactIdentity, UUID dialogUuid) {
            super(CONTACT_IDENTITY_TRUSTED_STATE_ID);
            this.contactSerializedDetails = contactSerializedDetails;
            this.contactIdentity = contactIdentity;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(contactIdentity),
                    Encoded.of(dialogUuid),
            });
        }
    }

    public static class MutualTrustConfirmedState extends ConcreteProtocolState {
        public MutualTrustConfirmedState(Encoded encodedState) throws Exception {
            super(MUTUAL_TRUST_CONFIRMED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        MutualTrustConfirmedState() {
            super(MUTUAL_TRUST_CONFIRMED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion






    // region Messages

    static final int INITIAL_MESSAGE_ID = 0;
    static final int SEND_COMMITMENT_MESSAGE_ID = 1;
    static final int PROPAGATE_INVITATION_TO_ALICE_DEVICES_MESSAGE_ID = 2;
    static final int PROPAGATE_COMMITMENT_TO_BOB_DEVICES_MESSAGE_ID = 4;
    static final int BOB_DIALOG_INVITATION_CONFIRMATION_MESSAGE_ID = 5;
    static final int PROPAGATE_CONFIRMATION_TO_BOB_DEVICES_MESSAGE_ID = 6;
    static final int SEND_BOB_SEED_MESSAGE_ID = 8;
    static final int SEND_DECOMMITMENT_MESSAGE_ID = 9;
    static final int DIALOG_FOR_SAS_EXCHANGE_MESSAGE_ID = 10;
    static final int PROPAGATE_ENTERED_SAS_TO_OTHER_DEVICES_MESSAGE_ID = 12;
    static final int MUTUAL_TRUST_CONFIRMATION_MESSAGE_ID = 13;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case SEND_COMMITMENT_MESSAGE_ID:
                return SendCommitmentMessage.class;
            case PROPAGATE_INVITATION_TO_ALICE_DEVICES_MESSAGE_ID:
                return PropagateInvitationToAliceDevicesMessage.class;
            case PROPAGATE_COMMITMENT_TO_BOB_DEVICES_MESSAGE_ID:
                return PropagateCommitmentToBobDevicesMessage.class;
            case BOB_DIALOG_INVITATION_CONFIRMATION_MESSAGE_ID:
                return BobDialogInvitationConfirmationMessage.class;
            case PROPAGATE_CONFIRMATION_TO_BOB_DEVICES_MESSAGE_ID:
                return PropagateConfirmationToBobDevicesMessage.class;
            case SEND_BOB_SEED_MESSAGE_ID:
                return SendBobSeedMessage.class;
            case SEND_DECOMMITMENT_MESSAGE_ID:
                return SendDecommitmentMessage.class;
            case DIALOG_FOR_SAS_EXCHANGE_MESSAGE_ID:
                return DialogForSasExchangeMessage.class;
            case PROPAGATE_ENTERED_SAS_TO_OTHER_DEVICES_MESSAGE_ID:
                return PropagateEnteredSasToOtherDevicesMessage.class;
            case MUTUAL_TRUST_CONFIRMATION_MESSAGE_ID:
                return MutualTrustConfirmationMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String contactDisplayName;
        private final String ownSerializedDetails;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String contactDisplayName, String ownSerializedDetails) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactDisplayName = contactDisplayName;
            this.ownSerializedDetails = ownSerializedDetails;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactDisplayName = receivedMessage.getInputs()[1].decodeString();
            this.ownSerializedDetails = receivedMessage.getInputs()[2].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDisplayName),
                    Encoded.of(ownSerializedDetails)
            };
        }
    }

    public static class SendCommitmentMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final UID[] contactDeviceUids;
        private final byte[] commitment;

        SendCommitmentMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String contactSerializedDetails, UID[] contactDeviceUids, byte[] commitment) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.contactDeviceUids = contactDeviceUids;
            this.commitment = commitment;
        }

        public SendCommitmentMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactSerializedDetails = receivedMessage.getInputs()[1].decodeString();
            this.contactDeviceUids = receivedMessage.getInputs()[2].decodeUidArray();
            this.commitment = receivedMessage.getInputs()[3].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return SEND_COMMITMENT_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(commitment),
            };
        }
    }

    public static class PropagateInvitationToAliceDevicesMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String contactDisplayName;
        private final byte[] decommitment;
        private final Seed seedForSas;

        PropagateInvitationToAliceDevicesMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String contactDisplayName, byte[] decommitment, Seed seedForSas) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactDisplayName = contactDisplayName;
            this.decommitment = decommitment;
            this.seedForSas = seedForSas;
        }

        public PropagateInvitationToAliceDevicesMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactDisplayName = receivedMessage.getInputs()[1].decodeString();
            this.decommitment = receivedMessage.getInputs()[2].decodeBytes();
            this.seedForSas = receivedMessage.getInputs()[3].decodeSeed();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_INVITATION_TO_ALICE_DEVICES_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDisplayName),
                    Encoded.of(decommitment),
                    Encoded.of(seedForSas),
            };
        }
    }


    public static class PropagateCommitmentToBobDevicesMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String contactDisplayName;
        private final UID[] contactDeviceUids;
        private final byte[] commitment;

        PropagateCommitmentToBobDevicesMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String contactDisplayName, UID[] contactDeviceUids, byte[] commitment) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactDisplayName = contactDisplayName;
            this.contactDeviceUids = contactDeviceUids;
            this.commitment = commitment;
        }

        public PropagateCommitmentToBobDevicesMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactDisplayName = receivedMessage.getInputs()[1].decodeString();
            this.contactDeviceUids = receivedMessage.getInputs()[2].decodeUidArray();
            this.commitment = receivedMessage.getInputs()[3].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_COMMITMENT_TO_BOB_DEVICES_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDisplayName),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(commitment),
            };
        }
    }


    public static class BobDialogInvitationConfirmationMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;
        private final UUID dialogUuid;

        BobDialogInvitationConfirmationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            invitationAccepted = false;
            dialogUuid = null;
        }

        public BobDialogInvitationConfirmationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            invitationAccepted = receivedMessage.getEncodedResponse().decodeBoolean();
            dialogUuid = receivedMessage.getUserDialogUuid();
        }

        @Override
        public int getProtocolMessageId() {
            return BOB_DIALOG_INVITATION_CONFIRMATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class PropagateConfirmationToBobDevicesMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;

        PropagateConfirmationToBobDevicesMessage(CoreProtocolMessage coreProtocolMessage, boolean invitationAccepted) {
            super(coreProtocolMessage);
            this.invitationAccepted = invitationAccepted;
        }

        public PropagateConfirmationToBobDevicesMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.invitationAccepted = receivedMessage.getInputs()[0].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_CONFIRMATION_TO_BOB_DEVICES_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(invitationAccepted),
            };
        }
    }


    public static class SendBobSeedMessage extends ConcreteProtocolMessage {
        private final Seed contactSeedForSas;
        private final UID[] contactDeviceUids;
        private final String contactSerializedDetails;

        SendBobSeedMessage(CoreProtocolMessage coreProtocolMessage, Seed contactSeedForSas, UID[] contactDeviceUids, String contactSerializedDetails) {
            super(coreProtocolMessage);
            this.contactSeedForSas = contactSeedForSas;
            this.contactDeviceUids = contactDeviceUids;
            this.contactSerializedDetails = contactSerializedDetails;
        }

        public SendBobSeedMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            this.contactSeedForSas = receivedMessage.getInputs()[0].decodeSeed();
            this.contactDeviceUids = receivedMessage.getInputs()[1].decodeUidArray();
            this.contactSerializedDetails = receivedMessage.getInputs()[2].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return SEND_BOB_SEED_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactSeedForSas),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(contactSerializedDetails),
            };
        }
    }


    public static class SendDecommitmentMessage extends ConcreteProtocolMessage {
        private final byte[] decommitment;

        SendDecommitmentMessage(CoreProtocolMessage coreProtocolMessage, byte[] decommitment) {
            super(coreProtocolMessage);
            this.decommitment = decommitment;
        }

        public SendDecommitmentMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.decommitment = receivedMessage.getInputs()[0].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return SEND_DECOMMITMENT_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(decommitment),
            };
        }
    }


    public static class DialogForSasExchangeMessage extends ConcreteProtocolMessage {
        private final byte[] sasEnteredByUser; // Only set when the message is sent to this protocol, not when sending this message to the UI
        private final UUID userDialogUuid;

        DialogForSasExchangeMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            this.sasEnteredByUser = null;
            this.userDialogUuid = null;
        }

        public DialogForSasExchangeMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            sasEnteredByUser = receivedMessage.getEncodedResponse().decodeBytes();
            userDialogUuid = receivedMessage.getUserDialogUuid();
        }

        @Override
        public int getProtocolMessageId() {
            return DIALOG_FOR_SAS_EXCHANGE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class PropagateEnteredSasToOtherDevicesMessage extends ConcreteProtocolMessage {
        private final byte[] sasEnteredByUser;

        PropagateEnteredSasToOtherDevicesMessage(CoreProtocolMessage coreProtocolMessage, byte[] sasEnteredByUser) {
            super(coreProtocolMessage);
            this.sasEnteredByUser = sasEnteredByUser;
        }

        public PropagateEnteredSasToOtherDevicesMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            sasEnteredByUser = receivedMessage.getInputs()[0].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_ENTERED_SAS_TO_OTHER_DEVICES_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(sasEnteredByUser),
            };
        }
    }


    public static class MutualTrustConfirmationMessage extends ConcreteProtocolMessage {
        MutualTrustConfirmationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        public MutualTrustConfirmationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return MUTUAL_TRUST_CONFIRMATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    // endregion








    // region Steps

    @Override
    public Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{
                        SendCommitmentStep.class,
                        StoreDecommitmentStep.class,
                        StoreAndPropagateCommitmentAndAskForConfirmationStep.class,
                        StoreCommitmentAndAskForConfirmationStep.class,
                };
            case WAITING_FOR_CONFIRMATION_STATE_ID:
                return new Class[]{
                        SendSeedAndPropagateConfirmationStep.class,
                        ReceivedConfirmationFromOtherDeviceStep.class,
                };
            case WAITING_FOR_SEED_STATE_ID:
                return new Class[]{ShowSasDialogAndSendDecommitmentStep.class};
            case WAITING_FOR_DECOMMITMENT_STATE_ID:
                return new Class[]{ShowSasDialogStep.class};
            case WAITING_FOR_USER_SAS_STATE_ID:
                return new Class[]{
                        CheckPropagatedSasAndAddTrustStep.class,
                        CheckSasAndAddTrustStep.class,
                };
            case CONTACT_IDENTITY_TRUSTED_STATE_ID:
                return new Class[]{NotifiedMutualTrustEstablishedStep.class};
            case CANCELLED_STATE_ID:
            case MUTUAL_TRUST_CONFIRMED_STATE_ID:
            default:
                return new Class[0];
        }
    }


    public static class SendCommitmentStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public SendCommitmentStep(InitialProtocolState startState, InitialMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UID[] ownedDeviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            UUID dialogUuid = UUID.randomUUID();

            Seed seedForSas = new Seed(getPrng());
            Commitment commitmentScheme = Suite.getDefaultCommitment(0);
            Commitment.CommitmentOutput commitmentOutput = commitmentScheme.commit(
                    getOwnedIdentity().getBytes(),
                    seedForSas.getBytes(),
                    getPrng()
            );

            {
                // Display invite sent dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createInviteSentDialog(receivedMessage.contactDisplayName, receivedMessage.contactIdentity), dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // Propagate invitation to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateInvitationToAliceDevicesMessage(coreProtocolMessage, receivedMessage.contactIdentity, receivedMessage.contactDisplayName, commitmentOutput.decommitment, seedForSas).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // Broadcast commitment to Bob
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new SendCommitmentMessage(coreProtocolMessage, getOwnedIdentity(), receivedMessage.ownSerializedDetails, ownedDeviceUids, commitmentOutput.commitment).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new WaitingForSeedState(
                    receivedMessage.contactIdentity,
                    receivedMessage.contactDisplayName,
                    commitmentOutput.decommitment,
                    seedForSas,
                    dialogUuid
            );
        }
    }


    public static class StoreDecommitmentStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final PropagateInvitationToAliceDevicesMessage receivedMessage;

        public StoreDecommitmentStep(InitialProtocolState startState, PropagateInvitationToAliceDevicesMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UUID dialogUuid = UUID.randomUUID();

            {
                // Display invite sent dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createInviteSentDialog(receivedMessage.contactDisplayName, receivedMessage.contactIdentity), dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new WaitingForSeedState(
                    receivedMessage.contactIdentity,
                    receivedMessage.contactDisplayName,
                    receivedMessage.decommitment,
                    receivedMessage.seedForSas,
                    dialogUuid
            );
        }
    }


    public static class StoreAndPropagateCommitmentAndAskForConfirmationStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final SendCommitmentMessage receivedMessage;

        public StoreAndPropagateCommitmentAndAskForConfirmationStep(InitialProtocolState startState, SendCommitmentMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UUID dialogUuid = UUID.randomUUID();

            {
                // Display invite received dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createAcceptInviteDialog(receivedMessage.contactSerializedDetails, receivedMessage.contactIdentity, receivedMessage.getServerTimestamp()), dialogUuid));
                ChannelMessageToSend messageToSend = new BobDialogInvitationConfirmationMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // Propagate invitation to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateCommitmentToBobDevicesMessage(coreProtocolMessage, receivedMessage.contactIdentity, receivedMessage.contactSerializedDetails, receivedMessage.contactDeviceUids, receivedMessage.commitment).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            return new WaitingForConfirmationState(
                    receivedMessage.contactIdentity,
                    receivedMessage.contactSerializedDetails,
                    receivedMessage.contactDeviceUids,
                    receivedMessage.commitment,
                    dialogUuid
            );
        }
    }


    public static class StoreCommitmentAndAskForConfirmationStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final PropagateCommitmentToBobDevicesMessage receivedMessage;

        public StoreCommitmentAndAskForConfirmationStep(InitialProtocolState startState, PropagateCommitmentToBobDevicesMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UUID dialogUuid = UUID.randomUUID();
            {
                // Display invite received dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createAcceptInviteDialog(receivedMessage.contactDisplayName, receivedMessage.contactIdentity, receivedMessage.getServerTimestamp()), dialogUuid));
                ChannelMessageToSend messageToSend = new BobDialogInvitationConfirmationMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new WaitingForConfirmationState(
                    receivedMessage.contactIdentity,
                    receivedMessage.contactDisplayName,
                    receivedMessage.contactDeviceUids,
                    receivedMessage.commitment,
                    dialogUuid
            );
        }
    }


    public static class SendSeedAndPropagateConfirmationStep extends ProtocolStep {
        private final WaitingForConfirmationState startState;
        private final BobDialogInvitationConfirmationMessage receivedMessage;

        public SendSeedAndPropagateConfirmationStep(WaitingForConfirmationState startState, BobDialogInvitationConfirmationMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!startState.dialogUuid.equals(receivedMessage.dialogUuid)) {
                Logger.e("ObvDialog uuid mismatch in BobDialogInvitationConfirmationMessage.");
                return null;
            }

            {
                // Propagate the accept/reject to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateConfirmationToBobDevicesMessage(coreProtocolMessage, receivedMessage.invitationAccepted).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            // if invitation was rejected, Cancel
            if (!receivedMessage.invitationAccepted) {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new CancelledState();
            }

            {
                // Display invitation accepted dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createInviteAcceptedDialog(startState.contactSerializedDetails, startState.contactIdentity), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            Seed seedForSas = protocolManagerSession.identityDelegate.getDeterministicSeedForOwnedIdentity(getOwnedIdentity(), startState.commitment);
            UID[] ownedDeviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            String ownSerializedDetails = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            {
                // send the seed to Alice
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), startState.contactDeviceUids));
                ChannelMessageToSend messageToSend = new SendBobSeedMessage(coreProtocolMessage, seedForSas, ownedDeviceUids, ownSerializedDetails).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new WaitingForDecommitmentState(
                    startState.contactIdentity,
                    startState.contactSerializedDetails,
                    startState.contactDeviceUids,
                    startState.commitment,
                    seedForSas,
                    startState.dialogUuid
            );
        }
    }

    public static class ReceivedConfirmationFromOtherDeviceStep extends ProtocolStep {
        private final WaitingForConfirmationState startState;
        private final PropagateConfirmationToBobDevicesMessage receivedMessage;

        public ReceivedConfirmationFromOtherDeviceStep(WaitingForConfirmationState startState, PropagateConfirmationToBobDevicesMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // if invitation was rejected, Cancel
            if (!receivedMessage.invitationAccepted) {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new CancelledState();
            }

            {
                // Display invitation accepted dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createInviteAcceptedDialog(startState.contactSerializedDetails, startState.contactIdentity), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            Seed seedForSas = protocolManagerSession.identityDelegate.getDeterministicSeedForOwnedIdentity(getOwnedIdentity(), startState.commitment);

            return new WaitingForDecommitmentState(
                    startState.contactIdentity,
                    startState.contactSerializedDetails,
                    startState.contactDeviceUids,
                    startState.commitment,
                    seedForSas,
                    startState.dialogUuid
            );
        }
    }



    public static class ShowSasDialogAndSendDecommitmentStep extends ProtocolStep {
        private final WaitingForSeedState startState;
        private final SendBobSeedMessage receivedMessage;

        public ShowSasDialogAndSendDecommitmentStep(WaitingForSeedState startState, SendBobSeedMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // send decommitment to Bob's devices
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), receivedMessage.contactDeviceUids));
                ChannelMessageToSend messageToSend = new SendDecommitmentMessage(coreProtocolMessage, startState.decommitment).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            byte[] sasToDisplay = SAS.compute(startState.seedForSas, receivedMessage.contactSeedForSas, Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);
            {
                // display sas exchange dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createSasExchangeDialog(receivedMessage.contactSerializedDetails, startState.contactIdentity, sasToDisplay, receivedMessage.getServerTimestamp()), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new DialogForSasExchangeMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new WaitingForUserSasState(
                    startState.contactIdentity,
                    receivedMessage.contactSerializedDetails,
                    receivedMessage.contactDeviceUids,
                    startState.seedForSas,
                    receivedMessage.contactSeedForSas,
                    startState.dialogUuid);
        }
    }


    public static class ShowSasDialogStep extends ProtocolStep {
        private final WaitingForDecommitmentState startState;
        private final SendDecommitmentMessage receivedMessage;

        public ShowSasDialogStep(WaitingForDecommitmentState startState, SendDecommitmentMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            Commitment commitmentScheme = Suite.getDefaultCommitment(0);
            byte[] opened = commitmentScheme.open(startState.contactIdentity.getBytes(), startState.commitment, receivedMessage.decommitment);
            if (opened == null) {
                Logger.e("Unable to open commitment.");
                return null;
            }
            Seed contactSeedForSas = new Seed(opened);
            byte[] sasToDisplay = SAS.compute(startState.seedForSas, contactSeedForSas, Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);

            {
                // display sas exchange dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createSasExchangeDialog(startState.contactSerializedDetails, startState.contactIdentity, sasToDisplay, receivedMessage.getServerTimestamp()), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new DialogForSasExchangeMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new WaitingForUserSasState(
                    startState.contactIdentity,
                    startState.contactSerializedDetails,
                    startState.contactDeviceUids,
                    startState.seedForSas,
                    contactSeedForSas,
                    startState.dialogUuid);
        }
    }


    public static class CheckSasAndAddTrustStep extends ProtocolStep {
        private final WaitingForUserSasState startState;
        private final DialogForSasExchangeMessage receivedMessage;

        public CheckSasAndAddTrustStep(WaitingForUserSasState startState, DialogForSasExchangeMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!startState.dialogUuid.equals(receivedMessage.userDialogUuid)) {
                Logger.e("ObvDialog uuid mismatch in DialogForSasExchangeMessage.");
                return null;
            }

            byte[] sasToDisplay = SAS.compute(startState.seedForSas, startState.contactSeedForSas, Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);
            byte[] computedSas = SAS.compute(startState.contactSeedForSas, startState.seedForSas, Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);
            if (! Arrays.equals(computedSas, receivedMessage.sasEnteredByUser)) {
                Logger.d("The SAS entered by the user does not match the computed SAS.");
                // re-display the sas exchange dialog and remain in the same state
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createSasExchangeDialog(startState.contactSerializedDetails, startState.contactIdentity, sasToDisplay, receivedMessage.getServerTimestamp()), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new DialogForSasExchangeMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                return startState;
            }

            // only create the contact if it does not already exist
            if (!protocolManagerSession.identityDelegate.isIdentityAContactIdentityOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, startState.contactIdentity, startState.contactSerializedDetails, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()));
            } else {
                protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()));
            }
            for (UID contactDeviceUid: startState.contactDeviceUids) {
                protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, contactDeviceUid);
            }


            {
                // propagate the entered sas to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateEnteredSasToOtherDevicesMessage(coreProtocolMessage, receivedMessage.sasEnteredByUser).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // display the sas confirmed dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createSasConfirmedDialog(startState.contactSerializedDetails, startState.contactIdentity, sasToDisplay, receivedMessage.sasEnteredByUser), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // notify the other party's devices that they are now trusted.
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), startState.contactDeviceUids));
                ChannelMessageToSend messageToSend = new MutualTrustConfirmationMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new ContactIdentityTrustedState(startState.contactSerializedDetails, startState.contactIdentity, startState.dialogUuid);
        }
    }


    public static class CheckPropagatedSasAndAddTrustStep extends ProtocolStep {
        private final WaitingForUserSasState startState;
        private final PropagateEnteredSasToOtherDevicesMessage receivedMessage;

        public CheckPropagatedSasAndAddTrustStep(WaitingForUserSasState startState, PropagateEnteredSasToOtherDevicesMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            byte[] sasToDisplay = SAS.compute(startState.seedForSas, startState.contactSeedForSas, Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);
            byte[] computedSas = SAS.compute(startState.contactSeedForSas, startState.seedForSas, Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);
            if (! Arrays.equals(computedSas, receivedMessage.sasEnteredByUser)) {
                Logger.e("The propagated SAS does not match the computed SAS.");

                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new CancelledState();
            }

            // only create the contact if it does not already exist
            if (!protocolManagerSession.identityDelegate.isIdentityAContactIdentityOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, startState.contactIdentity, startState.contactSerializedDetails, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()));
            }  else {
                protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), TrustOrigin.createDirectTrustOrigin(System.currentTimeMillis()));
            }
            for (UID contactDeviceUid: startState.contactDeviceUids) {
                protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, contactDeviceUid);
            }

            {
                // display the sas confirmed dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createSasConfirmedDialog(startState.contactSerializedDetails, startState.contactIdentity, sasToDisplay, receivedMessage.sasEnteredByUser), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new ContactIdentityTrustedState(startState.contactSerializedDetails, startState.contactIdentity, startState.dialogUuid);
        }
    }


    public static class NotifiedMutualTrustEstablishedStep extends ProtocolStep {
        private final ContactIdentityTrustedState startState;
        private final MutualTrustConfirmationMessage receivedMessage;

        public NotifiedMutualTrustEstablishedStep(ContactIdentityTrustedState startState, MutualTrustConfirmationMessage receivedMessage, TrustEstablishmentProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // display the mutual trust established dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createMutualTrustConfirmedDialog(startState.contactSerializedDetails, startState.contactIdentity), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new MutualTrustConfirmedState();
        }
    }

    // endregion
}
