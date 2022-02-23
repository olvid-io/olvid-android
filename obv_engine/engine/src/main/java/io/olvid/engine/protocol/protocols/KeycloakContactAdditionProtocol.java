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

import java.util.Objects;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.protocol.databases.LinkBetweenProtocolInstances;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.ChildToParentProtocolMessageInputs;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class KeycloakContactAdditionProtocol extends ConcreteProtocol {
    public KeycloakContactAdditionProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return KEYCLOAK_CONTACT_ADDITION_PROTOCOL_ID;
    }

    // region states

    public static final int WAITING_FOR_DEVICE_DISCOVERY_STATED_ID = 1;
    public static final int WAITING_FOR_CONFIRMATION_STATE_ID = 2;
    public static final int CHECKING_FOR_REVOCATION_STATE_ID = 3;
    public static final int FINISHED_STATE_ID = 4;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{FINISHED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case WAITING_FOR_DEVICE_DISCOVERY_STATED_ID:
                return WaitingForDeviceDiscoveryState.class;
            case WAITING_FOR_CONFIRMATION_STATE_ID:
                return WaitingForConfirmationState.class;
            case CHECKING_FOR_REVOCATION_STATE_ID:
                return CheckingForRevocationState.class;
            case FINISHED_STATE_ID:
                return FinishedProtocolState.class;
            default:
                return null;
        }
    }

    public static class WaitingForDeviceDiscoveryState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails; // this is a serialized JsonIdentityDetails
        private final String keycloakServerUrl;
        private final String signedOwnedDetails; // this is a JWT

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public WaitingForDeviceDiscoveryState(Encoded encodedState) throws Exception{
            super(WAITING_FOR_DEVICE_DISCOVERY_STATED_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            contactIdentity = list[0].decodeIdentity();
            contactSerializedDetails = list[1].decodeString();
            keycloakServerUrl = list[2].decodeString();
            signedOwnedDetails = list[3].decodeString();
        }

        public WaitingForDeviceDiscoveryState(Identity contactIdentity, String contactSerializedDetails, String keycloakServerUrl, String signedOwnedDetails) {
            super(WAITING_FOR_DEVICE_DISCOVERY_STATED_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.keycloakServerUrl = keycloakServerUrl;
            this.signedOwnedDetails = signedOwnedDetails;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(keycloakServerUrl),
                    Encoded.of(signedOwnedDetails),
            });
        }
    }

    public static class WaitingForConfirmationState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String keycloakServerUrl;

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public WaitingForConfirmationState(Encoded encodedState) throws Exception{
            super(WAITING_FOR_CONFIRMATION_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.keycloakServerUrl = list[1].decodeString();
        }

        public WaitingForConfirmationState(Identity contactIdentity, String keycloakServerUrl) {
            super(WAITING_FOR_CONFIRMATION_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.keycloakServerUrl = keycloakServerUrl;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(keycloakServerUrl),
            });
        }
    }

    public static class CheckingForRevocationState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String contactSerializedDetails; // serialized JsonIdentityDetails containing the signed JWT
        private final UID[] contactDeviceUids;
        private final String keycloakServerUrl;

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public CheckingForRevocationState(Encoded encodedState) throws Exception {
            super(CHECKING_FOR_REVOCATION_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 4) {
                throw new Exception();
            }
            contactIdentity = list[0].decodeIdentity();
            contactSerializedDetails = list[1].decodeString();
            contactDeviceUids = list[2].decodeUidArray();
            keycloakServerUrl = list[3].decodeString();
        }

        public CheckingForRevocationState(Identity contactIdentity, String contactSerializedDetails, UID[] contactDeviceUids, String keycloakServerUrl) {
            super(CHECKING_FOR_REVOCATION_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.contactSerializedDetails = contactSerializedDetails;
            this.contactDeviceUids = contactDeviceUids;
            this.keycloakServerUrl = keycloakServerUrl;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(keycloakServerUrl),
            });
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
    static final int DEVICE_DISCOVERY_DONE_MESSAGE_ID = 1;
    static final int PROPAGATE_CONTACT_ADDITION_TO_OTHER_DEVICES_MESSAGE_ID = 2;
    static final int INVITE_KEYCLOAK_CONTACT_MESSAGE_ID = 3;
    static final int CHECK_FOR_REVOCATION_SERVER_QUERY_MESSAGE_ID = 4;
    static final int CONFIRMATION_MESSAGE_ID = 5;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case DEVICE_DISCOVERY_DONE_MESSAGE_ID:
                return DeviceDiscoveryDoneMessage.class;
            case PROPAGATE_CONTACT_ADDITION_TO_OTHER_DEVICES_MESSAGE_ID:
                return PropagateContactAdditionToOtherDevicesMessage.class;
            case INVITE_KEYCLOAK_CONTACT_MESSAGE_ID:
                return InviteKeycloakContactMessage.class;
            case CHECK_FOR_REVOCATION_SERVER_QUERY_MESSAGE_ID:
                return CheckForRevocationServerQueryMessage.class;
            case CONFIRMATION_MESSAGE_ID:
                return ConfirmationMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String signedContactDetails;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String signedContactDetails) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.signedContactDetails = signedContactDetails;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.signedContactDetails = receivedMessage.getInputs()[1].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(signedContactDetails)
            };
        }
    }

    public static class DeviceDiscoveryDoneMessage extends ConcreteProtocolMessage {
        private final ChildToParentProtocolMessageInputs childToParentProtocolMessageInputs;

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public DeviceDiscoveryDoneMessage(CoreProtocolMessage coreProtocolMessage, ChildToParentProtocolMessageInputs childToParentProtocolMessageInputs) {
            super(coreProtocolMessage);
            this.childToParentProtocolMessageInputs = childToParentProtocolMessageInputs;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public DeviceDiscoveryDoneMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 3) {
                throw new Exception();
            }
            childToParentProtocolMessageInputs = new ChildToParentProtocolMessageInputs(receivedMessage.getInputs());
        }

        @Override
        public int getProtocolMessageId() {
            return DEVICE_DISCOVERY_DONE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(childToParentProtocolMessageInputs.toEncodedInputs())
            };
        }

        public DeviceDiscoveryChildProtocol.DeviceUidsReceivedState getDeviceUidsReceivedState() {
            try {
                return new DeviceDiscoveryChildProtocol.DeviceUidsReceivedState(childToParentProtocolMessageInputs.getChildProtocolEncodedState());
            } catch (Exception e) {
                return null;
            }
        }
    }


    public static class PropagateContactAdditionToOtherDevicesMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String keycloakServerUrl;
        private final String contactSerializedDetails;
        private final UID[] contactDeviceUids;
        private final long trustTimestamp;

        public PropagateContactAdditionToOtherDevicesMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String keycloakServerUrl, String contactSerializedDetails, UID[] contactDeviceUids, long trustTimestamp) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.keycloakServerUrl = keycloakServerUrl;
            this.contactSerializedDetails = contactSerializedDetails;
            this.contactDeviceUids = contactDeviceUids;
            this.trustTimestamp = trustTimestamp;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public PropagateContactAdditionToOtherDevicesMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 5) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.keycloakServerUrl = receivedMessage.getInputs()[1].decodeString();
            this.contactSerializedDetails = receivedMessage.getInputs()[2].decodeString();
            this.contactDeviceUids = receivedMessage.getInputs()[3].decodeUidArray();
            this.trustTimestamp = receivedMessage.getInputs()[4].decodeLong();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_CONTACT_ADDITION_TO_OTHER_DEVICES_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
                    Encoded.of(keycloakServerUrl),
                    Encoded.of(contactSerializedDetails),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(trustTimestamp),
            };
        }
    }


    public static class InviteKeycloakContactMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String signedContactDetails; // this is a JWT
        private final UID[] contactDeviceUids;
        private final String keycloakServerUrl;

        public InviteKeycloakContactMessage(CoreProtocolMessage coreProtocolMessage, Identity ownedIdentity, String signedOwnedDetails, UID[] ownedDeviceUids, String keycloakServerUrl) {
            super(coreProtocolMessage);
            this.contactIdentity = ownedIdentity;
            this.signedContactDetails = signedOwnedDetails;
            this.contactDeviceUids = ownedDeviceUids;
            this.keycloakServerUrl = keycloakServerUrl;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public InviteKeycloakContactMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 4) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.signedContactDetails = receivedMessage.getInputs()[1].decodeString();
            this.contactDeviceUids = receivedMessage.getInputs()[2].decodeUidArray();
            this.keycloakServerUrl = receivedMessage.getInputs()[3].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return INVITE_KEYCLOAK_CONTACT_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[] {
                    Encoded.of(contactIdentity),
                    Encoded.of(signedContactDetails),
                    Encoded.of(contactDeviceUids),
                    Encoded.of(keycloakServerUrl),
            };
        }
    }

    public static class CheckForRevocationServerQueryMessage extends ConcreteProtocolMessage {
        private final boolean userNotRevoked;

        public CheckForRevocationServerQueryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            userNotRevoked = false;
        }


        @SuppressWarnings({"unused", "RedundantSuppression"})
        public CheckForRevocationServerQueryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
            this.userNotRevoked = receivedMessage.getEncodedResponse().decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return CHECK_FOR_REVOCATION_SERVER_QUERY_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class ConfirmationMessage extends ConcreteProtocolMessage {
        private final boolean accepted;

        public ConfirmationMessage(CoreProtocolMessage coreProtocolMessage, boolean accepted) {
            super(coreProtocolMessage);
            this.accepted = accepted;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public ConfirmationMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.accepted = receivedMessage.getInputs()[0].decodeBoolean();
        }

        @Override
        public int getProtocolMessageId() {
            return CONFIRMATION_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(accepted),
            };
        }
    }


    // endregion



    // region steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{VerifyContactAndStartDeviceDiscoveryStep.class, ProcessPropagatedContactAdditionStep.class, ProcessReceivedKeycloakInviteStep.class};
            case WAITING_FOR_DEVICE_DISCOVERY_STATED_ID:
                return new Class[]{AddContactAndSendRequestStep.class};
            case WAITING_FOR_CONFIRMATION_STATE_ID:
                return new Class[]{ProcessConfirmationStep.class};
            case CHECKING_FOR_REVOCATION_STATE_ID:
                return new Class[]{AddContactAndSendConfirmationStep.class};
            case FINISHED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class VerifyContactAndStartDeviceDiscoveryStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public VerifyContactAndStartDeviceDiscoveryStep(InitialProtocolState startState, InitialMessage receivedMessage, KeycloakContactAdditionProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            /////////
            // first verify the contact signature
            /////////
            String keycloakServerUrl = protocolManagerSession.identityDelegate.getOwnedIdentityKeycloakServerUrl(protocolManagerSession.session, getOwnedIdentity());
            JsonIdentityDetailsWithVersionAndPhoto ownedIdentityDetailsWithVersionAndPhoto = protocolManagerSession.identityDelegate.getOwnedIdentityPublishedDetails(protocolManagerSession.session, getOwnedIdentity());
            if (keycloakServerUrl == null || ownedIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails() == null) {
                return new FinishedProtocolState();
            }

            JsonKeycloakUserDetails ownUserDetails = protocolManagerSession.identityDelegate.verifyKeycloakSignature(protocolManagerSession.session, getOwnedIdentity(), ownedIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails());
            JsonKeycloakUserDetails contactUserDetails = protocolManagerSession.identityDelegate.verifyKeycloakSignature(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.signedContactDetails);
            if (ownUserDetails == null || contactUserDetails == null) {
                return new FinishedProtocolState();
            }

            String contactSerializedDetails;
            try {
                JsonIdentityDetails contactDetails = contactUserDetails.getIdentityDetails(receivedMessage.signedContactDetails);
                contactSerializedDetails = protocol.getJsonObjectMapper().writeValueAsString(contactDetails);
            } catch (Exception e) {
                return new FinishedProtocolState();
            }


            /////////
            // signatures are valid --> launch a deviceDiscovery before adding the contact
            /////////

            UID childProtocolInstanceUid = new UID(getPrng());
            LinkBetweenProtocolInstances.create(
                    protocolManagerSession,
                    childProtocolInstanceUid,
                    getOwnedIdentity(),
                    DeviceDiscoveryChildProtocol.DEVICE_UIDS_RECEIVED_STATE_ID,
                    getProtocolInstanceUid(),
                    getProtocolId(),
                    DEVICE_DISCOVERY_DONE_MESSAGE_ID
            );
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                    DEVICE_DISCOVERY_CHILD_PROTOCOL_ID,
                    childProtocolInstanceUid,
                    false
            );
            ChannelMessageToSend messageToSend = new DeviceDiscoveryChildProtocol.InitialMessage(coreProtocolMessage, receivedMessage.contactIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new WaitingForDeviceDiscoveryState(receivedMessage.contactIdentity, contactSerializedDetails, keycloakServerUrl, ownedIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails());
        }
    }



    public static class AddContactAndSendRequestStep extends ProtocolStep {
        private final WaitingForDeviceDiscoveryState startState;
        private final DeviceDiscoveryDoneMessage receivedMessage;

        public AddContactAndSendRequestStep(WaitingForDeviceDiscoveryState startState, DeviceDiscoveryDoneMessage receivedMessage, KeycloakContactAdditionProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UID[] contactDeviceUids = receivedMessage.getDeviceUidsReceivedState().getDeviceUids();

            //////////
            // Abort protocol if deviceDiscovery failed...
            //////////
            if (contactDeviceUids == null || contactDeviceUids.length == 0) {
                return new FinishedProtocolState();
            }

            //////////
            // actually create the contact
            //////////
            final boolean contactCreated;
            final long trustTimestamp = System.currentTimeMillis();
            if (!protocolManagerSession.identityDelegate.isIdentityAContactIdentityOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                contactCreated = true;
                protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, startState.contactIdentity, startState.contactSerializedDetails, getOwnedIdentity(), TrustOrigin.createKeycloakTrustOrigin(trustTimestamp, startState.keycloakServerUrl));

                for (UID contactDeviceUid : contactDeviceUids) {
                    protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, contactDeviceUid);
                }
            } else {
                contactCreated = false;
                protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), TrustOrigin.createKeycloakTrustOrigin(trustTimestamp, startState.keycloakServerUrl));
                // no need to add devices, they should be in sync already
            }


            /////////
            // propagate the message to other known devices
            /////////
            {
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new PropagateContactAdditionToOtherDevicesMessage(coreProtocolMessage, startState.contactIdentity, startState.keycloakServerUrl, startState.contactSerializedDetails, contactDeviceUids, trustTimestamp).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }


            /////////
            // send an "invitation" to all contact devices
            /////////
            {
                UID[] ownedDeviceUids = protocolManagerSession.identityDelegate.getDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), contactDeviceUids));
                ChannelMessageToSend messageToSend = new InviteKeycloakContactMessage(coreProtocolMessage, getOwnedIdentity(), startState.signedOwnedDetails, ownedDeviceUids, startState.keycloakServerUrl).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            if (contactCreated) {
                return new WaitingForConfirmationState(startState.contactIdentity, startState.keycloakServerUrl);
            } else {
                return new FinishedProtocolState();
            }
        }
    }





    public static class ProcessPropagatedContactAdditionStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
        private final InitialProtocolState startState;
        private final PropagateContactAdditionToOtherDevicesMessage receivedMessage;

        public ProcessPropagatedContactAdditionStep(InitialProtocolState startState, PropagateContactAdditionToOtherDevicesMessage receivedMessage, KeycloakContactAdditionProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!protocolManagerSession.identityDelegate.isIdentityAContactIdentityOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity)) {
                protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, receivedMessage.contactIdentity, receivedMessage.contactSerializedDetails, getOwnedIdentity(), TrustOrigin.createKeycloakTrustOrigin(receivedMessage.trustTimestamp, receivedMessage.keycloakServerUrl));

                for (UID contactDeviceUid : receivedMessage.contactDeviceUids) {
                    protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.contactIdentity, contactDeviceUid);
                }
            } else {
                protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, receivedMessage.contactIdentity, getOwnedIdentity(), TrustOrigin.createKeycloakTrustOrigin(receivedMessage.trustTimestamp, receivedMessage.keycloakServerUrl));
                // no need to add devices, they should be in sync already
            }

            return new FinishedProtocolState();
        }
    }




    public static class ProcessReceivedKeycloakInviteStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
        private final InitialProtocolState startState;
        private final InviteKeycloakContactMessage receivedMessage;

        public ProcessReceivedKeycloakInviteStep(InitialProtocolState startState, InviteKeycloakContactMessage receivedMessage, KeycloakContactAdditionProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            ////////
            // verify the received contact signature
            ////////
            JsonKeycloakUserDetails contactUserDetails = protocolManagerSession.identityDelegate.verifyKeycloakSignature(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.signedContactDetails);

            if (contactUserDetails == null) {
                // respond "rejected"
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), receivedMessage.contactDeviceUids));
                ChannelMessageToSend messageToSend = new ConfirmationMessage(coreProtocolMessage, false).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new FinishedProtocolState();
            }


            String contactSerializedDetails;
            try {
                JsonIdentityDetails contactDetails = contactUserDetails.getIdentityDetails(receivedMessage.signedContactDetails);
                contactSerializedDetails = protocol.getJsonObjectMapper().writeValueAsString(contactDetails);
            } catch (Exception e) {
                // respond "rejected"
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), receivedMessage.contactDeviceUids));
                ChannelMessageToSend messageToSend = new ConfirmationMessage(coreProtocolMessage, false).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new FinishedProtocolState();
            }

            ////////
            // perform the server query to check for revoked identity
            ///////

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createCheckKeycloakRevocationServerQuery(receivedMessage.keycloakServerUrl, receivedMessage.signedContactDetails)));
            ChannelMessageToSend messageToSend = new CheckForRevocationServerQueryMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());


            return new CheckingForRevocationState(receivedMessage.contactIdentity, contactSerializedDetails, receivedMessage.contactDeviceUids, receivedMessage.keycloakServerUrl);
        }
    }

    public static class AddContactAndSendConfirmationStep extends ProtocolStep {
        private final CheckingForRevocationState startState;
        private final CheckForRevocationServerQueryMessage receivedMessage;

        public AddContactAndSendConfirmationStep(CheckingForRevocationState startState, CheckForRevocationServerQueryMessage receivedMessage, KeycloakContactAdditionProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (!receivedMessage.userNotRevoked) {
                // respond "rejected"
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), startState.contactDeviceUids));
                ChannelMessageToSend messageToSend = new ConfirmationMessage(coreProtocolMessage, false).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new FinishedProtocolState();
            }

            //////////
            // add the contact and devices
            //////////
            if (!protocolManagerSession.identityDelegate.isIdentityAContactIdentityOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity)) {
                protocolManagerSession.identityDelegate.addContactIdentity(protocolManagerSession.session, startState.contactIdentity, startState.contactSerializedDetails, getOwnedIdentity(), TrustOrigin.createKeycloakTrustOrigin(System.currentTimeMillis(), startState.keycloakServerUrl));

                for (UID contactDeviceUid : startState.contactDeviceUids) {
                    protocolManagerSession.identityDelegate.addDeviceForContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, contactDeviceUid);
                }
            } else {
                protocolManagerSession.identityDelegate.addTrustOriginToContact(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), TrustOrigin.createKeycloakTrustOrigin(System.currentTimeMillis(), startState.keycloakServerUrl));
                // no need to add devices, they should be in sync already
            }


            //////////
            // send confirmation message
            //////////
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAsymmetricChannelInfo(startState.contactIdentity, getOwnedIdentity(), startState.contactDeviceUids));
            ChannelMessageToSend messageToSend = new ConfirmationMessage(coreProtocolMessage, true).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new FinishedProtocolState();
        }
    }



    public static class ProcessConfirmationStep extends ProtocolStep {
        private final WaitingForConfirmationState startState;
        private final ConfirmationMessage receivedMessage;

        public ProcessConfirmationStep(WaitingForConfirmationState startState, ConfirmationMessage receivedMessage, KeycloakContactAdditionProtocol protocol) throws Exception {
            super(protocol.getOwnedIdentity(), ReceptionChannelInfo.createAsymmetricChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }


        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            //////////
            // if rejected --> delete the contact
            // if accepted --> everything is fine, do nothing
            //////////
            if (!receivedMessage.accepted) {
                // check all the contact trust origins --> if one is not the keycloak current addition, do nothing
                TrustOrigin[] trustOrigins = protocolManagerSession.identityDelegate.getTrustOriginsOfContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity);
                for (TrustOrigin trustOrigin : trustOrigins) {
                    if (trustOrigin.getType() != TrustOrigin.TYPE.KEYCLOAK || !Objects.equals(trustOrigin.getKeycloakServer(), startState.keycloakServerUrl)) {
                        return new FinishedProtocolState();
                    }
                }
                // the contact is only trusted through the keycloakServer which he just rejected --> delete the contact
                protocolManagerSession.identityDelegate.deleteContactIdentity(protocolManagerSession.session, getOwnedIdentity(), startState.contactIdentity, false);
            }

            return new FinishedProtocolState();
        }
    }



    // endregion






}
