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

package io.olvid.engine.datatypes.containers;

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

        private final int id;
        private final String server;
        private final Identity identity;
        private final UID serverLabel;
        private final String dataUrl; // always a relative path
        private final AuthEncKey dataKey;
        private final String signedContactDetails; // this is a JWT


        public Type(int id, String server, Identity identity, UID serverLabel, String dataUrl, AuthEncKey dataKey, String signedContactDetails) {
            this.id = id;
            this.server = server;
            this.identity = identity;
            this.serverLabel = serverLabel;
            this.dataUrl = dataUrl;
            this.dataKey = dataKey;
            this.signedContactDetails = signedContactDetails;
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

        public UID getServerLabel() {
            return serverLabel;
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

        public static Type createDeviceDiscoveryQuery(Identity identity) {
            return new Type(DEVICE_DISCOVERY_QUERY_ID, identity.getServer(), identity, null, null, null, null);
        }

        public static Type createPutUserDataQuery(Identity ownedIdentity, UID serverLabel, String dataUrl, AuthEncKey dataKey) {
            return new Type(PUT_USER_DATA_QUERY_ID, ownedIdentity.getServer(), ownedIdentity, serverLabel, dataUrl, dataKey, null);
        }

        public static Type createGetUserDataQuery(Identity contactIdentity, UID serverLabel) {
            return new Type(GET_USER_DATA_QUERY_ID, contactIdentity.getServer(), contactIdentity, serverLabel, null, null, null);
        }

        public static Type createCheckKeycloakRevocationServerQuery(String keycloakServerUrl, String signedContactDetails) {
            return new Type(CHECK_KEYCLOAK_REVOCATION_QUERY_ID, keycloakServerUrl, null, null, null, null, signedContactDetails);
        }

        public static Type of(Encoded encoded) throws DecodingException {
            Encoded[] list = encoded.decodeList();
            if (list.length != 3) {
                throw new DecodingException();
            }
            int id = (int) list[0].decodeLong();
            String server = list[1].decodeString();
            Identity identity = null;
            UID serverLabel = null;
            String dataUrl = null;
            AuthEncKey dataKey = null;
            String signedContactDetails = null;

            Encoded[] vars = list[2].decodeList();
            switch (id) {
                case DEVICE_DISCOVERY_QUERY_ID: {
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
                    serverLabel = vars[1].decodeUid();
                    dataUrl = vars[2].decodeString();
                    dataKey = (AuthEncKey) vars[3].decodeSymmetricKey();
                    break;
                }
                case GET_USER_DATA_QUERY_ID: {
                    if (vars.length != 2) {
                        throw new DecodingException();
                    }
                    identity = vars[0].decodeIdentity();
                    serverLabel = vars[1].decodeUid();
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
            }
            return new Type(id, server, identity, serverLabel, dataUrl, dataKey, signedContactDetails);
        }

        public Encoded encode() {
            Encoded encodedVars = null;
            switch (id) {
                case DEVICE_DISCOVERY_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(identity)
                    });
                    break;
                }
                case PUT_USER_DATA_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(identity),
                            Encoded.of(serverLabel),
                            Encoded.of(dataUrl),
                            Encoded.of(dataKey),
                    });
                    break;
                }
                case GET_USER_DATA_QUERY_ID: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(identity),
                            Encoded.of(serverLabel),
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
            }
            return Encoded.of(new Encoded[]{
                    Encoded.of(id),
                    Encoded.of(server),
                    encodedVars
            });
        }
    }
}
