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

package io.olvid.engine.networksend.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.UploadNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.networksend.datatypes.SendManagerSession;


public class OutboxMessage implements ObvDatabase {
    static final String TABLE_NAME = "outbox_message";

    private final SendManagerSession sendManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID uid;
    static final String UID_ = "uid";
    private UID uidFromServer;
    static final String UID_FROM_SERVER = "uid_from_server";
    private byte[] nonce;
    static final String NONCE = "nonce";
    private String server;
    static final String SERVER = "server";
    private EncryptedBytes encryptedContent;
    static final String ENCRYPTED_CONTENT = "encrypted_content";
    private boolean isApplicationMessage;
    static final String IS_APPLICATION_MESSAGE = "is_application_message";
    private boolean isVoipMessage;
    static final String IS_VOIP_MESSAGE = "is_voip_message";
    private EncryptedBytes encryptedExtendedContent;
    static final String ENCRYPTED_EXTENDED_CONTENT = "encrypted_extended_content";
    private long creationTimestamp;
    static final String CREATION_TIMESTAMP = "creation_timestamp";


    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getUid() {
        return uid;
    }

    public UID getUidFromServer() {
        return uidFromServer;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public String getServer() {
        return server;
    }

    public EncryptedBytes getEncryptedContent() {
        return encryptedContent;
    }

    public EncryptedBytes getEncryptedExtendedContent() {
        return encryptedExtendedContent;
    }

    public MessageHeader[] getHeaders() {
        return MessageHeader.getAll(sendManagerSession, ownedIdentity, uid);
    }

    public OutboxAttachment[] getAttachments() {
        return OutboxAttachment.getAll(sendManagerSession, ownedIdentity, uid);
    }

    public boolean isAcknowledged() {
        return uidFromServer != null;
    }

    public boolean isApplicationMessage() {
        return isApplicationMessage;
    }

    public boolean isVoipMessage() {
        return isVoipMessage;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    // region setters

    public void setUidFromServer(UID uidFromServer, byte[] nonce, long timestampFromServer) {
        if (this.uidFromServer != uidFromServer) {
            try (PreparedStatement statement = sendManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                    " SET " + UID_FROM_SERVER + " = ?, " +
                    NONCE + " = ? " +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " AND " + UID_ + " = ?;")) {
                statement.setBytes(1, (uidFromServer==null)?null:uidFromServer.getBytes());
                statement.setBytes(2, nonce);
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.setBytes(4, uid.getBytes());
                statement.executeUpdate();
                this.uidFromServer = uidFromServer;
                this.nonce = nonce;
                this.acknowledgedTimestampFromSever = timestampFromServer;
                if (timestampFromServer != 0) {
                    this.commitHookBits |= HOOK_BIT_ACKNOWLEDGED;
                }
                sendManagerSession.session.addSessionCommitListener(this);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    // endregion

    // region constructors

    public static OutboxMessage create(SendManagerSession sendManagerSession, Identity ownedIdentity, UID uid, String server, EncryptedBytes encryptedContent, EncryptedBytes encryptedExtendedContent, boolean isApplicationMessage, boolean isVoipMessage) {
        if (ownedIdentity == null || uid == null || server == null || encryptedContent == null) {
            return null;
        }
        try {
            OutboxMessage outboxMessage = new OutboxMessage(sendManagerSession, ownedIdentity, uid, server, encryptedContent, encryptedExtendedContent, isApplicationMessage, isVoipMessage);
            outboxMessage.insert();
            return outboxMessage;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private OutboxMessage(SendManagerSession sendManagerSession, ResultSet res) throws SQLException {
        this.sendManagerSession = sendManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.uid = new UID(res.getBytes(UID_));
        byte[] bytes = res.getBytes(UID_FROM_SERVER);
        this.uidFromServer = (bytes==null)?null:new UID(bytes);
        this.nonce = res.getBytes(NONCE);
        this.server = res.getString(SERVER);
        this.encryptedContent = new EncryptedBytes(res.getBytes(ENCRYPTED_CONTENT));
        this.isApplicationMessage = res.getBoolean(IS_APPLICATION_MESSAGE);
        this.isVoipMessage = res.getBoolean(IS_VOIP_MESSAGE);
        bytes = res.getBytes(ENCRYPTED_EXTENDED_CONTENT);
        this.encryptedExtendedContent = (bytes==null)?null:new EncryptedBytes(bytes);
        this.creationTimestamp = res.getLong(CREATION_TIMESTAMP);
    }

    private OutboxMessage(SendManagerSession sendManagerSession, Identity ownedIdentity, UID uid, String server, EncryptedBytes encryptedContent, EncryptedBytes encryptedExtendedContent, boolean isApplicationMessage, boolean isVoipMessage) {
        this.sendManagerSession = sendManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.uid = uid;
        this.uidFromServer = null;
        this.nonce = null;
        this.server = server;
        this.encryptedContent = encryptedContent;
        this.isApplicationMessage = isApplicationMessage;
        this.isVoipMessage = isVoipMessage;
        this.encryptedExtendedContent = encryptedExtendedContent;
        this.creationTimestamp = System.currentTimeMillis();
    }

    // endregion
    // region getter

    public static OutboxMessage get(SendManagerSession sendManagerSession, Identity ownedIdentity, UID uid) throws SQLException {
        if (ownedIdentity == null || uid == null) {
            return null;
        }
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OutboxMessage(sendManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static OutboxMessage[] getAll(SendManagerSession sendManagerSession) {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<OutboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OutboxMessage(sendManagerSession, res));
                }
                return list.toArray(new OutboxMessage[0]);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new OutboxMessage[0];
        }
    }

    public static OutboxMessage[] getAllForOwnedIdentity(SendManagerSession sendManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<OutboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OutboxMessage(sendManagerSession, res));
                }
                return list.toArray(new OutboxMessage[0]);
            }
        }
    }

    // endregion

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    UID_ + " BLOB NOT NULL, " +
                    UID_FROM_SERVER + " BLOB, " +
                    NONCE + " BLOB, " +
                    SERVER + " TEXT NOT NULL, " +
                    ENCRYPTED_CONTENT + " BLOB NOT NULL, " +
                    IS_APPLICATION_MESSAGE + " BIT NOT NULL, " +
                    IS_VOIP_MESSAGE + " BIT NOT NULL, " +
                    ENCRYPTED_EXTENDED_CONTENT + " BLOB, " +
                    CREATION_TIMESTAMP + " BIGINT NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + OWNED_IDENTITY + ", " + UID_ + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion <3 && newVersion >= 3) {
            Logger.d("MIGRATING `outbox_message` DATABASE FROM VERSION " + oldVersion + " TO 3\n!!!! THIS MIGRATION IS DESTRUCTIVE !!!!");
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS `outbox_message`;");
                statement.execute("CREATE TABLE IF NOT EXISTS outbox_message (" +
                        "uid BLOB PRIMARY KEY, " +
                        "uid_from_server BLOB, " +
                        "server TEXT NOT NULL, " +
                        "encrypted_content BLOB NOT NULL, " +
                        "proof_of_work_uid BLOB, " +
                        "proof_of_work_encoded_challenge BLOB, " +
                        "proof_of_work_encoded_solution BLOB);");
            }
            oldVersion = 3;
        }
        if (oldVersion <7 && newVersion >= 7) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE outbox_message ADD COLUMN is_application_message BIT NOT NULL DEFAULT 0");
            }
            oldVersion = 7;
        }
        if (oldVersion <15 && newVersion >= 15) {
            Logger.d("MIGRATING `outbox_message` DATABASE FROM VERSION " + oldVersion + " TO 15");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE outbox_message RENAME TO old_outbox_message");
                statement.execute("CREATE TABLE IF NOT EXISTS outbox_message (" +
                        "owned_identity BLOB NOT NULL, " +
                        "uid BLOB NOT NULL, " +
                        "uid_from_server BLOB, " +
                        "nonce BLOB, " +
                        "server TEXT NOT NULL, " +
                        "encrypted_content BLOB NOT NULL, " +
                        "proof_of_work_uid BLOB, " +
                        "proof_of_work_encoded_challenge BLOB, " +
                        "proof_of_work_encoded_solution BLOB," +
                        "is_application_message BIT NOT NULL," +
                        "CONSTRAINT PK_outbox_message PRIMARY KEY(owned_identity, uid));");
                statement.execute("INSERT INTO outbox_message SELECT i.identity, m.uid, m.uid_from_server, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', m.server, m.encrypted_content, m.proof_of_work_uid, m.proof_of_work_encoded_challenge, m.proof_of_work_encoded_solution, m.is_application_message FROM old_outbox_message AS m" +
                        " CROSS JOIN owned_identity AS i");
                statement.execute("DROP TABLE old_outbox_message");
            }
            oldVersion = 15;
        }
        if (oldVersion < 17 && newVersion >= 17) {
            Logger.d("MIGRATING `outbox_message` DATABASE FROM VERSION " + oldVersion + " TO 17");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE outbox_message RENAME TO old_outbox_message");
                statement.execute("CREATE TABLE IF NOT EXISTS outbox_message (" +
                        "owned_identity BLOB NOT NULL, " +
                        "uid BLOB NOT NULL, " +
                        "uid_from_server BLOB, " +
                        "nonce BLOB, " +
                        "server TEXT NOT NULL, " +
                        "encrypted_content BLOB NOT NULL, " +
                        "proof_of_work_uid BLOB, " +
                        "proof_of_work_encoded_challenge BLOB, " +
                        "proof_of_work_encoded_solution BLOB," +
                        "is_application_message BIT NOT NULL," +
                        "is_voip_message BIT NOT NULL," +
                        "CONSTRAINT PK_outbox_message PRIMARY KEY(owned_identity, uid));");
                statement.execute("INSERT INTO outbox_message SELECT owned_identity, uid, uid_from_server, nonce, server, encrypted_content, proof_of_work_uid, proof_of_work_encoded_challenge, proof_of_work_encoded_solution, is_application_message, 0  FROM old_outbox_message");
                statement.execute("DROP TABLE old_outbox_message");
            }
            oldVersion = 17;
        }
        if (oldVersion < 22 && newVersion >= 22) {
            Logger.d("MIGRATING `outbox_message` DATABASE FROM VERSION " + oldVersion + " TO 22");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE outbox_message ADD COLUMN `encrypted_extended_content` BLOB DEFAULT NULL");
            }
            oldVersion = 22;
        }
        if (oldVersion < 29 && newVersion >= 29) {
            Logger.d("MIGRATING `outbox_message` DATABASE FROM VERSION " + oldVersion + " TO 29");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE outbox_message RENAME TO old_outbox_message");
                statement.execute("CREATE TABLE outbox_message (" +
                        "owned_identity BLOB NOT NULL, " +
                        "uid BLOB NOT NULL, " +
                        "uid_from_server BLOB, " +
                        "nonce BLOB, " +
                        "server TEXT NOT NULL, " +
                        "encrypted_content BLOB NOT NULL, " +
                        "is_application_message BIT NOT NULL," +
                        "is_voip_message BIT NOT NULL," +
                        "encrypted_extended_content BLOB," +
                        "creation_timestamp BIGINT NOT NULL," +
                        "CONSTRAINT PK_outbox_message PRIMARY KEY(owned_identity, uid));");

                try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO outbox_message " +
                        "SELECT m.owned_identity, m.uid," +
                        " m.uid_from_server, m.nonce," +
                        " m.server, m.encrypted_content," +
                        " m.is_application_message, m.is_voip_message," +
                        " m.encrypted_extended_content, ? " +
                        " FROM old_outbox_message AS m")) {
                    preparedStatement.setLong(1, System.currentTimeMillis());
                    preparedStatement.executeUpdate();
                }

                statement.execute("DROP TABLE old_outbox_message");
            }
            oldVersion = 29;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES(?,?,?,?,?, ?,?,?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.setBytes(3, (uidFromServer==null)?null:uidFromServer.getBytes());
            statement.setBytes(4, nonce);
            statement.setString(5, server);

            statement.setBytes(6, encryptedContent.getBytes());
            statement.setBoolean(7, isApplicationMessage);
            statement.setBoolean(8, isVoipMessage);
            statement.setBytes(9, (encryptedExtendedContent==null)?null:encryptedExtendedContent.getBytes());
            statement.setLong(10, creationTimestamp);

            statement.executeUpdate();
            this.commitHookBits |= HOOK_BIT_INSERT;
            sendManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        // First, cascade delete MessageHeader and OutboxAttachment
        MessageHeader.deleteAll(sendManagerSession, ownedIdentity, uid);
        OutboxAttachment.deleteAll(sendManagerSession, ownedIdentity, uid);
        // Then actually delete the OutboxMessage
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
        }
    }


    public interface NewOutboxMessageListener {
        void newMessageToSend(Identity ownedIdentity, UID messageUid);
    }

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERT = 0x1;
    private static final long HOOK_BIT_ACKNOWLEDGED = 0x2;

    private long acknowledgedTimestampFromSever;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERT) != 0) {
            if (sendManagerSession.newOutboxMessageListener != null) {
                sendManagerSession.newOutboxMessageListener.newMessageToSend(ownedIdentity, uid);
            }
        }
        if ((commitHookBits & HOOK_BIT_ACKNOWLEDGED) != 0) {
            for (OutboxAttachment outboxAttachment: getAttachments()) {
                outboxAttachment.messageIsAcknowledged();
            }
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_UID_KEY, uid);
            userInfo.put(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER, acknowledgedTimestampFromSever);
            if (sendManagerSession.notificationPostingDelegate != null) {
                sendManagerSession.notificationPostingDelegate.postNotification(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED, userInfo);
            }
        }
        commitHookBits = 0;
    }

}
