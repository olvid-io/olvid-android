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

package io.olvid.engine.protocol.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.datatypes.GenericReceivedProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;


public class ReceivedMessage implements ObvDatabase {
    static final String TABLE_NAME = "received_message";

    private final ProtocolManagerSession protocolManagerSession;

    private UID uid;
    static final String UID_ = "uid";
    private Identity toIdentity;
    static final String TO_IDENTITY = "to_identity";
    private Encoded[] inputs;
    static final String INPUTS = "inputs";
    private UUID userDialogUuid;
    static final String USER_DIALOG_UUID = "user_dialog_uuid";
    private Encoded encodedResponse;
    static final String ENCODED_RESPONSE = "encoded_response";
    private UID protocolInstanceUid;
    static final String PROTOCOL_INSTANCE_UID = "protocol_instance_uid";
    private int protocolMessageId;
    static final String PROTOCOL_MESSAGE_ID = "protocol_message_id";
    private int protocolId;
    static final String PROTOCOL_ID = "protocol_id";
    private ReceptionChannelInfo receptionChannelInfo;
    static final String RECEPTION_CHANNEL_INFO = "reception_channel_info";
    private long expirationTimestamp;
    static final String EXPIRATION_TIMESTAMP = "expiration_timestamp";
    private long serverTimestamp;
    static final String SERVER_TIMESTAMP = "server_timestamp";

    public ProtocolManagerSession getProtocolManagerSession() {
        return protocolManagerSession;
    }

    public UID getUid() {
        return uid;
    }

    public Identity getToIdentity() {
        return toIdentity;
    }

    public Encoded[] getInputs() {
        return inputs;
    }

    public Encoded getEncodedResponse() {
        return encodedResponse;
    }

    public UUID getUserDialogUuid() {
        return userDialogUuid;
    }

    public UID getProtocolInstanceUid() {
        return protocolInstanceUid;
    }

    public int getProtocolMessageId() {
        return protocolMessageId;
    }

    public int getProtocolId() {
        return protocolId;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return receptionChannelInfo;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    // region constructors

    public static ReceivedMessage create(ProtocolManagerSession protocolManagerSession, GenericReceivedProtocolMessage message, PRNGService prng) {
        if ((message == null) || (prng == null)) {
            return null;
        }
        try {
            ReceivedMessage receivedMessage = new ReceivedMessage(
                    protocolManagerSession,
                    message.getToIdentity(),
                    message.getInputs(),
                    message.getUserDialogUuid(),
                    message.getEncodedResponse(),
                    message.getProtocolInstanceUid(),
                    message.getProtocolMessageId(),
                    message.getProtocolId(),
                    message.getReceptionChannelInfo(),
                    message.getServerTimestamp(),
                    prng);
            receivedMessage.insert();
            return receivedMessage;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ReceivedMessage(ProtocolManagerSession protocolManagerSession,
                           Identity toIdentity,
                           Encoded[] inputs,
                           UUID userDialogUuid,
                           Encoded encodedResponse,
                           UID protocolInstanceUid,
                           int protocolMessageId,
                           int protocolId,
                           ReceptionChannelInfo receptionChannelInfo,
                           long serverTimestamp,
                           PRNGService prng) {
        this.protocolManagerSession = protocolManagerSession;

        this.uid = new UID(prng);
        this.toIdentity = toIdentity;
        this.inputs = inputs;
        this.userDialogUuid = userDialogUuid;
        this.encodedResponse = encodedResponse;

        this.protocolInstanceUid = protocolInstanceUid;
        this.protocolMessageId = protocolMessageId;
        this.protocolId = protocolId;
        this.receptionChannelInfo = receptionChannelInfo;

        this.expirationTimestamp = System.currentTimeMillis() + Constants.PROTOCOL_RECEIVED_MESSAGE_EXPIRATION_DELAY;
        this.serverTimestamp = serverTimestamp;
    }


    private ReceivedMessage(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;


        this.uid = new UID(res.getBytes(UID_));
        try {
            this.toIdentity = Identity.of(res.getBytes(TO_IDENTITY));
            this.inputs = new Encoded(res.getBytes(INPUTS)).decodeList();
        } catch (DecodingException e) {
            throw new SQLException();
        }
        String uduuid = res.getString(USER_DIALOG_UUID);
        if (uduuid == null) {
            this.userDialogUuid = null;
        } else {
            this.userDialogUuid = UUID.fromString(uduuid);
        }
        byte[] udr = res.getBytes(ENCODED_RESPONSE);
        if (udr == null) {
            this.encodedResponse = null;
        } else {
            this.encodedResponse = new Encoded(udr);
        }


        this.protocolInstanceUid = new UID(res.getBytes(PROTOCOL_INSTANCE_UID));
        this.protocolMessageId = res.getInt(PROTOCOL_MESSAGE_ID);
        this.protocolId = res.getInt(PROTOCOL_ID);
        try {
            this.receptionChannelInfo = ReceptionChannelInfo.of(new Encoded(res.getBytes(RECEPTION_CHANNEL_INFO)));
        } catch (DecodingException e) {
            throw new SQLException();
        }

        this.expirationTimestamp = res.getLong(EXPIRATION_TIMESTAMP);
        this.serverTimestamp = res.getLong(SERVER_TIMESTAMP);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB PRIMARY KEY, " +
                    TO_IDENTITY + " BLOB NOT NULL, " +
                    INPUTS + " BLOB NOT NULL, " +
                    USER_DIALOG_UUID + " VARCHAR, " +
                    ENCODED_RESPONSE + " BLOB, " +

                    PROTOCOL_INSTANCE_UID + " BLOB NOT NULL, " +
                    PROTOCOL_MESSAGE_ID + " INT NOT NULL, " +
                    PROTOCOL_ID + " INT NOT NULL, " +
                    RECEPTION_CHANNEL_INFO + " BLOB NOT NULL, " +

                    EXPIRATION_TIMESTAMP + " BIGINT NOT NULL, " +
                    SERVER_TIMESTAMP + " BIGINT NOT NULL);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 10 && newVersion >= 10) {
            Logger.d("MIGRATING `received_message` DATABASE FROM VERSION " + oldVersion + " TO 10");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE `received_message` ADD COLUMN `server_timestamp` BIGINT NOT NULL DEFAULT 0");
            }
            oldVersion = 10;
        }
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `received_message` DATABASE FROM VERSION " + oldVersion + " TO 11");
                statement.execute("DELETE FROM received_message WHERE protocol_id = 5;");
            }
            oldVersion = 11;
        }
        if (oldVersion < 32 && newVersion >= 32) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `received_message` DATABASE FROM VERSION " + oldVersion + " TO 32");
                statement.execute("ALTER TABLE `received_message` DROP COLUMN `associated_owned_identity`");
            }
            oldVersion = 32;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?, ?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, toIdentity.getBytes());
            statement.setBytes(3, Encoded.of(inputs).getBytes());
            if (userDialogUuid != null) {
                statement.setString(4, Logger.getUuidString(userDialogUuid));
            } else {
                statement.setNull(4, Types.VARCHAR);
            }
            if (encodedResponse != null) {
                statement.setBytes(5, encodedResponse.getBytes());
            } else {
                statement.setNull(5, Types.BLOB);
            }

            statement.setBytes(6, protocolInstanceUid.getBytes());
            statement.setInt(7, protocolMessageId);
            statement.setInt(8, protocolId);
            statement.setBytes(9, receptionChannelInfo.encode().getBytes());
            statement.setLong(10, expirationTimestamp);

            statement.setLong(11, serverTimestamp);
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_INSERTED;
            protocolManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UID_ + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.executeUpdate();
        }
    }



    public static void deleteExpiredMessagesWithNoProtocol(ProtocolManagerSession protocolManagerSession) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement(
                "DELETE FROM " + TABLE_NAME + " WHERE " + UID_ + " IN "+
                        " (SELECT " + TABLE_NAME + "." + UID_ + " FROM " + TABLE_NAME +
                        " LEFT JOIN " + ProtocolInstance.TABLE_NAME + " ON " + ProtocolInstance.TABLE_NAME + "." + ProtocolInstance.UID_ + " = " + TABLE_NAME + "." + PROTOCOL_INSTANCE_UID  +
                        " WHERE " + TABLE_NAME + "." + EXPIRATION_TIMESTAMP + " < ?" +
                        " AND " + ProtocolInstance.TABLE_NAME + "." + ProtocolInstance.UID_ + " IS NULL);")
        ) {
            statement.setLong(1, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static ReceivedMessage get(ProtocolManagerSession protocolManagerSession, UID receivedMessageUid) {
        if ((receivedMessageUid == null)) {
            return null;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + UID_ + " = ?;")) {
            statement.setBytes(1, receivedMessageUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ReceivedMessage(protocolManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ReceivedMessage[] getAll(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + PROTOCOL_INSTANCE_UID + " = ? AND " + TO_IDENTITY + " = ?;")) {
            statement.setBytes(1, protocolInstanceUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ReceivedMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ReceivedMessage(protocolManagerSession, res));
                }
                return list.toArray(new ReceivedMessage[0]);
            }
        }
    }

    public static ReceivedMessage[] getAll(ProtocolManagerSession protocolManagerSession) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ReceivedMessage> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ReceivedMessage(protocolManagerSession, res));
                }
                return list.toArray(new ReceivedMessage[0]);
            }
        }
    }

    public static void deleteAllForOwnedIdentity(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + TO_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }



    // endregion

    // region hooks

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            if (protocolManagerSession.protocolReceivedMessageProcessorDelegate != null) {
                protocolManagerSession.protocolReceivedMessageProcessorDelegate.processReceivedMessage(uid);
            }
        }
        commitHookBits = 0;
    }

    // endregion
}
