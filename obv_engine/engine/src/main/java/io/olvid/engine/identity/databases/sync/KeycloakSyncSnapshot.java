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

package io.olvid.engine.identity.databases.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.KeycloakServer;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String SERVER_URL = "server_url";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String KEYCLOAK_USER_ID = "keycloak_user_id";
    public static final String JWKS = "jwks";
    public static final String SIGNATURE_KEY = "signature_key";
    public static final String SELF_REVOCATION_TEST_NONCE = "self_revocation_test_nonce";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(SERVER_URL, CLIENT_ID, CLIENT_SECRET, KEYCLOAK_USER_ID, JWKS, SIGNATURE_KEY, SELF_REVOCATION_TEST_NONCE));


    public String server_url;
    public String client_id;
    public String client_secret;
    public String keycloak_user_id;
    public String jwks;
    public String signature_key;
    public String self_revocation_test_nonce;
    public HashSet<String> domain;


    public static KeycloakSyncSnapshot of(IdentityManagerSession identityManagerSession, KeycloakServer keycloakServer) throws SQLException {
        KeycloakSyncSnapshot keycloakSyncSnapshot = new KeycloakSyncSnapshot();
        keycloakSyncSnapshot.server_url = keycloakServer.getServerUrl();
        keycloakSyncSnapshot.client_id = keycloakServer.getClientId();
        keycloakSyncSnapshot.client_secret = keycloakServer.getClientSecret();
        keycloakSyncSnapshot.keycloak_user_id = keycloakServer.getKeycloakUserId();
        keycloakSyncSnapshot.jwks = keycloakServer.getSerializedJwks();
        keycloakSyncSnapshot.signature_key = keycloakServer.getSerializedSignatureKey();
        keycloakSyncSnapshot.self_revocation_test_nonce = keycloakServer.getSelfRevocationTestNonce();
        keycloakSyncSnapshot.domain = DEFAULT_DOMAIN;
        return keycloakSyncSnapshot;
    }

    @JsonIgnore
    public KeycloakServer restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, KeycloakSyncSnapshot keycloak) throws Exception {
        if (!domain.contains(SERVER_URL) || !domain.contains(CLIENT_ID) || !domain.contains(KEYCLOAK_USER_ID) || !domain.contains(JWKS)) {
            Logger.e("Trying to restore an incomplete KeycloakSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }
        if (keycloak.server_url == null || keycloak.client_id == null || keycloak.keycloak_user_id == null || keycloak.jwks == null) {
            return null;
        }

        try {
            KeycloakServer keycloakServer = new KeycloakServer(identityManagerSession, server_url, ownedIdentity, jwks, domain.contains(SIGNATURE_KEY) ? signature_key : null, client_id, client_secret);
            keycloakServer.insert();
            keycloakServer.setKeycloakUserId(keycloak_user_id);
            keycloakServer.setSelfRevocationTestNonce(self_revocation_test_nonce);

            return keycloakServer;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof KeycloakSyncSnapshot)) {
            return false;
        }

        KeycloakSyncSnapshot other = (KeycloakSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case SERVER_URL: {
                    if (!Objects.equals(server_url, other.server_url)) {
                        return false;
                    }
                    break;
                }
                case CLIENT_ID: {
                    if (!Objects.equals(client_id, other.client_id)) {
                        return false;
                    }
                    break;
                }
                case CLIENT_SECRET: {
                    if (!Objects.equals(client_secret, other.client_secret)) {
                        return false;
                    }
                    break;
                }
                case KEYCLOAK_USER_ID: {
                    if (!Objects.equals(keycloak_user_id, other.keycloak_user_id)) {
                        return false;
                    }
                    break;
                }
                case JWKS: {
                    // TODO: deserialize for comparison
                    if (!Objects.equals(jwks, other.jwks)) {
                        return false;
                    }
                    break;
                }
                case SIGNATURE_KEY: {
                    // TODO: deserialize for comparison
                    if (!Objects.equals(signature_key, other.signature_key)) {
                        return false;
                    }
                    break;
                }
                case SELF_REVOCATION_TEST_NONCE: {
                    if (!Objects.equals(self_revocation_test_nonce, other.self_revocation_test_nonce)) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO computeDiff
        return null;
    }
}
