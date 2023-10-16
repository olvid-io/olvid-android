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
import java.util.Collections;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;


public class ProtocolInstance implements ObvDatabase {
    static final String TABLE_NAME = "protocol_instance";

    private final ProtocolManagerSession protocolManagerSession;

    // To improve: add an expiration timestamp, updated each time a new state is written
    //  --> this timestamp should depend on the protocol type (infinite for group management)

    private UID uid;
    static final String UID_ = "uid";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private int protocolId;
    static final String PROTOCOL_ID = "protocol_id";
    private int currentStateId;
    static final String CURRENT_STATE_ID = "current_state_id";
    private Encoded encodedCurrentState;
    static final String ENCODED_CURRENT_STATE = "encoded_current_state";

    public UID getUid() {
        return uid;
    }

    public int getProtocolId() {
        return protocolId;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public int getCurrentStateId() {
        return currentStateId;
    }

    public Encoded getEncodedCurrentState() {
        return encodedCurrentState;
    }

    public ProtocolManagerSession getProtocolManagerSession() {
        return protocolManagerSession;
    }


    public void updateCurrentState(ConcreteProtocolState newState) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                CURRENT_STATE_ID + " = ?, " +
                ENCODED_CURRENT_STATE + " = ? " +
                " WHERE " + UID_ + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            this.currentStateId = newState.id;
            this.encodedCurrentState = newState.encode();
            statement.setInt(1, this.currentStateId);
            if (this.encodedCurrentState != null) {
                statement.setBytes(2, this.encodedCurrentState.getBytes());
            } else {
                statement.setNull(2, Types.BLOB);
            }
            statement.setBytes(3, uid.getBytes());
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }



    public static ProtocolInstance create(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, Identity ownedIdentity, int protocolId, ConcreteProtocolState protocolState) {
        if ((protocolInstanceUid == null) || (ownedIdentity == null) || (protocolState == null)) {
            return null;
        }
        try {
            ProtocolInstance protocolInstance = new ProtocolInstance(
                    protocolManagerSession,
                    protocolInstanceUid,
                    ownedIdentity,
                    protocolId,
                    protocolState.id,
                    protocolState.encode()
            );
            protocolInstance.insert();
            return protocolInstance;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ProtocolInstance(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, Identity ownedIdentity, int protocolId, int currentStateId, Encoded encodedCurrentState) {
        this.protocolManagerSession = protocolManagerSession;

        this.uid = protocolInstanceUid;
        this.ownedIdentity = ownedIdentity;
        this.protocolId = protocolId;
        this.currentStateId = currentStateId;
        this.encodedCurrentState = encodedCurrentState;
    }

    private ProtocolInstance(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;

        this.uid = new UID(res.getBytes(UID_));
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.protocolId = res.getInt(PROTOCOL_ID);
        this.currentStateId = res.getInt(CURRENT_STATE_ID);
        byte[] bytes = res.getBytes(ENCODED_CURRENT_STATE);
        if (bytes == null) {
            this.encodedCurrentState = null;
        } else {
            this.encodedCurrentState = new Encoded(bytes);
        }
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    PROTOCOL_ID + " INT NOT NULL, " +
                    CURRENT_STATE_ID + " INT NOT NULL, " +
                    ENCODED_CURRENT_STATE + " BLOB, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + UID_ + ", " + OWNED_IDENTITY + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING protocol_instance DATABASE FROM VERSION " + oldVersion + " TO 11");
                statement.execute("DELETE FROM protocol_instance WHERE protocol_id = 5;");
            }
            oldVersion = 11;
        }
        if (oldVersion < 33 && newVersion >= 33) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING protocol_instance DATABASE FROM VERSION " + oldVersion + " TO 33");
                statement.execute("DELETE FROM protocol_instance WHERE protocol_id = 1;");
            }
            oldVersion = 33;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, protocolId);
            statement.setInt(4, currentStateId);
            if (encodedCurrentState != null) {
                statement.setBytes(5, encodedCurrentState.getBytes());
            } else {
                statement.setNull(5, Types.BLOB);
            }
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UID_ + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }


    public static ProtocolInstance get(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, Identity ownedIdentity) {
        if ((protocolInstanceUid == null) || (ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + UID_ + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, protocolInstanceUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ProtocolInstance(protocolManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public static List<ProtocolInstance> getAllForProtocolId(ProtocolManagerSession protocolManagerSession, int protocolId) {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + PROTOCOL_ID + " = ?;")) {
            statement.setInt(1, protocolId);
            try (ResultSet res = statement.executeQuery()) {
                List<ProtocolInstance> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ProtocolInstance(protocolManagerSession, res));
                }
                return list;
            }
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public static List<ProtocolInstance> getAllForOwnedIdentityProtocolId(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, int protocolId) {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + PROTOCOL_ID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, protocolId);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ProtocolInstance> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ProtocolInstance(protocolManagerSession, res));
                }
                return list;
            }
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public static void deleteAllForOwnedIdentity(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, UID excludedProtocolInstanceUid) throws SQLException {
        if (excludedProtocolInstanceUid == null) {
            try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
                statement.setBytes(1, ownedIdentity.getBytes());
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                    " WHERE " + OWNED_IDENTITY + " = ?" +
                    " AND " + UID_ + " != ?;")) {
                statement.setBytes(1, ownedIdentity.getBytes());
                statement.setBytes(2, excludedProtocolInstanceUid.getBytes());
                statement.executeUpdate();
            }
        }
    }

    public static void deleteAllTransfer(ProtocolManagerSession protocolManagerSession) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + PROTOCOL_ID + " = ?;")) {
            statement.setInt(1, ConcreteProtocol.OWNED_IDENTITY_TRANSFER_PROTOCOL_ID);
            statement.executeUpdate();
        }
    }

    @Override
    public void wasCommitted() {
        // No hooks here
    }
}
