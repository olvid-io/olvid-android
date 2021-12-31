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
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.datatypes.ChildToParentProtocolMessageInputs;
import io.olvid.engine.protocol.datatypes.GenericProtocolMessageToSend;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;

public class LinkBetweenProtocolInstances implements ObvDatabase {
    static final String TABLE_NAME = "link_between_protocol_instances";

    private final ProtocolManagerSession protocolManagerSession;

    private UID childProtocolInstanceUid;
    static final String CHILD_PROTOCOL_INSTANCE_UID = "child_protocol_instance_uid";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private int expectedChildStateId;
    static final String EXPECTED_CHILD_STATE_ID = "expected_child_state_id";
    private UID parentProtocolInstanceUid;
    static final String PARENT_PROTOCOL_INSTANCE_UID = "parent_protocol_instance_uid";
    private int parentProtocolId;
    static final String PARENT_PROTOCOL_ID = "parent_protocol_id";
    private int messageToSendId;
    static final String MESSAGE_TO_SEND_ID = "message_to_send_id";

    public UID getChildProtocolInstanceUid() {
        return childProtocolInstanceUid;
    }

    public UID getParentProtocolInstanceUid() {
        return parentProtocolInstanceUid;
    }

    public static LinkBetweenProtocolInstances create(ProtocolManagerSession protocolManagerSession, UID childProtocolInstanceUid, Identity ownedIdentity,
                                                      int expectedChildStateId, UID parentProtocolInstanceUid, int parentProtocolId, int messageToSendId) {
        if ((childProtocolInstanceUid == null) || (parentProtocolInstanceUid == null) || (ownedIdentity == null)) {
            return null;
        }
        try {
            LinkBetweenProtocolInstances linkBetweenProtocolInstances = new LinkBetweenProtocolInstances(protocolManagerSession, childProtocolInstanceUid, ownedIdentity, expectedChildStateId, parentProtocolInstanceUid, parentProtocolId, messageToSendId);
            linkBetweenProtocolInstances.insert();
            return linkBetweenProtocolInstances;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private LinkBetweenProtocolInstances(ProtocolManagerSession protocolManagerSession, UID childProtocolInstanceUid, Identity ownedIdentity,
                                         int expectedChildStateId, UID parentProtocolInstanceUid, int parentProtocolId, int messageToSendId) {
        this.protocolManagerSession = protocolManagerSession;

        this.childProtocolInstanceUid = childProtocolInstanceUid;
        this.ownedIdentity = ownedIdentity;
        this.expectedChildStateId = expectedChildStateId;
        this.parentProtocolInstanceUid = parentProtocolInstanceUid;
        this.parentProtocolId = parentProtocolId;

        this.messageToSendId = messageToSendId;
    }

    private LinkBetweenProtocolInstances(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;

        this.childProtocolInstanceUid = new UID(res.getBytes(CHILD_PROTOCOL_INSTANCE_UID));
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.expectedChildStateId = res.getInt(EXPECTED_CHILD_STATE_ID);
        this.parentProtocolInstanceUid = new UID(res.getBytes(PARENT_PROTOCOL_INSTANCE_UID));
        this.parentProtocolId = res.getInt(PARENT_PROTOCOL_ID);
        this.messageToSendId = res.getInt(MESSAGE_TO_SEND_ID);
    }



    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    CHILD_PROTOCOL_INSTANCE_UID + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    EXPECTED_CHILD_STATE_ID + " INT NOT NULL, " +
                    PARENT_PROTOCOL_INSTANCE_UID + " BLOB NOT NULL, " +
                    PARENT_PROTOCOL_ID + " INT NOT NULL, " +
                    MESSAGE_TO_SEND_ID + " INT NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY (" + CHILD_PROTOCOL_INSTANCE_UID + ", " + OWNED_IDENTITY + ", " + EXPECTED_CHILD_STATE_ID + "), " +
                    "FOREIGN KEY (" + PARENT_PROTOCOL_INSTANCE_UID + ", " + OWNED_IDENTITY + ") REFERENCES " + ProtocolInstance.TABLE_NAME + "(" + ProtocolInstance.UID_ + ", " + ProtocolInstance.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM link_between_protocol_instances WHERE parent_protocol_id = 5;");
            }
            oldVersion = 11;
        }
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM link_between_protocol_instances AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM protocol_instance " +
                        " WHERE uid = p.parent_protocol_instance_uid" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?,?);")) {
            statement.setBytes(1, childProtocolInstanceUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, expectedChildStateId);
            statement.setBytes(4, parentProtocolInstanceUid.getBytes());
            statement.setInt(5, parentProtocolId);
            statement.setInt(6, messageToSendId);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + CHILD_PROTOCOL_INSTANCE_UID + " = ? AND " + OWNED_IDENTITY  + " = ? AND " + EXPECTED_CHILD_STATE_ID + " = ?;")) {
            statement.setBytes(1, childProtocolInstanceUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, expectedChildStateId);
            statement.executeUpdate();
        }
    }



    public static LinkBetweenProtocolInstances get(ProtocolManagerSession protocolManagerSession, UID childProtocolInstanceUid, Identity ownedIdentity, int expectedChildStateId) {
        if ((childProtocolInstanceUid == null) || (ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + CHILD_PROTOCOL_INSTANCE_UID + " = ? AND " + OWNED_IDENTITY  + " = ? AND " + EXPECTED_CHILD_STATE_ID + " = ?;")) {
            statement.setBytes(1, childProtocolInstanceUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, expectedChildStateId);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new LinkBetweenProtocolInstances(protocolManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public static LinkBetweenProtocolInstances[] getAllParentLinks(ProtocolManagerSession protocolManagerSession, UID childProtocolInstanceUid, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + CHILD_PROTOCOL_INSTANCE_UID + " = ? AND " + OWNED_IDENTITY  + " = ?;")) {
            statement.setBytes(1, childProtocolInstanceUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<LinkBetweenProtocolInstances> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new LinkBetweenProtocolInstances(protocolManagerSession, res));
                }
                return list.toArray(new LinkBetweenProtocolInstances[0]);
            }
        }
    }

    public static LinkBetweenProtocolInstances[] getAllChildLinks(ProtocolManagerSession protocolManagerSession, UID parentProtocolInstanceUid, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + PARENT_PROTOCOL_INSTANCE_UID + " = ? AND " + OWNED_IDENTITY  + " = ?;")) {
            statement.setBytes(1, parentProtocolInstanceUid.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<LinkBetweenProtocolInstances> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new LinkBetweenProtocolInstances(protocolManagerSession, res));
                }
                return list.toArray(new LinkBetweenProtocolInstances[0]);
            }
        }
    }

    public static void deleteAllForOwnedIdentity(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static GenericProtocolMessageToSend getGenericProtocolMessageToSendWhenChildProtocolInstanceReachesAState(ProtocolManagerSession protocolManagerSession, UID childProtocolInstanceUid, Identity ownedIdentity, ConcreteProtocolState childProtocolState) {
        LinkBetweenProtocolInstances linkBetweenProtocolInstances = get(protocolManagerSession, childProtocolInstanceUid, ownedIdentity, childProtocolState.id);
        if (linkBetweenProtocolInstances == null) {
            return null;
        }
        Logger.d("Found a LinkBetweenProtocolInstances");
        Encoded[] inputs = new ChildToParentProtocolMessageInputs(childProtocolInstanceUid, childProtocolState).toEncodedInputs();
        ProtocolInstance parentProtocolInstance = ProtocolInstance.get(protocolManagerSession, linkBetweenProtocolInstances.parentProtocolInstanceUid, linkBetweenProtocolInstances.ownedIdentity);
        if (parentProtocolInstance == null) {
            return null;
        }
        try {
            linkBetweenProtocolInstances.delete();
        } catch (SQLException ignored) {} // it is not a problem if the delete fails, so no need to handle the exception
        return new GenericProtocolMessageToSend(SendChannelInfo.createLocalChannelInfo(parentProtocolInstance.getOwnedIdentity()),
                parentProtocolInstance.getProtocolId(),
                parentProtocolInstance.getUid(),
                linkBetweenProtocolInstances.messageToSendId,
                inputs,
                false);
    }

    @Override
    public void wasCommitted() {
        // No hooks here
    }

}
