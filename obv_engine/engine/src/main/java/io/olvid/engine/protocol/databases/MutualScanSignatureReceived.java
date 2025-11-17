/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;


public class MutualScanSignatureReceived implements ObvDatabase {
    static final String TABLE_NAME = "mutual_scan_signature_received";

    private final ProtocolManagerSession protocolManagerSession;

    private final Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private final byte[] signature;
    static final String SIGNATURE = "signature";


    // region constructors

    public static MutualScanSignatureReceived create(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, byte[] signature) {
        if (ownedIdentity == null || signature == null) {
            return null;
        }
        try {
            MutualScanSignatureReceived mutualScanSignatureReceived = new MutualScanSignatureReceived(protocolManagerSession, ownedIdentity, signature);
            mutualScanSignatureReceived.insert();
            return mutualScanSignatureReceived;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    private MutualScanSignatureReceived(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, byte[] signature) {
        this.protocolManagerSession = protocolManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.signature = signature;
    }


    private MutualScanSignatureReceived(ProtocolManagerSession protocolManagerSession, ResultSet res) throws SQLException {
        this.protocolManagerSession = protocolManagerSession;

        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.signature = res.getBytes(SIGNATURE);
    }

    // endregion


    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    SIGNATURE + " BLOB NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY (" + OWNED_IDENTITY + ", " + SIGNATURE + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 23 && newVersion >= 23) {
            Logger.d("CREATING `mutual_scan_signature_received` TABLE AS PART OF VERSION 23");
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS mutual_scan_signature_received (" +
                        " owned_identity BLOB NOT NULL, " +
                        " signature BLOB NOT NULL, " +
                        "CONSTRAINT PK_mutual_scan_signature_received PRIMARY KEY (owned_identity, signature));");
            }
            oldVersion = 23;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("MutualScanSignatureReceived.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES (?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, signature);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("MutualScanSignatureReceived.delete",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY  + " = ? AND " + SIGNATURE + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, signature);
            statement.executeUpdate();
        }
    }

    // endregion

    public static boolean exists(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity, byte[] commitment) throws SQLException {
        if (ownedIdentity == null || commitment == null) {
            return false;
        }
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("MutualScanSignatureReceived.exists",
                "SELECT 1 FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ? AND " + SIGNATURE + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, commitment);
            try (ResultSet res = statement.executeQuery()) {
                return res.next();
            }
        }
    }

    public static void deleteAllForOwnedIdentity(ProtocolManagerSession protocolManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("MutualScanSignatureReceived.deleteAllForOwnedIdentity",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }


    @Override
    public void wasCommitted() {
        // No hooks here
    }
}
