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

package io.olvid.engine.networkfetch.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;


public class ServerSession implements ObvDatabase {
    static final String TABLE_NAME = "server_session";

    public enum Permission {
        CALL,
        WEB_CLIENT,
        MULTI_DEVICE,
    }

    public enum ApiKeyStatus {
        VALID,
        UNKNOWN,
        LICENSES_EXHAUSTED,
        EXPIRED,
        OPEN_BETA_KEY,
        FREE_TRIAL_KEY,
        AWAITING_PAYMENT_GRACE_PERIOD,
        AWAITING_PAYMENT_ON_HOLD,
        FREE_TRIAL_KEY_EXPIRED,
    }

    private final FetchManagerSession fetchManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "identity";
    private byte[] nonce;
    static final String NONCE = "nonce";
    private byte[] challenge;
    static final String CHALLENGE = "challenge";
    private byte[] response;
    static final String RESPONSE = "response";
    private byte[] token;
    static final String TOKEN = "token";
    private int apiKeyStatus;
    static final String API_KEY_STATUS = "api_key_status";
    private long permissions;
    static final String PERMISSIONS = "permissions";
    private long apiKeyExpirationTimestamp;
    static final String API_KEY_EXPIRATION_TIMESTAMP = "api_key_expiration_timestamp";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getChallenge() {
        return challenge;
    }

    public byte[] getResponse() {
        return response;
    }

    public byte[] getToken() {
        return token;
    }

    public ApiKeyStatus getApiKeyStatus() {
        return deserializeApiKeyStatus(apiKeyStatus);
    }

    public static ApiKeyStatus deserializeApiKeyStatus(int apiKeyStatus) {
        switch (apiKeyStatus) {
            case Constants.API_KEY_STATUS_VALID:
                return ApiKeyStatus.VALID;
            case Constants.API_KEY_STATUS_EXPIRED:
                return ApiKeyStatus.EXPIRED;
            case Constants.API_KEY_STATUS_LICENSES_EXHAUSTED:
                return ApiKeyStatus.LICENSES_EXHAUSTED;
            case Constants.API_KEY_STATUS_OPEN_BETA_KEY:
                return ApiKeyStatus.OPEN_BETA_KEY;
            case Constants.API_KEY_STATUS_FREE_TRIAL_KEY:
                return ApiKeyStatus.FREE_TRIAL_KEY;
            case Constants.API_KEY_STATUS_AWAITING_PAYMENT_GRACE_PERIOD:
                return ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD;
            case Constants.API_KEY_STATUS_AWAITING_PAYMENT_ON_HOLD:
                return ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD;
            case Constants.API_KEY_STATUS_FREE_TRIAL_KEY_EXPIRED:
                return ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED;
            case Constants.API_KEY_STATUS_UNKNOWN:
            default:
                return ApiKeyStatus.UNKNOWN;
        }
    }

    public List<Permission> getPermissions() {
        return deserializePermissions(permissions);
    }

    public static List<Permission> deserializePermissions(long permissions) {
        List<Permission> out = new ArrayList<>();
        if ((permissions & Constants.API_KEY_PERMISSION_CALL) != 0){
            out.add(Permission.CALL);
        }
        if ((permissions & Constants.API_KEY_PERMISSION_WEB_CLIENT) != 0){
            out.add(Permission.WEB_CLIENT);
        }
        if ((permissions & Constants.API_KEY_PERMISSION_MULTI_DEVICE) != 0){
            out.add(Permission.MULTI_DEVICE);
        }
        return out;
    }

    public long getApiKeyExpirationTimestamp() {
        return apiKeyExpirationTimestamp;
    }

    public void setChallengeAndNonce(byte[] challenge, byte[] nonce) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.setChallengeAndNonce",
                "UPDATE " + TABLE_NAME + " SET " + CHALLENGE + " = ?, " + NONCE + " = ? WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, challenge);
            statement.setBytes(2, nonce);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.challenge = challenge;
            this.nonce = nonce;
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    public void setResponseForChallenge(byte[] challenge, byte[] response) {
        if (response == null || challenge == null) {
            return;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.setResponseForChallenge",
                "UPDATE " + TABLE_NAME +
                " SET " + RESPONSE + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + CHALLENGE + " = ?;")) {
            statement.setBytes(1, response);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, challenge);
            statement.executeUpdate();
            if (Arrays.equals(this.challenge, challenge)) {
                this.response = response;
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    public void setTokenAndPermissions(byte[] token, int apiKeyStatus, long permissions, long apiKeyExpirationTimestamp) {
        if (token == null) {
            return;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.setTokenAndPermissions",
                "UPDATE " + TABLE_NAME +
                " SET " + TOKEN + " = ?, " +
                API_KEY_STATUS + " = ?, " +
                PERMISSIONS + " = ?, " +
                API_KEY_EXPIRATION_TIMESTAMP + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, token);
            statement.setInt(2, apiKeyStatus);
            statement.setLong(3, permissions);
            statement.setLong(4, apiKeyExpirationTimestamp);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.token = token;
            this.apiKeyStatus = apiKeyStatus;
            this.permissions = permissions;
            this.apiKeyExpirationTimestamp = apiKeyExpirationTimestamp;
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    public static void deleteCurrentTokenIfEqualTo(FetchManagerSession fetchManagerSession, byte[] token, Identity ownedIdentity) {
        if (ownedIdentity == null) {
            return;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.deleteCurrentTokenIfEqualTo",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ? AND " + TOKEN + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, token);
            statement.executeUpdate();
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    public static void deleteForIdentity(FetchManagerSession fetchManagerSession, Identity ownedIdentity) {
        if (ownedIdentity == null) {
            return;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.deleteForIdentity",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            Logger.x(e);
        }
    }


    public static ServerSession create(FetchManagerSession fetchManagerSession, Identity ownedIdentity) {
        if (ownedIdentity == null) {
            return null;
        }
        try {
            ServerSession serverSession = new ServerSession(fetchManagerSession, ownedIdentity);
            serverSession.insert();
            return serverSession;
        } catch (SQLException e) {
            Logger.w("SQLException during ServerSession insert.");
            return null;
        }
    }

    private ServerSession(FetchManagerSession fetchManagerSession, Identity ownedIdentity) {
        this.fetchManagerSession = fetchManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.nonce = null;
        this.challenge = null;
        this.response = null;
        this.token = null;
        this.apiKeyStatus = -1;
        this.permissions = 0;
        this.apiKeyExpirationTimestamp = 0;
    }

    private ServerSession(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            Logger.x(e);
        }
        this.nonce = res.getBytes(NONCE);
        this.challenge = res.getBytes(CHALLENGE);
        this.response = res.getBytes(RESPONSE);
        this.token = res.getBytes(TOKEN);

        this.apiKeyStatus = res.getInt(API_KEY_STATUS);
        this.permissions = res.getLong(PERMISSIONS);
        this.apiKeyExpirationTimestamp = res.getLong(API_KEY_EXPIRATION_TIMESTAMP);
    }


    public static byte[] getToken(FetchManagerSession fetchManagerSession, Identity ownedIdentity) {
        ServerSession serverSession = ServerSession.get(fetchManagerSession, ownedIdentity);
        return (serverSession==null)?null:serverSession.token;
    }

    public static ServerSession get(FetchManagerSession fetchManagerSession, Identity ownedIdentity) {
        if (ownedIdentity == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.get",
                "SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ServerSession(fetchManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    public static ServerSession[] getAll(FetchManagerSession fetchManagerSession) throws SQLException {
        try (Statement statement = fetchManagerSession.session.createStatement("ServerSession.getAll")) {
            try (ResultSet res = statement.executeQuery("SELECT * FROM " + TABLE_NAME)) {
                List<ServerSession> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ServerSession(fetchManagerSession, res));
                }
                return list.toArray(new ServerSession[0]);
            }
        }
    }


    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB PRIMARY KEY, " +
                    NONCE + " BLOB, " +
                    CHALLENGE + " BLOB, " +
                    RESPONSE + " BLOB, " +
                    TOKEN + " BLOB, " +
                    API_KEY_STATUS + " INT NOT NULL, " +
                    PERMISSIONS + " BIGINT NOT NULL, " +
                    API_KEY_EXPIRATION_TIMESTAMP + " BIGINT NOT NULL);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 18 && newVersion >= 18) {
            Logger.d("MIGRATING `server_session` DATABASE FROM VERSION " + oldVersion + " TO 18");
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE server_session");
                statement.execute("CREATE TABLE server_session (" +
                        "identity BLOB PRIMARY KEY, " +
                        "nonce BLOB, " +
                        "challenge BLOB, " +
                        "response BLOB, " +
                        "token BLOB, " +
                        "api_key_status INT NOT NULL, " +
                        "permissions BIGINT NOT NULL, " +
                        "api_key_expiration_timestamp BIGINT NOT NULL);");
            }
            oldVersion = 18;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES(?,?,?,?,?, ?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, nonce);
            statement.setBytes(3, challenge);
            statement.setBytes(4, response);
            statement.setBytes(5, token);

            statement.setInt(6, apiKeyStatus);
            statement.setLong(7, permissions);
            statement.setLong(8, apiKeyExpirationTimestamp);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("ServerSession.delete",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }


    @Override
    public void wasCommitted() {
        // No hooks
    }
}
