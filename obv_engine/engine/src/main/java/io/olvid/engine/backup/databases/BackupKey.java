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

package io.olvid.engine.backup.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;


@SuppressWarnings("FieldMayBeFinal")
public class BackupKey implements ObvDatabase {
    static final String TABLE_NAME = "backup_key";

    private final BackupManagerSession backupManagerSession;

    private UID uid; // can also be used as a unique identifier to locate the backup file in the cloud
    static final String UID_ = "uid";
    private EncryptionPublicKey encryptionPublicKey;
    static final String ENCRYPTION_PUBLIC_KEY = "encryption_public_key";
    private MACKey macKey;
    static final String MAC_KEY = "mac_key";
    private long keyGenerationTimestamp;
    static final String KEY_GENERATION_TIMESTAMP = "key_generation_timestamp";
    private long lastSuccessfulKeyVerificationTimestamp;
    static final String LAST_SUCCESSFUL_KEY_VERIFICATION_TIMESTAMP = "last_successful_key_verification_timestamp";
    private long lastKeyVerificationPromptTimestamp;
    static final String LAST_KEY_VERIFICATION_PROMPT_TIMESTAMP = "last_key_verification_prompt_timestamp";
    private int successfulVerificationCount;
    static final String SUCCESSFUL_VERIFICATION_COUNT = "successful_verification_count";
    private Integer uploadedBackupVersion;
    static final String UPLOADED_BACKUP_VERSION = "uploaded_backup_version";
    private Integer exportedBackupVersion;
    static final String EXPORTED_BACKUP_VERSION = "exported_backup_version";
    private Integer latestBackupVersion;
    static final String LATEST_BACKUP_VERSION = "latest_backup_version";


    public UID getUid() {
        return uid;
    }

    public EncryptionPublicKey getEncryptionPublicKey() {
        return encryptionPublicKey;
    }

    public MACKey getMacKey() {
        return macKey;
    }

    public Integer getLatestBackupVersion() {
        return latestBackupVersion;
    }

    public long getKeyGenerationTimestamp() {
        return keyGenerationTimestamp;
    }

    public long getLastSuccessfulKeyVerificationTimestamp() {
        return lastSuccessfulKeyVerificationTimestamp;
    }

    public int getSuccessfulVerificationCount() {
        return successfulVerificationCount;
    }

    public Integer getUploadedBackupVersion() {
        return uploadedBackupVersion;
    }

    public Integer getExportedBackupVersion() {
        return exportedBackupVersion;
    }

    // region constructors

    public static BackupKey create(BackupManagerSession backupManagerSession, UID uid, EncryptionPublicKey encryptionPublicKey, MACKey macKey) {
        if (uid == null || encryptionPublicKey == null || macKey == null) {
            return null;
        }
        try {
            BackupKey backupKey = new BackupKey(backupManagerSession, uid, encryptionPublicKey, macKey, System.currentTimeMillis(), 0L, 0L, 0, null, null, null);
            backupKey.insert();
            return backupKey;
        } catch (SQLException e) {
            return null;
        }
    }

    private BackupKey(BackupManagerSession backupManagerSession, UID uid, EncryptionPublicKey encryptionPublicKey, MACKey macKey, long keyGenerationTimestamp, long lastSuccessfulKeyVerificationTimestamp, long lastKeyVerificationPromptTimestamp, int successfulVerificationCount, Integer uploadedBackupVersion, Integer exportedBackupVersion, Integer latestBackupVersion) {
        this.backupManagerSession = backupManagerSession;
        this.uid = uid;
        this.encryptionPublicKey = encryptionPublicKey;
        this.macKey = macKey;
        this.keyGenerationTimestamp = keyGenerationTimestamp;
        this.lastSuccessfulKeyVerificationTimestamp = lastSuccessfulKeyVerificationTimestamp;
        this.lastKeyVerificationPromptTimestamp = lastKeyVerificationPromptTimestamp;
        this.successfulVerificationCount = successfulVerificationCount;
        this.uploadedBackupVersion = uploadedBackupVersion;
        this.exportedBackupVersion = exportedBackupVersion;
        this.latestBackupVersion = latestBackupVersion;
    }

    private BackupKey(BackupManagerSession backupManagerSession, ResultSet res) throws SQLException {
        this.backupManagerSession = backupManagerSession;
        this.uid = new UID(res.getBytes(UID_));
        try {
            this.encryptionPublicKey = (EncryptionPublicKey) new Encoded(res.getBytes(ENCRYPTION_PUBLIC_KEY)).decodePublicKey();
            this.macKey = (MACKey) new Encoded(res.getBytes(MAC_KEY)).decodeSymmetricKey();
        } catch (DecodingException | ClassCastException e) {
            Logger.e("Unable to parse encryption public key or MAC key in BackupKey database!!!");
        }
        this.keyGenerationTimestamp = res.getLong(KEY_GENERATION_TIMESTAMP);
        this.lastSuccessfulKeyVerificationTimestamp = res.getLong(LAST_SUCCESSFUL_KEY_VERIFICATION_TIMESTAMP);
        this.lastKeyVerificationPromptTimestamp = res.getLong(LAST_KEY_VERIFICATION_PROMPT_TIMESTAMP);
        this.successfulVerificationCount = res.getInt(SUCCESSFUL_VERIFICATION_COUNT);
        this.uploadedBackupVersion = res.getInt(UPLOADED_BACKUP_VERSION);
        if (res.wasNull()) {
            this.uploadedBackupVersion = null;
        }
        this.exportedBackupVersion = res.getInt(EXPORTED_BACKUP_VERSION);
        if (res.wasNull()) {
            this.exportedBackupVersion = null;
        }
        this.latestBackupVersion = res.getInt(LATEST_BACKUP_VERSION);
        if (res.wasNull()) {
            this.latestBackupVersion = null;
        }
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UID_ + " BLOB PRIMARY KEY, " +
                    ENCRYPTION_PUBLIC_KEY + " BLOB NOT NULL, " +
                    MAC_KEY + " BLOB NOT NULL, " +
                    KEY_GENERATION_TIMESTAMP + " INTEGER NOT NULL, " +
                    LAST_SUCCESSFUL_KEY_VERIFICATION_TIMESTAMP + " INTEGER NOT NULL, " +
                    LAST_KEY_VERIFICATION_PROMPT_TIMESTAMP + " INTEGER NOT NULL, " +
                    SUCCESSFUL_VERIFICATION_COUNT + " INTEGER NOT NULL, " +
                    UPLOADED_BACKUP_VERSION + " INTEGER, " +
                    EXPORTED_BACKUP_VERSION + " INTEGER, " +
                    LATEST_BACKUP_VERSION + " INTEGER, " +
                    "FOREIGN KEY (" + UID_ + "," + UPLOADED_BACKUP_VERSION + ") REFERENCES " + Backup.TABLE_NAME + " (" + Backup.BACKUP_KEY_UID + "," + Backup.VERSION + "), " +
                    "FOREIGN KEY (" + UID_ + "," + EXPORTED_BACKUP_VERSION + ") REFERENCES " + Backup.TABLE_NAME + " (" + Backup.BACKUP_KEY_UID + "," + Backup.VERSION + "), " +
                    "FOREIGN KEY (" + UID_ + "," + LATEST_BACKUP_VERSION + ") REFERENCES " + Backup.TABLE_NAME + " (" + Backup.BACKUP_KEY_UID + "," + Backup.VERSION + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 36 && newVersion >= 36) {
            Logger.d("MIGRATING `backup_key` DATABASE FROM VERSION " + oldVersion + " TO 36");
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE backup_key_new (" +
                        " uid BLOB PRIMARY KEY, " +
                        " encryption_public_key BLOB NOT NULL, " +
                        " mac_key BLOB NOT NULL, " +
                        " key_generation_timestamp INTEGER NOT NULL, " +
                        " last_successful_key_verification_timestamp INTEGER NOT NULL, " +
                        " last_key_verification_prompt_timestamp INTEGER NOT NULL, " +
                        " successful_verification_count INTEGER NOT NULL, " +
                        " uploaded_backup_version INTEGER, " +
                        " exported_backup_version INTEGER, " +
                        " latest_backup_version INTEGER, " +
                        "FOREIGN KEY (uid, uploaded_backup_version) REFERENCES backup (backup_key_uid, version), " +
                        "FOREIGN KEY (uid, exported_backup_version) REFERENCES backup (backup_key_uid, version), " +
                        "FOREIGN KEY (uid, latest_backup_version) REFERENCES backup (backup_key_uid, version));");

                ResultSet res = statement.executeQuery("SELECT * FROM backup_key WHERE uid IS NULL");
                if (res.next()) {
                    // we have a null primary key --> copy it partially to new table
                    try (PreparedStatement ps = session.prepareStatement("INSERT INTO backup_key_new VALUES (?,?,?,?,?, ?,?,?,?,?)")) {
                        ps.setBytes(1, new UID(Suite.getDefaultPRNGService(Suite.LATEST_VERSION)).getBytes());
                        ps.setBytes(2, res.getBytes("encryption_public_key"));
                        ps.setBytes(3, res.getBytes("mac_key"));
                        ps.setLong(4, res.getLong("key_generation_timestamp"));
                        ps.setLong(5, res.getLong("last_successful_key_verification_timestamp"));
                        ps.setLong(6, res.getLong("last_key_verification_prompt_timestamp"));
                        ps.setInt(7, res.getInt("successful_verification_count"));
                        ps.setNull(8, Types.INTEGER);
                        ps.setNull(9, Types.INTEGER);
                        ps.setNull(10, Types.INTEGER);
                        ps.executeUpdate();
                    }
                    // delete all existing backups (the backup_key_uid no longer exists)
                    statement.execute("DELETE FROM backup");
                } else {
                    // no null primary key, simply copy the content of the old table to the new one
                    statement.execute("INSERT into backup_key_new (uid, encryption_public_key, mac_key, key_generation_timestamp, last_successful_key_verification_timestamp, last_key_verification_prompt_timestamp, successful_verification_count, uploaded_backup_version, exported_backup_version, latest_backup_version) SELECT uid, encryption_public_key, mac_key, key_generation_timestamp, last_successful_key_verification_timestamp, last_key_verification_prompt_timestamp, successful_verification_count, uploaded_backup_version, exported_backup_version, latest_backup_version FROM backup_key");
                }
                statement.execute("DROP TABLE backup_key");
                statement.execute("ALTER TABLE backup_key_new RENAME TO backup_key");

            }
            oldVersion = 36;
        }
    }

    // delete all BackupKey and all Backup
    public static void deleteAll(BackupManagerSession backupManagerSession) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + ";")) {
            statement.executeUpdate();
        }
        Backup.deleteAll(backupManagerSession);
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?);")) {
            statement.setBytes(1, uid.getBytes());
            statement.setBytes(2, Encoded.of(encryptionPublicKey).getBytes());
            statement.setBytes(3, Encoded.of(macKey).getBytes());
            statement.setLong(4, keyGenerationTimestamp);
            statement.setLong(5, lastSuccessfulKeyVerificationTimestamp);
            statement.setLong(6, lastKeyVerificationPromptTimestamp);
            statement.setInt(7, successfulVerificationCount);
            if (uploadedBackupVersion == null) {
                statement.setNull(8, Types.INTEGER);
            } else {
                statement.setInt(8, uploadedBackupVersion);
            }
            if (exportedBackupVersion == null) {
                statement.setNull(9, Types.INTEGER);
            } else {
                statement.setInt(9, exportedBackupVersion);
            }
            if (latestBackupVersion == null) {
                statement.setNull(10, Types.INTEGER);
            } else {
                statement.setInt(10, latestBackupVersion);
            }
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UID_ + " = ?;")) {
            statement.setBytes(1, uid.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region setters

    public void addSuccessfulVerification() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SUCCESSFUL_VERIFICATION_COUNT + " = ?, " +
                LAST_SUCCESSFUL_KEY_VERIFICATION_TIMESTAMP + " = ? " +
                " WHERE " + UID_ + " = ?;")) {
            long timestamp = System.currentTimeMillis();
            statement.setInt(1, successfulVerificationCount + 1);
            statement.setLong(2, timestamp);
            statement.setBytes(3, uid.getBytes());
            statement.executeUpdate();
            this.successfulVerificationCount++;
            this.lastSuccessfulKeyVerificationTimestamp = timestamp;
        }
    }


    public void setLastKeyVerificationPromptTimestamp() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + LAST_KEY_VERIFICATION_PROMPT_TIMESTAMP + " = ? " +
                " WHERE " + UID_ + " = ?;")) {
            long timestamp = System.currentTimeMillis();
            statement.setLong(1, timestamp);
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
            this.lastKeyVerificationPromptTimestamp = timestamp;
        }
    }

    public void setLatestBackupVersion(int version) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + LATEST_BACKUP_VERSION + " = ? " +
                " WHERE " + UID_ + " = ?;")) {
            statement.setInt(1, version);
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
            this.latestBackupVersion = version;
        }
    }

    public void setExportedBackupVersion(int version) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + EXPORTED_BACKUP_VERSION + " = ? " +
                " WHERE " + UID_ + " = ?;")) {
            statement.setInt(1, version);
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
            this.exportedBackupVersion = version;
        }
    }

    public void setUploadedBackupVersion(int version) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + UPLOADED_BACKUP_VERSION + " = ? " +
                " WHERE " + UID_ + " = ?;")) {
            statement.setInt(1, version);
            statement.setBytes(2, uid.getBytes());
            statement.executeUpdate();
            this.uploadedBackupVersion = version;
        }
    }
    // endregion

    // region getters

    public static BackupKey get(BackupManagerSession backupManagerSession, UID backupKeyUid) {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + UID_ + " = ?;")){
            preparedStatement.setBytes(1, backupKeyUid.getBytes());
            ResultSet res = preparedStatement.executeQuery();
            if (res.next()) {
                return new BackupKey(backupManagerSession, res);
            } else {
                return null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public static BackupKey[] getAll(BackupManagerSession backupManagerSession) {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")){
            ResultSet res = preparedStatement.executeQuery();
            List<BackupKey> list = new ArrayList<>();
            while (res.next()) {
                list.add(new BackupKey(backupManagerSession, res));
            }
            return list.toArray(new BackupKey[0]);
        } catch (SQLException e) {
            return new BackupKey[0];
        }
    }


    public Backup getUploadedBackup() {
        if (uploadedBackupVersion == null) {
            return null;
        }
        return Backup.get(backupManagerSession, uid, uploadedBackupVersion);
    }

    public Backup getExportedBackup() {
        if (exportedBackupVersion == null) {
            return null;
        }
        return Backup.get(backupManagerSession, uid, exportedBackupVersion);
    }

    // endregion

    @Override
    public void wasCommitted() {

    }
}
