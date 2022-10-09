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


import static io.olvid.engine.protocol.protocols.TrustEstablishmentWithMutualScanProtocol.FINISHED_STATE_ID;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.Group;
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


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
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
    // endregion






    // region steps
    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{OwnedIdentityDeletionWithContactNotificationStep.class};
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

            // TODO multi-device: also propagate this information to other owned devices, if requested

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
            // send delete notifications to contacts
            {
                UID protocolInstanceUid = new UID(getPrng());

                Identity[] contactIdentities = protocolManagerSession.identityDelegate.getContactsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                if (contactIdentities.length > 0) {
                    SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(contactIdentities, getOwnedIdentity());

                    for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                        try {
                            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(sendChannelInfo,
                                    CONTACT_MANAGEMENT_PROTOCOL_ID,
                                    protocolInstanceUid,
                                    false);

                            ChannelMessageToSend messageToSend = new ContactManagementProtocol.ContactDeletionNotificationMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        } catch (Exception e) {
                            // if the contact has no channel, catch the exception and proceed with the deletion
                        }
                    }
                }
            }


            // finally, delete all channels (all notifications message have already been encrypted) and actually delete owned identity
            protocolManagerSession.channelDelegate.deleteAllChannelsForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            protocolManagerSession.identityDelegate.deleteOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

            return new FinishedProtocolState();
        }
    }
    // endregion
}
