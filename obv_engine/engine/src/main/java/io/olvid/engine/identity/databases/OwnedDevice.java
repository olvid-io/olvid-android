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

package io.olvid.engine.identity.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.KeyId;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.PreKeyBlobOnServer;
import io.olvid.engine.datatypes.containers.PreKey;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class OwnedDevice implements ObvDatabase {
    static final String TABLE_NAME = "owned_device";

    private final IdentityManagerSession identityManagerSession;

    private UID uid;
    static final String UID_ = "uid";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "identity";
    private boolean isCurrentDevice;
    static final String IS_CURRENT_DEVICE = "is_current_device";
    private byte[] serializedDeviceCapabilities; // for the current device, this corresponds to the capabilities that were pushed to contacts. Actual capabilities are static in ObvCapability!
    static final String SERIALIZED_DEVICE_CAPABILITIES = "serialized_device_capabilities";

    private String displayName;
    static final String DISPLAY_NAME = "display_name";
    private Long expirationTimestamp;
    static final String EXPIRATION_TIMESTAMP = "expiration_timestamp";
    private Long lastRegistrationTimestamp;
    static final String LAST_REGISTRATION_TIMESTAMP = "last_registration_timestamp";
    private long latestChannelCreationPingTimestamp;
    static final String LATEST_CHANNEL_CREATION_PING_TIMESTAMP = "latest_channel_creation_ping_timestamp";
    private KeyId preKeyId;
    static final String PRE_KEY_ID = "pre_key_id";
    private EncryptionPublicKey preKeyEncryptionPublicKey;
    static final String PRE_KEY_ENCRYPTION_PUBLIC_KEY = "pre_key_encryption_public_key";
    private Long preKeyExpirationTimestamp;
    static final String PRE_KEY_EXPIRATION_TIMESTAMP = "pre_key_expiration_timestamp";



    public UID getUid() {
        return uid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public boolean isCurrentDevice() {
        return isCurrentDevice;
    }

    public boolean hasPreKey() {
        return preKeyId != null;
    }


    public PreKey getPreKey() {
        if (hasPreKey()) {
            return new PreKey(uid, preKeyId, preKeyEncryptionPublicKey, preKeyExpirationTimestamp);
        } else {
            return null;
        }
    }


    public String getDisplayName() {
        return displayName;
    }

    public Long getExpirationTimestamp() {
        return expirationTimestamp;
    }

    public Long getLastRegistrationTimestamp() {
        return lastRegistrationTimestamp;
    }

    public List<ObvCapability> getDeviceCapabilities() {
        return ObvCapability.deserializeDeviceCapabilities(serializedDeviceCapabilities);
    }

    public String[] getRawDeviceCapabilities() {
        return ObvCapability.deserializeRawDeviceCapabilities(serializedDeviceCapabilities);
    }

    public long getLatestChannelCreationPingTimestamp() {
        return latestChannelCreationPingTimestamp;
    }

    public static OwnedDevice createOtherDevice(IdentityManagerSession identityManagerSession, UID uid, Identity identity, String displayName, Long expirationTimestamp, Long lastRegistrationTimestamp, PreKeyBlobOnServer preKeyBlob, boolean channelCreationAlreadyInProgress) {
        if (identity == null) {
            return null;
        }
        try {
            OwnedDevice ownedDevice = new OwnedDevice(identityManagerSession, uid, identity, false, preKeyBlob == null ? null : ObvCapability.serializeRawDeviceCapabilities(preKeyBlob.rawDeviceCapabilities), displayName, expirationTimestamp, lastRegistrationTimestamp, preKeyBlob == null ? null : preKeyBlob.preKey);
            ownedDevice.insert();
            ownedDevice.channelCreationAlreadyInProgress = channelCreationAlreadyInProgress;
            return ownedDevice;
        } catch (SQLException e) {
            return null;
        }
    }

    public static OwnedDevice createCurrentDevice(IdentityManagerSession identityManagerSession, Identity identity, String displayName, PRNGService prng) {
        if (identity == null) {
            return null;
        }
        UID uid = new UID(prng);
        try {
            OwnedDevice ownedDevice = new OwnedDevice(identityManagerSession, uid, identity, true, null, displayName, null, null, null);
            ownedDevice.insert();
            return ownedDevice;
        } catch (SQLException e) {
            return null;
        }
    }

    private OwnedDevice(IdentityManagerSession identityManagerSession, UID uid, Identity ownedIdentity, boolean isCurrentDevice, byte[] serializedDeviceCapabilities, String displayName, Long expirationTimestamp, Long lastRegistrationTimestamp, PreKey preKey) {
        this.identityManagerSession = identityManagerSession;
        this.uid = uid;
        this.ownedIdentity = ownedIdentity;
        this.isCurrentDevice = isCurrentDevice;
        this.serializedDeviceCapabilities = serializedDeviceCapabilities;
        this.displayName = displayName;
        this.expirationTimestamp = expirationTimestamp;
        this.lastRegistrationTimestamp = lastRegistrationTimestamp;
        this.latestChannelCreationPingTimestamp = 0;
        if (preKey != null) {
            this.preKeyId = preKey.keyId;
            this.preKeyEncryptionPublicKey = preKey.encryptionPublicKey;
            this.preKeyExpirationTimestamp = preKey.expirationTimestamp;
        }
    }

    private OwnedDevice(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.uid = new UID(res.getBytes(UID_));
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.isCurrentDevice = res.getBoolean(IS_CURRENT_DEVICE);
        this.serializedDeviceCapabilities = res.getBytes(SERIALIZED_DEVICE_CAPABILITIES);
        this.displayName = res.getString(DISPLAY_NAME);
        this.expirationTimestamp = res.getLong(EXPIRATION_TIMESTAMP);
        if (res.wasNull()) {
            this.expirationTimestamp = null;
        }
        this.lastRegistrationTimestamp = res.getLong(LAST_REGISTRATION_TIMESTAMP);
        if (res.wasNull()) {
            this.lastRegistrationTimestamp = null;
        }
        this.latestChannelCreationPingTimestamp = res.getByte(LATEST_CHANNEL_CREATION_PING_TIMESTAMP);
        byte[] preKeyIdBytes = res.getBytes(PRE_KEY_ID);
        this.preKeyId = (preKeyIdBytes == null) ? null : new KeyId(preKeyIdBytes);
        byte[] preKeyEncodedPublicKeyBytes = res.getBytes(PRE_KEY_ENCRYPTION_PUBLIC_KEY);
        if (preKeyEncodedPublicKeyBytes != null) {
            try {
                this.preKeyEncryptionPublicKey = (EncryptionPublicKey) new Encoded(preKeyEncodedPublicKeyBytes).decodePublicKey();
            } catch (DecodingException ignored) {}
        }
        this.preKeyExpirationTimestamp = res.getLong(PRE_KEY_EXPIRATION_TIMESTAMP);
        if (res.wasNull()) {
            this.preKeyExpirationTimestamp = null;
        }
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB PRIMARY KEY, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    IS_CURRENT_DEVICE + " BIT NOT NULL, " +
                    SERIALIZED_DEVICE_CAPABILITIES + " BLOB DEFAULT NULL, " +
                    DISPLAY_NAME + " TEXT DEFAULT NULL, " +
                    EXPIRATION_TIMESTAMP + " INTEGER DEFAULT NULL, " +
                    LAST_REGISTRATION_TIMESTAMP + " INTEGER DEFAULT NULL, " +
                    LATEST_CHANNEL_CREATION_PING_TIMESTAMP + " BIGINT NOT NULL DEFAULT 0, " +
                    PRE_KEY_ID + " BLOB DEFAULT NULL, " +
                    PRE_KEY_ENCRYPTION_PUBLIC_KEY + " BLOB DEFAULT NULL, " +
                    PRE_KEY_EXPIRATION_TIMESTAMP + " BIGINT DEFAULT NULL, " +
                    "FOREIGN KEY (" + OWNED_IDENTITY + ") REFERENCES " + OwnedIdentity.TABLE_NAME + " (" + OwnedIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 27 && newVersion >= 27) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE owned_device ADD COLUMN `serialized_device_capabilities` BLOB DEFAULT NULL");
            }
            oldVersion = 27;
        }
        if (oldVersion < 35 && newVersion >= 35) {
            Logger.d("MIGRATING `owned_device` DATABASE FROM VERSION " + oldVersion + " TO 35");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE owned_device ADD COLUMN `display_name` TEXT DEFAULT NULL");
                statement.execute("ALTER TABLE owned_device ADD COLUMN `expiration_timestamp` INTEGER DEFAULT NULL");
                statement.execute("ALTER TABLE owned_device ADD COLUMN `last_registration_timestamp` INTEGER DEFAULT NULL");
            }
            oldVersion = 35;
        }
        if (oldVersion < 41 && newVersion >= 41) {
            Logger.d("MIGRATING `owned_device` DATABASE FROM VERSION " + oldVersion + " TO 41");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE owned_device ADD COLUMN `latest_channel_creation_ping_timestamp` BIGINT NOT NULL DEFAULT 0");
                statement.execute("ALTER TABLE owned_device ADD COLUMN `pre_key_id` BLOB DEFAULT NULL");
                statement.execute("ALTER TABLE owned_device ADD COLUMN `pre_key_encryption_public_key` BLOB DEFAULT NULL");
                statement.execute("ALTER TABLE owned_device ADD COLUMN `pre_key_expiration_timestamp` BIGINT DEFAULT NULL");
            }
            oldVersion = 41;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?, ?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBoolean(3, isCurrentDevice);
            statement.setBytes(4, serializedDeviceCapabilities);
            statement.setString(5, displayName);

            if (expirationTimestamp == null) {
                statement.setNull(6, Types.INTEGER);
            } else {
                statement.setLong(6, expirationTimestamp);
            }
            if (lastRegistrationTimestamp == null) {
                statement.setNull(7, Types.INTEGER);
            } else {
                statement.setLong(7, lastRegistrationTimestamp);
            }
            statement.setLong(8, latestChannelCreationPingTimestamp);


            if (preKeyId != null && preKeyEncryptionPublicKey != null && preKeyExpirationTimestamp != null) {
                statement.setBytes(9, preKeyId.getBytes());
                statement.setBytes(10, Encoded.of(preKeyEncryptionPublicKey).getBytes());
                statement.setLong(11, preKeyExpirationTimestamp);
            } else {
                statement.setBytes(9, null);
                statement.setBytes(10, null);
                statement.setNull(11, Types.BIGINT);
            }

            statement.executeUpdate();
            if (!isCurrentDevice) {
                commitHookBits |= HOOK_BIT_INSERTED_OTHER_DEVICE | HOOK_BIT_DEVICES_CHANGED;
                identityManagerSession.session.addSessionCommitListener(this);
            }
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UID_ + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_CAPABILITIES_UPDATED | HOOK_BIT_DEVICES_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }


    public static OwnedDevice get(IdentityManagerSession identityManagerSession, UID ownedDeviceUid) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                UID_ + " = ?;")) {
            statement.setBytes(1, ownedDeviceUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OwnedDevice(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }



    public static OwnedDevice getCurrentDeviceOfOwnedIdentity(IdentityManagerSession identityManagerSession, Identity identity) throws SQLException {
        if ((identity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                OWNED_IDENTITY + " = ? AND " +
                IS_CURRENT_DEVICE + " = 1;")) {
            statement.setBytes(1, identity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OwnedDevice(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static OwnedDevice[] getOtherDevicesOfOwnedIdentity(IdentityManagerSession identityManagerSession, Identity identity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                OWNED_IDENTITY + " = ? AND " +
                IS_CURRENT_DEVICE + " = 0;")) {
            statement.setBytes(1, identity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<OwnedDevice> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OwnedDevice(identityManagerSession, res));
                }
                return list.toArray(new OwnedDevice[0]);
            }
        }
    }

    public static List<OwnedDevice> getAllDevicesOfIdentity(IdentityManagerSession identityManagerSession, Identity identity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, identity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<OwnedDevice> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OwnedDevice(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public static List<OwnedDevice> getAllWithExpiredPreKey(IdentityManagerSession identityManagerSession, Identity ownedIdentity, long expirationTimestamp) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + PRE_KEY_EXPIRATION_TIMESTAMP + " < ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setLong(2, expirationTimestamp);
            try (ResultSet res = statement.executeQuery()) {
                List<OwnedDevice> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OwnedDevice(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public static UID[] getAllDeviceUidsOfIdentity(IdentityManagerSession identityManagerSession, Identity identity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT "  + UID_ + " FROM " + TABLE_NAME + " WHERE " +
                OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, identity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<UID> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new UID(res.getBytes(UID_)));
                }
                return list.toArray(new UID[0]);
            }
        }
    }

    public void setRawDeviceCapabilities(String[] rawDeviceCapabilities) throws SQLException {
        byte[] serializedDeviceCapabilities = ObvCapability.serializeRawDeviceCapabilities(rawDeviceCapabilities);
        if (Arrays.equals(serializedDeviceCapabilities, this.serializedDeviceCapabilities)) {
            // if the capabilities did not change, do not update/notify
            return;
        }

        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_DEVICE_CAPABILITIES + " = ? " +
                " WHERE " + UID_ + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, serializedDeviceCapabilities);
            statement.setBytes(2, this.uid.getBytes());
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.serializedDeviceCapabilities = serializedDeviceCapabilities;
            commitHookBits |= HOOK_BIT_CAPABILITIES_UPDATED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setDisplayName(String displayName) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + DISPLAY_NAME + " = ? " +
                " WHERE " + UID_ + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, displayName);
            statement.setBytes(2, this.uid.getBytes());
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.displayName = displayName;
            commitHookBits |= HOOK_BIT_DEVICES_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setLatestChannelCreationPingTimestamp(long timestamp) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + LATEST_CHANNEL_CREATION_PING_TIMESTAMP + " = ? " +
                " WHERE " + UID_ + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setLong(1, timestamp);
            statement.setBytes(2, this.uid.getBytes());
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.lastRegistrationTimestamp = timestamp;
        }
    }

    public void setTimestamps(Long expirationTimestamp, Long lastRegistrationTimestamp) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + EXPIRATION_TIMESTAMP + " = ?, " +
                LAST_REGISTRATION_TIMESTAMP + " = ? " +
                " WHERE " + UID_ + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            if (expirationTimestamp == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setLong(1, expirationTimestamp);
            }
            if (lastRegistrationTimestamp == null) {
                statement.setNull(2, Types.INTEGER);
            } else {
                statement.setLong(2, lastRegistrationTimestamp);
            }
            statement.setBytes(3, this.uid.getBytes());
            statement.setBytes(4, this.ownedIdentity.getBytes());
            statement.executeUpdate();
            this.expirationTimestamp = expirationTimestamp;
            this.lastRegistrationTimestamp = lastRegistrationTimestamp;
            commitHookBits |= HOOK_BIT_DEVICES_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setPreKey(PreKey preKey) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + PRE_KEY_ID + " = ?, " +
                PRE_KEY_ENCRYPTION_PUBLIC_KEY + " = ?, " +
                PRE_KEY_EXPIRATION_TIMESTAMP + " = ? " +
                " WHERE " + UID_ + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            boolean preKeyAddedOrRemoved;
            if (preKey == null) {
                statement.setNull(1, Types.BLOB);
                statement.setNull(2, Types.BLOB);
                statement.setNull(3, Types.BIGINT);
                preKeyAddedOrRemoved = this.preKeyId != null;
                this.preKeyId = null;
                this.preKeyEncryptionPublicKey = null;
                this.preKeyExpirationTimestamp = null;
            } else {
                statement.setBytes(1, preKey.keyId.getBytes());
                statement.setBytes(2, Encoded.of(preKey.encryptionPublicKey).getBytes());
                statement.setLong(3, preKey.expirationTimestamp);
                preKeyAddedOrRemoved = this.preKeyId == null;
                this.preKeyId = preKey.keyId;
                this.preKeyEncryptionPublicKey = preKey.encryptionPublicKey;
                this.preKeyExpirationTimestamp = preKey.expirationTimestamp;
            }
            statement.setBytes(4, uid.getBytes());
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.executeUpdate();
            if (preKeyAddedOrRemoved) {
                commitHookBits |= HOOK_BIT_DEVICES_CHANGED;
                identityManagerSession.session.addSessionCommitListener(this);
            }
        }
    }


    boolean channelCreationAlreadyInProgress = false;
    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED_OTHER_DEVICE = 0x1;
    private static final long HOOK_BIT_CAPABILITIES_UPDATED = 0x2;
    private static final long HOOK_BIT_DEVICES_CHANGED = 0x4;
    private static final long HOOK_BIT_DEVICE_PRE_KEY_ADDED_OR_REMOVED = 0x8;

    @Override
    public void wasCommitted() {
        // this notification is only caught in the ChannelManager, so as to create a new channel
        if ((commitHookBits & HOOK_BIT_INSERTED_OTHER_DEVICE) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE_DEVICE_UID_KEY, uid);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE_CHANNEL_CREATION_ALREADY_IN_PROGRESS_KEY, channelCreationAlreadyInProgress);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_CAPABILITIES_UPDATED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED, userInfo);
        }
        // this notification is propagated to the App
        if ((commitHookBits & HOOK_BIT_DEVICES_CHANGED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_OWNED_DEVICE_LIST_CHANGED_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_OWNED_DEVICE_LIST_CHANGED, userInfo);
        }
        commitHookBits = 0;
    }
}
