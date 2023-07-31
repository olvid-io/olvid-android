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

    public static class Type {
        public static final int DEVICE_DISCOVERY_QUERY_ID = 0;
        public static final int PUT_USER_DATA_QUERY_ID = 1;
        public static final int GET_USER_DATA_QUERY_ID = 2;
        public static final int CHECK_KEYCLOAK_REVOCATION_QUERY_ID = 3;
        public static final int CREATE_GROUP_BLOB_QUERY_ID = 4;
        public static final int GET_GROUP_BLOB_QUERY_ID = 5;
        public static final int LOCK_GROUP_BLOB_QUERY_ID = 6;
        public static final int UPDATE_GROUP_BLOB_QUERY_ID = 7;
        public static final int PUT_GROUP_LOG_QUERY_ID = 8;
        public static final int DELETE_GROUP_BLOB_QUERY_ID = 9;
        public static final int GET_KEYCLOAK_DATA_QUERY_ID = 10;
        public static final int OWNED_DEVICE_DISCOVERY_QUERY_ID = 11;
        public static final int DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID = 12;
        public static final int DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID = 13;
        public static final int DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID = 14;
        public static final int REGISTER_API_KEY_QUERY_ID = 15;

        private final int id;
        private final String server;
        private final Identity identity;
        private final UID serverLabelOrDeviceUid;
        private final String dataUrl; // always a relative path
        private final AuthEncKey dataKey;
        private final String signedContactDetails; // this is a JWT
        private final Encoded encodedGroupAdminPublicKey;
        private final EncryptedBytes encryptedBlob;
        private final byte[] querySignature;
        private final byte[] nonce;
        private final boolean isCurrentDevice;

        public Type(int id, String server, Identity identity, UID serverLabelOrDeviceUid, String dataUrl, AuthEncKey dataKey, String signedContactDetails, Encoded encodedGroupAdminPublicKey, EncryptedBytes encryptedBlob, byte[] querySignature, byte[] nonce, boolean isCurrentDevice) {
            this.id = id;
            this.server = server;
            this.identity = identity;
            this.serverLabelOrDeviceUid = serverLabelOrDeviceUid;
            this.dataUrl = dataUrl;
            this.dataKey = dataKey;
            this.signedContactDetails = signedContactDetails;
            this.encodedGroupAdminPublicKey = encodedGroupAdminPublicKey;
            this.encryptedBlob = encryptedBlob;
            this.querySignature = querySignature;
            this.nonce = nonce;
            this.isCurrentDevice = isCurrentDevice;
        }

        public int getId() {
            return id;
        }

        public String getServer() {
            return server;
        }

        public Identity getIdentity() {
            return identity;
        }

        public UID getServerLabelOrDeviceUid() {
            return serverLabelOrDeviceUid;
        }

        public String getDataUrl() {
            return dataUrl;
        }

        public AuthEncKey getDataKey() {
            return dataKey;
        }

        public String getSignedContactDetails() {
            return signedContactDetails;
        }

        public Encoded getEncodedGroupAdminPublicKey() {
            return encodedGroupAdminPublicKey;
        }

        public EncryptedBytes getEncryptedBlob() {
            return encryptedBlob;
        }

        public byte[] getQuerySignature() {
            return querySignature;
        }

        public byte[] getNonce() {
            return nonce;
        }

        public boolean isCurrentDevice() {
            return isCurrentDevice;
        }



        public static Type createDeviceDiscoveryQuery(Identity identity) {
            return new Type(DEVICE_DISCOVERY_QUERY_ID, identity.getServer(), identity, null, null, null, null, null, null, null, null, false);
        }

        public static Type createPutUserDataQuery(Identity ownedIdentity, UID serverLabel, String dataUrl, AuthEncKey dataKey) {
            return new Type(PUT_USER_DATA_QUERY_ID, ownedIdentity.getServer(), ownedIdentity, serverLabel, dataUrl, dataKey, null, null, null, null, null, false);
        }

        public static Type createGetUserDataQuery(Identity contactIdentity, UID serverLabel) {
            return new Type(GET_USER_DATA_QUERY_ID, contactIdentity.getServer(), contactIdentity, serverLabel, null, null, null, null, null, null, null, false);
        }

        public static Type createCheckKeycloakRevocationServerQuery(String keycloakServerUrl, String signedContactDetails) {
            return new Type(CHECK_KEYCLOAK_REVOCATION_QUERY_ID, keycloakServerUrl, null, null, null, null, signedContactDetails, null, null, null, null, false);
        }

        public static Type createCreateGroupBlobQuery(GroupV2.Identifier groupIdentifier, Encoded encodedGroupAdminPublicKey, EncryptedBytes encryptedBlob) {
            return new Type(CREATE_GROUP_BLOB_QUERY_ID, groupIdentifier.serverUrl, null, groupIdentifier.groupUid, null, null, null, encodedGroupAdminPublicKey, encryptedBlob, null, null, false);
        }

        public static Type createGetGroupBlobQuery(GroupV2.Identifier groupIdentifier, byte[] serverQueryNonce) {
            return new Type(GET_GROUP_BLOB_QUERY_ID, groupIdentifier.serverUrl, null, groupIdentifier.groupUid, null, null, null, null, null, null, serverQueryNonce, false);
        }

        public static Type createBlobLockQuery(GroupV2.Identifier groupIdentifier, byte[] lockNonce, byte[] querySignature) {
            return new Type(LOCK_GROUP_BLOB_QUERY_ID, groupIdentifier.serverUrl, null, groupIdentifier.groupUid, null, null, null, null, null, querySignature, lockNonce, false);
        }

        public static Type createUpdateGroupBlobQuery(GroupV2.Identifier groupIdentifier, byte[] lockNonce, EncryptedBytes encryptedBlob, Encoded encodedGroupAdminPublicKey, byte[] signature) {
            return new Type(UPDATE_GROUP_BLOB_QUERY_ID, groupIdentifier.serverUrl, null, groupIdentifier.groupUid, null, null, null, encodedGroupAdminPublicKey, encryptedBlob, signature, lockNonce, false);
        }

        public static Type createPutGroupLogQuery(GroupV2.Identifier groupIdentifier, byte[] querySignature) {
            return new Type(PUT_GROUP_LOG_QUERY_ID, groupIdentifier.serverUrl, null, groupIdentifier.groupUid, null, null, null, null, null, querySignature, null, false);
        }

        public static Type createDeleteGroupBlobQuery(GroupV2.Identifier groupIdentifier, byte[] querySignature) {
            return new Type(DELETE_GROUP_BLOB_QUERY_ID, groupIdentifier.serverUrl, null, groupIdentifier.groupUid, null, null, null, null, null, querySignature, null, false);
        }

        public static Type createGetKeycloakDataQuery(String serverUrl, UID serverLabel) {
            return new Type(GET_KEYCLOAK_DATA_QUERY_ID, serverUrl, null, serverLabel, null, null, null, null, null, null, null, false);
        }

        public static Type createOwnedDeviceDiscoveryQuery(Identity identity) {
            return new Type(OWNED_DEVICE_DISCOVERY_QUERY_ID, identity.getServer(), identity, null, null, null, null, null, null, null, null, false);
        }

        public static Type createDeviceManagementSetNicknameQuery(Identity identity, UID deviceUid, EncryptedBytes encryptedDeviceName, boolean isCurrentDevice) {
            return new Type(DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID, identity.getServer(), identity, deviceUid, null, null, null, null, encryptedDeviceName, null, null, isCurrentDevice);
        }

        public static Type createDeviceManagementDeactivateDeviceQuery(Identity identity, UID deviceUid) {
            return new Type(DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID, identity.getServer(), identity, deviceUid, null, null, null, null, null, null, null, false);
        }

        public static Type createDeviceManagementSetUnexpiringDeviceQuery(Identity identity, UID deviceUid) {
            return new Type(DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID, identity.getServer(), identity, deviceUid, null, null, null, null, null, null, null, false);
        }

        public static Type createRegisterApiKey(Identity ownedIdentity, byte[] serverSessionToken, String apiKeyString) {
            return new Type(REGISTER_API_KEY_QUERY_ID, ownedIdentity.getServer(), null, null, apiKeyString, null, null, null, null, null, serverSessionToken, false);
        }

        public static Type of(Encoded encoded) throws DecodingException {
            Encoded[] list = encoded.decodeList();
            if (list.length != 3) {
                throw new DecodingException();
            }
            int id = (int) list[0].decodeLong();
            String server = list[1].decodeString();
            Identity identity = null;
            UID serverLabelOrDeviceUid = null;
            String dataUrl = null;
            AuthEncKey dataKey = null;
            String signedContactDetails = null;
            Encoded encodedGroupAdminPublicKey = null;
            EncryptedBytes encryptedBlob = null;
            byte[] querySignature = null;
            byte[] nonce = null;
            boolean isCurrentDevice = false;

            Encoded[] vars = list[2].decodeList();
            switch (id) {
                case DEVICE_DISCOVERY_QUERY_ID:
                case OWNED_DEVICE_DISCOVERY_QUERY_ID: {
                    if (vars.length != 1) {
                        throw new DecodingException();
                    }
                    identity = vars[0].decodeIdentity();
                    break;
                }
                case PUT_USER_DATA_QUERY_ID: {
                    if (vars.length != 4) {
                        throw new DecodingException();
                    }
                    identity = vars[0].decodeIdentity();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    dataUrl = vars[2].decodeString();
                    dataKey = (AuthEncKey) vars[3].decodeSymmetricKey();
                    break;
                }
                case GET_USER_DATA_QUERY_ID: {
                    if (vars.length != 2) {
                        throw new DecodingException();
                    }
                    identity = vars[0].decodeIdentity();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    break;
                }
                case CHECK_KEYCLOAK_REVOCATION_QUERY_ID: {
                    if (vars.length != 2) {
                        throw new DecodingException();
                    }
                    server = vars[0].decodeString();
                    signedContactDetails = vars[1].decodeString();
                    break;
                }
                case CREATE_GROUP_BLOB_QUERY_ID: {
                    if (vars.length != 4) {
                        throw new DecodingException();
                    }
                    server = vars[0].decodeString();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    encodedGroupAdminPublicKey = vars[2];
                    encryptedBlob = vars[3].decodeEncryptedData();
                    break;
                }
                case GET_GROUP_BLOB_QUERY_ID: {
                    if (vars.length != 3) {
                        throw new DecodingException();
                    }
                    server = vars[0].decodeString();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    nonce = vars[2].decodeBytes();
                    break;
                }
                case LOCK_GROUP_BLOB_QUERY_ID: {
                    if (vars.length != 4) {
                        throw new DecodingException();
                    }
                    server = vars[0].decodeString();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    querySignature = vars[2].decodeBytes();
                    nonce = vars[3].decodeBytes();
                    break;
                }
                case UPDATE_GROUP_BLOB_QUERY_ID: {
                    if (vars.length != 6) {
                        throw new DecodingException();
                    }
                    server = vars[0].decodeString();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    encodedGroupAdminPublicKey = vars[2];
                    encryptedBlob = vars[3].decodeEncryptedData();
                    querySignature = vars[4].decodeBytes();
                    nonce = vars[5].decodeBytes();
                    break;
                }
                case DELETE_GROUP_BLOB_QUERY_ID:
                case PUT_GROUP_LOG_QUERY_ID: {
                    if (vars.length != 3) {
                        throw new DecodingException();
                    }
                    server = vars[0].decodeString();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    querySignature = vars[2].decodeBytes();
                    break;
                }
                case GET_KEYCLOAK_DATA_QUERY_ID: {
                    if (vars.length != 2) {
                        throw new DecodingException();
                    }
                    server = vars[0].decodeString();
                    serverLabelOrDeviceUid = vars[1].decodeUid();
                    break;
                }
                case DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID:
                case DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID: {
                    if (vars.length != 1) {
                        throw new DecodingException();
                    }
                    serverLabelOrDeviceUid = vars[0].decodeUid();
                    break;
                }
                case DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID: {
                    if (vars.length != 3) {
                        throw new DecodingException();
                    }
                    serverLabelOrDeviceUid = vars[0].decodeUid();
                    encryptedBlob = vars[1].decodeEncryptedData();
                    isCurrentDevice = vars[2].decodeBoolean();
                    break;
                }
                case REGISTER_API_KEY_QUERY_ID: {
                    if (vars.length != 2) {
                        throw new DecodingException();
                    }
                    dataUrl = vars[0].decodeString();
                    nonce = vars[1].decodeBytes();
                    break;
                }
            }
            return new Type(id, server, identity, serverLabelOrDeviceUid, dataUrl, dataKey, signedContactDetails, encodedGroupAdminPublicKey, encryptedBlob, querySignature, nonce, isCurrentDevice);
        }



        public Encoded encode() {
            Encoded encodedVars = null;
            switch (id) {
                case DEVICE_DISCOVERY_QUERY_ID:
                case OWNED_DEVICE_DISCOVERY_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(identity)
                    });
                    break;
                }
                case PUT_USER_DATA_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(identity),
                            Encoded.of(serverLabelOrDeviceUid),
                            Encoded.of(dataUrl),
                            Encoded.of(dataKey),
                    });
                    break;
                }
                case GET_USER_DATA_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(identity),
                            Encoded.of(serverLabelOrDeviceUid),
                    });
                    break;
                }
                case CHECK_KEYCLOAK_REVOCATION_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(server),
                            Encoded.of(signedContactDetails),
                    });
                    break;
                }
                case CREATE_GROUP_BLOB_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(server),
                            Encoded.of(serverLabelOrDeviceUid),
                            encodedGroupAdminPublicKey,
                            Encoded.of(encryptedBlob),
                    });
                    break;
                }
                case GET_GROUP_BLOB_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(server),
                            Encoded.of(serverLabelOrDeviceUid),
                            Encoded.of(nonce),
                    });
                    break;
                }
                case LOCK_GROUP_BLOB_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(server),
                            Encoded.of(serverLabelOrDeviceUid),
                            Encoded.of(querySignature),
                            Encoded.of(nonce),
                    });
                    break;
                }
                case UPDATE_GROUP_BLOB_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(server),
                            Encoded.of(serverLabelOrDeviceUid),
                            encodedGroupAdminPublicKey,
                            Encoded.of(encryptedBlob),
                            Encoded.of(querySignature),
                            Encoded.of(nonce),
                    });
                    break;
                }
                case DELETE_GROUP_BLOB_QUERY_ID:
                case PUT_GROUP_LOG_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(server),
                            Encoded.of(serverLabelOrDeviceUid),
                            Encoded.of(querySignature),
                    });
                    break;
                }
                case GET_KEYCLOAK_DATA_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(server),
                            Encoded.of(serverLabelOrDeviceUid),
                    });
                    break;
                }
                case DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID:
                case DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(serverLabelOrDeviceUid),
                    });
                    break;
                }
                case DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(serverLabelOrDeviceUid),
                            Encoded.of(encryptedBlob),
                            Encoded.of(isCurrentDevice),
                    });
                    break;
                }
                case REGISTER_API_KEY_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(dataUrl),
                            Encoded.of(nonce),
                    });
                    break;
                }
            }
            return Encoded.of(new Encoded[]{
                    Encoded.of(id),
                    Encoded.of(server),
                    encodedVars
            });
        }
    }
}
