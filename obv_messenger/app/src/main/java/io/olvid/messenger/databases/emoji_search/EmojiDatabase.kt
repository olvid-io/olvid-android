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

package io.olvid.messenger.databases.emoji_search

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.dao.RawDao
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Database(entities = [Emoji::class, EmojiFts::class], version = EmojiDatabase.DB_VERSION)
abstract class EmojiDatabase : RoomDatabase() {

    abstract fun emojiDao(): EmojiDao
    abstract fun rawDao(): RawDao

    companion object {
        const val DB_VERSION = 1
        const val DATABASE_NAME = "emoji_search.db"

        @Volatile
        private var instance: EmojiDatabase? = null

        fun getInstance(): EmojiDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase().also {
                    instance = it
                }
            }
        }

        private fun buildDatabase(): EmojiDatabase {
            val context = App.getContext()
            val lastVersion = AppSingleton.getLastEmojiDbVersion()
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            // If the on-device version is old, delete the existing DB file.
            if (DB_VERSION > lastVersion) {
                if (dbFile.exists()) {
                    context.deleteDatabase(DATABASE_NAME).also { Logger.e("OUTDATED EMOJI SEARCH DB DELETED: $it") }
                }
            }

            // If the DB file does not exist (either first run or after deletion),
            // unzip it from the assets.
            if (!dbFile.exists()) {
                runCatching {
                    context.assets.open("$DATABASE_NAME.zip").use { inputStream ->
                        ZipInputStream(inputStream).use { zipInputStream ->
                            zipInputStream.nextEntry
                            dbFile.parentFile?.mkdirs()
                            FileOutputStream(dbFile).use { outputStream ->
                                zipInputStream.copyTo(outputStream)
                            }
                        }
                    }
                    // After successfully unzipping, update the stored version.
                    AppSingleton.saveLastEmojiDbVersion(DB_VERSION)
                    Logger.w("INITIALIZED EMOJI SEARCH DB")
                }.onFailure {
                    Logger.x(it)
                }
            }

            return Room.databaseBuilder(
                context,
                EmojiDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration()
                .build()
        }
    }
}