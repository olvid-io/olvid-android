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

package io.olvid.engine.networksend.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networksend.datatypes.SendManagerSession;


public class ReturnReceipt implements ObvDatabase {
    static final String TABLE_NAME = "return_receipt";

    private final SendManagerSession sendManagerSession;


    private long id; // Autoincrement primary key
    static final String ID = "id";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private UID[] contactDeviceUids;
    static final String CONTACT_DEVICE_UIDS = "contact_device_uids";
    private int status;
    static final String STATUS = "status";
    private byte[] nonce;
    static final String NONCE = "nonce";
    private AuthEncKey key;
    static final String KEY = "key";
    private Integer attachmentNumber;
    static final String ATTACHMENT_NUMBER = "attachment_number";


    public long getId() {
        return id;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public UID[] getContactDeviceUids() {
        return contactDeviceUids;
    }

    public int getStatus() {
        return status;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public AuthEncKey getKey() {
        return key;
    }

    public Integer getAttachmentNumber() {
        return attachmentNumber;
    }

    public static ReturnReceipt create(SendManagerSession sendManagerSession, Identity ownedIdentity, Identity contactIdentity, UID[] contactDeviceUids, int status, byte[] nonce, AuthEncKey key, Integer attachmentNumber) {
        if ((ownedIdentity == null) || (contactIdentity == null) || (nonce == null) || (key == null)) {
            return null;
        }
        try {
            ReturnReceipt returnReceipt = new ReturnReceipt(sendManagerSession, ownedIdentity, contactIdentity, contactDeviceUids, status, nonce, key, attachmentNumber);
            returnReceipt.insert();
            return returnReceipt;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ReturnReceipt(SendManagerSession sendManagerSession, Identity ownedIdentity, Identity contactIdentity, UID[] contactDeviceUids, int status, byte[] nonce, AuthEncKey key, Integer attachmentNumber) {
        this.sendManagerSession = sendManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.contactIdentity = contactIdentity;
        this.contactDeviceUids = contactDeviceUids;
        this.status = status;
        this.nonce = nonce;
        this.key = key;
        this.attachmentNumber = attachmentNumber;
    }

    private ReturnReceipt(SendManagerSession sendManagerSession, ResultSet res) throws SQLException {
        this.sendManagerSession = sendManagerSession;
        this.id = res.getLong(ID);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
            this.key = (AuthEncKey) new Encoded(res.getBytes(KEY)).decodeSymmetricKey();
            this.contactDeviceUids = new Encoded(res.getBytes(CONTACT_DEVICE_UIDS)).decodeUidArray();
        } catch (DecodingException|ClassCastException e) {
            e.printStackTrace();
        }
        this.status = res.getInt(STATUS);
        this.nonce = res.getBytes(NONCE);
        this.attachmentNumber = res.getInt(ATTACHMENT_NUMBER);
        if (res.wasNull()) {
            this.attachmentNumber = null;
        }
    }


    public static ReturnReceipt get(SendManagerSession sendManagerSession, long returnReceiptId) throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + ID + " = ?;")) {
            statement.setLong(1, returnReceiptId);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ReturnReceipt(sendManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }


    public static ReturnReceipt[] getMany(SendManagerSession sendManagerSession, Long[] ids) throws SQLException {
        if (ids == null) {
            return null;
        }

        // build a ?,? string
        int count = ids.length;
        StringBuilder sb = new StringBuilder(count * 2);
        while (count-- > 1) {
            sb.append("?,");
        }
        sb.append("?");

        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + ID + " IN (" + sb + ");")) {
            for (int i = 0; i < ids.length; i++) {
                statement.setLong(i + 1, ids[i]);
            }
            try (ResultSet res = statement.executeQuery()) {
                List<ReturnReceipt> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ReturnReceipt(sendManagerSession, res));
                }
                return list.toArray(new ReturnReceipt[0]);
            }
        }
    }


    public static ReturnReceipt[] getAll(SendManagerSession sendManagerSession) throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ReturnReceipt> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ReturnReceipt(sendManagerSession, res));
                }
                return list.toArray(new ReturnReceipt[0]);
            }
        }
    }

    public static void deleteAllForOwnedIdentity(SendManagerSession sendManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }


    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    ID + " INTEGER PRIMARY KEY, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    CONTACT_DEVICE_UIDS + " BLOB NOT NULL, " +
                    STATUS + " INTEGER NOT NULL, " +
                    NONCE + " BLOB NOT NULL, " +
                    KEY + " BLOB NOT NULL, " +
                    ATTACHMENT_NUMBER + " INTEGER);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 26 && newVersion >= 26) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE return_receipt ADD COLUMN attachment_number INTEGER DEFAULT NULL");
            }
            oldVersion = 26;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME +
                "(" + OWNED_IDENTITY + ", " +
                CONTACT_IDENTITY + ", " +
                CONTACT_DEVICE_UIDS + ", " +
                STATUS + ", " +
                NONCE + ", " +
                KEY + ", " +
                ATTACHMENT_NUMBER + ") VALUES (?,?,?,?,?, ?,?);", Statement.RETURN_GENERATED_KEYS)) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, Encoded.of(contactDeviceUids).getBytes());
            statement.setInt(4, status);
            statement.setBytes(5, nonce);
            statement.setBytes(6, Encoded.of(key).getBytes());
            if (attachmentNumber != null) {
                statement.setInt(7, attachmentNumber);
            } else {
                statement.setNull(7, Types.INTEGER);
            }
            statement.executeUpdate();
            try (ResultSet res = statement.getGeneratedKeys()) {
                if (res.next()) {
                    id = res.getLong(1);
                    this.commitHookBits |= HOOK_BIT_INSERT;
                    sendManagerSession.session.addSessionCommitListener(this);
                }
            }
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ID + " = ?;")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    // region hooks

    public interface NewReturnReceiptListener {
        void newReturnReceipt(String server, Identity ownedIdentity, long id);
    }

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERT = 0x1;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERT) != 0) {
            if (sendManagerSession.newReturnReceiptListener != null) {
                sendManagerSession.newReturnReceiptListener.newReturnReceipt(contactIdentity.getServer(), ownedIdentity, id);
            }
        }
    }

    // endregion
}
