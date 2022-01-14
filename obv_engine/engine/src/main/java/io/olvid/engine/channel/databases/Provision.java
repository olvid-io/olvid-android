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

package io.olvid.engine.channel.databases;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.channel.datatypes.ChannelManagerSession;
import io.olvid.engine.channel.datatypes.RatchetingOutput;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.datatypes.Constants;

public class Provision implements ObvDatabase {
    static final String TABLE_NAME = "provision";

    private final ChannelManagerSession channelManagerSession;

    private int fullRatchetingCount;
    static final String FULL_RATCHETING_COUNT = "full_ratcheting_count";
    private int selfRatchetingCount;
    static final String SELF_RATCHETING_COUNT = "self_ratcheting_count";
    private Seed seedForNextProvisionedReceiveKey;
    static final String SEED_FOR_NEXT_PROVISIONED_RECEIVE_KEY = "seed_for_next_provisioned_receive_key";
    private int obliviousEngineVersion;
    static final String OBLIVIOUS_ENGINE_VERSION = "oblivious_engine_version";

    // foreign key for an ObliviousChannel
    private UID obliviousChannelCurrentDeviceUid;
    static final String OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID = "oblivious_channel_current_device_uid";
    private UID obliviousChannelRemoteDeviceUid;
    static final String OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID = "oblivious_channel_remote_device_uid";
    private Identity obliviousChannelRemoteIdentity;
    static final String OBLIVIOUS_CHANNEL_REMOTE_IDENTITY = "oblivious_channel_remote_identity";

    public int getFullRatchetingCount() {
        return fullRatchetingCount;
    }

    public int getSelfRatchetingCount() {
        return selfRatchetingCount;
    }

    public Seed getSeedForNextProvisionedReceiveKey() {
        return seedForNextProvisionedReceiveKey;
    }

    public int getObliviousEngineVersion() {
        return obliviousEngineVersion;
    }

    public UID getObliviousChannelCurrentDeviceUid() {
        return obliviousChannelCurrentDeviceUid;
    }

    public UID getObliviousChannelRemoteDeviceUid() {
        return obliviousChannelRemoteDeviceUid;
    }

    public Identity getObliviousChannelRemoteIdentity() {
        return obliviousChannelRemoteIdentity;
    }


    private void selfRatchet(int count) {
        // First generate all the new Key Material
        for (int i=0; i<count; i++) {
            RatchetingOutput ratchetingOutput = ObliviousChannel.computeSelfRatchet(seedForNextProvisionedReceiveKey, obliviousEngineVersion);
            seedForNextProvisionedReceiveKey = ratchetingOutput.getRatchetedSeed();
            ProvisionedKeyMaterial.create(channelManagerSession, ratchetingOutput.getKeyId(), ratchetingOutput.getAuthEncKey(), selfRatchetingCount, this);
            selfRatchetingCount++;
        }
        // Then update the current Provision
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                SEED_FOR_NEXT_PROVISIONED_RECEIVE_KEY + " = ?, " +
                SELF_RATCHETING_COUNT + " = ? " +
                " WHERE " + FULL_RATCHETING_COUNT + " = ? AND " +
                OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " +
                OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " +
                OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ?;")) {
            statement.setBytes(1, seedForNextProvisionedReceiveKey.getBytes());
            statement.setInt(2, selfRatchetingCount);
            statement.setInt(3, fullRatchetingCount);
            statement.setBytes(4, obliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(5, obliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(6, obliviousChannelRemoteIdentity.getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void selfRatchetIfRequired() {
        int remainingKeyMaterialCount = ProvisionedKeyMaterial.countNotExpiringProvisionedReceiveKey(channelManagerSession, this);
        if (remainingKeyMaterialCount < Constants.REPROVISIONING_THRESHOLD) {
            selfRatchet(Constants.REPROVISIONING_THRESHOLD);
        }
    }

    public static void deleteAllEmpty(ChannelManagerSession channelManagerSession) {
        // loop over all Provision, and count the number of ProvisionedKeyMaterial for each of them:
        // delete those with 0 ProvisionedKeyMaterial.
        try (Statement statement = channelManagerSession.session.createStatement()) {
            try (ResultSet res = statement.executeQuery("SELECT * FROM " + TABLE_NAME)) {
                while (res.next()) {
                    Provision provision = new Provision(channelManagerSession, res);
                    if (ProvisionedKeyMaterial.countProvisionedReceiveKey(channelManagerSession, provision) == 0) {
                        provision.delete();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    public static Provision createOrReplace(ChannelManagerSession channelManagerSession,
                                   int fullRatchetingCount,
                                   ObliviousChannel obliviousChannel,
                                   Seed seedForNextProvisionedReceiveKey,
                                   int obliviousEngineVersion) {
        if ((obliviousChannel == null) || (seedForNextProvisionedReceiveKey == null)) {
            return null;
        }
        try {
            Provision provision = new Provision(channelManagerSession, fullRatchetingCount, obliviousChannel, seedForNextProvisionedReceiveKey, obliviousEngineVersion);
            provision.insert();
            provision.selfRatchet(2*Constants.REPROVISIONING_THRESHOLD);
            return provision;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Provision(ChannelManagerSession channelManagerSession,
                      int fullRatchetingCount,
                      ObliviousChannel obliviousChannel,
                      Seed seedForNextProvisionedReceiveKey,
                      int obliviousEngineVersion) {
        this.channelManagerSession = channelManagerSession;
        this.fullRatchetingCount = fullRatchetingCount;
        this.selfRatchetingCount = 0;
        this.seedForNextProvisionedReceiveKey = seedForNextProvisionedReceiveKey;
        this.obliviousEngineVersion = obliviousEngineVersion;
        this.obliviousChannelCurrentDeviceUid = obliviousChannel.getCurrentDeviceUid();
        this.obliviousChannelRemoteDeviceUid = obliviousChannel.getRemoteDeviceUid();
        this.obliviousChannelRemoteIdentity = obliviousChannel.getRemoteIdentity();
    }

    private Provision(ChannelManagerSession channelManagerSession, ResultSet res) throws SQLException {
        this.channelManagerSession = channelManagerSession;
        this.fullRatchetingCount = res.getInt(FULL_RATCHETING_COUNT);
        this.selfRatchetingCount = res.getInt(SELF_RATCHETING_COUNT);
        this.seedForNextProvisionedReceiveKey = new Seed(res.getBytes(SEED_FOR_NEXT_PROVISIONED_RECEIVE_KEY));
        this.obliviousEngineVersion = res.getInt(OBLIVIOUS_ENGINE_VERSION);
        this.obliviousChannelCurrentDeviceUid = new UID(res.getBytes(OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID));
        this.obliviousChannelRemoteDeviceUid = new UID(res.getBytes(OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID));
        try {
            this.obliviousChannelRemoteIdentity = Identity.of(res.getBytes(OBLIVIOUS_CHANNEL_REMOTE_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
    }



    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    FULL_RATCHETING_COUNT + " INT NOT NULL, " +
                    SELF_RATCHETING_COUNT + " INT NOT NULL, " +
                    SEED_FOR_NEXT_PROVISIONED_RECEIVE_KEY + " BLOB NOT NULL, " +
                    OBLIVIOUS_ENGINE_VERSION + " INT NOT NULL, " +
                    OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " BLOB NOT NULL, " +
                    OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " BLOB NOT NULL, " +
                    OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + FULL_RATCHETING_COUNT + ", " + OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + ", " + OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + ", " + OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + "), " +
                    "FOREIGN KEY (" + OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + ", " + OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + ", " + OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + ") REFERENCES " + ObliviousChannel.TABLE_NAME + "(" + ObliviousChannel.CURRENT_DEVICE_UID + ", " + ObliviousChannel.REMOTE_DEVICE_UID + ", " + ObliviousChannel.REMOTE_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM provision AS p " +
                        " WHERE NOT EXISTS (" +
                          " SELECT 1 FROM oblivious_channel " +
                          " WHERE current_device_uid = p.oblivious_channel_current_device_uid" +
                          " AND remote_device_uid = p.oblivious_channel_remote_device_uid" +
                          " AND contact_identity = p.oblivious_channel_remote_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?);")) {
            statement.setInt(1, fullRatchetingCount);
            statement.setInt(2, selfRatchetingCount);
            statement.setBytes(3, seedForNextProvisionedReceiveKey.getBytes());
            statement.setInt(4, obliviousEngineVersion);
            statement.setBytes(5, obliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(6, obliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(7, obliviousChannelRemoteIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + FULL_RATCHETING_COUNT + " = ? AND " + OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " + OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " + OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, fullRatchetingCount);
            statement.setBytes(2, obliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(3, obliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(4, obliviousChannelRemoteIdentity.getBytes());
            statement.executeUpdate();
        }
    }




    public static Provision get(ChannelManagerSession channelManagerSession, int fullRatchetingCount, UID obliviousChannelCurrentDeviceUid, UID obliviousChannelRemoteDeviceUid, Identity obliviousChannelRemoteIdentity) {
        if ((obliviousChannelCurrentDeviceUid == null) || (obliviousChannelRemoteDeviceUid == null) || (obliviousChannelRemoteIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                FULL_RATCHETING_COUNT + " = ? AND " +
                OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " +
                OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " +
                OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, fullRatchetingCount);
            statement.setBytes(2, obliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(3, obliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(4, obliviousChannelRemoteIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new Provision(channelManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }


    public static Provision[] getAll(ChannelManagerSession channelManagerSession, UID obliviousChannelCurrentDeviceUid, UID obliviousChannelRemoteDeviceUid, Identity obliviousChannelRemoteIdentity) {
        if ((obliviousChannelCurrentDeviceUid == null) || (obliviousChannelRemoteDeviceUid == null) || (obliviousChannelRemoteIdentity == null)) {
            return new Provision[0];
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " +
                OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " +
                OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ?;")) {
            statement.setBytes(1, obliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(2, obliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(3, obliviousChannelRemoteIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<Provision> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new Provision(channelManagerSession, res));
                }
                return list.toArray(new Provision[0]);
            }
        } catch (SQLException e) {
            return new Provision[0];
        }
    }


    @Override
    public void wasCommitted() {
        // No hooks here
    }
}
