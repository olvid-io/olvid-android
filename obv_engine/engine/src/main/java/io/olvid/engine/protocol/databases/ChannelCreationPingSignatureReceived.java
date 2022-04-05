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

package io.olvid.engine.protocol.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;


public class ChannelCreationPingSignatureReceived implements ObvDatabase {
    static final String TABLE_NAME = "channel_creation_ping_signature_received";

    private final ProtocolManagerSession protocolManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private byte[] signature;
    static final String SIGNATURE = "signature";


    public static ChannelCreationPingSignatureReceived create(ProtocolManagerSession protocolManagerSession/*, UID contactDeviceUid, Identity contactIdentity*/, Identity ownedIdentity, byte[] signature) {
        if ((ownedIdentity == null) || (signature == null)) {
            return null;
        }
        try {
            ChannelCreationPingSignatureReceived channelCreationPingSignatureReceived = new ChannelCreationPingSignatureReceived(protocolManagerSession, /*contactDeviceUid, contactIdentity, */ownedIdentity, signature);
            channelCreationPingSignatureReceived.insert();
            return channelCreationPingSignatureReceived;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ChannelCreationPingSignatureReceived(ProtocolManagerSession protocolManagerSession/*, UID contactDeviceUid, Identity contactIdentity*/, Identity ownedIdentity, byte[] signature) {
        this.protocolManagerSession = protocolManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.signature = signature;
    }


//    private ChannelCreationPingSignatureReceived(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
//        this.protocolManagerSession = protocolManagerSession;
//
//        try {
//            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
//        } catch (DecodingException e) {
//            throw new SQLException();
//        }
//        this.signature = res.getBytes(SIGNATURE);
//    }




    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    SIGNATURE + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY (" + OWNED_IDENTITY + ", " + SIGNATURE + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 30 && newVersion >= 30) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING channel_creation_ping_signature_received DATABASE FROM VERSION " + oldVersion + " TO 30");
                statement.execute("ALTER TABLE channel_creation_ping_signature_received RENAME TO channel_creation_ping_signature_received_old");
                statement.execute("CREATE TABLE channel_creation_ping_signature_received (" +
                        " owned_identity BLOB NOT NULL, " +
                        " signature BLOB NOT NULL, " +
                        " CONSTRAINT PK_channel_creation_ping_signature_received PRIMARY KEY (owned_identity, signature))");

                statement.execute("INSERT INTO channel_creation_ping_signature_received SELECT owned_identity, signature FROM channel_creation_ping_signature_received_old");
                statement.execute("DROP TABLE channel_creation_ping_signature_received_old");
            }
            oldVersion = 30;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, signature);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY  + " = ? AND " + SIGNATURE  + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, signature);
            statement.executeUpdate();
        }
    }



    public static boolean exists(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, byte[] signature) throws SQLException {
        if ((ownedIdentity == null) || (signature == null)) {
            return false;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("SELECT 1 FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ? AND " + SIGNATURE  + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, signature);
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
