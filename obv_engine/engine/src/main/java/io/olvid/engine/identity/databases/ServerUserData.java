/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
    private byte[] groupDetailsOwnerAndUid;
    static final String GROUP_DETAILS_OWNER_AND_UID = "group_details_owner_and_uid";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getLabel() {
        return label;
    }

    public long getNextRefreshTimestamp() {
        return nextRefreshTimestamp;
    }

    public byte[] getGroupDetailsOwnerAndUid() {
        return groupDetailsOwnerAndUid;
    }


    // region constructors

    public static ServerUserData createForOwnedIdentityDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label) {
        if (ownedIdentity == null || label == null) {
            return null;
        }
        try {
            long nextRefreshTimestamp = System.currentTimeMillis() + Constants.USER_DATA_REFRESH_INTERVAL;
            ServerUserData serverUserData = new ServerUserData(identityManagerSession, ownedIdentity, label, nextRefreshTimestamp, null);
            serverUserData.insert();
            return serverUserData;
        } catch (SQLException e) {
            return null;
        }
    }

    public static ServerUserData createForOwnedGroupDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label, byte[] groupOwnerAndUid) {
        if (ownedIdentity == null || label == null) {
            return null;
        }
        try {
            long nextRefreshTimestamp = System.currentTimeMillis() + Constants.USER_DATA_REFRESH_INTERVAL;
            ServerUserData serverUserData = new ServerUserData(identityManagerSession, ownedIdentity, label, nextRefreshTimestamp, groupOwnerAndUid);
            serverUserData.insert();
            return serverUserData;
        } catch (SQLException e) {
            return null;
        }
    }

    public ServerUserData(IdentityManagerSession identityManagerSession, Identity ownedIdentity, UID label, long nextRefreshTimestamp, byte[] groupDetailsOwnerAndUid) {
        this.identityManagerSession = identityManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.label = label;
        this.nextRefreshTimestamp = nextRefreshTimestamp;
        this.groupDetailsOwnerAndUid = groupDetailsOwnerAndUid;
    }

    private ServerUserData(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            this.label = new UID(res.getBytes(LABEL));
            this.nextRefreshTimestamp = res.getLong(NEXT_REFRESH_TIMESTAMP);
            this.groupDetailsOwnerAndUid = res.getBytes(GROUP_DETAILS_OWNER_AND_UID);
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
                    GROUP_DETAILS_OWNER_AND_UID + " BLOB, " +
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
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, label.getBytes());
            statement.setLong(3, nextRefreshTimestamp);
            statement.setBytes(4, groupDetailsOwnerAndUid);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
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
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
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
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static ServerUserData[] getAll(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
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
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
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
            e.printStackTrace();
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
