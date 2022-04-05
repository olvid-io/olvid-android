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
import java.util.HashSet;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.GroupMembersChangedCallback;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;
//import io.olvid.engine.protocol.databases.PostponedGroupManagementReceivedMessage;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class GroupManagementProtocol extends ConcreteProtocol {

    public GroupManagementProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
        eraseReceivedMessagesAfterReachingAFinalState = false;
    }

    @Override
    public int getProtocolId() { return GROUP_MANAGEMENT_PROTOCOL_ID; }

    // region states

    static final int FINAL_STATE_ID = 1;


    @Override
    public int[] getFinalStateIds() {
        return new int[]{FINAL_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case FINAL_STATE_ID:
                return FinalState.class;
            default:
                return null;
        }
    }


    public static class FinalState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public FinalState(Encoded encodedState) throws Exception {
            super(FINAL_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        FinalState() {
            super(FINAL_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion







    // region messages

    private static final int INITIATE_GROUP_CREATION_MESSAGE_ID = 0;
    private static final int PROPAGATE_GROUP_CREATION_MESSAGE_ID = 1;
    private static final int GROUP_MEMBERS_CHANGED_TRIGGER_MESSAGE_ID = 2;
    private static final int NEW_MEMBERS_MESSAGE_ID = 3; // update to members, group details (including photo). Sent to members, pending (and owner's other devices ??)
    private static final int ADD_GROUP_MEMBERS_MESSAGE_ID = 4;
    private static final int REMOVE_GROUP_MEMBERS_MESSAGE_ID = 5;
    private static final int KICK_FROM_GROUP_MESSAGE_ID = 6;
    private static final int NOTIFY_GROUP_LEFT_MESSAGE_ID = 7;
    private static final int REINVITE_PENDING_MEMBER_MESSAGE_ID = 8;
    private static final int DISBAND_GROUP_MESSAGE_ID = 9;
    private static final int LEAVE_GROUP_MESSAGE_ID = 10;
    private static final int INITIATE_GROUP_MEMBERS_QUERY_MESSAGE_ID = 11;
    private static final int QUERY_GROUP_MEMBERS_MESSAGE_ID = 12;
    private static final int TRIGGER_REINVITE_MESSAGE_ID = 13;
    private static final int TRIGGER_UPDATE_MEMBERS_MESSAGE_ID = 14;
    private static final int UPLOAD_GROUP_PHOTO_MESSAGE_MESSAGE_ID = 15;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIATE_GROUP_CREATION_MESSAGE_ID:
                return InitiateGroupCreationMessage.class;
            case PROPAGATE_GROUP_CREATION_MESSAGE_ID:
                return PropagateGroupCreationMessage.class;
            case GROUP_MEMBERS_CHANGED_TRIGGER_MESSAGE_ID:
                return GroupMembersOrDetailsChangedTriggerMessage.class;
            case NEW_MEMBERS_MESSAGE_ID:
                return NewMembersMessage.class;
            case ADD_GROUP_MEMBERS_MESSAGE_ID:
                return AddGroupMembersMessage.class;
            case REMOVE_GROUP_MEMBERS_MESSAGE_ID:
                return RemoveGroupMembersMessage.class;
            case KICK_FROM_GROUP_MESSAGE_ID:
                return KickFromGroupMessage.class;
            case REINVITE_PENDING_MEMBER_MESSAGE_ID:
                return ReinvitePendingMemberMessage.class;
            case DISBAND_GROUP_MESSAGE_ID:
                return DisbandGroupMessage.class;
            case LEAVE_GROUP_MESSAGE_ID:
                return LeaveGroupMessage.class;
            case NOTIFY_GROUP_LEFT_MESSAGE_ID:
                return NotifyGroupLeftMessage.class;
            case INITIATE_GROUP_MEMBERS_QUERY_MESSAGE_ID:
                return InitiateGroupMembersQueryMessage.class;
            case QUERY_GROUP_MEMBERS_MESSAGE_ID:
                return QueryGroupMembersMessage.class;
            case TRIGGER_REINVITE_MESSAGE_ID:
                return TriggerReinviteMessage.class;
            case TRIGGER_UPDATE_MEMBERS_MESSAGE_ID:
                return TriggerUpdateMembersMessage.class;
            case UPLOAD_GROUP_PHOTO_MESSAGE_MESSAGE_ID:
                return UploadGroupPhotoMessage.class;
            default:
                return null;
        }
    }

    private static abstract class GroupInformationOnlyMessage extends ConcreteProtocolMessage {
        final GroupInformation groupInformation;

        public GroupInformationOnlyMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
        }

        public GroupInformationOnlyMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupInformation.encode(),
            };
        }
    }

    public static class InitiateGroupCreationMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails;
        private final String absolutePhotoUrl;

        public InitiateGroupCreationMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, String absolutePhotoUrl, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.groupMemberIdentitiesAndSerializedDetails = groupMemberIdentitiesAndSerializedDetails;
            this.absolutePhotoUrl = absolutePhotoUrl;
        }

        @SuppressWarnings("unused")
        public InitiateGroupCreationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.groupMemberIdentitiesAndSerializedDetails = new HashSet<>();
            for (Encoded encodedIdentityAndDisplayName: receivedMessage.getInputs()[1].decodeList()) {
                this.groupMemberIdentitiesAndSerializedDetails.add(IdentityWithSerializedDetails.of(encodedIdentityAndDisplayName));
            }
            this.absolutePhotoUrl = receivedMessage.getInputs()[2].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_GROUP_CREATION_MESSAGE_ID;
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
                    groupInformation.encode(),
                    Encoded.of(encodeds),
                    (absolutePhotoUrl ==null) ? Encoded.of("") : Encoded.of(absolutePhotoUrl),
            };
        }
    }

    public static class PropagateGroupCreationMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails;

        PropagateGroupCreationMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.groupMemberIdentitiesAndSerializedDetails = groupMemberIdentitiesAndSerializedDetails;
        }

        @SuppressWarnings("unused")
        public PropagateGroupCreationMessage(ReceivedMessage receivedMessage) throws Exception {
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
            return PROPAGATE_GROUP_CREATION_MESSAGE_ID;
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
                    groupInformation.encode(),
                    Encoded.of(encodeds),
            };
        }
    }

    public static class GroupMembersOrDetailsChangedTriggerMessage extends GroupInformationOnlyMessage {
        public GroupMembersOrDetailsChangedTriggerMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage, groupInformation);
        }

        @SuppressWarnings("unused")
        public GroupMembersOrDetailsChangedTriggerMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return GROUP_MEMBERS_CHANGED_TRIGGER_MESSAGE_ID;
        }
    }

    public static class NewMembersMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails;
        private final HashSet<IdentityWithSerializedDetails> pendingMemberIdentitiesAndSerializedDetails;
        private final long membersVersion;

        public NewMembersMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails, HashSet<IdentityWithSerializedDetails> pendingMemberIdentitiesAndSerializedDetails, long membersVersion) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.groupMemberIdentitiesAndSerializedDetails = groupMemberIdentitiesAndSerializedDetails;
            this.pendingMemberIdentitiesAndSerializedDetails = pendingMemberIdentitiesAndSerializedDetails;
            this.membersVersion = membersVersion;
        }

        @SuppressWarnings("unused")
        public NewMembersMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.groupMemberIdentitiesAndSerializedDetails = new HashSet<>();
            for (Encoded encodedIdentityAndDisplayName: receivedMessage.getInputs()[1].decodeList()) {
                this.groupMemberIdentitiesAndSerializedDetails.add(IdentityWithSerializedDetails.of(encodedIdentityAndDisplayName));
            }
            this.pendingMemberIdentitiesAndSerializedDetails = new HashSet<>();
            for (Encoded encodedIdentityAndDisplayName: receivedMessage.getInputs()[2].decodeList()) {
                this.pendingMemberIdentitiesAndSerializedDetails.add(IdentityWithSerializedDetails.of(encodedIdentityAndDisplayName));
            }
            this.membersVersion = receivedMessage.getInputs()[3].decodeLong();
        }
        @Override
        public int getProtocolMessageId() {
            return NEW_MEMBERS_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            Encoded[] encodedMembers = new Encoded[groupMemberIdentitiesAndSerializedDetails.size()];
            int i=0;
            for (IdentityWithSerializedDetails identityWithSerializedDetails : groupMemberIdentitiesAndSerializedDetails) {
                encodedMembers[i] = identityWithSerializedDetails.encode();
                i++;
            }
            Encoded[] encodedPendings = new Encoded[pendingMemberIdentitiesAndSerializedDetails.size()];
            i=0;
            for (IdentityWithSerializedDetails identityWithSerializedDetails : pendingMemberIdentitiesAndSerializedDetails) {
                encodedPendings[i] = identityWithSerializedDetails.encode();
                i++;
            }
            return new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(encodedMembers),
                    Encoded.of(encodedPendings),
                    Encoded.of(membersVersion),
            };
        }
    }

    public static class AddGroupMembersMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final HashSet<Identity> newMembersIdentity;

        public AddGroupMembersMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, HashSet<Identity> newMembersIdentity) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.newMembersIdentity = newMembersIdentity;
        }

        @SuppressWarnings("unused")
        public AddGroupMembersMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.newMembersIdentity = new HashSet<>();
            for (Encoded encodedIdentity: receivedMessage.getInputs()[1].decodeList()) {
                this.newMembersIdentity.add(encodedIdentity.decodeIdentity());
            }
        }

        @Override
        public int getProtocolMessageId() {
            return ADD_GROUP_MEMBERS_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            Encoded[] encodedMembers = new Encoded[newMembersIdentity.size()];
            int i=0;
            for (Identity identity: newMembersIdentity) {
                encodedMembers[i] = Encoded.of(identity);
                i++;
            }
            return new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(encodedMembers),
            };
        }
    }

    public static class RemoveGroupMembersMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final HashSet<Identity> removedMemberIdentities;

        public RemoveGroupMembersMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, HashSet<Identity> removedMemberIdentities) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.removedMemberIdentities = removedMemberIdentities;
        }

        @SuppressWarnings("unused")
        public RemoveGroupMembersMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.removedMemberIdentities = new HashSet<>();
            for (Encoded encodedIdentity: receivedMessage.getInputs()[1].decodeList()) {
                this.removedMemberIdentities.add(encodedIdentity.decodeIdentity());
            }
        }

        @Override
        public int getProtocolMessageId() {
            return REMOVE_GROUP_MEMBERS_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            Encoded[] encodedMembers = new Encoded[removedMemberIdentities.size()];
            int i=0;
            for (Identity identity: removedMemberIdentities) {
                encodedMembers[i] = Encoded.of(identity);
                i++;
            }
            return new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(encodedMembers),
            };
        }
    }

    public static class ReinvitePendingMemberMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final Identity pendingMemberIdentity;

        public ReinvitePendingMemberMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, Identity pendingMemberIdentity) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.pendingMemberIdentity = pendingMemberIdentity;
        }

        @SuppressWarnings("unused")
        public ReinvitePendingMemberMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.pendingMemberIdentity = receivedMessage.getInputs()[1].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return REINVITE_PENDING_MEMBER_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(pendingMemberIdentity),
            };
        }
    }


    public static class KickFromGroupMessage extends GroupInformationOnlyMessage {
        public KickFromGroupMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage, groupInformation);
        }

        @SuppressWarnings("unused")
        public KickFromGroupMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return KICK_FROM_GROUP_MESSAGE_ID;
        }
    }

    public static class DisbandGroupMessage extends GroupInformationOnlyMessage {
        public DisbandGroupMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage, groupInformation);
        }

        @SuppressWarnings("unused")
        public DisbandGroupMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return DISBAND_GROUP_MESSAGE_ID;
        }
    }

    public static class LeaveGroupMessage extends GroupInformationOnlyMessage {
        public LeaveGroupMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage, groupInformation);
        }

        @SuppressWarnings("unused")
        public LeaveGroupMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return LEAVE_GROUP_MESSAGE_ID;
        }
    }

    public static class NotifyGroupLeftMessage extends GroupInformationOnlyMessage {
        public NotifyGroupLeftMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage, groupInformation);
        }

        @SuppressWarnings("unused")
        public NotifyGroupLeftMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return NOTIFY_GROUP_LEFT_MESSAGE_ID;
        }
    }


    public static class InitiateGroupMembersQueryMessage extends GroupInformationOnlyMessage {
        public InitiateGroupMembersQueryMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage, groupInformation);
        }

        @SuppressWarnings("unused")
        public InitiateGroupMembersQueryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_GROUP_MEMBERS_QUERY_MESSAGE_ID;
        }
    }

    public static class QueryGroupMembersMessage extends GroupInformationOnlyMessage {
        public QueryGroupMembersMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage, groupInformation);
        }

        @SuppressWarnings("unused")
        public QueryGroupMembersMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return QUERY_GROUP_MEMBERS_MESSAGE_ID;
        }
    }

    public static class TriggerReinviteMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final Identity memberIdentity;

        public TriggerReinviteMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, Identity memberIdentity) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.memberIdentity = memberIdentity;
        }

        @SuppressWarnings("unused")
        public TriggerReinviteMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.memberIdentity = receivedMessage.getInputs()[1].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return TRIGGER_REINVITE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(memberIdentity),
            };
        }
    }


    public static class TriggerUpdateMembersMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;
        private final Identity memberIdentity;

        public TriggerUpdateMembersMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation, Identity memberIdentity) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
            this.memberIdentity = memberIdentity;
        }

        @SuppressWarnings("unused")
        public TriggerUpdateMembersMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
            this.memberIdentity = receivedMessage.getInputs()[1].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return TRIGGER_UPDATE_MEMBERS_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupInformation.encode(),
                    Encoded.of(memberIdentity),
            };
        }
    }

    public static class UploadGroupPhotoMessage extends ConcreteProtocolMessage {
        private final GroupInformation groupInformation;

        private UploadGroupPhotoMessage(CoreProtocolMessage coreProtocolMessage, GroupInformation groupInformation) {
            super(coreProtocolMessage);
            this.groupInformation = groupInformation;
        }

        @SuppressWarnings("unused")
        public UploadGroupPhotoMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() != null) {
                throw new Exception();
            }
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.groupInformation = GroupInformation.of(receivedMessage.getInputs()[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return UPLOAD_GROUP_PHOTO_MESSAGE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupInformation.encode(),
            };
        }
    }
    // endregion



    // region steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        if (stateId == INITIAL_STATE_ID) {
            return new Class[]{
                    InitiateGroupCreationStep.class,
                    NotifyMembersChangedStep.class,
                    ProcessNewMembersStep.class,
                    AddGroupMembersStep.class,
                    RemoveGroupMembersStep.class,
                    GetKickedStep.class,
                    ReinvitePendingMemberStep.class,
                    DisbandGroupStep.class,
                    LeaveGroupStep.class,
                    ProcessGroupLeftStep.class,
                    QueryGroupMembersStep.class,
                    SendGroupMembersStep.class,
                    ReinviteStep.class,
                    UpdateMembersStep.class,
            };
        }
        return new Class[0];
    }


    public static class InitiateGroupCreationStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final InitialProtocolState startState;
        private final InitiateGroupCreationMessage receivedMessage;

        public InitiateGroupCreationStep(InitialProtocolState startState, InitiateGroupCreationMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
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

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            GroupInformation groupInformation = receivedMessage.groupInformation;

            // Create the ContactGroup in database
            protocolManagerSession.identityDelegate.createContactGroup(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    groupInformation,
                    new Identity[0],
                    receivedMessage.groupMemberIdentitiesAndSerializedDetails.toArray(new IdentityWithSerializedDetails[0])
            );

            if (receivedMessage.absolutePhotoUrl != null && receivedMessage.absolutePhotoUrl.length() > 0) {
                try {
                    protocolManagerSession.identityDelegate.updateOwnedGroupPhoto(protocolManagerSession.session, getOwnedIdentity(), groupInformation.getGroupOwnerAndUid(), receivedMessage.absolutePhotoUrl, true);

                    JsonGroupDetailsWithVersionAndPhoto publishedDetails = protocolManagerSession.identityDelegate.getGroupPublishedAndLatestOrTrustedDetails(protocolManagerSession.session, getOwnedIdentity(), groupInformation.getGroupOwnerAndUid())[0];

                    // create what is needed to start the photo upload
                    UID photoServerLabel = new UID(getPrng());
                    AuthEnc authEnc = Suite.getDefaultAuthEnc(0);
                    AuthEncKey photoServerKey = authEnc.generateKey(getPrng());

                    publishedDetails.setPhotoServerKey(Encoded.of(photoServerKey).getBytes());
                    publishedDetails.setPhotoServerLabel(photoServerLabel.getBytes());

                    String serializedGroupDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().writeValueAsString(publishedDetails);

                    groupInformation = new GroupInformation(groupInformation.groupOwnerIdentity, groupInformation.groupUid, serializedGroupDetailsWithVersionAndPhoto);

                    // store the label and key in the details
                    protocolManagerSession.identityDelegate.setOwnedGroupDetailsServerLabelAndKey(protocolManagerSession.session, getOwnedIdentity(), groupInformation.getGroupOwnerAndUid(), publishedDetails.getVersion(), photoServerLabel, photoServerKey);

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createPutUserDataQuery(getOwnedIdentity(), photoServerLabel, publishedDetails.getPhotoUrl(), photoServerKey)));
                    ChannelMessageToSend messageToSend = new UploadGroupPhotoMessage(coreProtocolMessage, groupInformation).generateChannelServerQueryMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                } catch (Exception e) {
                    // an error occurred with the photo, this should not prevent group creation, so we do nothing
                }
            }

            {
                // Propagate the group creation to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateGroupCreationMessage(coreProtocolMessage, groupInformation, receivedMessage.groupMemberIdentitiesAndSerializedDetails).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }


            {
                // post an invitation to each group member by starting a child GroupInvitationProtocol
                for (IdentityWithSerializedDetails identityWithSerializedDetails : receivedMessage.groupMemberIdentitiesAndSerializedDetails) {
                    UID childProtocolInstanceUid = new UID(getPrng());
                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                            GROUP_INVITATION_PROTOCOL_ID,
                            childProtocolInstanceUid,
                            false
                    );
                    ChannelMessageToSend messageToSend = new GroupInvitationProtocol.InitialMessage(coreProtocolMessage, identityWithSerializedDetails.identity, groupInformation, receivedMessage.groupMemberIdentitiesAndSerializedDetails).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            return new FinalState();
        }
    }

    public static class NotifyMembersChangedStep extends ProtocolStep {
        @SuppressWarnings("unused")
        private final InitialProtocolState startState;
        private final GroupInformation groupInformation;

        @SuppressWarnings("unused")
        public NotifyMembersChangedStep(InitialProtocolState startState, GroupMembersOrDetailsChangedTriggerMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupInformation = receivedMessage.groupInformation;
        }

        @SuppressWarnings("unused")
        public NotifyMembersChangedStep(InitialProtocolState startState, UploadGroupPhotoMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupInformation = receivedMessage.groupInformation;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: the groupInformation contains a different Identity than ownedIdentity");
                return null;
            }

            if (!groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            JsonGroupDetailsWithVersionAndPhoto groupInfoDetails = protocol.getJsonObjectMapper().readValue(groupInformation.serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);
            JsonGroupDetailsWithVersionAndPhoto publishedDetails = protocolManagerSession.identityDelegate.getGroupPublishedAndLatestOrTrustedDetails(protocolManagerSession.session, getOwnedIdentity(), groupInformation.getGroupOwnerAndUid())[0];

            if (publishedDetails.getVersion() == groupInfoDetails.getVersion() && publishedDetails.getPhotoUrl() != null && (publishedDetails.getPhotoServerLabel() == null || publishedDetails.getPhotoServerKey() == null)) {
                // we need to upload a photo
                UID photoServerLabel = new UID(getPrng());
                AuthEnc authEnc = Suite.getDefaultAuthEnc(0);
                AuthEncKey photoServerKey = authEnc.generateKey(getPrng());

                publishedDetails.setPhotoServerKey(Encoded.of(photoServerKey).getBytes());
                publishedDetails.setPhotoServerLabel(photoServerLabel.getBytes());

                String serializedGroupDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().writeValueAsString(publishedDetails);

                GroupInformation groupInformationWithKeyAndLabel = new GroupInformation(groupInformation.groupOwnerIdentity, groupInformation.groupUid, serializedGroupDetailsWithVersionAndPhoto);

                // store the label and key in the details
                protocolManagerSession.identityDelegate.setOwnedGroupDetailsServerLabelAndKey(protocolManagerSession.session, getOwnedIdentity(), groupInformation.getGroupOwnerAndUid(), publishedDetails.getVersion(), photoServerLabel, photoServerKey);

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createPutUserDataQuery(getOwnedIdentity(), photoServerLabel, publishedDetails.getPhotoUrl(), photoServerKey)));
                ChannelMessageToSend messageToSend = new UploadGroupPhotoMessage(coreProtocolMessage, groupInformationWithKeyAndLabel).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new FinalState();
            }

            // get the group members
            Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), groupInformation.getGroupOwnerAndUid());

            HashSet<IdentityWithSerializedDetails> groupMembers = new HashSet<>();
            HashSet<IdentityWithSerializedDetails> pendingMembers = new HashSet<>(Arrays.asList(group.getPendingGroupMembers()));

            for (Identity memberIdentity: group.getGroupMembers()) {
                groupMembers.add(new IdentityWithSerializedDetails(memberIdentity, protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), memberIdentity)));
            }

            // also add yourself (group owner) to the group
            groupMembers.add(new IdentityWithSerializedDetails(getOwnedIdentity(), protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity())));

            {
                if (group.getGroupMembers().length > 0) {
                    // notify all group members (not the pending group members) with a single message
                    SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(group.getGroupMembers(), getOwnedIdentity());
                    for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(sendChannelInfo);
                        ChannelMessageToSend messageToSend = new NewMembersMessage(coreProtocolMessage, groupInformation, groupMembers, pendingMembers, group.getGroupMembersVersion()).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }
                }
            }

            return new FinalState();
        }
    }

    public static class ProcessNewMembersStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final NewMembersMessage receivedMessage;

        public ProcessNewMembersStep(InitialProtocolState startState, NewMembersMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(receivedMessage.getReceptionChannelInfo().getRemoteIdentity())) {
                Logger.w("Error: NewMembersMessage not received from the group owner");
                return null;
            }

            // get the group
            Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());

            if (group == null) {
                return null;
            }

            if (group.getGroupOwner() == null) {
                Logger.w("Error: received a NewMembersMessage for a group you own");
                return null;
            }

            if (group.getGroupMembersVersion() > receivedMessage.membersVersion) {
                // we already have a more recent members version --> do nothing
                return new FinalState();
            }

            {
                // check if a group photo need to be downloaded
                JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.groupInformation.serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);

                if (jsonGroupDetailsWithVersionAndPhoto.getPhotoServerLabel() != null && jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey() != null) {

                    JsonGroupDetailsWithVersionAndPhoto publishedDetails = protocolManagerSession.identityDelegate.getGroupPublishedAndLatestOrTrustedDetails(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid())[0];

                    if (! (Arrays.equals(jsonGroupDetailsWithVersionAndPhoto.getPhotoServerLabel(), publishedDetails.getPhotoServerLabel()) &&
                            ((jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey() == null && publishedDetails.getPhotoServerKey() == null) ||
                                    (jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey() != null && publishedDetails.getPhotoServerKey() != null && new Encoded(jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey()).decodeSymmetricKey().equals(new Encoded(publishedDetails.getPhotoServerKey()).decodeSymmetricKey()))) &&
                            publishedDetails.getPhotoUrl() != null)) {
                        // we need to download the photo, so we start a child protocol

                        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                DOWNLOAD_GROUP_PHOTO_CHILD_PROTOCOL_ID,
                                new UID(getPrng()),
                                false
                        );
                        ChannelMessageToSend messageToSend = new DownloadGroupPhotoChildProtocol.InitialMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }
                }
            }

            {
                // update group details and members version
                protocolManagerSession.identityDelegate.updateGroupMembersAndDetails(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation, receivedMessage.groupMemberIdentitiesAndSerializedDetails, receivedMessage.pendingMemberIdentitiesAndSerializedDetails, receivedMessage.membersVersion);
            }

            return new FinalState();
        }
    }

    public static class AddGroupMembersStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final AddGroupMembersMessage receivedMessage;

        public AddGroupMembersStep(InitialProtocolState startState, AddGroupMembersMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: the groupInformation contains a different Identity than ownedIdentity");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }


            {
                // add pending members to the group and notify existing members (in the callback)
                GroupMembersChangedCallback groupMembersChangedCallback = () -> {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new GroupMembersOrDetailsChangedTriggerMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                };

                protocolManagerSession.identityDelegate.addPendingMembersToGroup(protocolManagerSession.session, receivedMessage.groupInformation.getGroupOwnerAndUid(), getOwnedIdentity(), receivedMessage.newMembersIdentity.toArray(new Identity[0]), groupMembersChangedCallback);
            }

            {
                // post invitations to the new members
                Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());
                if (group == null) {
                    throw new Exception();
                }

                HashSet<IdentityWithSerializedDetails> allGroupMembers = new HashSet<>(Arrays.asList(group.getPendingGroupMembers()));
                for (Identity identity: group.getGroupMembers()) {
                    allGroupMembers.add(new IdentityWithSerializedDetails(identity, protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), identity)));
                }

                for (Identity contactIdentity : receivedMessage.newMembersIdentity) {
                    UID childProtocolInstanceUid = new UID(getPrng());
                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                            GROUP_INVITATION_PROTOCOL_ID,
                            childProtocolInstanceUid,
                            false
                    );
                    ChannelMessageToSend messageToSend = new GroupInvitationProtocol.InitialMessage(coreProtocolMessage, contactIdentity, receivedMessage.groupInformation, allGroupMembers).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            return new FinalState();
        }
    }

    public static class RemoveGroupMembersStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final RemoveGroupMembersMessage receivedMessage;

        public RemoveGroupMembersStep(InitialProtocolState startState, RemoveGroupMembersMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: the groupInformation contains a different Identity than ownedIdentity");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }


            {
                // remove members from the group and notify remaining members (in the callback)
                GroupMembersChangedCallback groupMembersChangedCallback = () -> {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new GroupMembersOrDetailsChangedTriggerMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                };

                protocolManagerSession.identityDelegate.removeMembersAndPendingFromGroup(protocolManagerSession.session, receivedMessage.groupInformation.getGroupOwnerAndUid(), getOwnedIdentity(), receivedMessage.removedMemberIdentities.toArray(new Identity[0]), groupMembersChangedCallback);
            }

            {
                // notify members that have been kicked
                Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());
                if (group == null) {
                    throw new Exception();
                }

                for (Identity contactIdentity : receivedMessage.removedMemberIdentities) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(contactIdentity, getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new KickFromGroupMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (Exception e) {
                        // after a contact delete this might fail as there are no channels with the deleted contact --> proceed
                    }
                }
            }

            return new FinalState();
        }
    }

    public static class GetKickedStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final KickFromGroupMessage receivedMessage;

        public GetKickedStep(InitialProtocolState startState, KickFromGroupMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();
            protocol.eraseReceivedMessagesAfterReachingAFinalState = true;

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(receivedMessage.getReceptionChannelInfo().getRemoteIdentity())) {
                Logger.w("Error: NewMembersMessage not received from the group owner");
                return null;
            }

            {
                // If the group exists, leave it
                Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());
                if (group != null) {
                    // simply delete the group on the engine side, everything will follow!
                    protocolManagerSession.identityDelegate.leaveGroup(protocolManagerSession.session, receivedMessage.groupInformation.getGroupOwnerAndUid(), getOwnedIdentity());
                }
            }

            return new FinalState();
        }
    }


    public static class ReinvitePendingMemberStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final ReinvitePendingMemberMessage receivedMessage;

        public ReinvitePendingMemberStep(InitialProtocolState startState, ReinvitePendingMemberMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: the groupInformation contains a different Identity than ownedIdentity");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            {
                // mark the pending member as "not declined"
                protocolManagerSession.identityDelegate.setPendingMemberDeclined(protocolManagerSession.session, receivedMessage.groupInformation.getGroupOwnerAndUid(), getOwnedIdentity(), receivedMessage.pendingMemberIdentity, false);
            }

            {
                // resend an invitation
                Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());
                if (group == null) {
                    throw new Exception();
                }

                HashSet<IdentityWithSerializedDetails> allGroupMembers = new HashSet<>(Arrays.asList(group.getPendingGroupMembers()));
                for (Identity identity: group.getGroupMembers()) {
                    allGroupMembers.add(new IdentityWithSerializedDetails(identity, protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), identity)));
                }

                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        GROUP_INVITATION_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new GroupInvitationProtocol.InitialMessage(coreProtocolMessage, receivedMessage.pendingMemberIdentity, receivedMessage.groupInformation, allGroupMembers).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinalState();
        }
    }

    public static class DisbandGroupStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final DisbandGroupMessage receivedMessage;

        public DisbandGroupStep(InitialProtocolState startState, DisbandGroupMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: the groupInformation contains a different Identity than ownedIdentity");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }


            {
                // send all members and pending members of the group a KickFromGroupMessage
                Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());
                if (group == null) {
                    throw new Exception();
                }

                if (group.getGroupMembers().length > 0) {
                    SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(group.getGroupMembers(), getOwnedIdentity());
                    for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(sendChannelInfo);
                        ChannelMessageToSend messageToSend = new KickFromGroupMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }
                }

                if (group.getPendingGroupMembers().length > 0) {
                    Identity[] pendingMemberIdentities = new Identity[group.getPendingGroupMembers().length];
                    for (int i = 0; i < pendingMemberIdentities.length; i++) {
                        pendingMemberIdentities[i] = group.getPendingGroupMembers()[i].identity;
                    }

                    SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(pendingMemberIdentities, getOwnedIdentity());
                    for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(sendChannelInfo);
                        ChannelMessageToSend messageToSend = new KickFromGroupMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }
                }
            }

            {
                // delete the group
                protocolManagerSession.identityDelegate.deleteGroup(protocolManagerSession.session, receivedMessage.groupInformation.getGroupOwnerAndUid(), getOwnedIdentity());
            }

            return new FinalState();
        }
    }

    public static class LeaveGroupStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final LeaveGroupMessage receivedMessage;

        public LeaveGroupStep(InitialProtocolState startState, LeaveGroupMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: cannot leave a group you own");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            try {
                // notify the group owner
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.groupInformation.groupOwnerIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new NotifyGroupLeftMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            } catch (Exception e) {
                Logger.w("LeaveGroupStep: Error notifying group owner. Probably no channel with him.");
            }

            {
                // simply delete the group on the engine side, everything will follow!
                protocolManagerSession.identityDelegate.leaveGroup(protocolManagerSession.session, receivedMessage.groupInformation.getGroupOwnerAndUid(), getOwnedIdentity());
            }
            return new FinalState();
        }
    }

    public static class ProcessGroupLeftStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final NotifyGroupLeftMessage receivedMessage;

        public ProcessGroupLeftStep(InitialProtocolState startState, NotifyGroupLeftMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: you are not the group owner");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            {
                // remove members from the group and notify remaining members (in the callback)
                GroupMembersChangedCallback groupMembersChangedCallback = () -> {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new GroupMembersOrDetailsChangedTriggerMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                };

                protocolManagerSession.identityDelegate.removeMembersAndPendingFromGroup(protocolManagerSession.session, receivedMessage.groupInformation.getGroupOwnerAndUid(), getOwnedIdentity(), new Identity[]{receivedMessage.getReceptionChannelInfo().getRemoteIdentity()}, groupMembersChangedCallback);
            }
            return new FinalState();
        }
    }


    public static class QueryGroupMembersStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitiateGroupMembersQueryMessage receivedMessage;

        public QueryGroupMembersStep(InitialProtocolState startState, InitiateGroupMembersQueryMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: you are the group owner");
                return null;
            }

            {
                // send query members message to group owner
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.groupInformation.groupOwnerIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new QueryGroupMembersMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinalState();
        }
    }


    public static class SendGroupMembersStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final QueryGroupMembersMessage receivedMessage;

        public SendGroupMembersStep(InitialProtocolState startState, QueryGroupMembersMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: you are not the group owner");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }

            Identity contactIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();

            // get the group members
            Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());

            if (group != null && group.isPendingMember(contactIdentity)) {
                // if we receive a query from someone who is pending we do nothing, it's probably because we not yet received his "accept" invitation message
                return new FinalState();
            }

            if (group == null || !group.isMember(contactIdentity)) {
                // group not found or member not in the group --> kick him
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new KickFromGroupMessage(coreProtocolMessage, receivedMessage.groupInformation).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                return new FinalState();
            }

            HashSet<IdentityWithSerializedDetails> groupMembers = new HashSet<>();
            HashSet<IdentityWithSerializedDetails> pendingMembers = new HashSet<>(Arrays.asList(group.getPendingGroupMembers()));

            for (Identity memberIdentity: group.getGroupMembers()) {
                groupMembers.add(new IdentityWithSerializedDetails(memberIdentity, protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), memberIdentity)));
            }

            // also add yourself (group owner) to the group
            groupMembers.add(new IdentityWithSerializedDetails(getOwnedIdentity(), protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity())));

            {
                // send group members to receivedMessage sender
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new NewMembersMessage(
                        coreProtocolMessage,
                        protocolManagerSession.identityDelegate.getGroupInformation(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid()),
                        groupMembers,
                        pendingMembers,
                        group.getGroupMembersVersion()).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }
            return new FinalState();
        }
    }


    public static class ReinviteStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final TriggerReinviteMessage receivedMessage;

        public ReinviteStep(InitialProtocolState startState, TriggerReinviteMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.memberIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: trying to reinvite yourself to a group");
                return null;
            }

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: you are not the group owner");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }


            // get the group members
            Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());

            if (group == null) {
                Logger.w("Error: group not found");
                return null;
            }

            if (!group.isMember(receivedMessage.memberIdentity) && !group.isPendingMember(receivedMessage.memberIdentity)) {
                Logger.w("Error in ReinviteStep: member is neither member, nor pending");
                return null;
            }

            {
                HashSet<IdentityWithSerializedDetails> allGroupMembers = new HashSet<>(Arrays.asList(group.getPendingGroupMembers()));
                for (Identity identity : group.getGroupMembers()) {
                    allGroupMembers.add(new IdentityWithSerializedDetails(identity, protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), identity)));
                }

                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        GROUP_INVITATION_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new GroupInvitationProtocol.InitialMessage(coreProtocolMessage, receivedMessage.memberIdentity, receivedMessage.groupInformation, allGroupMembers).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinalState();
        }
    }


    public static class UpdateMembersStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final TriggerUpdateMembersMessage receivedMessage;

        public UpdateMembersStep(InitialProtocolState startState, TriggerUpdateMembersMessage receivedMessage, GroupManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            final ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.memberIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: trying to reinvite yourself to a group");
                return null;
            }

            if (!receivedMessage.groupInformation.groupOwnerIdentity.equals(getOwnedIdentity())) {
                Logger.w("Error: you are not the group owner");
                return null;
            }

            if (!receivedMessage.groupInformation.computeProtocolUid().equals(protocol.getProtocolInstanceUid())) {
                Logger.w("Error: protocolUid mismatch");
                return null;
            }


            // get the group members
            Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupInformation.getGroupOwnerAndUid());

            if (group == null) {
                Logger.w("Error: group not found");
                return null;
            }

            if (!group.isMember(receivedMessage.memberIdentity)) {
                Logger.w("Error in UpdateMembersStep: contact is not member");
                return null;
            }

            {
                HashSet<IdentityWithSerializedDetails> groupMembers = new HashSet<>();
                HashSet<IdentityWithSerializedDetails> pendingMembers = new HashSet<>(Arrays.asList(group.getPendingGroupMembers()));

                for (Identity memberIdentity : group.getGroupMembers()) {
                    groupMembers.add(new IdentityWithSerializedDetails(memberIdentity, protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), memberIdentity)));
                }

                // also add yourself (group owner) to the group
                groupMembers.add(new IdentityWithSerializedDetails(getOwnedIdentity(), protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity())));


                // send group members to memberIdentity (in the receivedMessage)
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.memberIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new NewMembersMessage(coreProtocolMessage, receivedMessage.groupInformation, groupMembers, pendingMembers, group.getGroupMembersVersion()).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinalState();
        }
    }

    // endregion
}
