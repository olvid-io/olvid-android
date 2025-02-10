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
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.ProtocolInstance;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.databases.WaitingForOneToOneContactProtocolInstance;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.OneWayDialogProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class OneToOneContactInvitationProtocol extends ConcreteProtocol {
    public OneToOneContactInvitationProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return ONE_TO_ONE_CONTACT_INVITATION_PROTOCOL_ID;
    }

    // region States

    public static final int INVITATION_SENT_STATE_ID = 1;
    public static final int INVITATION_RECEIVED_STATE_ID = 2;
    public static final int FINISHED_STATE_ID = 3;

    @Override
    protected Class<?> getStateClass(int stateId)
    {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case INVITATION_SENT_STATE_ID:
                return InvitationSentState.class;
            case INVITATION_RECEIVED_STATE_ID:
                return InvitationReceivedState.class;
            case FINISHED_STATE_ID:
                return FinishedState.class;
            default:
                return null;
        }
    }


    @Override
    public int[] getFinalStateIds() {
        return new int[] {FINISHED_STATE_ID};
    }


    public static class InvitationSentState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UUID dialogUuid;

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public InvitationSentState(Encoded encodedState) throws Exception {
            super(INVITATION_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            contactIdentity = list[0].decodeIdentity();
            dialogUuid = list[1].decodeUuid();
        }

        public InvitationSentState(Identity contactIdentity, UUID dialogUuid) {
            super(INVITATION_SENT_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[] {
                    Encoded.of(contactIdentity),
                    Encoded.of(dialogUuid),
            });
        }
    }


    public static class InvitationReceivedState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final UUID dialogUuid;

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public InvitationReceivedState(Encoded encodedState) throws Exception {
            super(INVITATION_RECEIVED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            contactIdentity = list[0].decodeIdentity();
            dialogUuid = list[1].decodeUuid();
        }

        public InvitationReceivedState(Identity contactIdentity, UUID dialogUuid) {
            super(INVITATION_RECEIVED_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[] {
                    Encoded.of(contactIdentity),
                    Encoded.of(dialogUuid),
            });
        }
    }


    public static class FinishedState extends ConcreteProtocolState {
        @SuppressWarnings({"unused", "RedundantSuppression"})
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

    private static final int INITIAL_MESSAGE_ID = 0;
    private static final int ONE_TO_ONE_INVITATION_MESSAGE_ID = 1;
    private static final int DIALOG_INVITATION_SENT_MESSAGE_ID = 2;
    private static final int PROPAGATE_ONE_TO_ONE_INVITATION_MESSAGE_ID = 3;
    private static final int DIALOG_ACCEPT_ONE_TO_ONE_INVITATION_MESSAGE_ID = 4;
    private static final int ONE_TO_ONE_RESPONSE_MESSAGE_ID = 5;
    private static final int PROPAGATE_ONE_TO_ONE_RESPONSE_MESSAGE_ID = 6;
    private static final int ABORT_MESSAGE_ID = 7;
    private static final int CONTACT_UPGRADED_TO_ONE_TO_ONE_MESSAGE_ID = 8;
    private static final int PROPAGATE_ABORT_MESSAGE_ID = 9;
    private static final int INITIATE_ONE_TO_ONE_STATUS_SYNC_WITH_ALL_CONTACTS_MESSAGE_ID = 10;
    private static final int ONE_TO_ONE_STATUS_SYNC_REQUEST_MESSAGE_ID = 11;
    private static final int INITIATE_ONE_TO_ONE_STATUS_SYNC_WITH_ONE_CONTACT_MESSAGE_ID = 12;



    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {

        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case ONE_TO_ONE_INVITATION_MESSAGE_ID:
                return OneToOneInvitationMessage.class;
            case DIALOG_INVITATION_SENT_MESSAGE_ID:
                return DialogInvitationSentMessage.class;
            case PROPAGATE_ONE_TO_ONE_INVITATION_MESSAGE_ID:
                return PropagateOneToOneInvitationMessage.class;
            case DIALOG_ACCEPT_ONE_TO_ONE_INVITATION_MESSAGE_ID:
                return DialogAcceptOneToOneInvitationMessage.class;
            case ONE_TO_ONE_RESPONSE_MESSAGE_ID:
                return OneToOneResponseMessage.class;
            case PROPAGATE_ONE_TO_ONE_RESPONSE_MESSAGE_ID:
                return PropagateOneToOneResponseMessage.class;
            case ABORT_MESSAGE_ID:
                return AbortMessage.class;
            case CONTACT_UPGRADED_TO_ONE_TO_ONE_MESSAGE_ID:
                return ContactUpgradedToOneToOneMessage.class;
            case PROPAGATE_ABORT_MESSAGE_ID:
                return PropagateAbortMessage.class;
            case INITIATE_ONE_TO_ONE_STATUS_SYNC_WITH_ALL_CONTACTS_MESSAGE_ID:
                return InitiateOneToOneStatusSyncWithAllContactsMessage.class;
            case ONE_TO_ONE_STATUS_SYNC_REQUEST_MESSAGE_ID:
                return OneToOneStatusSyncRequestMessage.class;
            case INITIATE_ONE_TO_ONE_STATUS_SYNC_WITH_ONE_CONTACT_MESSAGE_ID:
                return InitiateOneToOneStatusSyncWithOneContactMessage.class;
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

        @SuppressWarnings({"unused", "RedundantSuppression"})
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
            return new Encoded[] {
                    Encoded.of(contactIdentity)
            };
        }
    }

    public static class OneToOneInvitationMessage extends ConcreteProtocolMessage {
        public OneToOneInvitationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public OneToOneInvitationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return ONE_TO_ONE_INVITATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class DialogInvitationSentMessage extends ConcreteProtocolMessage {
        private final boolean abort;
        private final UUID dialogUuid;

        DialogInvitationSentMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            abort = false;
            dialogUuid = null;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public DialogInvitationSentMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            abort = receivedMessage.getEncodedResponse().decodeBoolean();
            dialogUuid = receivedMessage.getUserDialogUuid();
        }

        @Override
        public int getProtocolMessageId() {
            return DIALOG_INVITATION_SENT_MESSAGE_ID;
        }

        // not used for this type of message
        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class PropagateOneToOneInvitationMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;

        public PropagateOneToOneInvitationMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public PropagateOneToOneInvitationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_ONE_TO_ONE_INVITATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity)
            };
        }
    }

    public static class DialogAcceptOneToOneInvitationMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;
        private final UUID dialogUuid;

        DialogAcceptOneToOneInvitationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            invitationAccepted = false;
            dialogUuid = null;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public DialogAcceptOneToOneInvitationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            invitationAccepted = receivedMessage.getEncodedResponse().decodeBoolean();
            dialogUuid = receivedMessage.getUserDialogUuid();
        }

        @Override
        public int getProtocolMessageId() {
            return DIALOG_ACCEPT_ONE_TO_ONE_INVITATION_MESSAGE_ID;
        }

        // not used for this type of message
        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class OneToOneResponseMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;

        public OneToOneResponseMessage(CoreProtocolMessage coreProtocolMessage, boolean invitationAccepted) {
            super(coreProtocolMessage);
            this.invitationAccepted = invitationAccepted;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public OneToOneResponseMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.invitationAccepted = receivedMessage.getInputs()[0].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return ONE_TO_ONE_RESPONSE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(invitationAccepted)
            };
        }
    }


    public static class PropagateOneToOneResponseMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;

        public PropagateOneToOneResponseMessage(CoreProtocolMessage coreProtocolMessage, boolean invitationAccepted) {
            super(coreProtocolMessage);
            this.invitationAccepted = invitationAccepted;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public PropagateOneToOneResponseMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.invitationAccepted = receivedMessage.getInputs()[0].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_ONE_TO_ONE_RESPONSE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(invitationAccepted)
            };
        }
    }


    public static class AbortMessage extends ConcreteProtocolMessage {
        public AbortMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public AbortMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return ABORT_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class ContactUpgradedToOneToOneMessage extends ConcreteProtocolMessage {
        Identity trustLevelIncreasedIdentity;

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public ContactUpgradedToOneToOneMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.trustLevelIncreasedIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return CONTACT_UPGRADED_TO_ONE_TO_ONE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class PropagateAbortMessage extends ConcreteProtocolMessage {
        public PropagateAbortMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public PropagateAbortMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_ABORT_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class InitiateOneToOneStatusSyncWithAllContactsMessage extends ConcreteProtocolMessage {
        public InitiateOneToOneStatusSyncWithAllContactsMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public InitiateOneToOneStatusSyncWithAllContactsMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_ONE_TO_ONE_STATUS_SYNC_WITH_ALL_CONTACTS_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class OneToOneStatusSyncRequestMessage extends ConcreteProtocolMessage {

        private final boolean aliceConsidersBobAsOneToOne;

        public OneToOneStatusSyncRequestMessage(CoreProtocolMessage coreProtocolMessage, boolean aliceConsidersBobAsOneToOne) {
            super(coreProtocolMessage);
            this.aliceConsidersBobAsOneToOne = aliceConsidersBobAsOneToOne;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public OneToOneStatusSyncRequestMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.aliceConsidersBobAsOneToOne = receivedMessage.getInputs()[0].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return ONE_TO_ONE_STATUS_SYNC_REQUEST_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(aliceConsidersBobAsOneToOne),
            };
        }
    }

    public static class InitiateOneToOneStatusSyncWithOneContactMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;

        public InitiateOneToOneStatusSyncWithOneContactMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public InitiateOneToOneStatusSyncWithOneContactMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_ONE_TO_ONE_STATUS_SYNC_WITH_ONE_CONTACT_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
            };
        }
    }

    // endregion








    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[] { AliceInvitesBobStep.class, BobProcessesAlicesInvitationStep.class, AliceProcessesPropagatedInvitationStep.class, AliceAbortsHerInvitationToBobStep.class, AliceProcessesUnexpectedBobResponseStep.class, AliceInitiatesOneToOneStatusSyncWithAllContactsStep.class,  AliceInitiatesOneToOneStatusSyncWithOneContactStep.class, BobProcessesSyncRequestStep.class };
            case INVITATION_SENT_STATE_ID:
                return new Class[] { AliceReceivesBobsResponseStep.class, AliceAbortsHerInvitationToBobStep.class, ProcessContactUpgradedToOneToOneStep.class, AliceProcessesPropagatedAbortStep.class};
            case INVITATION_RECEIVED_STATE_ID:
                return new Class[] { BobRespondsToAlicesInvitationStep.class, BobProcessesAbortStep.class, BobProcessesPropagatedResponseStep.class, ProcessContactUpgradedToOneToOneStep.class};
            case FINISHED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class AliceInvitesBobStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public AliceInvitesBobStep(InitialProtocolState startState, InitialMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // we do not check:
            //  - whether Bob is already oneToOne --> we send the invitation anyways
            //  - whether there is a channel with Bob --> if not, protocol will be retried a few times

            UUID dialogUuid = UUID.randomUUID();
            {
                // create a dialog to allow Alice to abort the protocol (only if Bob is not one to one)
                if (!protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createOneToOneInvitationSentDialog(receivedMessage.contactIdentity), dialogUuid));
                    ChannelMessageToSend messageToSend = new DialogInvitationSentMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // send invitation to all of Bob's devices
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new OneToOneInvitationMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // create WaitingForOneToOneContactProtocolInstance
                WaitingForOneToOneContactProtocolInstance.create(
                        protocolManagerSession,
                        getProtocolInstanceUid(),
                        getOwnedIdentity(),
                        receivedMessage.contactIdentity,
                        getProtocolId(),
                        CONTACT_UPGRADED_TO_ONE_TO_ONE_MESSAGE_ID);
            }

            {
                // propagate invitation to you other owned devices (if any)
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsOrPreKeysInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateOneToOneInvitationMessage(coreProtocolMessage, receivedMessage.contactIdentity).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            return new InvitationSentState(receivedMessage.contactIdentity, dialogUuid);
        }
    }



    public static class BobProcessesAlicesInvitationStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final InitialProtocolState startState;
        private final OneToOneInvitationMessage receivedMessage;

        public BobProcessesAlicesInvitationStep(InitialProtocolState startState, OneToOneInvitationMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // first check whether the remote identity is already a oneToOne contact
                if (protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity())) {
                    // directly confirm to Alice that we accepted the invitation
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new OneToOneResponseMessage(coreProtocolMessage, true).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                    return new FinishedState();
                }
            }


            {
                // check whether there is another protocol instance where Bob invited Alice to become oneToOne
                // detect this by looking at the WaitingForOneToOneContactProtocolInstance db

                WaitingForOneToOneContactProtocolInstance[] waitingForOneToOneContactProtocolInstances = WaitingForOneToOneContactProtocolInstance.getAllForContact(protocolManagerSession, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity());
                for (WaitingForOneToOneContactProtocolInstance waitingForOneToOneContactProtocolInstance : waitingForOneToOneContactProtocolInstances) {
                    // for each WaitingForOneToOneContactProtocolInstance, check whether the corresponding protocol instance is in the INVITATION_SENT_STATE_ID
                    if (waitingForOneToOneContactProtocolInstance.getProtocolId() == getProtocolId()) {
                        ProtocolInstance protocolInstance = ProtocolInstance.get(protocolManagerSession, waitingForOneToOneContactProtocolInstance.getProtocolUid(), getOwnedIdentity());
                        if (protocolInstance != null && protocolInstance.getCurrentStateId() == INVITATION_SENT_STATE_ID) {
                            // we indeed already invited Alice --> accept the invite and mark her as oneToOne

                            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), getOwnedIdentity()));
                            ChannelMessageToSend messageToSend = new OneToOneResponseMessage(coreProtocolMessage, true).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                            protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), true);

                            return new FinishedState();
                        }
                    }
                }
            }

            /////////
            // Alice is not yet oneToOne, and we have not invited her already --> prompt Bob to accept
            /////////


            UUID dialogUuid = UUID.randomUUID();
            {
                // create the accept invitation dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createAcceptOneToOneInvitationDialog(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), receivedMessage.getServerTimestamp()), dialogUuid));
                ChannelMessageToSend messageToSend = new DialogAcceptOneToOneInvitationMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }


            {
                // create a WaitingForOneToOneContactProtocolInstance just in case
                WaitingForOneToOneContactProtocolInstance.create(
                        protocolManagerSession,
                        getProtocolInstanceUid(),
                        getOwnedIdentity(),
                        receivedMessage.getReceptionChannelInfo().getRemoteIdentity(),
                        getProtocolId(),
                        CONTACT_UPGRADED_TO_ONE_TO_ONE_MESSAGE_ID);
            }

            return new InvitationReceivedState(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), dialogUuid);
        }
    }


    public static class BobRespondsToAlicesInvitationStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final DialogAcceptOneToOneInvitationMessage receivedMessage;

        public BobRespondsToAlicesInvitationStep(InvitationReceivedState startState, DialogAcceptOneToOneInvitationMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // only send the response if Alice is still a contact
            if (protocolManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                {
                    // send response to Alice
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(startState.contactIdentity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new OneToOneResponseMessage(coreProtocolMessage, receivedMessage.invitationAccepted).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                {
                    // update Alice's oneToOne status
                    protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, receivedMessage.invitationAccepted);
                }
            }

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), receivedMessage.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // Propagate the answer to Bob's other devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsOrPreKeysInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateOneToOneResponseMessage(coreProtocolMessage, receivedMessage.invitationAccepted).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            return new FinishedState();
        }
    }


    public static class AliceReceivesBobsResponseStep extends ProtocolStep {
        private final InvitationSentState startState;
        private final OneToOneResponseMessage receivedMessage;

        public AliceReceivesBobsResponseStep(InvitationSentState startState, OneToOneResponseMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!startState.contactIdentity.equals(receivedMessage.getReceptionChannelInfo().getRemoteIdentity())) {
                Logger.e("Contact identity mismatch in AliceReceivesBobsResponseStep: ignoring message.");
                return startState;
            }

            {
                // update Bob's oneToOne status
                protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, receivedMessage.invitationAccepted);
            }

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinishedState();
        }
    }


    public static class AliceAbortsHerInvitationToBobStep extends ProtocolStep {
        private final InvitationSentState startState;
        private final DialogInvitationSentMessage receivedMessage;

        public AliceAbortsHerInvitationToBobStep(InvitationSentState startState, DialogInvitationSentMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // do nothing if the response is not an abort!
            if (!receivedMessage.abort) {
                return startState;
            }

            // only send a response if Bob is still a contact
            if (protocolManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                {
                    // send an abort message to Bob
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(startState.contactIdentity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new AbortMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), receivedMessage.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // Propagate the abort to Alice's other devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsOrPreKeysInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateAbortMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            return new FinishedState();
        }
    }


    public static class BobProcessesAbortStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final AbortMessage receivedMessage;

        public BobProcessesAbortStep(InvitationReceivedState startState, AbortMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!startState.contactIdentity.equals(receivedMessage.getReceptionChannelInfo().getRemoteIdentity())) {
                Logger.e("Contact identity mismatch in BobProcessesAbortStep: ignoring message.");
                return startState;
            }

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinishedState();
        }
    }


    public static class ProcessContactUpgradedToOneToOneStep extends ProtocolStep {
        private final ConcreteProtocolState startState;
        private final Identity contactIdentity;
        private final UUID dialogUuid;
        @SuppressWarnings("unused")
        private final ContactUpgradedToOneToOneMessage receivedMessage;

        @SuppressWarnings("unused")
        public ProcessContactUpgradedToOneToOneStep(InvitationSentState startState, ContactUpgradedToOneToOneMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.contactIdentity = startState.contactIdentity;
            this.dialogUuid = startState.dialogUuid;
            this.receivedMessage = receivedMessage;
        }

        @SuppressWarnings("unused")
        public ProcessContactUpgradedToOneToOneStep(InvitationReceivedState startState, ContactUpgradedToOneToOneMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.contactIdentity = startState.contactIdentity;
            this.dialogUuid = startState.dialogUuid;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // check that the contact is indeed oneToOne now --> otherwise do nothing
                if (!protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), contactIdentity)) {
                    return startState;
                }
            }

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinishedState();
        }
    }



    public static class AliceProcessesPropagatedInvitationStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final PropagateOneToOneInvitationMessage receivedMessage;

        public AliceProcessesPropagatedInvitationStep(InitialProtocolState startState, PropagateOneToOneInvitationMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UUID dialogUuid = UUID.randomUUID();
            {
                // create a dialog to allow Alice to abort the protocol (only if Bob is not one to one)
                if (!protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createOneToOneInvitationSentDialog(receivedMessage.contactIdentity), dialogUuid));
                    ChannelMessageToSend messageToSend = new DialogInvitationSentMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // create WaitingForOneToOneContactProtocolInstance
                WaitingForOneToOneContactProtocolInstance.create(
                        protocolManagerSession,
                        getProtocolInstanceUid(),
                        getOwnedIdentity(),
                        receivedMessage.contactIdentity,
                        getProtocolId(),
                        CONTACT_UPGRADED_TO_ONE_TO_ONE_MESSAGE_ID);
            }

            return new InvitationSentState(receivedMessage.contactIdentity, dialogUuid);
        }
    }


    public static class BobProcessesPropagatedResponseStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final PropagateOneToOneResponseMessage receivedMessage;

        public BobProcessesPropagatedResponseStep(InvitationReceivedState startState, PropagateOneToOneResponseMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // update Alice's oneToOne status
                protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, receivedMessage.invitationAccepted);
            }

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinishedState();
        }
    }


    public static class AliceProcessesPropagatedAbortStep extends ProtocolStep {
        private final InvitationSentState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final PropagateAbortMessage receivedMessage;

        public AliceProcessesPropagatedAbortStep(InvitationSentState startState, PropagateAbortMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinishedState();
        }
    }


    public static class AliceProcessesUnexpectedBobResponseStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final OneToOneResponseMessage receivedMessage;

        public AliceProcessesUnexpectedBobResponseStep(InitialProtocolState startState, OneToOneResponseMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();


            {
                // if Bob accepted the invitation, there is nothing to do: we never upgrade him for now reason, and won't tell him to downgrade.
                if (receivedMessage.invitationAccepted) {
                    return new FinishedState();
                }
            }

            // Bob sent us an invitation rejected response, we downgrade him
            {
                // mark Bob as not oneToOne
                protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), false);
            }

            {
                // start a downgrade protocol
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        ConcreteProtocol.CONTACT_MANAGEMENT_PROTOCOL_ID,
                        childProtocolInstanceUid);
                ChannelMessageToSend messageToSend = new ContactManagementProtocol.InitiateContactDowngradeMessage(coreProtocolMessage, receivedMessage.getReceptionChannelInfo().getRemoteIdentity()).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinishedState();
        }
    }

    public static class AliceInitiatesOneToOneStatusSyncWithAllContactsStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitiateOneToOneStatusSyncWithAllContactsMessage receivedMessage;

        public AliceInitiatesOneToOneStatusSyncWithAllContactsStep(InitialProtocolState startState, InitiateOneToOneStatusSyncWithAllContactsMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                Identity[] contactIdentities = protocolManagerSession.identityDelegate.getContactsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

                // send a sync message to all contacts, within a try as some contacts without channel may fail
                for (Identity contactIdentity : contactIdentities) {
                    try {
                        final boolean oneToOne;
                        if (protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), contactIdentity)) {
                            oneToOne = true;
                        } else if (protocolManagerSession.identityDelegate.isIdentityANotOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), contactIdentity)) {
                            oneToOne = false;
                        } else {
                            // if oneToOne status is unknown, do nothing
                            continue;
                        }
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(contactIdentity, getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new OneToOneStatusSyncRequestMessage(coreProtocolMessage, oneToOne).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (SQLException e) {
                        // in case of SQLException we fail --> allows to retry the step
                        return null;
                    } catch (Exception ignored) {
                        // ignore exceptions during the post operation
                    }
                }
            }

            return new FinishedState();
        }
    }

    public static class AliceInitiatesOneToOneStatusSyncWithOneContactStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitiateOneToOneStatusSyncWithOneContactMessage receivedMessage;

        public AliceInitiatesOneToOneStatusSyncWithOneContactStep(InitialProtocolState startState, InitiateOneToOneStatusSyncWithOneContactMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // send a sync message to specific contact. He should have a channel, so fail in case of Exception
                final boolean oneToOne;
                if (protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                    oneToOne = true;
                } else if (protocolManagerSession.identityDelegate.isIdentityANotOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                    oneToOne = false;
                } else {
                    // if oneToOne status is unknown, do nothing
                    return new FinishedState();
                }

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new OneToOneStatusSyncRequestMessage(coreProtocolMessage, oneToOne).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinishedState();
        }
    }

    public static class BobProcessesSyncRequestStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final OneToOneStatusSyncRequestMessage receivedMessage;

        public BobProcessesSyncRequestStep(InitialProtocolState startState, OneToOneStatusSyncRequestMessage receivedMessage, OneToOneContactInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            final boolean aliceIsOneToOne;
            if (protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity())) {
                aliceIsOneToOne = true;
            } else if (protocolManagerSession.identityDelegate.isIdentityANotOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity())) {
                aliceIsOneToOne = false;
            } else {
                // if oneToOne status is unknown, do nothing
                return new FinishedState();
            }

            if (aliceIsOneToOne != receivedMessage.aliceConsidersBobAsOneToOne) {
                if (aliceIsOneToOne) {
                    // we consider Alice as oneToOne, but she does not --> we downgrade her
                    protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), false);
                } else {
                    // Alice considers us as oneToOne, but we don't --> we check if we have a pending invitation for her

                    WaitingForOneToOneContactProtocolInstance[] waitingForOneToOneContactProtocolInstances = WaitingForOneToOneContactProtocolInstance.getAllForContact(protocolManagerSession, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity());
                    for (WaitingForOneToOneContactProtocolInstance waitingForOneToOneContactProtocolInstance : waitingForOneToOneContactProtocolInstances) {
                        // for each WaitingForOneToOneContactProtocolInstance, check whether the corresponding protocol instance is in the INVITATION_SENT_STATE_ID
                        if (waitingForOneToOneContactProtocolInstance.getProtocolId() == getProtocolId()) {
                            ProtocolInstance protocolInstance = ProtocolInstance.get(protocolManagerSession, waitingForOneToOneContactProtocolInstance.getProtocolUid(), getOwnedIdentity());
                            if (protocolInstance != null && protocolInstance.getCurrentStateId() == INVITATION_SENT_STATE_ID) {
                                // we indeed already invited Alice --> mark her as oneToOne, this will trigger the other waiting instance and finish the protocol

                                protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), true);

                                return new FinishedState();
                            }
                        }
                    }

                    // we did not find an invitation, so we tell Alice to downgrade us with an unexpected response
                    // we generate a new random UID as her protocol instance already reached a final state (and she may receive other responses for the same protocol Uid)
                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfo(receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), getOwnedIdentity()),
                            getProtocolId(),
                            new UID(getPrng()));
                    ChannelMessageToSend messageToSend = new OneToOneStatusSyncRequestMessage(coreProtocolMessage, false).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            return new FinishedState();
        }
    }

    // endregion
}
