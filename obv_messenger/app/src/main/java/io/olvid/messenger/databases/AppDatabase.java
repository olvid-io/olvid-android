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

package io.olvid.messenger.databases;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.customClasses.DatabaseKey;
import io.olvid.messenger.databases.dao.ActionShortcutConfigurationDao;
import io.olvid.messenger.databases.dao.CallLogItemDao;
import io.olvid.messenger.databases.dao.ContactDao;
import io.olvid.messenger.databases.dao.ContactGroupJoinDao;
import io.olvid.messenger.databases.dao.DiscussionCustomizationDao;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.dao.FyleDao;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.dao.GlobalSearchDao;
import io.olvid.messenger.databases.dao.Group2Dao;
import io.olvid.messenger.databases.dao.Group2MemberDao;
import io.olvid.messenger.databases.dao.Group2PendingMemberDao;
import io.olvid.messenger.databases.dao.GroupDao;
import io.olvid.messenger.databases.dao.InvitationDao;
import io.olvid.messenger.databases.dao.KnownCertificateDao;
import io.olvid.messenger.databases.dao.LatestDiscussionSenderSequenceNumberDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.dao.MessageExpirationDao;
import io.olvid.messenger.databases.dao.MessageMetadataDao;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.dao.OwnedDeviceDao;
import io.olvid.messenger.databases.dao.OwnedIdentityDao;
import io.olvid.messenger.databases.dao.PendingGroupMemberDao;
import io.olvid.messenger.databases.dao.RawDao;
import io.olvid.messenger.databases.dao.ReactionDao;
import io.olvid.messenger.databases.dao.ReactionRequestDao;
import io.olvid.messenger.databases.dao.RemoteDeleteAndEditRequestDao;
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatusFTS;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.KnownCertificate;
import io.olvid.messenger.databases.entity.LatestDiscussionSenderSequenceNumber;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.entity.MessageFTS;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;
import io.olvid.messenger.databases.entity.OwnedDevice;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.PendingGroupMember;
import io.olvid.messenger.databases.entity.Reaction;
import io.olvid.messenger.databases.entity.ReactionRequest;
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest;

@Database(
        entities = {
                ActionShortcutConfiguration.class,
                CallLogItem.class,
                CallLogItemContactJoin.class,
                Contact.class,
                ContactGroupJoin.class,
                Discussion.class,
                DiscussionCustomization.class,
                Fyle.class,
                FyleMessageJoinWithStatus.class,
                FyleMessageJoinWithStatusFTS.class,
                Group.class,
                Group2.class,
                Group2Member.class,
                Group2PendingMember.class,
                Invitation.class,
                KnownCertificate.class,
                LatestDiscussionSenderSequenceNumber.class,
                Message.class,
                MessageExpiration.class,
                MessageFTS.class,
                MessageMetadata.class,
                MessageRecipientInfo.class,
                OwnedDevice.class,
                OwnedIdentity.class,
                PendingGroupMember.class,
                Reaction.class,
                ReactionRequest.class,
                RemoteDeleteAndEditRequest.class,
        },
        version = AppDatabase.DB_SCHEMA_VERSION
)
@TypeConverters({ObvTypeConverters.class})
public abstract class AppDatabase extends RoomDatabase {
    public static final int DB_SCHEMA_VERSION = 71;
    public static final int DB_FTS_GLOBAL_SEARCH_VERSION = 1;
    public static final String DB_FILE_NAME = "app_database";
    public static final String TMP_ENCRYPTED_DB_FILE_NAME = "encrypted_app_database";

    public abstract ContactDao contactDao();

    public abstract GroupDao groupDao();

    public abstract ContactGroupJoinDao contactGroupJoinDao();

    public abstract PendingGroupMemberDao pendingGroupMemberDao();

    public abstract DiscussionDao discussionDao();

    public abstract DiscussionCustomizationDao discussionCustomizationDao();

    public abstract FyleDao fyleDao();

    public abstract FyleMessageJoinWithStatusDao fyleMessageJoinWithStatusDao();

    public abstract InvitationDao invitationDao();

    public abstract MessageDao messageDao();

    public abstract MessageExpirationDao messageExpirationDao();

    public abstract MessageMetadataDao messageMetadataDao();

    public abstract MessageRecipientInfoDao messageRecipientInfoDao();

    public abstract OwnedIdentityDao ownedIdentityDao();

    public abstract OwnedDeviceDao ownedDeviceDao();

    public abstract CallLogItemDao callLogItemDao();

    public abstract RawDao rawDao();

    public abstract ReactionDao reactionDao();

    public abstract RemoteDeleteAndEditRequestDao remoteDeleteAndEditRequestDao();

    public abstract KnownCertificateDao knownCertificateDao();

    public abstract LatestDiscussionSenderSequenceNumberDao latestDiscussionSenderSequenceNumberDao();

    public abstract ReactionRequestDao reactionRequestDao();

    public abstract ActionShortcutConfigurationDao actionShortcutConfigurationDao();

    public abstract Group2Dao group2Dao();

    public abstract Group2MemberDao group2MemberDao();

    public abstract Group2PendingMemberDao group2PendingMemberDao();

    public abstract GlobalSearchDao globalSearchDao();

    private static final RoomDatabase.Callback roomDatabaseOpenCallback = new RoomDatabase.Callback() {
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            App.runThread(new AppDatabaseOpenCallback(instance));
        }
    };


    private static volatile AppDatabase instance;

    public static AppDatabase getInstance() {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    System.loadLibrary("sqlcipher");
                    File dbFile = new File(App.absolutePathFromRelative(DB_FILE_NAME));
                    String dbKey = DatabaseKey.get(DatabaseKey.APP_DATABASE_SECRET);
                    if (dbKey == null) {
                        dbKey = "";
                    }
                    try {
                        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), dbKey, null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY, null);
                        db.close();
                    } catch (Exception ex) {
                        Logger.i("App database may need to be encrypted");
                        long startTime = System.currentTimeMillis();
                        try {
                            int oldUserVersion = -1;
                            File tmpEncryptedDbFile = new File(App.absolutePathFromRelative(TMP_ENCRYPTED_DB_FILE_NAME));
                            if (tmpEncryptedDbFile.exists()) {
                                //noinspection ResultOfMethodCallIgnored
                                tmpEncryptedDbFile.delete();
                            }
                            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), "", null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY, null)) {
                                db.rawExecSQL("ATTACH DATABASE '" + tmpEncryptedDbFile.getPath() + "' AS encrypted KEY \"" + dbKey + "\";");
                                db.rawExecSQL("SELECT sqlcipher_export('encrypted');");
                                db.rawExecSQL("DETACH DATABASE encrypted;");
                                try (Cursor cursor = db.rawQuery("PRAGMA user_version;")) {
                                    cursor.moveToNext();
                                    oldUserVersion = cursor.getInt(0);
                                }
                            }

                            // Now, we need to copy the PRAGMA user_version from original DB to the new one
                            if (oldUserVersion == -1) {
                                throw new Exception("Unable to read user_version from current database");
                            } else {
                                try (SQLiteDatabase db = SQLiteDatabase.openDatabase(tmpEncryptedDbFile.getPath(), dbKey, null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY, null)) {
                                    db.rawExecSQL("PRAGMA user_version = " + oldUserVersion + ";");
                                }
                            }

                            boolean deleted = dbFile.delete();
                            if (deleted) {
                                boolean renamed = tmpEncryptedDbFile.renameTo(dbFile);
                                if (renamed) {
                                    Logger.i("App database encryption successful (took " + (System.currentTimeMillis() - startTime) + "ms)");
                                } else {
                                    Logger.e("App database encryption error: Unable to rename encrypted database!");
                                }
                            } else {
                                throw new RuntimeException("App database encryption error: unable to delete unencrypted database!");
                            }
                        } catch (Exception fatal) {
                            // database is encrypted but not with the provided dbKey, or database encryption failed --> try disabling encryption to use a plain database
                            Logger.e("App database encryption failed, falling back to un-encrypted database");
                            dbKey = "";
                        }
                    }

                    instance = Room.databaseBuilder(App.getContext(), AppDatabase.class, App.absolutePathFromRelative(DB_FILE_NAME))
                            .openHelperFactory(new PragmaSQLiteOpenHelperFactory(dbKey))
                            .addCallback(roomDatabaseOpenCallback)
                            .addMigrations(AppDatabaseMigrations.MIGRATIONS)
                            .build();
                }
            }
        }
        return instance;
    }
}
