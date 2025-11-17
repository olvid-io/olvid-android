/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Commitment;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.MAC;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.SAS;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.PrivateIdentity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.datatypes.notifications.ProtocolNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvDeviceManagementRequest;
import io.olvid.engine.engine.types.ObvTransferStep;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshot;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.sync.IdentityManagerSyncSnapshot;
import io.olvid.engine.metamanager.IdentityDelegate;
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

public class OwnedIdentityTransferProtocol extends ConcreteProtocol {
    public OwnedIdentityTransferProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return OWNED_IDENTITY_TRANSFER_PROTOCOL_ID;
    }

    // region States

    public static final int SOURCE_WAITING_FOR_SESSION_NUMBER_STATE_ID = 1;
    public static final int SOURCE_WAITING_FOR_TARGET_CONNECTION_STATE_ID = 2;
    public static final int TARGET_WAITING_FOR_SESSION_NUMBER_STATE_ID = 3;
    public static final int TARGET_WAITING_FOR_TRANSFERRED_IDENTITY_STATE_ID = 4;
    public static final int SOURCE_WAITING_FOR_TARGET_SEED_STATE_ID = 5;
    public static final int TARGET_WAITING_FOR_DECOMMITMENT_STATE_ID = 6;
    public static final int SOURCE_WAITING_FOR_SAS_INPUT_STATE_ID = 7;
    public static final int TARGET_WAITING_FOR_SNAPSHOT_STATE_ID = 8;
    public static final int SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID = 9;
    public static final int TARGET_WAITING_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID = 10;
    public static final int FINAL_STATE_ID = 99;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{FINAL_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case SOURCE_WAITING_FOR_SESSION_NUMBER_STATE_ID:
                return SourceWaitingForSessionNumberState.class;
            case SOURCE_WAITING_FOR_TARGET_CONNECTION_STATE_ID:
                return SourceWaitingForTargetConnectionState.class;
            case TARGET_WAITING_FOR_SESSION_NUMBER_STATE_ID:
                return TargetWaitingForSessionNumberState.class;
            case TARGET_WAITING_FOR_TRANSFERRED_IDENTITY_STATE_ID:
                return TargetWaitingForTransferredIdentityState.class;
            case SOURCE_WAITING_FOR_TARGET_SEED_STATE_ID:
                return SourceWaitingForTargetSeedState.class;
            case TARGET_WAITING_FOR_DECOMMITMENT_STATE_ID:
                return TargetWaitingForDecommitmentState.class;
            case SOURCE_WAITING_FOR_SAS_INPUT_STATE_ID:
                return SourceWaitingForSasInputState.class;
            case TARGET_WAITING_FOR_SNAPSHOT_STATE_ID:
                return TargetWaitingForSnapshotState.class;
            case SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID:
                return SourceWaitForKeycloakAuthenticationProofState.class;
            case TARGET_WAITING_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID:
                return TargetWaitingForKeycloakAuthenticationProofState.class;
            case FINAL_STATE_ID:
                return FinalState.class;
            default:
                return null;
        }
    }

    public static class SourceWaitingForSessionNumberState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        protected SourceWaitingForSessionNumberState(UUID dialogUuid) {
            super(SOURCE_WAITING_FOR_SESSION_NUMBER_STATE_ID);
            this.dialogUuid = dialogUuid;
        }

        @SuppressWarnings("unused")
        public SourceWaitingForSessionNumberState(Encoded encodedState) throws Exception {
            super(SOURCE_WAITING_FOR_SESSION_NUMBER_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
            });
        }
    }


    public static class SourceWaitingForTargetConnectionState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String ownConnectionIdentifier;
        private final long sessionNumber;

        protected SourceWaitingForTargetConnectionState(UUID dialogUuid, String ownConnectionIdentifier, long sessionNumber) {
            super(SOURCE_WAITING_FOR_TARGET_CONNECTION_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.ownConnectionIdentifier = ownConnectionIdentifier;
            this.sessionNumber = sessionNumber;
        }

        @SuppressWarnings("unused")
        public SourceWaitingForTargetConnectionState(Encoded encodedState) throws Exception {
            super(SOURCE_WAITING_FOR_TARGET_CONNECTION_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 3) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.ownConnectionIdentifier = list[1].decodeString();
            this.sessionNumber = list[2].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
                    Encoded.of(ownConnectionIdentifier),
                    Encoded.of(sessionNumber),
            });
        }
    }

    public static class TargetWaitingForSessionNumberState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String deviceName;
        private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
        private final EncryptionPrivateKey encryptionPrivateKey;
        private final MACKey macKey;

        protected TargetWaitingForSessionNumberState(UUID dialogUuid, String deviceName, ServerAuthenticationPrivateKey serverAuthenticationPrivateKey, EncryptionPrivateKey encryptionPrivateKey, MACKey macKey) {
            super(TARGET_WAITING_FOR_SESSION_NUMBER_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.deviceName = deviceName;
            this.serverAuthenticationPrivateKey = serverAuthenticationPrivateKey;
            this.encryptionPrivateKey = encryptionPrivateKey;
            this.macKey = macKey;
        }

        @SuppressWarnings("unused")
        public TargetWaitingForSessionNumberState(Encoded encodedState) throws Exception {
            super(TARGET_WAITING_FOR_SESSION_NUMBER_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 5) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.deviceName = list[1].decodeString();
            this.serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) list[2].decodePrivateKey();
            this.encryptionPrivateKey = (EncryptionPrivateKey) list[3].decodePrivateKey();
            this.macKey = (MACKey) list[4].decodeSymmetricKey();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
                    Encoded.of(deviceName),
                    Encoded.of(serverAuthenticationPrivateKey),
                    Encoded.of(encryptionPrivateKey),
                    Encoded.of(macKey),
            });
        }
    }



    public static class TargetWaitingForTransferredIdentityState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String deviceName;
        private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
        private final EncryptionPrivateKey encryptionPrivateKey;
        private final MACKey macKey;
        private final long sessionNumber;

        protected TargetWaitingForTransferredIdentityState(UUID dialogUuid, String deviceName, ServerAuthenticationPrivateKey serverAuthenticationPrivateKey, EncryptionPrivateKey encryptionPrivateKey, MACKey macKey, long sessionNumber) {
            super(TARGET_WAITING_FOR_TRANSFERRED_IDENTITY_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.deviceName = deviceName;
            this.serverAuthenticationPrivateKey = serverAuthenticationPrivateKey;
            this.encryptionPrivateKey = encryptionPrivateKey;
            this.macKey = macKey;
            this.sessionNumber = sessionNumber;
        }

        @SuppressWarnings("unused")
        public TargetWaitingForTransferredIdentityState(Encoded encodedState) throws Exception {
            super(TARGET_WAITING_FOR_TRANSFERRED_IDENTITY_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 6) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.deviceName = list[1].decodeString();
            this.serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) list[2].decodePrivateKey();
            this.encryptionPrivateKey = (EncryptionPrivateKey) list[3].decodePrivateKey();
            this.macKey = (MACKey) list[4].decodeSymmetricKey();
            this.sessionNumber = list[5].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
                    Encoded.of(deviceName),
                    Encoded.of(serverAuthenticationPrivateKey),
                    Encoded.of(encryptionPrivateKey),
                    Encoded.of(macKey),
                    Encoded.of(sessionNumber),
            });
        }
    }


    public static class SourceWaitingForTargetSeedState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String otherConnectionIdentifier;
        private final Identity ephemeralIdentity;
        private final Seed seedSourceForSas;
        private final byte[] decommitment;
        private final long sessionNumber;

        public SourceWaitingForTargetSeedState(UUID dialogUuid, String otherConnectionIdentifier, Identity ephemeralIdentity, Seed seedSourceForSas, byte[] decommitment, long sessionNumber) {
            super(SOURCE_WAITING_FOR_TARGET_SEED_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.otherConnectionIdentifier = otherConnectionIdentifier;
            this.ephemeralIdentity = ephemeralIdentity;
            this.seedSourceForSas = seedSourceForSas;
            this.decommitment = decommitment;
            this.sessionNumber = sessionNumber;
        }

        @SuppressWarnings("unused")
        public SourceWaitingForTargetSeedState(Encoded encodedState) throws Exception {
            super(SOURCE_WAITING_FOR_TARGET_SEED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 6) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.otherConnectionIdentifier = list[1].decodeString();
            this.ephemeralIdentity = list[2].decodeIdentity();
            this.seedSourceForSas = list[3].decodeSeed();
            this.decommitment = list[4].decodeBytes();
            this.sessionNumber = list[5].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
                    Encoded.of(otherConnectionIdentifier),
                    Encoded.of(ephemeralIdentity),
                    Encoded.of(seedSourceForSas),
                    Encoded.of(decommitment),
                    Encoded.of(sessionNumber),
            });
        }
    }


    public static class TargetWaitingForDecommitmentState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String deviceName;
        private final String otherConnectionIdentifier;
        private final Identity transferredIdentity;
        private final byte[] commitment;
        private final Seed seedTargetForSas;
        private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
        private final EncryptionPrivateKey encryptionPrivateKey;
        private final MACKey macKey;
        private final long sessionNumber;

        public TargetWaitingForDecommitmentState(UUID dialogUuid, String deviceName, String otherConnectionIdentifier, Identity transferredIdentity, byte[] commitment, Seed seedTargetForSas, ServerAuthenticationPrivateKey serverAuthenticationPrivateKey, EncryptionPrivateKey encryptionPrivateKey, MACKey macKey, long sessionNumber) {
            super(TARGET_WAITING_FOR_DECOMMITMENT_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.deviceName = deviceName;
            this.otherConnectionIdentifier = otherConnectionIdentifier;
            this.transferredIdentity = transferredIdentity;
            this.commitment = commitment;
            this.seedTargetForSas = seedTargetForSas;
            this.serverAuthenticationPrivateKey = serverAuthenticationPrivateKey;
            this.encryptionPrivateKey = encryptionPrivateKey;
            this.macKey = macKey;
            this.sessionNumber = sessionNumber;
        }

        @SuppressWarnings("unused")
        public TargetWaitingForDecommitmentState(Encoded encodedState) throws Exception {
            super(TARGET_WAITING_FOR_DECOMMITMENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 10) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.deviceName = list[1].decodeString();
            this.otherConnectionIdentifier = list[2].decodeString();
            this.transferredIdentity = list[3].decodeIdentity();
            this.commitment = list[4].decodeBytes();
            this.seedTargetForSas = list[5].decodeSeed();
            this.serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) list[6].decodePrivateKey();
            this.encryptionPrivateKey = (EncryptionPrivateKey) list[7].decodePrivateKey();
            this.macKey = (MACKey) list[8].decodeSymmetricKey();
            this.sessionNumber = list[9].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
                    Encoded.of(deviceName),
                    Encoded.of(otherConnectionIdentifier),
                    Encoded.of(transferredIdentity),
                    Encoded.of(commitment),
                    Encoded.of(seedTargetForSas),
                    Encoded.of(serverAuthenticationPrivateKey),
                    Encoded.of(encryptionPrivateKey),
                    Encoded.of(macKey),
                    Encoded.of(sessionNumber),
            });
        }
    }


    public static class SourceWaitingForSasInputState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String otherConnectionIdentifier;
        private final String targetDeviceName;
        private final Identity ephemeralIdentity;
        private final String fullSas;
        private final long sessionNumber;

        public SourceWaitingForSasInputState(UUID dialogUuid, String otherConnectionIdentifier, String targetDeviceName, Identity ephemeralIdentity, String fullSas, long sessionNumber) {
            super(SOURCE_WAITING_FOR_SAS_INPUT_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.otherConnectionIdentifier = otherConnectionIdentifier;
            this.targetDeviceName = targetDeviceName;
            this.ephemeralIdentity = ephemeralIdentity;
            this.fullSas = fullSas;
            this.sessionNumber = sessionNumber;
        }

        @SuppressWarnings("unused")
        public SourceWaitingForSasInputState(Encoded encodedState) throws Exception {
            super(SOURCE_WAITING_FOR_SAS_INPUT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 6) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.otherConnectionIdentifier = list[1].decodeString();
            this.targetDeviceName = list[2].decodeString();
            this.ephemeralIdentity = list[3].decodeIdentity();
            this.fullSas = list[4].decodeString();
            this.sessionNumber = list[5].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
                    Encoded.of(otherConnectionIdentifier),
                    Encoded.of(targetDeviceName),
                    Encoded.of(ephemeralIdentity),
                    Encoded.of(fullSas),
                    Encoded.of(sessionNumber),
            });
        }
    }

    public static class SourceWaitForKeycloakAuthenticationProofState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String otherConnectionIdentifier;
        private final Identity ephemeralIdentity;
        private final String fullSas;
        private final long sessionNumber;
        private final UID deviceUidToKeepActive; // may be null

        public SourceWaitForKeycloakAuthenticationProofState(UUID dialogUuid, String otherConnectionIdentifier, Identity ephemeralIdentity, String fullSas, long sessionNumber, UID deviceUidToKeepActive) {
            super(SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.otherConnectionIdentifier = otherConnectionIdentifier;
            this.ephemeralIdentity = ephemeralIdentity;
            this.fullSas = fullSas;
            this.sessionNumber = sessionNumber;
            this.deviceUidToKeepActive = deviceUidToKeepActive;
        }

        @SuppressWarnings("unused")
        public SourceWaitForKeycloakAuthenticationProofState(Encoded encodedState) throws Exception {
            super(SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 6 && list.length != 5) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.otherConnectionIdentifier = list[1].decodeString();
            this.ephemeralIdentity = list[2].decodeIdentity();
            this.fullSas = list[3].decodeString();
            this.sessionNumber = list[4].decodeLong();
            if (list.length == 6) {
                this.deviceUidToKeepActive = list[5].decodeUid();
            } else {
                this.deviceUidToKeepActive = null;
            }
        }

        @Override
        public Encoded encode() {
            if (deviceUidToKeepActive != null) {
                return Encoded.of(new Encoded[]{
                        Encoded.of(dialogUuid),
                        Encoded.of(otherConnectionIdentifier),
                        Encoded.of(ephemeralIdentity),
                        Encoded.of(fullSas),
                        Encoded.of(sessionNumber),
                        Encoded.of(deviceUidToKeepActive),
                });
            } else {
                return Encoded.of(new Encoded[]{
                        Encoded.of(dialogUuid),
                        Encoded.of(otherConnectionIdentifier),
                        Encoded.of(ephemeralIdentity),
                        Encoded.of(fullSas),
                        Encoded.of(sessionNumber),
                });
            }
        }
    }

    public static class TargetWaitingForKeycloakAuthenticationProofState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String deviceName;
        private final String otherConnectionIdentifier;
        private final Identity transferredIdentity;
        private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
        private final EncryptionPrivateKey encryptionPrivateKey;
        private final MACKey macKey;
        private final String fullSas;
        private final long sessionNumber;

        public TargetWaitingForKeycloakAuthenticationProofState(UUID dialogUuid, String deviceName, String otherConnectionIdentifier, Identity transferredIdentity, ServerAuthenticationPrivateKey serverAuthenticationPrivateKey, EncryptionPrivateKey encryptionPrivateKey, MACKey macKey, String fullSas, long sessionNumber) {
            super(TARGET_WAITING_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.deviceName = deviceName;
            this.otherConnectionIdentifier = otherConnectionIdentifier;
            this.transferredIdentity = transferredIdentity;
            this.serverAuthenticationPrivateKey = serverAuthenticationPrivateKey;
            this.encryptionPrivateKey = encryptionPrivateKey;
            this.macKey = macKey;
            this.fullSas = fullSas;
            this.sessionNumber = sessionNumber;
        }

        @SuppressWarnings("unused")
        public TargetWaitingForKeycloakAuthenticationProofState(Encoded encodedState) throws Exception {
            super(TARGET_WAITING_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 9) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.deviceName = list[1].decodeString();
            this.otherConnectionIdentifier = list[2].decodeString();
            this.transferredIdentity = list[3].decodeIdentity();
            this.serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) list[4].decodePrivateKey();
            this.encryptionPrivateKey = (EncryptionPrivateKey) list[5].decodePrivateKey();
            this.macKey = (MACKey) list[6].decodeSymmetricKey();
            this.fullSas = list[7].decodeString();
            this.sessionNumber = list[8].decodeLong();
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(dialogUuid),
                    Encoded.of(deviceName),
                    Encoded.of(otherConnectionIdentifier),
                    Encoded.of(transferredIdentity),
                    Encoded.of(serverAuthenticationPrivateKey),
                    Encoded.of(encryptionPrivateKey),
                    Encoded.of(macKey),
                    Encoded.of(fullSas),
                    Encoded.of(sessionNumber),
            });
        }
    }


    public static class TargetWaitingForSnapshotState extends ConcreteProtocolState {
        private final UUID dialogUuid;
        private final String deviceName;
        private final String otherConnectionIdentifier;
        private final Identity transferredIdentity;
        private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
        private final EncryptionPrivateKey encryptionPrivateKey;
        private final MACKey macKey;
        private final String fullSas;
        private final long sessionNumber;
        private final String serializedKeycloakAuthState; // non-null only after getting an transfer proof from keycloak

        public TargetWaitingForSnapshotState(UUID dialogUuid, String deviceName, String otherConnectionIdentifier, Identity transferredIdentity, ServerAuthenticationPrivateKey serverAuthenticationPrivateKey, EncryptionPrivateKey encryptionPrivateKey, MACKey macKey, String fullSas, long sessionNumber, String serializedKeycloakAuthState) {
            super(TARGET_WAITING_FOR_SNAPSHOT_STATE_ID);
            this.dialogUuid = dialogUuid;
            this.deviceName = deviceName;
            this.otherConnectionIdentifier = otherConnectionIdentifier;
            this.transferredIdentity = transferredIdentity;
            this.serverAuthenticationPrivateKey = serverAuthenticationPrivateKey;
            this.encryptionPrivateKey = encryptionPrivateKey;
            this.macKey = macKey;
            this.fullSas = fullSas;
            this.sessionNumber = sessionNumber;
            this.serializedKeycloakAuthState = serializedKeycloakAuthState;
        }

        @SuppressWarnings("unused")
        public TargetWaitingForSnapshotState(Encoded encodedState) throws Exception {
            super(TARGET_WAITING_FOR_SNAPSHOT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 10 && list.length != 9) {
                throw new Exception();
            }
            this.dialogUuid = list[0].decodeUuid();
            this.deviceName = list[1].decodeString();
            this.otherConnectionIdentifier = list[2].decodeString();
            this.transferredIdentity = list[3].decodeIdentity();
            this.serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) list[4].decodePrivateKey();
            this.encryptionPrivateKey = (EncryptionPrivateKey) list[5].decodePrivateKey();
            this.macKey = (MACKey) list[6].decodeSymmetricKey();
            this.fullSas = list[7].decodeString();
            this.sessionNumber = list[8].decodeLong();
            if (list.length == 10) {
                this.serializedKeycloakAuthState = list[9].decodeString();
            } else {
                this.serializedKeycloakAuthState = null;
            }
        }

        @Override
        public Encoded encode() {
            if (serializedKeycloakAuthState == null) {
                return Encoded.of(new Encoded[]{
                        Encoded.of(dialogUuid),
                        Encoded.of(deviceName),
                        Encoded.of(otherConnectionIdentifier),
                        Encoded.of(transferredIdentity),
                        Encoded.of(serverAuthenticationPrivateKey),
                        Encoded.of(encryptionPrivateKey),
                        Encoded.of(macKey),
                        Encoded.of(fullSas),
                        Encoded.of(sessionNumber),
                });
            } else {
                return Encoded.of(new Encoded[]{
                        Encoded.of(dialogUuid),
                        Encoded.of(deviceName),
                        Encoded.of(otherConnectionIdentifier),
                        Encoded.of(transferredIdentity),
                        Encoded.of(serverAuthenticationPrivateKey),
                        Encoded.of(encryptionPrivateKey),
                        Encoded.of(macKey),
                        Encoded.of(fullSas),
                        Encoded.of(sessionNumber),
                        Encoded.of(serializedKeycloakAuthState),
                });
            }
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

    public static final int INITIATE_TRANSFER_ON_SOURCE_DEVICE_MESSAGE_ID = 0;
    public static final int INITIATE_TRANSFER_ON_TARGET_DEVICE_MESSAGE_ID = 1;
    public static final int SOURCE_GET_SESSION_NUMBER_MESSAGE_ID = 2;
    public static final int ABORTABLE_ONE_WAY_DIALOG_MESSAGE_ID = 3;
    public static final int SOURCE_WAIT_FOR_TARGET_CONNECTION_MESSAGE_ID = 4;
    public static final int TARGET_GET_SESSION_NUMBER_MESSAGE_ID = 5;
    public static final int TARGET_SEND_EPHEMERAL_IDENTITY_MESSAGE_ID = 6;
    public static final int SOURCE_SEND_COMMITMENT_MESSAGE_ID = 7;
    public static final int TARGET_SEED_MESSAGE_ID = 8;
    public static final int SOURCE_SAS_INPUT_MESSAGE_ID = 9;
    public static final int SOURCE_DECOMMITMENT_MESSAGE_ID = 10;
    public static final int TARGET_WAIT_FOR_SNAPSHOT_MESSAGE_ID = 11;
    public static final int SOURCE_SNAPSHOT_MESSAGE_ID = 12;
    public static final int SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_MESSAGE_ID = 13;
    public static final int TARGET_RETRIEVE_KEYCLOAK_AUTHENTICATION_PROOF_MESSAGE_ID = 14;


    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIATE_TRANSFER_ON_SOURCE_DEVICE_MESSAGE_ID:
                return InitiateTransferOnSourceDeviceMessage.class;
            case INITIATE_TRANSFER_ON_TARGET_DEVICE_MESSAGE_ID:
                return InitiateTransferOnTargetDeviceMessage.class;
            case SOURCE_GET_SESSION_NUMBER_MESSAGE_ID:
                return SourceGetSessionNumberMessage.class;
            case ABORTABLE_ONE_WAY_DIALOG_MESSAGE_ID:
                return AbortableOneWayDialogMessage.class;
            case SOURCE_WAIT_FOR_TARGET_CONNECTION_MESSAGE_ID:
                return SourceWaitForTargetConnectionMessage.class;
            case TARGET_GET_SESSION_NUMBER_MESSAGE_ID:
                return TargetGetSessionNumberMessage.class;
            case TARGET_SEND_EPHEMERAL_IDENTITY_MESSAGE_ID:
                return TargetSendEphemeralIdentityMessage.class;
            case SOURCE_SEND_COMMITMENT_MESSAGE_ID:
                return SourceSendCommitmentMessage.class;
            case TARGET_SEED_MESSAGE_ID:
                return TargetSeedMessage.class;
            case SOURCE_SAS_INPUT_MESSAGE_ID:
                return SourceSasInputMessage.class;
            case SOURCE_DECOMMITMENT_MESSAGE_ID:
                return SourceDecommitmentMessage.class;
            case TARGET_WAIT_FOR_SNAPSHOT_MESSAGE_ID:
                return TargetWaitForSnapshotMessage.class;
            case SOURCE_SNAPSHOT_MESSAGE_ID:
                return SourceSnapshotMessage.class;
            case SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_MESSAGE_ID:
                return SourceWaitForKeycloakAuthenticationProofMessage.class;
            case TARGET_RETRIEVE_KEYCLOAK_AUTHENTICATION_PROOF_MESSAGE_ID:
                return TargetRetrieveKeycloakAuthenticationProofMessage.class;
            default:
                return null;
        }
    }

    public static class InitiateTransferOnSourceDeviceMessage extends ConcreteProtocolMessage {
        public InitiateTransferOnSourceDeviceMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public InitiateTransferOnSourceDeviceMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_TRANSFER_ON_SOURCE_DEVICE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class InitiateTransferOnTargetDeviceMessage extends ConcreteProtocolMessage {
        private final String deviceName;
        private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
        private final EncryptionPrivateKey encryptionPrivateKey;
        private final MACKey macKey;

        public InitiateTransferOnTargetDeviceMessage(CoreProtocolMessage coreProtocolMessage, String deviceName, ServerAuthenticationPrivateKey serverAuthenticationPrivateKey, EncryptionPrivateKey encryptionPrivateKey, MACKey macKey) {
            super(coreProtocolMessage);
            this.deviceName = deviceName;
            this.serverAuthenticationPrivateKey = serverAuthenticationPrivateKey;
            this.encryptionPrivateKey = encryptionPrivateKey;
            this.macKey = macKey;
        }

        @SuppressWarnings("unused")
        public InitiateTransferOnTargetDeviceMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 4) {
                throw new Exception();
            }
            this.deviceName = list[0].decodeString();
            this.serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) list[1].decodePrivateKey();
            this.encryptionPrivateKey = (EncryptionPrivateKey) list[2].decodePrivateKey();
            this.macKey = (MACKey) list[3].decodeSymmetricKey();
        }


        @Override
        public int getProtocolMessageId() {
            return INITIATE_TRANSFER_ON_TARGET_DEVICE_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(deviceName),
                    Encoded.of(serverAuthenticationPrivateKey),
                    Encoded.of(encryptionPrivateKey),
                    Encoded.of(macKey),
            };
        }
    }


    public static class SourceGetSessionNumberMessage extends EmptyProtocolMessage {
        private final String serializedJsonResponseSource;

        public SourceGetSessionNumberMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            serializedJsonResponseSource = null;
        }

        @SuppressWarnings("unused")
        public SourceGetSessionNumberMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
            serializedJsonResponseSource = receivedMessage.getEncodedResponse() == null ? null : receivedMessage.getEncodedResponse().decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return SOURCE_GET_SESSION_NUMBER_MESSAGE_ID;
        }
    }

    public static abstract class WaitOrRelayMessage extends EmptyProtocolMessage {
        protected final String serializedJsonResponse;

        protected WaitOrRelayMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            serializedJsonResponse = null;
        }

        public WaitOrRelayMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
            serializedJsonResponse = receivedMessage.getEncodedResponse() == null ? null : receivedMessage.getEncodedResponse().decodeString();
        }
    }

    public static class AbortableOneWayDialogMessage extends EmptyProtocolMessage {
        private final UUID dialogUuid;

        AbortableOneWayDialogMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            dialogUuid = null;
        }

        @SuppressWarnings("unused")
        public AbortableOneWayDialogMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
            if (receivedMessage.getEncodedResponse() != null) {
                throw new Exception();
            }
            dialogUuid = receivedMessage.getUserDialogUuid();
        }

        @Override
        public int getProtocolMessageId() {
            return ABORTABLE_ONE_WAY_DIALOG_MESSAGE_ID;
        }
    }

    public static class SourceWaitForTargetConnectionMessage extends WaitOrRelayMessage {
        protected SourceWaitForTargetConnectionMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public SourceWaitForTargetConnectionMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return SOURCE_WAIT_FOR_TARGET_CONNECTION_MESSAGE_ID;
        }
    }


    public static class TargetGetSessionNumberMessage extends EmptyProtocolMessage {
        private final Long sessionNumber;

        TargetGetSessionNumberMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            sessionNumber = null;
        }

        @SuppressWarnings("unused")
        public TargetGetSessionNumberMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
            sessionNumber = receivedMessage.getEncodedResponse() == null ? null : receivedMessage.getEncodedResponse().decodeLong();
        }

        @Override
        public int getProtocolMessageId() {
            return TARGET_GET_SESSION_NUMBER_MESSAGE_ID;
        }
    }

    public static class TargetSendEphemeralIdentityMessage extends WaitOrRelayMessage {
        protected TargetSendEphemeralIdentityMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public TargetSendEphemeralIdentityMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return TARGET_SEND_EPHEMERAL_IDENTITY_MESSAGE_ID;
        }
    }

    public static class SourceSendCommitmentMessage extends WaitOrRelayMessage {
        protected SourceSendCommitmentMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public SourceSendCommitmentMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return SOURCE_SEND_COMMITMENT_MESSAGE_ID;
        }
    }

    public static class TargetSeedMessage extends WaitOrRelayMessage {
        protected TargetSeedMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public TargetSeedMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return TARGET_SEED_MESSAGE_ID;
        }
    }

    public static class SourceSasInputMessage extends EmptyProtocolMessage {
        private final String sas;
        private final UID deviceUidToKeepActive;

        protected SourceSasInputMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            this.sas = null;
            this.deviceUidToKeepActive = null;
        }

        @SuppressWarnings("unused")
        public SourceSasInputMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
            if (receivedMessage.getEncodedResponse() == null) {
                this.sas = null;
                this.deviceUidToKeepActive = null;
            } else {
                Encoded[] list = receivedMessage.getEncodedResponse().decodeList();
                if (list.length == 1) {
                    this.sas = list[0].decodeString();
                    this.deviceUidToKeepActive = null;
                } else {
                    this.sas = list[0].decodeString();
                    this.deviceUidToKeepActive = list[1].decodeUid();
                }
            }
        }

        @Override
        public int getProtocolMessageId() {
            return SOURCE_SAS_INPUT_MESSAGE_ID;
        }
    }


    public static class SourceDecommitmentMessage extends WaitOrRelayMessage {
        protected SourceDecommitmentMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public SourceDecommitmentMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return SOURCE_DECOMMITMENT_MESSAGE_ID;
        }
    }

    public static class TargetWaitForSnapshotMessage extends WaitOrRelayMessage {
        protected TargetWaitForSnapshotMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public TargetWaitForSnapshotMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return TARGET_WAIT_FOR_SNAPSHOT_MESSAGE_ID;
        }
    }

    public static class SourceSnapshotMessage extends WaitOrRelayMessage {
        protected SourceSnapshotMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public SourceSnapshotMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return SOURCE_SNAPSHOT_MESSAGE_ID;
        }
    }

    public static class CloseWebSocketMessage extends EmptyProtocolMessage {
        protected CloseWebSocketMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return -1;
        }
    }

    public static class SourceWaitForKeycloakAuthenticationProofMessage extends WaitOrRelayMessage {
        protected SourceWaitForKeycloakAuthenticationProofMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public SourceWaitForKeycloakAuthenticationProofMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_MESSAGE_ID;
        }
    }

    public static class TargetRetrieveKeycloakAuthenticationProofMessage extends EmptyProtocolMessage {
        private final String signature;
        private final String serializedKeycloakAuthState;

        public TargetRetrieveKeycloakAuthenticationProofMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            this.signature = null;
            this.serializedKeycloakAuthState = null;
        }

        @SuppressWarnings("unused")
        public TargetRetrieveKeycloakAuthenticationProofMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
            if (receivedMessage.getEncodedResponse() == null) {
                this.signature = null;
                this.serializedKeycloakAuthState = null;
            } else {
                Encoded[] list = receivedMessage.getEncodedResponse().decodeList();
                if (list.length == 2) {
                    this.signature = list[0].decodeString();
                    this.serializedKeycloakAuthState = list[1].decodeString();
                } else {
                    this.signature = null;
                    this.serializedKeycloakAuthState = null;
                }
            }
        }

        @Override
        public int getProtocolMessageId() {
            return TARGET_RETRIEVE_KEYCLOAK_AUTHENTICATION_PROOF_MESSAGE_ID;
        }
    }

    // endregion





    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{InitiateTransferOnSourceDeviceStep.class, InitiateTransferOnTargetDeviceStep.class};
            case SOURCE_WAITING_FOR_SESSION_NUMBER_STATE_ID:
                return new Class[]{SourceDisplaysSessionNumberStep.class, UserInitiatedAbortProtocolStep.class};
            case SOURCE_WAITING_FOR_TARGET_CONNECTION_STATE_ID:
                return new Class[]{SourceSendsTransferredIdentityAndCommitmentStep.class, UserInitiatedAbortProtocolStep.class};
            case TARGET_WAITING_FOR_SESSION_NUMBER_STATE_ID:
                return new Class[]{TargetProcessesSessionNumberAndSendsEphemeralIdentityStep.class};
            case TARGET_WAITING_FOR_TRANSFERRED_IDENTITY_STATE_ID:
                return new Class[]{TargetSendsSeedStep.class, UserInitiatedAbortProtocolStep.class};
            case SOURCE_WAITING_FOR_TARGET_SEED_STATE_ID:
                return new Class[]{SourceSendsDecommitmentAndShowsSasInputStep.class, UserInitiatedAbortProtocolStep.class};
            case TARGET_WAITING_FOR_DECOMMITMENT_STATE_ID:
                return new Class[]{TargetShowsSasStep.class, UserInitiatedAbortProtocolStep.class};
            case SOURCE_WAITING_FOR_SAS_INPUT_STATE_ID:
                return new Class[]{SourceCheckSasInputAndSendSnapshotStep.class};
            case TARGET_WAITING_FOR_SNAPSHOT_STATE_ID:
                return new Class[]{TargetProcessesSnapshotStep.class, UserInitiatedAbortProtocolStep.class};
            case SOURCE_WAIT_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID:
                return new Class[]{SourceCheckTransferProofAndSendSnapshotStep.class};
            case TARGET_WAITING_FOR_KEYCLOAK_AUTHENTICATION_PROOF_STATE_ID:
                return new Class[]{TargetSendKeycloakAuthenticationProofStep.class};
            case FINAL_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class InitiateTransferOnSourceDeviceStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitiateTransferOnSourceDeviceMessage receivedMessage;

        public InitiateTransferOnSourceDeviceStep(InitialProtocolState startState, InitiateTransferOnSourceDeviceMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UUID dialogUuid = UUID.randomUUID();
            {
                // display spinner dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.SourceWaitForSessionNumberStep()), dialogUuid));
                ChannelMessageToSend messageToSend = new AbortableOneWayDialogMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // connect to the transfer server and get a session number
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferSourceQuery()));
                ChannelMessageToSend messageToSend = new SourceGetSessionNumberMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new SourceWaitingForSessionNumberState(dialogUuid);
        }
    }

    public static class SourceDisplaysSessionNumberStep extends ProtocolStep {
        private final SourceWaitingForSessionNumberState startState;
        private final SourceGetSessionNumberMessage receivedMessage;

        @SuppressWarnings("unused")
        public SourceDisplaysSessionNumberStep(SourceWaitingForSessionNumberState startState, SourceGetSessionNumberMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UUID dialogUuid = startState.dialogUuid;

            // check if the server query failed
            if (receivedMessage.serializedJsonResponseSource == null) {
                return failProtocol(this, dialogUuid, ObvTransferStep.Fail.FAIL_REASON_NETWORK_ERROR);
            }

            Long sessionNumber;
            String ownConnectionIdentifier;

            try {
                JsonResponseSource jsonResponseSource = getJsonObjectMapper().readValue(receivedMessage.serializedJsonResponseSource, JsonResponseSource.class);
                sessionNumber = jsonResponseSource.sessionNumber;
                ownConnectionIdentifier = jsonResponseSource.awsConnectionId;
            } catch (Exception e) {
                return failProtocol(this, dialogUuid, ObvTransferStep.Fail.FAIL_REASON_INVALID_RESPONSE);
            }

            if (sessionNumber == null || ownConnectionIdentifier == null) {
                return failProtocol(this, dialogUuid, ObvTransferStep.Fail.FAIL_REASON_NETWORK_ERROR);
            }

            {
                // display session number
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.SourceDisplaySessionNumber(sessionNumber)), dialogUuid));
                ChannelMessageToSend messageToSend = new AbortableOneWayDialogMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // wait for the transfer server's target connection message
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
                ChannelMessageToSend messageToSend = new SourceWaitForTargetConnectionMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new SourceWaitingForTargetConnectionState(dialogUuid, ownConnectionIdentifier, sessionNumber);
        }
    }


    public static class InitiateTransferOnTargetDeviceStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitiateTransferOnTargetDeviceMessage receivedMessage;

        public InitiateTransferOnTargetDeviceStep(InitialProtocolState startState, InitiateTransferOnTargetDeviceMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            UUID dialogUuid = UUID.randomUUID();
            {
                // display session number input field
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.TargetSessionNumberInput()), dialogUuid));
                ChannelMessageToSend messageToSend = new TargetGetSessionNumberMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new TargetWaitingForSessionNumberState(dialogUuid, receivedMessage.deviceName, receivedMessage.serverAuthenticationPrivateKey, receivedMessage.encryptionPrivateKey, receivedMessage.macKey);
        }
    }

    public static class TargetProcessesSessionNumberAndSendsEphemeralIdentityStep extends ProtocolStep {
        private final TargetWaitingForSessionNumberState startState;
        private final TargetGetSessionNumberMessage receivedMessage;

        public TargetProcessesSessionNumberAndSendsEphemeralIdentityStep(TargetWaitingForSessionNumberState startState, TargetGetSessionNumberMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.sessionNumber == null) {
                return userInitiatedAbortProtocol(this, startState.dialogUuid);
            }

            {
                // display spinner dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.OngoingProtocol()), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new AbortableOneWayDialogMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // send the ephemeral owned identity to the source
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferTargetQuery(receivedMessage.sessionNumber, Encoded.of(getOwnedIdentity()).getBytes())));
                ChannelMessageToSend messageToSend = new TargetSendEphemeralIdentityMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new TargetWaitingForTransferredIdentityState(startState.dialogUuid, startState.deviceName, startState.serverAuthenticationPrivateKey, startState.encryptionPrivateKey, startState.macKey, receivedMessage.sessionNumber);
        }
    }


    public static class SourceSendsTransferredIdentityAndCommitmentStep extends ProtocolStep {
        private final SourceWaitingForTargetConnectionState startState;
        private final SourceWaitForTargetConnectionMessage receivedMessage;

        public SourceSendsTransferredIdentityAndCommitmentStep(SourceWaitingForTargetConnectionState startState, SourceWaitForTargetConnectionMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        private ConcreteProtocolState restartStep(ProtocolManagerSession protocolManagerSession) throws Exception {
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
            ChannelMessageToSend messageToSend = new SourceWaitForTargetConnectionMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.serializedJsonResponse == null) {
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_NETWORK_ERROR);
            }

            JsonResponse jsonResponse;
            Identity ephemeralIdentity;
            try {
                jsonResponse = getJsonObjectMapper().readValue(receivedMessage.serializedJsonResponse, JsonResponse.class);
                ephemeralIdentity = new Encoded(jsonResponse.payload).decodeIdentity();
            } catch (Exception e) {
                // failed to parse the response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.SourceSendsTransferredIdentityAndCommitmentStep failed to parse response");
                return restartStep(protocolManagerSession);

            }

            if (jsonResponse.otherConnectionId == null || ephemeralIdentity == null) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.SourceSendsTransferredIdentityAndCommitmentStep invalid response");
                return restartStep(protocolManagerSession);
            }


            Seed seedSourceForSas = new Seed(getPrng());
            Commitment commitmentScheme = Suite.getDefaultCommitment(0);
            Commitment.CommitmentOutput commitmentOutput = commitmentScheme.commit(
                    getOwnedIdentity().getBytes(),
                    seedSourceForSas.getBytes(),
                    getPrng()
            );

            // send our own connectionIdentifier, the identity to transfer and a commitment
            byte[] cleartextPayload = Encoded.of(new Encoded[]{
                    Encoded.of(startState.ownConnectionIdentifier),
                    Encoded.of(getOwnedIdentity()),
                    Encoded.of(commitmentOutput.commitment),
            }).getBytes();

            EncryptedBytes payload = Suite.getPublicKeyEncryption(ephemeralIdentity.getEncryptionPublicKey()).encrypt(ephemeralIdentity.getEncryptionPublicKey(), cleartextPayload, getPrng());

            {
                // send the encrypted payload
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferRelayQuery(jsonResponse.otherConnectionId, payload.getBytes(), false)));
                ChannelMessageToSend messageToSend = new SourceSendCommitmentMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // display spinner dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.OngoingProtocol()), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new AbortableOneWayDialogMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new SourceWaitingForTargetSeedState(startState.dialogUuid, jsonResponse.otherConnectionId, ephemeralIdentity, seedSourceForSas, commitmentOutput.decommitment, startState.sessionNumber);
        }
    }



    public static class TargetSendsSeedStep extends ProtocolStep {
        private final TargetWaitingForTransferredIdentityState startState;
        private final TargetSendEphemeralIdentityMessage receivedMessage;

        public TargetSendsSeedStep(TargetWaitingForTransferredIdentityState startState, TargetSendEphemeralIdentityMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        private ConcreteProtocolState restartStep(ProtocolManagerSession protocolManagerSession) throws Exception {
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
            ChannelMessageToSend messageToSend = new TargetSendEphemeralIdentityMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.serializedJsonResponse == null) {
                // this happens if the session number was rejected by the server
                //  --> prompt for a new session number
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.TargetSessionNumberInput()), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new TargetGetSessionNumberMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new TargetWaitingForSessionNumberState(startState.dialogUuid, startState.deviceName, startState.serverAuthenticationPrivateKey, startState.encryptionPrivateKey, startState.macKey);
            }

            JsonResponse jsonResponse;
            try {
                jsonResponse = getJsonObjectMapper().readValue(receivedMessage.serializedJsonResponse, JsonResponse.class);
            } catch (Exception e) {
                // failed to parse the response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetSendsSeedStep failed to parse response");
                return restartStep(protocolManagerSession);
            }

            if (jsonResponse.otherConnectionId == null || jsonResponse.payload == null) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetSendsSeedStep invalid response");
                return restartStep(protocolManagerSession);
            }

            String otherConnectionIdentifier;
            Identity transferredIdentity;
            byte[] commitment;
            try {
                // decrypt and parse relayed message
                byte[] cleartextPayload = Suite.getPublicKeyEncryption(startState.encryptionPrivateKey).decrypt(startState.encryptionPrivateKey, new EncryptedBytes(jsonResponse.payload));
                Encoded[] list = new Encoded(cleartextPayload).decodeList();
                if (list.length != 3) {
                    throw new DecodingException();
                }
                otherConnectionIdentifier = list[0].decodeString();
                transferredIdentity = list[1].decodeIdentity();
                commitment = list[2].decodeBytes();
            } catch (Exception e) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetSendsSeedStep failed to decrypt and parse response");
                return restartStep(protocolManagerSession);
            }

            if (!Objects.equals(otherConnectionIdentifier, jsonResponse.otherConnectionId)) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetSendsSeedStep connection identifier mismatch!");
                return restartStep(protocolManagerSession);
            }

            if (protocolManagerSession.identityDelegate.isOwnedIdentity(protocolManagerSession.session, transferredIdentity)) {
                Logger.w("OwnedIdentityTransferProtocol: transferred identity is already an owned identity!");
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_TRANSFERRED_IDENTITY_ALREADY_EXISTS);
            }

            // compute Target part of the SAS
            PrivateIdentity privateIdentity = new PrivateIdentity(getOwnedIdentity(), startState.serverAuthenticationPrivateKey, startState.encryptionPrivateKey, startState.macKey);
            Seed seedTargetForSas = privateIdentity.getDeterministicSeedForOwnedIdentity(commitment, IdentityDelegate.DeterministicSeedContext.COMPUTE_TRANSFER_SAS);

            {
                // send the seedTargetForSas to Source
                Encoded dataToSend = Encoded.of(new Encoded[]{
                        Encoded.of(startState.deviceName),
                        Encoded.of(seedTargetForSas),
                });
                EncryptedBytes payload = Suite.getPublicKeyEncryption(transferredIdentity.getEncryptionPublicKey()).encrypt(transferredIdentity.getEncryptionPublicKey(), dataToSend.getBytes(), getPrng());
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferRelayQuery(otherConnectionIdentifier, payload.getBytes(), false)));
                ChannelMessageToSend messageToSend = new TargetSeedMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new TargetWaitingForDecommitmentState(startState.dialogUuid, startState.deviceName, otherConnectionIdentifier, transferredIdentity, commitment, seedTargetForSas, startState.serverAuthenticationPrivateKey, startState.encryptionPrivateKey, startState.macKey, startState.sessionNumber);
        }
    }



    public static class SourceSendsDecommitmentAndShowsSasInputStep extends ProtocolStep {
        private final SourceWaitingForTargetSeedState startState;
        private final SourceSendCommitmentMessage receivedMessage;

        public SourceSendsDecommitmentAndShowsSasInputStep(SourceWaitingForTargetSeedState startState, SourceSendCommitmentMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        private ConcreteProtocolState restartStep(ProtocolManagerSession protocolManagerSession) throws Exception {
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
            ChannelMessageToSend messageToSend = new SourceSendCommitmentMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.serializedJsonResponse == null) {
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_NETWORK_ERROR);
            }

            JsonResponse jsonResponse;
            try {
                jsonResponse = getJsonObjectMapper().readValue(receivedMessage.serializedJsonResponse, JsonResponse.class);
            } catch (Exception e) {
                // failed to parse the response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.SourceSendsDecommitmentAndShowsSasInputStep failed to parse response");
                return restartStep(protocolManagerSession);
            }

            if (!Objects.equals(jsonResponse.otherConnectionId, startState.otherConnectionIdentifier) || jsonResponse.payload == null) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.SourceSendsDecommitmentAndShowsSasInputStep invalid response or connectionIdentifier mismatch");
                return restartStep(protocolManagerSession);
            }

            Seed seedTargetForSas;
            String targetDeviceName;
            try {
                // decrypt and parse relayed message
                byte[] cleartextPayload = protocolManagerSession.encryptionForIdentityDelegate.decrypt(protocolManagerSession.session, new EncryptedBytes(jsonResponse.payload), getOwnedIdentity());

                Encoded[] list = new Encoded(cleartextPayload).decodeList(); // if cleartextPayload is null, this will throw
                targetDeviceName = list[0].decodeString();
                seedTargetForSas = list[1].decodeSeed();
            }  catch (Exception e) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.SourceSendsDecommitmentAndShowsSasInputStep failed to decrypt and parse response");
                return restartStep(protocolManagerSession);
            }

            {
                // send the decommitment
                EncryptedBytes payload = Suite.getPublicKeyEncryption(startState.ephemeralIdentity.getEncryptionPublicKey()).encrypt(startState.ephemeralIdentity.getEncryptionPublicKey(), startState.decommitment, getPrng());
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferRelayQuery(startState.otherConnectionIdentifier, payload.getBytes(), true)));
                ChannelMessageToSend messageToSend = new SourceDecommitmentMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }


            // compute the complete SAS
            String fullSas = new String(SAS.computeDouble(startState.seedSourceForSas, seedTargetForSas, startState.ephemeralIdentity, Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS), StandardCharsets.UTF_8);
            {
                // show a dialog for SAS input
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.SourceSasInput(fullSas, targetDeviceName)), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new SourceSasInputMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new SourceWaitingForSasInputState(startState.dialogUuid, startState.otherConnectionIdentifier, targetDeviceName, startState.ephemeralIdentity, fullSas, startState.sessionNumber);
        }
    }


    public static class TargetShowsSasStep extends ProtocolStep {
        private final TargetWaitingForDecommitmentState startState;
        private final TargetSeedMessage receivedMessage;

        public TargetShowsSasStep(TargetWaitingForDecommitmentState startState, TargetSeedMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        private ConcreteProtocolState restartStep(ProtocolManagerSession protocolManagerSession) throws Exception {
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
            ChannelMessageToSend messageToSend = new TargetSeedMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.serializedJsonResponse == null) {
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_NETWORK_ERROR);
            }

            JsonResponse jsonResponse;
            try {
                jsonResponse = getJsonObjectMapper().readValue(receivedMessage.serializedJsonResponse, JsonResponse.class);
            } catch (Exception e) {
                // failed to parse the response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetShowsSasStep failed to parse response");
                return restartStep(protocolManagerSession);
            }

            if (!Objects.equals(jsonResponse.otherConnectionId, startState.otherConnectionIdentifier) || jsonResponse.payload == null) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetShowsSasStep invalid response or connectionIdentifier mismatch");
                return restartStep(protocolManagerSession);
            }

            byte[] decommitment;
            try {
                // decrypt and parse relayed message
                decommitment = Suite.getPublicKeyEncryption(startState.encryptionPrivateKey).decrypt(startState.encryptionPrivateKey, new EncryptedBytes(jsonResponse.payload));
                if (decommitment == null) {
                    throw new Exception();
                }
            }  catch (Exception e) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetShowsSasStep failed to decrypt and parse response");
                return restartStep(protocolManagerSession);
            }

            byte[] fullSas;
            {
                // open the commitment and compute the full SAS
                Commitment commitmentScheme = Suite.getDefaultCommitment(0);
                byte[] opened = commitmentScheme.open(startState.transferredIdentity.getBytes(), startState.commitment, decommitment);
                if (opened == null) {
                    Logger.e("Unable to open commitment.");
                    return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_INVALID_RESPONSE);
                }
                Seed seedSourceForSas = new Seed(opened);
                fullSas = SAS.computeDouble(seedSourceForSas, startState.seedTargetForSas, getOwnedIdentity(), Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);
            }

            {
                // show the SAS dialog
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.TargetShowSas(new String(fullSas, StandardCharsets.UTF_8))), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new AbortableOneWayDialogMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // send a wait message to receive the snapshot
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
                ChannelMessageToSend messageToSend = new TargetWaitForSnapshotMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new TargetWaitingForSnapshotState(startState.dialogUuid, startState.deviceName, startState.otherConnectionIdentifier, startState.transferredIdentity, startState.serverAuthenticationPrivateKey, startState.encryptionPrivateKey, startState.macKey, new String(fullSas, StandardCharsets.UTF_8), startState.sessionNumber, null);
        }
    }



    public static class SourceCheckSasInputAndSendSnapshotStep extends ProtocolStep {
        private final SourceWaitingForSasInputState startState;
        private final SourceSasInputMessage receivedMessage;

        public SourceCheckSasInputAndSendSnapshotStep(SourceWaitingForSasInputState startState, SourceSasInputMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.sas == null) {
                return userInitiatedAbortProtocol(this, startState.dialogUuid);
            }

            if (!Objects.equals(receivedMessage.sas, startState.fullSas)) {
                // wrong sas --> show the dialog for SAS input again
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.SourceSasInput(startState.fullSas, startState.targetDeviceName)), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new SourceSasInputMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return startState;
            }

            // check if owned identity is keycloak managed and transfer restricted
            ObvKeycloakState keycloakState = protocolManagerSession.identityDelegate.getOwnedIdentityKeycloakState(protocolManagerSession.session, getOwnedIdentity());
            if (keycloakState != null && keycloakState.transferRestricted) {
                // sas is correct --> send keycloak parameters so the target device can authenticate and respond with a transferProof
                JsonKeycloakConfiguration configuration = new JsonKeycloakConfiguration();
                configuration.server = keycloakState.keycloakServer;
                configuration.cid = keycloakState.clientId;
                configuration.secret = keycloakState.clientSecret;

                byte[] dataToSend = getJsonObjectMapper().writeValueAsBytes(configuration);
                EncryptedBytes payload = Suite.getPublicKeyEncryption(startState.ephemeralIdentity.getEncryptionPublicKey()).encrypt(startState.ephemeralIdentity.getEncryptionPublicKey(), dataToSend, getPrng());

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferRelayQuery(startState.otherConnectionIdentifier, payload.getBytes(), false)));
                ChannelMessageToSend messageToSend = new SourceWaitForKeycloakAuthenticationProofMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new SourceWaitForKeycloakAuthenticationProofState(startState.dialogUuid, startState.otherConnectionIdentifier, startState.ephemeralIdentity, startState.fullSas, startState.sessionNumber, receivedMessage.deviceUidToKeepActive);
            } else {
                // sas is correct --> we can send a snapshot
                sendSnapshotAndCloseWebsocket(
                        protocolManagerSession,
                        getProtocolInstanceUid(),
                        getOwnedIdentity(),
                        receivedMessage.deviceUidToKeepActive,
                        startState.otherConnectionIdentifier,
                        startState.ephemeralIdentity,
                        startState.dialogUuid,
                        getPrng());

                return new FinalState();
            }
        }
    }

    private static void sendSnapshotAndCloseWebsocket(
            ProtocolManagerSession protocolManagerSession,
            UID protocolInstanceUid,
            Identity ownedIdentity,
            UID deviceUidToKeepActive,
            String otherConnectionIdentifier,
            Identity ephemeralIdentity,
            UUID dialogUuid,
            PRNGService prng) throws Exception {

        {
            ObvBackupAndSyncDelegate wrappedIdentityDelegate = protocolManagerSession.identityDelegate.getSyncDelegateWithinTransaction(protocolManagerSession.session);

            ObvSyncSnapshot syncSnapshot = ObvSyncSnapshot.get(ownedIdentity, wrappedIdentityDelegate, protocolManagerSession.appBackupAndSyncDelegate);
            byte[] cleartext;
            if (deviceUidToKeepActive == null) {
                cleartext = Encoded.of(new Encoded[]{
                        Encoded.of(syncSnapshot.toEncodedDictionary(wrappedIdentityDelegate, protocolManagerSession.appBackupAndSyncDelegate)),
                }).getBytes();
            } else {
                cleartext = Encoded.of(new Encoded[]{
                        Encoded.of(syncSnapshot.toEncodedDictionary(wrappedIdentityDelegate, protocolManagerSession.appBackupAndSyncDelegate)),
                        Encoded.of(deviceUidToKeepActive),
                }).getBytes();
            }
            EncryptedBytes payload = Suite.getPublicKeyEncryption(ephemeralIdentity.getEncryptionPublicKey()).encrypt(ephemeralIdentity.getEncryptionPublicKey(), cleartext, prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(ownedIdentity, new ServerQuery.TransferRelayQuery(otherConnectionIdentifier, payload.getBytes(), true)), OWNED_IDENTITY_TRANSFER_PROTOCOL_ID, protocolInstanceUid);
            ChannelMessageToSend messageToSend = new SourceSnapshotMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, prng);
        }

        {
            // close the websocket
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(ownedIdentity, new ServerQuery.TransferCloseQuery(false)), OWNED_IDENTITY_TRANSFER_PROTOCOL_ID, protocolInstanceUid);
            ChannelMessageToSend messageToSend = new CloseWebSocketMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, prng);
        }

        {
            // notify the app to end
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(ownedIdentity, DialogType.createTransferDialog(new ObvTransferStep.SourceSnapshotSent()), dialogUuid), OWNED_IDENTITY_TRANSFER_PROTOCOL_ID, protocolInstanceUid);
            ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, prng);
        }
    }


    public static class SourceCheckTransferProofAndSendSnapshotStep extends ProtocolStep {
        private final SourceWaitForKeycloakAuthenticationProofState startState;
        private final SourceWaitForKeycloakAuthenticationProofMessage receivedMessage;

        public SourceCheckTransferProofAndSendSnapshotStep(SourceWaitForKeycloakAuthenticationProofState startState, SourceWaitForKeycloakAuthenticationProofMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        private ConcreteProtocolState restartStep(ProtocolManagerSession protocolManagerSession) throws Exception {
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
            ChannelMessageToSend messageToSend = new SourceWaitForKeycloakAuthenticationProofMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.serializedJsonResponse == null) {
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_NETWORK_ERROR);
            }

            JsonResponse jsonResponse;
            String signature;
            try {
                jsonResponse = getJsonObjectMapper().readValue(receivedMessage.serializedJsonResponse, JsonResponse.class);
                byte[] cleartextPayload = protocolManagerSession.encryptionForIdentityDelegate.decrypt(protocolManagerSession.session, new EncryptedBytes(jsonResponse.payload), getOwnedIdentity());

                signature = new String(cleartextPayload, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // failed to parse the response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.SourceCheckTransferProofAndSendSnapshotStep failed to parse response");
                return restartStep(protocolManagerSession);

            }

            if (!Objects.equals(jsonResponse.otherConnectionId, startState.otherConnectionIdentifier)) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.SourceCheckTransferProofAndSendSnapshotStep invalid response");
                return restartStep(protocolManagerSession);
            }


            // validate the received signature
            try {
                String signedContent = protocolManagerSession.identityDelegate.verifyKeycloakSignature(protocolManagerSession.session, getOwnedIdentity(), signature);
                JsonTransferProof transferProof = getJsonObjectMapper().readValue(signedContent, JsonTransferProof.class);

                String keycloakUserId = protocolManagerSession.identityDelegate.getOwnedIdentityKeycloakUserId(protocolManagerSession.session, getOwnedIdentity());

                if (!Objects.equals(transferProof.session_id, String.format(Locale.ENGLISH, "%08d", startState.sessionNumber))
                        || !Objects.equals(transferProof.sas, startState.fullSas)
                        || !Arrays.equals(transferProof.identity, getOwnedIdentity().getBytes())
                        || !Objects.equals(transferProof.keycloak_id, keycloakUserId)) {
                    return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_INVALID_RESPONSE);
                }
            } catch (Exception ignored) {
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_INVALID_RESPONSE);
            }

            sendSnapshotAndCloseWebsocket(
                    protocolManagerSession,
                    getProtocolInstanceUid(),
                    getOwnedIdentity(),
                    startState.deviceUidToKeepActive,
                    startState.otherConnectionIdentifier,
                    startState.ephemeralIdentity,
                    startState.dialogUuid,
                    getPrng());

            return new FinalState();
        }
    }


    public static class TargetProcessesSnapshotStep extends ProtocolStep {
        // used to keep a reference to the listener waiting for the new device to be registered on the server
        private static NotificationListener deviceRegisteredNotificationListener = null;
        private static Long deviceRegisteredNotificationListenerNumber = null;

        private final TargetWaitingForSnapshotState startState;
        private final TargetWaitForSnapshotMessage receivedMessage;

        public TargetProcessesSnapshotStep(TargetWaitingForSnapshotState startState, TargetWaitForSnapshotMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        private ConcreteProtocolState restartStep(ProtocolManagerSession protocolManagerSession) throws Exception {
            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferWaitQuery()));
            ChannelMessageToSend messageToSend = new TargetWaitForSnapshotMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.serializedJsonResponse == null) {
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_NETWORK_ERROR);
            }


            JsonResponse jsonResponse;
            try {
                jsonResponse = getJsonObjectMapper().readValue(receivedMessage.serializedJsonResponse, JsonResponse.class);
            } catch (Exception e) {
                // failed to parse the response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetProcessesSnapshotStep failed to parse response");
                return restartStep(protocolManagerSession);
            }

            if (!Objects.equals(jsonResponse.otherConnectionId, startState.otherConnectionIdentifier) || jsonResponse.payload == null) {
                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetProcessesSnapshotStep invalid response or connectionIdentifier mismatch");
                return restartStep(protocolManagerSession);
            }

            ObvBackupAndSyncDelegate wrappedIdentityDelegate = protocolManagerSession.identityDelegate.getSyncDelegateWithinTransaction(protocolManagerSession.session);

            byte[] plaintext = null;
            ObvSyncSnapshot syncSnapshot;
            UID deviceUidToKeepActive;
            try {
                // decrypt
                plaintext = Suite.getPublicKeyEncryption(startState.encryptionPrivateKey).decrypt(startState.encryptionPrivateKey, new EncryptedBytes(jsonResponse.payload));

                // parse relayed message
                Encoded[] list = new Encoded(plaintext).decodeList();

                // make sure we can parse the snapshot, but don't do anything with it, the app will take care of this
                syncSnapshot = ObvSyncSnapshot.fromEncodedDictionary(list[0].decodeDictionary(), wrappedIdentityDelegate, protocolManagerSession.appBackupAndSyncDelegate);
                if (syncSnapshot == null) {
                    return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_INVALID_RESPONSE);
                }

                if (list.length == 2) {
                    deviceUidToKeepActive = list[1].decodeUid();
                } else {
                    deviceUidToKeepActive = null;
                }
            }  catch (Exception e) {
                // parsing failed, try to parse it as a keycloak configuration
                if (plaintext != null) {
                    try {
                        JsonKeycloakConfiguration jsonKeycloakConfiguration = getJsonObjectMapper().readValue(plaintext, JsonKeycloakConfiguration.class);
                        if (jsonKeycloakConfiguration != null && jsonKeycloakConfiguration.server != null && jsonKeycloakConfiguration.cid != null) {
                            // we have received a JsonKeycloakConfiguration that needs to be passed to the app to force authentication
                            {
                                // send keycloak config to app
                                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.TargetRequestsKeycloakAuthenticationProof(jsonKeycloakConfiguration.server, jsonKeycloakConfiguration.cid, jsonKeycloakConfiguration.secret, startState.fullSas, startState.sessionNumber)), startState.dialogUuid));
                                ChannelMessageToSend messageToSend = new TargetRetrieveKeycloakAuthenticationProofMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                            }

                            return new TargetWaitingForKeycloakAuthenticationProofState(
                                    startState.dialogUuid,
                                    startState.deviceName,
                                    startState.otherConnectionIdentifier,
                                    startState.transferredIdentity,
                                    startState.serverAuthenticationPrivateKey,
                                    startState.encryptionPrivateKey,
                                    startState.macKey,
                                    startState.fullSas,
                                    startState.sessionNumber
                            );
                        }
                    } catch (Exception ignored) { }
                }


                // invalid response --> send a Wait message and return to start state
                Logger.w("OwnedIdentityTransferProtocol.TargetProcessesSnapshotStep failed to decrypt and parse response");
                return restartStep(protocolManagerSession);
            }

            ////////
            // create the list of callbacks and add the sessionCommitListener first, so the delegates get a
            // chance to perform an action before the engine restore notifications start being sent
            final List<ObvBackupAndSyncDelegate.RestoreFinishedCallback> commitCallbackList = new ArrayList<>();
            protocolManagerSession.session.addSessionCommitListener(() -> {
                for (ObvBackupAndSyncDelegate.RestoreFinishedCallback callback : commitCallbackList) {
                    callback.onRestoreSuccess();
                }
            });

            try {

                // create the owned identity (and associated stuff) at engine level
                ObvSyncSnapshotNode node = syncSnapshot.getSnapshotNode(wrappedIdentityDelegate.getTag());
                ObvIdentity obvOwnedIdentity;
                if (node instanceof IdentityManagerSyncSnapshot) {
                    obvOwnedIdentity = protocolManagerSession.identityDelegate.restoreTransferredOwnedIdentity(protocolManagerSession.session, startState.deviceName, ((IdentityManagerSyncSnapshot) node));
                    if (startState.serializedKeycloakAuthState != null) {
                        protocolManagerSession.identityDelegate.saveKeycloakAuthState(protocolManagerSession.session, obvOwnedIdentity.getIdentity(), startState.serializedKeycloakAuthState);
                    }
                } else {
                    throw new Exception();
                }

                // give a chance for all delegates to create an owned identity based on what the engine just created
                List<ObvBackupAndSyncDelegate.RestoreFinishedCallback> callbacksOwnedIdentity = syncSnapshot.restoreOwnedIdentity(obvOwnedIdentity, wrappedIdentityDelegate, protocolManagerSession.appBackupAndSyncDelegate);
                if (callbacksOwnedIdentity != null && !callbacksOwnedIdentity.isEmpty()) {
                    commitCallbackList.addAll(callbacksOwnedIdentity);
                }


                {
                    // actually restore the snapshot
                    List<ObvBackupAndSyncDelegate.RestoreFinishedCallback> callbacks = syncSnapshot.restore(wrappedIdentityDelegate, protocolManagerSession.appBackupAndSyncDelegate);

                    if (callbacks != null && !callbacks.isEmpty()) {
                        commitCallbackList.addAll(callbacks);
                    }
                }
            } catch (Exception e) {
                // if an exception occurs, always call the failure of any already added callback
                for (ObvBackupAndSyncDelegate.RestoreFinishedCallback callback : commitCallbackList) {
                    callback.onRestoreFailure();
                }
                throw e;
            }



            if (deviceUidToKeepActive != null) {
                if (deviceRegisteredNotificationListenerNumber != null) {
                    // remove any left-over listener
                    protocolManagerSession.notificationListeningDelegate.removeListener(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED, deviceRegisteredNotificationListenerNumber);
                }
                // create the new listener
                deviceRegisteredNotificationListener = (String notificationName, Map<String, Object> userInfo) -> {
                    try {
                        if (!Objects.equals(notificationName, DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED) || userInfo == null) {
                            return;
                        }
                        Object identity = userInfo.get(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED_OWNED_IDENTITY_KEY);
                        if (!(identity instanceof Identity) || !Objects.equals(startState.transferredIdentity, identity)) {
                            return;
                        }

                        // this is the right notification, unregister this listener
                        if (deviceRegisteredNotificationListenerNumber != null) {
                            protocolManagerSession.notificationListeningDelegate.removeListener(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED, deviceRegisteredNotificationListenerNumber);
                            deviceRegisteredNotificationListener = null;
                            deviceRegisteredNotificationListenerNumber = null;
                        }

                        // trigger the device keep active request
                        protocolManagerSession.protocolStarterDelegate.processDeviceManagementRequest(startState.transferredIdentity, ObvDeviceManagementRequest.createSetUnexpiringDeviceRequest(deviceUidToKeepActive.getBytes()));
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                };
                // register it
                deviceRegisteredNotificationListenerNumber = protocolManagerSession.notificationListeningDelegate.addListener(DownloadNotifications.NOTIFICATION_PUSH_NOTIFICATION_REGISTERED, deviceRegisteredNotificationListener);
            }



            try {
                // trigger a download of all user data (including other identities, but we do not really care...)
                protocolManagerSession.identityDelegate.downloadAllUserData(protocolManagerSession.session);
            } catch (Exception ignored) { }



            {
                // close the websocket
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferCloseQuery(false)));
                ChannelMessageToSend messageToSend = new CloseWebSocketMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            {
                // notify the app that the transfer is finished
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.TargetSnapshotReceived()), startState.dialogUuid));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }



            {
                // at the very end, add a final session commit listener that will be called after all engine notifications are sent
                protocolManagerSession.session.addSessionCommitListener(() -> protocolManagerSession.notificationPostingDelegate.postNotification(ProtocolNotifications.NOTIFICATION_SNAPSHOT_RESTORATION_FINISHED, Collections.emptyMap()));
            }

            return new FinalState();
        }
    }


    public static class TargetSendKeycloakAuthenticationProofStep extends ProtocolStep {

        private final TargetWaitingForKeycloakAuthenticationProofState startState;
        private final TargetRetrieveKeycloakAuthenticationProofMessage receivedMessage;

        public TargetSendKeycloakAuthenticationProofStep(TargetWaitingForKeycloakAuthenticationProofState startState, TargetRetrieveKeycloakAuthenticationProofMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.signature == null) {
                return failProtocol(this, startState.dialogUuid, ObvTransferStep.Fail.FAIL_REASON_INVALID_RESPONSE);
            }


            {
                // send the signature to the source
                EncryptedBytes payload = Suite.getPublicKeyEncryption(startState.transferredIdentity.getEncryptionPublicKey()).encrypt(startState.transferredIdentity.getEncryptionPublicKey(), receivedMessage.signature.getBytes(StandardCharsets.UTF_8), getPrng());
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.TransferRelayQuery(startState.otherConnectionIdentifier, payload.getBytes(), false)));
                ChannelMessageToSend messageToSend = new TargetWaitForSnapshotMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }

            return new TargetWaitingForSnapshotState(
                    startState.dialogUuid,
                    startState.deviceName,
                    startState.otherConnectionIdentifier,
                    startState.transferredIdentity,
                    startState.serverAuthenticationPrivateKey,
                    startState.encryptionPrivateKey,
                    startState.macKey,
                    startState.fullSas,
                    startState.sessionNumber,
                    receivedMessage.serializedKeycloakAuthState
            );
        }
    }









    public static class UserInitiatedAbortProtocolStep extends ProtocolStep {
        private final AbortableOneWayDialogMessage receivedMessage;

        @SuppressWarnings("unused")
        public UserInitiatedAbortProtocolStep(SourceWaitingForSessionNumberState startState, AbortableOneWayDialogMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.receivedMessage = receivedMessage;
        }

        @SuppressWarnings("unused")
        public UserInitiatedAbortProtocolStep(SourceWaitingForTargetConnectionState startState, AbortableOneWayDialogMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.receivedMessage = receivedMessage;
        }

        @SuppressWarnings("unused")
        public UserInitiatedAbortProtocolStep(TargetWaitingForTransferredIdentityState startState, AbortableOneWayDialogMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.receivedMessage = receivedMessage;
        }

        @SuppressWarnings("unused")
        public UserInitiatedAbortProtocolStep(SourceWaitingForTargetSeedState startState, AbortableOneWayDialogMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.receivedMessage = receivedMessage;
        }

        @SuppressWarnings("unused")
        public UserInitiatedAbortProtocolStep(TargetWaitingForDecommitmentState startState, AbortableOneWayDialogMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.receivedMessage = receivedMessage;
        }

        @SuppressWarnings("unused")
        public UserInitiatedAbortProtocolStep(TargetWaitingForSnapshotState startState, AbortableOneWayDialogMessage receivedMessage, OwnedIdentityTransferProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            return userInitiatedAbortProtocol(this, receivedMessage.dialogUuid);
        }
    }

    private static ConcreteProtocolState userInitiatedAbortProtocol(ProtocolStep protocolStep, UUID dialogUuid) throws Exception {
        {
            // remove any dialog
            CoreProtocolMessage coreProtocolMessage = protocolStep.buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(protocolStep.getOwnedIdentity(), DialogType.createDeleteDialog(), dialogUuid));
            ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
            protocolStep.getProtocolManagerSession().channelDelegate.post(protocolStep.getProtocolManagerSession().session, messageToSend, protocolStep.getPrng());
        }

        {
            // close the websocket connection
            CoreProtocolMessage coreProtocolMessage = protocolStep.buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(protocolStep.getOwnedIdentity(), new ServerQuery.TransferCloseQuery(true)));
            ChannelMessageToSend messageToSend = new CloseWebSocketMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolStep.getProtocolManagerSession().channelDelegate.post(protocolStep.getProtocolManagerSession().session, messageToSend, protocolStep.getPrng());
        }

        return new FinalState();
    }

    private static ConcreteProtocolState failProtocol(ProtocolStep protocolStep, UUID dialogUuid, int failReason) throws Exception {
        {
            // display fail dialog
            CoreProtocolMessage coreProtocolMessage = protocolStep.buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(protocolStep.getOwnedIdentity(), DialogType.createTransferDialog(new ObvTransferStep.Fail(failReason)), dialogUuid));
            ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
            protocolStep.getProtocolManagerSession().channelDelegate.post(protocolStep.getProtocolManagerSession().session, messageToSend, protocolStep.getPrng());
        }

        {
            // close the websocket connection
            CoreProtocolMessage coreProtocolMessage = protocolStep.buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(protocolStep.getOwnedIdentity(), new ServerQuery.TransferCloseQuery(true)));
            ChannelMessageToSend messageToSend = new CloseWebSocketMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolStep.getProtocolManagerSession().channelDelegate.post(protocolStep.getProtocolManagerSession().session, messageToSend, protocolStep.getPrng());
        }

        return new FinalState();
    }

    // endregion


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonResponseSource {
        public String awsConnectionId;
        public Long sessionNumber;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonResponse {
        public String otherConnectionId;
        public byte[] payload;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonKeycloakConfiguration {
        public String server;
        public String cid;
        public String secret;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonTransferProof {
        public String session_id;
        public String sas;
        public byte[] identity;
        public String keycloak_id;
    }

}
