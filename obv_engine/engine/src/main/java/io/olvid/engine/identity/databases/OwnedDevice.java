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

package io.olvid.engine.identity.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
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

    public UID getUid() {
        return uid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public boolean isCurrentDevice() {
        return isCurrentDevice;
    }

    public OwnedIdentity getOwnedIdentityObject() throws SQLException {
        return OwnedIdentity.get(identityManagerSession, ownedIdentity);
    }

    public List<ObvCapability> getDeviceCapabilities() {
        return ObvCapability.deserializeDeviceCapabilities(serializedDeviceCapabilities);
    }

    public String[] getRawDeviceCapabilities() {
        return ObvCapability.deserializeRawDeviceCapabilities(serializedDeviceCapabilities);
    }

    public static OwnedDevice createOtherDevice(IdentityManagerSession identityManagerSession, UID uid, Identity identity) {
        if (identity == null) {
            return null;
        }
        try {
            OwnedDevice ownedDevice = new OwnedDevice(identityManagerSession, uid, identity, false, null);
            ownedDevice.insert();
            return ownedDevice;
        } catch (SQLException e) {
            return null;
        }
    }

    public static OwnedDevice createCurrentDevice(IdentityManagerSession identityManagerSession, Identity identity, PRNGService prng) {
        if (identity == null) {
            return null;
        }
        UID uid = new UID(prng);
        try {
            OwnedDevice ownedDevice = new OwnedDevice(identityManagerSession, uid, identity, true, null);
            ownedDevice.insert();
            return ownedDevice;
        } catch (SQLException e) {
            return null;
        }
    }

    private OwnedDevice(IdentityManagerSession identityManagerSession, UID uid, Identity ownedIdentity, boolean isCurrentDevice, byte[] serializedDeviceCapabilities) {
        this.identityManagerSession = identityManagerSession;
        this.uid = uid;
        this.ownedIdentity = ownedIdentity;
        this.isCurrentDevice = isCurrentDevice;
        this.serializedDeviceCapabilities = serializedDeviceCapabilities;
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
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB PRIMARY KEY, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    IS_CURRENT_DEVICE + " BIT NOT NULL, " +
                    SERIALIZED_DEVICE_CAPABILITIES + " BLOB DEFAULT NULL, " +
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
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBoolean(3, isCurrentDevice);
            statement.setBytes(4, serializedDeviceCapabilities);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UID_ + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_CAPABILITIES_UPDATED;
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

    public static OwnedDevice[] getAllDevicesOfIdentity(IdentityManagerSession identityManagerSession, Identity identity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                OWNED_IDENTITY + " = ?;")) {
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



    private long commitHookBits = 0;
    private static final long HOOK_BIT_CAPABILITIES_UPDATED = 0x2;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_CAPABILITIES_UPDATED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED, userInfo);
        }
        commitHookBits = 0;
    }
}
