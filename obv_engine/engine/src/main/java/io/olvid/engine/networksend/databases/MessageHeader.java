/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.networksend.datatypes.SendManagerSession;


public class MessageHeader implements ObvDatabase {
    static final String TABLE_NAME = "message_header";

    private final SendManagerSession sendManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID messageUid;
    static final String MESSAGE_UID = "message_uid";
    private UID deviceUid;
    static final String DEVICE_UID = "device_uid";
    private Identity toIdentity;
    static final String TO_IDENTITY = "to_identity";
    private EncryptedBytes wrappedKey;
    static final String WRAPPED_KEY = "wrapped_key";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public UID getDeviceUid() {
        return deviceUid;
    }

    public Identity getToIdentity() {
        return toIdentity;
    }

    public EncryptedBytes getWrappedKey() {
        return wrappedKey;
    }


    public static MessageHeader create(SendManagerSession sendManagerSession, Identity ownedIdentity, UID messageUid, UID deviceUid, Identity toIdentity, EncryptedBytes wrappedKey) {
        if (ownedIdentity == null || messageUid == null || deviceUid == null || toIdentity == null || wrappedKey == null) {
            return null;
        }
        try {
            MessageHeader messageHeader = new MessageHeader(sendManagerSession, ownedIdentity, messageUid, deviceUid, toIdentity, wrappedKey);
            messageHeader.insert();
            return messageHeader;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    private MessageHeader(SendManagerSession sendManagerSession, Identity ownedIdentity, UID messageUid, UID deviceUid, Identity toIdentity, EncryptedBytes wrappedKey) {
        this.sendManagerSession = sendManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.deviceUid = deviceUid;
        this.toIdentity = toIdentity;
        this.wrappedKey = wrappedKey;
    }

    private MessageHeader(SendManagerSession sendManagerSession, ResultSet res) throws SQLException {
        this.sendManagerSession = sendManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            Logger.x(e);
        }
        this.messageUid = new UID(res.getBytes(MESSAGE_UID));
        this.deviceUid = new UID(res.getBytes(DEVICE_UID));
        try {
            this.toIdentity = Identity.of(res.getBytes(TO_IDENTITY));
        } catch (DecodingException e) {
            Logger.x(e);
        }
        this.wrappedKey = new EncryptedBytes(res.getBytes(WRAPPED_KEY));
    }


    static MessageHeader[] getAll(SendManagerSession sendManagerSession, Identity ownedIdentity, UID messageUid) {
        if (messageUid == null) {
            return null;
        }
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<MessageHeader> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new MessageHeader(sendManagerSession, res));
                }
                return list.toArray(new MessageHeader[0]);
            }
        } catch (SQLException e) {
            return new MessageHeader[0];
        }
    }


    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    MESSAGE_UID + " BLOB NOT NULL, " +
                    DEVICE_UID + " BLOB NOT NULL, " +
                    TO_IDENTITY + " BLOB NOT NULL, " +
                    WRAPPED_KEY + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + OWNED_IDENTITY + ", " + MESSAGE_UID + ", " + DEVICE_UID + ", " + TO_IDENTITY + "), " +
                    "FOREIGN KEY (" + OWNED_IDENTITY + ", " + MESSAGE_UID + ") REFERENCES " + OutboxMessage.TABLE_NAME + "(" + OutboxMessage.OWNED_IDENTITY + ", " + OutboxMessage.UID_ + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 3 && newVersion >= 3) {
            Logger.d("MIGRATING `message_header` DATABASE FROM VERSION " + oldVersion + " TO 3\n!!!! THIS MIGRATION IS DESTRUCTIVE !!!!");
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS `message_header`;");
                statement.execute("CREATE TABLE IF NOT EXISTS message_header (" +
                        "message_uid BLOB NOT NULL, " +
                        "device_uid BLOB NOT NULL, " +
                        "to_identity BLOB NOT NULL, " +
                        "wrapped_key BLOB NOT NULL, " +
                        "CONSTRAINT PK_message_header PRIMARY KEY(message_uid, device_uid, to_identity), " +
                        "FOREIGN KEY (message_uid) REFERENCES outbox_message(uid));");
            }
            oldVersion = 3;
        }
        if (oldVersion <15 && newVersion >= 15) {
            Logger.d("MIGRATING `message_header` DATABASE FROM VERSION " + oldVersion + " TO 15");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE message_header RENAME TO old_message_header");
                statement.execute("CREATE TABLE IF NOT EXISTS message_header (" +
                        "owned_identity BLOB NOT NULL, " +
                        "message_uid BLOB NOT NULL, " +
                        "device_uid BLOB NOT NULL, " +
                        "to_identity BLOB NOT NULL, " +
                        "wrapped_key BLOB NOT NULL, " +
                        "CONSTRAINT PK_message_header PRIMARY KEY(owned_identity, message_uid, device_uid, to_identity), " +
                        "FOREIGN KEY (owned_identity, message_uid) REFERENCES outbox_message(owned_identity, uid));");
                statement.execute("INSERT INTO message_header SELECT i.identity, h.message_uid, h.device_uid, h.to_identity, h.wrapped_key FROM old_message_header AS h" +
                        " CROSS JOIN owned_identity AS i");
                statement.execute("DROP TABLE old_message_header");
            }
            oldVersion = 15;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setBytes(3, deviceUid.getBytes());
            statement.setBytes(4, toIdentity.getBytes());
            statement.setBytes(5, wrappedKey.getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + DEVICE_UID + " = ? " +
                " AND " + TO_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setBytes(3, deviceUid.getBytes());
            statement.setBytes(4, toIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    static void deleteAll(SendManagerSession sendManagerSession, Identity ownedIdentity, UID messageUid) throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void wasCommitted() {
        // No hooks
    }
}
