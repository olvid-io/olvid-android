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

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.notifications.ProtocolNotifications;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

import static io.olvid.engine.protocol.protocols.KeycloakContactAdditionProtocol.FINISHED_STATE_ID;

import java.util.HashMap;

public class KeycloakBindingAndUnbindingProtocol extends ConcreteProtocol {
    public KeycloakBindingAndUnbindingProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }


    @Override
    public int getProtocolId() {
        return KEYCLOAK_BINDING_AND_UNBINDING_PROTOCOL_ID;
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
    static final int OWNED_IDENTITY_KEYCLOAK_BINDING_MESSAGE_ID = 0;
    static final int OWNED_IDENTITY_KEYCLOAK_UNBINDING_MESSAGE_ID = 1;
    static final int PROPAGATE_KEYCLOAK_BINDING_MESSAGE_ID = 2;
    static final int PROPAGATE_KEYCLOAK_UNBINDING_MESSAGE_ID = 3;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case OWNED_IDENTITY_KEYCLOAK_BINDING_MESSAGE_ID:
                return OwnedIdentityKeycloakBindingMessage.class;
            case OWNED_IDENTITY_KEYCLOAK_UNBINDING_MESSAGE_ID:
                return OwnedIdentityKeycloakUnbindingMessage.class;
            case PROPAGATE_KEYCLOAK_BINDING_MESSAGE_ID:
                return PropagateKeycloakBindingMessage.class;
            case PROPAGATE_KEYCLOAK_UNBINDING_MESSAGE_ID:
                return PropagateKeycloakUnbindingMessage.class;
            default:
                return null;
        }
    }

    public static class OwnedIdentityKeycloakBindingMessage extends ConcreteProtocolMessage {
        private final ObvKeycloakState keycloakState;
        private final String keycloakUserId;

        public OwnedIdentityKeycloakBindingMessage(CoreProtocolMessage coreProtocolMessage, ObvKeycloakState keycloakState, String keycloakUserId) {
            super(coreProtocolMessage);
            this.keycloakState = keycloakState;
            this.keycloakUserId = keycloakUserId;
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public OwnedIdentityKeycloakBindingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.keycloakState = ObvKeycloakState.of(receivedMessage.getInputs()[0]);
            this.keycloakUserId = receivedMessage.getInputs()[1].decodeString();
        }


        @Override
        public int getProtocolMessageId() {
            return OWNED_IDENTITY_KEYCLOAK_BINDING_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    keycloakState.encode(),
                    Encoded.of(keycloakUserId),
            };
        }
    }

    public static class OwnedIdentityKeycloakUnbindingMessage extends ConcreteProtocolMessage {

        public OwnedIdentityKeycloakUnbindingMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public OwnedIdentityKeycloakUnbindingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return OWNED_IDENTITY_KEYCLOAK_UNBINDING_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class PropagateKeycloakBindingMessage extends ConcreteProtocolMessage {
        public final String keycloakUserId;
        public final String keycloakServer;
        public final String clientId;
        public final String clientSecret; // may be null --> encoded as an empty String in this case
        public final String jwks;
        public final String signatureKey;

        public PropagateKeycloakBindingMessage(CoreProtocolMessage coreProtocolMessage, String keycloakUserId, ObvKeycloakState keycloakState) {
            super(coreProtocolMessage);
            this.keycloakUserId = keycloakUserId;
            this.keycloakServer = keycloakState.keycloakServer;
            this.clientId = keycloakState.clientId;
            this.clientSecret = keycloakState.clientSecret;
            this.jwks = keycloakState.jwks.toJson();
            this.signatureKey = keycloakState.signatureKey.toJson();
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public PropagateKeycloakBindingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 6) {
                throw new Exception();
            }
            this.keycloakUserId = receivedMessage.getInputs()[0].decodeString();
            this.keycloakServer = receivedMessage.getInputs()[1].decodeString();
            this.clientId = receivedMessage.getInputs()[2].decodeString();
            String clientSecret = receivedMessage.getInputs()[3].decodeString();
            this.clientSecret = clientSecret.length() == 0 ? null : clientSecret;
            this.jwks = receivedMessage.getInputs()[4].decodeString();
            this.signatureKey = receivedMessage.getInputs()[5].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_KEYCLOAK_BINDING_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(keycloakUserId),
                    Encoded.of(keycloakServer),
                    Encoded.of(clientId),
                    Encoded.of(clientSecret == null ? "" : clientSecret),
                    Encoded.of(jwks),
                    Encoded.of(signatureKey),
            };
        }
    }

    public static class PropagateKeycloakUnbindingMessage extends ConcreteProtocolMessage {
        public PropagateKeycloakUnbindingMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public PropagateKeycloakUnbindingMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return PROPAGATE_KEYCLOAK_UNBINDING_MESSAGE_ID;
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
                return new Class[]{OwnedIdentityKeycloakBindingStep.class, OwnedIdentityKeycloakUnbindingStep.class};
            case FINISHED_STATED_ID:
            default:
                return new Class[0];
        }
    }

    public static class OwnedIdentityKeycloakBindingStep extends ProtocolStep {
        InitialProtocolState startState;
        String keycloakUserId;
        ObvKeycloakState keycloakState;
        boolean propagationNeeded;


        public OwnedIdentityKeycloakBindingStep(InitialProtocolState startState, OwnedIdentityKeycloakBindingMessage receivedMessage, KeycloakBindingAndUnbindingProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.keycloakUserId = receivedMessage.keycloakUserId;
            this.keycloakState = receivedMessage.keycloakState;
            this.propagationNeeded = true;
        }

        public OwnedIdentityKeycloakBindingStep(InitialProtocolState startState, PropagateKeycloakBindingMessage receivedMessage, KeycloakBindingAndUnbindingProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.keycloakUserId = receivedMessage.keycloakUserId;
            this.keycloakState = new ObvKeycloakState(
                    receivedMessage.keycloakServer,
                    receivedMessage.clientId,
                    receivedMessage.clientSecret,
                    new JsonWebKeySet(receivedMessage.jwks),
                    JsonWebKey.Factory.newJwk(receivedMessage.signatureKey),
                    null,
                    null,
                    0,
                    0
            );
            this.propagationNeeded = false;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            /////////
            // re-check all inputs
            if (keycloakUserId == null
                    || keycloakState == null
                    || keycloakState.keycloakServer == null
                    || keycloakState.jwks == null) {
                Logger.w("Bad inputs for OwnedIdentityKeycloakBindingStep, aborting.");
                return new FinishedProtocolState();
            }

            /////////
            // switch owned identity to keycloak managed, but
            // do not update details
            //   --> this will be done once we upload our key and download new signed details from keycloak
            protocolManagerSession.identityDelegate.bindOwnedIdentityToKeycloak(
                    protocolManagerSession.session,
                    getOwnedIdentity(),
                    keycloakUserId,
                    keycloakState);


            /////////
            // re-check all contacts
            protocolManagerSession.identityDelegate.reCheckAllCertifiedByOwnKeycloakContacts(protocolManagerSession.session, getOwnedIdentity());

            //////////
            // propagate the binding to other owned devices (if any)
            if (propagationNeeded) {
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateKeycloakBindingMessage(coreProtocolMessage, keycloakUserId, keycloakState).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) {
                    }
                }
            } else {
                // notify the app that a keycloak registration & synchronization is required
                protocolManagerSession.session.addSessionCommitListener(() -> {
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(ProtocolNotifications.NOTIFICATION_KEYCLOAK_SYNCHRONIZATION_REQUIRED_OWNED_IDENTITY_KEY, getOwnedIdentity());
                    protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_KEYCLOAK_SYNCHRONIZATION_REQUIRED, userInfo);
                });
            }

            return new FinishedProtocolState();
        }
    }


    public static class OwnedIdentityKeycloakUnbindingStep extends ProtocolStep {
        InitialProtocolState startState;
        boolean propagationNeeded;

        public OwnedIdentityKeycloakUnbindingStep(InitialProtocolState startState, OwnedIdentityKeycloakUnbindingMessage receivedMessage, KeycloakBindingAndUnbindingProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.propagationNeeded = true;
        }

        public OwnedIdentityKeycloakUnbindingStep(InitialProtocolState startState, PropagateKeycloakUnbindingMessage receivedMessage, KeycloakBindingAndUnbindingProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.propagationNeeded = false;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            /////////
            // un-switch owned identity from keycloak managed, and update details
            int version;
            {
                version = protocolManagerSession.identityDelegate.unbindOwnedIdentityFromKeycloak(
                        protocolManagerSession.session,
                        getOwnedIdentity());

                if (version == -2) {
                    throw new Exception();
                }
            }

            /////////
            // start a child identityDetailsPublicationProtocol
            {
                UID childProtocolInstanceUid = new UID(getPrng());
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                        SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                        IDENTITY_DETAILS_PUBLICATION_PROTOCOL_ID,
                        childProtocolInstanceUid,
                        false
                );
                ChannelMessageToSend messageToSend = new IdentityDetailsPublicationProtocol.InitialMessage(coreProtocolMessage, version).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }


            /////////
            // unmark all certified contacts
            {
                protocolManagerSession.identityDelegate.unmarkAllCertifiedByOwnKeycloakContacts(protocolManagerSession.session, getOwnedIdentity());
            }

            //////////
            // propagate the unbinding to other owned devices (if any)
            if (propagationNeeded) {
                int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
                if (numberOfOtherDevices > 0) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                        ChannelMessageToSend messageToSend = new PropagateKeycloakUnbindingMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (NoAcceptableChannelException ignored) {
                    }
                }
            }

            return new FinishedProtocolState();
        }
    }

    // endregion
}
