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

package io.olvid.engine.backup.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;


@SuppressWarnings("FieldMayBeFinal")
public class DeviceBackupSeed implements ObvDatabase {
    static final String TABLE_NAME = "device_backup_seed";

    private final BackupManagerSession backupManagerSession;

    private final BackupSeed backupSeed; // primary key
    static final String BACKUP_SEED = "backup_seed";
    private final String server; // the server on which the device backup should be uploaded
    static final String SERVER = "server";
    private boolean active; // there can be at most one active key at a time. Inactive keys are being cleaned from server and deleted
    static final String ACTIVE = "active";
    private long nextBackupTimestamp;
    static final String NEXT_BACKUP_TIMESTAMP = "next_backup_timestamp";

    public BackupSeed getBackupSeed() {
        return backupSeed;
    }

    public String getServer() {
        return server;
    }

    public boolean isActive() {
        return active;
    }

    public long getNextBackupTimestamp() {
        return nextBackupTimestamp;
    }

    // region constructors

    public static DeviceBackupSeed create(BackupManagerSession backupManagerSession, BackupSeed backupSeed, String server) {
        if (backupSeed == null || server == null) {
            return null;
        }
        try {
            DeviceBackupSeed backupKey = new DeviceBackupSeed(backupManagerSession, backupSeed, server);
            backupKey.insert();
            return backupKey;
        } catch (SQLException e) {
            return null;
        }
    }

    private DeviceBackupSeed(BackupManagerSession backupManagerSession, BackupSeed backupSeed, String server) {
        this.backupManagerSession = backupManagerSession;
        this.backupSeed = backupSeed;
        this.server = server;
        this.active = true;
        this.nextBackupTimestamp = 0;
    }



    private DeviceBackupSeed(BackupManagerSession backupManagerSession, ResultSet res) throws SQLException {
        this.backupManagerSession = backupManagerSession;
        BackupSeed seed;
        try {
            seed = new BackupSeed(res.getBytes(BACKUP_SEED));
        } catch (Exception e) {
            Logger.x(e);
            seed = null;
        }
        this.backupSeed = seed;
        this.server = res.getString(SERVER);
        this.active = res.getBoolean(ACTIVE);
        this.nextBackupTimestamp = res.getLong(NEXT_BACKUP_TIMESTAMP);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    BACKUP_SEED + " BLOB PRIMARY KEY, " +
                    SERVER + " TEXT NOT NULL, " +
                    ACTIVE + " BIY NOT NULL, " +
                    NEXT_BACKUP_TIMESTAMP + " BIGINT NOT NULL " +
                    ");"
            );
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 44 && newVersion >= 44) {
            Logger.d("CREATING `device_backup_seed` DATABASE FOR VERSION 44");
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS device_backup_seed (" +
                        " backup_seed BLOB PRIMARY KEY, " +
                        " server TEXT NOT NULL, " +
                        " active BIT NOT NULL," +
                        " next_backup_timestamp BIGINT NOT NULL " +
                        ");");
            }
            oldVersion = 44;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DeviceBackupSeed.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?);")) {
            statement.setBytes(1, backupSeed.getBackupSeedBytes());
            statement.setString(2, server);
            statement.setBoolean(3, active);
            statement.setLong(4, nextBackupTimestamp);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DeviceBackupSeed.delete",
                "DELETE FROM " + TABLE_NAME + " WHERE " + BACKUP_SEED + " = ?;")) {
            statement.setBytes(1, backupSeed.getBackupSeedBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region setters

    public void markBackupKeyInactive() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DeviceBackupSeed.markBackupKeyInactive",
                "UPDATE " + TABLE_NAME +
                " SET " + ACTIVE + " = ? " +
                " WHERE " + BACKUP_SEED + " = ?;")) {
            statement.setBoolean(1, false);
            statement.setBytes(2, backupSeed.getBackupSeedBytes());
            statement.executeUpdate();
            this.active = false;
        }
    }

    public void setNextBackupTimestamp(long nextBackupTimestamp) throws SQLException {
        if (!active) {
            return;
        }
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DeviceBackupSeed.setNextBackupTimestamp",
                "UPDATE " + TABLE_NAME +
                " SET " + NEXT_BACKUP_TIMESTAMP + " = ? " +
                " WHERE " + BACKUP_SEED + " = ?;")) {
            statement.setLong(1, nextBackupTimestamp);
            statement.setBytes(2, backupSeed.getBackupSeedBytes());
            statement.executeUpdate();
            this.nextBackupTimestamp = nextBackupTimestamp;
        }
    }

    // endregion

    // region getters

    public static DeviceBackupSeed get(BackupManagerSession backupManagerSession, BackupSeed backupSeed) throws SQLException {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("DeviceBackupSeed.get",
                "SELECT * FROM " + TABLE_NAME + " WHERE " + BACKUP_SEED + " = ?;")){
            preparedStatement.setBytes(1, backupSeed.getBackupSeedBytes());
            ResultSet res = preparedStatement.executeQuery();
            if (res.next()) {
                return new DeviceBackupSeed(backupManagerSession, res);
            } else {
                return null;
            }
        }
    }

    public static DeviceBackupSeed getActive(BackupManagerSession backupManagerSession) throws SQLException {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("DeviceBackupSeed.getActive",
                "SELECT * FROM " + TABLE_NAME + " WHERE " + ACTIVE + " = ?;")){
            preparedStatement.setBoolean(1, true);
            ResultSet res = preparedStatement.executeQuery();
            if (res.next()) {
                return new DeviceBackupSeed(backupManagerSession, res);
            } else {
                return null;
            }
        }
    }

    public static DeviceBackupSeed[] getAllInactive(BackupManagerSession backupManagerSession) throws SQLException {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("DeviceBackupSeed.getAllInactive",
                "SELECT * FROM " + TABLE_NAME + " WHERE " + ACTIVE + " = ?;")){
            preparedStatement.setBoolean(1, false);
            ResultSet res = preparedStatement.executeQuery();
            List<DeviceBackupSeed> list = new ArrayList<>();
            while (res.next()) {
                list.add(new DeviceBackupSeed(backupManagerSession, res));
            }
            return list.toArray(new DeviceBackupSeed[0]);
        }
    }

    // endregion

    // region hooks

    @Override
    public void wasCommitted() {
    }

    // endregion
}
