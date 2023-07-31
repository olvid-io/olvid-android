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
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;


public class ChannelCreationProtocolInstance implements ObvDatabase {
    static final String TABLE_NAME = "channel_creation_protocol_instance";

    private final ProtocolManagerSession protocolManagerSession;

    private UID contactDeviceUid;
    static final String CONTACT_DEVICE_UID = "contact_device_uid";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID protocolInstanceUid;
    static final String PROTOCOL_INSTANCE_UID = "protocol_instance_uid";

    public UID getProtocolInstanceUid() {
        return protocolInstanceUid;
    }


    public static ChannelCreationProtocolInstance create(ProtocolManagerSession protocolManagerSession, UID contactDeviceUid, Identity contactIdentity, Identity ownedIdentity, UID protocolInstanceUid) {
        if ((contactDeviceUid == null) || (contactIdentity == null) || (ownedIdentity == null) || (protocolInstanceUid == null)) {
            return null;
        }
        ProtocolInstance protocolInstance = ProtocolInstance.get(protocolManagerSession, protocolInstanceUid, ownedIdentity);
        if ((protocolInstance == null)
                || (protocolInstance.getProtocolId() != ConcreteProtocol.CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID && protocolInstance.getProtocolId() != ConcreteProtocol.CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID)) {
            return null;
        }
        try {
            ChannelCreationProtocolInstance channelCreationProtocolInstance = new ChannelCreationProtocolInstance(protocolManagerSession, contactDeviceUid, contactIdentity, ownedIdentity, protocolInstanceUid);
            channelCreationProtocolInstance.insert();
            return channelCreationProtocolInstance;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ChannelCreationProtocolInstance(ProtocolManagerSession protocolManagerSession, UID contactDeviceUid, Identity contactIdentity, Identity ownedIdentity, UID protocolInstanceUid) {
        this.protocolManagerSession = protocolManagerSession;

        this.contactDeviceUid = contactDeviceUid;
        this.contactIdentity = contactIdentity;
        this.ownedIdentity = ownedIdentity;
        this.protocolInstanceUid = protocolInstanceUid;
    }

    private ChannelCreationProtocolInstance(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;

        this.contactDeviceUid = new UID(res.getBytes(CONTACT_DEVICE_UID));
        try {
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.protocolInstanceUid = new UID(res.getBytes(PROTOCOL_INSTANCE_UID));
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    CONTACT_DEVICE_UID + " BLOB NOT NULL, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    PROTOCOL_INSTANCE_UID + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY (" + CONTACT_DEVICE_UID + ", " + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + "), " +
                    "FOREIGN KEY (" + PROTOCOL_INSTANCE_UID + ", " + OWNED_IDENTITY + ") REFERENCES " + ProtocolInstance.TABLE_NAME + "(" + ProtocolInstance.UID_ + ", " + ProtocolInstance.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM channel_creation_protocol_instance AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM protocol_instance " +
                        " WHERE uid = p.protocol_instance_uid" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?);")) {
            statement.setBytes(1, contactDeviceUid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, protocolInstanceUid.getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + CONTACT_DEVICE_UID + " = ? AND " + CONTACT_IDENTITY + " = ? AND " + OWNED_IDENTITY  + " = ?;")) {
            statement.setBytes(1, contactDeviceUid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }



    public static ChannelCreationProtocolInstance get(ProtocolManagerSession protocolManagerSession, UID contactDeviceUid, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        if ((contactDeviceUid == null) || (ownedIdentity == null) || (contactIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + CONTACT_DEVICE_UID + " = ? AND " + CONTACT_IDENTITY + " = ? AND " + OWNED_IDENTITY  + " = ?;")) {
            statement.setBytes(1, contactDeviceUid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ChannelCreationProtocolInstance(protocolManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static ChannelCreationProtocolInstance[] getAllForContact(ProtocolManagerSession protocolManagerSession, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        if ((ownedIdentity == null) || (contactIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + CONTACT_IDENTITY + " = ? AND " + OWNED_IDENTITY  + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ChannelCreationProtocolInstance> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ChannelCreationProtocolInstance(protocolManagerSession, res));
                }
                return list.toArray(new ChannelCreationProtocolInstance[0]);
            }
        }
    }

    public static void deleteAllForOwnedIdentity(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void wasCommitted() {
        // No hooks here
    }
}
