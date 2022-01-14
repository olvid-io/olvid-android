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

package io.olvid.engine.backup.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;


@SuppressWarnings("FieldMayBeFinal")
public class Backup implements ObvDatabase {
    static final String TABLE_NAME = "backup";

    private final BackupManagerSession backupManagerSession;

    private UID backupKeyUid;
    static final String BACKUP_KEY_UID = "backup_key_uid";
    private int version;
    static final String VERSION = "version";
    private boolean forExport;
    static final String FOR_EXPORT = "for_export";
    private int status;
    static final String STATUS = "status";
    private long statusChangeTimestamp;
    static final String STATUS_CHANGE_TIMESTAMP = "status_change_timestamp";
    private byte[] encryptedContent;
    static final String ENCRYPTED_CONTENT = "encrypted_content";
    private int backupJsonVersion;
    static final String BACKUP_JSON_VERSION = "backup_json_version";

    public static final int STATUS_ONGOING = 0;
    public static final int STATUS_READY = 1;
    public static final int STATUS_UPLOADED_OR_EXPORTED = 2;
    public static final int STATUS_FAILED = -1;

    public static void cleanup(BackupManagerSession backupManagerSession, UID backupKeyUid, Integer uploadedBackupVersion, Integer exportedBackupVersion, Integer latestBackupVersion) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + BACKUP_KEY_UID + " = ? " +
                " AND " +  VERSION + " NOT IN (?,?,?);")) {
            statement.setBytes(1, backupKeyUid.getBytes());
            statement.setInt(2, (uploadedBackupVersion!=null)?uploadedBackupVersion:-1);
            statement.setInt(3, (exportedBackupVersion!=null)?exportedBackupVersion:-1);
            statement.setInt(4, (latestBackupVersion!=null)?latestBackupVersion:-1);
            statement.executeUpdate();
        }
    }


    public boolean isForExport() {
        return forExport;
    }

    public int getStatus() {
        return status;
    }

    public int getBackupJsonVersion() {
        return backupJsonVersion;
    }

    public long getStatusChangeTimestamp() {
        return statusChangeTimestamp;
    }

    public int getVersion() {
        return version;
    }

    // region constructors

    public static Backup createOngoingBackup(BackupManagerSession backupManagerSession, UID backupKeyUid, int version, boolean forExport) {
        if (backupKeyUid == null) {
            return null;
        }
        try {
            Backup backup = new Backup(backupManagerSession, backupKeyUid, version, forExport, STATUS_ONGOING, System.currentTimeMillis(), null, Constants.CURRENT_BACKUP_JSON_VERSION);
            backup.insert();
            return backup;
        } catch (SQLException e) {
            return null;
        }
    }

    private Backup(BackupManagerSession backupManagerSession, UID backupKeyUid, int version, boolean forExport, int status, long statusChangeTimestamp, byte[] encryptedContent, int backupJsonVersion) {
        this.backupManagerSession = backupManagerSession;
        this.backupKeyUid = backupKeyUid;
        this.version = version;
        this.forExport = forExport;
        this.status = status;
        this.statusChangeTimestamp = statusChangeTimestamp;
        this.encryptedContent = encryptedContent;
        this.backupJsonVersion = backupJsonVersion;
    }

    private Backup(BackupManagerSession backupManagerSession, ResultSet res) throws SQLException {
        this.backupManagerSession = backupManagerSession;
        this.backupKeyUid = new UID(res.getBytes(BACKUP_KEY_UID));
        this.version = res.getInt(VERSION);
        this.forExport = res.getBoolean(FOR_EXPORT);
        this.status = res.getInt(STATUS);
        this.statusChangeTimestamp = res.getLong(STATUS_CHANGE_TIMESTAMP);
        this.encryptedContent = res.getBytes(ENCRYPTED_CONTENT);
        this.backupJsonVersion = res.getInt(BACKUP_JSON_VERSION);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    BACKUP_KEY_UID + " BLOB NOT NULL, " +
                    VERSION + " INTEGER NOT NULL, " +
                    FOR_EXPORT + " BIT NOT NULL, " +
                    STATUS + " INTEGER NOT NULL, " +
                    STATUS_CHANGE_TIMESTAMP + " INTEGER NOT NULL, " +
                    ENCRYPTED_CONTENT + " BLOB, " +
                    BACKUP_JSON_VERSION + " INTEGER NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + BACKUP_KEY_UID + ", " + VERSION + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, backupKeyUid.getBytes());
            statement.setInt(2, version);
            statement.setBoolean(3, forExport);
            statement.setInt(4, status);
            statement.setLong(5, statusChangeTimestamp);
            statement.setBytes(6, encryptedContent);
            statement.setInt(7, backupJsonVersion);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + BACKUP_KEY_UID + " = ? AND " + VERSION + " = ?;")) {
            statement.setBytes(1, backupKeyUid.getBytes());
            statement.setInt(2, version);
            statement.executeUpdate();
        }
    }

    static void deleteAll(BackupManagerSession backupManagerSession) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + ";")) {
            statement.executeUpdate();
        }
    }


    public static Backup get(BackupManagerSession backupManagerSession, UID backupKeyUid, int version) {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + BACKUP_KEY_UID + " = ? AND " + VERSION + " = ?;")){
            preparedStatement.setBytes(1, backupKeyUid.getBytes());
            preparedStatement.setInt(2, version);
            ResultSet res = preparedStatement.executeQuery();
            if (res.next()) {
                return new Backup(backupManagerSession, res);
            } else {
                return null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public void setReady(byte[] encryptedContent) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + STATUS + " = ?, " +
                ENCRYPTED_CONTENT + " = ?, " +
                STATUS_CHANGE_TIMESTAMP + " = ? " +
                " WHERE " + BACKUP_KEY_UID + " = ? AND " +
                VERSION + " = ?;")) {
            long timestamp = System.currentTimeMillis();
            statement.setInt(1, STATUS_READY);
            statement.setBytes(2, encryptedContent);
            statement.setLong(3, timestamp);
            statement.setBytes(4, backupKeyUid.getBytes());
            statement.setInt(5, version);
            statement.executeUpdate();
            this.status = STATUS_READY;
            this.encryptedContent = encryptedContent;
            this.statusChangeTimestamp = timestamp;
        }
    }

    public void setUploadedOrExported() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + STATUS + " = ?, " +
                STATUS_CHANGE_TIMESTAMP + " = ? " +
                " WHERE " + BACKUP_KEY_UID + " = ? AND " +
                VERSION + " = ? AND " +
                FOR_EXPORT + " = 0;")) {
            long timestamp = System.currentTimeMillis();
            statement.setInt(1, STATUS_UPLOADED_OR_EXPORTED);
            statement.setLong(2, timestamp);
            statement.setBytes(3, backupKeyUid.getBytes());
            statement.setInt(4, version);
            statement.executeUpdate();
            this.status = STATUS_UPLOADED_OR_EXPORTED;
            this.statusChangeTimestamp = timestamp;
        }
    }

    public void setFailed() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + STATUS + " = ?, " +
                STATUS_CHANGE_TIMESTAMP + " = ? " +
                " WHERE " + BACKUP_KEY_UID + " = ? AND " +
                VERSION + " = ? AND " +
                FOR_EXPORT + " = 0;")) {
            long timestamp = System.currentTimeMillis();
            statement.setInt(1, STATUS_FAILED);
            statement.setLong(2, timestamp);
            statement.setBytes(3, backupKeyUid.getBytes());
            statement.setInt(4, version);
            statement.executeUpdate();
            this.status = STATUS_FAILED;
            this.statusChangeTimestamp = timestamp;
        }
    }

    // endregion

    @Override
    public void wasCommitted() {

    }
}
