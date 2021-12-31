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

package io.olvid.engine.engine.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.engine.datatypes.EngineSession;


public class EngineDbSchemaVersion implements ObvDatabase {
    static final String TABLE_NAME = "engine_db_schema_version";

    private final EngineSession engineSession;

    private int version;
    static final String VERSION = "version";


    public int getVersion() {
        return version;
    }

    private EngineDbSchemaVersion(EngineSession engineSession, ResultSet res) throws SQLException {
        this.engineSession = engineSession;
        this.version = res.getInt(VERSION);
    }

    public static void createTable(Session session) throws SQLException {
        try (PreparedStatement statement = session.prepareStatement("SELECT name FROM sqlite_master WHERE type=? AND name = ?")) {
            statement.setString(1, "table");
            statement.setString(2, TABLE_NAME);
            try (ResultSet res = statement.executeQuery()) {
                if (!res.next()) {
                    // table does not exist yet, so create it and insert the current version number
                    try (Statement createStatement = session.createStatement()) {
                        createStatement.execute("CREATE TABLE " + TABLE_NAME + " (" + VERSION + " INT NOT NULL);");
                    }
                    try (PreparedStatement insertStatement = session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?)")) {
                        insertStatement.setInt(1, Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION);
                        insertStatement.executeUpdate();
                    }
                }
            }
        }
    }

    public static EngineDbSchemaVersion get(EngineSession engineSession) {
        try (PreparedStatement statement = engineSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new EngineDbSchemaVersion(engineSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public void update(int version) throws SQLException {
        try (PreparedStatement statement = engineSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + VERSION + " = ?;")) {
            statement.setInt(1, version);
            statement.executeUpdate();
            this.version = version;
        }
    }


    @Override
    public void delete() throws SQLException {
        Logger.e("Deletion in table " + TABLE_NAME + " Is Prohibited");
        throw new SQLException();
    }

    @Override
    public void insert() throws SQLException {
        Logger.e("Insertion in table " + TABLE_NAME + " Is Prohibited");
        throw new SQLException();
    }

    @Override
    public void wasCommitted() {
        // Nothing to do
    }
}
