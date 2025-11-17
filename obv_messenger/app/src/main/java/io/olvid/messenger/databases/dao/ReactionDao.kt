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
package io.olvid.messenger.databases.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.Reaction


@Dao
interface ReactionDao {
    @Insert
    fun insert(reaction: Reaction): Long

    @Update
    fun update(reaction: Reaction)

    @Delete
    fun delete(reaction: Reaction)

    @Query("SELECT * FROM ${Reaction.TABLE_NAME} " +
            " WHERE ${Reaction.MESSAGE_ID} = :messageId "
    )
    fun getAllForMessage(messageId: Long): List<Reaction>

    @Query("SELECT * FROM ${Reaction.TABLE_NAME} " +
            " WHERE ${Reaction.MESSAGE_ID} = :messageId " +
            " AND ${Reaction.EMOJI} IS NOT NULL " +
            " ORDER BY CASE WHEN ${Reaction.BYTES_IDENTITY} IS NULL THEN 0 ELSE 1 END ASC, " +
            " ${Reaction.TIMESTAMP} DESC"
    )
    fun getAllNonNullForMessageSortedByTimestampLiveData(messageId: Long): LiveData<List<Reaction>>

    @Query("SELECT * FROM ${Reaction.TABLE_NAME} " +
            " WHERE ${Reaction.MESSAGE_ID} = :messageId " +
            " AND ${Reaction.BYTES_IDENTITY} IS NULL "
    )
    fun getMyReactionForMessage(messageId: Long): Reaction?

    @Query("DELETE FROM ${Reaction.TABLE_NAME} " +
            " WHERE ${Reaction.MESSAGE_ID} = :messageId "
    )
    fun deleteAllForMessage(messageId: Long)

    @Query("""
        SELECT reac.*, ${MessageDao.PREFIX_MESSAGE_COLUMNS} FROM ${Reaction.TABLE_NAME} AS reac 
        INNER JOIN ${Message.TABLE_NAME} AS mess
        ON mess.id = reac.${Reaction.MESSAGE_ID}
        INNER JOIN ${Discussion.TABLE_NAME} AS disc
        ON disc.id = mess.${Message.DISCUSSION_ID}
        WHERE disc.${Discussion.BYTES_OWNED_IDENTITY} = :bytesOwnedIdentity
        AND disc.${Discussion.DISCUSSION_TYPE} = ${Discussion.TYPE_GROUP_V2}
        AND disc.${Discussion.BYTES_DISCUSSION_IDENTIFIER} = :bytesGroupIdentifier
        AND mess.${Message.TIMESTAMP} > :pendingCreationTimestamp
        AND mess.${Message.TIMESTAMP} < :creationTimestamp
        AND reac.${Reaction.BYTES_IDENTITY} IS NULL
        """
    )
    fun getAllMineInGroupV2DiscussionWithinTimeInterval(
        bytesOwnedIdentity: ByteArray,
        bytesGroupIdentifier: ByteArray,
        pendingCreationTimestamp: Long,
        creationTimestamp: Long,
    ): List<ReactionAndMessage>

    data class ReactionAndMessage(
        @Embedded val reaction: Reaction,
        @Embedded(prefix = "mess_") val message: Message
    )
}
