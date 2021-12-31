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

package io.olvid.engine.protocol.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.datatypes.GenericProtocolMessageToSend;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;


public class WaitingForTrustLevelIncreaseProtocolInstance implements ObvDatabase {
    static final String TABLE_NAME = "waiting_for_trust_level_increase_protocol_instance";

    private final ProtocolManagerSession protocolManagerSession;

    private UID protocolUid;
    static final String PROTOCOL_UID = "protocol_uid";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private int protocolId;
    static final String PROTOCOL_ID = "protocol_id";
    private int messageId;
    static final String MESSAGE_ID = "message_id";
    private TrustLevel targetTrustLevel;
    static final String TARGET_TRUST_LEVEL = "target_trust_level";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public TrustLevel getTargetTrustLevel() {
        return targetTrustLevel;
    }

    // region databases

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    PROTOCOL_UID + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    PROTOCOL_ID + " INTEGER NOT NULL, " +
                    MESSAGE_ID + " INTEGER NOT NULL, " +
                    TARGET_TRUST_LEVEL + " TEXT NOT NULL, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + PROTOCOL_UID + ", " + OWNED_IDENTITY + ", " + CONTACT_IDENTITY + "), " +
                    " FOREIGN KEY (" + PROTOCOL_UID + ", " + OWNED_IDENTITY + ") REFERENCES " + ProtocolInstance.TABLE_NAME + "(" + ProtocolInstance.UID_ + ", " + ProtocolInstance.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `waiting_for_trust_level_increase_protocol_instance` DATABASE FROM VERSION " + oldVersion + " TO 11");
                statement.execute("CREATE TABLE IF NOT EXISTS `waiting_for_trust_level_increase_protocol_instance` (" +
                        "protocol_uid BLOB NOT NULL, " +
                        "owned_identity BLOB NOT NULL, " +
                        "contact_identity BLOB NOT NULL, " +
                        "protocol_id INTEGER NOT NULL, " +
                        "message_id INTEGER NOT NULL, " +
                        "target_trust_level TEXT NOT NULL, " +
                        " CONSTRAINT PK_waiting_for_trust_level_increase_protocol_instance PRIMARY KEY(protocol_uid, owned_identity, contact_identity), " +
                        " FOREIGN KEY (protocol_uid, owned_identity) REFERENCES protocol_instance(uid, owned_identity) ON DELETE CASCADE);");
                statement.execute("DELETE FROM waiting_for_trust_level_increase_protocol_instance WHERE protocol_id = 5;");
            }
            oldVersion = 11;
        }
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM waiting_for_trust_level_increase_protocol_instance AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM protocol_instance " +
                        " WHERE uid = p.protocol_uid" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?);")) {
            statement.setBytes(1, protocolUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
            statement.setInt(4, protocolId);
            statement.setInt(5, messageId);
            statement.setString(6, targetTrustLevel.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + PROTOCOL_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, protocolUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region constructor

    public static WaitingForTrustLevelIncreaseProtocolInstance create(ProtocolManagerSession protocolManagerSession, UID protocolUid, Identity ownedIdentity, Identity contactIdentity, int protocolId, int messageId, TrustLevel targetTrustLevel) {
        if (protocolUid == null || ownedIdentity == null || contactIdentity == null || targetTrustLevel == null) {
            return null;
        }
        try {
            WaitingForTrustLevelIncreaseProtocolInstance instance = new WaitingForTrustLevelIncreaseProtocolInstance(protocolManagerSession, protocolUid, ownedIdentity, contactIdentity, protocolId, messageId, targetTrustLevel);
            instance.insert();
            return instance;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private WaitingForTrustLevelIncreaseProtocolInstance(ProtocolManagerSession protocolManagerSession, UID protocolUid, Identity ownedIdentity, Identity contactIdentity, int protocolId, int messageId, TrustLevel targetTrustLevel) {
        this.protocolManagerSession = protocolManagerSession;
        this.protocolUid = protocolUid;
        this.ownedIdentity = ownedIdentity;
        this.contactIdentity = contactIdentity;
        this.protocolId = protocolId;
        this.messageId = messageId;
        this.targetTrustLevel = targetTrustLevel;
    }

    private WaitingForTrustLevelIncreaseProtocolInstance(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;

        this.protocolUid = new UID(res.getBytes(PROTOCOL_UID));
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.protocolId = res.getInt(PROTOCOL_ID);
        this.messageId = res.getInt(MESSAGE_ID);
        this.targetTrustLevel = TrustLevel.of(res.getString(TARGET_TRUST_LEVEL));
    }

    // endregion

    // region getters

    public static WaitingForTrustLevelIncreaseProtocolInstance get(ProtocolManagerSession protocolManagerSession, UID protocolUid, Identity ownedIdentity, Identity contactIdentity) {
        if (protocolUid == null || ownedIdentity == null || contactIdentity == null) {
            return null;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + PROTOCOL_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, protocolUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new WaitingForTrustLevelIncreaseProtocolInstance(protocolManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public static WaitingForTrustLevelIncreaseProtocolInstance[] getAllWithTargetFulfilled(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, Identity contactIdentity, TrustLevel contactTrustLevel) {
        if (ownedIdentity == null || contactIdentity == null || contactTrustLevel == null) {
            return new WaitingForTrustLevelIncreaseProtocolInstance[0];
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<WaitingForTrustLevelIncreaseProtocolInstance> list = new ArrayList<>();
                while (res.next()) {
                    WaitingForTrustLevelIncreaseProtocolInstance waitingForTrustLevelIncreaseProtocolInstance = new WaitingForTrustLevelIncreaseProtocolInstance(protocolManagerSession, res);
                    if (contactTrustLevel.compareTo(waitingForTrustLevelIncreaseProtocolInstance.targetTrustLevel) >= 0) {
                        list.add(waitingForTrustLevelIncreaseProtocolInstance);
                    }
                }
                return list.toArray(new WaitingForTrustLevelIncreaseProtocolInstance[0]);
            }
        } catch (SQLException e) {
            return new WaitingForTrustLevelIncreaseProtocolInstance[0];
        }
    }

    public static WaitingForTrustLevelIncreaseProtocolInstance[] getAll(ProtocolManagerSession protocolManagerSession) {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<WaitingForTrustLevelIncreaseProtocolInstance> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new WaitingForTrustLevelIncreaseProtocolInstance(protocolManagerSession, res));
                }
                return list.toArray(new WaitingForTrustLevelIncreaseProtocolInstance[0]);
            }
        } catch (SQLException e) {
            return new WaitingForTrustLevelIncreaseProtocolInstance[0];
        }
    }

    public GenericProtocolMessageToSend getGenericProtocolMessageToSendWhenTrustLevelIncreased() {
        return new GenericProtocolMessageToSend(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                protocolId,
                protocolUid,
                messageId,
                new Encoded[]{Encoded.of(contactIdentity)},
                false);
    }

    public static void deleteAllForOwnedIdentity(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region hooks

    @Override
    public void wasCommitted() {

    }

    // endregion
}
