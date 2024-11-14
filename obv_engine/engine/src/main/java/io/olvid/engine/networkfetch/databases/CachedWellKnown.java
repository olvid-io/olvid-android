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
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;

public class CachedWellKnown implements ObvDatabase {
    static final String TABLE_NAME = "cached_well_known";

    private final FetchManagerSession fetchManagerSession;

    private String server;
    static final String SERVER = "server";
    private String serializedWellKnown;
    static final String SERIALIZED_WELL_KNOWN = "serialized_well_known";
    private long downloadTimestamp;
    static final String DOWNLOAD_TIMESTAMP = "download_timestamp";

    public String getServer() {
        return server;
    }

    public String getSerializedWellKnown() {
        return serializedWellKnown;
    }

    public long getDownloadTimestamp() {
        return downloadTimestamp;
    }

    // region getters

    public static List<CachedWellKnown> getAll(FetchManagerSession fetchManagerSession) {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<CachedWellKnown> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new CachedWellKnown(fetchManagerSession, res));
                }
                return list;
            }
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    public static CachedWellKnown get(FetchManagerSession fetchManagerSession, String server) throws SQLException {
        if (server == null) {
            return null;
        }
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + SERVER + " = ?;")) {
            statement.setString(1, server);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new CachedWellKnown(fetchManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    // endregion

    // region constructors

    public static CachedWellKnown create(FetchManagerSession fetchManagerSession, String server, String serializedWellKnown) {
        if (server == null || serializedWellKnown == null) {
            return null;
        }
        try {
            CachedWellKnown cachedWellKnown = new CachedWellKnown(fetchManagerSession, server, serializedWellKnown, System.currentTimeMillis());
            cachedWellKnown.insert();
            return cachedWellKnown;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }


    public CachedWellKnown(FetchManagerSession fetchManagerSession, String server, String serializedWellKnown, long downloadTimestamp) {
        this.fetchManagerSession = fetchManagerSession;

        this.server = server;
        this.serializedWellKnown = serializedWellKnown;
        this.downloadTimestamp = downloadTimestamp;
    }

    public CachedWellKnown(FetchManagerSession fetchManagerSession, ResultSet res) throws SQLException {
        this.fetchManagerSession = fetchManagerSession;

        this.server = res.getString(SERVER);
        this.serializedWellKnown = res.getString(SERIALIZED_WELL_KNOWN);
        this.downloadTimestamp = res.getLong(DOWNLOAD_TIMESTAMP);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    SERVER + " TEXT PRIMARY KEY, " +
                    SERIALIZED_WELL_KNOWN + " TEXT NOT NULL, " +
                    DOWNLOAD_TIMESTAMP + " BIGINT NOT NULL);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 19 && newVersion >= 19) {
            Logger.d("MIGRATING `cached_well_known` DATABASE FROM VERSION " + oldVersion + " TO 19");
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS cached_well_known (server TEXT PRIMARY KEY, serialized_well_known TEXT NOT NULL, download_timestamp BIGINT NOT NULL);");
            }
            oldVersion = 19;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?);")) {
            statement.setString(1, server);
            statement.setString(2, serializedWellKnown);
            statement.setLong(3, downloadTimestamp);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + SERVER  + " = ?;")) {
            statement.setString(1, server);
            statement.executeUpdate();
        }
    }

    public void update(String serializedWellKnown) throws SQLException {
        try (PreparedStatement statement = fetchManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_WELL_KNOWN + " = ?, " +
                DOWNLOAD_TIMESTAMP + " = ? " +
                " WHERE " + SERVER + " = ?;")) {
            long timestamp = System.currentTimeMillis();
            statement.setString(1, serializedWellKnown);
            statement.setLong(2, timestamp);
            statement.setString(3, server);
            statement.executeUpdate();
            this.serializedWellKnown = serializedWellKnown;
            this.downloadTimestamp = timestamp;
        }
    }

    // endregion

    @Override
    public void wasCommitted() {
        // no hook yet
    }

}
