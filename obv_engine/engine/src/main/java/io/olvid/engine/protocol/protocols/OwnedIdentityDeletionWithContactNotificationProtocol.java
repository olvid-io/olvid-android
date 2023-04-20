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


import static io.olvid.engine.protocol.protocols.TrustEstablishmentWithMutualScanProtocol.FINISHED_STATE_ID;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.KDF;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.GroupMembersChangedCallback;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.protocol.databases.IdentityDeletionSignatureReceived;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class OwnedIdentityDeletionWithContactNotificationProtocol extends ConcreteProtocol {
    public OwnedIdentityDeletionWithContactNotificationProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }


    @Override
    public int getProtocolId() {
        return OWNED_IDENTITY_DELETION_WITH_CONTACT_NOTIFICATION_PROTOCOL_ID;
    }

    // region states

    public static final int FINISHED_STATED_ID = 1;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{FINISHED_STATED_ID};
    }


    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case FINISHED_STATED_ID:
                return FinishedProtocolState.class;
            default:
                return null;
        }
    }


    public static class FinishedProtocolState extends ConcreteProtocolState {
        @SuppressWarnings({"unused", "RedundantSuppression"})
        public FinishedProtocolState(Encoded encodedState) throws Exception {
            super(FINISHED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public FinishedProtocolState() {
            super(FINISHED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }
    // endregion


    // region messages
    static final int INITIAL_MESSAGE_ID = 0;
    static final int CONTACT_OWNED_IDENTITY_WAS_DELETED_MESSAGE_ID = 1;


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case CONTACT_OWNED_IDENTITY_WAS_DELETED_MESSAGE_ID:
                return ContactOwnedIdentityWasDeletedMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        public InitialMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class ContactOwnedIdentityWasDeletedMessage extends ConcreteProtocolMessage {
        private final Identity deletedContactOwnedIdentity;
        private final byte[] signature;

        public ContactOwnedIdentityWasDeletedMessage(CoreProtocolMessage coreProtocolMessage, Identity deletedContactOwnedIdentity, byte[] signature) {
            super(coreProtocolMessage);
            this.deletedContactOwnedIdentity = deletedContactOwnedIdentity;
            this.signature = signature;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public ContactOwnedIdentityWasDeletedMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] inputs = receivedMessage.getInputs();
            if (inputs.length != 2) {
                throw new Exception();
            }
            this.deletedContactOwnedIdentity = inputs[0].decodeIdentity();
            this.signature = inputs[1].decodeBytes();
        }

        @Override
        public int getProtocolMessageId() {
            return CONTACT_OWNED_IDENTITY_WAS_DELETED_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(deletedContactOwnedIdentity),
                    Encoded.of(signature),
            };
        }
    }


    // endregion






    // region steps
    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{OwnedIdentityDeletionWithContactNotificationStep.class, ProcessContactOwnedIdentityWasDeletedMessageStep.class};
            case FINISHED_STATED_ID:
            default:
                return new Class[0];
        }
    }

    public static class OwnedIdentityDeletionWithContactNotificationStep extends ProtocolStep {
        InitialProtocolState startState;
        InitialMessage receivedMessage;

        public OwnedIdentityDeletionWithContactNotificationStep(InitialProtocolState startState, InitialMessage receivedMessage, OwnedIdentityDeletionWithContactNotificationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // TODO multi-device: also propagate this information to other owned devices

            ////////////
            // disband all owned groups & leave all joined groups
            {
                Group[] groups = protocolManagerSession.identityDelegate.getGroupsForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                for (Group group : groups) {
                    GroupInformation groupInformation = protocolManagerSession.identityDelegate.getGroupInformation(protocolManagerSession.session, getOwnedIdentity(), group.getGroupOwnerAndUid());
                    UID protocolInstanceUid = groupInformation.computeProtocolUid();

                    if (group.getGroupOwner() == null) {
                        ////////////
                        // owned group -> kick all members and pending members
                        if (group.getGroupMembers().length > 0) {
                            SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(group.getGroupMembers(), getOwnedIdentity());
                            for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                                try {
                                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(sendChannelInfo,
                                            GROUP_MANAGEMENT_PROTOCOL_ID,
                                            protocolInstanceUid,
                                            false);
                                    ChannelMessageToSend messageToSend = new GroupManagementProtocol.KickFromGroupMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                } catch (Exception ignored) {
                                    // continue even if there is an exception, contact notification is only best effort!
                                }
                            }
                        }
                        if (group.getPendingGroupMembers().length > 0) {
                            Identity[] pendingMemberIdentities = new Identity[group.getPendingGroupMembers().length];
                            for (int i = 0; i < pendingMemberIdentities.length; i++) {
                                pendingMemberIdentities[i] = group.getPendingGroupMembers()[i].identity;
                            }

                            SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(pendingMemberIdentities, getOwnedIdentity());
                            for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                                try {
                                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(sendChannelInfo,
                                            GROUP_MANAGEMENT_PROTOCOL_ID,
                                            protocolInstanceUid,
                                            false);
                                    ChannelMessageToSend messageToSend = new GroupManagementProtocol.KickFromGroupMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                } catch (Exception ignored) {
                                    // continue even if there is an exception, contact notification is only best effort!
                                }
                            }
                        }
                    } else {
                        ////////////
                        // joined group -> notify group owner
                        try {
                            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(group.getGroupOwner(), getOwnedIdentity()),
                                    GROUP_MANAGEMENT_PROTOCOL_ID,
                                    protocolInstanceUid,
                                    false);
                            ChannelMessageToSend message = new GroupManagementProtocol.NotifyGroupLeftMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
                        } catch (Exception ignored) {
                            // continue even if there is an exception, contact notification is only best effort!
                        }
                    }
                }
            }

            ////////////
            // leave all groups v2 & disband those where I am the only admin
            {
                List<ObvGroupV2> groupsV2 = protocolManagerSession.identityDelegate.getObvGroupsV2ForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                for (ObvGroupV2 groupV2 : groupsV2) {
                    if (groupV2.groupIdentifier.category == GroupV2.Identifier.CATEGORY_SERVER) {
                        // only consider non-keycloak groups
                        try {
                            // check if I am the only non-pending admin of this group
                            boolean iAmTheOnlyAdmin;
                            if (groupV2.ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN)) {
                                iAmTheOnlyAdmin = true;
                                for (ObvGroupV2.ObvGroupV2Member member : groupV2.otherGroupMembers) {
                                    if (member.permissions.contains(GroupV2.Permission.GROUP_ADMIN)) {
                                        iAmTheOnlyAdmin = false;
                                        break;
                                    }
                                }
                            } else {
                                iAmTheOnlyAdmin = false;
                            }

                            if (iAmTheOnlyAdmin) {
                                // delete the blob on the server
                                GroupV2.BlobKeys blobKeys = protocolManagerSession.identityDelegate.getGroupV2BlobKeys(protocolManagerSession.session, getOwnedIdentity(), groupV2.groupIdentifier);
                                {
                                    byte[] signature = Signature.sign(Constants.SignatureContext.GROUP_DELETE_ON_SERVER, blobKeys.groupAdminServerAuthenticationPrivateKey.getSignaturePrivateKey(), getPrng());
                                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createDeleteGroupBlobQuery(groupV2.groupIdentifier, signature)), ConcreteProtocol.GROUPS_V2_PROTOCOL_ID, new UID(getPrng()), false);
                                    ChannelMessageToSend messageToSend = new GroupsV2Protocol.DeleteGroupBlobFromServerMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                }

                                // immediately kick all members
                                byte[] chainPlaintext = protocolManagerSession.identityDelegate.getGroupV2AdministratorsChain(protocolManagerSession.session, getOwnedIdentity(), groupV2.groupIdentifier).encode().getBytes();
                                AuthEncKey encryptionKey = (AuthEncKey) Suite.getKDF(KDF.KDF_SHA256).gen(blobKeys.blobMainSeed, Suite.getDefaultAuthEnc(0).getKDFDelegate())[0];
                                EncryptedBytes encryptedChain = Suite.getAuthEnc(encryptionKey).encrypt(encryptionKey, chainPlaintext, getPrng());

                                GroupV2.ServerBlob serverBlob = protocolManagerSession.identityDelegate.getGroupV2ServerBlob(protocolManagerSession.session, getOwnedIdentity(), groupV2.groupIdentifier);

                                for (GroupV2.IdentityAndPermissionsAndDetails member : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
                                    if (member.identity.equals(getOwnedIdentity())) {
                                        continue;
                                    }

                                    byte[] dataToSign = new byte[encryptedChain.length + member.groupInvitationNonce.length];
                                    System.arraycopy(encryptedChain.getBytes(), 0, dataToSign, 0, encryptedChain.length);
                                    System.arraycopy(member.groupInvitationNonce, 0, dataToSign, encryptedChain.length, member.groupInvitationNonce.length);

                                    byte[] signature = protocolManagerSession.identityDelegate.signBlock(protocolManagerSession.session, Constants.SignatureContext.GROUP_KICK, dataToSign, getOwnedIdentity(), getPrng());

                                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(member.identity, getOwnedIdentity()), ConcreteProtocol.GROUPS_V2_PROTOCOL_ID, groupV2.groupIdentifier.computeProtocolInstanceUid(), false);
                                    ChannelMessageToSend messageToSend = new GroupsV2Protocol.KickMessage(coreProtocolMessage, groupV2.groupIdentifier, encryptedChain, signature).generateChannelProtocolMessageToSend();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                }
                            } else {
                                byte[] ownGroupInvitationNonce = protocolManagerSession.identityDelegate.getGroupV2OwnGroupInvitationNonce(protocolManagerSession.session, getOwnedIdentity(), groupV2.groupIdentifier);
                                if (ownGroupInvitationNonce != null) {
                                    // put a group left log on server
                                    // we do not notify the group members: they will refresh the groups when we send them the contact deletion message
                                    byte[] leaveSignature = protocolManagerSession.identityDelegate.signGroupInvitationNonce(
                                            protocolManagerSession.session,
                                            Constants.SignatureContext.GROUP_LEAVE_NONCE,
                                            groupV2.groupIdentifier,
                                            ownGroupInvitationNonce,
                                            null,
                                            getOwnedIdentity(),
                                            getPrng());

                                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createPutGroupLogQuery(groupV2.groupIdentifier, leaveSignature)), ConcreteProtocol.GROUPS_V2_PROTOCOL_ID, new UID(getPrng()), false);
                                    ChannelMessageToSend messageToSend = new GroupsV2Protocol.PutGroupLogOnServerMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                                }
                            }
                        } catch (Exception ignored) {
                            // continue even if there is an exception, contact notification is only best effort!
                        }
                    }
                }
            }

            ////////////
            // send delete notifications to contacts
            {
                UID protocolInstanceUid = new UID(getPrng());

                Identity[] contactIdentities = protocolManagerSession.identityDelegate.getContactsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                if (contactIdentities.length > 0) {
                    for (Identity contactIdentity : contactIdentities) {
                        try {
                            byte[] signature = protocolManagerSession.identityDelegate.signBlock(protocolManagerSession.session, Constants.SignatureContext.OWNED_IDENTITY_DELETION, contactIdentity.getBytes(), getOwnedIdentity(), getPrng());

                            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricBroadcastChannelInfo(contactIdentity, getOwnedIdentity()));
                            ChannelMessageToSend messageToSend = new ContactOwnedIdentityWasDeletedMessage(coreProtocolMessage, getOwnedIdentity(), signature).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        } catch (Exception ignored) {
                            // continue even if there is an exception, contact notification is only best effort!
                        }
                    }

                    // We no longer send the "legacy" delete contact message as it may mess up the treatment of our new ContactOwnedIdentityWasDeletedMessage
                }
            }


            // finally, delete all channels (all notifications message have already been encrypted) and actually delete owned identity
            protocolManagerSession.channelDelegate.deleteAllChannelsForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            protocolManagerSession.identityDelegate.deleteOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

            return new FinishedProtocolState();
        }
    }


    public static class ProcessContactOwnedIdentityWasDeletedMessageStep extends ProtocolStep {
        InitialProtocolState startState;
        ContactOwnedIdentityWasDeletedMessage receivedMessage;
        boolean propagated;

        public ProcessContactOwnedIdentityWasDeletedMessageStep(InitialProtocolState startState, ContactOwnedIdentityWasDeletedMessage receivedMessage, OwnedIdentityDeletionWithContactNotificationProtocol protocol) throws Exception {
            super((receivedMessage.getReceptionChannelInfo().getChannelType() == ReceptionChannelInfo.ASYMMETRIC_CHANNEL_TYPE) ? ReceptionChannelInfo.createAsymmetricChannelInfo() : ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
            propagated = receivedMessage.getReceptionChannelInfo().getChannelType() != ReceptionChannelInfo.ASYMMETRIC_CHANNEL_TYPE;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // check the message is not a replay
                if (IdentityDeletionSignatureReceived.exists(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature)) {
                    Logger.w("Received a ContactOwnedIdentityWasDeletedMessage with a known signature");
                    return new FinishedProtocolState();
                }
            }

            {
                // verify the signature
                if (!Signature.verify(Constants.SignatureContext.OWNED_IDENTITY_DELETION, getOwnedIdentity().getBytes(), receivedMessage.deletedContactOwnedIdentity, receivedMessage.signature)) {
                    Logger.w("Received a ContactOwnedIdentityWasDeletedMessage with an invalid signature");
                    return new FinishedProtocolState();
                }
            }

            // save the signature to prevent replay
            IdentityDeletionSignatureReceived.create(protocolManagerSession, getOwnedIdentity(), receivedMessage.signature);

            if (!propagated) {
                // propagate the message to other owned devices

                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new ContactOwnedIdentityWasDeletedMessage(coreProtocolMessage, receivedMessage.deletedContactOwnedIdentity, receivedMessage.signature).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }


            // now we can delete everything related to this contact

            {
                // delete all channels
                protocolManagerSession.channelDelegate.deleteObliviousChannelsWithContact(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.deletedContactOwnedIdentity);
            }

            {
                // deal with group v1
                List<byte[]> groupOwnerAndUids = new ArrayList<>(Arrays.asList(protocolManagerSession.identityDelegate.getGroupOwnerAndUidOfGroupsWhereContactIsPending(protocolManagerSession.session, receivedMessage.deletedContactOwnedIdentity, getOwnedIdentity())));
                groupOwnerAndUids.addAll(Arrays.asList(protocolManagerSession.identityDelegate.getGroupOwnerAndUidsOfGroupsContainingContact(protocolManagerSession.session, receivedMessage.deletedContactOwnedIdentity, getOwnedIdentity())));

                for (byte[] groupOwnerAndUid : groupOwnerAndUids) {
                    Group group = protocolManagerSession.identityDelegate.getGroup(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid);
                    if (!propagated && group.getGroupOwner() == null) {
                        // I own the group --> properly remove the member from the group and trigger the step to notify others
                        GroupInformation groupInformation = protocolManagerSession.identityDelegate.getGroupInformation(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid);

                        GroupMembersChangedCallback groupMembersChangedCallback = () -> {
                            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()), ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID, groupInformation.computeProtocolUid(), false);
                            ChannelMessageToSend messageToSend = new GroupManagementProtocol.GroupMembersOrDetailsChangedTriggerMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        };

                        protocolManagerSession.identityDelegate.removeMembersAndPendingFromGroup(protocolManagerSession.session, groupOwnerAndUid, getOwnedIdentity(), new Identity[]{receivedMessage.deletedContactOwnedIdentity}, groupMembersChangedCallback);
                    } else {
                        // I joined the group (or it is propagated)
                        if (receivedMessage.deletedContactOwnedIdentity.equals(group.getGroupOwner())) {
                            // the removed contact was the group owner --> leave the group
                            protocolManagerSession.identityDelegate.leaveGroup(protocolManagerSession.session, groupOwnerAndUid, getOwnedIdentity());
                        } else {
                            // remove the member/pending member before receiving the notification from the group owner
                            protocolManagerSession.identityDelegate.forcefullyRemoveMemberOrPendingFromJoinedGroup(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid, receivedMessage.deletedContactOwnedIdentity);
                        }
                    }
                }
            }


            {
                // deal with group v2
                for (GroupV2.IdentifierAndAdminStatus identifierAndAdminStatus : protocolManagerSession.identityDelegate.getServerGroupsV2IdentifierAndMyAdminStatusForContact(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.deletedContactOwnedIdentity)) {
                    if (!propagated && identifierAndAdminStatus.iAmAdmin) {
                        // I am a group admin --> start the standard group update protocol
                        ObvGroupV2.ObvGroupV2ChangeSet changeSet = new ObvGroupV2.ObvGroupV2ChangeSet();
                        changeSet.removedMembers.add(receivedMessage.deletedContactOwnedIdentity.getBytes());

                        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                                ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                                identifierAndAdminStatus.groupIdentifier.computeProtocolInstanceUid(),
                                false);

                        ChannelMessageToSend messageToSend = new GroupsV2Protocol.GroupUpdateInitialMessage(coreProtocolMessage, identifierAndAdminStatus.groupIdentifier, changeSet).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    }

                    // always remove contact from the group immediately: if admin, this does not prevent the update to work, if not, we will get an update/disband message soon
                    protocolManagerSession.identityDelegate.forcefullyRemoveMemberOrPendingFromNonAdminGroupV2(protocolManagerSession.session, getOwnedIdentity(), identifierAndAdminStatus.groupIdentifier, receivedMessage.deletedContactOwnedIdentity);
                }
            }

            // delete contact, do not fail if there are still some groups (typically, groups v2 where I am admin)
            protocolManagerSession.identityDelegate.deleteContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.deletedContactOwnedIdentity, false);

            return new FinishedProtocolState();
        }
    }
    // endregion
}
