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

    @Query("UPDATE ${Emoji.TABLE_NAME} SET ${Emoji.IS_FAVORITE} = :isFavorite WHERE ${Emoji.EMOJI} = :emoji")
    suspend fun setFavorite(emoji: String, isFavorite: Boolean)

    @Query("UPDATE ${Emoji.TABLE_NAME} SET ${Emoji.LAST_USED} = :timestamp WHERE ${Emoji.EMOJI} = :emoji")
    suspend fun updateLastUsed(emoji: String, timestamp: Long)

    @Query("SELECT * FROM ${Emoji.TABLE_NAME} WHERE ${Emoji.EMOJI} = :emoji")
    suspend fun getEmoji(emoji: String): Emoji?
}