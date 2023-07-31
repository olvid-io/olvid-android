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

import java.util.HashSet;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.GroupMembersChangedCallback;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.OneWayDialogProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class GroupInvitationProtocol extends ConcreteProtocol {

    public GroupInvitationProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() { return GROUP_INVITATION_PROTOCOL_ID; }

    // region states

    private static final int INVITATION_SENT_STATE_ID = 1;
    private static final int INVITATION_RECEIVED_STATE_ID = 2;
    private static final int RESPONSE_SENT_STATE_ID = 3;
    private static final int RESPONSE_RECEIVED_STATE_ID = 4;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{INVITATION_SENT_STATE_ID, RESPONSE_SENT_STATE_ID, RESPONSE_RECEIVED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case INVITATION_SENT_STATE_ID:
                return InvitationSentState.class;
            case INVITATION_RECEIVED_STATE_ID:
                return InvitationReceivedState.class;
            case RESPONSE_SENT_STATE_ID:
                return ResponseSentState.class;
            case RESPONSE_RECEIVED_STATE_ID:
                return ResponseReceivedState.class;
            default:
                return null;
        }
    }


    public static class InvitationReceivedState extends ConcreteProtocolState {
        private final GroupInformation groupInformation;
        private final UUID dialogUuid;
        private final HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails;

        InvitationReceivedState(GroupInformation groupInformation, UUID dialogUuid, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails) {
            super(INVITATION_RECEIVED_STATE_ID);
            this.groupInformation = groupInformation;
            this.dialogUuid = dialogUuid;
            this.groupMemberIdentitiesAndSerializedDetails = groupMemberIdentitiesAndSerializedDetails;
        }

        @SuppressWarnings("unused")
        public InvitationReceivedState(Encoded encodedState) throws Exception {
            super(INVITATION_RECEIVED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 3) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(list[0]);
            this.dialogUuid = list[1].decodeUuid();
            this.groupMemberIdentitiesAndSerializedDetails = new HashSet<>();
            for (Encoded encodedIdentityAndDisplayName: list[2].decodeList()) {
                this.groupMemberIdentitiesAndSerializedDetails.add(IdentityWithSerializedDetails.of(encodedIdentityAndDisplayName));
            }
        }

        @Override
        public Encoded encode() {
            Encoded[] encodedGroupMembers = new Encoded[groupMemberIdentitiesAndSerializedDetails.size()];
            int i=0;
            for (IdentityWithSerializedDetails identityWithSerializedDetails : groupMemberIdentitiesAndSerializedDetails) {
                encodedGroupMembers[i] = identityWithSerializedDetails.encode();
                i++;
            }
            return Encoded.of(new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(dialogUuid),
                    Encoded.of(encodedGroupMembers),
            });
        }
    }



    public static class InvitationSentState extends ConcreteProtocolState {
        InvitationSentState() {
            super(INVITATION_SENT_STATE_ID);
        }

        @SuppressWarnings("unused")
        public InvitationSentState(Encoded encodedState) throws Exception {
            super(INVITATION_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    public static class ResponseSentState extends ConcreteProtocolState {
        ResponseSentState() {
            super(RESPONSE_SENT_STATE_ID);
        }

        @SuppressWarnings("unused")
        public ResponseSentState(Encoded encodedState) throws Exception {
            super(RESPONSE_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    public static class ResponseReceivedState extends ConcreteProtocolState {
        ResponseReceivedState() {
            super(RESPONSE_RECEIVED_STATE_ID);
        }

        @SuppressWarnings("unused")
        public ResponseReceivedState(Encoded encodedState) throws Exception {
            super(RESPONSE_RECEIVED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion







    // region messages

    private static final int INITIAL_MESSAGE_ID = 0;
    private static final int GROUP_INVITATION_MESSAGE_ID = 1;
    private static final int DIALOG_ACCEPT_GROUP_INVITATION_MESSAGE_ID = 2;
    private static final int ACCEPT_INVITATION_MESSAGE_ID = 3;
    private static final int PROPAGATE_INVITATION_RESPONSE_MESSAGE_ID = 4;
//    private static final int TRUST_LEVEL_INCREASED_MESSAGE_ID = 5;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case GROUP_INVITATION_MESSAGE_ID:
                return GroupInvitationMessage.class;
            case DIALOG_ACCEPT_GROUP_INVITATION_MESSAGE_ID:
                return DialogAcceptGroupInvitationMessage.class;
            case ACCEPT_INVITATION_MESSAGE_ID:
                return InvitationResponseMessage.class;
            case PROPAGATE_INVITATION_RESPONSE_MESSAGE_ID:
                return PropagateInvitationResponseMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final GroupInformation groupInformation;
        private final HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, GroupInformation groupInformation, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.groupInformation = groupInformation;
            this.groupMemberIdentitiesAndSerializedDetails = groupMemberIdentitiesAndSerializedDetails;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[1]);
            this.groupMemberIdentitiesAndSerializedDetails = new HashSet<>();
            for (Encoded encodedIdentityAndDisplayName: receivedMessage.getInputs()[2].decodeList()) {
                this.groupMemberIdentitiesAndSerializedDetails.add(IdentityWithSerializedDetails.of(encodedIdentityAndDisplayName));
            }
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            Encoded[] encodeds = new Encoded[groupMemberIdentitiesAndSerializedDetails.size()];
            int i=0;
            for (IdentityWithSerializedDetails identityWithSerializedDetails : groupMemberIdentitiesAndSerializedDetails) {
                encodeds[i] = identityWithSerializedDetails.encode();
                i++;
            }
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    groupInformation.encode(),
                    Encoded.of(encodeds),
            };
        }
    }


    public static class GroupInvitationMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails;

        GroupInvitationMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.groupMemberIdentitiesAndSerializedDetails = groupMemberIdentitiesAndSerializedDetails;
        }

        @SuppressWarnings("unused")
        public GroupInvitationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.groupMemberIdentitiesAndSerializedDetails = new HashSet<>();
            for (Encoded encodedIdentityAndDisplayName: receivedMessage.getInputs()[1].decodeList()) {
                this.groupMemberIdentitiesAndSerializedDetails.add(IdentityWithSerializedDetails.of(encodedIdentityAndDisplayName));
            }
        }
        @Override
        public int getProtocolMessageId() {
            return GROUP_INVITATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            Encoded[] encodedGroupMembers = new Encoded[groupMemberIdentitiesAndSerializedDetails.size()];
            int i=0;
            for (IdentityWithSerializedDetails identityWithSerializedDetails : groupMemberIdentitiesAndSerializedDetails) {
                encodedGroupMembers[i] = identityWithSerializedDetails.encode();
                i++;
            }
            return new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(encodedGroupMembers),
            };
        }
    }


    public static class DialogAcceptGroupInvitationMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;
        private final UUID dialogUuid;

        DialogAcceptGroupInvitationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            invitationAccepted = false;
            dialogUuid = null;
        }

        @SuppressWarnings("unused")
        public DialogAcceptGroupInvitationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            invitationAccepted = receivedMessage.getEncodedResponse().decodeBoolean();
            dialogUuid = receivedMessage.getUserDialogUuid();
        }

        @Override
        public int getProtocolMessageId() {
            return DIALOG_ACCEPT_GROUP_INVITATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class InvitationResponseMessage extends ConcreteProtocolMessage {
        private final UID groupUid;
        private final boolean invitationAccepted;

        InvitationResponseMessage(CoreProtocolMessage coreProtocolMessage, UID groupUid, boolean invitationAccepted) {
            super(coreProtocolMessage);
            this.groupUid = groupUid;
            this.invitationAccepted = invitationAccepted;
        }

        @SuppressWarnings("unused")
        public InvitationResponseMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.groupUid = receivedMessage.getInputs()[0].decodeUid();
            this.invitationAccepted = receivedMessage.getInputs()[1].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return ACCEPT_INVITATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(groupUid),
                    Encoded.of(invitationAccepted),
            };
        }
    }


    public static class PropagateInvitationResponseMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;

        PropagateInvitationResponseMessage(CoreProtocolMessage coreProtocolMessage, boolean invitationAccepted) {
            super(coreProtocolMessage);
            this.invitationAccepted = invitationAccepted;
        }

        @SuppressWarnings("unused")
        public PropagateInvitationResponseMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.invitationAccepted = receivedMessage.getInputs()[0].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_INVITATION_RESPONSE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(invitationAccepted),
            };
        }
    }

    // endregion





    // region steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{SendInvitationStep.class, ProcessInvitationStep.class, ProcessResponseStep.class};
            case INVITATION_RECEIVED_STATE_ID:
                return new Class[]{ProcessInvitationDialogResponseStep.class, ProcessPropagatedInvitationResponseStep.class};
            default:
                return new Class[0];
        }
    }


    public static class SendInvitationStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public SendInvitationStep(InitialProtocolState startState, InitialMessage receivedMessage, GroupInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.groupMemberIdentitiesAndSerializedDetails.contains(new IdentityWithSerializedDetails(getOwnedIdentity(), ""))) {
                Logger.w("Error: the groupMemberIdentitiesAndSerializedDetails contains the ownedIdentity");
                return null;
            }

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: the groupInformation contains a different Identity than ownedIdentity");
                return null;
            }

            if (!receivedMessage.groupMemberIdentitiesAndSerializedDetails.contains(new IdentityWithSerializedDetails(receivedMessage.contactIdentity, ""))) {
                Logger.w("Error: the groupMemberIdentitiesAndSerializedDetails does not contain the contactIdentity");
                return null;
            }

            {
                // post an invitation to contactIdentity
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new GroupInvitationMessage(coreProtocolMessage, receivedMessage.groupInformation, receivedMessage.groupMemberIdentitiesAndSerializedDetails).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new InvitationSentState();
        }
    }


    public static class ProcessInvitationStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final GroupInvitationMessage receivedMessage;

        public ProcessInvitationStep(InitialProtocolState startState, GroupInvitationMessage receivedMessage, GroupInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupMemberIdentitiesAndSerializedDetails.contains(new IdentityWithSerializedDetails(getOwnedIdentity(), ""))) {
                Logger.w("Error: you received an invitation to a group without being part of groupMemberIdentitiesAndSerializedDetails");
                return null;
            }

            Identity groupOwnerIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();

            // check the message was received from the groupOwnerIdentity
            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(groupOwnerIdentity)) {
                Logger.w("Error: you received an invitation to a group from someone who is not the group owner");
                return null;
            }

            // check you are not already part of the group
            if (protocolManagerSession.identityDelegate.getGroup(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    receivedMessage.groupInformation.getGroupOwnerAndUid()) != null) {
                Logger.w("Received an invitation to a group you already belong to: accepting it :)");
                {
                    // notify groupOwner that you accepted the groupInvitation
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(groupOwnerIdentity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new InvitationResponseMessage(coreProtocolMessage, receivedMessage.groupInformation.groupUid, true).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
                {
                    // Propagate the accept to other owned devices
                    int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                    if (numberOfOtherDevices > 0) {
                        try {
                            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                            ChannelMessageToSend messageToSend = new PropagateInvitationResponseMessage(coreProtocolMessage, true).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        } catch (NoAcceptableChannelException ignored) { }
                    }
                }
                {
                    // reset the group members version to 0 and the published group details to those contained in the group information
                    protocolManagerSession.identityDelegate.resetGroupMembersAndPublishedDetailsVersions(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation);
                }
                return new ResponseSentState();
            }


            JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.groupInformation.serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);
            JsonGroupDetails jsonGroupDetails = jsonGroupDetailsWithVersionAndPhoto.getGroupDetails();
            String serializedGroupDetails = protocol.getJsonObjectMapper().writeValueAsString(jsonGroupDetails);

            // prompt user to accept

            UUID dialogUuid = UUID.randomUUID();
            {
                Identity[] identities = new Identity[receivedMessage.groupMemberIdentitiesAndSerializedDetails.size()];
                String[] serializedDetails = new String[identities.length];
                int i=0;
                for (IdentityWithSerializedDetails identityWithSerializedDetails : receivedMessage.groupMemberIdentitiesAndSerializedDetails) {
                    if (identityWithSerializedDetails.identity.equals(getOwnedIdentity())) {
                        identities[i] = groupOwnerIdentity;
                        serializedDetails[i] = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), groupOwnerIdentity);
                    } else {
                        identities[i] = identityWithSerializedDetails.identity;
                        serializedDetails[i] = identityWithSerializedDetails.serializedDetails;
                    }
                    i++;
                }

                // display group invite dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createAcceptGroupInviteDialog(serializedGroupDetails, receivedMessage.groupInformation.groupUid, receivedMessage.groupInformation.groupOwnerIdentity, identities, serializedDetails, receivedMessage.getServerTimestamp()), dialogUuid));
                ChannelMessageToSend messageToSend = new DialogAcceptGroupInvitationMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new InvitationReceivedState(receivedMessage.groupInformation, dialogUuid, receivedMessage.groupMemberIdentitiesAndSerializedDetails);
        }
    }


    public static class ProcessInvitationDialogResponseStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final DialogAcceptGroupInvitationMessage receivedMessage;

        public ProcessInvitationDialogResponseStep(InvitationReceivedState startState, DialogAcceptGroupInvitationMessage receivedMessage, GroupInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!startState.dialogUuid.equals(receivedMessage.dialogUuid)) {
                Logger.e("ObvDialog uuid mismatch in DialogAcceptGroupInvitationMessage.");
                return null;
            }


            boolean invitationAccepted = receivedMessage.invitationAccepted;
            Identity groupOwnerIdentity = startState.groupInformation.groupOwnerIdentity;

            {
                // Propagate the accept to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateInvitationResponseMessage(coreProtocolMessage, invitationAccepted).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), groupOwnerIdentity)) {
                // the groupOwner was deleted, abort the protocol
                // remove any dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new ResponseSentState();
            }

            {
                // notify groupOwner that you accepted the groupInvitation
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(groupOwnerIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new InvitationResponseMessage(coreProtocolMessage, startState.groupInformation.groupUid, invitationAccepted).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }





            if (invitationAccepted) {
                // create the group
                IdentityWithSerializedDetails[] pendingGroupMembers = new IdentityWithSerializedDetails[startState.groupMemberIdentitiesAndSerializedDetails.size()-1];
                int i=0;
                for (IdentityWithSerializedDetails identityWithSerializedDetails : startState.groupMemberIdentitiesAndSerializedDetails) {
                    if (identityWithSerializedDetails.identity.equals(getOwnedIdentity())) {
                        continue;
                    }
                    pendingGroupMembers[i] = identityWithSerializedDetails;
                    i++;
                }

                protocolManagerSession.identityDelegate.createContactGroup(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        startState.groupInformation,
                        new Identity[]{groupOwnerIdentity},
                        pendingGroupMembers,
                        false
                );
            }

            // remove the dialog
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
            ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new ResponseSentState();
        }
    }



    public static class ProcessPropagatedInvitationResponseStep extends ProtocolStep {
        private final InvitationReceivedState startState;
        private final PropagateInvitationResponseMessage receivedMessage;

        public ProcessPropagatedInvitationResponseStep(InvitationReceivedState startState, PropagateInvitationResponseMessage receivedMessage, GroupInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
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

            boolean invitationAccepted = receivedMessage.invitationAccepted;
            Identity groupOwnerIdentity = startState.groupInformation.groupOwnerIdentity;

            if (!protocolManagerSession.identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), groupOwnerIdentity)) {
                // the groupOwner was deleted, abort the protocol
                return new ResponseSentState();
            }

            if (invitationAccepted) {
                // create the group
                IdentityWithSerializedDetails[] pendingGroupMembers = new IdentityWithSerializedDetails[startState.groupMemberIdentitiesAndSerializedDetails.size()-1];
                int i=0;
                for (IdentityWithSerializedDetails identityWithSerializedDetails : startState.groupMemberIdentitiesAndSerializedDetails) {
                    if (identityWithSerializedDetails.identity.equals(getOwnedIdentity())) {
                        continue;
                    }
                    pendingGroupMembers[i] = identityWithSerializedDetails;
                    i++;
                }

                protocolManagerSession.identityDelegate.createContactGroup(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        startState.groupInformation,
                        new Identity[]{groupOwnerIdentity},
                        pendingGroupMembers,
                        false
                );
            }

            return new ResponseSentState();
        }
    }


    public static class ProcessResponseStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InvitationResponseMessage receivedMessage;

        public ProcessResponseStep(InitialProtocolState startState, InvitationResponseMessage receivedMessage, GroupInvitationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            Identity contactIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();

            final byte[] groupOwnerAndUid = new byte[getOwnedIdentity().getBytes().length + UID.UID_LENGTH];
            System.arraycopy(getOwnedIdentity().getBytes(), 0, groupOwnerAndUid, 0, getOwnedIdentity().getBytes().length);
            System.arraycopy(receivedMessage.groupUid.getBytes(), 0, groupOwnerAndUid, getOwnedIdentity().getBytes().length, UID.UID_LENGTH);


            GroupMembersChangedCallback groupMembersChangedCallback = new GroupMembersChangedCallback() {
                private final GroupInformation groupInformation = protocolManagerSession.identityDelegate.getGroupInformation(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid);
                @Override
                public void callback() throws Exception {
                    UID childProtocolUid = groupInformation.computeProtocolUid();
                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                            GROUP_MANAGEMENT_PROTOCOL_ID,
                            childProtocolUid,
                            false
                    );
                    ChannelMessageToSend messageToSend = new GroupManagementProtocol.GroupMembersOrDetailsChangedTriggerMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            };



            Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid);
            if (group == null || !group.isPendingMember(contactIdentity)) {
                // received a response from someone not in the group or already member --> if they are not already member, kick them
                if (receivedMessage.invitationAccepted && (group == null || !group.isMember(contactIdentity))) {
                    // the guy accepted, but he is neither pending, nor member --> send a KickFromGroupMessage message
                    UID groupManagementProtocolUid = GroupInformation.computeProtocolUid(getOwnedIdentity().getBytes(), receivedMessage.groupUid.getBytes());
                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createAllConfirmedObliviousChannelsInfo(contactIdentity, getOwnedIdentity()),
                            GROUP_MANAGEMENT_PROTOCOL_ID,
                            groupManagementProtocolUid,
                            false
                    );
                    ChannelMessageToSend messageToSend = new GroupManagementProtocol.KickFromGroupMessage(coreProtocolMessage, new GroupInformation(getOwnedIdentity(), receivedMessage.groupUid, JsonGroupDetailsWithVersionAndPhoto.DUMMY_GROUP_DETAILS)).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                } else if (!receivedMessage.invitationAccepted && group != null && group.isMember(contactIdentity)){
                    // the guy declined, but he is a member --> demote him to declined PendingMember
                    protocolManagerSession.identityDelegate.demoteGroupMemberToDeclinedPendingMember(
                            protocolManagerSession.session,
                            groupOwnerAndUid,
                            getOwnedIdentity(),
                            contactIdentity,
                            groupMembersChangedCallback);

                    UID groupManagementProtocolUid = GroupInformation.computeProtocolUid(getOwnedIdentity().getBytes(), receivedMessage.groupUid.getBytes());
                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createAllConfirmedObliviousChannelsInfo(contactIdentity, getOwnedIdentity()),
                            GROUP_MANAGEMENT_PROTOCOL_ID,
                            groupManagementProtocolUid,
                            false
                    );
                    ChannelMessageToSend messageToSend = new GroupManagementProtocol.KickFromGroupMessage(coreProtocolMessage, new GroupInformation(getOwnedIdentity(), receivedMessage.groupUid, JsonGroupDetailsWithVersionAndPhoto.DUMMY_GROUP_DETAILS)).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                } else if (receivedMessage.invitationAccepted && group.isMember(contactIdentity)) {
                    // the contact accepted an invite but was already member --> send him an up to date members list and group details
                    UID groupManagementProtocolUid = GroupInformation.computeProtocolUid(getOwnedIdentity().getBytes(), receivedMessage.groupUid.getBytes());

                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                            GROUP_MANAGEMENT_PROTOCOL_ID,
                            groupManagementProtocolUid,
                            false);
                    ChannelMessageToSend messageToSend = new GroupManagementProtocol.TriggerUpdateMembersMessage(
                            coreProtocolMessage,
                            protocolManagerSession.identityDelegate.getGroupInformation(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid),
                            contactIdentity).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
                return new ResponseReceivedState();
            }

            if (receivedMessage.invitationAccepted) {
                protocolManagerSession.identityDelegate.addGroupMemberFromPendingMember(
                        protocolManagerSession.session,
                        groupOwnerAndUid,
                        getOwnedIdentity(),
                        contactIdentity,
                        groupMembersChangedCallback
                );
            } else {
                protocolManagerSession.identityDelegate.setPendingMemberDeclined(
                        protocolManagerSession.session,
                        groupOwnerAndUid,
                        getOwnedIdentity(),
                        contactIdentity,
                        true
                );
            }

            return new ResponseReceivedState();
        }
    }
    // endregion
}
