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

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;


public class ChannelCreationPingSignatureReceived implements ObvDatabase {
    static final String TABLE_NAME = "channel_creation_ping_signature_received";

    private final ProtocolManagerSession protocolManagerSession;

    private UID contactDeviceUid;
    static final String CONTACT_DEVICE_UID = "contact_device_uid";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private byte[] signature;
    static final String SIGNATURE = "signature";


    public static ChannelCreationPingSignatureReceived create(ProtocolManagerSession protocolManagerSession, UID contactDeviceUid, Identity contactIdentity, Identity ownedIdentity, byte[] signature) {
        if ((contactDeviceUid == null) || (contactIdentity == null) || (ownedIdentity == null) || (signature == null)) {
            return null;
        }
        try {
            ChannelCreationPingSignatureReceived channelCreationPingSignatureReceived = new ChannelCreationPingSignatureReceived(protocolManagerSession, contactDeviceUid, contactIdentity, ownedIdentity, signature);
            channelCreationPingSignatureReceived.insert();
            return channelCreationPingSignatureReceived;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ChannelCreationPingSignatureReceived(ProtocolManagerSession protocolManagerSession, UID contactDeviceUid, Identity contactIdentity, Identity ownedIdentity, byte[] signature) {
        this.protocolManagerSession = protocolManagerSession;
        this.contactDeviceUid = contactDeviceUid;
        this.contactIdentity = contactIdentity;
        this.ownedIdentity = ownedIdentity;
        this.signature = signature;
    }


    private ChannelCreationPingSignatureReceived(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;

        this.contactDeviceUid = new UID(res.getBytes(CONTACT_DEVICE_UID));
        try {
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.signature = res.getBytes(SIGNATURE);
    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    CONTACT_DEVICE_UID + " BLOB NOT NULL, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    SIGNATURE + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY (" + CONTACT_DEVICE_UID + ", " + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + ", " + SIGNATURE + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        // No upgrade
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?);")) {
            statement.setBytes(1, contactDeviceUid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, signature);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + CONTACT_DEVICE_UID + " = ? AND " + CONTACT_IDENTITY + " = ? AND " + OWNED_IDENTITY  + " = ? AND " + SIGNATURE  + " = ?;")) {
            statement.setBytes(1, contactDeviceUid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, signature);
            statement.executeUpdate();
        }
    }



    public static boolean exists(ProtocolManagerSession protocolManagerSession, UID contactDeviceUid, Identity contactIdentity, Identity ownedIdentity, byte[] signature) throws SQLException {
        if (contactDeviceUid == null || ownedIdentity == null || contactIdentity == null || signature == null) {
            return false;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT 1 FROM " + TABLE_NAME + " WHERE " + CONTACT_DEVICE_UID + " = ? AND " + CONTACT_IDENTITY + " = ? AND " + OWNED_IDENTITY + " = ? AND " + SIGNATURE  + " = ?;")) {
            statement.setBytes(1, contactDeviceUid.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, signature);
            try (ResultSet res = statement.executeQuery()) {
                return res.next();
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
