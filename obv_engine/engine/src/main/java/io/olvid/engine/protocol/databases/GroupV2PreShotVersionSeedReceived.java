/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.engine.protocol.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;


public class GroupV2PreShotVersionSeedReceived implements ObvDatabase {
    static final String TABLE_NAME = "group_v2_pre_shot_version_seed_received";

    private final ProtocolManagerSession protocolManagerSession;

    private final Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private final byte[] groupIdentifier;
    static final String GROUP_IDENTIFIER = "group_identifier";
    private final byte[] preShotVersionSeed;
    static final String PRE_SHOT_VERSION_SEED = "pre_shot_version_seed";
    private final long creationTimestamp;
    static final String CREATION_TIMESTAMP = "creation_timestamp";

    public Seed getVersionSeed() {
        return new Seed(preShotVersionSeed);
    }


    // region constructors

    public static GroupV2PreShotVersionSeedReceived create(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Seed preShotVersionSeed) {
        if (ownedIdentity == null || groupIdentifier == null || preShotVersionSeed == null) {
            return null;
        }
        try {
            GroupV2PreShotVersionSeedReceived groupV2SignatureReceived = new GroupV2PreShotVersionSeedReceived(protocolManagerSession, ownedIdentity, groupIdentifier.getBytes(), preShotVersionSeed.getBytes(), System.currentTimeMillis());
            groupV2SignatureReceived.insert();
            return groupV2SignatureReceived;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    private GroupV2PreShotVersionSeedReceived(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, byte[] groupIdentifier, byte[] preShotVersionSeed, long creationTimestamp) {
        this.protocolManagerSession = protocolManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.groupIdentifier = groupIdentifier;
        this.preShotVersionSeed = preShotVersionSeed;
        this.creationTimestamp = creationTimestamp;
    }


    private GroupV2PreShotVersionSeedReceived(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;

        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.groupIdentifier = res.getBytes(GROUP_IDENTIFIER);
        this.preShotVersionSeed = res.getBytes(PRE_SHOT_VERSION_SEED);
        this.creationTimestamp = res.getLong(CREATION_TIMESTAMP);
    }

    // endregion


    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    GROUP_IDENTIFIER + " BLOB NOT NULL, " +
                    PRE_SHOT_VERSION_SEED + " BLOB NOT NULL, " +
                    CREATION_TIMESTAMP + " INTEGER NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY (" + OWNED_IDENTITY + ", " + GROUP_IDENTIFIER + ", " + PRE_SHOT_VERSION_SEED + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 50 && newVersion >= 50) {
            Logger.d("CREATING `group_v2_pre_shot_version_seed_received` TABLE AS PART OF VERSION 50");
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS group_v2_pre_shot_version_seed_received (" +
                        " owned_identity BLOB NOT NULL, " +
                        " group_identifier BLOB NOT NULL, " +
                        " pre_shot_version_seed BLOB NOT NULL, " +
                        " creation_timestamp INTEGER NOT NULL, " +
                        "CONSTRAINT PK_group_v2_pre_shot_version_seed_received PRIMARY KEY (owned_identity, group_identifier, pre_shot_version_seed));");
            }
            oldVersion = 50;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("GroupV2PreShotVersionSeedReceived.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, groupIdentifier);
            statement.setBytes(3, preShotVersionSeed);
            statement.setLong(4, creationTimestamp);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("GroupV2PreShotVersionSeedReceived.delete",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY  + " = ? AND " + GROUP_IDENTIFIER + " = ? AND " + PRE_SHOT_VERSION_SEED + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, groupIdentifier);
            statement.setBytes(3, preShotVersionSeed);
            statement.executeUpdate();
        }
    }

    // endregion


    public static List<GroupV2PreShotVersionSeedReceived> getAllForGroupIdentifier(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if (ownedIdentity == null || groupIdentifier == null) {
            return Collections.emptyList();
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("GroupV2PreShotVersionSeedReceived.getAllForGroupIdentifier",
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + OWNED_IDENTITY + " = ? " +
                        " AND " + GROUP_IDENTIFIER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, groupIdentifier.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<GroupV2PreShotVersionSeedReceived> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new GroupV2PreShotVersionSeedReceived(protocolManagerSession, res));
                }
                return list;
            }
        }
    }

    public static void expire(ProtocolManagerSession protocolManagerSession, long timestamp) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("GroupV2PreShotVersionSeedReceived.expire",
                "DELETE FROM " + TABLE_NAME + " WHERE " + CREATION_TIMESTAMP + " < ?;")) {
            statement.setLong(1, timestamp);
            statement.executeUpdate();
        }
    }

    public static void deleteAllForGroupIdentifier(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("GroupV2PreShotVersionSeedReceived.deleteAllForGroupIdentifier",
                "DELETE FROM " + TABLE_NAME +
                        " WHERE " + OWNED_IDENTITY + " = ? " +
                        " AND " + GROUP_IDENTIFIER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, groupIdentifier.getBytes());
            statement.executeUpdate();
        }
    }

    public static void deleteAllForOwnedIdentity(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("GroupV2PreShotVersionSeedReceived.deleteAllForOwnedIdentity",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }


    @Override
    public void wasCommitted() {
        // No hooks here
    }
}
