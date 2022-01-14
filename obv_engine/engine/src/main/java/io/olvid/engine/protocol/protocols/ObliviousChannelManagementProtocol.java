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

import java.util.HashSet;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;


public class ObliviousChannelManagementProtocol extends ConcreteProtocol {

    public ObliviousChannelManagementProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
        eraseReceivedMessagesAfterReachingAFinalState = false;
    }

    @Override
    public int getProtocolId() { return OBLIVIOUS_CHANNEL_MANAGEMENT_PROTOCOL_ID; }


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

    private static final int INITIATE_CONTACT_DELETION_MESSAGE_ID = 0;
    private static final int NOTIFY_CONTACT_DELETED_MESSAGE_ID = 1;
    private static final int PROPAGATE_CONTACT_DELETED_MESSAGE_ID = 2;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIATE_CONTACT_DELETION_MESSAGE_ID:
                return InitiateContactDeletionMessage.class;
            case NOTIFY_CONTACT_DELETED_MESSAGE_ID:
                return ContactDeletionNotificationMessage.class;
            case PROPAGATE_CONTACT_DELETED_MESSAGE_ID:
                return PropagateContactDeletionMessage.class;
            default:
                return null;
        }
    }

    public static class InitiateContactDeletionMessage extends ConcreteProtocolMessage {
        Identity contactIdentity;

        public InitiateContactDeletionMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        public InitiateContactDeletionMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_CONTACT_DELETION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
            };
        }
    }


    public static class ContactDeletionNotificationMessage extends ConcreteProtocolMessage {
        public ContactDeletionNotificationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        public ContactDeletionNotificationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return NOTIFY_CONTACT_DELETED_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class PropagateContactDeletionMessage extends ConcreteProtocolMessage {
        Identity contactIdentity;

        public PropagateContactDeletionMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        public PropagateContactDeletionMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_CONTACT_DELETED_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
            };
        }
    }

    // endregion



    // region steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{
                        DeleteContactStep.class,
                        ProcessContactDeletionNotificationStep.class,
                        ProcessPropagatedContactDeletionStep.class,
                };
            default:
                return new Class[0];
        }
    }


    public static class DeleteContactStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final InitiateContactDeletionMessage receivedMessage;

        public DeleteContactStep(InitialProtocolState startState, InitiateContactDeletionMessage receivedMessage, ObliviousChannelManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // propagate to other devices
            {
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateContactDeletionMessage(coreProtocolMessage, receivedMessage.contactIdentity).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            // notify contact (we need the oblivious channel --> before deleting the contact)
            try {
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                ChannelMessageToSend messageToSend = new ContactDeletionNotificationMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            } catch (Exception e) {
                // if the contact has no channel, throw an exception but proceed with the deletion
            }

            // delete all channels
            protocolManagerSession.channelDelegate.deleteObliviousChannelsWithContact(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity);


            // remove contact from all owned groups where it is pending
            byte[][] groupOwnerAndUids = protocolManagerSession.identityDelegate.getGroupOwnerAndUidOfGroupsWhereContactIsPending(protocolManagerSession.session, receivedMessage.contactIdentity, getOwnedIdentity());

            HashSet<Identity> removedMemberIdentities = new HashSet<>(1);
            removedMemberIdentities.add(receivedMessage.contactIdentity);

            for (byte[] groupOwnerAndUid: groupOwnerAndUids) {
                GroupInformation groupInformation = protocolManagerSession.identityDelegate.getGroupInformation(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid);

                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                        groupInformation.computeProtocolUid(),
                        false);
                ChannelMessageToSend messageToSend = new GroupManagementProtocol.RemoveGroupMembersMessage(coreProtocolMessage, groupInformation, removedMemberIdentities).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }


            // delete contact (if there are no groups)
            protocolManagerSession.identityDelegate.deleteContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity, true);


            return new FinalState();
        }
    }

    public static class ProcessPropagatedContactDeletionStep extends ProtocolStep {
        private final InitialProtocolState startState;

        private final PropagateContactDeletionMessage receivedMessage;
        public ProcessPropagatedContactDeletionStep(InitialProtocolState startState, PropagateContactDeletionMessage receivedMessage, ObliviousChannelManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // delete all channels
            protocolManagerSession.channelDelegate.deleteObliviousChannelsWithContact(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity);

            // we do not do anything about own group pending members: the GroupManagementProtocol will propagate the information itself

            // delete the contact (even if still in some groups, this is only temporary)
            protocolManagerSession.identityDelegate.deleteContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity, false);

            return new FinalState();
        }

    }

    public static class ProcessContactDeletionNotificationStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final ContactDeletionNotificationMessage receivedMessage;

        public ProcessContactDeletionNotificationStep(InitialProtocolState startState, ContactDeletionNotificationMessage receivedMessage, ObliviousChannelManagementProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();
            Identity contactIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();

            // delete all channels
            protocolManagerSession.channelDelegate.deleteObliviousChannelsWithContact(protocolManagerSession.session, getOwnedIdentity(), contactIdentity);

            // delete contact, fails if there are still some groups, but catch Exception to still delete channels (destroyed on sender side).
            try {
                {
                    // first, leave all groups where contact is the owner (as this should never be a fail cause)
                    byte[][] groupOwnerAndUids = protocolManagerSession.identityDelegate.getGroupOwnerAndUidsOfGroupsOwnedByContact(protocolManagerSession.session, getOwnedIdentity(), contactIdentity);
                    for (byte[] groupOwnerAndUid : groupOwnerAndUids) {
                        protocolManagerSession.identityDelegate.leaveGroup(protocolManagerSession.session, groupOwnerAndUid, getOwnedIdentity());
                    }
                }

                // delete contact, fails if there are still some groups
                protocolManagerSession.identityDelegate.deleteContactIdentity(protocolManagerSession.session, getOwnedIdentity(), contactIdentity, true);

                // if the contact was indeed deleted (no exception thrown) remove contact from all owned groups where it is pending
                byte[][] groupOwnerAndUids = protocolManagerSession.identityDelegate.getGroupOwnerAndUidOfGroupsWhereContactIsPending(protocolManagerSession.session, contactIdentity, getOwnedIdentity());

                HashSet<Identity> removedMemberIdentities = new HashSet<>(1);
                removedMemberIdentities.add(contactIdentity);

                for (byte[] groupOwnerAndUid: groupOwnerAndUids) {
                    GroupInformation groupInformation = protocolManagerSession.identityDelegate.getGroupInformation(protocolManagerSession.session, getOwnedIdentity(), groupOwnerAndUid);

                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                            ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                            groupInformation.computeProtocolUid(),
                            false);
                    ChannelMessageToSend messageToSend = new GroupManagementProtocol.RemoveGroupMembersMessage(coreProtocolMessage, groupInformation, removedMemberIdentities).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            } catch (Exception ignored) { }
            return new FinalState();
        }
    }

    // endregion
}






