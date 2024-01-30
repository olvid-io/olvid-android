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
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.notifications.ProtocolNotifications;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.databases.WaitingForOneToOneContactProtocolInstance;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.EmptyProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.OneWayDialogProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;


public class ContactMutualIntroductionProtocol extends ConcreteProtocol {
    public ContactMutualIntroductionProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return CONTACT_MUTUAL_INTRODUCTION_PROTOCOL_ID;
    }



    private static final int ACCEPT_TYPE_ALREADY_TRUSTED = 0;
    private static final int ACCEPT_TYPE_MANUAL = 2;

    // region states

    private static final int CONTACTS_INTRODUCED_STATE_ID = 1;
    private static final int INVITATION_RECEIVED_STATE_ID = 2;
    private static final int INVITATION_ACCEPTED_STATE_ID = 3;
    private static final int INVITATION_REJECTED_STATE_ID = 4;
    private static final int WAITING_FOR_ACK_STATE_ID = 5;
    private static final int MUTUAL_TRUST_ESTABLISHED_STATE_ID = 6;


    @Override
    public int[] getFinalStateIds() {
        return new int[]{CONTACTS_INTRODUCED_STATE_ID, INVITATION_REJECTED_STATE_ID, MUTUAL_TRUST_ESTABLISHED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case CONTACTS_INTRODUCED_STATE_ID:
                return ContactsIntroducedState.class;
            case INVITATION_RECEIVED_STATE_ID:
                return InvitationReceivedState.class;
            case INVITATION_ACCEPTED_STATE_ID:
                return InvitationAcceptedState.class;
            case INVITATION_REJECTED_STATE_ID:
                return InvitationRejectedState.class;
            case WAITING_FOR_ACK_STATE_ID:
                return WaitingForAckState.class;
            case MUTUAL_TRUST_ESTABLISHED_STATE_ID:
                return MutualTrustEstablishedState.class;
            default:
                return null;
        }
    }


    public static class ContactsIntroducedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public ContactsIntroducedState(Encoded encodedState) throws Exception {
            super(CONTACTS_INTRODUCED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }
        ContactsIntroducedState() {
            super(CONTACTS_INTRODUCED_STATE_ID);
        }
        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }


    public static class InvitationReceivedState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final Identity mediatorIdentity;
        private final UUID dialogUuid;

        @SuppressWarnings("unused")
        public InvitationReceivedState(Encoded encodedState) throws Exception {
            super(INVITATION_RECEIVED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactSerializedDetails = list[1].decodeString();
            this.mediatorIdentity = list[2].decodeIdentity();
            this.dialogUuid = list[3].decodeUuid();
        }

        InvitationReceivedState(Identity contactIdentity, String contactSerializedDetails, Identity mediatorIdentity, UUID dialogUuid) {
            super(INVITATION_RECEIVED_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.mediatorIdentity = mediatorIdentity;
            this.dialogUuid = dialogUuid;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(mediatorIdentity),
                    Encoded.of(dialogUuid),
            });
        }
    }


    public static class InvitationAcceptedState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final Identity mediatorIdentity;
        private final UUID dialogUuid;
        private final int acceptType;

        @SuppressWarnings("unused")
        public InvitationAcceptedState(Encoded encodedState) throws Exception {
            super(INVITATION_ACCEPTED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 5) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactSerializedDetails = list[1].decodeString();
            this.mediatorIdentity = list[2].decodeIdentity();
            this.dialogUuid = list[3].decodeUuid();
            this.acceptType = (int) list[4].decodeLong();
        }

        InvitationAcceptedState(Identity contactIdentity, String contactSerializedDetails, Identity mediatorIdentity, UUID dialogUuid, int acceptType) {
            super(INVITATION_ACCEPTED_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.mediatorIdentity = mediatorIdentity;
            this.dialogUuid = dialogUuid;
            this.acceptType = acceptType;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(mediatorIdentity),
                    Encoded.of(dialogUuid),
                    Encoded.of(acceptType),
            });
        }
    }


    public static class InvitationRejectedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public InvitationRejectedState(Encoded encodedState) throws Exception {
            super(INVITATION_REJECTED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }
        InvitationRejectedState() {
            super(INVITATION_REJECTED_STATE_ID);
        }
        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }


    public static class WaitingForAckState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final Identity mediatorIdentity;
        private final UUID dialogUuid;
        private final int acceptType;

        @SuppressWarnings("unused")
        public WaitingForAckState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_ACK_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 5) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.contactSerializedDetails = list[1].decodeString();
            this.mediatorIdentity = list[2].decodeIdentity();
            this.dialogUuid = list[3].decodeUuid();
            this.acceptType = (int) list[4].decodeLong();
        }

        WaitingForAckState(Identity contactIdentity, String contactSerializedDetails, Identity mediatorIdentity, UUID dialogUuid, int acceptType) {
            super(WAITING_FOR_ACK_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.mediatorIdentity = mediatorIdentity;
            this.dialogUuid = dialogUuid;
            this.acceptType = acceptType;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(mediatorIdentity),
                    Encoded.of(dialogUuid),
                    Encoded.of(acceptType),
            });
        }
    }


    public static class MutualTrustEstablishedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public MutualTrustEstablishedState(Encoded encodedState) throws Exception {
            super(MUTUAL_TRUST_ESTABLISHED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }
        MutualTrustEstablishedState() {
            super(MUTUAL_TRUST_ESTABLISHED_STATE_ID);
        }
        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion








    // region messages

    private static final int INITIAL_MESSAGE_ID = 0;
    private static final int MEDIATOR_INVITATION_MESSAGE_ID = 1;
    private static final int DIALOG_ACCEPT_MEDIATOR_INVITE_MESSAGE_ID = 2;
    private static final int PROPAGATE_CONFIRMATION_MESSAGE_ID = 3;
    private static final int NOTIFY_CONTACT_OF_ACCEPTED_INVITATION_MESSAGE_ID = 4;
    private static final int PROPAGATE_NOTIFICATION_MESSAGE_ID = 5;
    private static final int ACK_MESSAGE_ID = 6;
    private static final int TRUST_LEVEL_INCREASED_MESSAGE_ID = 7;
    private static final int PROPAGATED_INITIAL_MESSAGE = 9; // we skip 8 to stay in sync with iOS

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case MEDIATOR_INVITATION_MESSAGE_ID:
                return MediatorInvitationMessage.class;
            case DIALOG_ACCEPT_MEDIATOR_INVITE_MESSAGE_ID:
                return DialogAcceptMediatorInviteMessage.class;
            case PROPAGATE_CONFIRMATION_MESSAGE_ID:
                return PropagateConfirmationMessage.class;
            case NOTIFY_CONTACT_OF_ACCEPTED_INVITATION_MESSAGE_ID:
                return NotifyContactOfAcceptedInvitationMessage.class;
            case PROPAGATE_NOTIFICATION_MESSAGE_ID:
                return PropagateNotificationMessage.class;
            case ACK_MESSAGE_ID:
                return AckMessage.class;
            case TRUST_LEVEL_INCREASED_MESSAGE_ID:
                return TrustLevelIncreasedMessage.class;
            case PROPAGATED_INITIAL_MESSAGE:
                return PropagatedInitialMessage.class;
            default:
                return null;
        }
    }


    public static class InitialMessage extends ConcreteProtocolMessage {
        protected final Identity contactIdentityA;
        protected final Identity contactIdentityB;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentityA, Identity contactIdentityB) {
            super(coreProtocolMessage);
            this.contactIdentityA = contactIdentityA;
            this.contactIdentityB = contactIdentityB;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactIdentityA = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactIdentityB = receivedMessage.getInputs()[1].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentityA),
                    Encoded.of(contactIdentityB),
            };
        }
    }


    public static class MediatorInvitationMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String contactSerializedDetails;

        MediatorInvitationMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String contactSerializedDetails) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
        }

        @SuppressWarnings("unused")
        public MediatorInvitationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.contactSerializedDetails = receivedMessage.getInputs()[1].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return MEDIATOR_INVITATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
            };
        }
    }


    public static class DialogAcceptMediatorInviteMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;
        private final UUID dialogUuid;

        DialogAcceptMediatorInviteMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            invitationAccepted = false;
            dialogUuid = null;
        }

        @SuppressWarnings("unused")
        public DialogAcceptMediatorInviteMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            invitationAccepted = receivedMessage.getEncodedResponse().decodeBoolean();
            dialogUuid = receivedMessage.getUserDialogUuid();
        }

        @Override
        public int getProtocolMessageId() {
            return DIALOG_ACCEPT_MEDIATOR_INVITE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class PropagateConfirmationMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;
        private final Identity contactIdentity;
        private final String contactSerializedDetails;
        private final Identity mediatorIdentity;

        PropagateConfirmationMessage(CoreProtocolMessage coreProtocolMessage, boolean invitationAccepted, Identity contactIdentity, String contactSerializedDetails, Identity mediatorIdentity) {
            super(coreProtocolMessage);
            this.invitationAccepted = invitationAccepted;
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.mediatorIdentity = mediatorIdentity;
        }


        @SuppressWarnings("unused")
        public PropagateConfirmationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.invitationAccepted = receivedMessage.getInputs()[0].decodeBoolean();
            this.contactIdentity = receivedMessage.getInputs()[1].decodeIdentity();
            this.contactSerializedDetails = receivedMessage.getInputs()[2].decodeString();
            this.mediatorIdentity = receivedMessage.getInputs()[3].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_CONFIRMATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(invitationAccepted),
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(mediatorIdentity),
            };
        }
    }

    public static class NotifyContactOfAcceptedInvitationMessage extends ConcreteProtocolMessage {
        private final UID[] contactDeviceUids;
        private final byte[] signature;

        NotifyContactOfAcceptedInvitationMessage(CoreProtocolMessage coreProtocolMessage, UID[] contactDeviceUids, byte[] signature) {
            super(coreProtocolMessage);
            this.contactDeviceUids = contactDeviceUids;
            this.signature = signature;
        }

        @SuppressWarnings("unused")
        public NotifyContactOfAcceptedInvitationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactDeviceUids = receivedMessage.getInputs()[0].decodeUidArray();
            this.signature = receivedMessage.getInputs()[1].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return NOTIFY_CONTACT_OF_ACCEPTED_INVITATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactDeviceUids),
                    Encoded.of(signature),
            };
        }
    }


    public static class PropagateNotificationMessage extends ConcreteProtocolMessage {
        private final UID[] contactDeviceUids;

        PropagateNotificationMessage(CoreProtocolMessage coreProtocolMessage, UID[] contactDeviceUids) {
            super(coreProtocolMessage);
            this.contactDeviceUids = contactDeviceUids;
        }

        @SuppressWarnings("unused")
        public PropagateNotificationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactDeviceUids = receivedMessage.getInputs()[0].decodeUidArray();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_NOTIFICATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactDeviceUids),
            };
        }
    }


    public static class AckMessage extends EmptyProtocolMessage {

        AckMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public AckMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return ACK_MESSAGE_ID;
        }
    }


    public static class TrustLevelIncreasedMessage extends ConcreteProtocolMessage {
        Identity trustLevelIncreasedIdentity;

        // no other constructor needed here. Instances are created by WaitingForOneToOneContactProtocolInstance.getGenericProtocolMessageToSendWhenTrustLevelIncreased()

        public TrustLevelIncreasedMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.trustLevelIncreasedIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return TRUST_LEVEL_INCREASED_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class PropagatedInitialMessage extends InitialMessage {
        public PropagatedInitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentityA, Identity contactIdentityB) {
            super(coreProtocolMessage, contactIdentityA, contactIdentityB);
        }

        @SuppressWarnings("unused")
        public PropagatedInitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATED_INITIAL_MESSAGE;
        }
    }

    // endregion









    // region steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{IntroduceContactsStep.class, ProcessPropagatedInitialMessageStep.class, CheckTrustLevelsAndShowDialogStep.class};
            case INVITATION_RECEIVED_STATE_ID:
                return new Class[]{PropagateInviteResponseStep.class, ProcessPropagatedInviteResponseStep.class, ReCheckTrustLevelsAfterTrustLevelIncreaseStep.class};
            case INVITATION_ACCEPTED_STATE_ID:
                return new Class[]{PropagateNotificationAddTrustAndSendAckStep.class, ProcessPropagatedNotificationAndAddTrustStep.class};
            case WAITING_FOR_ACK_STATE_ID:
                return new Class[]{NotifyMutualTrustEstablishedStep.class};
            case CONTACTS_INTRODUCED_STATE_ID:
            case INVITATION_REJECTED_STATE_ID:
            case MUTUAL_TRUST_ESTABLISHED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class IntroduceContactsStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public IntroduceContactsStep(InitialProtocolState startState, InitialMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // check that both contacts are active and oneToOne
                if (!protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentityA)
                    || !protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentityB)
                    || !protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentityA)
                    || !protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentityA)) {
                    return new ContactsIntroducedState();
                }
            }

            {
                // post an invitation message to contact A
                String serializedDetailsB = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentityB);
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.contactIdentityA, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new MediatorInvitationMessage(coreProtocolMessage, receivedMessage.contactIdentityB, serializedDetailsB).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // post an invitation message to contact B
                String serializedDetailsA = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentityA);
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.contactIdentityB, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new MediatorInvitationMessage(coreProtocolMessage, receivedMessage.contactIdentityA, serializedDetailsA).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // if we have other devices, propagate the invite so the invitation sent messages can be inserted in the relevant discussion
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagatedInitialMessage(coreProtocolMessage, receivedMessage.contactIdentityA, receivedMessage.contactIdentityB).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            // send a notification to insert invitation sent messages in relevant discussions
            protocolManagerSession.session.addSessionCommitListener(() -> {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT_OWNED_IDENTITY_KEY, getOwnedIdentity());
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT_CONTACT_IDENTITY_A_KEY, receivedMessage.contactIdentityA);
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT_CONTACT_IDENTITY_B_KEY, receivedMessage.contactIdentityB);
                protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT, userInfo);
            });

            return new ContactsIntroducedState();
        }
    }


    public static class ProcessPropagatedInitialMessageStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final PropagatedInitialMessage receivedMessage;

        public ProcessPropagatedInitialMessageStep(InitialProtocolState startState, PropagatedInitialMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // send a notification to insert invitation sent messages in relevant discussions
            protocolManagerSession.session.addSessionCommitListener(() -> {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT_OWNED_IDENTITY_KEY, getOwnedIdentity());
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT_CONTACT_IDENTITY_A_KEY, receivedMessage.contactIdentityA);
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT_CONTACT_IDENTITY_B_KEY, receivedMessage.contactIdentityB);
                protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_SENT, userInfo);
            });

            return new ContactsIntroducedState();
        }
    }

    public static class CheckTrustLevelsAndShowDialogStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final MediatorInvitationMessage receivedMessage;

        public CheckTrustLevelsAndShowDialogStep(InitialProtocolState startState, MediatorInvitationMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            Identity mediatorIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();

            // check that the mediator is a one to one contact --> reject if not
            if (!protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), mediatorIdentity)) {
                return new InvitationRejectedState();
            }

            // check if presented contact is already oneToOne
            boolean contactAlreadyOneToOne = protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity);
            if (contactAlreadyOneToOne) {
                // auto-accept
                UID[] deviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

                byte[] signature = protocolManagerSession.identityDelegate.signIdentities(
                        protocolManagerSession.session,
                        Constants.SignatureContext.MUTUAL_INTRODUCTION,
                        new Identity[]{mediatorIdentity, receivedMessage.contactIdentity, getOwnedIdentity()},
                        getOwnedIdentity(),
                        getPrng()
                );

                // notify contact and send him the deviceUids to send ACK to
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new NotifyContactOfAcceptedInvitationMessage(coreProtocolMessage, deviceUids, signature).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                UUID dialogUuid = UUID.randomUUID();

                return new InvitationAcceptedState(receivedMessage.contactIdentity, receivedMessage.contactSerializedDetails, mediatorIdentity, dialogUuid, ACCEPT_TYPE_ALREADY_TRUSTED);
            } else {
                // prompt user to accept
                UUID dialogUuid = UUID.randomUUID();

                {
                    // display mediator invite dialog
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createAcceptMediatorInviteDialog(receivedMessage.contactSerializedDetails, receivedMessage.contactIdentity, mediatorIdentity, receivedMessage.getServerTimestamp()), dialogUuid));
                    ChannelMessageToSend messageToSend = new DialogAcceptMediatorInviteMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                // also insert a WaitingForTrustLevelIncrease to re-evaluate if needed.
                WaitingForOneToOneContactProtocolInstance.create(
                        protocolManagerSession,
                        getProtocolInstanceUid(),
                        getOwnedIdentity(),
                        receivedMessage.contactIdentity,
                        getProtocolId(),
                        TRUST_LEVEL_INCREASED_MESSAGE_ID);

                return new InvitationReceivedState(receivedMessage.contactIdentity, receivedMessage.contactSerializedDetails, mediatorIdentity, dialogUuid);
            }
        }
    }


    public static class ReCheckTrustLevelsAfterTrustLevelIncreaseStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final TrustLevelIncreasedMessage receivedMessage;

        public ReCheckTrustLevelsAfterTrustLevelIncreaseStep(InvitationReceivedState startState, TrustLevelIncreasedMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // check if presented contact is already oneToOne
            boolean contactAlreadyTrusted = protocolManagerSession.identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity);
            if (contactAlreadyTrusted) {
                // auto-accept
                {
                    UID[] deviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

                    byte[] signature = protocolManagerSession.identityDelegate.signIdentities(
                            protocolManagerSession.session,
                            Constants.SignatureContext.MUTUAL_INTRODUCTION,
                            new Identity[]{ startState.mediatorIdentity, startState.contactIdentity, getOwnedIdentity()},
                            getOwnedIdentity(),
                            getPrng()
                    );

                    // notify contact and send him the deviceUids to send ACK to
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(startState.contactIdentity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new NotifyContactOfAcceptedInvitationMessage(coreProtocolMessage, deviceUids, signature).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                // remove the old dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new InvitationAcceptedState(startState.contactIdentity, startState.contactSerializedDetails, startState.mediatorIdentity, startState.dialogUuid, ACCEPT_TYPE_ALREADY_TRUSTED);
            } else {
                // prompt user to accept
                {
                    // display mediator invite dialog
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createAcceptMediatorInviteDialog(startState.contactSerializedDetails, startState.contactIdentity, startState.mediatorIdentity, System.currentTimeMillis()), startState.dialogUuid));
                    ChannelMessageToSend messageToSend = new DialogAcceptMediatorInviteMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }


                {
                    // also insert a WaitingForTrustLevelIncrease to re-evaluate if needed (and delete the previous one)
                    WaitingForOneToOneContactProtocolInstance instance = WaitingForOneToOneContactProtocolInstance.get(
                            protocolManagerSession,
                            getProtocolInstanceUid(),
                            getOwnedIdentity(),
                            receivedMessage.trustLevelIncreasedIdentity);
                    if (instance != null) {
                        instance.delete();
                    }
                    WaitingForOneToOneContactProtocolInstance.create(
                            protocolManagerSession,
                            getProtocolInstanceUid(),
                            getOwnedIdentity(),
                            startState.contactIdentity,
                            getProtocolId(),
                            TRUST_LEVEL_INCREASED_MESSAGE_ID);
                }

                return startState;
            }
        }
    }


    public static class PropagateInviteResponseStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final DialogAcceptMediatorInviteMessage receivedMessage;

        public PropagateInviteResponseStep(InvitationReceivedState startState, DialogAcceptMediatorInviteMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!startState.dialogUuid.equals(receivedMessage.dialogUuid)) {
                Logger.e("ObvDialog uuid mismatch in DialogAcceptMediatorInviteMessage.");
                return null;
            }

            {
                // Propagate the accept/reject to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateConfirmationMessage(coreProtocolMessage, receivedMessage.invitationAccepted, startState.contactIdentity, startState.contactSerializedDetails, startState.mediatorIdentity).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            // send a notification to insert invitation accepted/ignored messages in the discussion
            protocolManagerSession.session.addSessionCommitListener(() -> {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_OWNED_IDENTITY_KEY, getOwnedIdentity());
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_MEDIATOR_IDENTITY_KEY, startState.mediatorIdentity);
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_CONTACT_SERIALIZED_DETAILS_KEY, startState.contactSerializedDetails);
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_ACCEPTED_KEY, receivedMessage.invitationAccepted);
                protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE, userInfo);
            });

            if (receivedMessage.invitationAccepted) {
                {
                    // Display invitation accepted dialog
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createMediatorInviteAcceptedDialog(startState.contactSerializedDetails, startState.contactIdentity, startState.mediatorIdentity), startState.dialogUuid));
                    ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                {
                    UID[] deviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

                    byte[] signature = protocolManagerSession.identityDelegate.signIdentities(
                            protocolManagerSession.session,
                            Constants.SignatureContext.MUTUAL_INTRODUCTION,
                            new Identity[]{startState.mediatorIdentity, startState.contactIdentity, getOwnedIdentity()},
                            getOwnedIdentity(),
                            getPrng()
                    );

                    // notify contact and send him the deviceUids to send ACK to
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(startState.contactIdentity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new NotifyContactOfAcceptedInvitationMessage(coreProtocolMessage, deviceUids, signature).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                }

                return new InvitationAcceptedState(startState.contactIdentity, startState.contactSerializedDetails, startState.mediatorIdentity, startState.dialogUuid, ACCEPT_TYPE_MANUAL);
            } else {
                {
                    // remove the dialog
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                    ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                return new InvitationRejectedState();
            }
        }
    }


    public static class ProcessPropagatedInviteResponseStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final PropagateConfirmationMessage receivedMessage;

        public ProcessPropagatedInviteResponseStep(InvitationReceivedState startState, PropagateConfirmationMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // send a notification to insert invitation accepted/ignored messages in the discussion
            protocolManagerSession.session.addSessionCommitListener(() -> {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_OWNED_IDENTITY_KEY, getOwnedIdentity());
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_MEDIATOR_IDENTITY_KEY, startState.mediatorIdentity);
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_CONTACT_SERIALIZED_DETAILS_KEY, startState.contactSerializedDetails);
                userInfo.put(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE_ACCEPTED_KEY, receivedMessage.invitationAccepted);
                protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_CONTACT_INTRODUCTION_INVITATION_RESPONSE, userInfo);
            });

            if (receivedMessage.invitationAccepted) {
                {
                    // Display invitation accepted dialog
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createMediatorInviteAcceptedDialog(receivedMessage.contactSerializedDetails, receivedMessage.contactIdentity, receivedMessage.mediatorIdentity), startState.dialogUuid));
                    ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                return new InvitationAcceptedState(receivedMessage.contactIdentity, receivedMessage.contactSerializedDetails, receivedMessage.mediatorIdentity, startState.dialogUuid, ACCEPT_TYPE_MANUAL);
            } else {
                {
                    // remove the dialog
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                    ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                return new InvitationRejectedState();
            }
        }
    }


    public static class PropagateNotificationAddTrustAndSendAckStep extends ProtocolStep {
        private final InvitationAcceptedState startState;
        private final NotifyContactOfAcceptedInvitationMessage receivedMessage;

        public PropagateNotificationAddTrustAndSendAckStep(InvitationAcceptedState startState, NotifyContactOfAcceptedInvitationMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            boolean signatureIsValid = Signature.verify(
                    Constants.SignatureContext.MUTUAL_INTRODUCTION,
                    new Identity[]{startState.mediatorIdentity, getOwnedIdentity(), startState.contactIdentity},
                    startState.contactIdentity,
                    receivedMessage.signature
            );

            if (!signatureIsValid) {
                Logger.w("Received a NotifyContactOfAcceptedInvitationMessage with an invalid signature");
                return null;
            }

            // only create the contact if it does not already exist
            if (!protocolManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, startState.contactIdentity, startState.contactSerializedDetails, getOwnedIdentity(), TrustOrigin.createIntroductionTrustOrigin(System.currentTimeMillis(), startState.mediatorIdentity), true);
            } else {
                protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), TrustOrigin.createIntroductionTrustOrigin(System.currentTimeMillis(), startState.mediatorIdentity), true);
            }
            for (UID contactDeviceUid: receivedMessage.contactDeviceUids) {
                protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, contactDeviceUid, false);
            }


            {
                // Propagate the notification to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateNotificationMessage(coreProtocolMessage, receivedMessage.contactDeviceUids).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            {
                // send ack to contact
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), receivedMessage.contactDeviceUids));
                ChannelMessageToSend messageToSend = new AckMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new WaitingForAckState(startState.contactIdentity, startState.contactSerializedDetails, startState.mediatorIdentity, startState.dialogUuid, startState.acceptType);
        }
    }


    public static class ProcessPropagatedNotificationAndAddTrustStep extends ProtocolStep {
        private final InvitationAcceptedState startState;
        private final PropagateNotificationMessage receivedMessage;

        public ProcessPropagatedNotificationAndAddTrustStep(InvitationAcceptedState startState, PropagateNotificationMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // only create the contact if it does not already exist
            if (!protocolManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, startState.contactIdentity, startState.contactSerializedDetails, getOwnedIdentity(), TrustOrigin.createIntroductionTrustOrigin(System.currentTimeMillis(), startState.mediatorIdentity), true);
            } else {
                protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), TrustOrigin.createIntroductionTrustOrigin(System.currentTimeMillis(), startState.mediatorIdentity), true);
            }
            for (UID contactDeviceUid: receivedMessage.contactDeviceUids) {
                protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, contactDeviceUid, false);
            }

            return new WaitingForAckState(startState.contactIdentity, startState.contactSerializedDetails, startState.mediatorIdentity, startState.dialogUuid, startState.acceptType);
        }
    }


    public static class NotifyMutualTrustEstablishedStep extends ProtocolStep {
        private final WaitingForAckState startState;
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final AckMessage receivedMessage;

        public NotifyMutualTrustEstablishedStep(WaitingForAckState startState, AckMessage receivedMessage, ContactMutualIntroductionProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // display the mutual trust established dialog
                switch (startState.acceptType) {
                    case ACCEPT_TYPE_MANUAL: {
                        // delete dialog
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                        ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        break;
                    }
                    case ACCEPT_TYPE_ALREADY_TRUSTED:
                    default:
                        break;
                }
            }

            return new MutualTrustEstablishedState();
        }
    }

    // endregion
}
