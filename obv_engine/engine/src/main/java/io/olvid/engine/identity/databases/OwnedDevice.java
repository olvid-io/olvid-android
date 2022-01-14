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
import java.util.List;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class OwnedDevice implements ObvDatabase {
    static final String TABLE_NAME = "owned_device";

    private final IdentityManagerSession identityManagerSession;

    private UID uid;
    static final String UID_ = "uid";
    private Identity identity;
    static final String IDENTITY = "identity";
    private boolean isCurrentDevice;
    static final String IS_CURRENT_DEVICE = "is_current_device";

    public UID getUid() {
        return uid;
    }

    public Identity getIdentity() {
        return identity;
    }

    public boolean isCurrentDevice() {
        return isCurrentDevice;
    }

    public OwnedIdentity getOwnedIdentity() throws SQLException {
        return OwnedIdentity.get(identityManagerSession, identity);
    }


    public static OwnedDevice createOtherDevice(IdentityManagerSession identityManagerSession, UID uid, Identity identity) {
        if (identity == null) {
            return null;
        }
        try {
            OwnedDevice ownedDevice = new OwnedDevice(identityManagerSession, uid, identity, false);
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
            OwnedDevice ownedDevice = new OwnedDevice(identityManagerSession, uid, identity, true);
            ownedDevice.insert();
            return ownedDevice;
        } catch (SQLException e) {
            return null;
        }
    }

    private OwnedDevice(IdentityManagerSession identityManagerSession, UID uid, Identity identity, boolean isCurrentDevice) {
        this.identityManagerSession = identityManagerSession;
        this.uid = uid;
        this.identity = identity;
        this.isCurrentDevice = isCurrentDevice;
    }

    private OwnedDevice(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.uid = new UID(res.getBytes(UID_));
        try {
            this.identity = Identity.of(res.getBytes(IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.isCurrentDevice = res.getBoolean(IS_CURRENT_DEVICE);
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB PRIMARY KEY, " +
                    IDENTITY + " BLOB NOT NULL, " +
                    IS_CURRENT_DEVICE + " BIT NOT NULL, " +
                    "FOREIGN KEY (" + IDENTITY + ") REFERENCES " + OwnedIdentity.TABLE_NAME + " (" + OwnedIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, identity.getBytes());
            statement.setBoolean(3, isCurrentDevice);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UID_ + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.executeUpdate();
        }
    }


    public static OwnedDevice get(IdentityManagerSession identityManagerSession, UID currentDeviceUid) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                UID_ + " = ?;")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
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
                IDENTITY + " = ? AND " +
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
                IDENTITY + " = ? AND " +
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
                IDENTITY + " = ?;")) {
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

    @Override
    public void wasCommitted() {
        // No notifications here
    }
}
