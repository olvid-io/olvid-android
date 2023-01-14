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

package io.olvid.engine.networkfetch.databases;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.DecryptedApplicationMessage;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;


public class InboxMessage implements ObvDatabase {
    static final String TABLE_NAME = "inbox_message";

    private final FetchManagerSession fetchManagerSession;

    private static final long DELETED_MESSAGE_RETENTION_TIME_MILLIS = 600_000L; // keep deleted messages uids for 10 minutes
    private static long lastExpungeTimestamp = System.currentTimeMillis();
    private static final HashMap<IdentityAndUid, Long> deletedMessageUids = new HashMap<>();

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID uid;
    static final String UID_ = "uid";
    private EncryptedBytes wrappedKey;
    static final String WRAPPED_KEY = "wrapped_key";
    private EncryptedBytes encryptedContent;
    static final String ENCRYPTED_CONTENT = "encrypted_content";
    private boolean markedForDeletion;
    static final String MARKED_FOR_DELETION = "marked_for_deletion";
    private long serverTimestamp;
    static final String SERVER_TIMESTAMP = "server_timestamp";
    private byte[] payload;
    static final String PAYLOAD = "payload";
    private Identity fromIdentity;
    static final String FROM_IDENTITY = "from_identity";
    private long downloadTimestamp;
    static final String DOWNLOAD_TIMESTAMP = "download_timestamp";
    private long localDownloadTimestamp;
    static final String LOCAL_DOWNLOAD_TIMESTAMP = "local_download_timestamp";
    private boolean hasExtendedPayload;
    static final String HAS_EXTENDED_PAYLOAD = "has_extended_payload";
    private AuthEncKey extendedPayloadKey;
    static final String EXTENDED_PAYLOAD_KEY = "extended_payload_key";
    private byte[] extendedPayload;
    static final String EXTENDED_PAYLOAD = "extended_payload";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getUid() {
        return uid;
    }

    public EncryptedBytes getWrappedKey() {
        return wrappedKey;
    }

    public EncryptedBytes getEncryptedContent() {
        return encryptedContent;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    public byte[] getPayload() {
        return payload;
    }

    public Identity getFromIdentity() {
        return fromIdentity;
    }

    public long getDownloadTimestamp() {
        return downloadTimestamp;
    }

    public AuthEncKey getExtendedPayloadKey() {
        return extendedPayloadKey;
    }

    public byte[] getExtendedPayload() {
        return extendedPayload;
    }


    public InboxAttachment[] getAttachments() {
        return InboxAttachment.getAll(fetchManagerSession, ownedIdentity, uid);
    }

    public NetworkReceivedMessage getNetworkReceivedMessage() {
        if ((encryptedContent == null) || (wrappedKey == null)) {
            return null;
        }
        NetworkReceivedMessage.Header header = new NetworkReceivedMessage.Header(ownedIdentity, wrappedKey);
        return new NetworkReceivedMessage(uid, serverTimestamp, encryptedContent, header, hasExtendedPayload);
    }

    public DecryptedApplicationMessage getDecryptedApplicationMessage() {
        if ((payload == null) || (fromIdentity == null)) {
            return null;
        }
        return new DecryptedApplicationMessage(uid, payload, fromIdentity, ownedIdentity, serverTimestamp, downloadTimestamp, localDownloadTimestamp);
    }

    public static UID computeUniqueUid(Identity ownedIdentity, UID messageUid) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] input = new byte[ownedIdentity.getBytes().length + UID.UID_LENGTH];
        System.arraycopy(ownedIdentity.getBytes(), 0, input, 0, ownedIdentity.getBytes().length);
        System.arraycopy(messageUid.getBytes(), 0, input, ownedIdentity.getBytes().length, UID.UID_LENGTH);
        return new UID(sha256.digest(input));
    }

    public boolean canBeDeleted() {
        if (! markedForDeletion) {
            return false;
        }
        for (InboxAttachment inboxAttachment: getAttachments()) {
            if (!inboxAttachment.isMarkedForDeletion()) {
                return false;
            }
        }
        return true;
    }

    // region setters

    public void markForDeletion() {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + MARKED_FOR_DELETION + " = 1 " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
            this.markedForDeletion = true;
        } catch (SQLException ignored) {}
    }

    public void setPayloadAndFromIdentity(byte[] payload, Identity fromIdentity, AuthEncKey extendedPayloadKey) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + PAYLOAD + " = ?, " +
                FROM_IDENTITY + " = ?, " +
                EXTENDED_PAYLOAD_KEY + " = ? " +
                "WHERE " + OWNED_IDENTITY + " = ? " +
                " AND "  + UID_ + " = ?;")) {
            statement.setBytes(1, payload);
            statement.setBytes(2, fromIdentity.getBytes());
            statement.setBytes(3, (extendedPayloadKey == null) ? null: Encoded.of(extendedPayloadKey).getBytes());
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setBytes(5, uid.getBytes());
            statement.executeUpdate();
            this.payload = payload;
            this.fromIdentity = fromIdentity;
            this.extendedPayloadKey = extendedPayloadKey;
            commitHookBits |= HOOK_BIT_PAYLOAD_AND_FROM_IDENTITY_SET;
            fetchManagerSession.session.addSessionCommitListener(this);
        } catch (SQLException e) {
            // nothing
        }
    }

    public void setExtendedPayload(byte[] extendedPayload) throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + EXTENDED_PAYLOAD + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, extendedPayload);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, uid.getBytes());
            statement.executeUpdate();
            this.extendedPayload = extendedPayload;
            commitHookBits |= HOOK_BIT_EXTENDED_PAYLOAD_SET;
            fetchManagerSession.session.addSessionCommitListener(this);
        }
    }

    public static void clearExtendedPayload(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + HAS_EXTENDED_PAYLOAD + " = 0, " +
                EXTENDED_PAYLOAD_KEY + " = NULL, " +
                EXTENDED_PAYLOAD + " = NULL " +
                "WHERE " + OWNED_IDENTITY + " = ? " +
                " AND "  + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            // nothing
        }
    }

    // endregion
    // region constructors


    public static InboxMessage create(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid, EncryptedBytes encryptedContent, EncryptedBytes wrappedKey, long serverTimestamp, long downloadTimestamp, long localDownloadTimestamp, boolean hasExtendedContent) {
        if (messageUid == null || ownedIdentity == null || encryptedContent == null || wrappedKey == null) {
            return null;
        }
        if (deletedMessageUids.containsKey(new IdentityAndUid(ownedIdentity, messageUid))) {
            // we listed again a message that was deleted, just to be sure, create a pendingDelete
            try {
                PendingDeleteFromServer.create(fetchManagerSession, ownedIdentity, messageUid);
            } catch (Exception ignored) { }
            return null;
        }
        try {
            InboxMessage inboxMessage = new InboxMessage(fetchManagerSession, ownedIdentity, messageUid, encryptedContent, wrappedKey, serverTimestamp, downloadTimestamp, localDownloadTimestamp, hasExtendedContent);
            inboxMessage.insert();
            return inboxMessage;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private InboxMessage(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid, EncryptedBytes encryptedContent, EncryptedBytes wrappedKey, long serverTimestamp, long downloadTimestamp,  long localDownloadTimestamp, boolean hasExtendedContent) {
        this.fetchManagerSession = fetchManagerSession;
        this.uid =  messageUid;
        this.ownedIdentity = ownedIdentity;
        this.encryptedContent = encryptedContent;
        this.wrappedKey = wrappedKey;
        this.serverTimestamp = serverTimestamp;
        this.payload = null;
        this.fromIdentity = null;
        this.downloadTimestamp = downloadTimestamp;
        this.localDownloadTimestamp = localDownloadTimestamp;
        this.hasExtendedPayload = hasExtendedContent;
        this.extendedPayloadKey = null;
        this.extendedPayload = null;
    }

    private InboxMessage(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.uid = new UID(res.getBytes(UID_));
        byte[] bytes = res.getBytes(WRAPPED_KEY);
        this.wrappedKey = (bytes == null)?null:new EncryptedBytes(bytes);
        bytes = res.getBytes(ENCRYPTED_CONTENT);
        this.encryptedContent = (bytes == null)?null:new EncryptedBytes(bytes);
        this.markedForDeletion = res.getBoolean(MARKED_FOR_DELETION);
        this.serverTimestamp = res.getLong(SERVER_TIMESTAMP);
        this.payload = res.getBytes(PAYLOAD);
        byte[] fromIdentityBytes = res.getBytes(FROM_IDENTITY);
        if (fromIdentityBytes == null) {
            this.fromIdentity = null;
        } else {
            try {
                this.fromIdentity = Identity.of(fromIdentityBytes);
            } catch (DecodingException e) {
                e.printStackTrace();
            }
        }
        this.downloadTimestamp = res.getLong(DOWNLOAD_TIMESTAMP);
        this.localDownloadTimestamp = res.getLong(LOCAL_DOWNLOAD_TIMESTAMP);
        this.hasExtendedPayload = res.getBoolean(HAS_EXTENDED_PAYLOAD);
        try {
            this.extendedPayloadKey = (AuthEncKey) new Encoded(res.getBytes(EXTENDED_PAYLOAD_KEY)).decodeSymmetricKey();
        } catch (Exception e) {
            this.extendedPayloadKey = null;
        }
        this.extendedPayload = res.getBytes(EXTENDED_PAYLOAD);
    }

    // endregion
    // region getters

    public static InboxMessage get(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID uid) {
        if (uid == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new InboxMessage(fetchManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static InboxMessage[] getAllForOwnedIdentity(FetchManagerSession fetchManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list.toArray(new InboxMessage[0]);
            }
        }
    }

    public static InboxMessage[] getUnprocessedMessages(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + PAYLOAD + " IS NULL " +
                        " AND " + MARKED_FOR_DELETION + " = 0;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list.toArray(new InboxMessage[0]);
            }
        } catch (SQLException e) {
            return new InboxMessage[0];
        }
    }

    public static InboxMessage[] getDecryptedMessages(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + PAYLOAD + " NOT NULL " +
                        " AND " + MARKED_FOR_DELETION + " = 0;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list.toArray(new InboxMessage[0]);
            }
        } catch (SQLException e) {
            return new InboxMessage[0];
        }
    }

    public static InboxMessage[] getMarkedForDeletionMessages(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + MARKED_FOR_DELETION + " = 1;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list.toArray(new InboxMessage[0]);
            }
        } catch (SQLException e) {
            return new InboxMessage[0];
        }
    }

    public static InboxMessage[] getExtendedPayloadMessages(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + EXTENDED_PAYLOAD + " IS NOT NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list.toArray(new InboxMessage[0]);
            }
        } catch (SQLException e) {
            return new InboxMessage[0];
        }
    }

    public static InboxMessage[] getMissingExtendedPayloadMessages(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + EXTENDED_PAYLOAD_KEY + " IS NOT NULL" +
                        " AND " + EXTENDED_PAYLOAD + " IS NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list.toArray(new InboxMessage[0]);
            }
        } catch (SQLException e) {
            return new InboxMessage[0];
        }
    }


    // endregion
    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                            OWNED_IDENTITY + " BLOB NOT NULL, " +
                            UID_ + " BLOB NOT NULL, " +
                            WRAPPED_KEY + " BLOB NOT NULL, " +
                            ENCRYPTED_CONTENT + " BLOB NOT NULL, " +
                            MARKED_FOR_DELETION + " BIT NOT NULL, " +

                            SERVER_TIMESTAMP + " BIGINT NOT NULL, " +
                            PAYLOAD + " BLOB, " +
                            FROM_IDENTITY + " BLOB, " +
                            DOWNLOAD_TIMESTAMP + " BIGINT NOT NULL, " +
                            LOCAL_DOWNLOAD_TIMESTAMP + " BIGINT NOT NULL, " +
                            HAS_EXTENDED_PAYLOAD + " BIT NOT NULL, " +
                            EXTENDED_PAYLOAD_KEY + " BLOB, " +
                            EXTENDED_PAYLOAD + " BLOB, " +
                            " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + OWNED_IDENTITY + ", " + UID_ + "));"
            );
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 2 && newVersion >= 2) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 2");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE `inbox_message` ADD COLUMN `server_timestamp` BIGINT NOT NULL DEFAULT 0");
            }
            oldVersion = 2;
        }
        if (oldVersion < 4 && newVersion >= 4) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 4\n!!!! THIS MIGRATION IS DESTRUCTIVE !!!!");
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS `inbox_message`;");
                statement.execute("CREATE TABLE IF NOT EXISTS inbox_message (" +
                                "uid BLOB PRIMARY KEY, " +
                                "to_identity BLOB NOT NULL, " +
                                "wrapped_key BLOB NOT NULL, " +
                                "encrypted_content BLOB NOT NULL, " +
                                "marked_for_deletion BIT NOT NULL, " +

                                "server_timestamp BIGINT NOT NULL, " +
                                "payload BLOB, " +
                                "from_identity BLOB);"
                );
            }
            oldVersion = 4;
        }
        if (oldVersion < 15 && newVersion >= 15) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 15");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message RENAME TO old_inbox_message");
                statement.execute("CREATE TABLE IF NOT EXISTS inbox_message (" +
                                " owned_identity BLOB NOT NULL, " +
                                " uid BLOB NOT NULL, " +
                                " wrapped_key BLOB NOT NULL, " +
                                " encrypted_content BLOB NOT NULL, " +
                                " marked_for_deletion BIT NOT NULL, " +

                                " server_timestamp BIGINT NOT NULL, " +
                                " payload BLOB, " +
                                " from_identity BLOB, " +
                                " CONSTRAINT PK_inbox_message PRIMARY KEY(owned_identity, uid));");
                statement.execute("INSERT INTO inbox_message SELECT to_identity, uid, wrapped_key, encrypted_content, marked_for_deletion, server_timestamp, payload, from_identity FROM old_inbox_message");
                statement.execute("DROP TABLE old_inbox_message");
            }
            oldVersion = 15;
        }
        if (oldVersion < 17 && newVersion >= 17) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 17");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message RENAME TO old_inbox_message");
                statement.execute("CREATE TABLE IF NOT EXISTS inbox_message (" +
                        " owned_identity BLOB NOT NULL, " +
                        " uid BLOB NOT NULL, " +
                        " wrapped_key BLOB NOT NULL, " +
                        " encrypted_content BLOB NOT NULL, " +
                        " marked_for_deletion BIT NOT NULL, " +

                        " server_timestamp BIGINT NOT NULL, " +
                        " payload BLOB, " +
                        " from_identity BLOB, " +
                        " download_timestamp BIGINT NOT NULL, " +
                        " CONSTRAINT PK_inbox_message PRIMARY KEY(owned_identity, uid));");
                statement.execute("INSERT INTO inbox_message SELECT owned_identity, uid, wrapped_key, encrypted_content, marked_for_deletion, server_timestamp, payload, from_identity, server_timestamp FROM old_inbox_message");
                statement.execute("DROP TABLE old_inbox_message");
            }
            oldVersion = 17;
        }
        if (oldVersion < 19 && newVersion >= 19) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 19");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message ADD COLUMN `local_download_timestamp` BIGINT NOT NULL DEFAULT 0");
            }
            oldVersion = 19;
        }
        if (oldVersion < 22 && newVersion >= 22) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 22");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message ADD COLUMN `has_extended_payload` BIT NOT NULL DEFAULT 0");
                statement.execute("ALTER TABLE inbox_message ADD COLUMN `extended_payload_key` BLOB DEFAULT NULL");
                statement.execute("ALTER TABLE inbox_message ADD COLUMN `extended_payload` BLOB DEFAULT NULL");
            }
            oldVersion = 22;
        }
    }


    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES(?,?,?,?,?, ?,?,?,?,?, ?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.setBytes(3, wrappedKey.getBytes());
            statement.setBytes(4, encryptedContent.getBytes());
            statement.setBoolean(5, markedForDeletion);

            statement.setLong(6, serverTimestamp);
            statement.setBytes(7, payload);
            statement.setBytes(8, (fromIdentity==null)?null:fromIdentity.getBytes());
            statement.setLong(9, downloadTimestamp);
            statement.setLong(10, localDownloadTimestamp);

            statement.setBoolean(11, hasExtendedPayload);
            statement.setBytes(12, (extendedPayloadKey==null)?null:Encoded.of(extendedPayloadKey).getBytes());
            statement.setBytes(13, extendedPayload);

            statement.executeUpdate();
            this.commitHookBits |= HOOK_BIT_INSERT;
            fetchManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        // before inserting a new deletedMessageUid, sometimes expunge all old entries
        if (System.currentTimeMillis() > lastExpungeTimestamp + DELETED_MESSAGE_RETENTION_TIME_MILLIS) {
            lastExpungeTimestamp = System.currentTimeMillis();
            try {
                long timestamp = System.currentTimeMillis() - DELETED_MESSAGE_RETENTION_TIME_MILLIS;
                List<IdentityAndUid> toDelete = new ArrayList<>();
                for (Map.Entry<IdentityAndUid, Long> entry : deletedMessageUids.entrySet()) {
                    if (entry.getValue() < timestamp) {
                        toDelete.add(entry.getKey());
                    }
                }
                Logger.d("Expunging " + toDelete.size() + " deletedMessageUids");
                for (IdentityAndUid key : toDelete) {
                    deletedMessageUids.remove(key);
                }
            } catch (Exception ignored) { }
        }

        deletedMessageUids.put(new IdentityAndUid(ownedIdentity, uid), System.currentTimeMillis());

        // first, cascade delete the attachments, then delete the message itself.
        for (InboxAttachment inboxAttachment : getAttachments()) {
            try {
                inboxAttachment.deleteAttachmentFile();
            } catch (IOException e) {
                throw new SQLException("Error deleting attachment file.");
            }
            inboxAttachment.delete();
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND "  + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion
    // region hooks

    public interface InboxMessageListener {
        void messageWasDownloaded(NetworkReceivedMessage networkReceivedMessage);
        void messageDecrypted(Identity ownedIdentity, UID uid);
    }

    public interface ExtendedPayloadListener {
        void messageHasExtendedPayloadToDownload(Identity ownedIdentity, UID uid);
        void messageExtendedPayloadDownloaded(Identity ownedIdentity, UID uid, byte[] extendedPayload);
    }

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERT = 0x1;
    private static final long HOOK_BIT_PAYLOAD_AND_FROM_IDENTITY_SET = 0x2;
    private static final long HOOK_BIT_EXTENDED_PAYLOAD_SET = 0x4;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERT) != 0) {
            if (fetchManagerSession.inboxMessageListener != null) {
                fetchManagerSession.inboxMessageListener.messageWasDownloaded(getNetworkReceivedMessage());
            }
        }
        if ((commitHookBits & HOOK_BIT_PAYLOAD_AND_FROM_IDENTITY_SET) != 0) {
            if (fetchManagerSession.inboxMessageListener != null) {
                fetchManagerSession.inboxMessageListener.messageDecrypted(ownedIdentity, uid);
                if (extendedPayloadKey != null && fetchManagerSession.extendedPayloadListener != null) {
                    fetchManagerSession.extendedPayloadListener.messageHasExtendedPayloadToDownload(ownedIdentity, uid);
                }
            }
        }
        if ((commitHookBits & HOOK_BIT_EXTENDED_PAYLOAD_SET) != 0) {
            if (fetchManagerSession.extendedPayloadListener != null) {
                fetchManagerSession.extendedPayloadListener.messageExtendedPayloadDownloaded(ownedIdentity, uid, extendedPayload);
            }
        }
        commitHookBits = 0;
    }

    // endregion
}
