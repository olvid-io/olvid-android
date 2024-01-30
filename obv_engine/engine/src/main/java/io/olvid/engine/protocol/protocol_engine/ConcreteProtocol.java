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

package io.olvid.engine.protocol.protocol_engine;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.ProtocolInstance;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocols.ChannelCreationWithContactDeviceProtocol;
import io.olvid.engine.protocol.protocols.ChannelCreationWithOwnedDeviceProtocol;
import io.olvid.engine.protocol.protocols.DeviceCapabilitiesDiscoveryProtocol;
import io.olvid.engine.protocol.protocols.ContactMutualIntroductionProtocol;
import io.olvid.engine.protocol.protocols.DeviceDiscoveryChildProtocol;
import io.olvid.engine.protocol.protocols.DeviceDiscoveryProtocol;
import io.olvid.engine.protocol.protocols.DownloadGroupPhotoChildProtocol;
import io.olvid.engine.protocol.protocols.DownloadGroupV2PhotoProtocol;
import io.olvid.engine.protocol.protocols.DownloadIdentityPhotoChildProtocol;
import io.olvid.engine.protocol.protocols.FullRatchetProtocol;
import io.olvid.engine.protocol.protocols.GroupInvitationProtocol;
import io.olvid.engine.protocol.protocols.GroupManagementProtocol;
import io.olvid.engine.protocol.protocols.GroupsV2Protocol;
import io.olvid.engine.protocol.protocols.IdentityDetailsPublicationProtocol;
import io.olvid.engine.protocol.protocols.KeycloakBindingAndUnbindingProtocol;
import io.olvid.engine.protocol.protocols.KeycloakContactAdditionProtocol;
import io.olvid.engine.protocol.protocols.ContactManagementProtocol;
import io.olvid.engine.protocol.protocols.OneToOneContactInvitationProtocol;
import io.olvid.engine.protocol.protocols.OwnedDeviceDiscoveryProtocol;
import io.olvid.engine.protocol.protocols.OwnedDeviceManagementProtocol;
import io.olvid.engine.protocol.protocols.OwnedIdentityDeletionProtocol;
import io.olvid.engine.protocol.protocols.OwnedIdentityTransferProtocol;
import io.olvid.engine.protocol.protocols.SynchronizationProtocol;
import io.olvid.engine.protocol.protocols.TrustEstablishmentWithMutualScanProtocol;
import io.olvid.engine.protocol.protocols.TrustEstablishmentWithSasProtocol;

public abstract class ConcreteProtocol {
    public static final int INITIAL_STATE_ID = 0;


    public static final int DEVICE_DISCOVERY_PROTOCOL_ID = 0;
    public static final int TRUST_ESTABLISHMENT_PROTOCOL_ID = 1; // no longer used (superseded by 11)
    public static final int CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID = 2;
    public static final int DEVICE_DISCOVERY_CHILD_PROTOCOL_ID = 3;
    public static final int CONTACT_MUTUAL_INTRODUCTION_PROTOCOL_ID = 4;
    public static final int GROUP_CREATION_PROTOCOL_ID = 5; // no longer used
    public static final int IDENTITY_DETAILS_PUBLICATION_PROTOCOL_ID = 6;
    public static final int DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID = 7;
    public static final int GROUP_INVITATION_PROTOCOL_ID = 8;
    public static final int GROUP_MANAGEMENT_PROTOCOL_ID = 9;
    public static final int CONTACT_MANAGEMENT_PROTOCOL_ID = 10;
    public static final int TRUST_ESTABLISHMENT_WITH_SAS_PROTOCOL_ID = 11;
    public static final int TRUST_ESTABLISHMENT_WITH_MUTUAL_SCAN_PROTOCOL_ID = 12;
    public static final int FULL_RATCHET_PROTOCOL_ID = 13;
    public static final int DOWNLOAD_GROUP_PHOTO_CHILD_PROTOCOL_ID = 14;
    public static final int KEYCLOAK_CONTACT_ADDITION_PROTOCOL_ID = 15;
    public static final int DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID = 16;
    public static final int ONE_TO_ONE_CONTACT_INVITATION_PROTOCOL_ID = 17;
    public static final int GROUPS_V2_PROTOCOL_ID = 18;
    public static final int DOWNLOAD_GROUPS_V2_PHOTO_PROTOCOL_ID = 19;
    public static final int OWNED_IDENTITY_DELETION_PROTOCOL_ID = 20;
    public static final int OWNED_DEVICE_DISCOVERY_PROTOCOL_ID = 21;
    public static final int CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID = 22;
    public static final int KEYCLOAK_BINDING_AND_UNBINDING_PROTOCOL_ID = 23;
    public static final int OWNED_DEVICE_MANAGEMENT_PROTOCOL_ID = 24;
    public static final int SYNCHRONIZATION_PROTOCOL_ID = 25;
    public static final int OWNED_IDENTITY_TRANSFER_PROTOCOL_ID = 26;

    // internal protocols, Android only
    public static final int LEGACY_KEYCLOAK_BINDING_AND_UNBINDING_PROTOCOL_ID = 1000;


    protected final ProtocolManagerSession protocolManagerSession;
    protected final UID protocolInstanceUid;
    protected final Identity ownedIdentity;
    protected final PRNGService prng;
    protected final ObjectMapper jsonObjectMapper;
    protected ConcreteProtocolState currentState;

    public boolean eraseReceivedMessagesAfterReachingAFinalState = true;

    public UID getProtocolInstanceUid() {
        return protocolInstanceUid;
    }

    public ConcreteProtocolState getCurrentState() {
        return currentState;
    }

    public void updateCurrentState(ConcreteProtocolState newState) {
        currentState = newState;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public ProtocolManagerSession getProtocolManagerSession() {
        return protocolManagerSession;
    }

    public PRNGService getPrng() {
        return prng;
    }

    public ObjectMapper getJsonObjectMapper() {
        return jsonObjectMapper;
    }

    public ConcreteProtocol(ProtocolManagerSession protocolManagerSession,
                            UID protocolInstanceUid,
                            int currentStateId,
                            Encoded encodedCurrentState,
                            Identity ownedIdentity,
                            PRNGService prng,
                            ObjectMapper jsonObjectMapper) throws Exception {
        this.protocolManagerSession = protocolManagerSession;
        this.protocolInstanceUid = protocolInstanceUid;
        this.ownedIdentity = ownedIdentity;
        this.prng = prng;
        this.jsonObjectMapper = jsonObjectMapper;
        this.currentState = getProtocolState(getStateClass(currentStateId), encodedCurrentState);
    }

    public static ConcreteProtocol getConcreteProtocol(ProtocolInstance protocolInstance, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        if (protocolInstance == null) {
            return null;
        }
        ProtocolManagerSession protocolManagerSession = protocolInstance.getProtocolManagerSession();
        int protocolId = protocolInstance.getProtocolId();
        UID protocolInstanceUid = protocolInstance.getUid();
        int currentStateId = protocolInstance.getCurrentStateId();
        Encoded encodedCurrentState = protocolInstance.getEncodedCurrentState();
        Identity ownedIdentity = protocolInstance.getOwnedIdentity();
        return getConcreteProtocol(protocolManagerSession, protocolId, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    public static ConcreteProtocol getConcreteProtocolInInitialState(ProtocolManagerSession protocolManagerSession, int protocolId, UID protocolInstanceUid, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        return getConcreteProtocol(protocolManagerSession, protocolId, protocolInstanceUid, INITIAL_STATE_ID, Encoded.of(new Encoded[0]), ownedIdentity, prng, jsonObjectMapper);
    }

    private static ConcreteProtocol getConcreteProtocol(ProtocolManagerSession protocolManagerSession,
                                                        int protocolId,
                                                        UID protocolInstanceUid,
                                                        int stateId,
                                                        Encoded encodedState,
                                                        Identity ownedIdentity,
                                                        PRNGService prng,
                                                        ObjectMapper jsonObjectMapper) throws Exception {
        switch (protocolId) {
            case DEVICE_DISCOVERY_PROTOCOL_ID:
                return new DeviceDiscoveryProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID:
                return new ChannelCreationWithContactDeviceProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case DEVICE_DISCOVERY_CHILD_PROTOCOL_ID:
                return new DeviceDiscoveryChildProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case CONTACT_MUTUAL_INTRODUCTION_PROTOCOL_ID:
                return new ContactMutualIntroductionProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case IDENTITY_DETAILS_PUBLICATION_PROTOCOL_ID:
                return new IdentityDetailsPublicationProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID:
                return new DownloadIdentityPhotoChildProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case GROUP_INVITATION_PROTOCOL_ID:
                return new GroupInvitationProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case GROUP_MANAGEMENT_PROTOCOL_ID:
                return new GroupManagementProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case CONTACT_MANAGEMENT_PROTOCOL_ID:
                return new ContactManagementProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case TRUST_ESTABLISHMENT_WITH_SAS_PROTOCOL_ID:
                return new TrustEstablishmentWithSasProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case TRUST_ESTABLISHMENT_WITH_MUTUAL_SCAN_PROTOCOL_ID:
                return new TrustEstablishmentWithMutualScanProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case FULL_RATCHET_PROTOCOL_ID:
                return new FullRatchetProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case DOWNLOAD_GROUP_PHOTO_CHILD_PROTOCOL_ID:
                return new DownloadGroupPhotoChildProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case KEYCLOAK_CONTACT_ADDITION_PROTOCOL_ID:
                return new KeycloakContactAdditionProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID:
                return new DeviceCapabilitiesDiscoveryProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case LEGACY_KEYCLOAK_BINDING_AND_UNBINDING_PROTOCOL_ID:
            case KEYCLOAK_BINDING_AND_UNBINDING_PROTOCOL_ID:
                return new KeycloakBindingAndUnbindingProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case ONE_TO_ONE_CONTACT_INVITATION_PROTOCOL_ID:
                return new OneToOneContactInvitationProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case GROUPS_V2_PROTOCOL_ID:
                return new GroupsV2Protocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case DOWNLOAD_GROUPS_V2_PHOTO_PROTOCOL_ID:
                return new DownloadGroupV2PhotoProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case OWNED_IDENTITY_DELETION_PROTOCOL_ID:
                return new OwnedIdentityDeletionProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case OWNED_DEVICE_DISCOVERY_PROTOCOL_ID:
                return new OwnedDeviceDiscoveryProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID:
                return new ChannelCreationWithOwnedDeviceProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case OWNED_DEVICE_MANAGEMENT_PROTOCOL_ID:
                return new OwnedDeviceManagementProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case SYNCHRONIZATION_PROTOCOL_ID:
                return new SynchronizationProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            case OWNED_IDENTITY_TRANSFER_PROTOCOL_ID:
                return new OwnedIdentityTransferProtocol(protocolManagerSession, protocolInstanceUid, stateId, encodedState, ownedIdentity, prng, jsonObjectMapper);
            default:
                Logger.w("Unknown protocol id: " + protocolId);
                return null;
        }

    }

    public abstract int getProtocolId();

    protected abstract Class<?> getStateClass(int stateId);
    protected final ConcreteProtocolState getProtocolState(Class<?> currentState, Encoded encodedCurrentState) throws Exception {
        Constructor<?> constructor = currentState.getConstructor(Encoded.class);
        return (ConcreteProtocolState) constructor.newInstance(encodedCurrentState);
    }

    public abstract int[] getFinalStateIds();
    public final boolean hasReachedFinalState() {
        for (int finalStateId: getFinalStateIds()) {
            if (currentState.id == finalStateId) {
                return true;
            }
        }
        return false;
    }

    protected abstract Class<?> getMessageClass(int protocolMessageId);
    public final ConcreteProtocolMessage getConcreteProtocolMessage(ReceivedMessage receivedMessage) {
        try {
            Class<?> messageClass = getMessageClass(receivedMessage.getProtocolMessageId());
            if (messageClass == null) {
                return null;
            }
            Constructor<?> constructor = messageClass.getConstructor(ReceivedMessage.class);
            return (ConcreteProtocolMessage) constructor.newInstance(receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    protected abstract Class<?>[] getPossibleStepClasses(int stateId);
    public final ProtocolStep getStepToExecute(ConcreteProtocolMessage concreteProtocolMessage) {
        try {
            int matches = 0;
            Constructor<?> constructor = null;
            Class<?>[] classes = getPossibleStepClasses(currentState.id);
            for (Class<?> clazz: classes) {
                try {
                    constructor = clazz.getConstructor(currentState.getClass(), concreteProtocolMessage.getClass(), this.getClass());
                    matches++;
                } catch (NoSuchMethodException ignored) {}
            }
            if (matches != 1) {
                Logger.d("Found " + matches + " protocolStep to execute in " + this.getClass() + " for state " + currentState.getClass() + " and message " + concreteProtocolMessage.getClass());
                return null;
            }
            return (ProtocolStep) constructor.newInstance(currentState, concreteProtocolMessage, this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
