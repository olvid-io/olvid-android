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
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;

public class PendingServerQuery implements ObvDatabase {
    static final String TABLE_NAME = "server_query";

    private final FetchManagerSession fetchManagerSession;

    static final String UID_ = "uid";
    private UID uid;
    static final String ENCODED_QUERY = "encoded_query";
    private Encoded encodedQuery;
    static final String CREATION_TIMESTAMP = "creation_timestamp";
    private long creationTimestamp;
    static final String WEBSOCKET = "websocket";
    private boolean webSocket;

    public UID getUid() {
        return uid;
    }

    public Encoded getEncodedQuery() {
        return encodedQuery;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    public boolean isWebSocket() {
        return webSocket;
    }

    // region constructors
    
    public static PendingServerQuery create(FetchManagerSession fetchManagerSession, ServerQuery serverQuery, PRNGService prng) {
        if (serverQuery == null) {
            return null;
        }
        try {
            Encoded encodedQuery = serverQuery.encode();
            UID uid = new UID(prng);
            PendingServerQuery pendingServerQuery = new PendingServerQuery(fetchManagerSession, uid, encodedQuery, serverQuery.isWebSocket());
            pendingServerQuery.insert();
            return pendingServerQuery;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    private PendingServerQuery(FetchManagerSession fetchManagerSession, UID uid, Encoded encodedQuery, boolean webSocket) {
        this.fetchManagerSession = fetchManagerSession;

        this.uid = uid;
        this.encodedQuery = encodedQuery;
        this.creationTimestamp = System.currentTimeMillis();
        this.webSocket = webSocket;
    }

    private PendingServerQuery(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;

        this.uid = new UID(res.getBytes(UID_));
        this.encodedQuery = new Encoded(res.getBytes(ENCODED_QUERY));
        this.creationTimestamp = res.getLong(CREATION_TIMESTAMP);
        this.webSocket = res.getBoolean(WEBSOCKET);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB PRIMARY KEY, " +
                    ENCODED_QUERY + " BLOB NOT NULL, " +
                    CREATION_TIMESTAMP + " BIGINT NOT NULL, " +
                    WEBSOCKET + " BIT NOT NULL " +
                    " );");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 31 && newVersion >= 31) {
            Logger.d("MIGRATING `server_query` DATABASE FROM VERSION " + oldVersion + " TO 31");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE `server_query` ADD COLUMN `creation_timestamp` BIGINT NOT NULL DEFAULT " + System.currentTimeMillis());
            }
            oldVersion = 31;
        }
        if (oldVersion < 37 && newVersion >= 37) {
            Logger.d("MIGRATING `server_query` DATABASE FROM VERSION " + oldVersion + " TO 36");
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE `server_query` ADD COLUMN `websocket` BIT NOT NULL DEFAULT 0");
            }
            oldVersion = 37;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, encodedQuery.getBytes());
            statement.setLong(3, creationTimestamp);
            statement.setBoolean(4, webSocket);
            statement.executeUpdate();
            this.commitHookBits |= HOOK_BIT_INSERTED;
            fetchManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UID_  + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static PendingServerQuery get(FetchManagerSession fetchManagerSession, UID uid) {
        if (uid == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + UID_  + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new PendingServerQuery(fetchManagerSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public static PendingServerQuery[] getAll(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<PendingServerQuery> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new PendingServerQuery(fetchManagerSession, res));
                }
                return list.toArray(new PendingServerQuery[0]);
            }
        } catch (SQLException e) {
            return new PendingServerQuery[0];
        }
    }

    // endregion

    // region hooks

    public interface PendingServerQueryListener {
        void newPendingServerQuery(PendingServerQuery pendingServerQuery);
    }


    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            if (fetchManagerSession.pendingServerQueryListener != null) {
                fetchManagerSession.pendingServerQueryListener.newPendingServerQuery(this);
            }
        }
        commitHookBits = 0;
    }

    // endregion
}
