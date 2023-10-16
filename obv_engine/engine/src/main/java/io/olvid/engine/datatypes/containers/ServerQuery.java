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

package io.olvid.engine.datatypes.containers;

import java.util.HashMap;
import java.util.Map;

import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ServerQuery {
    private final Identity ownedIdentity;
    private final Type type;
    private final Encoded encodedElements;
    private Encoded encodedResponse;

    public ServerQuery(Encoded encodedElements, Identity ownedIdentity, Type type) {
        this.ownedIdentity = ownedIdentity;
        this.type = type;
        this.encodedElements = encodedElements;
        this.encodedResponse = null;
    }

    public boolean isWebSocket() {
        return type.isWebSocket();
    }

    public static ServerQuery of(Encoded encoded) throws DecodingException {
        Encoded[] list = encoded.decodeList();
        if (list.length != 3) {
            throw new DecodingException();
        }
        return new ServerQuery(
                list[0],
                list[1].decodeIdentity(),
                Type.of(list[2])
        );
    }

    public Encoded encode() {
        return Encoded.of(new Encoded[]{
                encodedElements,
                Encoded.of(ownedIdentity),
                type.encode()
        });
    }

    public void setResponse(Encoded encodedResponse) {
        this.encodedResponse = encodedResponse;
    }

    public Type getType() {
        return type;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }

    public Encoded getEncodedResponse() {
        return encodedResponse;
    }


    public enum TypeId {
        DEVICE_DISCOVERY_QUERY_ID(0),
        PUT_USER_DATA_QUERY_ID(1),
        GET_USER_DATA_QUERY_ID(2),
        CHECK_KEYCLOAK_REVOCATION_QUERY_ID(3),
        CREATE_GROUP_BLOB_QUERY_ID(4),
        GET_GROUP_BLOB_QUERY_ID(5),
        LOCK_GROUP_BLOB_QUERY_ID(6),
        UPDATE_GROUP_BLOB_QUERY_ID(7),
        PUT_GROUP_LOG_QUERY_ID(8),
        DELETE_GROUP_BLOB_QUERY_ID(9),
        GET_KEYCLOAK_DATA_QUERY_ID(10),
        OWNED_DEVICE_DISCOVERY_QUERY_ID(11),
        DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID(12),
        DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID(13),
        DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID(14),
        REGISTER_API_KEY_QUERY_ID(15),
        TRANSFER_SOURCE_QUERY_ID(1000),
        TRANSFER_TARGET_QUERY_ID(1001),
        TRANSFER_RELAY_QUERY_ID(1002),
        TRANSFER_WAIT_QUERY_ID(1003),
        TRANSFER_CLOSE_QUERY_ID(1004);

        private static final Map<Integer, TypeId> valueMap = new HashMap<>();
        static {
            for (TypeId step : values()) {
                valueMap.put(step.value, step);
            }
        }

        final int value;

        TypeId(int value) {
            this.value = value;
        }

        static TypeId fromIntValue(int value) {
            return valueMap.get(value);
        }
    }

    public static abstract class Type {
        public abstract TypeId getId();

        abstract String getServer();

        abstract Encoded[] getEncodedParts();

        abstract boolean isWebSocket();

        public static Type of(Encoded encoded) throws DecodingException {
            Encoded[] list = encoded.decodeList();
            if (list.length != 3) {
                throw new DecodingException();
            }
            int id = (int) list[0].decodeLong();
            String server = list[1].decodeString();
            Encoded[] encodedParts = list[2].decodeList();
            TypeId typeId = TypeId.fromIntValue(id);
            if (typeId == null) {
                throw new DecodingException();
            }
            switch (typeId) {
                case DEVICE_DISCOVERY_QUERY_ID:
                    return new DeviceDiscoveryQuery(server, encodedParts);
                case PUT_USER_DATA_QUERY_ID:
                    return new PutUserDataQuery(server, encodedParts);
                case GET_USER_DATA_QUERY_ID:
                    return new GetUserDataQuery(server, encodedParts);
                case CHECK_KEYCLOAK_REVOCATION_QUERY_ID:
                    return new CheckKeycloakRevocationQuery(server, encodedParts);
                case CREATE_GROUP_BLOB_QUERY_ID:
                    return new CreateGroupBlobQuery(server, encodedParts);
                case GET_GROUP_BLOB_QUERY_ID:
                    return new GetGroupBlobQuery(server, encodedParts);
                case LOCK_GROUP_BLOB_QUERY_ID:
                    return new LockGroupBlobQuery(server, encodedParts);
                case UPDATE_GROUP_BLOB_QUERY_ID:
                    return new UpdateGroupBlobQuery(server, encodedParts);
                case PUT_GROUP_LOG_QUERY_ID:
                    return new PutGroupLogQuery(server, encodedParts);
                case DELETE_GROUP_BLOB_QUERY_ID:
                    return new DeleteGroupBlobQuery(server, encodedParts);
                case GET_KEYCLOAK_DATA_QUERY_ID:
                    return new GetKeycloakDataQuery(server, encodedParts);
                case OWNED_DEVICE_DISCOVERY_QUERY_ID:
                    return new OwnedDeviceDiscoveryQuery(server, encodedParts);
                case DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID:
                    return new DeviceManagementSetNicknameQuery(server, encodedParts);
                case DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID:
                    return new DeviceManagementDeactivateDeviceQuery(server, encodedParts);
                case DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID:
                    return new DeviceManagementSetUnexpiringDeviceQuery(server, encodedParts);
                case REGISTER_API_KEY_QUERY_ID:
                    return new RegisterApiKeyQuery(server, encodedParts);
                case TRANSFER_SOURCE_QUERY_ID:
                    return new TransferSourceQuery(encodedParts);
                case TRANSFER_TARGET_QUERY_ID:
                    return new TransferTargetQuery(encodedParts);
                case TRANSFER_RELAY_QUERY_ID:
                    return new TransferRelayQuery(encodedParts);
                case TRANSFER_WAIT_QUERY_ID:
                    return new TransferWaitQuery(encodedParts);
                case TRANSFER_CLOSE_QUERY_ID:
                    return new TransferCloseQuery(encodedParts);
                default:
                    throw new DecodingException();
            }
        }

        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(getId().value),
                    Encoded.of(getServer()),
                    Encoded.of(getEncodedParts()),
            });
        }
    }

    public static class DeviceDiscoveryQuery extends Type {
        public final String server;
        public final Identity identity;

        public DeviceDiscoveryQuery(Identity identity) {
            this.server = identity.getServer();
            this.identity = identity;
        }

        public DeviceDiscoveryQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length != 1) {
                throw new DecodingException();
            }
            this.identity = encodedParts[0].decodeIdentity();
        }

        @Override
        public TypeId getId() {
            return TypeId.DEVICE_DISCOVERY_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(identity),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class PutUserDataQuery extends Type {
        public final String server;
        public final Identity ownedIdentity;
        public final UID serverLabel;
        public final String dataUrl; // always a relative path
        public final AuthEncKey dataKey;

        public PutUserDataQuery(Identity ownedIdentity, UID serverLabel, String dataUrl, AuthEncKey dataKey) {
            this.server = ownedIdentity.getServer();
            this.ownedIdentity = ownedIdentity;
            this.serverLabel = serverLabel;
            this.dataUrl = dataUrl;
            this.dataKey = dataKey;
        }

        public PutUserDataQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length != 4) {
                throw new DecodingException();
            }
            this.ownedIdentity = encodedParts[0].decodeIdentity();
            this.serverLabel = encodedParts[1].decodeUid();
            this.dataUrl = encodedParts[2].decodeString();
            this.dataKey = (AuthEncKey) encodedParts[3].decodeSymmetricKey();
        }

        @Override
        public TypeId getId() {
            return TypeId.PUT_USER_DATA_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(ownedIdentity),
                    Encoded.of(serverLabel),
                    Encoded.of(dataUrl),
                    Encoded.of(dataKey),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class GetUserDataQuery extends Type {
        public final String server;
        public final Identity identity;
        public final UID serverLabel;

        public GetUserDataQuery(Identity identity, UID serverLabel) {
            this.server = identity.getServer();
            this.identity = identity;
            this.serverLabel = serverLabel;
        }

        public GetUserDataQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length != 2) {
                throw new DecodingException();
            }
            this.identity = encodedParts[0].decodeIdentity();
            this.serverLabel = encodedParts[1].decodeUid();
        }

        @Override
        public TypeId getId() {
            return TypeId.GET_USER_DATA_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(identity),
                    Encoded.of(serverLabel),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class CheckKeycloakRevocationQuery extends Type {
        public final String server;
        public final String signedContactDetails; // this is a JWT

        public CheckKeycloakRevocationQuery(String keycloakServerUrl, String signedContactDetails) {
            this.server = keycloakServerUrl;
            this.signedContactDetails = signedContactDetails;
        }

        public CheckKeycloakRevocationQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 1) {
                this.signedContactDetails = encodedParts[0].decodeString();
            } else if (encodedParts.length == 2) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.signedContactDetails = encodedParts[1].decodeString();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.CHECK_KEYCLOAK_REVOCATION_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(signedContactDetails),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class CreateGroupBlobQuery extends Type {
        public final String server;
        public final UID groupUid;
        public final Encoded encodedGroupAdminPublicKey;
        public final EncryptedBytes encryptedBlob;

        public CreateGroupBlobQuery(GroupV2.Identifier groupIdentifier, Encoded encodedGroupAdminPublicKey, EncryptedBytes encryptedBlob) {
            this.server = groupIdentifier.serverUrl;
            this.groupUid = groupIdentifier.groupUid;
            this.encodedGroupAdminPublicKey = encodedGroupAdminPublicKey;
            this.encryptedBlob = encryptedBlob;
        }

        public CreateGroupBlobQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 3) {
                this.groupUid = encodedParts[0].decodeUid();
                this.encodedGroupAdminPublicKey = encodedParts[1];
                this.encryptedBlob = encodedParts[2].decodeEncryptedData();
            } else if (encodedParts.length == 4) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.groupUid = encodedParts[1].decodeUid();
                this.encodedGroupAdminPublicKey = encodedParts[2];
                this.encryptedBlob = encodedParts[3].decodeEncryptedData();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.CREATE_GROUP_BLOB_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(groupUid),
                    encodedGroupAdminPublicKey,
                    Encoded.of(encryptedBlob),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class GetGroupBlobQuery extends Type {
        public final String server;
        public final UID groupUid;
        public final byte[] serverQueryNonce;

        public GetGroupBlobQuery(GroupV2.Identifier groupIdentifier, byte[] serverQueryNonce) {
            this.server = groupIdentifier.serverUrl;
            this.groupUid = groupIdentifier.groupUid;
            this.serverQueryNonce = serverQueryNonce;
        }

        public GetGroupBlobQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 2) {
                this.groupUid = encodedParts[0].decodeUid();
                this.serverQueryNonce = encodedParts[1].decodeBytes();
            } else if (encodedParts.length == 3) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.groupUid = encodedParts[1].decodeUid();
                this.serverQueryNonce = encodedParts[2].decodeBytes();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.GET_GROUP_BLOB_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(groupUid),
                    Encoded.of(serverQueryNonce),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class LockGroupBlobQuery extends Type {
        public final String server;
        public final UID groupUid;
        public final byte[] signature;
        public final byte[] lockNonce;

        public LockGroupBlobQuery(GroupV2.Identifier groupIdentifier, byte[] lockNonce, byte[] signature) {
            this.server = groupIdentifier.serverUrl;
            this.groupUid = groupIdentifier.groupUid;
            this.signature = signature;
            this.lockNonce = lockNonce;
        }

        public LockGroupBlobQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 3) {
                this.groupUid = encodedParts[0].decodeUid();
                this.signature = encodedParts[1].decodeBytes();
                this.lockNonce = encodedParts[2].decodeBytes();
            } else if (encodedParts.length == 4) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.groupUid = encodedParts[1].decodeUid();
                this.signature = encodedParts[2].decodeBytes();
                this.lockNonce = encodedParts[3].decodeBytes();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.LOCK_GROUP_BLOB_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(groupUid),
                    Encoded.of(signature),
                    Encoded.of(lockNonce),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class UpdateGroupBlobQuery extends Type {
        public final String server;
        public final UID groupUid;
        public final Encoded encodedGroupAdminPublicKey;
        public final EncryptedBytes encryptedBlob;
        public final byte[] signature;
        public final byte[] lockNonce;

        public UpdateGroupBlobQuery(GroupV2.Identifier groupIdentifier, byte[] lockNonce, EncryptedBytes encryptedBlob, Encoded encodedGroupAdminPublicKey, byte[] signature) {
            this.server = groupIdentifier.serverUrl;
            this.groupUid = groupIdentifier.groupUid;
            this.encodedGroupAdminPublicKey = encodedGroupAdminPublicKey;
            this.encryptedBlob = encryptedBlob;
            this.signature = signature;
            this.lockNonce = lockNonce;
        }

        public UpdateGroupBlobQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 5) {
                this.groupUid = encodedParts[0].decodeUid();
                this.encodedGroupAdminPublicKey = encodedParts[1];
                this.encryptedBlob = encodedParts[2].decodeEncryptedData();
                this.signature = encodedParts[3].decodeBytes();
                this.lockNonce = encodedParts[4].decodeBytes();
            } else if (encodedParts.length == 6) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.groupUid = encodedParts[1].decodeUid();
                this.encodedGroupAdminPublicKey = encodedParts[2];
                this.encryptedBlob = encodedParts[3].decodeEncryptedData();
                this.signature = encodedParts[4].decodeBytes();
                this.lockNonce = encodedParts[5].decodeBytes();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.UPDATE_GROUP_BLOB_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(groupUid),
                    encodedGroupAdminPublicKey,
                    Encoded.of(encryptedBlob),
                    Encoded.of(signature),
                    Encoded.of(lockNonce),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class PutGroupLogQuery extends Type {
        public final String server;
        public final UID groupUid;
        public final byte[] signature;

        public PutGroupLogQuery(GroupV2.Identifier groupIdentifier, byte[] signature) {
            this.server = groupIdentifier.serverUrl;
            this.groupUid = groupIdentifier.groupUid;
            this.signature = signature;
        }

        public PutGroupLogQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 2) {
                this.groupUid = encodedParts[0].decodeUid();
                this.signature = encodedParts[1].decodeBytes();
            } else if (encodedParts.length == 3) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.groupUid = encodedParts[1].decodeUid();
                this.signature = encodedParts[2].decodeBytes();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.PUT_GROUP_LOG_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(groupUid),
                    Encoded.of(signature),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class DeleteGroupBlobQuery extends Type {
        public final String server;
        public final UID groupUid;
        public final byte[] signature;

        public DeleteGroupBlobQuery(GroupV2.Identifier groupIdentifier, byte[] signature) {
            this.server = groupIdentifier.serverUrl;
            this.groupUid = groupIdentifier.groupUid;
            this.signature = signature;
        }

        public DeleteGroupBlobQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 2) {
                this.groupUid = encodedParts[0].decodeUid();
                this.signature = encodedParts[1].decodeBytes();
            } else if (encodedParts.length == 3) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.groupUid = encodedParts[1].decodeUid();
                this.signature = encodedParts[2].decodeBytes();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.DELETE_GROUP_BLOB_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(groupUid),
                    Encoded.of(signature),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class GetKeycloakDataQuery extends Type {
        public final String server;
        public final UID serverLabel;

        public GetKeycloakDataQuery(String serverUrl, UID serverLabel) {
            this.server = serverUrl;
            this.serverLabel = serverLabel;
        }

        public GetKeycloakDataQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 1) {
                this.serverLabel = encodedParts[0].decodeUid();
            } else if (encodedParts.length == 2) {
                // legacy encoder
                // this.server = encodedParts[0].decodeString();
                this.serverLabel = encodedParts[1].decodeUid();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.GET_KEYCLOAK_DATA_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(serverLabel),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class OwnedDeviceDiscoveryQuery extends Type {
        public final String server;

        public OwnedDeviceDiscoveryQuery(Identity ownedIdentity) {
            this.server = ownedIdentity.getServer();
        }

        @SuppressWarnings("StatementWithEmptyBody")
        public OwnedDeviceDiscoveryQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length == 0) {
            } else if (encodedParts.length == 1) {
                // legacy encoder
                // this.ownedIdentity = encodedParts[0].decodeIdentity();
            } else {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.OWNED_DEVICE_DISCOVERY_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[0];
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class DeviceManagementSetNicknameQuery extends Type {
        public final String server;
        public final UID deviceUid;
        public final EncryptedBytes encryptedDeviceName;
        public final boolean isCurrentDevice;

        public DeviceManagementSetNicknameQuery(Identity ownedIdentity, UID deviceUid, EncryptedBytes encryptedDeviceName, boolean isCurrentDevice) {
            this.server = ownedIdentity.getServer();
            this.deviceUid = deviceUid;
            this.encryptedDeviceName = encryptedDeviceName;
            this.isCurrentDevice = isCurrentDevice;
        }

        public DeviceManagementSetNicknameQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length != 3) {
                throw new DecodingException();
            }
            this.deviceUid = encodedParts[0].decodeUid();
            this.encryptedDeviceName = encodedParts[1].decodeEncryptedData();
            this.isCurrentDevice = encodedParts[2].decodeBoolean();
        }

        @Override
        public TypeId getId() {
            return TypeId.DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(deviceUid),
                    Encoded.of(encryptedDeviceName),
                    Encoded.of(isCurrentDevice),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class DeviceManagementDeactivateDeviceQuery extends Type {
        public final String server;
        public final UID deviceUid;

        public DeviceManagementDeactivateDeviceQuery(Identity ownedIdentity, UID deviceUid) {
            this.server = ownedIdentity.getServer();
            this.deviceUid = deviceUid;
        }

        public DeviceManagementDeactivateDeviceQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length != 1) {
                throw new DecodingException();
            }
            this.deviceUid = encodedParts[0].decodeUid();
        }

        @Override
        public TypeId getId() {
            return TypeId.DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(deviceUid),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class DeviceManagementSetUnexpiringDeviceQuery extends Type {
        public final String server;
        public final UID deviceUid;

        public DeviceManagementSetUnexpiringDeviceQuery(Identity ownedIdentity, UID deviceUid) {
            this.server = ownedIdentity.getServer();
            this.deviceUid = deviceUid;
        }

        public DeviceManagementSetUnexpiringDeviceQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length != 1) {
                throw new DecodingException();
            }
            this.deviceUid = encodedParts[0].decodeUid();
        }

        @Override
        public TypeId getId() {
            return TypeId.DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(deviceUid),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class RegisterApiKeyQuery extends Type {
        public final String server;
        public final String apiKeyString;
        public final byte[] serverSessionToken;

        public RegisterApiKeyQuery(Identity ownedIdentity, byte[] serverSessionToken, String apiKeyString) {
            this.server = ownedIdentity.getServer();
            this.apiKeyString = apiKeyString;
            this.serverSessionToken = serverSessionToken;
        }

        public RegisterApiKeyQuery(String server, Encoded[] encodedParts) throws DecodingException {
            this.server = server;
            if (encodedParts.length != 2) {
                throw new DecodingException();
            }
            this.apiKeyString = encodedParts[0].decodeString();
            this.serverSessionToken = encodedParts[1].decodeBytes();
        }

        @Override
        public TypeId getId() {
            return TypeId.REGISTER_API_KEY_QUERY_ID;
        }

        @Override
        String getServer() {
            return server;
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(apiKeyString),
                    Encoded.of(serverSessionToken),
            };
        }

        @Override
        boolean isWebSocket() {
            return false;
        }
    }

    public static class TransferSourceQuery extends Type {
        public TransferSourceQuery() {
        }

        public TransferSourceQuery(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 0) {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.TRANSFER_SOURCE_QUERY_ID;
        }

        @Override
        String getServer() {
            return "";
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[0];
        }

        @Override
        boolean isWebSocket() {
            return true;
        }
    }

    public static class TransferTargetQuery extends Type {
        public final long sessionNumber;
        public final byte[] payload;

        public TransferTargetQuery(long sessionNumber, byte[] payload) {
            this.sessionNumber = sessionNumber;
            this.payload = payload;
        }

        public TransferTargetQuery(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 2) {
                throw new DecodingException();
            }
            this.sessionNumber = encodedParts[0].decodeLong();
            this.payload = encodedParts[1].decodeBytes();
        }

        @Override
        public TypeId getId() {
            return TypeId.TRANSFER_TARGET_QUERY_ID;
        }

        @Override
        String getServer() {
            return "";
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(sessionNumber),
                    Encoded.of(payload),
            };
        }

        @Override
        boolean isWebSocket() {
            return true;
        }
    }

    public static class TransferRelayQuery extends Type {
        public final String connectionIdentifier;
        public final byte[] payload;
        public final boolean noResponseExpected;

        public TransferRelayQuery(String connectionIdentifier, byte[] payload, boolean noResponseExpected) {
            this.connectionIdentifier = connectionIdentifier;
            this.payload = payload;
            this.noResponseExpected = noResponseExpected;
        }

        public TransferRelayQuery(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 3) {
                throw new DecodingException();
            }
            this.connectionIdentifier = encodedParts[0].decodeString();
            this.payload = encodedParts[1].decodeBytes();
            this.noResponseExpected = encodedParts[2].decodeBoolean();
        }

        @Override
        public TypeId getId() {
            return TypeId.TRANSFER_RELAY_QUERY_ID;
        }

        @Override
        String getServer() {
            return "";
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[] {
                    Encoded.of(connectionIdentifier),
                    Encoded.of(payload),
                    Encoded.of(noResponseExpected),
            };
        }

        @Override
        boolean isWebSocket() {
            return true;
        }
    }

    public static class TransferWaitQuery extends Type {
        public TransferWaitQuery() {
        }

        public TransferWaitQuery(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 0) {
                throw new DecodingException();
            }
        }

        @Override
        public TypeId getId() {
            return TypeId.TRANSFER_WAIT_QUERY_ID;
        }

        @Override
        String getServer() {
            return "";
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[0];
        }

        @Override
        boolean isWebSocket() {
            return true;
        }
    }

    public static class TransferCloseQuery extends Type {
        public final boolean abort;
        public TransferCloseQuery(boolean abort) {
            this.abort = abort;
        }

        public TransferCloseQuery(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 1) {
                throw new DecodingException();
            }
            this.abort = encodedParts[0].decodeBoolean();
        }

        @Override
        public TypeId getId() {
            return TypeId.TRANSFER_CLOSE_QUERY_ID;
        }

        @Override
        String getServer() {
            return "";
        }

        @Override
        Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(abort),
            };
        }

        @Override
        boolean isWebSocket() {
            return true;
        }
    }}
