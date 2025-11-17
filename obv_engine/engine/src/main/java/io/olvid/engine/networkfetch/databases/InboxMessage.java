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

package io.olvid.engine.networkfetch.databases;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
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
    private final UID uid;
    static final String UID_ = "uid";
    private final EncryptedBytes wrappedKey;
    static final String WRAPPED_KEY = "wrapped_key";
    private final EncryptedBytes encryptedContent;
    static final String ENCRYPTED_CONTENT = "encrypted_content";
    private boolean markedForDeletion;
    static final String MARKED_FOR_DELETION = "marked_for_deletion";
    private final long serverTimestamp;
    static final String SERVER_TIMESTAMP = "server_timestamp";
    private byte[] payload;
    static final String PAYLOAD = "payload";
    private Identity fromIdentity;
    static final String FROM_IDENTITY = "from_identity";
    private final long downloadTimestamp;
    static final String DOWNLOAD_TIMESTAMP = "download_timestamp";
    private final long localDownloadTimestamp;
    static final String LOCAL_DOWNLOAD_TIMESTAMP = "local_download_timestamp";
    private final boolean hasExtendedPayload;
    static final String HAS_EXTENDED_PAYLOAD = "has_extended_payload";
    private AuthEncKey extendedPayloadKey;
    static final String EXTENDED_PAYLOAD_KEY = "extended_payload_key";
    private byte[] extendedPayload;
    static final String EXTENDED_PAYLOAD = "extended_payload";
    private UID fromDeviceUid;
    static final String FROM_DEVICE_UID = "from_device_uid";
    private boolean onHold;
    static final String ON_HOLD = "on_hold";


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
        return new DecryptedApplicationMessage(uid, payload, fromIdentity, fromDeviceUid, ownedIdentity, serverTimestamp, downloadTimestamp, localDownloadTimestamp);
    }

    public static UID computeUniqueUid(Identity ownedIdentity, UID messageUid, boolean markAsListed) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] input = new byte[ownedIdentity.getBytes().length + UID.UID_LENGTH + 1];
        System.arraycopy(ownedIdentity.getBytes(), 0, input, 0, ownedIdentity.getBytes().length);
        System.arraycopy(messageUid.getBytes(), 0, input, ownedIdentity.getBytes().length, UID.UID_LENGTH);
        input[ownedIdentity.getBytes().length + UID.UID_LENGTH] = markAsListed ? (byte) 0x01 : (byte) 0x00;
        return new UID(sha256.digest(input));
    }

    public boolean canBeDeleted() {
        if (!markedForDeletion) {
            return false;
        }
        for (InboxAttachment inboxAttachment : getAttachments()) {
            if (!inboxAttachment.isMarkedForDeletion()) {
                return false;
            }
        }
        return true;
    }

    // region setters

    public void markForDeletion() {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.markForDeletion",
                "UPDATE " + TABLE_NAME +
                " SET " + MARKED_FOR_DELETION + " = 1 " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
            this.markedForDeletion = true;
        } catch (SQLException ignored) {
        }
    }

    public void markAsOnHold() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.markAsOnHold",
                "UPDATE " + TABLE_NAME +
                " SET " + ON_HOLD + " = 1 " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
            this.onHold = true;
        }
    }

    public void setPayloadAndFromIdentity(byte[] payload, Identity fromIdentity, UID fromDeviceUid, AuthEncKey extendedPayloadKey, InboxAttachment[] attachments) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.setPayloadAndFromIdentity",
                "UPDATE " + TABLE_NAME +
                " SET " + PAYLOAD + " = ?, " +
                FROM_IDENTITY + " = ?, " +
                FROM_DEVICE_UID + " = ?, " +
                EXTENDED_PAYLOAD_KEY + " = ? " +
                "WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, payload);
            statement.setBytes(2, fromIdentity.getBytes());
            statement.setBytes(3, (fromDeviceUid == null) ? null : fromDeviceUid.getBytes());
            statement.setBytes(4, (extendedPayloadKey == null) ? null : Encoded.of(extendedPayloadKey).getBytes());
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.setBytes(6, uid.getBytes());
            statement.executeUpdate();
            this.payload = payload;
            this.fromIdentity = fromIdentity;
            this.fromDeviceUid = fromDeviceUid;
            this.extendedPayloadKey = extendedPayloadKey;
            attachmentsToNotify = attachments;
            commitHookBits |= HOOK_BIT_PAYLOAD_AND_FROM_IDENTITY_SET;
            fetchManagerSession.session.addSessionCommitListener(this);
        } catch (SQLException e) {
            // nothing
        }
    }

    public void setFromIdentityForMissingPreKeyContact(Identity fromIdentity) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.setFromIdentityForMissingPreKeyContact",
                "UPDATE " + TABLE_NAME +
                " SET " + FROM_IDENTITY + " = ? " +
                "WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, fromIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, uid.getBytes());
            statement.executeUpdate();
            this.fromIdentity = fromIdentity;
        } catch (SQLException e) {
            // nothing
        }
    }

    public void setExtendedPayload(byte[] extendedPayload) throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.setExtendedPayload",
                "UPDATE " + TABLE_NAME +
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
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.clearExtendedPayload",
                "UPDATE " + TABLE_NAME +
                " SET " + HAS_EXTENDED_PAYLOAD + " = 0, " +
                EXTENDED_PAYLOAD_KEY + " = NULL, " +
                EXTENDED_PAYLOAD + " = NULL " +
                "WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
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
            fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeDeletedFromServer(ownedIdentity, messageUid);
            return null;
        }
        try {
            InboxMessage inboxMessage = new InboxMessage(fetchManagerSession, ownedIdentity, messageUid, encryptedContent, wrappedKey, serverTimestamp, downloadTimestamp, localDownloadTimestamp, hasExtendedContent);
            inboxMessage.insert();
            return inboxMessage;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    private InboxMessage(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid, EncryptedBytes encryptedContent, EncryptedBytes wrappedKey, long serverTimestamp, long downloadTimestamp, long localDownloadTimestamp, boolean hasExtendedContent) {
        this.fetchManagerSession = fetchManagerSession;
        this.uid = messageUid;
        this.ownedIdentity = ownedIdentity;
        this.encryptedContent = encryptedContent;
        this.wrappedKey = wrappedKey;
        this.markedForDeletion = false;
        this.serverTimestamp = serverTimestamp;
        this.payload = null;
        this.fromIdentity = null;
        this.fromDeviceUid = null;
        this.downloadTimestamp = downloadTimestamp;
        this.localDownloadTimestamp = localDownloadTimestamp;
        this.hasExtendedPayload = hasExtendedContent;
        this.extendedPayloadKey = null;
        this.extendedPayload = null;
        this.onHold = false;
    }

    private InboxMessage(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            Logger.x(e);
        }
        this.uid = new UID(res.getBytes(UID_));
        byte[] bytes = res.getBytes(WRAPPED_KEY);
        this.wrappedKey = (bytes == null) ? null : new EncryptedBytes(bytes);
        bytes = res.getBytes(ENCRYPTED_CONTENT);
        this.encryptedContent = (bytes == null) ? null : new EncryptedBytes(bytes);
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
                Logger.x(e);
            }
        }
        byte[] fromDeviceUidBytes = res.getBytes(FROM_DEVICE_UID);
        if (fromDeviceUidBytes == null || fromDeviceUidBytes.length != UID.UID_LENGTH) {
            this.fromDeviceUid = null;
        } else {
            this.fromDeviceUid = new UID(fromDeviceUidBytes);
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
        this.onHold = res.getBoolean(ON_HOLD);
    }

    // endregion
    // region getters

    public static InboxMessage get(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID uid) {
        if (uid == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.get",
                "SELECT * FROM " + TABLE_NAME +
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
            Logger.x(e);
            return null;
        }
    }

    public static boolean exists(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID uid) {
        if (uid == null) {
            return false;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.exists",
                "SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                return res.next();
            }
        } catch (SQLException e) {
            Logger.x(e);
            return false;
        }
    }

    public static InboxMessage[] getAllForOwnedIdentity(FetchManagerSession fetchManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getAllForOwnedIdentity",
                "SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
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


    // this method only returns truly unprocessed messages, not PreKey messages without a contact
    public static InboxMessage[] getUnprocessedMessagesForOwnedIdentity(FetchManagerSession fetchManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getUnprocessedMessagesForOwnedIdentity",
                "SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ?" +
                " AND " + PAYLOAD + " IS NULL " +
                " AND " + FROM_IDENTITY + " IS NULL " +
                " AND " + MARKED_FOR_DELETION + " = 0" +
                " ORDER BY " + SERVER_TIMESTAMP + " ASC;")) {
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

    // this method return unprocessed messages, but also PreKey messages where the contact was not yet a contact
    public static InboxMessage[] getUnprocessedMessages(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getUnprocessedMessages",
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + PAYLOAD + " IS NULL " +
                        " AND " + MARKED_FOR_DELETION + " = 0" +
                        " ORDER BY " + SERVER_TIMESTAMP + " ASC;")) {
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

    public static List<InboxMessage> getPendingPreKeyMessages(FetchManagerSession fetchManagerSession, Identity ownedIdentity, Identity contactIdentity) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getPendingPreKeyMessages",
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + PAYLOAD + " IS NULL " +
                        " AND " + OWNED_IDENTITY + " = ? " +
                        " AND " + FROM_IDENTITY + " = ? " +
                        " AND " + MARKED_FOR_DELETION + " = 0 " +
                        " ORDER BY " + SERVER_TIMESTAMP + " ASC;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list;
            }
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public static List<InboxMessage> getExpiredPendingPreKeyMessages(FetchManagerSession fetchManagerSession, long timestamp) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getExpiredPendingPreKeyMessages",
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + PAYLOAD + " IS NULL " +
                        " AND " + FROM_IDENTITY + " IS NOT NULL " +
                        " AND " + LOCAL_DOWNLOAD_TIMESTAMP + " < ?;")) {
            statement.setLong(1, timestamp);
            try (ResultSet res = statement.executeQuery()) {
                List<InboxMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxMessage(fetchManagerSession, res));
                }
                return list;
            }
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public static InboxMessage[] getDecryptedMessages(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getDecryptedMessages",
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + PAYLOAD + " NOT NULL " +
                        " AND " + MARKED_FOR_DELETION + " = 0" +
                        " AND " + ON_HOLD + " = 0" +
                        " ORDER BY " + SERVER_TIMESTAMP + " ASC;")) {
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
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getMarkedForDeletionMessages",
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
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getExtendedPayloadMessages",

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
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.getMissingExtendedPayloadMessages",
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
                            FROM_DEVICE_UID + " BLOB, " +
                            ON_HOLD + " BIT NOT NULL, " +
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
        if (oldVersion < 38 && newVersion >= 38) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 38");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message ADD COLUMN `marked_as_listed_on_server` BIT NOT NULL DEFAULT 0");
            }
            oldVersion = 38;
        }
        if (oldVersion < 40 && newVersion >= 40) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 40");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message DROP COLUMN `marked_as_listed_on_server`");
            }
            oldVersion = 40;
        }
        if (oldVersion < 43 && newVersion >= 43) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 43");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message ADD COLUMN `from_device_uid` BLOB DEFAULT NULL");
            }
            oldVersion = 43;
        }
        if (oldVersion < 45 && newVersion >= 45) {
            Logger.d("MIGRATING `inbox_message` DATABASE FROM VERSION " + oldVersion + " TO 45");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_message ADD COLUMN `on_hold` BIT NOT NULL DEFAULT 0");
            }
            oldVersion = 45;
        }
    }


    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES(?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.setBytes(3, wrappedKey.getBytes());
            statement.setBytes(4, encryptedContent.getBytes());
            statement.setBoolean(5, markedForDeletion);

            statement.setLong(6, serverTimestamp);
            statement.setBytes(7, payload);
            statement.setBytes(8, (fromIdentity == null) ? null : fromIdentity.getBytes());
            statement.setLong(9, downloadTimestamp);
            statement.setLong(10, localDownloadTimestamp);

            statement.setBoolean(11, hasExtendedPayload);
            statement.setBytes(12, (extendedPayloadKey == null) ? null : Encoded.of(extendedPayloadKey).getBytes());
            statement.setBytes(13, extendedPayload);
            statement.setBytes(14, fromDeviceUid == null ? null : fromDeviceUid.getBytes());
            statement.setBoolean(15, onHold);

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
            } catch (Exception ignored) {
            }
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
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("InboxMessage.delete",
                "DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + UID_ + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion
    // region hooks

    public interface InboxMessageListener {
        void messageWasDownloaded(NetworkReceivedMessage networkReceivedMessage);
        void messageDecrypted(InboxMessage inboxMessage, InboxAttachment[] attachments);
    }

    public interface ExtendedPayloadListener {
        void messageHasExtendedPayloadToDownload(Identity ownedIdentity, UID uid);
        void messageExtendedPayloadDownloaded(Identity ownedIdentity, UID uid, byte[] extendedPayload);
    }

    public interface MarkAsListedAndDeleteOnServerListener {
        void messageCanBeMarkedAsListedOnServer(Identity ownedIdentity, UID messageUid);
        void messageCanBeDeletedFromServer(Identity ownedIdentity, UID messageUid);
    }


    private long commitHookBits = 0;
    private InboxAttachment[] attachmentsToNotify;
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
                fetchManagerSession.inboxMessageListener.messageDecrypted(this, attachmentsToNotify);
                if (extendedPayloadKey != null && fetchManagerSession.extendedPayloadListener != null) {
                    fetchManagerSession.extendedPayloadListener.messageHasExtendedPayloadToDownload(ownedIdentity, uid);
                }
            }
            // for application messages, we always mark as listed on server, even if there are no attachments --> this way we do not rely on the app properly processing the message to avoid relisting
            if (fetchManagerSession.markAsListedAndDeleteOnServerListener != null) {
                fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeMarkedAsListedOnServer(ownedIdentity, uid);
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
