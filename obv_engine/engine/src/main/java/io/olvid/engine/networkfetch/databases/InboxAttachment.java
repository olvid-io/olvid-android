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

package io.olvid.engine.networkfetch.databases;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Chunk;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.datatypes.DownloadAttachmentPriorityCategory;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;


public class InboxAttachment implements ObvDatabase {
    static final String TABLE_NAME = "inbox_attachment";

    private final FetchManagerSession fetchManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID messageUid;
    static final String MESSAGE_UID = "message_uid";
    private int attachmentNumber;
    static final String ATTACHMENT_NUMBER = "attachment_number";
    private long expectedLength;
    static final String EXPECTED_LENGTH = "expected_length";
    private int chunkLength;
    static final String CHUNK_LENGTH = "chunk_length";
    private byte[] metadata;
    static final String METADATA = "metadata";
    private AuthEncKey key;
    static final String KEY = "key";
    private long fileSize;
    static final String FILE_SIZE = "file_size";
    private long receivedLength;
    static final String RECEIVED_LENGTH = "received_length";
    private Integer priorityCategory;
    static final String PRIORITY_CATEGORY = "priority_category";
    private boolean downloadRequested;
    static final String DOWNLOAD_REQUESTED = "download_requested";
    private Long timestampOfFetchRequest;
    static final String TIMESTAMP_OF_FETCH_REQUEST = "timestamp_of_fetch_request";
    private boolean markedForDeletion;
    static final String MARKED_FOR_DELETION = "marked_for_deletion";
    private String chunkDownloadPrivateUrls;
    static final String CHUNK_DOWNLOAD_PRIVATE_URLS = "chunk_download_private_urls";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public int getAttachmentNumber() {
        return attachmentNumber;
    }

    public long getExpectedLength() {
        return expectedLength;
    }

    public int getChunkLength() {
        return chunkLength;
    }

    public byte[] getMetadata() {
        return metadata;
    }

    public AuthEncKey getKey() {
        return key;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getReceivedLength() {
        return receivedLength;
    }

    public Integer getPriorityCategory() {
        return priorityCategory;
    }

    public boolean isDownloadRequested() {
        return downloadRequested;
    }

    public Long getTimestampOfFetchRequest() {
        return timestampOfFetchRequest;
    }

    public boolean isMarkedForDeletion() {
        return markedForDeletion;
    }

    public String[] getChunkDownloadPrivateUrls() {
        if (chunkDownloadPrivateUrls == null) {
            return new String[0];
        }
        return chunkDownloadPrivateUrls.split("¦", -1);
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

    public boolean cannotBeFetched() {
        return key == null;
    }

    public long getPlaintextExpectedLength() {
        AuthEnc authEnc = Suite.getDefaultAuthEnc(0);
        long fullChunkCount = (expectedLength - 1) / chunkLength;
        return  Chunk.lengthOfInnerDataFromLengthOfEncodedChunk(authEnc.plaintextLengthFromCiphertextLength(chunkLength)) * fullChunkCount +
                Chunk.lengthOfInnerDataFromLengthOfEncodedChunk(authEnc.plaintextLengthFromCiphertextLength((int) (expectedLength - fullChunkCount*chunkLength)));
    }

    public long getPlaintextReceivedLength() {
        AuthEnc authEnc = Suite.getDefaultAuthEnc(0);
        long fullChunkCount = (receivedLength - 1) / chunkLength;
        return  Chunk.lengthOfInnerDataFromLengthOfEncodedChunk(authEnc.plaintextLengthFromCiphertextLength(chunkLength)) * fullChunkCount +
                Chunk.lengthOfInnerDataFromLengthOfEncodedChunk(authEnc.plaintextLengthFromCiphertextLength((int) (receivedLength - fullChunkCount*chunkLength)));
    }

    public long getPriority() {
        switch (priorityCategory) {
            case DownloadAttachmentPriorityCategory.WEIGHT:
                return expectedLength - receivedLength;
            case DownloadAttachmentPriorityCategory.TIMESTAMP:
                return -timestampOfFetchRequest;
            default:
                return 0;
        }
    }

    public int getReceivedChunkCount() {
        if (receivedLength == expectedLength) {
            return 1 + (int)((receivedLength -1)/ chunkLength);
        } else {
            return (int) (receivedLength / chunkLength);
        }
    }

    public float getProgress() {
        return ((float) receivedLength)/expectedLength;
    }

    public InboxMessage getMessage() {
        return InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
    }

    // endregion
    // region setters

    public void requestDownload(int priorityCategory) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + DOWNLOAD_REQUESTED + " = 1, " +
                PRIORITY_CATEGORY + " = ?, " +
                TIMESTAMP_OF_FETCH_REQUEST + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setInt(1, priorityCategory);
            long timestamp = System.currentTimeMillis();
            statement.setLong(2, timestamp);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, messageUid.getBytes());
            statement.setInt(5, attachmentNumber);
            statement.executeUpdate();
            this.downloadRequested = true;
            this.priorityCategory = priorityCategory;
            this.timestampOfFetchRequest = timestamp;
            commitHookBits |= HOOK_BIT_DOWNLOAD_REQUESTED;
            fetchManagerSession.session.addSessionCommitListener(this);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void pauseDownload() {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + DOWNLOAD_REQUESTED + " = 0 " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            statement.executeUpdate();
            this.downloadRequested = false;
            // No notification needed: the downloadSmallAttachment operation will cancel and the coordinator sends a "paused" notification
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void markForDeletion() {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                MARKED_FOR_DELETION + " = 1 " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            statement.executeUpdate();
            this.markedForDeletion = true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void setKeyAndMetadata(AuthEncKey key, byte[] metadata) throws Exception {
        if (key == null || metadata == null) {
            throw new IllegalArgumentException();
        }
        if (this.key != null || this.metadata != null) {
            throw new Exception("Attachment key and metadata were already set.");
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + KEY + " = ?, " + METADATA + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, Encoded.of(key).getBytes());
            statement.setBytes(2, metadata);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, messageUid.getBytes());
            statement.setInt(5, attachmentNumber);
            statement.executeUpdate();
            this.key = key;
            this.metadata = metadata;
        }
    }

    public void setChunkDownloadPrivateUrls(String[] chunkDownloadPrivateUrls) throws Exception {
        if (chunkDownloadPrivateUrls == null || chunkDownloadPrivateUrls.length == 0) {
            throw new IllegalArgumentException();
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String chunkDownloadPrivateUrl : chunkDownloadPrivateUrls) {
            if (!first) {
                sb.append("¦");
            }
            first = false;
            sb.append(chunkDownloadPrivateUrl);
        }
        String serialized = sb.toString();

        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + CHUNK_DOWNLOAD_PRIVATE_URLS + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?" +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setString(1, serialized);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, messageUid.getBytes());
            statement.setInt(4, attachmentNumber);
            statement.executeUpdate();
            this.chunkDownloadPrivateUrls = serialized;
        }
    }

    public void deleteAttachmentFile() throws IOException {
        File attachmentDirectory = new File(fetchManagerSession.engineBaseDirectory, getAttachmentDirectory());
        if (!attachmentDirectory.isDirectory()) {
            return;
        }
        File attachmentFile = new File(fetchManagerSession.engineBaseDirectory, getUrl());
        if (attachmentFile.exists()) {
            if (!attachmentFile.delete()) {
                throw new IOException();
            }
        }
        if (attachmentDirectory.list().length == 0) {
            if (!attachmentDirectory.delete()) {
                throw new IOException();
            }
        }
    }

    private String getAttachmentDirectory() {
        return Constants.INBOUND_ATTACHMENTS_DIRECTORY + File.separator + ownedIdentity.computeUniqueUid().toString() + "-" + messageUid.toString();
    }

    public String getUrl() {
        return getAttachmentDirectory() + File.separator + attachmentNumber;
    }

    public boolean writeToAttachmentFile(byte[] attachmentBytes, int encryptedLength) {
        //noinspection ResultOfMethodCallIgnored
        new File(fetchManagerSession.engineBaseDirectory, getAttachmentDirectory()).mkdirs();
        try (RandomAccessFile f = new RandomAccessFile(new File(fetchManagerSession.engineBaseDirectory, getUrl()), "rw")) {
            f.setLength(fileSize);
            f.seek(fileSize);
            f.write(attachmentBytes);

            try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                    RECEIVED_LENGTH + " = ?, " +
                    FILE_SIZE + " = ? " +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " AND " + MESSAGE_UID + " = ? " +
                    " AND " + ATTACHMENT_NUMBER + " = ?;")) {
                statement.setLong(1, receivedLength + encryptedLength);
                statement.setLong(2, fileSize + attachmentBytes.length);
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.setBytes(4, messageUid.getBytes());
                statement.setInt(5, attachmentNumber);
                statement.executeUpdate();
                this.receivedLength += encryptedLength;
                this.fileSize += attachmentBytes.length;
                if (expectedLength == receivedLength) {
                    commitHookBits |= HOOK_BIT_LAST_CHUNK_RECEIVED;
                }
                commitHookBits |= HOOK_BIT_CHUNK_RECEIVED;
                fetchManagerSession.session.addSessionCommitListener(this);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // endregion
    // region constructors

    public static InboxAttachment create(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid, int attachmentNumber, long expectedLength, int chunkLength, String[] chunkDownloadPrivateUrls) throws SQLException {
        if (ownedIdentity == null || messageUid == null) {
            return null;
        }
        InboxAttachment inboxAttachment = new InboxAttachment(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber, expectedLength, chunkLength, chunkDownloadPrivateUrls);
        inboxAttachment.insert();
        return inboxAttachment;
    }

    private InboxAttachment(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid, int attachmentNumber, long expectedLength, int chunkLength, String[] chunkDownloadPrivateUrls) {
        this.fetchManagerSession = fetchManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.attachmentNumber = attachmentNumber;
        this.expectedLength = expectedLength;
        this.chunkLength = chunkLength;
        this.metadata = null;
        this.key = null;
        this.fileSize = 0;
        this.receivedLength = 0;
        this.priorityCategory = null;
        this.downloadRequested = false;
        this.timestampOfFetchRequest = null;
        this.markedForDeletion = false;

        String serialized;
        if (chunkDownloadPrivateUrls == null || chunkDownloadPrivateUrls.length == 0) {
            serialized = null;
        } else {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String chunkDownloadPrivateUrl: chunkDownloadPrivateUrls) {
                if (!first) {
                    sb.append("¦");
                }
                first = false;
                sb.append(chunkDownloadPrivateUrl);
            }
            serialized = sb.toString();
        }
        this.chunkDownloadPrivateUrls = serialized;
    }

    private InboxAttachment(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.messageUid = new UID(res.getBytes(MESSAGE_UID));
        this.attachmentNumber = res.getInt(ATTACHMENT_NUMBER);
        this.expectedLength = res.getLong(EXPECTED_LENGTH);
        this.chunkLength = res.getInt(CHUNK_LENGTH);
        this.metadata = res.getBytes(METADATA);
        try {
            this.key = (AuthEncKey) new Encoded(res.getBytes(KEY)).decodeSymmetricKey();
        } catch (Exception e) {
            this.key = null;
        }
        this.fileSize = res.getLong(FILE_SIZE);
        this.receivedLength = res.getLong(RECEIVED_LENGTH);
        this.priorityCategory = res.getInt(PRIORITY_CATEGORY);
        if (res.wasNull()) {
            this.priorityCategory = null;
        }
        this.downloadRequested = res.getBoolean(DOWNLOAD_REQUESTED);
        this.timestampOfFetchRequest = res.getLong(TIMESTAMP_OF_FETCH_REQUEST);
        if (res.wasNull()) {
            this.timestampOfFetchRequest = null;
        }
        this.markedForDeletion = res.getBoolean(MARKED_FOR_DELETION);
        this.chunkDownloadPrivateUrls = res.getString(CHUNK_DOWNLOAD_PRIVATE_URLS);
    }

    // endregion
    // region getters

    public static InboxAttachment get(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws SQLException {
        if (ownedIdentity == null || messageUid == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new InboxAttachment(fetchManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static InboxAttachment[] getAll(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid) {
        if (ownedIdentity == null || messageUid == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + OWNED_IDENTITY + " = ? " +
                        " AND " + MESSAGE_UID + " = ? " +
                        " ORDER BY " + ATTACHMENT_NUMBER + " ASC;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<InboxAttachment> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxAttachment(fetchManagerSession, res));
                }
                return list.toArray(new InboxAttachment[0]);
            }
        } catch (SQLException e) {
            return new InboxAttachment[0];
        }
    }

    public static InboxAttachment[] getAllDownloaded(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + RECEIVED_LENGTH + " = " + EXPECTED_LENGTH  +
                        " AND " + MARKED_FOR_DELETION + " = 0;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<InboxAttachment> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxAttachment(fetchManagerSession, res));
                }
                return list.toArray(new InboxAttachment[0]);
            }
        } catch (SQLException e) {
            return new InboxAttachment[0];
        }
    }


    public static InboxAttachment[] getAllAttachmentsToResume(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + DOWNLOAD_REQUESTED + " = 1 " +
                        " AND " + KEY + " NOT NULL " +
                        " AND " + RECEIVED_LENGTH + " < " + EXPECTED_LENGTH  +
                        " AND " + MARKED_FOR_DELETION + " = 0;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<InboxAttachment> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new InboxAttachment(fetchManagerSession, res));
                }
                return list.toArray(new InboxAttachment[0]);
            }
        } catch (SQLException e) {
            return new InboxAttachment[0];
        }
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    MESSAGE_UID + " BLOB NOT NULL, " +
                    ATTACHMENT_NUMBER + " INT, " +
                    EXPECTED_LENGTH + " BIGINT NOT NULL, " +
                    CHUNK_LENGTH + " INT NOT NULL, " +

                    METADATA + " BLOB, " +
                    KEY + " BLOB, " +
                    FILE_SIZE + " BIGINT NOT NULL, " +
                    RECEIVED_LENGTH + " BIGINT NOT NULL, " +
                    PRIORITY_CATEGORY + " INT, " +

                    DOWNLOAD_REQUESTED + " BIT NOT NULL, " +
                    TIMESTAMP_OF_FETCH_REQUEST + " BIGINT, " +
                    MARKED_FOR_DELETION + " BIT NOT NULL, " +
                    CHUNK_DOWNLOAD_PRIVATE_URLS + " TEXT, " +

                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY (" + OWNED_IDENTITY + ", " + MESSAGE_UID + ", " + ATTACHMENT_NUMBER + "), " +
                    "FOREIGN KEY (" + OWNED_IDENTITY + ", " + MESSAGE_UID + ") REFERENCES " + InboxMessage.TABLE_NAME + "(" + InboxMessage.OWNED_IDENTITY + ", " + InboxMessage.UID_ + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 4 && newVersion >= 4) {
            Logger.d("MIGRATING `inbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 4\n!!!! THIS MIGRATION IS DESTRUCTIVE !!!!");
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS `inbox_attachment`;");
                statement.execute("CREATE TABLE IF NOT EXISTS inbox_attachment (" +
                        "message_uid BLOB, " +
                        "attachment_number INT, " +
                        "expected_length BIGINT NOT NULL, " +
                        "chunk_length INT NOT NULL, " +
                        "metadata BLOB, " +

                        "key BLOB, " +
                        "file_size BIGINT NOT NULL, " +
                        "received_length BIGINT NOT NULL, " +
                        "priority_category INT, " +
                        "pending_cancel_fetch_request BIT NOT NULL, " +

                        "download_requested BIT NOT NULL, " +
                        "timestamp_of_fetch_request BIGINT, " +
                        "marked_for_deletion BIT NOT NULL, " +

                        "CONSTRAINT PK_inbox_attachment PRIMARY KEY(message_uid, attachment_number), " +
                        "FOREIGN KEY (message_uid) REFERENCES inbox_message(uid));");
            }
            oldVersion = 4;
        }
        if (oldVersion < 8 && newVersion >= 8) {
            Logger.d("MIGRATING `inbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 8");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_attachment RENAME TO old_inbox_attachment");
                statement.execute("CREATE TABLE IF NOT EXISTS inbox_attachment (" +
                        "message_uid BLOB, " +
                        "attachment_number INT, " +
                        "expected_length BIGINT NOT NULL, " +
                        "chunk_length INT NOT NULL, " +
                        "metadata BLOB, " +

                        "key BLOB, " +
                        "file_size BIGINT NOT NULL, " +
                        "received_length BIGINT NOT NULL, " +
                        "priority_category INT, " +
                        "download_requested BIT NOT NULL, " +

                        "timestamp_of_fetch_request BIGINT, " +
                        "marked_for_deletion BIT NOT NULL, " +

                        "CONSTRAINT PK_inbox_attachment PRIMARY KEY(message_uid, attachment_number), " +
                        "FOREIGN KEY (message_uid) REFERENCES inbox_message(uid))");
                statement.execute("INSERT INTO inbox_attachment " +
                        " SELECT message_uid, attachment_number, expected_length, chunk_length, metadata, " +
                        " key, file_size, received_length, priority_category, download_requested, " +
                        " timestamp_of_fetch_request, marked_for_deletion FROM old_inbox_attachment");
                statement.execute("DROP TABLE old_inbox_attachment");
            }
            oldVersion = 8;
        }
        if (oldVersion < 13 && newVersion >= 13) {
            Logger.d("MIGRATING `inbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 13");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_attachment ADD COLUMN chunk_download_private_urls TEXT DEFAULT NULL;");
            }
            oldVersion = 13;
        }
        if (oldVersion < 15 && newVersion >= 15) {
            Logger.d("MIGRATING `inbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 15");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE inbox_attachment RENAME TO old_inbox_attachment");
                statement.execute("CREATE TABLE IF NOT EXISTS inbox_attachment (" +
                        "owned_identity BLOB NOT NULL, " +
                        "message_uid BLOB NOT NULL, " +
                        "attachment_number INT, " +
                        "expected_length BIGINT NOT NULL, " +
                        "chunk_length INT NOT NULL, " +

                        "metadata BLOB, " +
                        "key BLOB, " +
                        "file_size BIGINT NOT NULL, " +
                        "received_length BIGINT NOT NULL, " +
                        "priority_category INT, " +

                        "download_requested BIT NOT NULL, " +
                        "timestamp_of_fetch_request BIGINT, " +
                        "marked_for_deletion BIT NOT NULL, " +
                        "chunk_download_private_urls TEXT, " +

                        "CONSTRAINT PK_inbox_attachment PRIMARY KEY (owned_identity, message_uid, attachment_number), " +
                        "FOREIGN KEY (owned_identity, message_uid) REFERENCES inbox_message(owned_identity, uid))");
                statement.execute("INSERT INTO inbox_attachment " +
                        " SELECT m.owned_identity, a.message_uid, a.attachment_number, a.expected_length, a.chunk_length, a.metadata, " +
                        " a.key, a.file_size, a.received_length, a.priority_category, a.download_requested, " +
                        " a.timestamp_of_fetch_request, a.marked_for_deletion, a.chunk_download_private_urls " +
                        " FROM old_inbox_attachment AS a " +
                        " INNER JOIN inbox_message AS m ON a.message_uid = m.uid");
                statement.execute("DROP TABLE old_inbox_attachment");
            }
            oldVersion = 15;
        }
        if (oldVersion < 16 && newVersion >= 16) {
            Logger.d("MIGRATING `inbox_attachment` DATABASE FROM VERSION " + oldVersion + " TO 16");
            try (Statement statement = session.createStatement()) {
                statement.execute("UPDATE inbox_attachment SET received_length = 0, file_size = 0");
            }
            oldVersion = 16;
        }
    }
    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES(?,?,?,?,?, ?,?,?,?,?, ?,?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            statement.setLong(4, expectedLength);
            statement.setInt(5, chunkLength);

            statement.setBytes(6, metadata);
            statement.setBytes(7, (key==null)?null:Encoded.of(key).getBytes());
            statement.setLong(8, fileSize);
            statement.setLong(9, receivedLength);
            if (priorityCategory == null) {
                statement.setNull(10, Types.INTEGER);
            } else {
                statement.setInt(10, priorityCategory);
            }

            statement.setBoolean(11, downloadRequested);
            if (timestampOfFetchRequest == null) {
                statement.setNull(12, Types.BIGINT);
            } else {
                statement.setLong(12, timestampOfFetchRequest);
            }
            statement.setBoolean(13, markedForDeletion);
            statement.setString(14, chunkDownloadPrivateUrls);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ? " +
                " AND " + ATTACHMENT_NUMBER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.setInt(3, attachmentNumber);
            statement.executeUpdate();
        }
    }

    // endregion

    // region hooks

    public interface InboxAttachmentListener {
        void attachmentDownloadProgressed(Identity ownedIdentity, UID messageUid, int attachmentNumber, float progress);
        void attachmentDownloadFinished(Identity ownedIdentity, UID messageUid, int attachmentNumber);
        void attachmentDownloadWasRequested(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory, long initialPriority);
    }

    private long commitHookBits = 0;
    private static final long HOOK_BIT_CHUNK_RECEIVED = 0x1;
    private static final long HOOK_BIT_LAST_CHUNK_RECEIVED = 0x2;
    private static final long HOOK_BIT_DOWNLOAD_REQUESTED = 0x4;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_CHUNK_RECEIVED) != 0) {
            if (fetchManagerSession.inboxAttachmentListener != null) {
                fetchManagerSession.inboxAttachmentListener.attachmentDownloadProgressed(ownedIdentity, messageUid, attachmentNumber, getProgress());
            }
        }
        if ((commitHookBits & HOOK_BIT_LAST_CHUNK_RECEIVED) != 0) {
            if (fetchManagerSession.inboxAttachmentListener != null) {
                fetchManagerSession.inboxAttachmentListener.attachmentDownloadFinished(ownedIdentity, messageUid, attachmentNumber);
            }
        }
        if ((commitHookBits & HOOK_BIT_DOWNLOAD_REQUESTED) != 0) {
            if (fetchManagerSession.inboxAttachmentListener != null) {
                fetchManagerSession.inboxAttachmentListener.attachmentDownloadWasRequested(ownedIdentity, messageUid, attachmentNumber, priorityCategory, getPriority());
            }
        }
        commitHookBits = 0;
    }
    // endregion
}
