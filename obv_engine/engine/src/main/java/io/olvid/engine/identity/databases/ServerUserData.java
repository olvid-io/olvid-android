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

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.UserData;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class ServerUserData implements ObvDatabase {
    static final String TABLE_NAME = "server_user_data";

    private final IdentityManagerSession identityManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID label;
    static final String LABEL = "label";
    private long nextRefreshTimestamp;
    static final String NEXT_REFRESH_TIMESTAMP = "next_refresh_timestamp";
    private byte[] bytesGroupOwnerAndUidOrIdentifier; // null for owned identity user data
    static final String BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER = "bytes_group_owner_and_uid_or_identifier";
    private int userDataType;
    static final String USER_DATA_TYPE = "user_data_type";

    static final int TYPE_OWNED_IDENTITY = 1;
    static final int TYPE_GROUP = 2;
    static final int TYPE_GROUP_V2 = 3;


    public UserData getUserData() {
        UserData.Type type;
        switch (userDataType) {
            case TYPE_GROUP_V2:
                return new UserData(ownedIdentity, label, nextRefreshTimestamp, UserData.Type.GROUP_V2, bytesGroupOwnerAndUidOrIdentifier);
            case TYPE_GROUP:
                return new UserData(ownedIdentity, label, nextRefreshTimestamp, UserData.Type.GROUP, bytesGroupOwnerAndUidOrIdentifier);
            case TYPE_OWNED_IDENTITY:
                return new UserData(ownedIdentity, label, nextRefreshTimestamp, UserData.Type.OWNED_IDENTITY, null);
        }
        return null;
    }

    // region constructors

    public static ServerUserData createForOwnedIdentityDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label) {
        if (ownedIdentity == null || label == null) {
            return null;
        }
        try {
            long nextRefreshTimestamp = System.currentTimeMillis() + Constants.USER_DATA_REFRESH_INTERVAL;
            ServerUserData serverUserData = new ServerUserData(identityManagerSession, ownedIdentity, label, nextRefreshTimestamp, null, TYPE_OWNED_IDENTITY);
            serverUserData.insert();
            return serverUserData;
        } catch (SQLException e) {
            return null;
        }
    }

    public static ServerUserData createForOwnedGroupDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label, byte[] bytesGroupOwnerAndUid) {
        if (ownedIdentity == null || label == null || bytesGroupOwnerAndUid == null) {
            return null;
        }
        try {
            long nextRefreshTimestamp = System.currentTimeMillis() + Constants.USER_DATA_REFRESH_INTERVAL;
            ServerUserData serverUserData = new ServerUserData(identityManagerSession, ownedIdentity, label, nextRefreshTimestamp, bytesGroupOwnerAndUid, TYPE_GROUP);
            serverUserData.insert();
            return serverUserData;
        } catch (SQLException e) {
            return null;
        }
    }

    public static ServerUserData createForGroupV2(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label, byte[] bytesGroupIdentifier) {
        if (ownedIdentity == null || label == null || bytesGroupIdentifier == null) {
            return null;
        }
        try {
            long nextRefreshTimestamp = System.currentTimeMillis() + Constants.USER_DATA_REFRESH_INTERVAL;
            ServerUserData serverUserData = new ServerUserData(identityManagerSession, ownedIdentity, label, nextRefreshTimestamp, bytesGroupIdentifier, TYPE_GROUP_V2);
            serverUserData.insert();
            return serverUserData;
        } catch (SQLException e) {
            return null;
        }
    }

    private ServerUserData(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label, long nextRefreshTimestamp, byte[] bytesGroupOwnerAndUidOrIdentifier, int userDataType) {
        this.identityManagerSession = identityManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.label = label;
        this.nextRefreshTimestamp = nextRefreshTimestamp;
        this.bytesGroupOwnerAndUidOrIdentifier = bytesGroupOwnerAndUidOrIdentifier;
        this.userDataType = userDataType;
    }

    private ServerUserData(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            this.label = new UID(res.getBytes(LABEL));
            this.nextRefreshTimestamp = res.getLong(NEXT_REFRESH_TIMESTAMP);
            this.bytesGroupOwnerAndUidOrIdentifier = res.getBytes(BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER);
            this.userDataType = res.getInt(USER_DATA_TYPE);
        } catch (DecodingException e) {
            throw new SQLException();
        }
    }

    // endregion


    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    LABEL + " BLOB NOT NULL, " +
                    NEXT_REFRESH_TIMESTAMP + " INT NOT NULL, " +
                    BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER + " BLOB, " +
                    USER_DATA_TYPE + " INT NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + OWNED_IDENTITY + ", " + LABEL + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 21 && newVersion >= 21) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING server_user_data DATABASE FROM VERSION " + oldVersion + " TO 21");
                statement.execute("ALTER TABLE server_user_data RENAME TO old_server_user_data");
                statement.execute("CREATE TABLE IF NOT EXISTS server_user_data (" +
                                " owned_identity BLOB NOT NULL, " +
                                " label BLOB NOT NULL, " +
                                " next_refresh_timestamp INT NOT NULL, " +
                                " group_details_owner_and_uid BLOB, " +
                                "CONSTRAINT PK_server_user_data PRIMARY KEY(owned_identity, label));");
                statement.execute("INSERT INTO server_user_data (owned_identity, label, next_refresh_timestamp, group_details_owner_and_uid) SELECT owned_identity, label, next_refresh_timestamp, group_details_owner_and_uid FROM old_server_user_data");
                statement.execute("DROP TABLE old_server_user_data");
            }
            oldVersion = 21;
        }
        if (oldVersion < 32 && newVersion >= 32) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING server_user_data DATABASE FROM VERSION " + oldVersion + " TO 32");
                statement.execute("ALTER TABLE server_user_data RENAME TO old_server_user_data");
                statement.execute("CREATE TABLE server_user_data (" +
                        " owned_identity BLOB NOT NULL, " +
                        " label BLOB NOT NULL, " +
                        " next_refresh_timestamp INT NOT NULL, " +
                        " bytes_group_owner_and_uid_or_identifier BLOB, " +
                        " user_data_type INT NOT NULL, " +
                        "CONSTRAINT PK_server_user_data PRIMARY KEY(owned_identity, label));");
                statement.execute("INSERT INTO server_user_data (owned_identity, label, next_refresh_timestamp, bytes_group_owner_and_uid_or_identifier, user_data_type) SELECT owned_identity, label, next_refresh_timestamp, group_details_owner_and_uid, CASE WHEN group_details_owner_and_uid IS NULL THEN 1 ELSE 2 END FROM old_server_user_data");
                statement.execute("DROP TABLE old_server_user_data");
            }
            oldVersion = 32;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ServerUserData.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, label.getBytes());
            statement.setLong(3, nextRefreshTimestamp);
            statement.setBytes(4, bytesGroupOwnerAndUidOrIdentifier);
            statement.setInt(5, userDataType);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ServerUserData.delete",
                "DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + LABEL + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, label.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static ServerUserData get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ServerUserData.get",
                "SELECT * FROM " + TABLE_NAME + " WHERE " +
                OWNED_IDENTITY + " = ? AND " +
                LABEL + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, label.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ServerUserData(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static void deleteAllForOwnedIdentity(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ServerUserData.deleteAllForOwnedIdentity",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static ServerUserData[] getAll(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ServerUserData.getAll",
                "SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ServerUserData> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ServerUserData(identityManagerSession, res));
                }
                return list.toArray(new ServerUserData[0]);
            }
        }
    }

    // endregion


    // region setters

    public void updateNextRefreshTimestamp() {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ServerUserData.updateNextRefreshTimestamp",
                "UPDATE " + TABLE_NAME +
                " SET " + NEXT_REFRESH_TIMESTAMP + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + LABEL + " = ?;")) {
            long timestamp = System.currentTimeMillis() + Constants.USER_DATA_REFRESH_INTERVAL;
            statement.setLong(1, timestamp);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, label.getBytes());
            statement.executeUpdate();
            this.nextRefreshTimestamp = timestamp;
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    // endregion

    // region hooks

    @Override
    public void wasCommitted() {
        // no hooks
    }

    // endregion
}
