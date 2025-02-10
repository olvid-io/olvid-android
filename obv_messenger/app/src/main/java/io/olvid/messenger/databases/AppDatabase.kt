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
package io.olvid.messenger.databases

import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.customClasses.DatabaseKey
import io.olvid.messenger.customClasses.DatabaseKey.Companion.get
import io.olvid.messenger.databases.dao.ActionShortcutConfigurationDao
import io.olvid.messenger.databases.dao.CallLogItemDao
import io.olvid.messenger.databases.dao.ContactDao
import io.olvid.messenger.databases.dao.ContactGroupJoinDao
import io.olvid.messenger.databases.dao.DiscussionCustomizationDao
import io.olvid.messenger.databases.dao.DiscussionDao
import io.olvid.messenger.databases.dao.FyleDao
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao
import io.olvid.messenger.databases.dao.FyleMessageTextBlockDao
import io.olvid.messenger.databases.dao.GlobalSearchDao
import io.olvid.messenger.databases.dao.Group2Dao
import io.olvid.messenger.databases.dao.Group2MemberDao
import io.olvid.messenger.databases.dao.Group2PendingMemberDao
import io.olvid.messenger.databases.dao.GroupDao
import io.olvid.messenger.databases.dao.InvitationDao
import io.olvid.messenger.databases.dao.KnownCertificateDao
import io.olvid.messenger.databases.dao.LatestDiscussionSenderSequenceNumberDao
import io.olvid.messenger.databases.dao.MessageDao
import io.olvid.messenger.databases.dao.MessageExpirationDao
import io.olvid.messenger.databases.dao.MessageMetadataDao
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao
import io.olvid.messenger.databases.dao.MessageReturnReceiptDao
import io.olvid.messenger.databases.dao.OwnedDeviceDao
import io.olvid.messenger.databases.dao.OwnedIdentityDao
import io.olvid.messenger.databases.dao.PendingGroupMemberDao
import io.olvid.messenger.databases.dao.RawDao
import io.olvid.messenger.databases.dao.ReactionDao
import io.olvid.messenger.databases.dao.ReactionRequestDao
import io.olvid.messenger.databases.dao.RemoteDeleteAndEditRequestDao
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration
import io.olvid.messenger.databases.entity.CallLogItem
import io.olvid.messenger.databases.entity.CallLogItemContactJoin
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.ContactGroupJoin
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatusFTS
import io.olvid.messenger.databases.entity.Group
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.Group2Member
import io.olvid.messenger.databases.entity.Group2PendingMember
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.databases.entity.KnownCertificate
import io.olvid.messenger.databases.entity.LatestDiscussionSenderSequenceNumber
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.MessageFTS
import io.olvid.messenger.databases.entity.MessageMetadata
import io.olvid.messenger.databases.entity.MessageRecipientInfo
import io.olvid.messenger.databases.entity.MessageReturnReceipt
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.entity.PendingGroupMember
import io.olvid.messenger.databases.entity.Reaction
import io.olvid.messenger.databases.entity.ReactionRequest
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest
import io.olvid.messenger.databases.entity.TextBlock
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import kotlin.concurrent.Volatile

@Database(
    entities = [ActionShortcutConfiguration::class, CallLogItem::class, CallLogItemContactJoin::class, Contact::class, ContactGroupJoin::class, Discussion::class, DiscussionCustomization::class, Fyle::class, FyleMessageJoinWithStatus::class, FyleMessageJoinWithStatusFTS::class, Group::class, Group2::class, Group2Member::class, Group2PendingMember::class, Invitation::class, KnownCertificate::class, LatestDiscussionSenderSequenceNumber::class, Message::class, MessageExpiration::class, MessageFTS::class, MessageMetadata::class, MessageRecipientInfo::class, MessageReturnReceipt::class, OwnedDevice::class, OwnedIdentity::class, PendingGroupMember::class, Reaction::class, ReactionRequest::class, RemoteDeleteAndEditRequest::class, TextBlock::class
    ], version = AppDatabase.DB_SCHEMA_VERSION
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    abstract fun groupDao(): GroupDao

    abstract fun contactGroupJoinDao(): ContactGroupJoinDao

    abstract fun pendingGroupMemberDao(): PendingGroupMemberDao

    abstract fun discussionDao(): DiscussionDao

    abstract fun discussionCustomizationDao(): DiscussionCustomizationDao

    abstract fun fyleDao(): FyleDao

    abstract fun fyleMessageJoinWithStatusDao(): FyleMessageJoinWithStatusDao

    abstract fun invitationDao(): InvitationDao

    abstract fun messageDao(): MessageDao

    abstract fun messageExpirationDao(): MessageExpirationDao

    abstract fun messageMetadataDao(): MessageMetadataDao

    abstract fun messageRecipientInfoDao(): MessageRecipientInfoDao

    abstract fun ownedIdentityDao(): OwnedIdentityDao

    abstract fun ownedDeviceDao(): OwnedDeviceDao

    abstract fun callLogItemDao(): CallLogItemDao

    abstract fun rawDao(): RawDao

    abstract fun reactionDao(): ReactionDao

    abstract fun remoteDeleteAndEditRequestDao(): RemoteDeleteAndEditRequestDao

    abstract fun knownCertificateDao(): KnownCertificateDao

    abstract fun latestDiscussionSenderSequenceNumberDao(): LatestDiscussionSenderSequenceNumberDao

    abstract fun reactionRequestDao(): ReactionRequestDao

    abstract fun actionShortcutConfigurationDao(): ActionShortcutConfigurationDao

    abstract fun group2Dao(): Group2Dao

    abstract fun group2MemberDao(): Group2MemberDao

    abstract fun group2PendingMemberDao(): Group2PendingMemberDao

    abstract fun globalSearchDao(): GlobalSearchDao

    abstract fun fyleMessageTextBlockDao(): FyleMessageTextBlockDao

    abstract fun messageReturnReceiptDao(): MessageReturnReceiptDao

    companion object {
        const val DB_SCHEMA_VERSION: Int = 77
        const val DB_FTS_GLOBAL_SEARCH_VERSION: Int = 1
        const val DB_FILE_NAME: String = "app_database"
        const val TMP_ENCRYPTED_DB_FILE_NAME: String = "encrypted_app_database"

        private val roomDatabaseOpenCallback: Callback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                App.runThread(AppDatabaseOpenCallback(instance))
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        fun getInstance(): AppDatabase {
            if (instance == null) {
                synchronized(AppDatabase::class.java) {
                    if (instance == null) {
                        System.loadLibrary("sqlcipher")
                        val dbFile = File(
                            App.absolutePathFromRelative(
                                DB_FILE_NAME
                            )
                        )
                        var dbKey = get(DatabaseKey.APP_DATABASE_SECRET)
                        if (dbKey == null) {
                            dbKey = ""
                        }
                        try {
                            val db = SQLiteDatabase.openDatabase(
                                dbFile.path,
                                dbKey,
                                null,
                                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                                null
                            )
                            db.close()
                        } catch (ex: Exception) {
                            Logger.i("App database may need to be encrypted")
                            val startTime = System.currentTimeMillis()
                            try {
                                var oldUserVersion = -1
                                val tmpEncryptedDbFile = File(
                                    App.absolutePathFromRelative(
                                        TMP_ENCRYPTED_DB_FILE_NAME
                                    )
                                )
                                if (tmpEncryptedDbFile.exists()) {
                                    tmpEncryptedDbFile.delete()
                                }
                                SQLiteDatabase.openDatabase(
                                    dbFile.path,
                                    "",
                                    null,
                                    SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                                    null
                                ).use { db ->
                                    db.rawExecSQL("ATTACH DATABASE '" + tmpEncryptedDbFile.path + "' AS encrypted KEY \"" + dbKey + "\";")
                                    db.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                                    db.rawExecSQL("DETACH DATABASE encrypted;")
                                    db.rawQuery("PRAGMA user_version;").use { cursor ->
                                        cursor.moveToNext()
                                        oldUserVersion = cursor.getInt(0)
                                    }
                                }
                                // Now, we need to copy the PRAGMA user_version from original DB to the new one
                                if (oldUserVersion == -1) {
                                    throw Exception("Unable to read user_version from current database")
                                } else {
                                    SQLiteDatabase.openDatabase(
                                        tmpEncryptedDbFile.path,
                                        dbKey,
                                        null,
                                        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                                        null
                                    ).use { db ->
                                        db.rawExecSQL(
                                            "PRAGMA user_version = $oldUserVersion;"
                                        )
                                    }
                                }

                                val deleted = dbFile.delete()
                                if (deleted) {
                                    val renamed = tmpEncryptedDbFile.renameTo(dbFile)
                                    if (renamed) {
                                        Logger.i("App database encryption successful (took " + (System.currentTimeMillis() - startTime) + "ms)")
                                    } else {
                                        Logger.e("App database encryption error: Unable to rename encrypted database!")
                                    }
                                } else {
                                    throw RuntimeException("App database encryption error: unable to delete unencrypted database!")
                                }
                            } catch (fatal: Exception) {
                                // database is encrypted but not with the provided dbKey, or database encryption failed --> try disabling encryption to use a plain database
                                Logger.e("App database encryption failed, falling back to un-encrypted database")
                                dbKey = ""
                            }
                        }

                        instance = databaseBuilder(
                            App.getContext(),
                            AppDatabase::class.java, App.absolutePathFromRelative(
                                DB_FILE_NAME
                            )
                        )
                            .openHelperFactory(PragmaSQLiteOpenHelperFactory(dbKey))
                            .addCallback(roomDatabaseOpenCallback)
                            .addMigrations(*AppDatabaseMigrations.MIGRATIONS)
                            .build()
                    }
                }
            }
            return instance!!
        }
    }
}
