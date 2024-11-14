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

package io.olvid.engine.channel.databases;

import java.security.InvalidKeyException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.channel.datatypes.PreKeyChannel;
import io.olvid.engine.datatypes.containers.AuthEncKeyAndChannelInfo;
import io.olvid.engine.channel.datatypes.ChannelManagerSession;
import io.olvid.engine.channel.datatypes.NetworkChannel;
import io.olvid.engine.channel.datatypes.RatchetingOutput;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.KDF;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.crypto.exceptions.DecryptionException;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.KeyId;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelProtocolMessageToSend;
import io.olvid.engine.datatypes.containers.MessageToSend;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.containers.OwnedDeviceAndPreKey;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.UidAndPreKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.ChannelNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;


public class ObliviousChannel extends NetworkChannel implements ObvDatabase {
    static final String TABLE_NAME = "oblivious_channel";

    private final ChannelManagerSession channelManagerSession;

    private UID currentDeviceUid;
    static final String CURRENT_DEVICE_UID = "current_device_uid";
    private UID remoteDeviceUid;
    static final String REMOTE_DEVICE_UID = "remote_device_uid";
    private Identity remoteIdentity;
    static final String REMOTE_IDENTITY = "contact_identity";
    private boolean confirmed;
    static final String CONFIRMED = "confirmed";

    // there is already a obliviousEngineVersion field in the Channal parent class for this DB field
    // private int obliviousEngineVersion;
    static final String OBLIVIOUS_ENGINE_VERSION = "oblivious_engine_version";
    private Seed seedForNextSendKey;
    static final String SEED_FOR_NEXT_SEND_KEY = "seed_for_next_send_key";
    private int fullRatchetingCountOfLastProvision;
    static final String FULL_RATCHETING_COUNT_OF_LAST_PROVISION = "full_ratcheting_count_of_last_provision";

    // info used for the full ratcheting
    private int numberOfEncryptedMessages;
    static final String NUMBER_OF_ENCRYPTED_MESSAGES = "number_of_encrypted_messages";
    private int numberOfEncryptedMessagesAtTheTimeOfTheLastFullRatchet;
    static final String NUMBER_OF_ENCRYPTED_MESSAGES_AT_THE_TIME_OF_THE_LAST_FULL_RATCHET = "number_of_encrypted_messages_at_the_time_of_the_last_full_ratchet";
    private int numberOfEncryptedMessagesSinceLastFullRatchetSentMessage;
    static final String NUMBER_OF_ENCRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE = "number_of_encrypted_messages_since_last_full_ratchet_sent_message";
    private int numberOfDecryptedMessagesSinceLastFullRatchetSentMessage;
    static final String NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE = "number_of_decrypted_messages_since_last_full_ratchet_sent_message";
    private long timestampOfLastFullRatchet;
    static final String TIMESTAMP_OF_LAST_FULL_RATCHET = "timestamp_of_last_full_ratchet";
    private long timestampOfLastFullRatchetSentMessage;
    static final String TIMESTAMP_OF_LAST_FULL_RATCHET_SENT_MESSAGE = "timestamp_of_last_full_ratchet_sent_message";
    private boolean fullRatchetOfTheSendSeedInProgress;
    static final String FULL_RATCHET_OF_THE_SEND_SEED_IN_PROGRESS = "full_ratchet_of_the_send_seed_in_progress";

    // info for GKMV2
    private boolean supportsGKMV2;
    static final String SUPPORTS_GKMV_2 = "supports_gkmv_2";
    private int fullRatchetingCountForGkmv2Support;
    static final String FULL_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT = "full_ratcheting_count_with_gkmv_2_support";
    private int selfRatchetingCountForGkmv2Support;
    static final String SELF_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT = "self_ratcheting_count_with_gkmv_2_support";


    public UID getCurrentDeviceUid() {
        return currentDeviceUid;
    }

    public UID getRemoteDeviceUid() {
        return remoteDeviceUid;
    }

    public Identity getRemoteIdentity() {
        return remoteIdentity;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return ReceptionChannelInfo.createObliviousChannelInfo(remoteDeviceUid, remoteIdentity);
    }

    private boolean supportsGKMV2(int fullRatchetingCount, int selfRatchetingCount) {
        return  supportsGKMV2
                && (fullRatchetingCount > fullRatchetingCountForGkmv2Support
                || (fullRatchetingCount == fullRatchetingCountForGkmv2Support && selfRatchetingCount > selfRatchetingCountForGkmv2Support));
    }

    public int getNumberOfEncryptedMessagesSinceLastFullRatchet() {
        return numberOfEncryptedMessages - numberOfEncryptedMessagesAtTheTimeOfTheLastFullRatchet;
    }

    public boolean requiresFullRatchet() {
        if (fullRatchetOfTheSendSeedInProgress) {
            // 1. If we received too many messages since the last full ratchet protocol message that we sent,
            // it means that the other end of the channel will probably never send an answer to our last protocol message.
            // In that case, we decide to start the full ratchet protocol all over again.
            if (numberOfDecryptedMessagesSinceLastFullRatchetSentMessage >= Constants.THRESHOLD_NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE) {
                return true;
            }

            // 2. If too much time passed since the time we sent a message related to the full ratcheting protocol in progress,
            // we decide to start the protocol all over again.
            if (System.currentTimeMillis() - timestampOfLastFullRatchetSentMessage >= Constants.THRESHOLD_TIME_INTERVAL_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE) {
                return true;
            }

            // 3. If the number of messages sent since the last sent message related to the full ratcheting protocol
            // is larger than the reprovisioning threshold, we must restart the protocol since the recipient could end up
            // not being able to decrypt an old message arriving after the end of the full ratcheting.
            if (numberOfEncryptedMessagesSinceLastFullRatchetSentMessage >= Constants.REPROVISIONING_THRESHOLD) {
                return true;
            }
        } else {
            // 1. If the number of encrypted messages since the last successful full ratchet is too high,
            // we must start a new full ratchet
            if (getNumberOfEncryptedMessagesSinceLastFullRatchet() >= Constants.THRESHOLD_NUMBER_OF_ENCRYPTED_MESSAGES_PER_FULL_RATCHET) {
                return true;
            }

            // 2. If the elapsed time since the last successful full ratchet is too high,
            // we must start a new full ratchet
            if (System.currentTimeMillis() - timestampOfLastFullRatchet >= Constants.FULL_RATCHET_TIME_INTERVAL_VALIDITY) {
                return true;
            }
        }
        return false;
    }

    public Provision getLatestProvision() {
        return Provision.get(channelManagerSession, fullRatchetingCountOfLastProvision, currentDeviceUid, remoteDeviceUid, remoteIdentity);
    }


    public void aSendSeedFullRatchetMessageWasSent() {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                FULL_RATCHET_OF_THE_SEND_SEED_IN_PROGRESS + " = 1, " +
                NUMBER_OF_ENCRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE + " = 0, " +
                NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE + " = 0, " +
                TIMESTAMP_OF_LAST_FULL_RATCHET_SENT_MESSAGE + " = ? " +
                " WHERE " + CURRENT_DEVICE_UID + " = ? AND " + REMOTE_DEVICE_UID + " = ? AND " + REMOTE_IDENTITY + " = ?;")) {
            long now = System.currentTimeMillis();
            statement.setLong(1, now);
            statement.setBytes(2, currentDeviceUid.getBytes());
            statement.setBytes(3, remoteDeviceUid.getBytes());
            statement.setBytes(4, remoteIdentity.getBytes());
            statement.executeUpdate();
            this.fullRatchetOfTheSendSeedInProgress = true;
            this.numberOfDecryptedMessagesSinceLastFullRatchetSentMessage = 0;
            this.numberOfEncryptedMessagesSinceLastFullRatchetSentMessage = 0;
            this.timestampOfLastFullRatchet = now;
        } catch (SQLException ignored) {}
    }

    public void confirm() throws SQLException {
        if (confirmed) {
            return;
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                CONFIRMED + " = 1 " +
                " WHERE " + CURRENT_DEVICE_UID + " = ? " +
                " AND " + REMOTE_DEVICE_UID + " = ? " +
                " AND " + REMOTE_IDENTITY + " = ?;")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
            statement.setBytes(2, remoteDeviceUid.getBytes());
            statement.setBytes(3, remoteIdentity.getBytes());
            statement.executeUpdate();
            this.confirmed = true;
            commitHookBits |= HOOK_BIT_CHANNEL_CONFIRMED;
            channelManagerSession.session.addSessionCommitListener(this);
        }
    }

    public static void setSupportsGKMV2(ChannelManagerSession channelManagerSession, UID currentDeviceUid, UID remoteDeviceUid, Identity remoteIdentity, int fullRatchetingCount, int selfRatchetingCount) throws SQLException {
        ObliviousChannel obliviousChannel = get(channelManagerSession, currentDeviceUid, remoteDeviceUid, remoteIdentity, false);
        if (obliviousChannel.supportsGKMV2(fullRatchetingCount, selfRatchetingCount)) {
            // the oblivious channel is already tagged as supporting GKMV2 at an older full/self ratcheting count
            return;
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                SUPPORTS_GKMV_2 + " = 1, " +
                FULL_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT + " = ?," +
                SELF_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT + " = ? " +
                " WHERE " + CURRENT_DEVICE_UID + " = ? " +
                " AND " + REMOTE_DEVICE_UID + " = ? " +
                " AND " + REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, fullRatchetingCount);
            statement.setInt(2, selfRatchetingCount);
            statement.setBytes(3, currentDeviceUid.getBytes());
            statement.setBytes(4, remoteDeviceUid.getBytes());
            statement.setBytes(5, remoteIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // This method is called after a send full ratchet
    public void updateSendSeed(Seed seed, int obliviousEngineVersion) {
        Seed sendSeed = generateDiversifiedSeed(seed, currentDeviceUid, obliviousEngineVersion);
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                SEED_FOR_NEXT_SEND_KEY + " = ?, " +
                OBLIVIOUS_ENGINE_VERSION + " = ?, " +
                NUMBER_OF_ENCRYPTED_MESSAGES_AT_THE_TIME_OF_THE_LAST_FULL_RATCHET + " = ?, " +
                TIMESTAMP_OF_LAST_FULL_RATCHET + " = ?, " +
                FULL_RATCHET_OF_THE_SEND_SEED_IN_PROGRESS + " = 0 " +
                " WHERE " + CURRENT_DEVICE_UID + " = ? AND " + REMOTE_DEVICE_UID + " = ? AND " + REMOTE_IDENTITY + " = ?;")) {
            long now = System.currentTimeMillis();
            statement.setBytes(1, sendSeed.getBytes());
            statement.setInt(2, obliviousEngineVersion);
            statement.setInt(3, numberOfEncryptedMessages);
            statement.setLong(4, now);
            statement.setBytes(5, currentDeviceUid.getBytes());
            statement.setBytes(6, remoteDeviceUid.getBytes());
            statement.setBytes(7, remoteIdentity.getBytes());
            statement.executeUpdate();
            this.seedForNextSendKey = sendSeed;
            this.obliviousEngineVersion = obliviousEngineVersion;
            this.numberOfEncryptedMessagesAtTheTimeOfTheLastFullRatchet = numberOfEncryptedMessages;
            this.timestampOfLastFullRatchet = now;
            this.fullRatchetOfTheSendSeedInProgress = false;
        } catch (SQLException ignored) {}
    }

    // This method is called after a receive full ratchet
    public void createNewProvision(Seed seed, int obliviousEngineVersion) throws SQLException {
        Seed receiveSeed = generateDiversifiedSeed(seed, remoteDeviceUid, obliviousEngineVersion);
        Provision provision = Provision.createOrReplace(channelManagerSession, fullRatchetingCountOfLastProvision + 1, this, receiveSeed, obliviousEngineVersion);
        if (provision == null) {
            throw new SQLException();
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                FULL_RATCHETING_COUNT_OF_LAST_PROVISION + " = ? " +
                " WHERE " + CURRENT_DEVICE_UID + " = ? AND " + REMOTE_DEVICE_UID + " = ? AND " + REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, fullRatchetingCountOfLastProvision + 1);
            statement.setBytes(2, currentDeviceUid.getBytes());
            statement.setBytes(3, remoteDeviceUid.getBytes());
            statement.setBytes(4, remoteIdentity.getBytes());
            statement.executeUpdate();
            this.fullRatchetingCountOfLastProvision++;
        }
    }

    public static void clean(ChannelManagerSession channelManagerSession) {
        ProvisionedKeyMaterial.deleteAllExpired(channelManagerSession);
        Provision.deleteAllEmpty(channelManagerSession);
    }

    private RatchetingOutput selfRatchet() {
        RatchetingOutput ratchetingOutput = ObliviousChannel.computeSelfRatchet(seedForNextSendKey, obliviousEngineVersion);
        if (ratchetingOutput == null) {
            return null;
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                SEED_FOR_NEXT_SEND_KEY + " = ? " +
                " WHERE " + CURRENT_DEVICE_UID + " = ? AND " + REMOTE_DEVICE_UID + " = ? AND " + REMOTE_IDENTITY + " = ?;")) {
            statement.setBytes(1, ratchetingOutput.getRatchetedSeed().getBytes());
            statement.setBytes(2, currentDeviceUid.getBytes());
            statement.setBytes(3, remoteDeviceUid.getBytes());
            statement.setBytes(4, remoteIdentity.getBytes());
            statement.executeUpdate();
            this.seedForNextSendKey = ratchetingOutput.getRatchetedSeed();
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
        return ratchetingOutput;
    }

    public static ObliviousChannel create(ChannelManagerSession channelManagerSession,
                                   UID currentDeviceUid,
                                   UID remoteDeviceUid,
                                   Identity remoteIdentity,
                                   Seed seed,
                                   int obliviousEngineVersion) {

        if ((currentDeviceUid == null) || (remoteDeviceUid == null) || (remoteIdentity == null) || (seed == null)) {
            return null;
        }
        Seed sendSeed = generateDiversifiedSeed(seed, currentDeviceUid, obliviousEngineVersion);
        Seed receiveSeed = generateDiversifiedSeed(seed, remoteDeviceUid, obliviousEngineVersion);
        try {
            ObliviousChannel obliviousChannel = new ObliviousChannel(channelManagerSession, currentDeviceUid, remoteDeviceUid, remoteIdentity, sendSeed, obliviousEngineVersion);
            obliviousChannel.insert();
            Provision provision = Provision.createOrReplace(channelManagerSession, 0, obliviousChannel, receiveSeed, obliviousEngineVersion);
            if (provision == null) {
                obliviousChannel.delete();
                throw new SQLException();
            }
            return obliviousChannel;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    private ObliviousChannel(ChannelManagerSession channelManagerSession,
                             UID currentDeviceUid,
                             UID remoteDeviceUid,
                             Identity remoteIdentity,
                             Seed seedForNextSendKey,
                             int obliviousEngineVersion) {
        this.channelManagerSession = channelManagerSession;

        this.currentDeviceUid = currentDeviceUid;
        this.remoteDeviceUid = remoteDeviceUid;
        this.remoteIdentity = remoteIdentity;
        this.confirmed = false;
        this.obliviousEngineVersion = obliviousEngineVersion;

        this.seedForNextSendKey = seedForNextSendKey;
        this.fullRatchetingCountOfLastProvision = 0;
        this.numberOfEncryptedMessages = 0;
        this.numberOfEncryptedMessagesAtTheTimeOfTheLastFullRatchet= 0;
        this.numberOfEncryptedMessagesSinceLastFullRatchetSentMessage = 0;

        this.numberOfDecryptedMessagesSinceLastFullRatchetSentMessage = 0;
        this.timestampOfLastFullRatchet = System.currentTimeMillis();
        this.timestampOfLastFullRatchetSentMessage = this.timestampOfLastFullRatchet;
        this.fullRatchetOfTheSendSeedInProgress = false;
        this.supportsGKMV2 = false;

        this.fullRatchetingCountForGkmv2Support = -1;
        this.selfRatchetingCountForGkmv2Support = -1;
    }

    private ObliviousChannel(ChannelManagerSession channelManagerSession, ResultSet res) throws SQLException {
        this.channelManagerSession = channelManagerSession;

        this.currentDeviceUid = new UID(res.getBytes(CURRENT_DEVICE_UID));
        this.remoteDeviceUid = new UID(res.getBytes(REMOTE_DEVICE_UID));
        try {
            this.remoteIdentity = Identity.of(res.getBytes(REMOTE_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.confirmed = res.getBoolean(CONFIRMED);
        this.obliviousEngineVersion = res.getInt(OBLIVIOUS_ENGINE_VERSION);

        this.seedForNextSendKey = new Seed(res.getBytes(SEED_FOR_NEXT_SEND_KEY));
        this.fullRatchetingCountOfLastProvision = res.getInt(FULL_RATCHETING_COUNT_OF_LAST_PROVISION);
        this.numberOfEncryptedMessages = res.getInt(NUMBER_OF_ENCRYPTED_MESSAGES);
        this.numberOfEncryptedMessagesAtTheTimeOfTheLastFullRatchet= res.getInt(NUMBER_OF_ENCRYPTED_MESSAGES_AT_THE_TIME_OF_THE_LAST_FULL_RATCHET);
        this.numberOfEncryptedMessagesSinceLastFullRatchetSentMessage = res.getInt(NUMBER_OF_ENCRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE);

        this.numberOfDecryptedMessagesSinceLastFullRatchetSentMessage = res.getInt(NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE);
        this.timestampOfLastFullRatchet = res.getLong(TIMESTAMP_OF_LAST_FULL_RATCHET);
        this.timestampOfLastFullRatchetSentMessage = res.getLong(TIMESTAMP_OF_LAST_FULL_RATCHET_SENT_MESSAGE);
        this.fullRatchetOfTheSendSeedInProgress = res.getBoolean(FULL_RATCHET_OF_THE_SEND_SEED_IN_PROGRESS);
        this.supportsGKMV2 = res.getBoolean(SUPPORTS_GKMV_2);

        this.fullRatchetingCountForGkmv2Support = res.getInt(FULL_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT);
        this.selfRatchetingCountForGkmv2Support = res.getInt(SELF_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT);
    }



    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    CURRENT_DEVICE_UID + " BLOB NOT NULL, " +
                    REMOTE_DEVICE_UID + " BLOB NOT NULL, " +
                    REMOTE_IDENTITY + " BLOB NOT NULL, " +
                    CONFIRMED + " BIT NOT NULL, " +
                    OBLIVIOUS_ENGINE_VERSION + " INT NOT NULL, " +
                    SEED_FOR_NEXT_SEND_KEY + " BLOB NOT NULL, " +
                    FULL_RATCHETING_COUNT_OF_LAST_PROVISION + " INT NOT NULL, " +
                    NUMBER_OF_ENCRYPTED_MESSAGES + " INT NOT NULL, " +
                    NUMBER_OF_ENCRYPTED_MESSAGES_AT_THE_TIME_OF_THE_LAST_FULL_RATCHET + " INT NOT NULL, " +
                    NUMBER_OF_ENCRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE + " INT NOT NULL, " +
                    NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE + " INT NOT NULL, " +
                    TIMESTAMP_OF_LAST_FULL_RATCHET + " BIGINT NOT NULL, " +
                    TIMESTAMP_OF_LAST_FULL_RATCHET_SENT_MESSAGE + " BIGINT NOT NULL, " +
                    FULL_RATCHET_OF_THE_SEND_SEED_IN_PROGRESS + " BIT NOT NULL, " +
                    SUPPORTS_GKMV_2 + " BIT NOT NULL, " +
                    FULL_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT + " INT NOT NULL, " +
                    SELF_RATCHETING_COUNT_FOR_GKMV_2_SUPPORT + " INT NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + CURRENT_DEVICE_UID + ", " + REMOTE_DEVICE_UID +", " + REMOTE_IDENTITY + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 39 && newVersion >= 39) {
            Logger.d("MIGRATING `oblivious_channel` DATABASE FROM VERSION " + oldVersion + " TO 39");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE oblivious_channel ADD COLUMN `supports_gkmv_2` BIT NOT NULL DEFAULT 0");
                statement.execute("ALTER TABLE oblivious_channel ADD COLUMN `full_ratcheting_count_with_gkmv_2_support` INT NOT NULL DEFAULT -1");
                statement.execute("ALTER TABLE oblivious_channel ADD COLUMN `self_ratcheting_count_with_gkmv_2_support` INT NOT NULL DEFAULT -1");
            }
            oldVersion = 39;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
            statement.setBytes(2, remoteDeviceUid.getBytes());
            statement.setBytes(3, remoteIdentity.getBytes());
            statement.setBoolean(4, confirmed);
            statement.setInt(5, obliviousEngineVersion);

            statement.setBytes(6, seedForNextSendKey.getBytes());
            statement.setInt(7, fullRatchetingCountOfLastProvision);
            statement.setInt(8, numberOfEncryptedMessages);
            statement.setInt(9, numberOfEncryptedMessagesAtTheTimeOfTheLastFullRatchet);
            statement.setInt(10, numberOfEncryptedMessagesSinceLastFullRatchetSentMessage);

            statement.setInt(11, numberOfDecryptedMessagesSinceLastFullRatchetSentMessage);
            statement.setLong(12, timestampOfLastFullRatchet);
            statement.setLong(13, timestampOfLastFullRatchetSentMessage);
            statement.setBoolean(14, fullRatchetOfTheSendSeedInProgress);
            statement.setBoolean(15, supportsGKMV2);

            statement.setInt(16, fullRatchetingCountForGkmv2Support);
            statement.setInt(17, selfRatchetingCountForGkmv2Support);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + CURRENT_DEVICE_UID + " = ? AND " + REMOTE_DEVICE_UID + " = ? AND " + REMOTE_IDENTITY + " = ?;")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
            statement.setBytes(2, remoteDeviceUid.getBytes());
            statement.setBytes(3, remoteIdentity.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_CHANNEL_DELETED;
            channelManagerSession.session.addSessionCommitListener(this);
        }
    }


    // region getters

    public static ObliviousChannel get(ChannelManagerSession channelManagerSession, UID currentDeviceUid, UID remoteDeviceUid, Identity remoteIdentity, boolean necessarilyConfirmed) {
        if ((currentDeviceUid == null) || (remoteDeviceUid == null) || (remoteIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                (necessarilyConfirmed ? (CONFIRMED + " = 1 AND ") : "") +
                CURRENT_DEVICE_UID + " = ? AND " + REMOTE_DEVICE_UID + " = ? AND " + REMOTE_IDENTITY + " = ?;")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
            statement.setBytes(2, remoteDeviceUid.getBytes());
            statement.setBytes(3, remoteIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ObliviousChannel(channelManagerSession, res);
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
        return null;
    }

    public static ObliviousChannel[] getAll(ChannelManagerSession channelManagerSession) {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME)) {
            try (ResultSet res = statement.executeQuery()) {
                List<ObliviousChannel> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ObliviousChannel(channelManagerSession, res));
                }
                return list.toArray(new ObliviousChannel[0]);
            }
        } catch (SQLException e) {
            return new ObliviousChannel[0];
        }
    }


    public static ObliviousChannel[] getAllConfirmed(ChannelManagerSession channelManagerSession) {
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + CONFIRMED + " = 1;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ObliviousChannel> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ObliviousChannel(channelManagerSession, res));
                }
                return list.toArray(new ObliviousChannel[0]);
            }
        } catch (SQLException e) {
            return new ObliviousChannel[0];
        }
    }

    public static ObliviousChannel[] getMany(ChannelManagerSession channelManagerSession, UID currentDeviceUid, UID[] remoteDeviceUids, Identity remoteIdentity, boolean necessarilyConfirmed) {
        if ((currentDeviceUid == null) || (remoteDeviceUids == null) || (remoteDeviceUids.length == 0) || (remoteIdentity == null)) {
            return null;
        }
        String questionMarks = "(";
        for (int i = 0; i < remoteDeviceUids.length; i++) {
            if (i == 0) {
                questionMarks += "?";
            } else {
                //noinspection StringConcatenationInLoop
                questionMarks += ",?";
            }
        }
        questionMarks += ")";
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " +
                (necessarilyConfirmed ? (CONFIRMED + " = 1 AND ") : "") +
                CURRENT_DEVICE_UID + " = ? AND " +
                REMOTE_IDENTITY + " = ? AND " +
                REMOTE_DEVICE_UID + " IN " + questionMarks + ";")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
            statement.setBytes(2, remoteIdentity.getBytes());
            for (int i = 0; i < remoteDeviceUids.length; i++) {
                statement.setBytes(3 + i, remoteDeviceUids[i].getBytes());
            }
            try (ResultSet res = statement.executeQuery()) {
                List<ObliviousChannel> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ObliviousChannel(channelManagerSession, res));
                }
                return list.toArray(new ObliviousChannel[0]);
            }
        } catch (SQLException e) {
            return new ObliviousChannel[0];
        }
    }

    public static void deleteMany(final ChannelManagerSession channelManagerSession, final UID currentDeviceUid, final UID[] remoteDeviceUids, final Identity remoteIdentity) {
        if ((currentDeviceUid == null) || (remoteDeviceUids == null) || (remoteDeviceUids.length == 0) || (remoteIdentity == null)) {
            return;
        }
        String questionMarks = "(";
        for (int i=0; i<remoteDeviceUids.length; i++) {
            if (i==0) {
                questionMarks += "?";
            } else {
                //noinspection StringConcatenationInLoop
                questionMarks += ",?";
            }
        }
        questionMarks += ")";
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " +
                CURRENT_DEVICE_UID + " = ? AND " +
                REMOTE_IDENTITY + " = ? AND " +
                REMOTE_DEVICE_UID + " IN " + questionMarks + ";")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
            statement.setBytes(2, remoteIdentity.getBytes());
            for (int i=0; i<remoteDeviceUids.length; i++) {
                statement.setBytes(3+i, remoteDeviceUids[i].getBytes());
            }
            statement.executeUpdate();
            channelManagerSession.session.addSessionCommitListener(() -> {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_CURRENT_DEVICE_UID_KEY, currentDeviceUid);
                userInfo.put(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_REMOTE_IDENTITY_KEY, remoteIdentity);
                channelManagerSession.notificationPostingDelegate.postNotification(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED, userInfo);
            });
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    public static void deleteAll(ChannelManagerSession channelManagerSession, UID currentDeviceUid) throws SQLException {
        if (currentDeviceUid == null) {
            return;
        }
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + CURRENT_DEVICE_UID + " = ?;")) {
            statement.setBytes(1, currentDeviceUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    try {
                        ObliviousChannel obliviousChannel = new ObliviousChannel(channelManagerSession, res);
                        obliviousChannel.delete();
                    } catch (SQLException ignored) {}
                }
            }
        }
    }


    // endregion


    private long commitHookBits = 0;
    private static final long HOOK_BIT_MIGHT_NEED_FULL_RATCHET = 0x1;
    private static final long HOOK_BIT_CHANNEL_CONFIRMED = 0x2;
    private static final long HOOK_BIT_CHANNEL_DELETED = 0x4;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_MIGHT_NEED_FULL_RATCHET) != 0) {
            if (requiresFullRatchet()) {
                if (channelManagerSession.fullRatchetProtocolStarterDelegate != null) {
                    try {
                        channelManagerSession.fullRatchetProtocolStarterDelegate.startFullRatchetProtocolForObliviousChannel(currentDeviceUid, remoteDeviceUid, remoteIdentity);
                    } catch (Exception e) {
                        // no need to do anything, the next message will try to restart the full ratchet
                        Logger.x(e);
                    }
                } else {
                    Logger.w("Full ratchet required, but no FullRatchetProtocolStarterDelegate is set.");
                }
            }
        }
        if ((commitHookBits & HOOK_BIT_CHANNEL_CONFIRMED) != 0) {
            // refresh members of groups owned by the remoteIdentity (useful after a backup restore)
            channelManagerSession.identityDelegate.refreshMembersOfGroupsOwnedByGroupOwner(currentDeviceUid, remoteIdentity);
            // re-invite members of groups owned (useful after a backup restore)
            channelManagerSession.identityDelegate.pushMembersOfOwnedGroupsToContact(currentDeviceUid, remoteIdentity);
            // resend a batch of all keys for common groups V2
            channelManagerSession.identityDelegate.initiateGroupV2BatchKeysResend(currentDeviceUid, remoteIdentity, remoteDeviceUid);

            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED_CURRENT_DEVICE_UID_KEY, currentDeviceUid);
            userInfo.put(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED_REMOTE_IDENTITY_KEY, remoteIdentity);
            channelManagerSession.notificationPostingDelegate.postNotification(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_CHANNEL_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_CURRENT_DEVICE_UID_KEY, currentDeviceUid);
            userInfo.put(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_REMOTE_IDENTITY_KEY, remoteIdentity);
            channelManagerSession.notificationPostingDelegate.postNotification(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED, userInfo);
        }
        commitHookBits = 0;
    }







    static RatchetingOutput computeSelfRatchet(Seed seed, int obliviousEngineVersion) {
        PRNG prng = Suite.getDefaultPRNG(obliviousEngineVersion, seed);

        Seed ratchetedSeed = new Seed(prng);
        KeyId keyId = new KeyId(prng.bytes(KeyId.KEYID_LENGTH));
        AuthEncKey authEncKey;

        KDF kdf = Suite.getDefaultKDF(obliviousEngineVersion);
        Seed kdfSeed = new Seed(prng);
        try {
            authEncKey = (AuthEncKey) kdf.gen(kdfSeed, Suite.getDefaultAuthEnc(obliviousEngineVersion).getKDFDelegate())[0];
        } catch (Exception e) {
            return null;
        }
        return new RatchetingOutput(ratchetedSeed, keyId, authEncKey);
    }

    private static Seed generateDiversifiedSeed(Seed seed, UID uid, int obliviousEngineVersion) {
        byte[] longSeedBytes = new byte[seed.length + uid.getBytes().length];
        System.arraycopy(seed.getBytes(), 0, longSeedBytes, 0, seed.length);
        System.arraycopy(uid.getBytes(), 0, longSeedBytes, seed.length, uid.getBytes().length);
        PRNG prng = Suite.getDefaultPRNG(obliviousEngineVersion, new Seed(longSeedBytes));
        return new Seed(prng);
    }





    public static NetworkChannel[] acceptableChannelsForPosting(ChannelManagerSession channelManagerSession, ChannelMessageToSend message) throws SQLException {
        if (channelManagerSession.identityDelegate == null) {
            Logger.w("Calling acceptableChannelsForPosting with no IdentityDelegate set.");
            return new ObliviousChannel[0];
        }

        switch (message.getSendChannelInfo().getChannelType()) {
            case SendChannelInfo.OBLIVIOUS_CHANNEL_TYPE: {
                if (!message.getSendChannelInfo().getNecessarilyConfirmed() && message.getMessageType() != MessageType.PROTOCOL_MESSAGE_TYPE) {
                    // Only protocol messages may be sent through unconfirmed channels
                    return new ObliviousChannel[0];
                }
                HashSet<UID> remoteDeviceUidSet;
                if (Objects.equals(message.getSendChannelInfo().getFromIdentity(), message.getSendChannelInfo().getToIdentity())) {
                    // posting for owned identity
                    remoteDeviceUidSet = new HashSet<>(Arrays.asList(channelManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity())));
                } else {
                    // posting for contact
                    remoteDeviceUidSet = new HashSet<>(Arrays.asList(channelManagerSession.identityDelegate.getDeviceUidsOfContactIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity(), message.getSendChannelInfo().getToIdentity())));
                }
                remoteDeviceUidSet.retainAll(Arrays.asList(message.getSendChannelInfo().getRemoteDeviceUids()));
                UID currentDeviceUid = channelManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity());

                ObliviousChannel[] channels = ObliviousChannel.getAcceptableObliviousChannels(channelManagerSession, currentDeviceUid, remoteDeviceUidSet.toArray(new UID[0]), message.getSendChannelInfo().getToIdentity(), message.getSendChannelInfo().getNecessarilyConfirmed()).toArray(new ObliviousChannel[0]);

                if (message.getMessageType() == MessageType.PROTOCOL_MESSAGE_TYPE) {
                    if (((ChannelProtocolMessageToSend) message).isPartOfFullRatchetProtocolOfTheSendSeed()) {
                        for (ObliviousChannel channel : channels) {
                            channel.aSendSeedFullRatchetMessageWasSent();
                        }
                    }
                }
                return channels;
            }
            case SendChannelInfo.ALL_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_ON_SAME_SERVER_TYPE: {
                List<NetworkChannel> acceptableChannels = new ArrayList<>();
                UID currentDeviceUid = channelManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity());
                for (Identity toIdentity: message.getSendChannelInfo().getToIdentities()) {
                    List<UidAndPreKey> uidsAndPreKeys = new ArrayList<>();
                    if (Objects.equals(message.getSendChannelInfo().getFromIdentity(), toIdentity)) {
                        List<OwnedDeviceAndPreKey> ownedDeviceAndPreKeys = channelManagerSession.identityDelegate.getDevicesAndPreKeysOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity());
                        for (OwnedDeviceAndPreKey ownedDeviceAndPreKey : ownedDeviceAndPreKeys) {
                            if (!ownedDeviceAndPreKey.currentDevice) {
                                uidsAndPreKeys.add(new UidAndPreKey(ownedDeviceAndPreKey.deviceUid, ownedDeviceAndPreKey.preKey));
                            }
                        }
                    } else {
                        uidsAndPreKeys = channelManagerSession.identityDelegate.getDeviceUidsAndPreKeysOfContactIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity(), toIdentity);
                    }
                    acceptableChannels.addAll(ObliviousChannel.getAcceptableObliviousOrPreKeyChannels(channelManagerSession, message.getSendChannelInfo().getFromIdentity(), currentDeviceUid, uidsAndPreKeys.toArray(new UidAndPreKey[0]), toIdentity));
                }
                return acceptableChannels.toArray(new NetworkChannel[0]);
            }
            case SendChannelInfo.ALL_OWNED_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_TYPE: {
                List<UidAndPreKey> uidsAndPreKeys = new ArrayList<>();
                List<OwnedDeviceAndPreKey> ownedDeviceAndPreKeys = channelManagerSession.identityDelegate.getDevicesAndPreKeysOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity());
                for (OwnedDeviceAndPreKey ownedDeviceAndPreKey : ownedDeviceAndPreKeys) {
                    if (!ownedDeviceAndPreKey.currentDevice) {
                        uidsAndPreKeys.add(new UidAndPreKey(ownedDeviceAndPreKey.deviceUid, ownedDeviceAndPreKey.preKey));
                    }
                }

                UID currentDeviceUid = channelManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity());
                return ObliviousChannel.getAcceptableObliviousOrPreKeyChannels(channelManagerSession, message.getSendChannelInfo().getFromIdentity(), currentDeviceUid, uidsAndPreKeys.toArray(new UidAndPreKey[0]), message.getSendChannelInfo().getToIdentity()).toArray(new NetworkChannel[0]);
            }
            case SendChannelInfo.OBLIVIOUS_CHANNEL_OR_PRE_KEY_TYPE: {
                List<UidAndPreKey> uidsAndPreKeys = new ArrayList<>();
                if (Objects.equals(message.getSendChannelInfo().getFromIdentity(), message.getSendChannelInfo().getToIdentity())) {
                    List<OwnedDeviceAndPreKey> ownedDeviceAndPreKeys = channelManagerSession.identityDelegate.getDevicesAndPreKeysOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity());
                    for (OwnedDeviceAndPreKey ownedDeviceAndPreKey : ownedDeviceAndPreKeys) {
                        if (!ownedDeviceAndPreKey.currentDevice) {
                            uidsAndPreKeys.add(new UidAndPreKey(ownedDeviceAndPreKey.deviceUid, ownedDeviceAndPreKey.preKey));
                        }
                    }
                } else {
                    uidsAndPreKeys = channelManagerSession.identityDelegate.getDeviceUidsAndPreKeysOfContactIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity(), message.getSendChannelInfo().getToIdentity());
                }

                HashSet<UID> remoteUids = new HashSet<>(Arrays.asList(message.getSendChannelInfo().getRemoteDeviceUids()));
                List<UidAndPreKey> remoteUidsAndPreKeys = new ArrayList<>();
                for (UidAndPreKey uidAndPreKey : uidsAndPreKeys) {
                    if (remoteUids.contains(uidAndPreKey.uid)) {
                        remoteUidsAndPreKeys.add(uidAndPreKey);
                    }
                }

                UID currentDeviceUid = channelManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getFromIdentity());
                return ObliviousChannel.getAcceptableObliviousOrPreKeyChannels(channelManagerSession, message.getSendChannelInfo().getFromIdentity(), currentDeviceUid, remoteUidsAndPreKeys.toArray(new UidAndPreKey[0]), message.getSendChannelInfo().getToIdentity()).toArray(new NetworkChannel[0]);
            }
            default:
                return new ObliviousChannel[0];
        }
    }

    private static List<NetworkChannel> getAcceptableObliviousOrPreKeyChannels(ChannelManagerSession channelManagerSession, Identity ownedIdentity, UID currentDeviceUid, UidAndPreKey[] remoteDeviceUidsAndPreKeys, Identity remoteIdentity) {
        // first get all oblivious channels
        UID[] uids = new UID[remoteDeviceUidsAndPreKeys.length];
        for (int i=0; i<remoteDeviceUidsAndPreKeys.length; i++) {
            uids[i] = remoteDeviceUidsAndPreKeys[i].uid;
        }
        List<ObliviousChannel> obliviousChannels = getAcceptableObliviousChannels(channelManagerSession, currentDeviceUid, uids, remoteIdentity, true);
        HashSet<UID> obliviousChannelUids = new HashSet<>();
        for (ObliviousChannel obliviousChannel : obliviousChannels) {
            obliviousChannelUids.add(obliviousChannel.remoteDeviceUid);
        }

        List<NetworkChannel> acceptableChannels = new ArrayList<>();
        for (UidAndPreKey uidAndPreKey : remoteDeviceUidsAndPreKeys) {
            if (!obliviousChannelUids.contains(uidAndPreKey.uid) && uidAndPreKey.preKey != null) {
                acceptableChannels.add(new PreKeyChannel(channelManagerSession.session, ownedIdentity, remoteIdentity, uidAndPreKey.uid, channelManagerSession.preKeyEncryptionDelegate));
            }
        }

        acceptableChannels.addAll(obliviousChannels);
        return acceptableChannels;
    }

    private static List<ObliviousChannel> getAcceptableObliviousChannels(ChannelManagerSession channelManagerSession, UID currentDeviceUid, UID[] remoteDeviceUids, Identity remoteIdentity, boolean necessarilyConfirmed) {
        ObliviousChannel[] channels = getMany(channelManagerSession, currentDeviceUid, remoteDeviceUids, remoteIdentity, necessarilyConfirmed);
        if (channels == null) {
            return Collections.emptyList();
        }
        List<ObliviousChannel> channelList = new ArrayList<>();
        for (ObliviousChannel channel: channels) {
            if (channel.getObliviousEngineVersion() >= Suite.MINIMUM_ACCEPTABLE_VERSION) {
                channelList.add(channel);
            }
        }
        return channelList;
    }

    @Override
    public MessageToSend.Header wrapMessageKey(AuthEncKey messageKey, PRNGService prng, boolean partOfFullRatchetProtocol) {
        RatchetingOutput ratchetingOutput = selfRatchet();
        if (ratchetingOutput == null) {
            return null;
        }
        AuthEnc authEnc = Suite.getAuthEnc(ratchetingOutput.getAuthEncKey());
        EncryptedBytes encryptedMessageKey;

        try {
            encryptedMessageKey = authEnc.encrypt(ratchetingOutput.getAuthEncKey(), Encoded.of(messageKey).getBytes(), prng);
        } catch (InvalidKeyException e) {
            Logger.x(e);
            return null;
        }
        byte[] headerBytes = new byte[KeyId.KEYID_LENGTH + encryptedMessageKey.length];
        System.arraycopy(ratchetingOutput.getKeyId().getBytes(), 0, headerBytes, 0, KeyId.KEYID_LENGTH);
        System.arraycopy(encryptedMessageKey.getBytes(), 0, headerBytes, KeyId.KEYID_LENGTH, encryptedMessageKey.length);

        MessageToSend.Header header = new MessageToSend.Header(remoteDeviceUid, remoteIdentity, new EncryptedBytes(headerBytes));
        try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                NUMBER_OF_ENCRYPTED_MESSAGES + " = ?, " +
                NUMBER_OF_ENCRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE + " = ? " +
                " WHERE " + CURRENT_DEVICE_UID + " = ? AND " + REMOTE_DEVICE_UID + " = ? AND " + REMOTE_IDENTITY + " = ?;")) {
            statement.setInt(1, numberOfEncryptedMessages + 1);
            statement.setInt(2, numberOfEncryptedMessagesSinceLastFullRatchetSentMessage + 1);
            statement.setBytes(3, currentDeviceUid.getBytes());
            statement.setBytes(4, remoteDeviceUid.getBytes());
            statement.setBytes(5, remoteIdentity.getBytes());
            statement.executeUpdate();
            this.numberOfEncryptedMessages++;
            this.numberOfEncryptedMessagesSinceLastFullRatchetSentMessage++;
            if (!partOfFullRatchetProtocol) {
                commitHookBits |= HOOK_BIT_MIGHT_NEED_FULL_RATCHET;
                channelManagerSession.session.addSessionCommitListener(this);
            }
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
        return header;
    }

    public static AuthEncKeyAndChannelInfo unwrapMessageKey(ChannelManagerSession channelManagerSession, NetworkReceivedMessage.Header header) {
        byte[] bytes = header.getWrappedKey().getBytes();
        if (bytes.length < KeyId.KEYID_LENGTH) {
            return null;
        }
        KeyId keyId = new KeyId(Arrays.copyOfRange(bytes, 0, KeyId.KEYID_LENGTH));
        EncryptedBytes encryptedMessageKey = new EncryptedBytes(Arrays.copyOfRange(bytes, KeyId.KEYID_LENGTH, bytes.length));
        UID deviceUid;
        try {
            deviceUid = channelManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(channelManagerSession.session, header.getOwnedIdentity());
        } catch (SQLException e) {
            Logger.e("Error retrieving a currentDeviceUid -> a received message might have been lost...");
            Logger.x(e);
            return null;
        }
        ProvisionedKeyMaterial[] provisionedKeys = ProvisionedKeyMaterial.getAll(channelManagerSession, keyId, deviceUid);
        for (ProvisionedKeyMaterial provisionedKey: provisionedKeys) {
            try {
                AuthEnc authEnc = Suite.getAuthEnc(provisionedKey.getAuthEncKey());
                Encoded encodedMessageKey = new Encoded(authEnc.decrypt(provisionedKey.getAuthEncKey(), encryptedMessageKey));
                AuthEncKey messageKey = (AuthEncKey) encodedMessageKey.decodeSymmetricKey();
                ObliviousChannel obliviousChannel = provisionedKey.getObliviousChannel();
                if (obliviousChannel == null) {
                    Logger.w("While unwrapping a message key, a provision was found without a corresponding channel.");
                    continue;
                }

                ////////////////
                // From this point, we start modifying the database and must not return null
                ////////////////

                provisionedKey.setExpirationTimestampsOfOlderProvisionedKeyMaterials();

                {
                    Provision provision = Provision.get(channelManagerSession, provisionedKey.getProvisionFullRatchetingCount(), provisionedKey.getProvisionObliviousChannelCurrentDeviceUid(), provisionedKey.getProvisionObliviousChannelRemoteDeviceUid(), provisionedKey.getProvisionObliviousChannelRemoteIdentity());
                    if (provision != null) {
                        provision.selfRatchetIfRequired();
                    }
                }

                if (obliviousChannel.fullRatchetOfTheSendSeedInProgress) {
                    try (PreparedStatement statement = channelManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                            " SET " + NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE + " = ? " +
                            " WHERE " + CURRENT_DEVICE_UID + " = ? " +
                            " AND " + REMOTE_DEVICE_UID + " = ? " +
                            " AND " + REMOTE_IDENTITY + " = ?;")) {
                        statement.setInt(1, obliviousChannel.numberOfDecryptedMessagesSinceLastFullRatchetSentMessage + 1);
                        statement.setBytes(2, obliviousChannel.currentDeviceUid.getBytes());
                        statement.setBytes(3, obliviousChannel.remoteDeviceUid.getBytes());
                        statement.setBytes(4, obliviousChannel.remoteIdentity.getBytes());
                        statement.executeUpdate();
                        obliviousChannel.numberOfDecryptedMessagesSinceLastFullRatchetSentMessage++;
                        obliviousChannel.commitHookBits |= HOOK_BIT_MIGHT_NEED_FULL_RATCHET;
                        channelManagerSession.session.addSessionCommitListener(obliviousChannel);
                    } catch (SQLException e) {
                        Logger.x(e);
                    }
                }
                try {
                    provisionedKey.delete();
                    if (!obliviousChannel.confirmed) {
                        obliviousChannel.confirm();
                    }
                } catch (SQLException e) {
                    Logger.x(e);
                }
                ReceptionChannelInfo receptionChannelInfo = obliviousChannel.getReceptionChannelInfo();
                // add information about GKMV2 in receptionChannelInfo
                receptionChannelInfo.enrichWithGKMV2Info(provisionedKey.getProvisionFullRatchetingCount(), provisionedKey.getSelfRatchetingCount(), obliviousChannel.supportsGKMV2(provisionedKey.getProvisionFullRatchetingCount(), provisionedKey.getSelfRatchetingCount()));
                return new AuthEncKeyAndChannelInfo(messageKey, receptionChannelInfo);
            } catch (InvalidKeyException | DecryptionException | DecodingException | ClassCastException e) {
                // nothing to do
            }
        }
        return null;
    }
}
