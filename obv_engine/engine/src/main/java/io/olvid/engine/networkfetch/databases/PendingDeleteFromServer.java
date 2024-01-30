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

package io.olvid.engine.networkfetch.databases;

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
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;


public class PendingDeleteFromServer implements ObvDatabase {
    static final String TABLE_NAME = "pending_delete_from_server";

    private final FetchManagerSession fetchManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private UID messageUid;
    static final String MESSAGE_UID = "message_uid";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }



    public static PendingDeleteFromServer create(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid) {
        if (ownedIdentity == null || messageUid == null) {
            return null;
        }
        try {
            PendingDeleteFromServer pendingDeleteFromServer = new PendingDeleteFromServer(fetchManagerSession, ownedIdentity, messageUid);
            pendingDeleteFromServer.insert();
            return pendingDeleteFromServer;
        } catch (SQLException e) {
            // no log, exception may be normal in case of duplicate delete during initial queueing
            return null;
        }
    }

    private PendingDeleteFromServer(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid) {
        this.fetchManagerSession = fetchManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
    }

    private PendingDeleteFromServer(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            e.printStackTrace();
        }
        this.messageUid = new UID(res.getBytes(MESSAGE_UID));
    }

    public static PendingDeleteFromServer get(FetchManagerSession fetchManagerSession, Identity ownedIdentity, UID messageUid) {
        if (ownedIdentity == null || messageUid == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new PendingDeleteFromServer(fetchManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static PendingDeleteFromServer[] getAll(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<PendingDeleteFromServer> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new PendingDeleteFromServer(fetchManagerSession, res));
                }
                return list.toArray(new PendingDeleteFromServer[0]);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new PendingDeleteFromServer[0];
        }
    }

    public static void deleteAllForOwnedIdentity(FetchManagerSession protocolManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = protocolManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    MESSAGE_UID + " BLOB NOT NULL, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + OWNED_IDENTITY + ", " + MESSAGE_UID + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 15 && newVersion >= 15) {
            Logger.d("MIGRATING `pending_delete_from_server` DATABASE FROM VERSION " + oldVersion + " TO 15");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE pending_delete_from_server RENAME TO old_pending_delete_from_server");
                statement.execute("CREATE TABLE IF NOT EXISTS pending_delete_from_server (" +
                        " owned_identity BLOB NOT NULL, " +
                        " message_uid BLOB NOT NULL, " +
                        " CONSTRAINT PK_pending_delete_from_server PRIMARY KEY(owned_identity, message_uid));");
                statement.execute("INSERT INTO pending_delete_from_server SELECT m.to_identity, p.message_uid " +
                        " FROM old_pending_delete_from_server AS p " +
                        " INNER JOIN inbox_message AS m ON p.message_uid = m.uid");
                statement.execute("DROP TABLE old_pending_delete_from_server");
            }
            oldVersion = 15;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES(?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.executeUpdate();
            this.commitHookBits |= HOOK_BIT_INSERT;
            fetchManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + MESSAGE_UID + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, messageUid.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region hooks

    public interface PendingDeleteFromServerListener {
        void newPendingDeleteFromServer(Identity ownedIdentity, UID messageUid);
    }

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERT = 0x1;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERT) != 0) {
            if (fetchManagerSession.pendingDeleteFromServerListener != null) {
                fetchManagerSession.pendingDeleteFromServerListener.newPendingDeleteFromServer(ownedIdentity, messageUid);
            }
        }
        commitHookBits = 0;
    }

    // endregion
}
