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
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;


@SuppressWarnings("FieldMayBeFinal")
public class ProfileBackupThreadId implements ObvDatabase {
    static final String TABLE_NAME = "profile_backup_thread_id";

    private final BackupManagerSession backupManagerSession;

    private final Identity ownedIdentity; // primary key
    static final String OWNED_IDENTITY = "owned_identity";
    private final UID threadId; // the profile backup threadId on the server
    static final String THREAD_ID = "thread_id";
    private long nextBackupTimestamp;
    static final String NEXT_BACKUP_TIMESTAMP = "next_backup_timestamp";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getThreadId() {
        return threadId;
    }

    public long getNextBackupTimestamp() {
        return nextBackupTimestamp;
    }

    // region constructors

    public static ProfileBackupThreadId create(BackupManagerSession backupManagerSession, Identity ownedIdentity, PRNG prng) {
        if (ownedIdentity == null || prng == null) {
            return null;
        }
        try {
            UID threadId = new UID(prng);
            ProfileBackupThreadId profileBackupThreadId = new ProfileBackupThreadId(backupManagerSession, ownedIdentity, threadId);
            profileBackupThreadId.insert();
            return profileBackupThreadId;
        } catch (SQLException e) {
            return null;
        }
    }

    private ProfileBackupThreadId(BackupManagerSession backupManagerSession, Identity ownedIdentity, UID threadId) {
        this.backupManagerSession = backupManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.threadId = threadId;
        this.nextBackupTimestamp = 0;
    }



    private ProfileBackupThreadId(BackupManagerSession backupManagerSession, ResultSet res) throws SQLException {
        this.backupManagerSession = backupManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.threadId = new UID(res.getBytes(THREAD_ID));
        this.nextBackupTimestamp = res.getLong(NEXT_BACKUP_TIMESTAMP);
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB PRIMARY KEY, " +
                    THREAD_ID + " BLOB NOT NULL, " +
                    NEXT_BACKUP_TIMESTAMP + " BIGINT NOT NULL " +
                    ");"
            );
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 44 && newVersion >= 44) {
            Logger.d("CREATING `device_backup_seed` DATABASE FOR VERSION 44");
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS profile_backup_thread_id (" +
                        " owned_identity BLOB PRIMARY KEY, " +
                        " thread_id BLOB NOT NULL, " +
                        " next_backup_timestamp BIGINT NOT NULL " +
                        ");");
            }
            oldVersion = 44;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("ProfileBackupThreadId.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES (?,?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, threadId.getBytes());
            statement.setLong(3, nextBackupTimestamp);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("ProfileBackupThreadId.delete",
                "DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region setters

    public void setNextBackupTimestamp(long nextBackupTimestamp) throws SQLException {
        try (PreparedStatement statement = backupManagerSession.session.prepareStatement("ProfileBackupThreadId.setNextBackupTimestamp",
                "UPDATE " + TABLE_NAME +
                " SET " + NEXT_BACKUP_TIMESTAMP + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setLong(1, nextBackupTimestamp);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.nextBackupTimestamp = nextBackupTimestamp;
        }
    }

    // endregion

    // region getters

    public static ProfileBackupThreadId get(BackupManagerSession backupManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("ProfileBackupThreadId.get",
                "SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")){
            preparedStatement.setBytes(1, ownedIdentity.getBytes());
            ResultSet res = preparedStatement.executeQuery();
            if (res.next()) {
                return new ProfileBackupThreadId(backupManagerSession, res);
            } else {
                return null;
            }
        }
    }

    public static List<ProfileBackupThreadId> getAll(BackupManagerSession backupManagerSession) throws SQLException {
        try (PreparedStatement preparedStatement = backupManagerSession.session.prepareStatement("ProfileBackupThreadId.getAll",
                "SELECT * FROM " + TABLE_NAME + ";")){
            ResultSet res = preparedStatement.executeQuery();
            List<ProfileBackupThreadId> list = new ArrayList<>();
            while (res.next()) {
                list.add(new ProfileBackupThreadId(backupManagerSession, res));
            }
            return list;
        }
    }

    // endregion

    // region hooks

    @Override
    public void wasCommitted() {
    }

    // endregion
}
