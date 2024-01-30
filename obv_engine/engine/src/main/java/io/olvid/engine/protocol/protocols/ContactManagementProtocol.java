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

import java.util.HashSet;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
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
import io.olvid.engine.protocol.protocol_engine.EmptyProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;


public class ContactManagementProtocol extends ConcreteProtocol {

    public ContactManagementProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
        eraseReceivedMessagesAfterReachingAFinalState = false;
    }

    @Override
    public int getProtocolId() { return CONTACT_MANAGEMENT_PROTOCOL_ID; }


    // region States

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







    // region Messages

    private static final int INITIATE_CONTACT_DELETION_MESSAGE_ID = 0;
    private static final int CONTACT_DELETION_NOTIFICATION_MESSAGE_ID = 1;
    private static final int PROPAGATE_CONTACT_DELETION_MESSAGE_ID = 2;

    private static final int INITIATE_CONTACT_DOWNGRADE_MESSAGE_ID = 3;
    private static final int CONTACT_DOWNGRADE_NOTIFICATION_MESSAGE_ID = 4;
    private static final int PROPAGATE_CONTACT_DOWNGRADE_MESSAGE_ID = 5;
    private static final int PERFORM_CONTACT_DEVICE_DISCOVERY_MESSAGE_ID = 6;


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIATE_CONTACT_DELETION_MESSAGE_ID:
                return InitiateContactDeletionMessage.class;
            case CONTACT_DELETION_NOTIFICATION_MESSAGE_ID:
                return ContactDeletionNotificationMessage.class;
            case PROPAGATE_CONTACT_DELETION_MESSAGE_ID:
                return PropagateContactDeletionMessage.class;
            case INITIATE_CONTACT_DOWNGRADE_MESSAGE_ID:
                return InitiateContactDowngradeMessage.class;
            case CONTACT_DOWNGRADE_NOTIFICATION_MESSAGE_ID:
                return ContactDowngradeNotificationMessage.class;
            case PROPAGATE_CONTACT_DOWNGRADE_MESSAGE_ID:
                return PropagateContactDowngradeMessage.class;
            case PERFORM_CONTACT_DEVICE_DISCOVERY_MESSAGE_ID:
                return PerformContactDeviceDiscoveryMessage.class;
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

        @SuppressWarnings("unused")
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


    public static class ContactDeletionNotificationMessage extends EmptyProtocolMessage {
        public ContactDeletionNotificationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public ContactDeletionNotificationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return CONTACT_DELETION_NOTIFICATION_MESSAGE_ID;
        }
    }


    public static class PropagateContactDeletionMessage extends ConcreteProtocolMessage {
        Identity contactIdentity;

        public PropagateContactDeletionMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        @SuppressWarnings("unused")
        public PropagateContactDeletionMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_CONTACT_DELETION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
            };
        }
    }

    public static class InitiateContactDowngradeMessage extends ConcreteProtocolMessage {
        Identity contactIdentity;

        public InitiateContactDowngradeMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        @SuppressWarnings("unused")
        public InitiateContactDowngradeMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_CONTACT_DOWNGRADE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
            };
        }
    }

    public static class ContactDowngradeNotificationMessage extends EmptyProtocolMessage {
        public ContactDowngradeNotificationMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public ContactDowngradeNotificationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return CONTACT_DOWNGRADE_NOTIFICATION_MESSAGE_ID;
        }
    }

    public static class PropagateContactDowngradeMessage extends ConcreteProtocolMessage {
        Identity contactIdentity;

        public PropagateContactDowngradeMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
        }

        @SuppressWarnings("unused")
        public PropagateContactDowngradeMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_CONTACT_DOWNGRADE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
            };
        }
    }


    public static class PerformContactDeviceDiscoveryMessage extends EmptyProtocolMessage {
        protected PerformContactDeviceDiscoveryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public PerformContactDeviceDiscoveryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return PERFORM_CONTACT_DEVICE_DISCOVERY_MESSAGE_ID;
        }
    }

    // endregion







    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        if (stateId == INITIAL_STATE_ID) {
            return new Class[]{
                    DeleteContactStep.class,
                    ProcessContactDeletionNotificationStep.class,
                    ProcessPropagatedContactDeletionStep.class,
                    DowngradeContactStep.class,
                    ProcessContactDowngradeNotificationStep.class,
                    ProcessPropagatedContactDowngradeStep.class,
                    ProcessPerformContactDeviceDiscoveryMessageStep.class,
            };
        }
        return new Class[0];
    }


    public static class DeleteContactStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitiateContactDeletionMessage receivedMessage;

        public DeleteContactStep(InitialProtocolState startState, InitiateContactDeletionMessage receivedMessage, ContactManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
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
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateContactDeletionMessage(coreProtocolMessage, receivedMessage.contactIdentity).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
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
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final PropagateContactDeletionMessage receivedMessage;

        public ProcessPropagatedContactDeletionStep(InitialProtocolState startState, PropagateContactDeletionMessage receivedMessage, ContactManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
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
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final ContactDeletionNotificationMessage receivedMessage;

        public ProcessContactDeletionNotificationStep(InitialProtocolState startState, ContactDeletionNotificationMessage receivedMessage, ContactManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
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


    public static class DowngradeContactStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitiateContactDowngradeMessage receivedMessage;

        public DowngradeContactStep(InitialProtocolState startState, InitiateContactDowngradeMessage receivedMessage, ContactManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // mark contact as not oneToOne
                protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity, false);
            }

            {
                try {
                    // notify the contact he has been downgraded
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllConfirmedObliviousChannelsInfo(receivedMessage.contactIdentity, getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new ContactDowngradeNotificationMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                } catch (Exception ignored) { }
            }

            {
                // propagate downgrade to other owned devices
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateContactDowngradeMessage(coreProtocolMessage, receivedMessage.contactIdentity).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) { }
                }
            }

            return new FinalState();
        }
    }


    public static class ProcessContactDowngradeNotificationStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final ContactDowngradeNotificationMessage receivedMessage;

        public ProcessContactDowngradeNotificationStep(InitialProtocolState startState, ContactDowngradeNotificationMessage receivedMessage, ContactManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // mark contact as not oneToOne
                protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), false);
            }

            return new FinalState();
        }
    }


    public static class ProcessPropagatedContactDowngradeStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final PropagateContactDowngradeMessage receivedMessage;

        public ProcessPropagatedContactDowngradeStep(InitialProtocolState startState, PropagateContactDowngradeMessage receivedMessage, ContactManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            {
                // mark contact as not oneToOne
                protocolManagerSession.identityDelegate.setContactOneToOne(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity, false);
            }

            return new FinalState();
        }
    }

    public static class ProcessPerformContactDeviceDiscoveryMessageStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final PerformContactDeviceDiscoveryMessage receivedMessage;

        public ProcessPerformContactDeviceDiscoveryMessageStep(InitialProtocolState startState, PerformContactDeviceDiscoveryMessage receivedMessage, ContactManagementProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                    ConcreteProtocol.DEVICE_DISCOVERY_PROTOCOL_ID,
                    new UID(getPrng()),
                    false);
            ChannelMessageToSend message = new DeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage, receivedMessage.getReceptionChannelInfo().getRemoteIdentity()).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());

            return new FinalState();
        }
    }

    // endregion
}






