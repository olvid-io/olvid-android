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

package io.olvid.engine.channel.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.channel.datatypes.ChannelManagerSession;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.KeyId;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.datatypes.Constants;


public class ProvisionedKeyMaterial implements ObvDatabase {
    static final String TABLE_NAME = "provisioned_key_material";

    private final ChannelManagerSession channelManagerSession;

    private KeyId keyId;
    static final String KEY_ID = "key_id";
    private AuthEncKey authEncKey;
    static final String AUTH_ENC_KEY = "auth_enc_key";
    private Long expirationTimestamp;
    static final String EXPIRATION_TIMESTAMP = "expiration_timestamp";
    private int selfRatchetingCount;
    static final String SELF_RATCHETING_COUNT = "self_ratcheting_count";

    // foreign key for a Provision
    private int provisionFullRatchetingCount;
    static final String PROVISION_FULL_RATCHETING_COUNT = "provision_full_ratcheting_count";
    private UID provisionObliviousChannelCurrentDeviceUid;
    static final String PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID = "provision_oblivious_channel_current_device_uid";
    private UID provisionObliviousChannelRemoteDeviceUid;
    static final String PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID = "provision_oblivious_channel_remote_device_uid";
    private Identity provisionObliviousChannelRemoteIdentity;
    static final String PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY = "provision_oblivious_channel_remote_identity";


    public AuthEncKey getAuthEncKey() {
        return authEncKey;
    }

    public int getSelfRatchetingCount() {
        return selfRatchetingCount;
    }

    public int getProvisionFullRatchetingCount() {
        return provisionFullRatchetingCount;
    }

    public UID getProvisionObliviousChannelCurrentDeviceUid() {
        return provisionObliviousChannelCurrentDeviceUid;
    }

    public UID getProvisionObliviousChannelRemoteDeviceUid() {
        return provisionObliviousChannelRemoteDeviceUid;
    }

    public Identity getProvisionObliviousChannelRemoteIdentity() {
        return provisionObliviousChannelRemoteIdentity;
    }

    public Provision getProvision() {
        return Provision.get(channelManagerSession, provisionFullRatchetingCount, provisionObliviousChannelCurrentDeviceUid, provisionObliviousChannelRemoteDeviceUid, provisionObliviousChannelRemoteIdentity);
    }

    ObliviousChannel getObliviousChannel() {
        return ObliviousChannel.get(channelManagerSession, provisionObliviousChannelCurrentDeviceUid, provisionObliviousChannelRemoteDeviceUid, provisionObliviousChannelRemoteIdentity, false);
    }



    public void setExpirationTimestampsOfOlderProvisionedKeyMaterials() {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                EXPIRATION_TIMESTAMP + " = ? " +
                " WHERE " + EXPIRATION_TIMESTAMP + " IS NULL AND " +
                PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ? AND " +
                "( " + PROVISION_FULL_RATCHETING_COUNT + " < ? OR " +
                "( " + PROVISION_FULL_RATCHETING_COUNT + " = ? AND " +
                SELF_RATCHETING_COUNT + " < ?));")) {
            long expirationTimestamp = System.currentTimeMillis() + Constants.PROVISIONED_KEY_MATERIAL_EXPIRATION_DELAY;
            statement.setLong(1, expirationTimestamp);
            statement.setBytes(2, provisionObliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(3, provisionObliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(4, provisionObliviousChannelRemoteIdentity.getBytes());
            statement.setInt(5, provisionFullRatchetingCount);
            statement.setInt(6, provisionFullRatchetingCount);
            statement.setInt(7, selfRatchetingCount);
            statement.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public static void deleteAllExpired(ChannelManagerSession channelManagerSession) {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement(
                "DELETE FROM " + TABLE_NAME + " WHERE " + EXPIRATION_TIMESTAMP + " IS NOT NULL AND " + EXPIRATION_TIMESTAMP + " < ?;")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ignored) {}
    }




    public static ProvisionedKeyMaterial create(ChannelManagerSession channelManagerSession,
                                                KeyId keyId,
                                                AuthEncKey authEncKey,
                                                int selfRatchetingCount,
                                                Provision provision) {
        if ((keyId == null) || (authEncKey == null) || (provision == null)) {
            return null;
        }
        try {
            ProvisionedKeyMaterial provisionedKeyMaterial = new ProvisionedKeyMaterial(channelManagerSession, keyId, authEncKey, selfRatchetingCount, provision);
            provisionedKeyMaterial.insert();
            return provisionedKeyMaterial;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ProvisionedKeyMaterial(ChannelManagerSession channelManagerSession,
                                   KeyId keyId,
                                   AuthEncKey authEncKey,
                                   int selfRatchetingCount,
                                   Provision provision) {
        this.channelManagerSession = channelManagerSession;
        this.keyId = keyId;
        this.authEncKey = authEncKey;
        this.expirationTimestamp = null;
        this.selfRatchetingCount = selfRatchetingCount;
        this.provisionFullRatchetingCount = provision.getFullRatchetingCount();
        this.provisionObliviousChannelCurrentDeviceUid = provision.getObliviousChannelCurrentDeviceUid();
        this.provisionObliviousChannelRemoteDeviceUid = provision.getObliviousChannelRemoteDeviceUid();
        this.provisionObliviousChannelRemoteIdentity = provision.getObliviousChannelRemoteIdentity();
    }

    private ProvisionedKeyMaterial(ChannelManagerSession channelManagerSession, ResultSet res) throws SQLException {
        this.channelManagerSession = channelManagerSession;
        this.keyId = new KeyId(res.getBytes(KEY_ID));
        try {
            this.authEncKey = (AuthEncKey) new Encoded(res.getBytes(AUTH_ENC_KEY)).decodeSymmetricKey();
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.expirationTimestamp = res.getLong(EXPIRATION_TIMESTAMP);
        this.selfRatchetingCount = res.getInt(SELF_RATCHETING_COUNT);
        this.provisionFullRatchetingCount = res.getInt(PROVISION_FULL_RATCHETING_COUNT);
        this.provisionObliviousChannelCurrentDeviceUid = new UID(res.getBytes(PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID));
        this.provisionObliviousChannelRemoteDeviceUid = new UID(res.getBytes(PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID));
        try {
            this.provisionObliviousChannelRemoteIdentity = Identity.of(res.getBytes(PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    KEY_ID + " BLOB NOT NULL, " +
                    AUTH_ENC_KEY + " BLOB NOT NULL, " +
                    EXPIRATION_TIMESTAMP + " BIGINT, " +
                    SELF_RATCHETING_COUNT + " INT NOT NULL, " +
                    PROVISION_FULL_RATCHETING_COUNT + " INT NOT NULL, " +
                    PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " BLOB NOT NULL, " +
                    PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " BLOB NOT NULL, " +
                    PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + SELF_RATCHETING_COUNT + ", " + PROVISION_FULL_RATCHETING_COUNT + ", " + PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + ", " + PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID  + ", " + PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + "), " +
                    "FOREIGN KEY (" + PROVISION_FULL_RATCHETING_COUNT + ", " + PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + ", " + PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + ", " + PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + ") REFERENCES " + Provision.TABLE_NAME + "(" + Provision.FULL_RATCHETING_COUNT + ", " + Provision.OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + ", " + Provision.OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + ", " + Provision.OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM provisioned_key_material AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM provision " +
                        " WHERE full_ratcheting_count = p.provision_full_ratcheting_count" +
                        " AND oblivious_channel_current_device_uid = p.provision_oblivious_channel_current_device_uid" +
                        " AND oblivious_channel_remote_device_uid = p.provision_oblivious_channel_remote_device_uid" +
                        " AND oblivious_channel_remote_identity = p.provision_oblivious_channel_remote_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?);")) {
            statement.setBytes(1, keyId.getBytes());
            statement.setBytes(2, Encoded.of(authEncKey).getBytes());
            if (expirationTimestamp == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, expirationTimestamp);
            }
            statement.setInt(4, selfRatchetingCount);
            statement.setInt(5, provisionFullRatchetingCount);
            statement.setBytes(6, provisionObliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(7, provisionObliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(8, provisionObliviousChannelRemoteIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + SELF_RATCHETING_COUNT + " = ? AND " + PROVISION_FULL_RATCHETING_COUNT + " = ? AND " + PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " + PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " + PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, selfRatchetingCount);
            statement.setInt(2, provisionFullRatchetingCount);
            statement.setBytes(3, provisionObliviousChannelCurrentDeviceUid.getBytes());
            statement.setBytes(4, provisionObliviousChannelRemoteDeviceUid.getBytes());
            statement.setBytes(5, provisionObliviousChannelRemoteIdentity.getBytes());
            statement.executeUpdate();
        }
    }



    public static ProvisionedKeyMaterial[] getAll(ChannelManagerSession channelManagerSession, KeyId keyId, UID currentDeviceUid) {
        if ((keyId == null) || (currentDeviceUid == null)) {
            return new ProvisionedKeyMaterial[0];
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME + " WHERE " +
                        KEY_ID + " = ? AND " +
                        PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ?;")) {
            statement.setBytes(1, keyId.getBytes());
            statement.setBytes(2, currentDeviceUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ProvisionedKeyMaterial> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ProvisionedKeyMaterial(channelManagerSession, res));
                }
                return list.toArray(new ProvisionedKeyMaterial[0]);
            }
        } catch (SQLException e) {
            return new ProvisionedKeyMaterial[0];
        }
    }


    static int countNotExpiringProvisionedReceiveKey(ChannelManagerSession channelManagerSession, Provision provision) {
        final String COUNT = "count";
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT COUNT(*) AS " + COUNT + " FROM " + TABLE_NAME + " WHERE " +
                EXPIRATION_TIMESTAMP + " IS NULL AND " +
                PROVISION_FULL_RATCHETING_COUNT + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, provision.getFullRatchetingCount());
            statement.setBytes(2, provision.getObliviousChannelCurrentDeviceUid().getBytes());
            statement.setBytes(3, provision.getObliviousChannelRemoteDeviceUid().getBytes());
            statement.setBytes(4, provision.getObliviousChannelRemoteIdentity().getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return res.getInt(COUNT);
                } else {
                    return 0;
                }
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    static int countProvisionedReceiveKey(ChannelManagerSession channelManagerSession, Provision provision) {
        final String COUNT = "count";
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT COUNT(*) AS " + COUNT + " FROM " + TABLE_NAME + " WHERE " +
                PROVISION_FULL_RATCHETING_COUNT + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_CURRENT_DEVICE_UID + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_REMOTE_DEVICE_UID + " = ? AND " +
                PROVISION_OBLIVIOUS_CHANNEL_REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, provision.getFullRatchetingCount());
            statement.setBytes(2, provision.getObliviousChannelCurrentDeviceUid().getBytes());
            statement.setBytes(3, provision.getObliviousChannelRemoteDeviceUid().getBytes());
            statement.setBytes(4, provision.getObliviousChannelRemoteIdentity().getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return res.getInt(COUNT);
                } else {
                    return 0;
                }
            }
        } catch (SQLException e) {
            return 0;
        }
    }



    @Override
    public void wasCommitted() {
        // no hooks here
    }
}
