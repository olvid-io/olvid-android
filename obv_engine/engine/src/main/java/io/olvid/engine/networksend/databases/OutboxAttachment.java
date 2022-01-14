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

package io.olvid.engine.networksend.databases;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Chunk;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.datatypes.notifications.UploadNotifications;

public class OutboxAttachment implements ObvDatabase {
    static final String TABLE_NAME = "outbox_attachment";

    private final SendManagerSession sendManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID messageUid;
    static final String MESSAGE_UID = "message_uid";
    private int attachmentNumber;
    static final String ATTACHMENT_NUMBER = "attachment_number";
    private String url; // this is a relative path to the attachment file
    static final String URL = "url";
    private boolean deleteAfterSend;
    static final String DELETE_AFTER_SEND = "delete_after_send";
    private long attachmentLength;
    static final String ATTACHMENT_LENGTH = "attachment_length";
    private AuthEncKey key;
    static final String KEY = "key";
    private int acknowledgedChunkCount;
    static final String ACKNOWLEDGED_CHUNK_COUNT = "acknowledged_chunk_count";
    private boolean acknowledged;
    static final String ACKNOWLEDGED = "acknowledged";
    private int ciphertextChunkLength;
    static final String CIPHERTEXT_CHUNK_LENGTH = "ciphertext_chunk_length";
    private boolean cancelExternallyRequested;
    static final String CANCEL_EXTERNALLY_REQUESTED = "cancel_externally_requested";
    private String chunkUploadPrivateUrls;
    static final String CHUNK_UPLOAD_PRIVATE_URLS = "chunk_upload_private_urls";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public int getAttachmentNumber() {
        return attachmentNumber;
    }

    public String getUrl() {
        return url;
    }

    public boolean shouldBeDeletedAfterSend() {
        return deleteAfterSend;
    }

    public long getAttachmentLength() {
        return attachmentLength;
    }

    public AuthEncKey getKey() {
        return key;
    }

    public int getAcknowledgedChunkCount() {
        return acknowledgedChunkCount;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public int getCiphertextChunkLength() {
        return ciphertextChunkLength;
    }

    public boolean isCancelExternallyRequested() {
        return cancelExternallyRequested;
    }

    public String[] getChunkUploadPrivateUrls() {
        if (chunkUploadPrivateUrls == null) {
            return new String[0];
        }
        return chunkUploadPrivateUrls.split("¦", -1);
    }

    // region computed properties

    public static UID computeUniqueUid(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] input = new byte[ownedIdentity.getBytes().length + UID.UID_LENGTH + Encoded.INT_ENCODING_LENGTH + Encoded.ENCODED_HEADER_LENGTH];
        System.arraycopy(ownedIdentity.getBytes(), 0, input, 0, ownedIdentity.getBytes().length);
        System.arraycopy(messageUid.getBytes(), 0, input, ownedIdentity.getBytes().length, UID.UID_LENGTH);
        System.arraycopy(Encoded.of(attachmentNumber).getBytes(), 0, input, ownedIdentity.getBytes().length + UID.UID_LENGTH, Encoded.INT_ENCODING_LENGTH + Encoded.ENCODED_HEADER_LENGTH);
        return new UID(sha256.digest(input));
    }

    private int attachmentChunkLength = 0;

    public int getCleartextChunkLength() {
        if (attachmentChunkLength == 0) {
            AuthEnc authEnc = Suite.getAuthEnc(key);
            attachmentChunkLength = Chunk.lengthOfInnerDataFromLengthOfEncodedChunk(authEnc.plaintextLengthFromCiphertextLength(ciphertextChunkLength));
        }
        return attachmentChunkLength;
    }

    private int numberOfChunks = 0;

    public int getNumberOfChunks() {
        if (numberOfChunks == 0) {
            numberOfChunks = 1 + (int) (((attachmentLength - 1) / getCleartextChunkLength()));
        }
        return numberOfChunks;
    }

    private long ciphertextLength = 0;

    public long getCiphertextLength() {
        if (ciphertextLength == 0) {
            AuthEnc authEnc = Suite.getAuthEnc(key);
            int lastChunkLength = (int) (attachmentLength - (getNumberOfChunks()-1)*((long) getCleartextChunkLength()));
            // the ciphertext is a number of full chunks, plus the encrypted length of the lastChunk
            ciphertextLength =  (getNumberOfChunks()-1)*((long) ciphertextChunkLength) + authEnc.ciphertextLengthFromPlaintextLength(Chunk.lengthOfEncodedChunkFromLengthOfInnerData(lastChunkLength));
        }
        return ciphertextLength;
    }

    public long getRemainingByteCountToSend() {
        long remaining = getCiphertextLength() - ((long)ciphertextChunkLength) * acknowledgedChunkCount;
        if (remaining < 0) {
            return 0;
        } else {
            return remaining;
        }
    }

    public long getPriority() {
        return getRemainingByteCountToSend();
    }

    // endregion
    // region setters

    public void setCancelExternallyRequested() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                CANCEL_EXTERNALLY_REQUESTED + " = 1 " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            statement.executeUpdate();
            this.cancelExternallyRequested = true;
            commitHookBits |= HOOK_BIT_CANCEL_REQUESTED;
            sendManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setCancelProcessed() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                ACKNOWLEDGED + " = 1 " +
                " WHERE " + CANCEL_EXTERNALLY_REQUESTED + " = 1 " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND "  + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            statement.executeUpdate();
            this.acknowledged = true;
        }
    }


    public void setAcknowledgedChunkCount(int acknowledgedChunkCount) {
        if (acknowledgedChunkCount < this.acknowledgedChunkCount) {
            return;
        }

        String sqlQueryString = "UPDATE " + TABLE_NAME + " SET ";
        if (acknowledgedChunkCount > this.acknowledgedChunkCount) {
            commitHookBits |= HOOK_BIT_PROGRESS;
            sendManagerSession.session.addSessionCommitListener(this);
        }
        if (acknowledgedChunkCount == this.getNumberOfChunks()) {
            sqlQueryString += ACKNOWLEDGED + " = 1, ";
            commitHookBits |= HOOK_BIT_FINISHED;
            sendManagerSession.session.addSessionCommitListener(this);
        }
        sqlQueryString += ACKNOWLEDGED_CHUNK_COUNT + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;";

        try (PreparedStatement statement = sendManagerSession.session.prepareStatement(sqlQueryString)) {
            statement.setLong(1, acknowledgedChunkCount);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, messageUid.getBytes());
            statement.setInt(4, attachmentNumber);
            statement.executeUpdate();
            if (acknowledgedChunkCount == this.getNumberOfChunks()) {
                this.acknowledged = true;
            }
            this.acknowledgedChunkCount = acknowledgedChunkCount;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setChunkUploadPrivateUrls(String[] chunkUploadPrivateUrls) {
        String serialized;
        if (chunkUploadPrivateUrls == null || chunkUploadPrivateUrls.length == 0) {
            serialized = null;
        } else {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String chunkUploadPrivateUrl:chunkUploadPrivateUrls) {
                if (!first) {
                    sb.append("¦");
                }
                first = false;
                sb.append(chunkUploadPrivateUrl);
            }
            serialized = sb.toString();
        }
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " + CHUNK_UPLOAD_PRIVATE_URLS + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setString(1, serialized);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, messageUid.getBytes());
            statement.setInt(4, attachmentNumber);
            statement.executeUpdate();
            this.chunkUploadPrivateUrls = serialized;
        } catch (SQLException ignored) {}
    }

    // endregion

    // region constructors

    public static OutboxAttachment create(SendManagerSession session, Identity ownedIdentity, UID messageUid, int attachmentNumber, String url, boolean deleteAfterSend, long attachmentLength, AuthEncKey key) {
        if (ownedIdentity == null || messageUid == null || url == null || key == null) {
            return null;
        }
        try {
            OutboxAttachment outboxAttachment = new OutboxAttachment(session, ownedIdentity, messageUid, attachmentNumber, url, deleteAfterSend, attachmentLength, key);
            outboxAttachment.insert();
            return outboxAttachment;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private OutboxAttachment(SendManagerSession sendManagerSession, Identity ownedIdentity, UID messageUid, int attachmentNumber, String url, boolean deleteAfterSend, long attachmentLength, AuthEncKey key) {
        this.sendManagerSession = sendManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.attachmentNumber = attachmentNumber;
        this.url = url;
        this.deleteAfterSend = deleteAfterSend;
        this.attachmentLength = attachmentLength;
        this.key = key;
        this.acknowledgedChunkCount = 0;
        this.acknowledged = false;
        this.ciphertextChunkLength = Constants.DEFAULT_ATTACHMENT_CHUNK_LENGTH;
        this.cancelExternallyRequested = false;
        this.chunkUploadPrivateUrls = null;
    }

    private OutboxAttachment(SendManagerSession sendManagerSession, ResultSet res) throws SQLException {
        this.sendManagerSession = sendManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.messageUid = new UID(res.getBytes(MESSAGE_UID));
        this.attachmentNumber = res.getInt(ATTACHMENT_NUMBER);
        this.url = res.getString(URL);
        this.deleteAfterSend = res.getBoolean(DELETE_AFTER_SEND);
        this.attachmentLength = res.getLong(ATTACHMENT_LENGTH);
        try {
            this.key = (AuthEncKey) new Encoded(res.getBytes(KEY)).decodeSymmetricKey();
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.acknowledgedChunkCount = res.getInt(ACKNOWLEDGED_CHUNK_COUNT);
        this.acknowledged = res.getBoolean(ACKNOWLEDGED);
        this.ciphertextChunkLength = res.getInt(CIPHERTEXT_CHUNK_LENGTH);
        this.cancelExternallyRequested = res.getBoolean(CANCEL_EXTERNALLY_REQUESTED);
        this.chunkUploadPrivateUrls = res.getString(CHUNK_UPLOAD_PRIVATE_URLS);
    }

    // endregion
    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    MESSAGE_UID + " BLOB NOT NULL, " +
                    ATTACHMENT_NUMBER + " INT NOT NULL, " +
                    URL + " TEXT NOT NULL, " +
                    DELETE_AFTER_SEND + " BIT NOT NULL, " +
                    ATTACHMENT_LENGTH + " BIGINT NOT NULL, " +
                    KEY + " BLOB NOT NULL, " +
                    ACKNOWLEDGED_CHUNK_COUNT + " INT NOT NULL, " +
                    ACKNOWLEDGED + " BIT NOT NULL, " +
                    CIPHERTEXT_CHUNK_LENGTH + " INT NOT NULL, " +
                    CANCEL_EXTERNALLY_REQUESTED + " BIT NOT NULL, " +
                    CHUNK_UPLOAD_PRIVATE_URLS + " TEXT, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + OWNED_IDENTITY + ", " + MESSAGE_UID + ", " + ATTACHMENT_NUMBER + "), " +
                    "FOREIGN KEY (" + OWNED_IDENTITY + ", " + MESSAGE_UID + ") REFERENCES " + OutboxMessage.TABLE_NAME + "(" + OutboxMessage.OWNED_IDENTITY + ", " + OutboxMessage.UID_ + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion <13 && newVersion >= 13) {
            Logger.d("MIGRATING `outbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 13");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE outbox_attachment ADD COLUMN chunk_upload_private_urls TEXT DEFAULT NULL;");
            }
            oldVersion = 13;
        }
        if (oldVersion <15 && newVersion >= 15) {
            Logger.d("MIGRATING `outbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 15");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE outbox_attachment RENAME TO old_outbox_attachment");
                statement.execute("CREATE TABLE IF NOT EXISTS outbox_attachment (" +
                        "owned_identity BLOB NOT NULL, " +
                        "message_uid BLOB NOT NULL, " +
                        "attachment_number INT NOT NULL, " +
                        "url TEXT NOT NULL, " +
                        "delete_after_send BIT NOT NULL, " +
                        "attachment_length BIGINT NOT NULL, " +
                        "key BLOB NOT NULL, " +
                        "acknowledged_chunk_count INT NOT NULL, " +
                        "acknowledged BIT NOT NULL, " +
                        "ciphertext_chunk_length INT NOT NULL, " +
                        "cancel_externally_requested BIT NOT NULL, " +
                        "chunk_upload_private_urls TEXT, " +
                        "CONSTRAINT PK_outbox_attachment PRIMARY KEY(owned_identity, message_uid, attachment_number), " +
                        "FOREIGN KEY (owned_identity, message_uid) REFERENCES outbox_message(owned_identity, uid));");
                statement.execute("INSERT INTO outbox_attachment SELECT i.identity, a.message_uid, a.attachment_number, a.url, a.delete_after_send, a.attachment_length, a.key, a.acknowledged_chunk_count, a.acknowledged, a.ciphertext_chunk_length, a.cancel_externally_requested, a.chunk_upload_private_urls FROM old_outbox_attachment AS a" +
                        " CROSS JOIN owned_identity AS i");
                statement.execute("DROP TABLE old_outbox_attachment");
            }
            oldVersion = 15;
        }
        if (oldVersion < 16 && newVersion >= 16) {
            Logger.d("MIGRATING `outbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 16");
            Pattern pattern = Pattern.compile("^.*/(fyles/[0-9A-F]+$)");
            try (Statement statement = session.createStatement();
                 PreparedStatement updateStatement = session.prepareStatement(
                         "UPDATE outbox_attachment " +
                                 " SET url = ? " +
                                 " WHERE owned_identity = ? " +
                                 " AND message_uid = ? " +
                                 " AND attachment_number = ?")) {
                try (ResultSet res = statement.executeQuery("SELECT * FROM outbox_attachment")) {
                    while (res.next()) {
                        String oldUrl = res.getString("url");
                        Matcher m = pattern.matcher(oldUrl);
                        String newUrl = "";
                        if (m.find()) {
                            newUrl = m.group(1);
                        }
                        updateStatement.setString(1, newUrl);
                        updateStatement.setBytes(2, res.getBytes("owned_identity"));
                        updateStatement.setBytes(3, res.getBytes("message_uid"));
                        updateStatement.setInt(4, res.getInt("attachment_number"));
                        updateStatement.executeUpdate();
                    }
                }
            }
            oldVersion = 16;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            statement.setString(4, url);
            statement.setBoolean(5, deleteAfterSend);

            statement.setLong(6, attachmentLength);
            statement.setBytes(7, Encoded.of(key).getBytes());
            statement.setInt(8, acknowledgedChunkCount);
            statement.setBoolean(9, acknowledged);
            statement.setInt(10, ciphertextChunkLength);

            statement.setBoolean(11, cancelExternallyRequested);
            statement.setString(12, chunkUploadPrivateUrls);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
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

    // endregion
    // region getters

    static OutboxAttachment[] getAll(SendManagerSession sendManagerSession, Identity ownedIdentity, UID messageUid) {
        if (ownedIdentity == null || messageUid == null) {
            return null;
        }
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + OWNED_IDENTITY + " = ? " +
                        " AND " + MESSAGE_UID + " = ? " +
                        " ORDER BY " + ATTACHMENT_NUMBER + " ASC;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<OutboxAttachment> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OutboxAttachment(sendManagerSession, res));
                }
                return list.toArray(new OutboxAttachment[0]);
            }
        } catch (SQLException e) {
            return new OutboxAttachment[0];
        }
    }

    public static OutboxAttachment get(SendManagerSession sendManagerSession, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws SQLException {
        if (ownedIdentity == null || messageUid == null) {
            return null;
        }
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OutboxAttachment(sendManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static OutboxAttachment[] getAllToCancel(SendManagerSession sendManagerSession) {
        try (PreparedStatement statement = sendManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + CANCEL_EXTERNALLY_REQUESTED + " = 1 " +
                        " AND " + ACKNOWLEDGED + " = 0")) {
            try (ResultSet res = statement.executeQuery()) {
                List<OutboxAttachment> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OutboxAttachment(sendManagerSession, res));
                }
                return list.toArray(new OutboxAttachment[0]);
            }
        } catch (SQLException e) {
            return new OutboxAttachment[0];
        }
    }

    // endregion

    public interface OutboxAttachmentCanBeSentListener {
        void outboxAttachmentCanBeSent(Identity ownedIdentity, UID messageUid, int attachmentNumber, long initialPriority);
    }

    public interface OutboxAttachmentCancelRequestedListener {
        void outboxAttachmentCancelRequested(Identity ownedIdentity, UID messageUid, int attachmentNumber);
    }

    private long commitHookBits = 0;
    private static final long HOOK_BIT_PROGRESS = 0x1;
    private static final long HOOK_BIT_FINISHED = 0x2;
    private static final long HOOK_BIT_CANCEL_REQUESTED = 0x4;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_FINISHED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_MESSAGE_UID_KEY, messageUid);
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_ATTACHMENT_NUMBER_KEY, attachmentNumber);
            if (sendManagerSession.notificationPostingDelegate != null) {
                sendManagerSession.notificationPostingDelegate.postNotification(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED, userInfo);
            }
        } else if ((commitHookBits & HOOK_BIT_PROGRESS) != 0) { // Only send a progress notification when upload is not finished
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_UID_KEY, messageUid);
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY, ((float) acknowledgedChunkCount*ciphertextChunkLength)/getCiphertextLength());
            if (sendManagerSession.notificationPostingDelegate != null) {
                sendManagerSession.notificationPostingDelegate.postNotification(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS, userInfo);
            }
        } else if ((commitHookBits & HOOK_BIT_CANCEL_REQUESTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_UID_KEY, messageUid);
            userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY, attachmentNumber);
            if (sendManagerSession.notificationPostingDelegate != null) {
                sendManagerSession.notificationPostingDelegate.postNotification(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED, userInfo);
            }
            if (sendManagerSession.outboxAttachmentCancelRequestedListener != null) {
                sendManagerSession.outboxAttachmentCancelRequestedListener.outboxAttachmentCancelRequested(ownedIdentity, messageUid, attachmentNumber);
            }
        }
        commitHookBits = 0;
    }

    void messageIsAcknowledged() {
        if (sendManagerSession.outboxAttachmentCanBeSentListener != null) {
            if (!acknowledged) {
                sendManagerSession.outboxAttachmentCanBeSentListener.outboxAttachmentCanBeSent(ownedIdentity, messageUid, attachmentNumber, getPriority());
            }
        }
    }
}
