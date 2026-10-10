/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.databases.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.olvid.messenger.databases.entity.Emoji

@Dao
interface EmojiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(emoji: Emoji)

    @Query("SELECT * FROM ${Emoji.TABLE_NAME} WHERE ${Emoji.IS_FAVORITE} = 1 ORDER BY ${Emoji.LAST_USED} DESC")
    fun getFavoriteEmojis(): LiveData<List<Emoji>>

    @Query("SELECT * FROM ${Emoji.TABLE_NAME} WHERE ${Emoji.LAST_USED} > 0 ORDER BY ${Emoji.LAST_USED} DESC LIMIT 10")
    fun getRecentEmojis(): LiveData<List<Emoji>>

    @Query("SELECT * FROM ${Emoji.TABLE_NAME} WHERE ${Emoji.LAST_USED_KEYBOARD} > 0 ORDER BY ${Emoji.LAST_USED_KEYBOARD} DESC LIMIT 20")
    suspend fun getRecentKeyboardEmojis(): List<Emoji>

    @Query("UPDATE ${Emoji.TABLE_NAME} SET ${Emoji.IS_FAVORITE} = :isFavorite WHERE ${Emoji.EMOJI} = :emoji")
    suspend fun setFavorite(emoji: String, isFavorite: Boolean)

    @Query("UPDATE ${Emoji.TABLE_NAME} SET ${Emoji.LAST_USED} = :timestamp WHERE ${Emoji.EMOJI} = :emoji")
    suspend fun updateLastUsed(emoji: String, timestamp: Long)

    @Query("UPDATE ${Emoji.TABLE_NAME} SET ${Emoji.LAST_USED_KEYBOARD} = :timestamp WHERE ${Emoji.EMOJI} = :emoji")
    suspend fun updateLastUsedKeyboard(emoji: String, timestamp: Long)

    @Query("SELECT * FROM ${Emoji.TABLE_NAME} WHERE ${Emoji.EMOJI} = :emoji")
    suspend fun getEmoji(emoji: String): Emoji?
}