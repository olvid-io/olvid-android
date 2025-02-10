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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

@SuppressWarnings("FieldMayBeFinal")
public class KeycloakRevokedIdentity implements ObvDatabase {
    static final String TABLE_NAME = "keycloak_revoked_identity";

    private final IdentityManagerSession identityManagerSession;

    private long rowId;
    static final String ROW_ID = "row_id";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private String keycloakServerUrl;
    static final String KEYCLOAK_SERVER_URL = "keycloak_server_url";
    private Identity revokedIdentity;
    static final String REVOKED_IDENTITY = "revoked_identity";
    private int revocationType;
    static final String REVOCATION_TYPE = "revocation_type";
    private long revocationTimestamp;
    static final String REVOCATION_TIMESTAMP = "revocation_timestamp";

    public static final int TYPE_COMPROMISED = 0;
    public static final int TYPE_LEFT_COMPANY = 1;

    public int getRevocationType() {
        return revocationType;
    }

    public long getRevocationTimestamp() {
        return revocationTimestamp;
    }

    public String getKeycloakServerUrl() {
        return keycloakServerUrl;
    }

    // region constructors

    public static KeycloakRevokedIdentity create(IdentityManagerSession identityManagerSession, Identity ownedIdentity, String keycloakServerUrl, Identity revokedIdentity, int revocationType, long revocationTimestamp) {
        if (ownedIdentity == null || keycloakServerUrl == null || revokedIdentity == null) {
            return null;
        }
        try {
            KeycloakRevokedIdentity keycloakRevokedIdentity = new KeycloakRevokedIdentity(identityManagerSession, ownedIdentity, keycloakServerUrl, revokedIdentity, revocationType, revocationTimestamp);
            keycloakRevokedIdentity.insert();
            return keycloakRevokedIdentity;
        } catch (SQLException e) {
            return null;
        }
    }

    public KeycloakRevokedIdentity(IdentityManagerSession identityManagerSession, Identity ownedIdentity, String keycloakServerUrl, Identity revokedIdentity, int revocationType, long revocationTimestamp) {
        this.identityManagerSession = identityManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.keycloakServerUrl = keycloakServerUrl;
        this.revokedIdentity = revokedIdentity;
        this.revocationType = revocationType;
        this.revocationTimestamp = revocationTimestamp;
    }

    public KeycloakRevokedIdentity(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.rowId = res.getLong(ROW_ID);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.keycloakServerUrl = res.getString(KEYCLOAK_SERVER_URL);
        try {
            this.revokedIdentity = Identity.of(res.getBytes(REVOKED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.revocationType = res.getInt(REVOCATION_TYPE);
        this.revocationTimestamp = res.getLong(REVOCATION_TIMESTAMP);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    KEYCLOAK_SERVER_URL + " TEXT NOT NULL, " +
                    REVOKED_IDENTITY + " BLOB NOT NULL, " +
                    REVOCATION_TYPE + " INT NOT NULL, " +
                    REVOCATION_TIMESTAMP + " BIGINT NOT NULL, " +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ", " + KEYCLOAK_SERVER_URL + ") REFERENCES " + KeycloakServer.TABLE_NAME + "(" + KeycloakServer.OWNED_IDENTITY + ", " + KeycloakServer.SERVER_URL + ") ON DELETE CASCADE);");
            statement.execute("CREATE INDEX IF NOT EXISTS `index_" + TABLE_NAME + "_" + REVOKED_IDENTITY + "` ON " + TABLE_NAME + " (" + REVOKED_IDENTITY + ")");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 25 && newVersion >= 25) {
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE keycloak_revoked_identity (" +
                        " row_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        " owned_identity BLOB NOT NULL, " +
                        " keycloak_server_url TEXT NOT NULL, " +
                        " revoked_identity BLOB NOT NULL, " +
                        " revocation_type INT NOT NULL, " +
                        " revocation_timestamp BIGINT NOT NULL, " +
                        " FOREIGN KEY (owned_identity, keycloak_server_url) REFERENCES keycloak_server (owned_identity, server_url) ON DELETE CASCADE);");
                statement.execute("CREATE INDEX `index_keycloak_revoked_identity_revoked_identity` ON keycloak_revoked_identity (revoked_identity)");
            }
            oldVersion = 25;
        }
    }


    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + "(" +
                OWNED_IDENTITY + ", " +
                KEYCLOAK_SERVER_URL + ", " +
                REVOKED_IDENTITY + ", " +
                REVOCATION_TYPE + ", " +
                REVOCATION_TIMESTAMP + ") " +
                " VALUES (?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS)) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setString(2, keycloakServerUrl);
            statement.setBytes(3, revokedIdentity.getBytes());
            statement.setInt(4, revocationType);
            statement.setLong(5, revocationTimestamp);
            statement.executeUpdate();
            try (ResultSet res = statement.getGeneratedKeys()) {
                if (res.next()) {
                    this.rowId = res.getLong(1);
                }
            }
        }
    }


    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + ROW_ID + " = ?;")) {
            statement.setLong(1, rowId);
            statement.executeUpdate();
        }
    }

    // endregion

    public static List<KeycloakRevokedIdentity> get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity identityToVerify) throws SQLException {
        if ((ownedIdentity == null) || (identityToVerify == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT kr.* FROM " + TABLE_NAME + " AS kr " +
                " INNER JOIN " + OwnedIdentity.TABLE_NAME + " AS oi " +
                " ON kr." + OWNED_IDENTITY + " = oi." + OwnedIdentity.OWNED_IDENTITY +
                " AND kr." + KEYCLOAK_SERVER_URL + " = oi." + OwnedIdentity.KEYCLOAK_SERVER_URL +
                " WHERE oi." + OwnedIdentity.OWNED_IDENTITY + " = ? " +
                " AND kr." + REVOKED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, identityToVerify.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<KeycloakRevokedIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new KeycloakRevokedIdentity(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public static void prune(IdentityManagerSession identityManagerSession, Identity ownedIdentity, String keycloakServerUrl, long timestamp) throws SQLException {
        if ((ownedIdentity == null) || (keycloakServerUrl == null)) {
            return;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + KEYCLOAK_SERVER_URL + " = ? " +
                " AND " + REVOCATION_TIMESTAMP + " < ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setString(2, keycloakServerUrl);
            statement.setLong(3, timestamp);
            statement.executeUpdate();
        }
    }

    // region hooks

    @Override
    public void wasCommitted() {
        // no hooks around here
    }

    // endregion
}
