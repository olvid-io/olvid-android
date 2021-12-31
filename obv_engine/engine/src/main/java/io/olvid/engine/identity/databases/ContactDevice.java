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
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;


@SuppressWarnings("FieldMayBeFinal")
public class ContactDevice implements ObvDatabase {
    static final String TABLE_NAME = "contact_device";

    private final IdentityManagerSession identityManagerSession;

    private UID uid;
    static final String UID_ = "uid";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";

    public UID getUid() {
        return uid;
    }

    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public static ContactDevice create(IdentityManagerSession identityManagerSession, UID uid, Identity contactIdentity, Identity ownedIdentity) {
        if ((uid == null) || (contactIdentity == null) || (ownedIdentity == null)) {
            return null;
        }
        try {
            ContactDevice contactDevice = new ContactDevice(identityManagerSession, uid, contactIdentity, ownedIdentity);
            contactDevice.insert();
            return contactDevice;
        } catch (SQLException e) {
            return null;
        }
    }

    private ContactDevice(IdentityManagerSession identityManagerSession, UID uid, Identity contactIdentity, Identity ownedIdentity) {
        this.identityManagerSession = identityManagerSession;
        this.uid = uid;
        this.contactIdentity = contactIdentity;
        this.ownedIdentity = ownedIdentity;
    }

    private ContactDevice(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.uid = new UID(res.getBytes(UID_));
        try {
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB NOT NULL, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + UID_ + ", " + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + "), " +
                    "FOREIGN KEY (" + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + ") REFERENCES " + ContactIdentity.TABLE_NAME + " (" + ContactIdentity.CONTACT_IDENTITY + ", " + ContactIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM contact_device AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM contact_identity " +
                        " WHERE identity = p.contact_identity" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_INSERTED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + UID_ + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }


    public static ContactDevice get(IdentityManagerSession identityManagerSession, UID contactDeviceUid, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                UID_ + " = ? AND " +
                CONTACT_IDENTITY + " = ? AND " +
                OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, contactDeviceUid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactDevice(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static ContactDevice[] getAll(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                CONTACT_IDENTITY + " = ? AND " +
                OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactDevice> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactDevice(identityManagerSession, res));
                }
                return list.toArray(new ContactDevice[0]);
            }
        }
    }

    public static ContactDevice[] getAll(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactDevice> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactDevice(identityManagerSession, res));
                }
                return list.toArray(new ContactDevice[0]);
            }
        }
    }

    // region setters

    public static void deleteAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_DEVICE_UID_KEY, uid);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY, contactIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE, userInfo);
        }
        commitHookBits = 0;
    }
}
