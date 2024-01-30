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

package io.olvid.engine.networkfetch.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;


public class PushNotificationConfiguration implements ObvDatabase {
    static final String TABLE_NAME = "push_notification_configuration";


    private final FetchManagerSession fetchManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "identity";
    private UID deviceUid;
    static final String DEVICE_UID = "device_uid";
    private byte pushNotificationType;
    static final String PUSH_NOTIFICATION_TYPE = "push_notification_type";
    private byte[] token;
    static final String TOKEN = "token";
    private UID identityMaskingUid;
    static final String IDENTITY_MASKING_UID = "identity_masking_uid";
    private int multiDeviceConfiguration;
    static final String MULTI_DEVICE_CONFIGURATION = "multi_device_configuration";
    private UID deviceUidToReplace;
    static final String DEVICE_UID_TO_REPLACE = "device_uid_to_replace";


    private static final int CONFIGURATION_REACTIVATE_CURRENT_DEVICE_AT_NEXT_REGISTRATION_BIT = 0x1;
//    private static final int CONFIGURATION_USE_MULTI_DEVICE_BIT = 0x2;

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getDeviceUid() {
        return deviceUid;
    }

    public byte getPushNotificationType() {
        return pushNotificationType;
    }

    public byte[] getToken() {
        return token;
    }

    public UID getIdentityMaskingUid() {
        return identityMaskingUid;
    }

    public boolean shouldReactivateCurrentDevice() {
        return (multiDeviceConfiguration & CONFIGURATION_REACTIVATE_CURRENT_DEVICE_AT_NEXT_REGISTRATION_BIT) != 0;
    }

    public UID getDeviceUidToReplace() {
        return deviceUidToReplace;
    }

    public PushNotificationTypeAndParameters getPushNotificationTypeAndParameters() {
        boolean reactivateCurrentDevice = (multiDeviceConfiguration & CONFIGURATION_REACTIVATE_CURRENT_DEVICE_AT_NEXT_REGISTRATION_BIT) != 0;
        return new PushNotificationTypeAndParameters(pushNotificationType, token, identityMaskingUid, reactivateCurrentDevice, deviceUidToReplace);
    }

    // region constructors

    public static PushNotificationConfiguration create(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID deviceUid, PushNotificationTypeAndParameters pushNotificationTypeAndParameters) {
        if (ownedIdentity == null || deviceUid == null || pushNotificationTypeAndParameters == null) {
            return null;
        }

        int multiDeviceConfiguration = 0;
        if (pushNotificationTypeAndParameters.reactivateCurrentDevice) {
            multiDeviceConfiguration |= CONFIGURATION_REACTIVATE_CURRENT_DEVICE_AT_NEXT_REGISTRATION_BIT;
        }

        try {
            PushNotificationConfiguration pushNotificationConfiguration = new PushNotificationConfiguration(fetchManagerSession, ownedIdentity, deviceUid, pushNotificationTypeAndParameters.pushNotificationType, pushNotificationTypeAndParameters.token, pushNotificationTypeAndParameters.identityMaskingUid, multiDeviceConfiguration, pushNotificationTypeAndParameters.deviceUidToReplace);
            pushNotificationConfiguration.insert();
            return pushNotificationConfiguration;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private PushNotificationConfiguration(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID deviceUid, byte pushNotificationType, byte[] token, UID identityMaskingUid, int multiDeviceConfiguration, UID deviceUidToReplace) {
        this.fetchManagerSession = fetchManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.deviceUid = deviceUid;
        this.pushNotificationType = pushNotificationType;
        this.token = token;
        this.identityMaskingUid = identityMaskingUid;
        this.multiDeviceConfiguration = multiDeviceConfiguration;
        this.deviceUidToReplace = deviceUidToReplace;
    }

    private PushNotificationConfiguration(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.deviceUid = new UID(res.getBytes(DEVICE_UID));
        this.pushNotificationType = res.getByte(PUSH_NOTIFICATION_TYPE);
        this.token = res.getBytes(TOKEN);
        byte[] identityMaskingBytes = res.getBytes(IDENTITY_MASKING_UID);
        this.identityMaskingUid = (identityMaskingBytes == null) ? null : new UID(identityMaskingBytes);
        this.multiDeviceConfiguration = res.getInt(MULTI_DEVICE_CONFIGURATION);
        byte[] bytesDeviceUidToReplace = res.getBytes(DEVICE_UID_TO_REPLACE);
        this.deviceUidToReplace = (bytesDeviceUidToReplace == null) ? null : new UID(bytesDeviceUidToReplace);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB PRIMARY KEY, " +
                    DEVICE_UID + " BLOB NOT NULL, " +
                    PUSH_NOTIFICATION_TYPE + " INT NOT NULL, " +
                    TOKEN + " BLOB, " +
                    IDENTITY_MASKING_UID + " BLOB, " +
                    MULTI_DEVICE_CONFIGURATION + " INT NOT NULL," +
                    DEVICE_UID_TO_REPLACE + " BLOB " +
                    ");");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 15 && newVersion >= 15) {
            Logger.d("DELETING old `registered_push_notification` table. It is replaced by the new and better `push_notification_configuration`.");
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS registered_push_notification");
            }
            oldVersion = 15;
        }
        if (oldVersion < 35 && newVersion >= 35) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `push_notification_configuration` TABLE FROM VERSION " + oldVersion + " TO 35");
                statement.execute("ALTER TABLE push_notification_configuration ADD COLUMN device_uid_to_replace BLOB DEFAULT NULL");
            }
            oldVersion = 35;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES(?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, deviceUid.getBytes());
            statement.setByte(3, pushNotificationType);
            statement.setBytes(4, token);
            statement.setBytes(5, (identityMaskingUid == null) ? null : identityMaskingUid.getBytes());
            statement.setInt(6, multiDeviceConfiguration);
            statement.setBytes(7, (deviceUidToReplace == null) ? null : deviceUidToReplace.getBytes());
            statement.executeUpdate();
            this.commitHookBits |= HOOK_BIT_INSERT;
            fetchManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static PushNotificationConfiguration get(FetchManagerSession fetchManagerSession, Identity ownedIdentity) {
        if (ownedIdentity == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new PushNotificationConfiguration(fetchManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static PushNotificationConfiguration[] getAll(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<PushNotificationConfiguration> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new PushNotificationConfiguration(fetchManagerSession, res));
                }
                return list.toArray(new PushNotificationConfiguration[0]);
            }
        } catch (SQLException e) {
            return new PushNotificationConfiguration[0];
        }
    }

    // endregion

    // region setters

    public void clearKickOtherDevices() {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + MULTI_DEVICE_CONFIGURATION + " = ?, " +
                DEVICE_UID_TO_REPLACE + " = NULL " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            int newMultiDeviceConfiguration = multiDeviceConfiguration & (~CONFIGURATION_REACTIVATE_CURRENT_DEVICE_AT_NEXT_REGISTRATION_BIT);
            statement.setInt(1, newMultiDeviceConfiguration);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.multiDeviceConfiguration = newMultiDeviceConfiguration;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteForOwnedIdentity(FetchManagerSession fetchManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region hooks

    public interface NewPushNotificationConfigurationListener {
        void newPushNotificationConfiguration(Identity identity, UID deviceUid, PushNotificationTypeAndParameters pushNotificationTypeAndParameters);
    }

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERT = 0x1;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERT) != 0) {
            if (fetchManagerSession.newPushNotificationConfigurationListener != null) {
                fetchManagerSession.newPushNotificationConfigurationListener.newPushNotificationConfiguration(ownedIdentity, deviceUid, getPushNotificationTypeAndParameters());
            }
        }
    }

    // endregion
}
