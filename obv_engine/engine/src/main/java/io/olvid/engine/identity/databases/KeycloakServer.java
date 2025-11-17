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

package io.olvid.engine.identity.databases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class KeycloakServer implements ObvDatabase {
    static final String TABLE_NAME = "keycloak_server";

    private final IdentityManagerSession identityManagerSession;

    private String serverUrl;
    static final String SERVER_URL = "server_url";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private String serializedJwks;
    static final String SERIALIZED_JWKS = "serialized_jwks";
    private String clientId;  // non null only for the keycloak server of a managed identity
    static final String CLIENT_ID = "client_id";
    private String clientSecret;  // non null only for the keycloak server of a managed identity
    static final String CLIENT_SECRET = "client_secret";
    private String keycloakUserId; // non null only for the keycloak server of a managed identity
    static final String KEYCLOAK_USER_ID = "keycloak_user_id";
    private String serializedAuthState;  // non null only for the keycloak server of a managed identity
    static final String SERIALIZED_AUTH_STATE = "serialized_auth_state";
    private byte[] serializedPushTopics;  // non null only for the keycloak server of a managed identity. Contains a serialized array of String, separated by 0 byte (null or empty are equivalent)
    static final String SERIALIZED_PUSH_TOPICS = "serialized_push_topics";
    private String serializedSignatureKey;  // the key (serialized JsonWebKey) used to sign the user's details which should not change
    static final String SERIALIZED_SIGNATURE_KEY = "serialized_signature_key";
    private String selfRevocationTestNonce; // a secret nonce given to the user when they upload their key, to check whether they were revoked
    static final String SELF_REVOCATION_TEST_NONCE = "self_revocation_test_nonce";
    private long latestRevocationListTimestamp; // the last time a revocation list was retrieved from the keycloak server
    static final String LATEST_REVOCATION_LIST_TIMESTAMP = "latest_revocation_list_timestamp";
    private long latestGroupUpdateTimestamp; // the last time groups wre retrieved from the keycloak server
    static final String LATEST_GROUP_UPDATE_TIMESTAMP = "latest_group_update_timestamp";
    private String ownApiKey; // the api key given to us by keycloak, non null only for the keycloak server of a managed identity
    static final String OWN_API_KEY = "own_api_key";
    private boolean transferRestricted; // true if transfer requires a re-authentication, may only be true for the keycloak server of a managed identity
    static final String TRANSFER_RESTRICTED = "transfer_restricted";

    public String getServerUrl() {
        return serverUrl;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public String getSerializedAuthState() {
        return serializedAuthState;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public JsonWebKeySet getJwks() throws Exception {
        return new JsonWebKeySet(serializedJwks);
    }

    public String getSerializedJwks() {
        return serializedJwks;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public JsonWebKey getSignatureKey() throws Exception {
        if (serializedSignatureKey == null) {
            return null;
        }
        return JsonWebKey.Factory.newJwk(serializedSignatureKey);
    }

    public String getSerializedSignatureKey() {
        return serializedSignatureKey;
    }

    public String getSelfRevocationTestNonce() {
        return selfRevocationTestNonce;
    }

    public String getOwnApiKey() {
        return ownApiKey;
    }

    public boolean isTransferRestricted() {
        return transferRestricted;
    }

    public List<String> getPushTopics() {
        if (serializedPushTopics == null) {
            return new ArrayList<>(0);
        }

        List<String> res = new ArrayList<>();
        int startPos = 0;
        for (int i=0; i<serializedPushTopics.length; i++) {
            if (serializedPushTopics[i] == 0) {
                res.add(new String(Arrays.copyOfRange(serializedPushTopics, startPos, i), StandardCharsets.UTF_8));
                startPos = i+1;
            }
        }
        if (startPos != serializedPushTopics.length) {
            res.add(new String(Arrays.copyOfRange(serializedPushTopics, startPos, serializedPushTopics.length), StandardCharsets.UTF_8));
        }
        return res;
    }

    public long getLatestRevocationListTimestamp() {
        return latestRevocationListTimestamp;
    }

    public long getLatestGroupUpdateTimestamp() {
        return latestGroupUpdateTimestamp;
    }

    // region constructors

    public static KeycloakServer create(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, String serializedJwks, String serializedKey, String clientId, String clientSecret, boolean transferRestricted) {
        if (serverUrl == null || ownedIdentity == null || serializedJwks == null) {
            return null;
        }
        try {
            KeycloakServer keycloakServer = new KeycloakServer(identityManagerSession, serverUrl, ownedIdentity, serializedJwks, serializedKey, clientId, clientSecret, transferRestricted);
            keycloakServer.insert();
            return keycloakServer;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }



    public KeycloakServer(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, String serializedJwks, String serializedSignatureKey, String clientId, String clientSecret, boolean transferRestricted) {
        this.identityManagerSession = identityManagerSession;
        this.serverUrl = serverUrl;
        this.ownedIdentity = ownedIdentity;
        this.serializedJwks = serializedJwks;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.keycloakUserId = null;
        this.serializedAuthState = null;
        this.serializedPushTopics = null;
        this.serializedSignatureKey = serializedSignatureKey;
        this.selfRevocationTestNonce = null;
        this.latestRevocationListTimestamp = 0;
        this.latestGroupUpdateTimestamp = 0;
        this.ownApiKey = null;
        this.transferRestricted = transferRestricted;
    }

    private KeycloakServer(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.serverUrl = res.getString(SERVER_URL);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.serializedJwks = res.getString(SERIALIZED_JWKS);
        this.clientId = res.getString(CLIENT_ID);
        this.clientSecret = res.getString(CLIENT_SECRET);
        this.keycloakUserId = res.getString(KEYCLOAK_USER_ID);
        this.serializedAuthState = res.getString(SERIALIZED_AUTH_STATE);
        this.serializedPushTopics = res.getBytes(SERIALIZED_PUSH_TOPICS);
        this.serializedSignatureKey = res.getString(SERIALIZED_SIGNATURE_KEY);
        this.selfRevocationTestNonce = res.getString(SELF_REVOCATION_TEST_NONCE);
        this.latestRevocationListTimestamp = res.getLong(LATEST_REVOCATION_LIST_TIMESTAMP);
        this.latestGroupUpdateTimestamp = res.getLong(LATEST_GROUP_UPDATE_TIMESTAMP);
        this.ownApiKey = res.getString(OWN_API_KEY);
        this.transferRestricted = res.getBoolean(TRANSFER_RESTRICTED);
    }

    // endregion


    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    SERVER_URL + " TEXT NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    SERIALIZED_JWKS + " TEXT NOT NULL, " +
                    CLIENT_ID + " TEXT, " +
                    CLIENT_SECRET + " TEXT, " +
                    KEYCLOAK_USER_ID + " TEXT, " +
                    SERIALIZED_AUTH_STATE + " TEXT, " +
                    SERIALIZED_PUSH_TOPICS + " BLOB, " +
                    SERIALIZED_SIGNATURE_KEY + " TEXT, " +
                    SELF_REVOCATION_TEST_NONCE + " TEXT, " +
                    LATEST_REVOCATION_LIST_TIMESTAMP + " BIGINT NOT NULL, " +
                    LATEST_GROUP_UPDATE_TIMESTAMP + " BIGINT NOT NULL, " +
                    OWN_API_KEY + " TEXT, " +
                    TRANSFER_RESTRICTED + " BIT NOT NULL, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + SERVER_URL + ", " + OWNED_IDENTITY + "), " +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ") REFERENCES " + OwnedIdentity.TABLE_NAME + " (" + OwnedIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 24 && newVersion >= 24) {
            Logger.d("MIGRATING `keycloak_server` DATABASE FROM VERSION " + oldVersion + " TO 24");
            try (Statement statement = session.createStatement()) {
                // migration anomaly
                // we forgot to add the create table statement in the v20 migration, we add it here with an "if not exist"
                statement.execute("CREATE TABLE IF NOT EXISTS keycloak_server (" +
                        " server_url TEXT NOT NULL, " +
                        " owned_identity BLOB NOT NULL, " +
                        " serialized_jwks TEXT NOT NULL, " +
                        " client_id TEXT, " +
                        " client_secret TEXT, " +
                        " keycloak_user_id TEXT, " +
                        " serialized_auth_state TEXT, " +
                        " CONSTRAINT PK_keycloak_server PRIMARY KEY(server_url, owned_identity), " +
                        " FOREIGN KEY (owned_identity) REFERENCES owned_identity (identity) ON DELETE CASCADE);");
                // back to normal migration
                statement.execute("ALTER TABLE keycloak_server ADD COLUMN `serialized_push_topics` BLOB DEFAULT NULL;");
            }
            oldVersion = 24;
        }
        if (oldVersion < 25 && newVersion >= 25) {
            Logger.d("MIGRATING `keycloak_server` DATABASE FROM VERSION " + oldVersion + " TO 25");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE keycloak_server ADD COLUMN `serialized_signature_key` TEXT DEFAULT NULL;");
                statement.execute("ALTER TABLE keycloak_server ADD COLUMN `self_revocation_test_nonce` TEXT DEFAULT NULL;");
                statement.execute("ALTER TABLE keycloak_server ADD COLUMN `latest_revocation_list_timestamp` BIGINT NOT NULL DEFAULT 0;");
            }
            oldVersion = 25;
        }
        if (oldVersion < 34 && newVersion >= 34) {
            Logger.d("MIGRATING `keycloak_server` DATABASE FROM VERSION " + oldVersion + " TO 34");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE keycloak_server ADD COLUMN `latest_group_update_timestamp` BIGINT NOT NULL DEFAULT 0;");
            }
            oldVersion = 34;
        }
        if (oldVersion < 35 && newVersion >= 35) {
            Logger.d("MIGRATING `keycloak_server` DATABASE FROM VERSION " + oldVersion + " TO 35");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE keycloak_server ADD COLUMN `own_api_key` TEXT DEFAULT NULL;");
            }
            oldVersion = 35;
        }
        if (oldVersion < 42 && newVersion >= 42) {
            Logger.d("MIGRATING `keycloak_server` DATABASE FROM VERSION " + oldVersion + " TO 42");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE keycloak_server ADD COLUMN `transfer_restricted` BIT NOT NULL DEFAULT 0;");
            }
            oldVersion = 35;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?,?);")) {
            statement.setString(1, serverUrl);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setString(3, serializedJwks);
            statement.setString(4, clientId);
            statement.setString(5, clientSecret);

            statement.setString(6, keycloakUserId);
            statement.setString(7, serializedAuthState);
            statement.setBytes(8, serializedPushTopics);
            statement.setString(9, serializedSignatureKey);
            statement.setString(10, selfRevocationTestNonce);

            statement.setLong(11, latestRevocationListTimestamp);
            statement.setLong(12, latestGroupUpdateTimestamp);
            statement.setString(13, ownApiKey);
            statement.setBoolean(14, transferRestricted);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.delete",
                "DELETE FROM " + TABLE_NAME +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, serverUrl);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static KeycloakServer get(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.get",
                "SELECT * FROM " + TABLE_NAME +
                " WHERE " + SERVER_URL + " = ?" +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, serverUrl);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new KeycloakServer(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static List<KeycloakServer> getAllWithPushTopic(IdentityManagerSession identityManagerSession, String pushTopic) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.getAllWithPushTopic",
                "SELECT * FROM " + TABLE_NAME +
                " WHERE " + SERIALIZED_PUSH_TOPICS + " LIKE ?;")) {
            statement.setBytes(1, ("%" + pushTopic + "%").getBytes(StandardCharsets.UTF_8));
            try (ResultSet res = statement.executeQuery()) {
                List<KeycloakServer> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new KeycloakServer(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    // endregion

    // region setters

    public static void saveAuthState(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, String serializedAuthState) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.saveAuthState",
                "UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_AUTH_STATE + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, serializedAuthState);
            statement.setString(2, serverUrl);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static void saveJwks(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, String serializedJwks) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.saveJwks",
                "UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_JWKS + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, serializedJwks);
            statement.setString(2, serverUrl);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static void saveApiKey(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, String apiKey) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.saveApiKey",
                "UPDATE " + TABLE_NAME +
                " SET " + OWN_API_KEY + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, apiKey);
            statement.setString(2, serverUrl);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static void setKeycloakUserId(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, String userId) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setKeycloakUserId",
                "UPDATE " + TABLE_NAME +
                " SET " + KEYCLOAK_USER_ID + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, userId);
            statement.setString(2, serverUrl);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public void setKeycloakUserId(String userId) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setKeycloakUserId",
                "UPDATE " + TABLE_NAME +
                " SET " + KEYCLOAK_USER_ID + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, userId);
            statement.setString(2, this.serverUrl);
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.keycloakUserId = userId;
        }
    }

    public void setTransferRestricted(boolean transferRestricted) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setTransferRestricted",
                "UPDATE " + TABLE_NAME +
                " SET " + TRANSFER_RESTRICTED + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBoolean(1, transferRestricted);
            statement.setString(2, this.serverUrl);
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.transferRestricted = transferRestricted;
        }
    }

    public void setPushTopics(List<String> pushTopics) throws SQLException {
        byte[] serializedPushTopics;
        if (pushTopics == null || pushTopics.isEmpty()) {
            serializedPushTopics = null;
        } else {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                boolean first = true;
                for (String pushTopic : pushTopics) {
                    if (!first) {
                        baos.write(new byte[]{0});
                    }
                    first = false;
                    baos.write(pushTopic.getBytes(StandardCharsets.UTF_8));
                }
                serializedPushTopics = baos.toByteArray();
            } catch (IOException e) {
                serializedPushTopics = null;
            }
        }

        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setPushTopics",
                "UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_PUSH_TOPICS + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, serializedPushTopics);
            statement.setString(2, this.serverUrl);
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.serializedPushTopics = serializedPushTopics;
        }
    }

    public void setSelfRevocationTestNonce(String selfRevocationTestNonce) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setSelfRevocationTestNonce",
                "UPDATE " + TABLE_NAME +
                " SET " + SELF_REVOCATION_TEST_NONCE + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, selfRevocationTestNonce);
            statement.setString(2, this.serverUrl);
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.selfRevocationTestNonce = selfRevocationTestNonce;
        }
    }

    public static void setSignatureKey(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, JsonWebKey signatureKey) throws SQLException {
        // everytime we reset the signature key, we also reset the latestGroupUpdateTimestamp to re-download all groups
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setSignatureKey",
                "UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_SIGNATURE_KEY + " = ?, " +
                LATEST_GROUP_UPDATE_TIMESTAMP + " = 0 " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, signatureKey == null ? null : signatureKey.toJson());
            statement.setString(2, serverUrl);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static void setLatestRevocationListTimestamp(IdentityManagerSession identityManagerSession, String serverUrl, Identity ownedIdentity, long latestRevocationListTimetamp) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setLatestRevocationListTimestamp",
                "UPDATE " + TABLE_NAME +
                " SET " + LATEST_REVOCATION_LIST_TIMESTAMP + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setLong(1, latestRevocationListTimetamp);
            statement.setString(2, serverUrl);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public void setLatestGroupUpdateTimestamp(long latestGroupUpdateTimestamp) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("KeycloakServer.setLatestGroupUpdateTimestamp",
                "UPDATE " + TABLE_NAME +
                " SET " + LATEST_GROUP_UPDATE_TIMESTAMP + " = ? " +
                " WHERE " + SERVER_URL + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setLong(1, latestGroupUpdateTimestamp);
            statement.setString(2, serverUrl);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.latestRevocationListTimestamp = latestGroupUpdateTimestamp;
        }
    }


    // endregion

    // region hooks

    @Override
    public void wasCommitted() {
        // No notifications here
    }

    // endregion

    // region backup

    Pojo_0 backup() {
        Pojo_0 pojo = new Pojo_0();
        pojo.server_url = serverUrl;
        pojo.jwks = serializedJwks;

        pojo.client_id = clientId;
        pojo.client_secret = clientSecret;
        pojo.keycloak_user_id = keycloakUserId;

        pojo.serialized_signature_key = serializedSignatureKey;
        pojo.self_revocation_test_nonce = selfRevocationTestNonce;
        return pojo;
    }

    public static KeycloakServer restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Pojo_0 pojo) throws SQLException {
        if (ownedIdentity == null || pojo.server_url == null || pojo.client_id == null || pojo.jwks == null) {
            return null;
        }

        KeycloakServer keycloakServer = new KeycloakServer(identityManagerSession, pojo.server_url, ownedIdentity, pojo.jwks, pojo.serialized_signature_key, pojo.client_id, pojo.client_secret, false);
        keycloakServer.keycloakUserId = pojo.keycloak_user_id;
        keycloakServer.selfRevocationTestNonce = pojo.self_revocation_test_nonce;
        keycloakServer.insert();

        return keycloakServer;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public String server_url;
        public String jwks;
        public String client_id;
        public String client_secret;
        public String keycloak_user_id;
        public String serialized_signature_key;
        public String self_revocation_test_nonce;
    }

    // endregion
}
