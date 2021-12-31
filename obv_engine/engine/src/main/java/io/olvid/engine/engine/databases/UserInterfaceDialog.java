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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.datatypes.EngineSession;
import io.olvid.engine.engine.types.ObvDialog;


public class UserInterfaceDialog implements ObvDatabase {
    static final String TABLE_NAME = "user_interface_dialog";

    private final EngineSession engineSession;

    private UUID uuid;
    static final String UUID_ = "uuid";
    private Encoded encodedDialog;
    static final String ENCODED_DIALOG = "encoded_dialog";
    private long creationTimestamp;
    static final String CREATION_TIMESTAMP = "creation_timestamp";


    public void resend() {
        try {
            sendNotification();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void update(Encoded encodedDialog) {
        try (PreparedStatement statement = engineSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                ENCODED_DIALOG + " = ?, " +
                CREATION_TIMESTAMP + " = ? " +
                " WHERE " + UUID_ + " = ?;")) {
            long timestamp = System.currentTimeMillis();
            statement.setBytes(1, encodedDialog.getBytes());
            statement.setLong(2, timestamp);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
            this.encodedDialog = encodedDialog;
            this.creationTimestamp = timestamp;
            this.commitHookBits |= HOOK_BIT_SHOULD_SEND_NOTIFICATION;
            engineSession.session.addSessionCommitListener(this);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    public static UserInterfaceDialog createOrReplace(EngineSession engineSession, ObvDialog dialog) {
        if ((dialog == null)) {
            return null;
        }
        try {
            UserInterfaceDialog previousUserInterfaceDialog = UserInterfaceDialog.get(engineSession, dialog.getUuid());
            if (previousUserInterfaceDialog != null) {
                previousUserInterfaceDialog.update(dialog.encode(engineSession.jsonObjectMapper));
                return previousUserInterfaceDialog;
            } else {
                UserInterfaceDialog userInterfaceDialog = new UserInterfaceDialog(engineSession, dialog.getUuid(), dialog.encode(engineSession.jsonObjectMapper));
                userInterfaceDialog.insert();
                return userInterfaceDialog;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private UserInterfaceDialog(EngineSession engineSession, UUID uuid, Encoded encodedDialog) {
        this.engineSession = engineSession;

        this.uuid = uuid;
        this.encodedDialog = encodedDialog;
        this.creationTimestamp = System.currentTimeMillis();
    }

    private UserInterfaceDialog(EngineSession engineSession, ResultSet res) throws SQLException {
        this.engineSession = engineSession;

        this.uuid = UUID.fromString(res.getString(UUID_));
        this.encodedDialog = new Encoded(res.getBytes(ENCODED_DIALOG));
        this.creationTimestamp = res.getLong(CREATION_TIMESTAMP);
    }



    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    UUID_ + " VARCHAR PRIMARY KEY, " +
                    ENCODED_DIALOG + " BLOB NOT NULL, " +
                    CREATION_TIMESTAMP + " BIGINT NOT NULL);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 8 && newVersion >= 8) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM user_interface_dialog");
            }
            oldVersion = 8;
        }
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM user_interface_dialog");
            }
            oldVersion = 11;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = engineSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?);")) {
            statement.setString(1, uuid.toString());
            statement.setBytes(2, encodedDialog.getBytes());
            statement.setLong(3, creationTimestamp);
            statement.executeUpdate();
            this.commitHookBits |= HOOK_BIT_SHOULD_SEND_NOTIFICATION;
            engineSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = engineSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + UUID_  + " = ?;")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
            this.commitHookBits |= HOOK_BIT_DELETED;
            engineSession.session.addSessionCommitListener(this);
        }
    }


    public static UserInterfaceDialog get(EngineSession engineSession, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        try (PreparedStatement statement = engineSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + UUID_  + " = ?;")) {
            statement.setString(1, uuid.toString());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new UserInterfaceDialog(engineSession, res);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public static UserInterfaceDialog[] getAll(EngineSession engineSession) {
        try (PreparedStatement statement = engineSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<UserInterfaceDialog> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new UserInterfaceDialog(engineSession, res));
                }
                return list.toArray(new UserInterfaceDialog[0]);
            }
        } catch (SQLException e) {
            return new UserInterfaceDialog[0];
        }
    }


    public void sendNotification() throws Exception {
        engineSession.userInterfaceDialogListener.sendUserInterfaceDialogNotification(uuid, getObvDialog(), creationTimestamp);
    }

    public ObvDialog getObvDialog() throws Exception {
        return ObvDialog.of(encodedDialog, engineSession.jsonObjectMapper);
    }


    private long commitHookBits = 0;
    private static final long HOOK_BIT_SHOULD_SEND_NOTIFICATION = 0x1;
    private static final long HOOK_BIT_DELETED = 0x2;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_SHOULD_SEND_NOTIFICATION) != 0) {
            if (engineSession.userInterfaceDialogListener != null) {
                try {
                    sendNotification();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if ((commitHookBits & HOOK_BIT_DELETED) != 0) {
            if (engineSession.userInterfaceDialogListener != null) {
                try {
                    engineSession.userInterfaceDialogListener.sendUserInterfaceDialogDeletionNotification(uuid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        commitHookBits = 0;
    }
}
