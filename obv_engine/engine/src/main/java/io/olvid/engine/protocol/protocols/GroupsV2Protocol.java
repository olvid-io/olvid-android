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

import java.io.File;
import java.security.InvalidKeyException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.KDF;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.crypto.exceptions.DecryptionException;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.asymmetric.KeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.ProtocolNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.protocol.databases.GroupV2SignatureReceived;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.EmptyProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.OneWayDialogProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class GroupsV2Protocol extends ConcreteProtocol {
    public GroupsV2Protocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
        eraseReceivedMessagesAfterReachingAFinalState = false;
    }

    @Override
    public int getProtocolId() {
        return GROUPS_V2_PROTOCOL_ID;
    }

    // region States

    private static final int UPLOADING_CREATED_GROUP_DATA_STATE_ID = 1; // frozen
    private static final int DOWNLOADING_GROUP_BLOB_STATE_ID = 2; // frozen
    private static final int I_NEED_MORE_SEEDS_STATE_ID = 3; // frozen
    private static final int INVITATION_RECEIVED_STATE_ID = 4;
    private static final int REJECTING_INVITATION_OR_LEAVING_GROUP_STATE_ID = 5;
    private static final int WAITING_FOR_LOCK_STATE_ID = 6; // frozen
    private static final int UPLOADING_UPDATED_GROUP_BLOB_STATE_ID = 7; // frozen
    private static final int UPLOADING_UPDATED_GROUP_PHOTO_STATE_ID = 8; // frozen
    private static final int DISBANDING_GROUP_STATE_ID = 9; // frozen

    private static final int FINAL_STATE_ID = 99;

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case UPLOADING_CREATED_GROUP_DATA_STATE_ID:
                return UploadingCreatedGroupDataState.class;
            case DOWNLOADING_GROUP_BLOB_STATE_ID:
                return DownloadingGroupBlobState.class;
            case I_NEED_MORE_SEEDS_STATE_ID:
                return INeedMoreSeedsState.class;
            case INVITATION_RECEIVED_STATE_ID:
                return InvitationReceivedState.class;
            case REJECTING_INVITATION_OR_LEAVING_GROUP_STATE_ID:
                return RejectingInvitationOrLeavingGroupState.class;
            case WAITING_FOR_LOCK_STATE_ID:
                return WaitingForLockState.class;
            case UPLOADING_UPDATED_GROUP_BLOB_STATE_ID:
                return UploadingUpdatedGroupBlobState.class;
            case UPLOADING_UPDATED_GROUP_PHOTO_STATE_ID:
                return UploadingUpdatedGroupPhotoState.class;
            case DISBANDING_GROUP_STATE_ID:
                return DisbandingGroupState.class;
            case FINAL_STATE_ID:
                return FinalState.class;
            default:
                return null;
        }
    }

    @Override
    public int[] getFinalStateIds() {
        return new int[]{FINAL_STATE_ID};
    }

    public static class UploadingCreatedGroupDataState extends ConcreteProtocolState {
        private final GroupV2.Identifier groupIdentifier;
        private final int groupVersion;
        private final boolean waitingForBlobUpload;
        private final boolean waitingForPhotoUpload;

        public UploadingCreatedGroupDataState(GroupV2.Identifier groupIdentifier, int groupVersion, boolean waitingForBlobUpload, boolean waitingForPhotoUpload) {
            super(UPLOADING_CREATED_GROUP_DATA_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.groupVersion = groupVersion;
            this.waitingForBlobUpload = waitingForBlobUpload;
            this.waitingForPhotoUpload = waitingForPhotoUpload;
        }

        @SuppressWarnings("unused")
        public UploadingCreatedGroupDataState(Encoded encodedState) throws Exception {
            super(UPLOADING_CREATED_GROUP_DATA_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.groupVersion = (int) list[1].decodeLong();
            this.waitingForBlobUpload = list[2].decodeBoolean();
            this.waitingForPhotoUpload = list[3].decodeBoolean();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(groupVersion),
                    Encoded.of(waitingForBlobUpload),
                    Encoded.of(waitingForPhotoUpload),
            });
        }
    }

    public static abstract class CollectingSeedsAbstractState extends ConcreteProtocolState {
        protected final GroupV2.Identifier groupIdentifier;
        protected final UUID dialogUuid;
        protected final GroupV2.InvitationCollectedData invitationCollectedData;
        protected final byte[] lastKnownOwnInvitationNonce;
        protected final Identity[] lastKnownOtherGroupMemberIdentities;

        protected CollectingSeedsAbstractState(int stateId, GroupV2.Identifier groupIdentifier, UUID dialogUuid, GroupV2.InvitationCollectedData invitationCollectedData, byte[] lastKnownOwnInvitationNonce, Identity[] lastKnownOtherGroupMemberIdentities) {
            super(stateId);
            this.groupIdentifier = groupIdentifier;
            this.invitationCollectedData = invitationCollectedData;
            this.dialogUuid = dialogUuid;
            this.lastKnownOwnInvitationNonce = lastKnownOwnInvitationNonce;
            this.lastKnownOtherGroupMemberIdentities = lastKnownOtherGroupMemberIdentities;
        }

        @SuppressWarnings("unused")
        public CollectingSeedsAbstractState(int stateId, Encoded encodedState) throws Exception {
            super(stateId);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 3 && list.length != 5) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.dialogUuid = list[1].decodeUuid();
            this.invitationCollectedData = GroupV2.InvitationCollectedData.of(list[2]);
            this.lastKnownOwnInvitationNonce = list.length == 5 ? list[3].decodeBytes() : null;
            this.lastKnownOtherGroupMemberIdentities = list.length == 5 ? list[4].decodeIdentityArray() : null;
        }

        @Override
        public Encoded encode() {
            if (lastKnownOwnInvitationNonce == null || lastKnownOtherGroupMemberIdentities == null) {
                return Encoded.of(new Encoded[]{
                        groupIdentifier.encode(),
                        Encoded.of(dialogUuid),
                        invitationCollectedData.encode(),
                });
            } else {
                return Encoded.of(new Encoded[]{
                        groupIdentifier.encode(),
                        Encoded.of(dialogUuid),
                        invitationCollectedData.encode(),
                        Encoded.of(lastKnownOwnInvitationNonce),
                        Encoded.of(lastKnownOtherGroupMemberIdentities),
                });
            }
        }
    }

    public static class DownloadingGroupBlobState extends CollectingSeedsAbstractState {
        private final byte[] serverQueryNonce;

        protected DownloadingGroupBlobState(GroupV2.Identifier groupIdentifier, UUID dialogUuid, GroupV2.InvitationCollectedData invitationCollectedData, byte[] lastKnownOwnInvitationNonce, Identity[] lastKnownOtherGroupMemberIdentities, byte[] serverQueryNonce) {
            super(DOWNLOADING_GROUP_BLOB_STATE_ID, groupIdentifier, dialogUuid, invitationCollectedData, lastKnownOwnInvitationNonce, lastKnownOtherGroupMemberIdentities);
            this.serverQueryNonce = serverQueryNonce;
        }

        @SuppressWarnings("unused")
        public DownloadingGroupBlobState(Encoded encodedState) throws Exception {
            super(DOWNLOADING_GROUP_BLOB_STATE_ID, encodedState.decodeList()[0]);
            serverQueryNonce = encodedState.decodeList()[1].decodeBytes();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    super.encode(),
                    Encoded.of(serverQueryNonce),
            });
        }
    }

    public static class INeedMoreSeedsState extends CollectingSeedsAbstractState {
        protected INeedMoreSeedsState(GroupV2.Identifier groupIdentifier, UUID dialogUuid, GroupV2.InvitationCollectedData invitationCollectedData, byte[] lastKnownOwnInvitationNonce, Identity[] lastKnownOtherGroupMemberIdentities) {
            super(I_NEED_MORE_SEEDS_STATE_ID, groupIdentifier, dialogUuid, invitationCollectedData, lastKnownOwnInvitationNonce, lastKnownOtherGroupMemberIdentities);
        }

        @SuppressWarnings("unused")
        public INeedMoreSeedsState(Encoded encodedState) throws Exception {
            super(I_NEED_MORE_SEEDS_STATE_ID, encodedState);
        }
    }

    public static class InvitationReceivedState extends ConcreteProtocolState {
        private final GroupV2.Identifier groupIdentifier;
        private final UUID dialogUuid;
        private final Identity inviterIdentity;
        private final GroupV2.ServerBlob serverBlob;
        private final GroupV2.BlobKeys blobKeys;


        public InvitationReceivedState(GroupV2.Identifier groupIdentifier, UUID dialogUuid, Identity inviterIdentity, GroupV2.ServerBlob serverBlob, GroupV2.BlobKeys blobKeys) {
            super(INVITATION_RECEIVED_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.dialogUuid = dialogUuid;
            this.inviterIdentity = inviterIdentity;
            this.serverBlob = serverBlob;
            this.blobKeys = blobKeys;
        }

        @SuppressWarnings("unused")
        public InvitationReceivedState(Encoded encodedState) throws Exception {
            super(INVITATION_RECEIVED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 5) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.dialogUuid = list[1].decodeUuid();
            this.inviterIdentity = list[2].decodeIdentity();
            this.serverBlob = GroupV2.ServerBlob.of(list[3]);
            this.blobKeys = GroupV2.BlobKeys.of(list[4]);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(dialogUuid),
                    Encoded.of(inviterIdentity),
                    serverBlob.encode(),
                    blobKeys.encode(),
            });
        }
    }

    public static class RejectingInvitationOrLeavingGroupState extends ConcreteProtocolState {
        private final GroupV2.Identifier groupIdentifier;
        private final List<Identity> groupMembersToNotify;

        public RejectingInvitationOrLeavingGroupState(GroupV2.Identifier groupIdentifier, List<Identity> groupMembersToNotify) {
            super(REJECTING_INVITATION_OR_LEAVING_GROUP_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.groupMembersToNotify = groupMembersToNotify;
        }

        @SuppressWarnings("unused")
        public RejectingInvitationOrLeavingGroupState(Encoded encodedState) throws Exception {
            super(REJECTING_INVITATION_OR_LEAVING_GROUP_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.groupMembersToNotify = Arrays.asList(list[1].decodeIdentityArray());
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(groupMembersToNotify.toArray(new Identity[0])),
            });
        }
    }


    public static class WaitingForLockState extends ConcreteProtocolState {
        private final GroupV2.Identifier groupIdentifier;
        private final ObvGroupV2.ObvGroupV2ChangeSet changeSet;
        private final byte[] lockNonce;
        private final long failedUploadCounter;

        public WaitingForLockState(GroupV2.Identifier groupIdentifier, ObvGroupV2.ObvGroupV2ChangeSet changeSet, byte[] lockNonce, long failedUploadCounter) {
            super(WAITING_FOR_LOCK_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.changeSet = changeSet;
            this.lockNonce = lockNonce;
            this.failedUploadCounter = failedUploadCounter;
        }

        @SuppressWarnings("unused")
        public WaitingForLockState(Encoded encodedState) throws Exception {
            super(WAITING_FOR_LOCK_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.changeSet = ObvGroupV2.ObvGroupV2ChangeSet.of(list[1]);
            this.lockNonce = list[2].decodeBytes();
            this.failedUploadCounter = list[3].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    changeSet.encode(),
                    Encoded.of(lockNonce),
                    Encoded.of(failedUploadCounter),
            });
        }
    }

    public static Encoded encodeMembersToKick(HashMap<Identity, byte[]> membersToKick) {
        Encoded[] encodeds = new Encoded[2 * membersToKick.size()];
        int i = 0;
        for (Map.Entry<Identity, byte[]> entry : membersToKick.entrySet()) {
            encodeds[i] = Encoded.of(entry.getKey());
            encodeds[i+1] = Encoded.of(entry.getValue());
            i += 2;
        }
        return Encoded.of(encodeds);
    }

    public static HashMap<Identity, byte[]> decodeMembersToKick(Encoded encoded) throws DecodingException {
        HashMap<Identity, byte[]> membersToKick = new HashMap<>();
        Encoded[] encodeds = encoded.decodeList();
        for (int i = 0; i < encodeds.length; i += 2) {
            membersToKick.put(encodeds[i].decodeIdentity(), encodeds[i+1].decodeBytes());
        }
        return membersToKick;
    }

    public static class UploadingUpdatedGroupBlobState extends ConcreteProtocolState {
        private final GroupV2.Identifier groupIdentifier;
        private final ObvGroupV2.ObvGroupV2ChangeSet changeSet;
        private final GroupV2.ServerBlob updatedBlob;
        private final GroupV2.BlobKeys updatedBlobKeys;
        private final HashMap<Identity, byte[]> membersToKick;
        private final String absolutePhotoUrlToUpload;
        private final long failedUploadCounter;

        public UploadingUpdatedGroupBlobState(GroupV2.Identifier groupIdentifier, ObvGroupV2.ObvGroupV2ChangeSet changeSet, GroupV2.ServerBlob updatedBlob, GroupV2.BlobKeys updatedBlobKeys, HashMap<Identity, byte[]> membersToKick, String absolutePhotoUrlToUpload, long failedUploadCounter) {
            super(UPLOADING_UPDATED_GROUP_BLOB_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.changeSet = changeSet;
            this.updatedBlob = updatedBlob;
            this.updatedBlobKeys = updatedBlobKeys;
            this.membersToKick = membersToKick;
            this.absolutePhotoUrlToUpload = absolutePhotoUrlToUpload;
            this.failedUploadCounter = failedUploadCounter;
        }


        @SuppressWarnings("unused")
        public UploadingUpdatedGroupBlobState(Encoded encodedState) throws Exception {
            super(UPLOADING_UPDATED_GROUP_BLOB_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 7) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.changeSet = ObvGroupV2.ObvGroupV2ChangeSet.of(list[1]);
            this.updatedBlob = GroupV2.ServerBlob.of(list[2]);
            this.updatedBlobKeys = GroupV2.BlobKeys.of(list[3]);
            this.membersToKick = decodeMembersToKick(list[4]);
            String decoded = list[5].decodeString();
            this.absolutePhotoUrlToUpload = decoded.length() == 0 ? null : decoded;
            this.failedUploadCounter = list[6].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    changeSet.encode(),
                    updatedBlob.encode(),
                    updatedBlobKeys.encode(),
                    encodeMembersToKick(membersToKick),
                    Encoded.of(absolutePhotoUrlToUpload == null ? "" : absolutePhotoUrlToUpload),
                    Encoded.of(failedUploadCounter),
            });
        }
    }

    public static class UploadingUpdatedGroupPhotoState extends ConcreteProtocolState {
        private final GroupV2.Identifier groupIdentifier;
        private final ObvGroupV2.ObvGroupV2ChangeSet changeSet;
        private final GroupV2.ServerBlob updatedBlob;
        private final GroupV2.BlobKeys updatedBlobKeys;
        private final HashMap<Identity, byte[]> membersToKick;
        private final String absolutePhotoUrlToUpload;

        public UploadingUpdatedGroupPhotoState(GroupV2.Identifier groupIdentifier, ObvGroupV2.ObvGroupV2ChangeSet changeSet, GroupV2.ServerBlob updatedBlob, GroupV2.BlobKeys updatedBlobKeys, HashMap<Identity, byte[]> membersToKick, String absolutePhotoUrlToUpload) {
            super(UPLOADING_UPDATED_GROUP_PHOTO_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.changeSet = changeSet;
            this.updatedBlob = updatedBlob;
            this.updatedBlobKeys = updatedBlobKeys;
            this.membersToKick = membersToKick;
            this.absolutePhotoUrlToUpload = absolutePhotoUrlToUpload;
        }


        @SuppressWarnings("unused")
        public UploadingUpdatedGroupPhotoState(Encoded encodedState) throws Exception {
            super(UPLOADING_UPDATED_GROUP_PHOTO_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 6) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.changeSet = ObvGroupV2.ObvGroupV2ChangeSet.of(list[1]);
            this.updatedBlob = GroupV2.ServerBlob.of(list[2]);
            this.updatedBlobKeys = GroupV2.BlobKeys.of(list[3]);
            this.membersToKick = decodeMembersToKick(list[4]);
            String decoded = list[5].decodeString();
            this.absolutePhotoUrlToUpload = decoded.length() == 0 ? null : decoded;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    changeSet.encode(),
                    updatedBlob.encode(),
                    updatedBlobKeys.encode(),
                    encodeMembersToKick(membersToKick),
                    Encoded.of(absolutePhotoUrlToUpload == null ? "" : absolutePhotoUrlToUpload),
            });
        }
    }

    public static class DisbandingGroupState extends ConcreteProtocolState {
        private final GroupV2.Identifier groupIdentifier;
        private final GroupV2.BlobKeys blobKeys;

        public DisbandingGroupState(GroupV2.Identifier groupIdentifier, GroupV2.BlobKeys blobKeys) {
            super(DISBANDING_GROUP_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.blobKeys = blobKeys;
        }

        @SuppressWarnings("unused")
        public DisbandingGroupState(Encoded encodedState) throws Exception {
            super(DISBANDING_GROUP_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.blobKeys = GroupV2.BlobKeys.of(list[1]);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    blobKeys.encode(),
            });
        }
    }


    public static class FinalState extends ConcreteProtocolState {
        protected FinalState() {
            super(FINAL_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion







    // region Messages

    private static final int GROUP_CREATION_INITIAL_MESSAGE_ID = 0;
    private static final int UPLOAD_GROUP_PHOTO_MESSAGE_ID = 1;
    private static final int UPLOAD_GROUP_BLOB_MESSAGE_ID = 2;
    private static final int FINALIZE_GROUP_CREATION_MESSAGE_ID = 3;
    private static final int INVITATION_OR_MEMBERS_UPDATE_MESSAGE_ID = 4;
    private static final int INVITATION_OR_MEMBERS_UPDATE_BROADCAST_MESSAGE_ID = 5;
    private static final int INVITATION_OR_MEMBERS_UPDATE_PROPAGATED_MESSAGE_ID = 6;
    private static final int DOWNLOAD_GROUP_BLOB_MESSAGE_ID = 7;
    private static final int FINALIZE_GROUP_UPDATE_MESSAGE_ID = 8;
    private static final int DELETE_GROUP_BLOB_FROM_SERVER_MESSAGE_ID = 9;
    private static final int DIALOG_ACCEPT_GROUP_INVITATION_MESSAGE_ID = 10;
    private static final int PING_MESSAGE_ID = 11;
    private static final int PROPAGATED_PING_MESSAGE_ID = 12;
    private static final int KICK_MESSAGE_ID = 13;
    private static final int PROPAGATE_INVITATION_DIALOG_RESPONSE_MESSAGE_ID = 14;
    private static final int PUT_GROUP_LOG_ON_SERVER_MESSAGE_ID = 15;
    private static final int INVITATION_REJECTED_BROADCAST_MESSAGE_ID = 16;
    private static final int PROPAGATE_INVITATION_REJECTED_MESSAGE_ID = 17;
    private static final int GROUP_UPDATE_INITIAL_MESSAGE_ID = 18;
    private static final int REQUEST_LOCK_MESSAGE_ID = 19;
    private static final int GROUP_LEAVE_INITIAL_MESSAGE_ID = 20;
    private static final int PROPAGATED_GROUP_LEAVE_MESSAGE_ID = 21;
    private static final int GROUP_DISBAND_INITIAL_MESSAGE_ID = 22;
    private static final int PROPAGATED_GROUP_DISBAND_MESSAGE_ID = 23;
    private static final int PROPAGATED_KICK_MESSAGE_ID = 24;
    private static final int GROUP_RE_DOWNLOAD_INITIAL_MESSAGE_ID = 25;
    private static final int INITIATE_BATCH_KEYS_RESEND_MESSAGE_ID = 26;
    private static final int BLOB_KEYS_BATCH_AFTER_CHANNEL_CREATION_MESSAGE_ID = 27;
    private static final int BLOB_KEYS_AFTER_CHANNEL_CREATION_MESSAGE_ID = 28;


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case GROUP_CREATION_INITIAL_MESSAGE_ID:
                return GroupCreationInitialMessage.class;
            case UPLOAD_GROUP_PHOTO_MESSAGE_ID:
                return UploadGroupPhotoMessage.class;
            case UPLOAD_GROUP_BLOB_MESSAGE_ID:
                return UploadGroupBlobMessage.class;
            case FINALIZE_GROUP_CREATION_MESSAGE_ID:
                return FinalizeGroupCreationMessage.class;
            case INVITATION_OR_MEMBERS_UPDATE_MESSAGE_ID:
                return InvitationOrMembersUpdateMessage.class;
            case INVITATION_OR_MEMBERS_UPDATE_BROADCAST_MESSAGE_ID:
                return InvitationOrMembersUpdateBroadcastMessage.class;
            case INVITATION_OR_MEMBERS_UPDATE_PROPAGATED_MESSAGE_ID:
                return InvitationOrMembersUpdatePropagatedMessage.class;
            case DOWNLOAD_GROUP_BLOB_MESSAGE_ID:
                return DownloadGroupBlobMessage.class;
            case FINALIZE_GROUP_UPDATE_MESSAGE_ID:
                return FinalizeGroupUpdateMessage.class;
            case DELETE_GROUP_BLOB_FROM_SERVER_MESSAGE_ID:
                return DeleteGroupBlobFromServerMessage.class;
            case DIALOG_ACCEPT_GROUP_INVITATION_MESSAGE_ID:
                return DialogAcceptGroupInvitationMessage.class;
            case PING_MESSAGE_ID:
                return PingMessage.class;
            case PROPAGATED_PING_MESSAGE_ID:
                return PropagatedPingMessage.class;
            case KICK_MESSAGE_ID:
                return KickMessage.class;
            case PROPAGATE_INVITATION_DIALOG_RESPONSE_MESSAGE_ID:
                return PropagateInvitationDialogResponseMessage.class;
            case PUT_GROUP_LOG_ON_SERVER_MESSAGE_ID:
                return PutGroupLogOnServerMessage.class;
            case INVITATION_REJECTED_BROADCAST_MESSAGE_ID:
                return InvitationRejectedBroadcastMessage.class;
            case PROPAGATE_INVITATION_REJECTED_MESSAGE_ID:
                return PropagateInvitationRejectedMessage.class;
            case GROUP_UPDATE_INITIAL_MESSAGE_ID:
                return GroupUpdateInitialMessage.class;
            case REQUEST_LOCK_MESSAGE_ID:
                return RequestLockMessage.class;
            case GROUP_LEAVE_INITIAL_MESSAGE_ID:
                return GroupLeaveInitialMessage.class;
            case PROPAGATED_GROUP_LEAVE_MESSAGE_ID:
                return PropagatedGroupLeaveMessage.class;
            case GROUP_DISBAND_INITIAL_MESSAGE_ID:
                return GroupDisbandInitialMessage.class;
            case PROPAGATED_GROUP_DISBAND_MESSAGE_ID:
                return PropagatedGroupDisbandMessage.class;
            case GROUP_RE_DOWNLOAD_INITIAL_MESSAGE_ID:
                return GroupReDownloadInitialMessage.class;
            case INITIATE_BATCH_KEYS_RESEND_MESSAGE_ID:
                return InitiateBatchKeysResendMessage.class;
            case BLOB_KEYS_BATCH_AFTER_CHANNEL_CREATION_MESSAGE_ID:
                return BlobKeysBatchAfterChannelCreationMessage.class;
            case BLOB_KEYS_AFTER_CHANNEL_CREATION_MESSAGE_ID:
                return BlobKeysAfterChannelCreationMessage.class;
            default:
                return null;
        }
    }


    public static class GroupCreationInitialMessage extends ConcreteProtocolMessage {
        private final HashSet<GroupV2.Permission> ownPermissions;
        private final HashSet<GroupV2.IdentityAndPermissions> otherGroupMembers; // does not include the group creator identity
        private final String serializedGroupDetails; // serialized JsonGroupDetails
        private final String absolutePhotoUrl;


        public GroupCreationInitialMessage(CoreProtocolMessage coreProtocolMessage, HashSet<GroupV2.Permission> ownPermissions, HashSet<GroupV2.IdentityAndPermissions> otherGroupMembers, String serializedGroupDetails, String absolutePhotoUrl) {
            super(coreProtocolMessage);
            this.ownPermissions = ownPermissions;
            this.otherGroupMembers = otherGroupMembers;
            this.serializedGroupDetails = serializedGroupDetails;
            this.absolutePhotoUrl = absolutePhotoUrl;
        }

        @SuppressWarnings("unused")
        public GroupCreationInitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 4) {
                throw new Exception();
            }
            this.ownPermissions = GroupV2.Permission.deserializeKnownPermissions(inputs[0].decodeBytes());
            this.otherGroupMembers = new HashSet<>();
            for (Encoded encodedGroupMember : inputs[1].decodeList()) {
                this.otherGroupMembers.add(GroupV2.IdentityAndPermissions.of(encodedGroupMember));
            }
            this.serializedGroupDetails = inputs[2].decodeString();
            String url = inputs[3].decodeString();
            if (url.length() == 0) {
                this.absolutePhotoUrl = null;
            } else {
                this.absolutePhotoUrl = url;
            }
        }

        @Override
        public int getProtocolMessageId() {
            return GROUP_CREATION_INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            List<Encoded> encodedGroupMembers = new ArrayList<>();
            for (GroupV2.IdentityAndPermissions groupMember : otherGroupMembers) {
                encodedGroupMembers.add(groupMember.encode());
            }
            //noinspection ConstantConditions
            return new Encoded[] {
                    Encoded.of(GroupV2.Permission.serializePermissions(ownPermissions)),
                    Encoded.of(encodedGroupMembers.toArray(new Encoded[0])),
                    Encoded.of(serializedGroupDetails),
                    Encoded.of(absolutePhotoUrl == null ? "" : absolutePhotoUrl),
            };
        }
    }

    public static class UploadGroupPhotoMessage extends ConcreteProtocolMessage {
        private UploadGroupPhotoMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public UploadGroupPhotoMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() != null) { // the response should always be null for putUserData
                throw new Exception();
            }
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return UPLOAD_GROUP_PHOTO_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class UploadGroupBlobMessage extends ConcreteProtocolMessage {
        private final int uploadResult; // 0 success, 1 retry-able fail, 2 definitive fail

        private UploadGroupBlobMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            this.uploadResult = 2;
        }

        @SuppressWarnings("unused")
        public UploadGroupBlobMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) { // the response should never be null for putGroupBlob
                throw new Exception();
            }
            this.uploadResult = (int) receivedMessage.getEncodedResponse().decodeLong();
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return UPLOAD_GROUP_BLOB_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class FinalizeGroupCreationMessage extends EmptyProtocolMessage {
        private FinalizeGroupCreationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public FinalizeGroupCreationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return FINALIZE_GROUP_CREATION_MESSAGE_ID;
        }
    }

    public static class InvitationOrMembersUpdateMessage extends ConcreteProtocolMessage {

        private final GroupV2.Identifier groupIdentifier;
        private final int groupVersion;
        private final GroupV2.BlobKeys blobKeys;
        private final UID[] notifiedDeviceUids;

        public InvitationOrMembersUpdateMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, int groupVersion, GroupV2.BlobKeys blobKeys, UID[] notifiedDeviceUids) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.groupVersion = groupVersion;
            this.blobKeys = blobKeys;
            this.notifiedDeviceUids = notifiedDeviceUids;
        }

        @SuppressWarnings("unused")
        public InvitationOrMembersUpdateMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 4) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.groupVersion = (int) list[1].decodeLong();
            this.blobKeys = GroupV2.BlobKeys.of(list[2]);
            this.notifiedDeviceUids = list[3].decodeUidArray();
        }


        @Override
        public int getProtocolMessageId() {
            return INVITATION_OR_MEMBERS_UPDATE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(groupVersion),
                    blobKeys.encode(),
                    Encoded.of(notifiedDeviceUids),
            };
        }
    }

    public static class InvitationOrMembersUpdateBroadcastMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;
        private final int groupVersion;
        private final GroupV2.BlobKeys blobKeys;

        public InvitationOrMembersUpdateBroadcastMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, int groupVersion, GroupV2.BlobKeys blobKeys) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.groupVersion = groupVersion;
            this.blobKeys = blobKeys;
        }

        @SuppressWarnings("unused")
        public InvitationOrMembersUpdateBroadcastMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 3) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.groupVersion = (int) list[1].decodeLong();
            this.blobKeys = GroupV2.BlobKeys.of(list[2]);
        }

        @Override
        public int getProtocolMessageId() {
            return INVITATION_OR_MEMBERS_UPDATE_BROADCAST_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(groupVersion),
                    blobKeys.encode(),
            };
        }
    }

    public static class InvitationOrMembersUpdatePropagatedMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;
        private final int groupVersion;
        private final GroupV2.BlobKeys blobKeys;
        private final Identity inviterIdentity; // may be null

        public InvitationOrMembersUpdatePropagatedMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, int groupVersion, GroupV2.BlobKeys blobKeys, Identity inviterIdentity) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.groupVersion = groupVersion;
            this.blobKeys = blobKeys;
            this.inviterIdentity = inviterIdentity;
        }

        @SuppressWarnings("unused")
        public InvitationOrMembersUpdatePropagatedMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 4 && list.length != 3) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.groupVersion = (int) list[1].decodeLong();
            this.blobKeys = GroupV2.BlobKeys.of(list[2]);
            if (list.length == 3) {
                this.inviterIdentity = null;
            } else {
                this.inviterIdentity = list[3].decodeIdentity();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return INVITATION_OR_MEMBERS_UPDATE_PROPAGATED_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            if (inviterIdentity == null) {
                return new Encoded[]{
                        groupIdentifier.encode(),
                        Encoded.of(groupVersion),
                        blobKeys.encode(),
                };
            } else {
                return new Encoded[]{
                        groupIdentifier.encode(),
                        Encoded.of(groupVersion),
                        blobKeys.encode(),
                        Encoded.of(inviterIdentity),
                };
            }
        }
    }

    public static class DownloadGroupBlobMessage extends ConcreteProtocolMessage {
        protected final EncryptedBytes encryptedServerBlob;
        protected final List<byte[]> logEntries;
        protected final ServerAuthenticationPublicKey groupAdminPublicKey;
        protected final byte[] serverQueryNonce;
        protected final boolean deletedFromServer;

        private DownloadGroupBlobMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            this.encryptedServerBlob = null;
            this.logEntries = null;
            this.groupAdminPublicKey = null;
            this.serverQueryNonce = null;
            this.deletedFromServer = false;
        }

        @SuppressWarnings("unused")
        public DownloadGroupBlobMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) { // a null response means the query has expired --> the protocol can be aborted
                this.encryptedServerBlob = null;
                this.logEntries = null;
                this.groupAdminPublicKey = null;
                this.serverQueryNonce = null;
                this.deletedFromServer = false;
            } else {
                Encoded[] list = receivedMessage.getEncodedResponse().decodeList();
                if (list.length == 1 && list[0].decodeBoolean()) { // this response means the group was deleted from the server --> the protocol can be aborted and the group deleted
                    this.encryptedServerBlob = null;
                    this.logEntries = null;
                    this.groupAdminPublicKey = null;
                    this.serverQueryNonce = null;
                    this.deletedFromServer = true;
                } else {
                    this.encryptedServerBlob = list[0].decodeEncryptedData();
                    this.logEntries = new ArrayList<>();
                    for (Encoded encodedLogEntry : list[1].decodeList()) {
                        this.logEntries.add(encodedLogEntry.decodeBytes());
                    }
                    this.groupAdminPublicKey = (ServerAuthenticationPublicKey) list[2].decodePublicKey();
                    this.serverQueryNonce = list[3].decodeBytes();
                    this.deletedFromServer = false;
                }
            }
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return DOWNLOAD_GROUP_BLOB_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class FinalizeGroupUpdateMessage extends EmptyProtocolMessage {
        private FinalizeGroupUpdateMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public FinalizeGroupUpdateMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return FINALIZE_GROUP_UPDATE_MESSAGE_ID;
        }
    }

    public static class DeleteGroupBlobFromServerMessage extends EmptyProtocolMessage {
        private final boolean success;

        private DeleteGroupBlobFromServerMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            success = false;
        }

        @SuppressWarnings("unused")
        public DeleteGroupBlobFromServerMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
            if (receivedMessage.getEncodedResponse() == null) {
                success = false;
            } else {
                success = receivedMessage.getEncodedResponse().decodeBoolean();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return DELETE_GROUP_BLOB_FROM_SERVER_MESSAGE_ID;
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

    public static class PingMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;
        private final byte[] groupMemberInvitationNonce;
        private final byte[] signature;
        private final boolean isResponse;

        public PingMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, byte[] groupMemberInvitationNonce, byte[] signature, boolean isResponse) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.groupMemberInvitationNonce = groupMemberInvitationNonce;
            this.signature = signature;
            this.isResponse = isResponse;
        }

        @SuppressWarnings("unused")
        public PingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 4) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.groupMemberInvitationNonce = list[1].decodeBytes();
            this.signature = list[2].decodeBytes();
            this.isResponse = list[3].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return PING_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(groupMemberInvitationNonce),
                    Encoded.of(signature),
                    Encoded.of(isResponse),
            };
        }
    }

    public static class PropagatedPingMessage extends PingMessage {
        public PropagatedPingMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, byte[] groupMemberInvitationNonce, byte[] signature, boolean isResponse) {
            super(coreProtocolMessage, groupIdentifier, groupMemberInvitationNonce, signature, isResponse);
        }

        @SuppressWarnings("unused")
        public PropagatedPingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATED_PING_MESSAGE_ID;
        }
    }

    public static class KickMessage extends ConcreteProtocolMessage {
        protected final GroupV2.Identifier groupIdentifier;
        protected final EncryptedBytes encryptedAdministratorsChain;
        protected final byte[] signature;

        public KickMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, EncryptedBytes encryptedAdministratorsChain, byte[] signature) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.encryptedAdministratorsChain = encryptedAdministratorsChain;
            this.signature = signature;
        }

        @SuppressWarnings("unused")
        public KickMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 3) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.encryptedAdministratorsChain = list[1].decodeEncryptedData();
            this.signature = list[2].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return KICK_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(encryptedAdministratorsChain),
                    Encoded.of(signature),
            };
        }
    }

    public static class PropagatedKickMessage extends KickMessage {
        public PropagatedKickMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, EncryptedBytes encryptedAdministratorsChain, byte[] signature) {
            super(coreProtocolMessage, groupIdentifier, encryptedAdministratorsChain, signature);
        }

        @SuppressWarnings("unused")
        public PropagatedKickMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATED_KICK_MESSAGE_ID;
        }
    }


        public static class PropagateInvitationDialogResponseMessage extends ConcreteProtocolMessage {
        private final boolean invitationAccepted;
        private final byte[] ownGroupInvitationNonce;

        PropagateInvitationDialogResponseMessage(CoreProtocolMessage coreProtocolMessage, boolean invitationAccepted, byte[] ownGroupInvitationNonce) {
            super(coreProtocolMessage);
            this.invitationAccepted = invitationAccepted;
            this.ownGroupInvitationNonce = ownGroupInvitationNonce;
        }

        @SuppressWarnings("unused")
        public PropagateInvitationDialogResponseMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 2) {
                throw new Exception();
            }
            this.invitationAccepted = list[0].decodeBoolean();
            this.ownGroupInvitationNonce = list[1].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_INVITATION_DIALOG_RESPONSE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(invitationAccepted),
                    Encoded.of(ownGroupInvitationNonce),
            };
        }
    }

    public static class PutGroupLogOnServerMessage extends EmptyProtocolMessage {
        private PutGroupLogOnServerMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public PutGroupLogOnServerMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return PUT_GROUP_LOG_ON_SERVER_MESSAGE_ID;
        }
    }


    public static class InvitationRejectedBroadcastMessage extends ConcreteProtocolMessage {
        protected final GroupV2.Identifier groupIdentifier;

        public InvitationRejectedBroadcastMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
        }

        @SuppressWarnings("unused")
        public InvitationRejectedBroadcastMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 1) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return INVITATION_REJECTED_BROADCAST_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupIdentifier.encode(),
            };
        }
    }


    public static class PropagateInvitationRejectedMessage extends InvitationRejectedBroadcastMessage {
        public PropagateInvitationRejectedMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier) {
            super(coreProtocolMessage, groupIdentifier);
        }

        @SuppressWarnings("unused")
        public PropagateInvitationRejectedMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_INVITATION_REJECTED_MESSAGE_ID;
        }
    }


    public static class GroupUpdateInitialMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;
        private final ObvGroupV2.ObvGroupV2ChangeSet changeSet;


        public GroupUpdateInitialMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, ObvGroupV2.ObvGroupV2ChangeSet changeSet) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.changeSet = changeSet;
        }


        @SuppressWarnings("unused")
        public GroupUpdateInitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 2) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(inputs[0]);
            this.changeSet = ObvGroupV2.ObvGroupV2ChangeSet.of(inputs[1]);
        }

        @Override
        public int getProtocolMessageId() {
            return GROUP_UPDATE_INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    groupIdentifier.encode(),
                    changeSet.encode(),
            };
        }
    }

    public static class RequestLockMessage extends DownloadGroupBlobMessage {
        private RequestLockMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public RequestLockMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return REQUEST_LOCK_MESSAGE_ID;
        }
    }

    public static class GroupLeaveInitialMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;


        public GroupLeaveInitialMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
        }


        @SuppressWarnings("unused")
        public GroupLeaveInitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 1) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(inputs[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return GROUP_LEAVE_INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    groupIdentifier.encode(),
            };
        }
    }

    public static class PropagatedGroupLeaveMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;
        private final byte[] ownInvitationNonce;


        public PropagatedGroupLeaveMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, byte[] ownInvitationNonce) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.ownInvitationNonce = ownInvitationNonce;
        }


        @SuppressWarnings("unused")
        public PropagatedGroupLeaveMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 2) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(inputs[0]);
            this.ownInvitationNonce = inputs[1].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATED_GROUP_LEAVE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    groupIdentifier.encode(),
                    Encoded.of(ownInvitationNonce),
            };
        }
    }


    public static class GroupDisbandInitialMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;


        public GroupDisbandInitialMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
        }


        @SuppressWarnings("unused")
        public GroupDisbandInitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 1) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(inputs[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return GROUP_DISBAND_INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    groupIdentifier.encode(),
            };
        }
    }

    public static class PropagatedGroupDisbandMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;

        public PropagatedGroupDisbandMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
        }


        @SuppressWarnings("unused")
        public PropagatedGroupDisbandMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 1) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(inputs[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATED_GROUP_DISBAND_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    groupIdentifier.encode(),
            };
        }
    }

    public static class GroupReDownloadInitialMessage extends ConcreteProtocolMessage {
        private final GroupV2.Identifier groupIdentifier;

        public GroupReDownloadInitialMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
        }


        @SuppressWarnings("unused")
        public GroupReDownloadInitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 1) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(inputs[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return GROUP_RE_DOWNLOAD_INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    groupIdentifier.encode(),
            };
        }
    }



    public static class InitiateBatchKeysResendMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final UID contactDeviceUid;

        public InitiateBatchKeysResendMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, UID contactDeviceUid) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.contactDeviceUid = contactDeviceUid;
        }


        @SuppressWarnings("unused")
        public InitiateBatchKeysResendMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 2) {
                throw new Exception();
            }
            this.contactIdentity = inputs[0].decodeIdentity();
            this.contactDeviceUid = inputs[1].decodeUid();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_BATCH_KEYS_RESEND_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
                    Encoded.of(contactDeviceUid),
            };
        }
    }


    public static class BlobKeysBatchAfterChannelCreationMessage extends ConcreteProtocolMessage {
        private final GroupV2.IdentifierVersionAndKeys[] groupInfos;

        public BlobKeysBatchAfterChannelCreationMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.IdentifierVersionAndKeys[] groupInfos) {
            super(coreProtocolMessage);
            this.groupInfos = groupInfos;
        }

        @SuppressWarnings("unused")
        public BlobKeysBatchAfterChannelCreationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 1) {
                throw new Exception();
            }
            Encoded[] encodeds = inputs[0].decodeList();
            this.groupInfos = new GroupV2.IdentifierVersionAndKeys[encodeds.length];
            for (int i=0; i< encodeds.length; i++) {
                this.groupInfos[i] = new GroupV2.IdentifierVersionAndKeys(encodeds[i]);
            }
        }

        @Override
        public int getProtocolMessageId() {
            return BLOB_KEYS_BATCH_AFTER_CHANNEL_CREATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            Encoded[] encodeds = new Encoded[groupInfos.length];
            for (int i=0; i< groupInfos.length; i++) {
                encodeds[i] = groupInfos[i].encode();
            }
            return new Encoded[] {
                    Encoded.of(encodeds),
            };
        }

    }

    public static class BlobKeysAfterChannelCreationMessage extends ConcreteProtocolMessage {
        private final Identity groupInviter;
        private final GroupV2.Identifier groupIdentifier;
        private final int groupVersion;
        private final GroupV2.BlobKeys blobKeys;

        public BlobKeysAfterChannelCreationMessage(CoreProtocolMessage coreProtocolMessage, Identity groupInviter, GroupV2.Identifier groupIdentifier, int groupVersion, GroupV2.BlobKeys blobKeys) {
            super(coreProtocolMessage);
            this.groupInviter = groupInviter;
            this.groupIdentifier = groupIdentifier;
            this.groupVersion = groupVersion;
            this.blobKeys = blobKeys;
        }

        @SuppressWarnings("unused")
        public BlobKeysAfterChannelCreationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 4) {
                throw new Exception();
            }
            this.groupInviter = list[0].decodeIdentity();
            this.groupIdentifier = GroupV2.Identifier.of(list[1]);
            this.groupVersion = (int) list[2].decodeLong();
            this.blobKeys = GroupV2.BlobKeys.of(list[3]);
        }

        @Override
        public int getProtocolMessageId() {
            return BLOB_KEYS_AFTER_CHANNEL_CREATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(groupInviter),
                    groupIdentifier.encode(),
                    Encoded.of(groupVersion),
                    blobKeys.encode(),
            };
        }
    }

    // endregion








    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{InitiateGroupCreationStep.class, ProcessInvitationOrMembersUpdateStep.class, DoNothingAfterServerQueryStep.class, ProcessPingStep.class, InitiateBlobReDownloadStep.class, InitiateGroupUpdateStep.class, GetKickedStep.class, LeaveGroupStep.class, DisbandGroupStep.class, PrepareBatchKeysMessageStep.class, ProcessBatchKeysMessageStep.class };
            case UPLOADING_CREATED_GROUP_DATA_STATE_ID:
                return new Class[]{CheckIfGroupCreationCanBeFinalizedStep.class, FinalizeGroupCreationStep.class};
            case DOWNLOADING_GROUP_BLOB_STATE_ID:
                return new Class[]{ProcessDownloadedGroupDataStep.class, ProcessInvitationDialogResponseStep.class, LeaveGroupStep.class, GetKickedStep.class, DisbandGroupStep.class};
            case I_NEED_MORE_SEEDS_STATE_ID:
                return new Class[]{ProcessInvitationOrMembersUpdateStep.class, ProcessInvitationDialogResponseStep.class, LeaveGroupStep.class, GetKickedStep.class, DisbandGroupStep.class};
            case INVITATION_RECEIVED_STATE_ID:
                return new Class[]{ProcessInvitationOrMembersUpdateStep.class, ProcessInvitationDialogResponseStep.class, InitiateBlobReDownloadStep.class, GetKickedStep.class, DisbandGroupStep.class};
            case REJECTING_INVITATION_OR_LEAVING_GROUP_STATE_ID:
                return new Class[]{NotifyMembersOfRejectionOrGroupLeftStep.class};
            case WAITING_FOR_LOCK_STATE_ID:
                return new Class[]{PrepareBlobForGroupUpdateStep.class, GetKickedStep.class, LeaveGroupStep.class};
            case UPLOADING_UPDATED_GROUP_BLOB_STATE_ID:
                return new Class[]{ProcessGroupUpdateBlobUploadResponseStep.class};
            case UPLOADING_UPDATED_GROUP_PHOTO_STATE_ID:
                return new Class[]{ProcessGroupUpdatePhotoUploadResponseStep.class, FinalizeGroupUpdateStep.class};
            case DISBANDING_GROUP_STATE_ID:
                return new Class[]{FinalizeGroupDisbandStep.class};
            default:
                return new Class[0];
        }
    }

    public static class InitiateGroupCreationStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final GroupCreationInitialMessage receivedMessage;

        public InitiateGroupCreationStep(InitialProtocolState startState, GroupCreationInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // no need to check that group members are indeed contacts, this will be checked in createNewGroupV2


            GroupV2.AdministratorsChain chain;
            {
                List<Identity> otherAdmins = new ArrayList<>();
                for (GroupV2.IdentityAndPermissions groupMember : receivedMessage.otherGroupMembers) {
                    if (groupMember.isAdmin()) {
                        otherAdmins.add(groupMember.identity);
                    }
                }

                // compute the first blockchain block
                chain = GroupV2.AdministratorsChain.startNewChain(
                        protocolManagerSession.session,
                        protocolManagerSession.identityDelegate,
                        getOwnedIdentity(),
                        otherAdmins.toArray(new Identity[0]),
                        getPrng());
            }

            byte[] verifiedAdministratorsChain = chain.encode().getBytes();
            GroupV2.Identifier groupIdentifier = new GroupV2.Identifier(chain.groupUid, getOwnedIdentity().getServer(), GroupV2.Identifier.CATEGORY_SERVER);
            GroupV2.ServerPhotoInfo serverPhotoInfo = receivedMessage.absolutePhotoUrl == null ? null : new GroupV2.ServerPhotoInfo(
                    getOwnedIdentity(),
                    new UID(getPrng()),
                    Suite.getDefaultAuthEnc(0).generateKey(getPrng())
            );
            Seed blobMainSeed = new Seed(getPrng());
            Seed blobVersionSeed = new Seed(getPrng());
            KeyPair groupAdminServerAuthenticationKeyPair = Suite.generateServerAuthenticationKeyPair(null, getPrng());
            byte[] ownGroupInvitationNonce = getPrng().bytes(Constants.GROUP_V2_INVITATION_NONCE_LENGTH);
            {
                // create the group in database
                HashSet<GroupV2.IdentityAndPermissionsAndDetails> otherGroupMembers = new HashSet<>();
                for (GroupV2.IdentityAndPermissions identityAndPermissions : receivedMessage.otherGroupMembers) {
                    List<String> permissionStrings = new ArrayList<>();
                    for (GroupV2.Permission permission : identityAndPermissions.permissions) {
                        permissionStrings.add(permission.getString());
                    }
                    String serializedContactDetails = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), identityAndPermissions.identity);
                    otherGroupMembers.add(new GroupV2.IdentityAndPermissionsAndDetails(
                            identityAndPermissions.identity,
                            permissionStrings,
                            serializedContactDetails,
                            getPrng().bytes(Constants.GROUP_V2_INVITATION_NONCE_LENGTH)
                    ));
                }

                List<String> ownPermissionStrings = new ArrayList<>();
                for (GroupV2.Permission permission : receivedMessage.ownPermissions) {
                    ownPermissionStrings.add(permission.getString());
                }

                // this create a frozen group, so no need to freeze in this step
                //noinspection ConstantConditions
                protocolManagerSession.identityDelegate.createNewGroupV2(
                        protocolManagerSession.session,
                        getOwnedIdentity(),
                        groupIdentifier,
                        receivedMessage.serializedGroupDetails,
                        receivedMessage.absolutePhotoUrl,
                        serverPhotoInfo,
                        verifiedAdministratorsChain,
                        new GroupV2.BlobKeys(blobMainSeed,
                                blobVersionSeed,
                                (ServerAuthenticationPrivateKey) groupAdminServerAuthenticationKeyPair.getPrivateKey()),
                        ownGroupInvitationNonce,
                        ownPermissionStrings,
                        otherGroupMembers
                );
            }

            GroupV2.ServerBlob serverBlob = protocolManagerSession.identityDelegate.getGroupV2ServerBlob(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);

            if (serverBlob == null) {
                throw new Exception("Failed to retrieve serverBlob from a just created group");
            }

            EncryptedBytes encryptedBlob;
            {
                // compute the encoded, signed, padded, and encrypted blob from the ServerBlob we have

                Encoded encodedServerBlob = serverBlob.encode();
                byte[] signature = protocolManagerSession.identityDelegate.signBlock(
                        protocolManagerSession.session,
                        Constants.SignatureContext.GROUP_BLOB,
                        encodedServerBlob.getBytes(),
                        getOwnedIdentity(),
                        getPrng()
                );

                Encoded encodedSignedBlob = Encoded.of(new Encoded[]{
                        encodedServerBlob,
                        Encoded.of(getOwnedIdentity()),
                        Encoded.of(signature),
                });

                int unpaddedLength = encodedSignedBlob.getBytes().length;
                int paddedLength = (1 + ((unpaddedLength - 1) >> 12)) << 12; // we pad to the smallest multiple of 4096 larger than the actual length

                byte[] paddedBlobPlaintext = new byte[paddedLength];
                System.arraycopy(encodedSignedBlob.getBytes(), 0, paddedBlobPlaintext, 0, unpaddedLength);
                AuthEncKey blobEncryptionKey = GroupV2.getSharedBlobSecretKey(blobMainSeed, blobVersionSeed);
                encryptedBlob = Suite.getAuthEnc(blobEncryptionKey).encrypt(blobEncryptionKey, paddedBlobPlaintext, getPrng());
            }


            if (serverBlob.serverPhotoInfo != null) {
                // upload the group photo if needed

                String photoUrl = protocolManagerSession.identityDelegate.getGroupV2PhotoUrl(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);

                if (photoUrl != null) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createPutUserDataQuery(getOwnedIdentity(), serverBlob.serverPhotoInfo.serverPhotoLabel, photoUrl, serverBlob.serverPhotoInfo.serverPhotoKey)));
                    ChannelMessageToSend messageToSend = new UploadGroupPhotoMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }


            {
                // upload the encrypted blob

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createCreateGroupBlobQuery(groupIdentifier, Encoded.of(groupAdminServerAuthenticationKeyPair.getPublicKey()), encryptedBlob)));
                ChannelMessageToSend messageToSend = new UploadGroupBlobMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new UploadingCreatedGroupDataState(
                    groupIdentifier,
                    serverBlob.version,
                    true,
                    serverBlob.serverPhotoInfo != null);
        }
    }


    public static class CheckIfGroupCreationCanBeFinalizedStep extends ProtocolStep {
        private enum UploadType {
            BLOB,
            PHOTO,
        }

        private final UploadingCreatedGroupDataState startState;
        private final UploadType uploadType;
        private final int uploadResult;

        @SuppressWarnings("unused")
        public CheckIfGroupCreationCanBeFinalizedStep(UploadingCreatedGroupDataState startState, UploadGroupPhotoMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.uploadType = UploadType.PHOTO;
            this.uploadResult = 0;
        }

        @SuppressWarnings("unused")
        public CheckIfGroupCreationCanBeFinalizedStep(UploadingCreatedGroupDataState startState, UploadGroupBlobMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.uploadType = UploadType.BLOB;
            this.uploadResult = receivedMessage.uploadResult;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            GroupV2.Identifier groupIdentifier = startState.groupIdentifier;
            boolean waitingForBlobUpload = startState.waitingForBlobUpload;
            boolean waitingForPhotoUpload = startState.waitingForPhotoUpload;

            switch (uploadType){
                case BLOB: {
                    if (uploadResult == 0) {
                        waitingForBlobUpload = false;
                    } else {
                        // we were not able to upload the blob to the server --> delete the group
                        protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);
                        return new FinalState();
                    }
                    break;
                }
                case PHOTO:
                    waitingForPhotoUpload = false;
                    break;
            }

            if (!waitingForBlobUpload && !waitingForPhotoUpload) {
                // if there is nothing left to upload, post a message to initiate the finalization of the group creation
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new FinalizeGroupCreationMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new UploadingCreatedGroupDataState(groupIdentifier, startState.groupVersion, waitingForBlobUpload, waitingForPhotoUpload);
        }
    }


    public static class FinalizeGroupCreationStep extends ProtocolStep {
        private final UploadingCreatedGroupDataState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final FinalizeGroupCreationMessage receivedMessage;

        public FinalizeGroupCreationStep(UploadingCreatedGroupDataState startState, FinalizeGroupCreationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();


            {
                // for each group member & pending member, send
                //  - for new members the main seed
                //  - the version seed for everyone
                //  - for admins the groupAdmin private key
                // send the message through oblivious channel when possible (required for new members), asymmetric broadcast otherwise

                GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
                HashSet<GroupV2.IdentityAndPermissions> groupMembersAndPermissions = protocolManagerSession.identityDelegate.getGroupV2OtherMembersAndPermissions(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
                if ((blobKeys == null) || (blobKeys.groupAdminServerAuthenticationPrivateKey == null) || (groupMembersAndPermissions == null)) {
                    // we are unable to retrieve basic group information --> delete the group we created before inviting anyone
                    protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
                    return new FinalState();
                }

                UID invitationProtocolInstanceUid = startState.groupIdentifier.computeProtocolInstanceUid();

                for (GroupV2.IdentityAndPermissions groupMembersAndPermission : groupMembersAndPermissions) {
                    UID[] contactDeviceUidsWithChannel = protocolManagerSession.channelDelegate.getConfirmedObliviousChannelDeviceUids(protocolManagerSession.session, getOwnedIdentity(), groupMembersAndPermission.identity);
                    if (contactDeviceUidsWithChannel.length > 0) {
                        GroupV2.BlobKeys keysToSend = new GroupV2.BlobKeys(
                                blobKeys.blobMainSeed,
                                blobKeys.blobVersionSeed,
                                groupMembersAndPermission.isAdmin() ? blobKeys.groupAdminServerAuthenticationPrivateKey : null
                        );

                        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                SendChannelInfo.createAllConfirmedObliviousChannelsInfo(groupMembersAndPermission.identity, getOwnedIdentity()),
                                getProtocolId(),
                                invitationProtocolInstanceUid,
                                false);
                        ChannelMessageToSend messageToSend = new InvitationOrMembersUpdateMessage(coreProtocolMessage, startState.groupIdentifier, startState.groupVersion, keysToSend, contactDeviceUidsWithChannel).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } else {
                        // we have a problem, we invited a member with whom we do not have a channel...
                        // rollback everything and delete the group
                        protocolManagerSession.session.rollback();
                        protocolManagerSession.session.startTransaction();
                        protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

                        // delete the group from the server
                        byte[] signature = Signature.sign(Constants.SignatureContext.GROUP_DELETE_ON_SERVER, blobKeys.groupAdminServerAuthenticationPrivateKey.getSignaturePrivateKey(), getPrng());
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createDeleteGroupBlobQuery(startState.groupIdentifier, signature)));
                        ChannelMessageToSend messageToSend = new DeleteGroupBlobFromServerMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                        return new FinalState();
                    }
                }
            }

            {
                // also unfreeze the group
                protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
            }


            return new FinalState();
        }
    }


    public static class ProcessInvitationOrMembersUpdateStep extends ProtocolStep {
        private final Identity obliviousChannelContactIdentity;
        private final ConcreteProtocolState startState;
        private final GroupV2.InvitationCollectedData invitationCollectedData;
        private final UUID dialogUuid;
        private final byte[] lastKnownOwnInvitationNonce;
        private final Identity[] lastKnownOtherGroupMemberIdentities;

        // elements from the received message
        private final GroupV2.Identifier groupIdentifier;
        private final int groupVersion;
        private final GroupV2.BlobKeys blobKeys;
        private final UID[] notifiedDeviceUids;
        private final boolean propagateIfNeeded;


        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InitialProtocolState startState, InvitationOrMembersUpdateMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();
            this.startState = null;
            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.dialogUuid = UUID.randomUUID();
            this.lastKnownOwnInvitationNonce = null;
            this.lastKnownOtherGroupMemberIdentities = null;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = receivedMessage.notifiedDeviceUids;
            this.propagateIfNeeded = true;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InitialProtocolState startState, InvitationOrMembersUpdateBroadcastMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = null;
            this.startState = null;
            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.dialogUuid = UUID.randomUUID();
            this.lastKnownOwnInvitationNonce = null;
            this.lastKnownOtherGroupMemberIdentities = null;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            // never consider a mainSeed received through an asymmetric channel, IT'S A TRAP!
            this.blobKeys = new GroupV2.BlobKeys(null, receivedMessage.blobKeys.blobVersionSeed, receivedMessage.blobKeys.groupAdminServerAuthenticationPrivateKey);
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = true;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InitialProtocolState startState, InvitationOrMembersUpdatePropagatedMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.inviterIdentity;
            this.startState = null;
            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.dialogUuid = UUID.randomUUID();
            this.lastKnownOwnInvitationNonce = null;
            this.lastKnownOtherGroupMemberIdentities = null;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = false;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InitialProtocolState startState, BlobKeysAfterChannelCreationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.groupInviter;
            this.startState = null;
            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.dialogUuid = UUID.randomUUID();
            this.lastKnownOwnInvitationNonce = null;
            this.lastKnownOtherGroupMemberIdentities = null;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = false;
        }




        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(INeedMoreSeedsState startState, InvitationOrMembersUpdateMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();
            this.startState = startState;
            this.invitationCollectedData = startState.invitationCollectedData;
            this.dialogUuid = startState.dialogUuid;
            this.lastKnownOwnInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.lastKnownOtherGroupMemberIdentities = startState.lastKnownOtherGroupMemberIdentities;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = receivedMessage.notifiedDeviceUids;
            this.propagateIfNeeded = true;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(INeedMoreSeedsState startState, InvitationOrMembersUpdateBroadcastMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = null;
            this.startState = startState;
            this.invitationCollectedData = startState.invitationCollectedData;
            this.dialogUuid = startState.dialogUuid;
            this.lastKnownOwnInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.lastKnownOtherGroupMemberIdentities = startState.lastKnownOtherGroupMemberIdentities;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            // never consider a mainSeed received through an asymmetric channel, IT'S A TRAP!
            this.blobKeys = new GroupV2.BlobKeys(null, receivedMessage.blobKeys.blobVersionSeed, receivedMessage.blobKeys.groupAdminServerAuthenticationPrivateKey);
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = true;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(INeedMoreSeedsState startState, InvitationOrMembersUpdatePropagatedMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.inviterIdentity;
            this.startState = startState;
            this.invitationCollectedData = startState.invitationCollectedData;
            this.dialogUuid = startState.dialogUuid;
            this.lastKnownOwnInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.lastKnownOtherGroupMemberIdentities = startState.lastKnownOtherGroupMemberIdentities;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = false;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(INeedMoreSeedsState startState, BlobKeysAfterChannelCreationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.groupInviter;
            this.startState = startState;
            this.invitationCollectedData = startState.invitationCollectedData;
            this.dialogUuid = startState.dialogUuid;
            this.lastKnownOwnInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.lastKnownOtherGroupMemberIdentities = startState.lastKnownOtherGroupMemberIdentities;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = false;
        }




        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InvitationReceivedState startState, InvitationOrMembersUpdateMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();
            this.startState = startState;

            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.invitationCollectedData.addBlobKeysCandidates(startState.inviterIdentity, startState.blobKeys);

            this.dialogUuid = startState.dialogUuid;
            byte[] nonce = null;
            List<Identity> identities = new ArrayList<>();
            for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : startState.serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                if (Objects.equals(identityAndPermissionsAndDetails.identity, getOwnedIdentity())) {
                    nonce = identityAndPermissionsAndDetails.groupInvitationNonce;
                    continue;
                }
                identities.add(identityAndPermissionsAndDetails.identity);
            }
            this.lastKnownOwnInvitationNonce = nonce;
            this.lastKnownOtherGroupMemberIdentities = identities.toArray(new Identity[0]);
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = receivedMessage.notifiedDeviceUids;
            this.propagateIfNeeded = true;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InvitationReceivedState startState, InvitationOrMembersUpdateBroadcastMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = null;
            this.startState = startState;

            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.invitationCollectedData.addBlobKeysCandidates(startState.inviterIdentity, startState.blobKeys);

            this.dialogUuid = startState.dialogUuid;
            byte[] nonce = null;
            List<Identity> identities = new ArrayList<>();
            for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : startState.serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                if (Objects.equals(identityAndPermissionsAndDetails.identity, getOwnedIdentity())) {
                    nonce = identityAndPermissionsAndDetails.groupInvitationNonce;
                    continue;
                }
                identities.add(identityAndPermissionsAndDetails.identity);
            }
            this.lastKnownOwnInvitationNonce = nonce;
            this.lastKnownOtherGroupMemberIdentities = identities.toArray(new Identity[0]);
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            // never consider a mainSeed received through an asymmetric channel, IT'S A TRAP!
            this.blobKeys = new GroupV2.BlobKeys(null, receivedMessage.blobKeys.blobVersionSeed, receivedMessage.blobKeys.groupAdminServerAuthenticationPrivateKey);
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = true;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InvitationReceivedState startState, InvitationOrMembersUpdatePropagatedMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.inviterIdentity;
            this.startState = startState;

            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.invitationCollectedData.addBlobKeysCandidates(startState.inviterIdentity, startState.blobKeys);

            this.dialogUuid = startState.dialogUuid;
            byte[] nonce = null;
            List<Identity> identities = new ArrayList<>();
            for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : startState.serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                if (Objects.equals(identityAndPermissionsAndDetails.identity, getOwnedIdentity())) {
                    nonce = identityAndPermissionsAndDetails.groupInvitationNonce;
                    continue;
                }
                identities.add(identityAndPermissionsAndDetails.identity);
            }
            this.lastKnownOwnInvitationNonce = nonce;
            this.lastKnownOtherGroupMemberIdentities = identities.toArray(new Identity[0]);
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = false;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationOrMembersUpdateStep(InvitationReceivedState startState, BlobKeysAfterChannelCreationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.obliviousChannelContactIdentity = receivedMessage.groupInviter;
            this.startState = startState;

            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.invitationCollectedData.addBlobKeysCandidates(startState.inviterIdentity, startState.blobKeys);

            this.dialogUuid = startState.dialogUuid;
            byte[] nonce = null;
            List<Identity> identities = new ArrayList<>();
            for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : startState.serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                if (Objects.equals(identityAndPermissionsAndDetails.identity, getOwnedIdentity())) {
                    nonce = identityAndPermissionsAndDetails.groupInvitationNonce;
                    continue;
                }
                identities.add(identityAndPermissionsAndDetails.identity);
            }
            this.lastKnownOwnInvitationNonce = nonce;
            this.lastKnownOtherGroupMemberIdentities = identities.toArray(new Identity[0]);
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.groupVersion = receivedMessage.groupVersion;
            this.blobKeys = receivedMessage.blobKeys;
            this.notifiedDeviceUids = new UID[0];
            this.propagateIfNeeded = false;
        }




        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // first check that the protocolInstanceUid matches the groupIdentifier
                if (!getProtocolInstanceUid().equals(groupIdentifier.computeProtocolInstanceUid())) {
                    if (startState != null) {
                        return startState;
                    } else {
                        return new FinalState();
                    }
                }
            }

            {
                // if the sender could not send the message to all devices, propagate it to other owned devices, if any
                if (propagateIfNeeded) {
                    UID[] otherOwnedDeviceUids = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

                    HashSet<UID> notNotifiedUids = new HashSet<>(Arrays.asList(otherOwnedDeviceUids));
                    for (UID deviceUid : notifiedDeviceUids){
                        notNotifiedUids.remove(deviceUid);
                    }

                    if (notNotifiedUids.size() > 0) {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(getOwnedIdentity(), getOwnedIdentity(), notNotifiedUids.toArray(new UID[0]), true));
                        ChannelMessageToSend messageToSend = new InvitationOrMembersUpdatePropagatedMessage(coreProtocolMessage, groupIdentifier, groupVersion, blobKeys, obliviousChannelContactIdentity).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }
                }
            }

            {
                // check if we already have a more recent group version invitation
                if (startState instanceof InvitationReceivedState) {
                    if (((InvitationReceivedState) startState).serverBlob.version >= groupVersion) {
                        return startState;
                    }


                    // freeze the invitation while we update the blob
                    HashSet<ObvGroupV2.ObvGroupV2PendingMember> groupV2PendingMembers = new HashSet<>();
                    HashSet<GroupV2.Permission> ownPermissions = new HashSet<>();
                    for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : ((InvitationReceivedState) startState).serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                        if (Objects.equals(identityAndPermissionsAndDetails.identity, getOwnedIdentity())) {
                            ownPermissions = GroupV2.Permission.fromStrings(identityAndPermissionsAndDetails.permissionStrings);
                            continue;
                        }
                        groupV2PendingMembers.add(new ObvGroupV2.ObvGroupV2PendingMember(
                                identityAndPermissionsAndDetails.identity.getBytes(),
                                GroupV2.Permission.fromStrings(identityAndPermissionsAndDetails.permissionStrings),
                                identityAndPermissionsAndDetails.serializedIdentityDetails
                        ));
                    }

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createGroupV2FrozenInvitationDialog(((InvitationReceivedState) startState).inviterIdentity, new ObvGroupV2(
                            getOwnedIdentity().getBytes(),
                            ((InvitationReceivedState) startState).groupIdentifier,
                            ownPermissions,
                            null,
                            groupV2PendingMembers,
                            ((InvitationReceivedState) startState).serverBlob.serializedGroupDetails,
                            null, null, null
                    )), ((InvitationReceivedState) startState).dialogUuid));
                    ChannelMessageToSend messageToSend = new DialogAcceptGroupInvitationMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            // check if we already joined this group and have a larger group version
            Integer dbGroupVersion = protocolManagerSession.identityDelegate.getGroupV2Version(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);
            if (dbGroupVersion != null && dbGroupVersion >= groupVersion) {
                // we already have a more recent version of this group, ignore the message
                if (startState != null) {
                    return startState;
                } else {
                    return new FinalState();
                }
            }

            ///////////////
            // see what was already collected, and augment it with what we received/what we have in db

            if (dbGroupVersion != null) {
                // if we already joined the group, retrieve blobKeys from db
                GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);
                // add the mainSeed as a "ownedIdentity" candidate...
                invitationCollectedData.addBlobKeysCandidates(getOwnedIdentity(), blobKeys);

                // freeze the group
                protocolManagerSession.identityDelegate.freezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);
            }

            invitationCollectedData.addBlobKeysCandidates(obliviousChannelContactIdentity, blobKeys);

            byte[] serverQueryNonce = getPrng().bytes(16);
            {
                // run the server query to download the server blob
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createGetGroupBlobQuery(groupIdentifier, serverQueryNonce)));
                ChannelMessageToSend messageToSend = new DownloadGroupBlobMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new DownloadingGroupBlobState(
                    groupIdentifier,
                    dialogUuid,
                    invitationCollectedData,
                    lastKnownOwnInvitationNonce,
                    lastKnownOtherGroupMemberIdentities,
                    serverQueryNonce
            );
        }
    }

    public static class ProcessDownloadedGroupDataStep extends ProtocolStep {
        private final DownloadingGroupBlobState startState;
        private final DownloadGroupBlobMessage receivedMessage;

        @SuppressWarnings("unused")
        public ProcessDownloadedGroupDataStep(DownloadingGroupBlobState startState, DownloadGroupBlobMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                if (receivedMessage.serverQueryNonce != null && !Arrays.equals(receivedMessage.serverQueryNonce, startState.serverQueryNonce)) {
                    // this serverQuery response was for another request, ignore it!
                    return startState;
                }
            }

            {
                if (receivedMessage.encryptedServerBlob == null || receivedMessage.logEntries == null || receivedMessage.groupAdminPublicKey == null) {
                    // the server does not have a group with that identifier, there is nothing we can do --> abort the protocol
                    if (receivedMessage.deletedFromServer) {
                        // blob was deleted from server --> delete the group locally too
                        protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
                    }

                    // remove the dialog if any
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                    ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                    // unfreeze the group anyway as we will be in FinalState
                    protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

                    return new FinalState();
                }
            }


            {
                // try to decrypt the downloaded blob
                for (Map.Entry<Identity, Seed> inviterIdentityAndBlobMainSeedCandidate : startState.invitationCollectedData.inviterIdentityAndBlobMainSeedCandidates.entrySet()) {
                    for (Seed blobVersionSeed : startState.invitationCollectedData.blobVersionSeedCandidates) {

                        AuthEncKey authEncKey = GroupV2.getSharedBlobSecretKey(inviterIdentityAndBlobMainSeedCandidate.getValue(), blobVersionSeed);
                        try {
                            byte[] paddedBlobPlaintext = Suite.getAuthEnc(authEncKey).decrypt(authEncKey, receivedMessage.encryptedServerBlob);
                            Encoded[] encodeds = new Encoded(paddedBlobPlaintext).decodeListWithPadding();

                            GroupV2.ServerBlob serverBlob = GroupV2.ServerBlob.of(encodeds[0]);
                            Identity signerIdentity = encodeds[1].decodeIdentity();
                            byte[] signature = encodeds[2].decodeBytes();

                            // check the administrators chain
                            try {
                                serverBlob.administratorsChain.withCheckedIntegrity(serverBlob.administratorsChain.groupUid, signerIdentity);
                            } catch (Exception e) {
                                Logger.w("Downloaded a group blob with invalid administratorsChain");
                                throw new DecodingException();
                            }


                            // check the signature
                            if (!Signature.verify(
                                    Constants.SignatureContext.GROUP_BLOB,
                                    encodeds[0].getBytes(),
                                    signerIdentity,
                                    signature)) {
                                Logger.w("Downloaded a group blob with invalid signature");
                                throw new DecodingException();
                            }

                            // check that admins match the administratorsChain
                            {
                                HashSet<Identity> blobAdmins = new HashSet<>();
                                for (GroupV2.IdentityAndPermissionsAndDetails member : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                                    if (member.permissionStrings.contains(GroupV2.Permission.GROUP_ADMIN.getString())) {
                                        blobAdmins.add(member.identity);
                                    }
                                }
                                HashSet<Identity> chainAdmins = serverBlob.administratorsChain.getAdminIdentities();
                                if (!Objects.equals(blobAdmins, chainAdmins)) {
                                    Logger.w("Downloaded a group blob with non-matching admins in AdministratorsChain");
                                    throw new DecodingException();
                                }
                            }

                            /////////
                            // if we reach this point, we have the right seeds and a valid decrypted blob

                            ///////////////
                            // process the received log to remove people who left the group (including myself sometimes...)
                            serverBlob.consolidateWithLogEntries(startState.groupIdentifier, receivedMessage.logEntries);


                            // check whether I am indeed part of the group
                            GroupV2.IdentityAndPermissionsAndDetails ownIdentityAndPermissions = null;
                            boolean admin = false;
                            for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                                if (getOwnedIdentity().equals(identityAndPermissionsAndDetails.identity)) {
                                    ownIdentityAndPermissions = identityAndPermissionsAndDetails;
                                    // check if I am admin
                                    for (String permissionString : identityAndPermissionsAndDetails.permissionStrings) {
                                        if (GroupV2.Permission.fromString(permissionString) == GroupV2.Permission.GROUP_ADMIN) {
                                            admin = true;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }

                            if (ownIdentityAndPermissions == null) {
                                Logger.w("Downloaded a group blob for a group I am not part of");
                                throw new DecodingException();
                            }

                            ServerAuthenticationPrivateKey groupAdminServerAuthenticationPrivateKey = null;
                            // I am admin, check that I indeed have the groupAdminServerAuthenticationPrivateKey
                            if (admin) {
                                for (ServerAuthenticationPrivateKey serverAuthenticationPrivateKey : startState.invitationCollectedData.groupAdminServerAuthenticationPrivateKeyCandidates) {
                                    if (KeyPair.areKeysMatching(receivedMessage.groupAdminPublicKey, serverAuthenticationPrivateKey)) {
                                        groupAdminServerAuthenticationPrivateKey = serverAuthenticationPrivateKey;
                                        break;
                                    }
                                }

                                if (groupAdminServerAuthenticationPrivateKey == null) {
                                    Logger.d("We were able to decrypt a blob, we are admin, but we do not yet have the groupAdminServerAuthenticationPrivateKey");
                                    throw new DecryptionException();
                                }
                            }



                            ///////////////
                            // from here we have everything:
                            //  - the blob
                            //  - the inviter
                            //  - the keys
                            //  - the leaverIdentities

                            GroupV2.BlobKeys blobKeys = new GroupV2.BlobKeys(
                                    inviterIdentityAndBlobMainSeedCandidate.getValue(),
                                    blobVersionSeed,
                                    groupAdminServerAuthenticationPrivateKey);

                            if (protocolManagerSession.identityDelegate.getGroupV2Version(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier) != null) {
                                // update the group from what we downloaded, and retrieve the list of new members to "ping"
                                List<Identity> newGroupMembers = protocolManagerSession.identityDelegate.updateGroupV2WithNewBlob(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier, serverBlob, blobKeys, false);

                                if (newGroupMembers == null) {
                                    // We were not able to update the group, return null to retry...
                                    return null;
                                }

                                protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

                                // check if a photo download is needed
                                if (serverBlob.serverPhotoInfo != null && protocolManagerSession.identityDelegate.getGroupV2PhotoUrl(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier) == null) {
                                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                            SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                            DOWNLOAD_GROUPS_V2_PHOTO_PROTOCOL_ID,
                                            new UID(getPrng()),
                                            false
                                    );
                                    ChannelMessageToSend messageToSend = new DownloadGroupV2PhotoProtocol.InitialMessage(coreProtocolMessage, startState.groupIdentifier, serverBlob.serverPhotoInfo).generateChannelProtocolMessageToSend();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                }

                                if (!newGroupMembers.isEmpty()) {
                                    // send a ping to all new members to notify them you indeed joined the group
                                    for (Identity groupMemberIdentity : newGroupMembers) {
                                        byte[] pingSignature = protocolManagerSession.identityDelegate.signGroupInvitationNonce(
                                                protocolManagerSession.session,
                                                Constants.SignatureContext.GROUP_JOIN_NONCE,
                                                startState.groupIdentifier,
                                                ownIdentityAndPermissions.groupInvitationNonce,
                                                groupMemberIdentity,
                                                getOwnedIdentity(),
                                                getPrng());

                                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(groupMemberIdentity, getOwnedIdentity()));
                                        ChannelMessageToSend messageToSend = new PingMessage(coreProtocolMessage, startState.groupIdentifier, ownIdentityAndPermissions.groupInvitationNonce, pingSignature, false).generateChannelProtocolMessageToSend();
                                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                    }
                                }

                                return new FinalState();
                            } else {
                                {
                                    // create the accept invitation dialog (or unfreeze the previous invitation)
                                    HashSet<ObvGroupV2.ObvGroupV2PendingMember> groupV2PendingMembers = new HashSet<>();
                                    for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                                        if (identityAndPermissionsAndDetails.identity.equals(getOwnedIdentity())) {
                                            continue;
                                        }
                                        groupV2PendingMembers.add(new ObvGroupV2.ObvGroupV2PendingMember(
                                                identityAndPermissionsAndDetails.identity.getBytes(),
                                                GroupV2.Permission.fromStrings(identityAndPermissionsAndDetails.permissionStrings),
                                                identityAndPermissionsAndDetails.serializedIdentityDetails
                                        ));
                                    }

                                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createGroupV2InvitationDialog(inviterIdentityAndBlobMainSeedCandidate.getKey(), new ObvGroupV2(
                                            getOwnedIdentity().getBytes(),
                                            startState.groupIdentifier,
                                            GroupV2.Permission.fromStrings(ownIdentityAndPermissions.permissionStrings),
                                            null,
                                            groupV2PendingMembers,
                                            serverBlob.serializedGroupDetails,
                                            null, null, null
                                    )), startState.dialogUuid));
                                    ChannelMessageToSend messageToSend = new DialogAcceptGroupInvitationMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                }


                                return new InvitationReceivedState(
                                        startState.groupIdentifier,
                                        startState.dialogUuid,
                                        inviterIdentityAndBlobMainSeedCandidate.getKey(),
                                        serverBlob,
                                        blobKeys);
                            }
                        } catch (DecryptionException | InvalidKeyException ignored) {
                            // it is normal that some seed candidates are not able to decrypt
                            // can also happen if we have the right seeds but the groupAdminServerAuthenticationPrivateKey is missing
                        } catch (DecodingException e) {
                            // we have the right key, but are unable to decode the decrypted blob or the validation of the blob failed --> abort
                            protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

                            // remove the dialog if any
                            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startState.dialogUuid));
                            ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                            return new FinalState();
                        }
                    }
                }
            }

            return new INeedMoreSeedsState(startState.groupIdentifier, startState.dialogUuid, startState.invitationCollectedData, startState.lastKnownOwnInvitationNonce, startState.lastKnownOtherGroupMemberIdentities);
        }
    }


    public static class DoNothingAfterServerQueryStep extends ProtocolStep {
        @SuppressWarnings("unused")
        public DoNothingAfterServerQueryStep(InitialProtocolState startState, DeleteGroupBlobFromServerMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            return new FinalState();
        }
    }

    public static class ProcessPingStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final PingMessage receivedMessage;
        private final boolean propagationNeeded;

        @SuppressWarnings("unused")
        public ProcessPingStep(InitialProtocolState startState, PingMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagationNeeded = true;
        }

        @SuppressWarnings("unused")
        public ProcessPingStep(InitialProtocolState startState, PropagatedPingMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagationNeeded = false;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // check that the protocolInstanceUid matches the groupIdentifier
                if (!getProtocolInstanceUid().equals(receivedMessage.groupIdentifier.computeProtocolInstanceUid())) {
                    return new FinalState();
                }
            }

            {
                // check the message is not a replay
                if (GroupV2SignatureReceived.exists(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature)) {
                    Logger.w("Received a group join ping with a known signature");
                    return new FinalState();
                }
            }

            if (propagationNeeded) {
                // propagate the ping to other own devices, if any
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagatedPingMessage(coreProtocolMessage, receivedMessage.groupIdentifier, receivedMessage.groupMemberInvitationNonce, receivedMessage.signature, receivedMessage.isResponse).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            // check whether the group exists in db
            byte[] ownGroupInvitationNonce = protocolManagerSession.identityDelegate.getGroupV2OwnGroupInvitationNonce(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier);
            if (ownGroupInvitationNonce == null) {
                return new FinalState();
            }

            Identity pingSenderIdentity = null;
            {
                // find the member/pending members that sent the message
                List<Identity> pingSenderCandidates = protocolManagerSession.identityDelegate.getGroupV2MembersAndPendingMembersFromNonce(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier, receivedMessage.groupMemberInvitationNonce);
                if (pingSenderCandidates != null) {
                    for (Identity pingSenderCandidate : pingSenderCandidates) {
                        // check if the signature matches
                        if (Signature.verify(Constants.SignatureContext.GROUP_JOIN_NONCE, receivedMessage.groupIdentifier, receivedMessage.groupMemberInvitationNonce, getOwnedIdentity(), pingSenderCandidate, receivedMessage.signature)) {
                            pingSenderIdentity = pingSenderCandidate;
                            break;
                        }
                    }
                }
            }

            if (pingSenderIdentity == null) {
                return new FinalState();
            }

            {
                // send a response if needed
                if (!receivedMessage.isResponse) {
                    byte[] pingSignature = protocolManagerSession.identityDelegate.signGroupInvitationNonce(
                            protocolManagerSession.session,
                            Constants.SignatureContext.GROUP_JOIN_NONCE,
                            receivedMessage.groupIdentifier,
                            ownGroupInvitationNonce,
                            pingSenderIdentity,
                            getOwnedIdentity(),
                            getPrng());

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(pingSenderIdentity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PingMessage(coreProtocolMessage, receivedMessage.groupIdentifier, ownGroupInvitationNonce, pingSignature, true).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // store the received signature
                GroupV2SignatureReceived.create(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature);
            }

            protocolManagerSession.identityDelegate.moveGroupV2PendingMemberToMembers(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier, pingSenderIdentity);

            return new FinalState();
        }
    }

    public static class ProcessInvitationDialogResponseStep extends ProtocolStep {
        private final ConcreteProtocolState startState;
        private final UUID startDialogUuid;
        private final GroupV2.Identifier groupIdentifier;
        private final boolean propagated;
        private final boolean invitationAccepted;
        private final UUID receivedDialogUuid;
        private final byte[] ownGroupInvitationNonce;
        private final byte[] propagatedOwnGroupInvitationNonce;
        private final List<Identity> groupMembersToNotify;

        @SuppressWarnings("unused")
        public ProcessInvitationDialogResponseStep(InvitationReceivedState startState, DialogAcceptGroupInvitationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.startDialogUuid = startState.dialogUuid;
            this.groupIdentifier = startState.groupIdentifier;
            this.propagated = false;
            this.invitationAccepted = receivedMessage.invitationAccepted;
            this.receivedDialogUuid = receivedMessage.dialogUuid;
            this.propagatedOwnGroupInvitationNonce = null;
            this.groupMembersToNotify = new ArrayList<>();
            byte[] ownGroupInvitationNonce = null;
            for (GroupV2.IdentityAndPermissionsAndDetails groupMember : startState.serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                if (groupMember.identity.equals(getOwnedIdentity())) {
                    ownGroupInvitationNonce = groupMember.groupInvitationNonce;
                    continue;
                }
                groupMembersToNotify.add(groupMember.identity);
            }
            this.ownGroupInvitationNonce = ownGroupInvitationNonce;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationDialogResponseStep(InvitationReceivedState startState, PropagateInvitationDialogResponseMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.startDialogUuid = startState.dialogUuid;
            this.groupIdentifier = startState.groupIdentifier;
            this.propagated = true;
            this.invitationAccepted = receivedMessage.invitationAccepted;
            this.receivedDialogUuid = null;
            this.propagatedOwnGroupInvitationNonce = receivedMessage.ownGroupInvitationNonce;
            this.groupMembersToNotify = new ArrayList<>();
            byte[] ownGroupInvitationNonce = null;
            for (GroupV2.IdentityAndPermissionsAndDetails groupMember : startState.serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                if (groupMember.identity.equals(getOwnedIdentity())) {
                    ownGroupInvitationNonce = groupMember.groupInvitationNonce;
                    continue;
                }
                groupMembersToNotify.add(groupMember.identity);
            }
            this.ownGroupInvitationNonce = ownGroupInvitationNonce;
        }

        @SuppressWarnings("unused")
        public ProcessInvitationDialogResponseStep(DownloadingGroupBlobState startState, DialogAcceptGroupInvitationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.startDialogUuid = startState.dialogUuid;
            this.groupIdentifier = startState.groupIdentifier;
            this.propagated = false;
            this.invitationAccepted = receivedMessage.invitationAccepted;
            this.receivedDialogUuid = receivedMessage.dialogUuid;
            this.propagatedOwnGroupInvitationNonce = null;
            this.ownGroupInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.groupMembersToNotify = startState.lastKnownOtherGroupMemberIdentities == null ? null : Arrays.asList(startState.lastKnownOtherGroupMemberIdentities);
        }

        @SuppressWarnings("unused")
        public ProcessInvitationDialogResponseStep(DownloadingGroupBlobState startState, PropagateInvitationDialogResponseMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.startDialogUuid = startState.dialogUuid;
            this.groupIdentifier = startState.groupIdentifier;
            this.propagated = true;
            this.invitationAccepted = receivedMessage.invitationAccepted;
            this.receivedDialogUuid = null;
            this.propagatedOwnGroupInvitationNonce = receivedMessage.ownGroupInvitationNonce;
            this.ownGroupInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.groupMembersToNotify = startState.lastKnownOtherGroupMemberIdentities == null ? null : Arrays.asList(startState.lastKnownOtherGroupMemberIdentities);
        }

        @SuppressWarnings("unused")
        public ProcessInvitationDialogResponseStep(INeedMoreSeedsState startState, DialogAcceptGroupInvitationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.startDialogUuid = startState.dialogUuid;
            this.groupIdentifier = startState.groupIdentifier;
            this.propagated = false;
            this.invitationAccepted = receivedMessage.invitationAccepted;
            this.receivedDialogUuid = receivedMessage.dialogUuid;
            this.propagatedOwnGroupInvitationNonce = null;
            this.ownGroupInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.groupMembersToNotify = startState.lastKnownOtherGroupMemberIdentities == null ? null : Arrays.asList(startState.lastKnownOtherGroupMemberIdentities);
        }

        @SuppressWarnings("unused")
        public ProcessInvitationDialogResponseStep(INeedMoreSeedsState startState, PropagateInvitationDialogResponseMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.startDialogUuid = startState.dialogUuid;
            this.groupIdentifier = startState.groupIdentifier;
            this.propagated = true;
            this.invitationAccepted = receivedMessage.invitationAccepted;
            this.receivedDialogUuid = null;
            this.propagatedOwnGroupInvitationNonce = receivedMessage.ownGroupInvitationNonce;
            this.ownGroupInvitationNonce = startState.lastKnownOwnInvitationNonce;
            this.groupMembersToNotify = startState.lastKnownOtherGroupMemberIdentities == null ? null : Arrays.asList(startState.lastKnownOtherGroupMemberIdentities);
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!propagated && !Objects.equals(this.startDialogUuid, this.receivedDialogUuid)) {
                // bad dialogUuid, ignore the message
                return startState;
            }

            if (!(startState instanceof InvitationReceivedState) && invitationAccepted) {
                // when not in InvitationReceivedState, only rejects are accepted
                return startState;
            }

            // if we are not part of the group, abort !
            if (this.ownGroupInvitationNonce == null) {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startDialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new FinalState();
            }

            if (propagated && !Arrays.equals(this.propagatedOwnGroupInvitationNonce, ownGroupInvitationNonce)) {
                // propagated response for bad invitation nonce --> ignore the message
                return startState;
            }


            if (!propagated) {
                // propagate the dialog response to other devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateInvitationDialogResponseMessage(coreProtocolMessage, this.invitationAccepted, ownGroupInvitationNonce).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // remove the dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), startDialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            if ((startState instanceof InvitationReceivedState) && invitationAccepted) {
                // force the integrityWasChecked to true
                ((InvitationReceivedState) startState).serverBlob.administratorsChain.integrityWasChecked = true;

                // create the group in db
                boolean success = protocolManagerSession.identityDelegate.createJoinedGroupV2(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier, ((InvitationReceivedState) startState).blobKeys, ((InvitationReceivedState) startState).serverBlob);

                // if success == false, this is not a retry-able failure, so we do nothing
                if (success) {
                    if (((InvitationReceivedState) startState).serverBlob.serverPhotoInfo != null && protocolManagerSession.identityDelegate.getGroupV2PhotoUrl(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier) == null) {
                        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                                SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                DOWNLOAD_GROUPS_V2_PHOTO_PROTOCOL_ID,
                                new UID(getPrng()),
                                false
                        );
                        ChannelMessageToSend messageToSend = new DownloadGroupV2PhotoProtocol.InitialMessage(coreProtocolMessage, groupIdentifier, ((InvitationReceivedState) startState).serverBlob.serverPhotoInfo).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }

                    // send a ping to all members to notify them you indeed joined the group
                    // NOTE: we send the ping even for propagated accepts as we might have missed the ping response to the main device ping
                    for (GroupV2.IdentityAndPermissionsAndDetails groupMember : ((InvitationReceivedState) startState).serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                        if (groupMember.identity.equals(getOwnedIdentity())) {
                            continue;
                        }

                        byte[] pingSignature = protocolManagerSession.identityDelegate.signGroupInvitationNonce(
                                protocolManagerSession.session,
                                Constants.SignatureContext.GROUP_JOIN_NONCE,
                                groupIdentifier,
                                ownGroupInvitationNonce,
                                groupMember.identity,
                                getOwnedIdentity(),
                                getPrng());

                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(groupMember.identity, getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PingMessage(coreProtocolMessage, groupIdentifier, ownGroupInvitationNonce, pingSignature, false).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }
                }

                return new FinalState();
            } else {
                if (propagated) {
                    return new FinalState();
                } else if (groupMembersToNotify != null) {
                    // only put a server log and notify others for the non-propagated response
                    byte[] leaveSignature = protocolManagerSession.identityDelegate.signGroupInvitationNonce(
                            protocolManagerSession.session,
                            Constants.SignatureContext.GROUP_LEAVE_NONCE,
                            groupIdentifier,
                            ownGroupInvitationNonce,
                            null,
                            getOwnedIdentity(),
                            getPrng());

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createPutGroupLogQuery(groupIdentifier, leaveSignature)));
                    ChannelMessageToSend messageToSend = new PutGroupLogOnServerMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                    return new RejectingInvitationOrLeavingGroupState(groupIdentifier, groupMembersToNotify);
                } else {
                    // this should normally never happen
                    return startState;
                }
            }
        }
    }


    public static class NotifyMembersOfRejectionOrGroupLeftStep extends ProtocolStep {
        private final RejectingInvitationOrLeavingGroupState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final PutGroupLogOnServerMessage receivedMessage;

        @SuppressWarnings("unused")
        public NotifyMembersOfRejectionOrGroupLeftStep(RejectingInvitationOrLeavingGroupState startState, PutGroupLogOnServerMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            for (Identity groupMember : startState.groupMembersToNotify) {
                // send rejection/left group update message
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(groupMember, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new InvitationRejectedBroadcastMessage(coreProtocolMessage, startState.groupIdentifier).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinalState();
        }
    }


    public static class InitiateBlobReDownloadStep extends ProtocolStep {
        private final ConcreteProtocolState startState;
        private final GroupV2.Identifier groupIdentifier;
        private final UUID dialogUuid;
        private final GroupV2.InvitationCollectedData invitationCollectedData;
        private final boolean propagationNeeded;


        @SuppressWarnings("unused")
        public InitiateBlobReDownloadStep(InitialProtocolState startState, GroupReDownloadInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = null;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.dialogUuid = UUID.randomUUID();
            this.invitationCollectedData = null;
            this.propagationNeeded = true;
        }

        @SuppressWarnings("unused")
        public InitiateBlobReDownloadStep(InitialProtocolState startState, InvitationRejectedBroadcastMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = null;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.dialogUuid = UUID.randomUUID();
            this.invitationCollectedData = null;
            this.propagationNeeded = true;
        }

        @SuppressWarnings("unused")
        public InitiateBlobReDownloadStep(InitialProtocolState startState, PropagateInvitationRejectedMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = null;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.dialogUuid = UUID.randomUUID();
            this.invitationCollectedData = null;
            this.propagationNeeded = false;
        }

        @SuppressWarnings("unused")
        public InitiateBlobReDownloadStep(InvitationReceivedState startState, InvitationRejectedBroadcastMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.dialogUuid = startState.dialogUuid;
            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.invitationCollectedData.addBlobKeysCandidates(startState.inviterIdentity, startState.blobKeys);
            this.propagationNeeded = true;
        }

        @SuppressWarnings("unused")
        public InitiateBlobReDownloadStep(InvitationReceivedState startState, PropagateInvitationRejectedMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.dialogUuid = startState.dialogUuid;
            this.invitationCollectedData = new GroupV2.InvitationCollectedData();
            this.invitationCollectedData.addBlobKeysCandidates(startState.inviterIdentity, startState.blobKeys);
            this.propagationNeeded = false;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // check that the protocolInstanceUid matches the groupIdentifier
                if (!getProtocolInstanceUid().equals(groupIdentifier.computeProtocolInstanceUid())) {
                    if (startState != null) {
                        return startState;
                    } else {
                        return new FinalState();
                    }
                }
            }

            // propagate the message if needed
            if (propagationNeeded) {
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateInvitationRejectedMessage(coreProtocolMessage, groupIdentifier).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }


            if (invitationCollectedData == null) {
                // fetch the blobKeys from DB
                GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);
                if (blobKeys == null) {
                    if (startState != null) {
                        return startState;
                    } else {
                        return new FinalState();
                    }
                }

                protocolManagerSession.identityDelegate.freezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), groupIdentifier);

                byte[] serverQueryNonce = getPrng().bytes(16);
                {
                    // run the server query to download the server blob
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createGetGroupBlobQuery(groupIdentifier, serverQueryNonce)));
                    ChannelMessageToSend messageToSend = new DownloadGroupBlobMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                GroupV2.InvitationCollectedData invitationCollectedData = new GroupV2.InvitationCollectedData();
                invitationCollectedData.addBlobKeysCandidates(getOwnedIdentity(), blobKeys);

                return new DownloadingGroupBlobState(
                        groupIdentifier,
                        dialogUuid,
                        invitationCollectedData,
                        null,
                        null,
                        serverQueryNonce
                );
            } else if (startState instanceof InvitationReceivedState) {
                byte[] ownInvitationNonce = null;
                List<Identity> otherGroupMemberIdentities = new ArrayList<>();
                // We were in InvitationReceivedState, freeze the invitation dialog
                {
                    HashSet<ObvGroupV2.ObvGroupV2PendingMember> groupV2PendingMembers = new HashSet<>();
                    HashSet<GroupV2.Permission> ownPermissions = new HashSet<>();
                    for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : ((InvitationReceivedState) startState).serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                        if (Objects.equals(identityAndPermissionsAndDetails.identity, getOwnedIdentity())) {
                            ownPermissions = GroupV2.Permission.fromStrings(identityAndPermissionsAndDetails.permissionStrings);
                            ownInvitationNonce = identityAndPermissionsAndDetails.groupInvitationNonce;
                            continue;
                        }
                        otherGroupMemberIdentities.add(identityAndPermissionsAndDetails.identity);
                        groupV2PendingMembers.add(new ObvGroupV2.ObvGroupV2PendingMember(
                                identityAndPermissionsAndDetails.identity.getBytes(),
                                GroupV2.Permission.fromStrings(identityAndPermissionsAndDetails.permissionStrings),
                                identityAndPermissionsAndDetails.serializedIdentityDetails
                        ));
                    }

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createGroupV2FrozenInvitationDialog(((InvitationReceivedState) startState).inviterIdentity, new ObvGroupV2(
                            getOwnedIdentity().getBytes(),
                            ((InvitationReceivedState) startState).groupIdentifier,
                            ownPermissions,
                            null,
                            groupV2PendingMembers,
                            ((InvitationReceivedState) startState).serverBlob.serializedGroupDetails,
                            null, null, null
                    )), ((InvitationReceivedState) startState).dialogUuid));
                    ChannelMessageToSend messageToSend = new DialogAcceptGroupInvitationMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                byte[] serverQueryNonce = getPrng().bytes(16);
                {
                    // run the server query to re-download the server blob
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createGetGroupBlobQuery(groupIdentifier, serverQueryNonce)));
                    ChannelMessageToSend messageToSend = new DownloadGroupBlobMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                return new DownloadingGroupBlobState(
                        groupIdentifier,
                        dialogUuid,
                        invitationCollectedData,
                        ownInvitationNonce,
                        otherGroupMemberIdentities.toArray(new Identity[0]),
                        serverQueryNonce
                );
            } else {
                return new FinalState();
            }
        }
    }

    public static class InitiateGroupUpdateStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final GroupUpdateInitialMessage receivedMessage;

        public InitiateGroupUpdateStep(InitialProtocolState startState, GroupUpdateInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // check that we indeed know the group and have the admin private key for group updates
            GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier);
            boolean adminKeyIsMissing = blobKeys == null || blobKeys.groupAdminServerAuthenticationPrivateKey == null;

            // check that we did not remove ourself, or our GROUP_ADMIN permission in the changeSet
            boolean removedOurself = receivedMessage.changeSet.removedMembers.contains(getOwnedIdentity().getBytes());
            HashSet<GroupV2.Permission> ownPermissions = receivedMessage.changeSet.permissionChanges.get(new ObvBytesKey(getOwnedIdentity().getBytes()));
            boolean removedOurAdminPermission = ownPermissions != null && !ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN);

            if (adminKeyIsMissing || removedOurself || removedOurAdminPermission) {
                // invalid update, discard the changeSet and notify (for app)
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_OWNED_IDENTITY_KEY, getOwnedIdentity());
                userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_GROUP_IDENTIFIER_KEY, receivedMessage.groupIdentifier);
                userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_ERROR_KEY, true);
                protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED, userInfo);

                return new FinalState();
            }

            protocolManagerSession.identityDelegate.freezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier);

            // request group lock on server
            byte[] lockNonce = getPrng().bytes(Constants.GROUP_V2_LOCK_NONCE_LENGTH);
            {
                byte[] signature = Signature.sign(Constants.SignatureContext.GROUP_LOCK_ON_SERVER, lockNonce, blobKeys.groupAdminServerAuthenticationPrivateKey.getSignaturePrivateKey(), getPrng());

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createBlobLockQuery(receivedMessage.groupIdentifier, lockNonce, signature)));
                ChannelMessageToSend messageToSend = new RequestLockMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            // wait for the lock
            return new WaitingForLockState(receivedMessage.groupIdentifier, receivedMessage.changeSet, lockNonce, 0);
        }
    }


    public static class PrepareBlobForGroupUpdateStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final WaitingForLockState startState;
        private final RequestLockMessage receivedMessage;

        public PrepareBlobForGroupUpdateStep(WaitingForLockState startState, RequestLockMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        private void unfreezeAndNotifyUpdateFailed(ProtocolManagerSession protocolManagerSession, boolean error) throws SQLException {
            protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_OWNED_IDENTITY_KEY, getOwnedIdentity());
            userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_GROUP_IDENTIFIER_KEY, startState.groupIdentifier);
            userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_ERROR_KEY, error);
            protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED, userInfo);
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                if (receivedMessage.serverQueryNonce != null && !Arrays.equals(receivedMessage.serverQueryNonce, startState.lockNonce)) {
                    // this serverQuery response was for another request, ignore it!
                    return startState;
                }
            }

            if (receivedMessage.encryptedServerBlob == null || receivedMessage.logEntries == null || receivedMessage.groupAdminPublicKey == null) {
                unfreezeAndNotifyUpdateFailed(protocolManagerSession, true);
                return new FinalState();
            }

            GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
            if (blobKeys == null) {
                unfreezeAndNotifyUpdateFailed(protocolManagerSession, true);
                return new FinalState();
            }

            GroupV2.ServerBlob initialServerBlob;
            try {
                AuthEncKey authEncKey = GroupV2.getSharedBlobSecretKey(blobKeys.blobMainSeed, blobKeys.blobVersionSeed);
                byte[] paddedBlobPlaintext = Suite.getAuthEnc(authEncKey).decrypt(authEncKey, receivedMessage.encryptedServerBlob);
                Encoded[] encodeds = new Encoded(paddedBlobPlaintext).decodeListWithPadding();

                initialServerBlob = GroupV2.ServerBlob.of(encodeds[0]);
                Identity signerIdentity = encodeds[1].decodeIdentity();
                byte[] signature = encodeds[2].decodeBytes();

                // check the administrators chain
                try {
                    initialServerBlob.administratorsChain.withCheckedIntegrity(initialServerBlob.administratorsChain.groupUid, signerIdentity);
                } catch (Exception e) {
                    Logger.w("Downloaded a group blob with invalid administratorsChain");
                    throw new DecodingException();
                }


                // check the signature
                if (!Signature.verify(
                        Constants.SignatureContext.GROUP_BLOB,
                        encodeds[0].getBytes(),
                        signerIdentity,
                        signature)) {
                    Logger.w("Downloaded a group blob with invalid signature");
                    throw new DecodingException();
                }

                // check that admins match the administratorsChain
                {
                    HashSet<Identity> blobAdmins = new HashSet<>();
                    for (GroupV2.IdentityAndPermissionsAndDetails member : initialServerBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                        if (member.permissionStrings.contains(GroupV2.Permission.GROUP_ADMIN.getString())) {
                            blobAdmins.add(member.identity);
                        }
                    }
                    HashSet<Identity> chainAdmins = initialServerBlob.administratorsChain.getAdminIdentities();
                    if (!Objects.equals(blobAdmins, chainAdmins)) {
                        Logger.w("Downloaded a group blob with non-matching admins in AdministratorsChain");
                        throw new DecodingException();
                    }

                    // also check we are still administrator of the group
                    if (!blobAdmins.contains(getOwnedIdentity())) {
                        Logger.w("We are no longer admin of a group we wanted to update --> aborting");
                        throw new DecodingException();
                    }
                }
            } catch (Exception e) {
                Logger.w("Failed to decrypt/verify server blob during update");
                e.printStackTrace();
                unfreezeAndNotifyUpdateFailed(protocolManagerSession, true);
                return new FinalState();
            }

            // consolidate the blob with the received log entries
            initialServerBlob.consolidateWithLogEntries(startState.groupIdentifier, receivedMessage.logEntries);


            // check if there is anything to change in the blob, based on the received changeSet
            boolean changed = false;

            HashSet<GroupV2.IdentityAndPermissionsAndDetails> members = new HashSet<>(initialServerBlob.groupMemberIdentityAndPermissionsAndDetailsList);
            HashSet<Identity> membersToInvite = new HashSet<>();
            HashMap<Identity, byte[]> membersToKick = new HashMap<>();
            {
                // removed members
                if (!startState.changeSet.removedMembers.isEmpty()) {
                    HashSet<Identity> removedMembersSet = new HashSet<>();
                    for (byte[] bytesIdentity : startState.changeSet.removedMembers) {
                        try {
                            removedMembersSet.add(Identity.of(bytesIdentity));
                        } catch (DecodingException ignored) {}
                    }

                    List<GroupV2.IdentityAndPermissionsAndDetails> toRemove = new ArrayList<>();
                    for (GroupV2.IdentityAndPermissionsAndDetails member : members) {
                        if (removedMembersSet.contains(member.identity)) {
                            toRemove.add(member);
                        }
                    }

                    for (GroupV2.IdentityAndPermissionsAndDetails member : toRemove) {
                        changed = true;
                        members.remove(member);
                        membersToKick.put(member.identity, member.groupInvitationNonce);
                    }
                }


                // permission changes
                if (!startState.changeSet.permissionChanges.isEmpty()) {
                    for (GroupV2.IdentityAndPermissionsAndDetails member : members) {
                        try {
                            HashSet<GroupV2.Permission> newPermissions = startState.changeSet.permissionChanges.get(new ObvBytesKey(member.identity.getBytes()));
                            if (newPermissions == null) {
                                continue;
                            }
                            HashSet<GroupV2.Permission> initialPermissions = new HashSet<>();
                            for (String permissionString : member.permissionStrings) {
                                GroupV2.Permission permission = GroupV2.Permission.fromString(permissionString);
                                if (permission != null) {
                                    initialPermissions.add(permission);
                                }
                            }
                            if (Objects.equals(newPermissions, initialPermissions)) {
                                continue;
                            }

                            changed = true;
                            member.permissionStrings.clear();
                            for (GroupV2.Permission permission : newPermissions) {
                                member.permissionStrings.add(permission.getString());
                            }
                        } catch (Exception ignored) {}
                    }
                }

                // use this opportunity to update any group member serialized details
                HashSet<GroupV2.IdentityAndPermissionsAndDetails> updatedMembers = new HashSet<>();
                for (GroupV2.IdentityAndPermissionsAndDetails member : members) {
                    String serializedDetails;
                    if (Objects.equals(member.identity, getOwnedIdentity())) {
                        serializedDetails = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                    } else{
                        serializedDetails = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), member.identity);
                    }
                    if (serializedDetails != null && !Objects.equals(member.serializedIdentityDetails, serializedDetails)) {
                            GroupV2.IdentityAndPermissionsAndDetails updatedMember = new GroupV2.IdentityAndPermissionsAndDetails(member.identity, member.permissionStrings, serializedDetails, member.groupInvitationNonce);
                            updatedMembers.add(updatedMember);
                    }
                }
                for (GroupV2.IdentityAndPermissionsAndDetails updatedMember : updatedMembers) {
                    // we do not mark any change --> only update the server blob if there is a "real" change
                    members.remove(updatedMember); // remove the old element
                    members.add(updatedMember); // insert the updated one
                }


                // added members
                for (Map.Entry<ObvBytesKey, HashSet<GroupV2.Permission>> entry : startState.changeSet.addedMembersWithPermissions.entrySet()) {
                    try {
                        Identity memberIdentity = Identity.of(entry.getKey().getBytes());
                        List<String> permissionStrings = new ArrayList<>();
                        for (GroupV2.Permission permission : entry.getValue()) {
                            permissionStrings.add(permission.getString());
                        }
                        String serializedContactDetails = protocolManagerSession.identityDelegate.getSerializedPublishedDetailsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), memberIdentity);
                        if (serializedContactDetails == null) {
                            continue;
                        }

                        GroupV2.IdentityAndPermissionsAndDetails newMember = new GroupV2.IdentityAndPermissionsAndDetails(
                                memberIdentity,
                                permissionStrings,
                                serializedContactDetails,
                                getPrng().bytes(Constants.GROUP_V2_INVITATION_NONCE_LENGTH)
                        );
                        if (members.contains(newMember)) {
                            continue;
                        }

                        changed = true;
                        members.add(newMember);
                        membersToInvite.add(memberIdentity);
                    } catch (Exception ignored) {}
               }

                // group details
                if (startState.changeSet.updatedSerializedGroupDetails != null && !Objects.equals(startState.changeSet.updatedSerializedGroupDetails, initialServerBlob.serializedGroupDetails)) {
                    changed = true;
                }

                // group photoUrl
                if (startState.changeSet.updatedPhotoUrl != null && (initialServerBlob.serverPhotoInfo != null || startState.changeSet.updatedPhotoUrl.length() > 0)) {
                    changed = true;
                }
            }

            if (!changed) {
                // nothing changed --> nothing to upload, discard the changeSet and notify (for app)
                unfreezeAndNotifyUpdateFailed(protocolManagerSession, false);
                return new FinalState();
            }

            // check that we indeed have an oblivious channel with all membersToInvite
            for (Identity identity : membersToInvite) {
                if (protocolManagerSession.channelDelegate.getConfirmedObliviousChannelDeviceUids(protocolManagerSession.session, getOwnedIdentity(), identity).length == 0) {
                    // a new member does not have a channel --> discard the changeSet and notify (for app)
                    unfreezeAndNotifyUpdateFailed(protocolManagerSession, true);
                    return new FinalState();
                }
            }

            ////////////////////////
            // if we reach this point, there are some changes to publish on the server


            // create the new ServerBlob
            GroupV2.ServerBlob updatedServerBlob;
            String absolutePhotoUrlToUpload = null;
            boolean adminKeyChangeRequired = false;
            {
                GroupV2.AdministratorsChain updatedAdministratorsChain;
                {
                    HashSet<Identity> blobAdmins = new HashSet<>();
                    for (GroupV2.IdentityAndPermissionsAndDetails member : members) {
                        if (member.permissionStrings.contains(GroupV2.Permission.GROUP_ADMIN.getString())) {
                            blobAdmins.add(member.identity);
                        }
                    }
                    HashSet<Identity> chainAdmins = initialServerBlob.administratorsChain.getAdminIdentities();
                    if (Objects.equals(blobAdmins, chainAdmins)) {
                        updatedAdministratorsChain = initialServerBlob.administratorsChain;
                    } else {
                        // the admins have changed --> we need to add a block to the chain
                        if (!blobAdmins.containsAll(chainAdmins)) {
                            // some admins were removed --> key change required
                            adminKeyChangeRequired = true;
                        }

                        blobAdmins.remove(getOwnedIdentity());
                        updatedAdministratorsChain = initialServerBlob.administratorsChain.buildNewChainByAppendingABlock(
                                protocolManagerSession.session,
                                protocolManagerSession.identityDelegate,
                                getOwnedIdentity(),
                                blobAdmins.toArray(new Identity[0]),
                                getPrng()
                        );
                    }
                }

                String updatedSerializedGroupDetails;
                if (startState.changeSet.updatedSerializedGroupDetails != null) {
                    updatedSerializedGroupDetails = startState.changeSet.updatedSerializedGroupDetails;
                } else {
                    updatedSerializedGroupDetails = initialServerBlob.serializedGroupDetails;
                }

                GroupV2.ServerPhotoInfo updatedServerPhotoInfo;
                if (startState.changeSet.updatedPhotoUrl != null && startState.changeSet.updatedPhotoUrl.length() == 0) {
                    // photo was removed
                    updatedServerPhotoInfo = null;
                } else if (startState.changeSet.updatedPhotoUrl != null) {
                    // new photo url
                    absolutePhotoUrlToUpload = startState.changeSet.updatedPhotoUrl;
                    updatedServerPhotoInfo = new GroupV2.ServerPhotoInfo(
                            getOwnedIdentity(),
                            new UID(getPrng()),
                            Suite.getDefaultAuthEnc(0).generateKey(getPrng()));
                } else if (initialServerBlob.serverPhotoInfo == null) {
                    // no update and there was no photo
                    updatedServerPhotoInfo = null;
                } else if (Objects.equals(initialServerBlob.serverPhotoInfo.serverPhotoIdentity, getOwnedIdentity())) {
                    // there was a photo and we were the owner --> no need to touch it
                    updatedServerPhotoInfo = initialServerBlob.serverPhotoInfo;
                } else {
                    // there was a photo, from some other administrator, check we have the photo at hand
                    absolutePhotoUrlToUpload = protocolManagerSession.identityDelegate.getGroupV2PhotoUrl(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
                    if (absolutePhotoUrlToUpload == null) {
                        // we don't have the photo --> remove it from the group
                        updatedServerPhotoInfo = null;
                    } else {
                        // convert the photoUrl to an absolute path
                        absolutePhotoUrlToUpload = new File(protocolManagerSession.engineBaseDirectory, absolutePhotoUrlToUpload).getPath();

                        updatedServerPhotoInfo = new GroupV2.ServerPhotoInfo(
                                getOwnedIdentity(),
                                new UID(getPrng()),
                                Suite.getDefaultAuthEnc(0).generateKey(getPrng()));
                    }
                }
                updatedServerBlob = new GroupV2.ServerBlob(
                        updatedAdministratorsChain,
                        members,
                        initialServerBlob.version + 1,
                        updatedSerializedGroupDetails,
                        updatedServerPhotoInfo
                );
            }


            KeyPair groupAdminServerAuthenticationKeyPair;
            if (adminKeyChangeRequired) {
                groupAdminServerAuthenticationKeyPair = Suite.generateServerAuthenticationKeyPair(null, getPrng());
                if (groupAdminServerAuthenticationKeyPair == null) {
                    throw new Exception();
                }
            } else {
                groupAdminServerAuthenticationKeyPair = new KeyPair(receivedMessage.groupAdminPublicKey, blobKeys.groupAdminServerAuthenticationPrivateKey);
            }


            GroupV2.BlobKeys updatedBlobKeys = new GroupV2.BlobKeys(blobKeys.blobMainSeed, new Seed(getPrng()), (ServerAuthenticationPrivateKey) groupAdminServerAuthenticationKeyPair.getPrivateKey());


            EncryptedBytes encryptedBlob;
            {
                // compute the encoded, signed, padded, and encrypted blob from the ServerBlob we have
                Encoded encodedServerBlob = updatedServerBlob.encode();
                byte[] signature = protocolManagerSession.identityDelegate.signBlock(
                        protocolManagerSession.session,
                        Constants.SignatureContext.GROUP_BLOB,
                        encodedServerBlob.getBytes(),
                        getOwnedIdentity(),
                        getPrng()
                );

                Encoded encodedSignedBlob = Encoded.of(new Encoded[]{
                        encodedServerBlob,
                        Encoded.of(getOwnedIdentity()),
                        Encoded.of(signature),
                });

                int unpaddedLength = encodedSignedBlob.getBytes().length;
                int paddedLength = (1 + ((unpaddedLength - 1) >> 12)) << 12; // we pad to the smallest multiple of 4096 larger than the actual length

                byte[] paddedBlobPlaintext = new byte[paddedLength];
                System.arraycopy(encodedSignedBlob.getBytes(), 0, paddedBlobPlaintext, 0, unpaddedLength);
                AuthEncKey blobEncryptionKey = GroupV2.getSharedBlobSecretKey(updatedBlobKeys.blobMainSeed, updatedBlobKeys.blobVersionSeed);
                encryptedBlob = Suite.getAuthEnc(blobEncryptionKey).encrypt(blobEncryptionKey, paddedBlobPlaintext, getPrng());
            }

            {
                // upload the encrypted blob

                Encoded encodedPublicKey = Encoded.of(groupAdminServerAuthenticationKeyPair.getPublicKey());

                byte[] dataToSign = new byte[startState.lockNonce.length + encryptedBlob.length + encodedPublicKey.getBytes().length];
                System.arraycopy(startState.lockNonce, 0, dataToSign, 0, startState.lockNonce.length);
                System.arraycopy(encryptedBlob.getBytes(), 0, dataToSign, startState.lockNonce.length, encryptedBlob.length);
                System.arraycopy(encodedPublicKey.getBytes(), 0, dataToSign, startState.lockNonce.length + encryptedBlob.length, encodedPublicKey.getBytes().length);

                byte[] signature = Signature.sign(Constants.SignatureContext.GROUP_UPDATE_ON_SERVER, dataToSign, blobKeys.groupAdminServerAuthenticationPrivateKey.getSignaturePrivateKey(), getPrng());

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createUpdateGroupBlobQuery(startState.groupIdentifier, startState.lockNonce, encryptedBlob, encodedPublicKey, signature)));
                ChannelMessageToSend messageToSend = new UploadGroupBlobMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new UploadingUpdatedGroupBlobState(
                    startState.groupIdentifier,
                    startState.changeSet,
                    updatedServerBlob,
                    updatedBlobKeys,
                    membersToKick,
                    absolutePhotoUrlToUpload,
                    startState.failedUploadCounter
            );
        }
    }


    public static class ProcessGroupUpdateBlobUploadResponseStep extends ProtocolStep {
        private final UploadingUpdatedGroupBlobState startState;
        private final int uploadResult;

        @SuppressWarnings("unused")
        public ProcessGroupUpdateBlobUploadResponseStep(UploadingUpdatedGroupBlobState startState, UploadGroupBlobMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.uploadResult = receivedMessage.uploadResult;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (uploadResult == 2 || (uploadResult == 1 && startState.failedUploadCounter > 9)) { // definitive fail
                protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_OWNED_IDENTITY_KEY, getOwnedIdentity());
                userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_GROUP_IDENTIFIER_KEY, startState.groupIdentifier);
                userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_ERROR_KEY, true);
                protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED, userInfo);

                return new FinalState();
            } else if (uploadResult == 1) { // retry-able fail
                // check that we still know the group and have the admin private key for group updates
                GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
                if (blobKeys == null || blobKeys.groupAdminServerAuthenticationPrivateKey == null) {
                    // we don't have the key to update on server, discard the changeSet and notify (for app)
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_OWNED_IDENTITY_KEY, getOwnedIdentity());
                    userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_GROUP_IDENTIFIER_KEY, startState.groupIdentifier);
                    userInfo.put(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_ERROR_KEY, true);
                    protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED, userInfo);

                    protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

                    return new FinalState();
                }


                // request a new group lock on server
                byte[] lockNonce = getPrng().bytes(Constants.GROUP_V2_LOCK_NONCE_LENGTH);
                {
                    byte[] signature = Signature.sign(Constants.SignatureContext.GROUP_LOCK_ON_SERVER, lockNonce, blobKeys.groupAdminServerAuthenticationPrivateKey.getSignaturePrivateKey(), getPrng());

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createBlobLockQuery(startState.groupIdentifier, lockNonce, signature)));
                    ChannelMessageToSend messageToSend = new RequestLockMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                // increment fail counter and wait for the lock
                return new WaitingForLockState(startState.groupIdentifier, startState.changeSet, lockNonce, startState.failedUploadCounter + 1);
            }

            
            if (startState.absolutePhotoUrlToUpload == null) {
                // if there is no photo to upload, post a message to initiate the finalization of the group update
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new FinalizeGroupUpdateMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            } else {
                // upload the group photo if needed
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createPutUserDataQuery(getOwnedIdentity(), startState.updatedBlob.serverPhotoInfo.serverPhotoLabel, startState.absolutePhotoUrlToUpload, startState.updatedBlob.serverPhotoInfo.serverPhotoKey)));
                ChannelMessageToSend messageToSend = new UploadGroupPhotoMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            // return an uploading photo state in all cases, even if there is no photo to upload
            return new UploadingUpdatedGroupPhotoState(startState.groupIdentifier, startState.changeSet, startState.updatedBlob, startState.updatedBlobKeys, startState.membersToKick, startState.absolutePhotoUrlToUpload);
        }
    }

    public static class ProcessGroupUpdatePhotoUploadResponseStep extends ProtocolStep {
        private final UploadingUpdatedGroupPhotoState startState;

        @SuppressWarnings("unused")
        public ProcessGroupUpdatePhotoUploadResponseStep(UploadingUpdatedGroupPhotoState startState, UploadGroupPhotoMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // post a message to initiate the finalization of the group update
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()));
            ChannelMessageToSend messageToSend = new FinalizeGroupUpdateMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return startState;
        }
    }


    public static class FinalizeGroupUpdateStep extends ProtocolStep {
        private final UploadingUpdatedGroupPhotoState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final FinalizeGroupUpdateMessage receivedMessage;

        public FinalizeGroupUpdateStep(UploadingUpdatedGroupPhotoState startState, FinalizeGroupUpdateMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // validate integrity of the chain so that the IdentityManager accepts it
            startState.updatedBlob.administratorsChain.withCheckedIntegrity(startState.groupIdentifier.groupUid, null);
            List<Identity> updateOutput = protocolManagerSession.identityDelegate.updateGroupV2WithNewBlob(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier, startState.updatedBlob, startState.updatedBlobKeys, true);

            if (updateOutput == null) {
                // update failed, return null to try again
                return null;
            }

            ////////////////////////
            // update successful
            //  - unfreeze the group
            //  - notify all members of new keys and invite new members to the group
            //  - kick removed members
            //  - copy the local photo to the IdentityManager

            protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);


            {
                // for each group member & pending member, send
                //  - for members with an oblivious channel the main seed
                //  - the version seed for everyone
                //  - for admins the groupAdmin private key

                for (GroupV2.IdentityAndPermissionsAndDetails groupMember : startState.updatedBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                    UID[] contactDeviceUidsWithChannel = protocolManagerSession.channelDelegate.getConfirmedObliviousChannelDeviceUids(protocolManagerSession.session, getOwnedIdentity(), groupMember.identity);
                    boolean isAdmin = groupMember.permissionStrings.contains(GroupV2.Permission.GROUP_ADMIN.getString());

                    if (contactDeviceUidsWithChannel.length > 0) {
                        // send through oblivious channel
                        GroupV2.BlobKeys keysToSend;
                        if (isAdmin) {
                            keysToSend = startState.updatedBlobKeys;
                        } else {
                            keysToSend = new GroupV2.BlobKeys(
                                    startState.updatedBlobKeys.blobMainSeed,
                                    startState.updatedBlobKeys.blobVersionSeed,
                                    null
                            );
                        }

                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(groupMember.identity, getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new InvitationOrMembersUpdateMessage(coreProtocolMessage, startState.groupIdentifier, startState.updatedBlob.version, keysToSend, contactDeviceUidsWithChannel).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } else {
                        // send through broadcast channel
                        GroupV2.BlobKeys keysToSend = new GroupV2.BlobKeys(
                                null,
                                startState.updatedBlobKeys.blobVersionSeed,
                                isAdmin ? startState.updatedBlobKeys.groupAdminServerAuthenticationPrivateKey : null
                        );

                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(groupMember.identity, getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new InvitationOrMembersUpdateBroadcastMessage(coreProtocolMessage, startState.groupIdentifier, startState.updatedBlob.version, keysToSend).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }
                }
            }

            if (!startState.membersToKick.isEmpty()) {
                // compute the encrypted administrators chain
                byte[] chainPlaintext = startState.updatedBlob.administratorsChain.encode().getBytes();
                AuthEncKey encryptionKey = (AuthEncKey) Suite.getKDF(KDF.KDF_SHA256).gen(startState.updatedBlobKeys.blobMainSeed, Suite.getDefaultAuthEnc(0).getKDFDelegate())[0];
                EncryptedBytes encryptedChain = Suite.getAuthEnc(encryptionKey).encrypt(encryptionKey, chainPlaintext, getPrng());

                // kick removed members
                for (Map.Entry<Identity, byte[]> entry : startState.membersToKick.entrySet()) {
                    byte[] dataToSign = new byte[encryptedChain.length + entry.getValue().length];
                    System.arraycopy(encryptedChain.getBytes(), 0, dataToSign, 0, encryptedChain.length);
                    System.arraycopy(entry.getValue(), 0, dataToSign, encryptedChain.length, entry.getValue().length);

                    byte[] signature = protocolManagerSession.identityDelegate.signBlock(protocolManagerSession.session, Constants.SignatureContext.GROUP_KICK, dataToSign, getOwnedIdentity(), getPrng());

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(entry.getKey(), getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new KickMessage(coreProtocolMessage, startState.groupIdentifier, encryptedChain, signature).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            // copy the photo to the IdentityManager
            if (startState.absolutePhotoUrlToUpload != null) {
                protocolManagerSession.identityDelegate.setUpdatedGroupV2PhotoUrl(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier, startState.updatedBlob.version, startState.absolutePhotoUrlToUpload);
            }

            return new FinalState();
        }
    }


    public static class GetKickedStep extends ProtocolStep {
        private final ConcreteProtocolState startState;
        private final KickMessage receivedMessage;
        private final boolean propagated;

        @SuppressWarnings("unused")
        public GetKickedStep(InitialProtocolState startState, KickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = false;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(InvitationReceivedState startState, KickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = false;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(DownloadingGroupBlobState startState, KickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = false;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(INeedMoreSeedsState startState, KickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = false;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(WaitingForLockState startState, KickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = false;
        }


        @SuppressWarnings("unused")
        public GetKickedStep(InitialProtocolState startState, PropagatedKickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = true;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(InvitationReceivedState startState, PropagatedKickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = true;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(DownloadingGroupBlobState startState, PropagatedKickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = true;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(INeedMoreSeedsState startState, PropagatedKickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = true;
        }

        @SuppressWarnings("unused")
        public GetKickedStep(WaitingForLockState startState, PropagatedKickMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            this.propagated = true;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // first check that the protocolInstanceUid matches the groupIdentifier
                if (!getProtocolInstanceUid().equals(receivedMessage.groupIdentifier.computeProtocolInstanceUid())) {
                    return startState;
                }
            }

            if (!propagated) {
                // propagate the kick message
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagatedKickMessage(coreProtocolMessage, receivedMessage.groupIdentifier, receivedMessage.encryptedAdministratorsChain, receivedMessage.signature).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // check the kick message is valid
                GroupV2.BlobKeys blobKeys;
                GroupV2.AdministratorsChain knownAdministratorsChain;
                byte[] invitationNonce = null;
                if (startState instanceof InvitationReceivedState) {
                    blobKeys = ((InvitationReceivedState) startState).blobKeys;
                    knownAdministratorsChain = ((InvitationReceivedState) startState).serverBlob.administratorsChain;
                    GroupV2.IdentityAndPermissionsAndDetails ownIdentityAndPermissionsAndDetails = null;
                    for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : ((InvitationReceivedState) startState).serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                        if (identityAndPermissionsAndDetails.identity.equals(getOwnedIdentity())) {
                            ownIdentityAndPermissionsAndDetails = identityAndPermissionsAndDetails;
                            break;
                        }
                    }
                    if (ownIdentityAndPermissionsAndDetails != null) {
                        invitationNonce = ownIdentityAndPermissionsAndDetails.groupInvitationNonce;
                    }
                } else {
                    blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier);
                    knownAdministratorsChain = protocolManagerSession.identityDelegate.getGroupV2AdministratorsChain(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier);
                    invitationNonce = protocolManagerSession.identityDelegate.getGroupV2OwnGroupInvitationNonce(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier);
                }

                if (invitationNonce != null && blobKeys != null && blobKeys.blobMainSeed != null) {
                    // decrypt the AdministratorsChain
                    AuthEncKey encryptionKey = (AuthEncKey) Suite.getKDF(KDF.KDF_SHA256).gen(blobKeys.blobMainSeed, Suite.getDefaultAuthEnc(0).getKDFDelegate())[0];
                    byte[] chainPlaintext = Suite.getAuthEnc(encryptionKey).decrypt(encryptionKey, receivedMessage.encryptedAdministratorsChain);
                    GroupV2.AdministratorsChain administratorsChain = GroupV2.AdministratorsChain.of(new Encoded(chainPlaintext));

                    // verify the chain
                    try {
                        administratorsChain.withCheckedIntegrity(receivedMessage.groupIdentifier.groupUid, null);
                        if (!administratorsChain.isPrefixedBy(knownAdministratorsChain)) {
                            return startState;
                        }
                    } catch (Exception ignored) {
                        return startState;
                    }

                    // verify that the signature in the received message matches an administrator of the chain
                    byte[] dataToSign = new byte[receivedMessage.encryptedAdministratorsChain.length + invitationNonce.length];
                    System.arraycopy(receivedMessage.encryptedAdministratorsChain.getBytes(), 0, dataToSign, 0, receivedMessage.encryptedAdministratorsChain.length);
                    System.arraycopy(invitationNonce, 0, dataToSign, receivedMessage.encryptedAdministratorsChain.length, invitationNonce.length);

                    boolean valid = false;
                    for (Identity identity : administratorsChain.getAdminIdentities()) {
                        if (Signature.verify(Constants.SignatureContext.GROUP_KICK, dataToSign, identity, receivedMessage.signature)) {
                            valid = true;
                            break;
                        }
                    }

                    if (valid) {
                        // remove the dialog/delete the group
                        if (startState instanceof InvitationReceivedState) {
                            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createDeleteDialog(), ((InvitationReceivedState) startState).dialogUuid));
                            ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        }

                        protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.groupIdentifier);

                        if (startState instanceof DownloadingGroupBlobState || startState instanceof INeedMoreSeedsState) {
                            return startState;
                        } else {
                            return new FinalState();
                        }
                    }
                }
            }

            return startState;
        }
    }

    public static class LeaveGroupStep extends ProtocolStep {
        private final ConcreteProtocolState startState;
        private final GroupV2.Identifier groupIdentifier;
        private final boolean propagated;
        private final byte[] ownGroupInvitationNonce;

        @SuppressWarnings("unused")
        public LeaveGroupStep(InitialProtocolState startState, GroupLeaveInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = false;
            this.ownGroupInvitationNonce = null;
        }

        @SuppressWarnings("unused")
        public LeaveGroupStep(DownloadingGroupBlobState startState, GroupLeaveInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = false;
            this.ownGroupInvitationNonce = null;
        }

        @SuppressWarnings("unused")
        public LeaveGroupStep(INeedMoreSeedsState startState, GroupLeaveInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = false;
            this.ownGroupInvitationNonce = null;
        }

        @SuppressWarnings("unused")
        public LeaveGroupStep(WaitingForLockState startState, GroupLeaveInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = false;
            this.ownGroupInvitationNonce = null;
        }

        @SuppressWarnings("unused")
        public LeaveGroupStep(InitialProtocolState startState, PropagatedGroupLeaveMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
            this.ownGroupInvitationNonce = receivedMessage.ownInvitationNonce;
        }

        @SuppressWarnings("unused")
        public LeaveGroupStep(DownloadingGroupBlobState startState, PropagatedGroupLeaveMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
            this.ownGroupInvitationNonce = receivedMessage.ownInvitationNonce;
        }

        @SuppressWarnings("unused")
        public LeaveGroupStep(INeedMoreSeedsState startState, PropagatedGroupLeaveMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
            this.ownGroupInvitationNonce = receivedMessage.ownInvitationNonce;
        }

        @SuppressWarnings("unused")
        public LeaveGroupStep(WaitingForLockState startState, PropagatedGroupLeaveMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
            this.ownGroupInvitationNonce = receivedMessage.ownInvitationNonce;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // first check that the protocolInstanceUid matches the groupIdentifier
            if (!getProtocolInstanceUid().equals(this.groupIdentifier.computeProtocolInstanceUid())) {
                return startState;
            }

            byte[] ownGroupInvitationNonce = protocolManagerSession.identityDelegate.getGroupV2OwnGroupInvitationNonce(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier);

            // if we are not part of the group, abort!
            if (ownGroupInvitationNonce == null) {
                return startState;
            }

            // propagated message for bad invitation nonce --> ignore the message
            if (propagated && !Arrays.equals(this.ownGroupInvitationNonce, ownGroupInvitationNonce)) {
                return startState;
            }

            {
                // check I am not the only admin
                boolean admin = protocolManagerSession.identityDelegate.getGroupV2AdminStatus(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier);
                if (admin && !protocolManagerSession.identityDelegate.getGroupV2HasOtherAdminMember(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier)) {
                    return startState;
                }
            }


            List<Identity> groupMembersToNotify = new ArrayList<>();
            if (!propagated) {
                // propagate the group leave message to other devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagatedGroupLeaveMessage(coreProtocolMessage, this.groupIdentifier, ownGroupInvitationNonce).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }

                // put a group left log on server
                byte[] leaveSignature = protocolManagerSession.identityDelegate.signGroupInvitationNonce(
                        protocolManagerSession.session,
                        Constants.SignatureContext.GROUP_LEAVE_NONCE,
                        groupIdentifier,
                        ownGroupInvitationNonce,
                        null,
                        getOwnedIdentity(),
                        getPrng());

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createPutGroupLogQuery(groupIdentifier, leaveSignature)));
                ChannelMessageToSend messageToSend = new PutGroupLogOnServerMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                // get the list of members to notify (before deleting the group)
                for (GroupV2.IdentityAndPermissions groupMember : protocolManagerSession.identityDelegate.getGroupV2OtherMembersAndPermissions(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier)) {
                    groupMembersToNotify.add(groupMember.identity);
                }
            }

            {
                // delete the group
                protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier);
            }

            if (propagated) {
                return new FinalState();
            } else {
                return new RejectingInvitationOrLeavingGroupState(this.groupIdentifier, groupMembersToNotify);
            }
        }
    }

    public static class DisbandGroupStep extends ProtocolStep {
        private final ConcreteProtocolState startState;
        private final GroupV2.Identifier groupIdentifier;
        private final boolean propagated;

        @SuppressWarnings("unused")
        public DisbandGroupStep(InitialProtocolState startState, GroupDisbandInitialMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = false;
        }


        @SuppressWarnings("unused")
        public DisbandGroupStep(InitialProtocolState startState, PropagatedGroupDisbandMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
        }

        @SuppressWarnings("unused")
        public DisbandGroupStep(DownloadingGroupBlobState startState, PropagatedGroupDisbandMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
        }

        @SuppressWarnings("unused")
        public DisbandGroupStep(INeedMoreSeedsState startState, PropagatedGroupDisbandMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
        }

        @SuppressWarnings("unused")
        public DisbandGroupStep(InvitationReceivedState startState, PropagatedGroupDisbandMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.groupIdentifier = receivedMessage.groupIdentifier;
            this.propagated = true;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // first check that the protocolInstanceUid matches the groupIdentifier
            if (!getProtocolInstanceUid().equals(this.groupIdentifier.computeProtocolInstanceUid())) {
                return startState;
            }

            // check I am an admin and I have the admin keys
            boolean admin = protocolManagerSession.identityDelegate.getGroupV2AdminStatus(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier);
            GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier);

            if (!admin || blobKeys == null || blobKeys.groupAdminServerAuthenticationPrivateKey == null) {
                return new FinalState();
            }

            if (!propagated) {
                // delete the group from the server
                byte[] signature = Signature.sign(Constants.SignatureContext.GROUP_DELETE_ON_SERVER, blobKeys.groupAdminServerAuthenticationPrivateKey.getSignaturePrivateKey(), getPrng());
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createDeleteGroupBlobQuery(this.groupIdentifier, signature)));
                ChannelMessageToSend messageToSend = new DeleteGroupBlobFromServerMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                // freeze the group
                protocolManagerSession.identityDelegate.freezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier);

                return new DisbandingGroupState(this.groupIdentifier, blobKeys);
            } else {
                // locally delete the group
                protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), this.groupIdentifier);

                return new FinalState();
            }
        }
    }

    public static class FinalizeGroupDisbandStep extends ProtocolStep {
        private final DisbandingGroupState startState;
        private final DeleteGroupBlobFromServerMessage receivedMessage;

        public FinalizeGroupDisbandStep(DisbandingGroupState startState, DeleteGroupBlobFromServerMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();


            if (!receivedMessage.success) {
                Logger.e("Failed to delete groupV2 blob on the server following a disband request");
                protocolManagerSession.identityDelegate.unfreezeGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);
                return new FinalState();
            }

            {
                // propagate the disband request
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagatedGroupDisbandMessage(coreProtocolMessage, startState.groupIdentifier).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            {
                // send kick messages to everyone else in the group
                byte[] chainPlaintext = protocolManagerSession.identityDelegate.getGroupV2AdministratorsChain(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier).encode().getBytes();
                AuthEncKey encryptionKey = (AuthEncKey) Suite.getKDF(KDF.KDF_SHA256).gen(startState.blobKeys.blobMainSeed, Suite.getDefaultAuthEnc(0).getKDFDelegate())[0];
                EncryptedBytes encryptedChain = Suite.getAuthEnc(encryptionKey).encrypt(encryptionKey, chainPlaintext, getPrng());

                // kick removed members
                GroupV2.ServerBlob serverBlob = protocolManagerSession.identityDelegate.getGroupV2ServerBlob(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

                for (GroupV2.IdentityAndPermissionsAndDetails member : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                    if (member.identity.equals(getOwnedIdentity())) {
                        continue;
                    }

                    byte[] dataToSign = new byte[encryptedChain.length + member.groupInvitationNonce.length];
                    System.arraycopy(encryptedChain.getBytes(), 0, dataToSign, 0, encryptedChain.length);
                    System.arraycopy(member.groupInvitationNonce, 0, dataToSign, encryptedChain.length, member.groupInvitationNonce.length);

                    byte[] signature = protocolManagerSession.identityDelegate.signBlock(protocolManagerSession.session, Constants.SignatureContext.GROUP_KICK, dataToSign, getOwnedIdentity(), getPrng());

                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(member.identity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new KickMessage(coreProtocolMessage, startState.groupIdentifier, encryptedChain, signature).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            // locally delete the group
            protocolManagerSession.identityDelegate.deleteGroupV2(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier);

            return new FinalState();
        }
    }


    public static class PrepareBatchKeysMessageStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final InitialProtocolState startState;
        private final InitiateBatchKeysResendMessage receivedMessage;

        public PrepareBatchKeysMessageStep(InitialProtocolState startState, InitiateBatchKeysResendMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // get all shared groups with the contact
            GroupV2.IdentifierVersionAndKeys[] identifierVersionAndKeys = protocolManagerSession.identityDelegate.getGroupsV2IdentifierVersionAndKeysForContact(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity);

            if (identifierVersionAndKeys.length > 0) {
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[] {receivedMessage.contactDeviceUid}, false));
                ChannelMessageToSend messageToSend = new BlobKeysBatchAfterChannelCreationMessage(coreProtocolMessage, identifierVersionAndKeys).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinalState();
        }
    }



    public static class ProcessBatchKeysMessageStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final InitialProtocolState startState;
        private final BlobKeysBatchAfterChannelCreationMessage receivedMessage;

        public ProcessBatchKeysMessageStep(InitialProtocolState startState, BlobKeysBatchAfterChannelCreationMessage receivedMessage, GroupsV2Protocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // post one local message with the correct protocol uid for each group
            for (GroupV2.IdentifierVersionAndKeys identifierVersionAndKeys : receivedMessage.groupInfos) {
                UID protocolInstanceUid = identifierVersionAndKeys.groupIdentifier.computeProtocolInstanceUid();

                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()), GROUPS_V2_PROTOCOL_ID, protocolInstanceUid, false);
                ChannelMessageToSend messageToSend = new BlobKeysAfterChannelCreationMessage(coreProtocolMessage, receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), identifierVersionAndKeys.groupIdentifier, identifierVersionAndKeys.groupVersion, identifierVersionAndKeys.blobKeys).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new FinalState();
        }
    }

    // endregion
}
