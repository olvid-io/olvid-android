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
import androidx.room.Upsert
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.PollVote
import java.util.UUID

@Dao
interface PollVoteDao {
    @Insert
    fun insert(pollVote: PollVote)

    @Delete
    fun delete(pollVote: PollVote)

    @Update
    fun update(pollVote: PollVote)

    @Upsert
    fun upsert(pollVote: PollVote)

    @Query(
        "SELECT * FROM " + PollVote.TABLE_NAME +
                " WHERE " + PollVote.MESSAGE_ID + " = :messageId " +
                " AND " + PollVote.VOTER + " = :voter " +
                " AND " + PollVote.VOTE_UUID + " = :voteUuid "
    )
    fun get(
        messageId: Long,
        voter: ByteArray,
        voteUuid: UUID,
    ): PollVote?

    @Query(
        "SELECT * FROM " + PollVote.TABLE_NAME +
                " WHERE " + PollVote.MESSAGE_ID + " = :messageId " +
                " AND " + PollVote.VOTER + " = :voter "
    )
    fun getAllByVoter(
        messageId: Long,
        voter: ByteArray,
    ): List<PollVote>

    @Query(
        "DELETE FROM " + PollVote.TABLE_NAME +
                " WHERE " + PollVote.MESSAGE_ID + " = :messageId "
    )
    fun deleteAllForMessage(
        messageId: Long
    )

    @Query(
        """
        -- Step 1: Get all "none" votes
        SELECT
            PV.*
        FROM ${PollVote.TABLE_NAME} AS PV
        WHERE PV.${PollVote.MESSAGE_ID} = :messageId
          AND PV.${PollVote.VOTE_UUID} = :noneVoteUuidString
          AND PV.${PollVote.VOTED} = 1

        UNION ALL

        -- Step 2: Get all other active votes, but only for voters who DID NOT pick "none"
        SELECT
            PV.*
        FROM ${PollVote.TABLE_NAME} AS PV
        WHERE PV.${PollVote.MESSAGE_ID} = :messageId
          AND PV.${PollVote.VOTE_UUID} != :noneVoteUuidString
          AND PV.${PollVote.VOTED} = 1
          AND NOT EXISTS ( -- Ensure this voter didn't also vote "none" for the same poll
              SELECT 1
              FROM ${PollVote.TABLE_NAME} AS PNV
              WHERE PNV.${PollVote.MESSAGE_ID} = PV.${PollVote.MESSAGE_ID}
                AND PNV.${PollVote.VOTER} = PV.${PollVote.VOTER} -- Crucial: same voter
                AND PNV.${PollVote.VOTE_UUID} = :noneVoteUuidString
                AND PNV.${PollVote.VOTED} = 1
          )
    """
    )
    fun getAllVotersChoicesOrNone(
        messageId: Long,
        noneVoteUuidString: String = "00000000-0000-0000-0000-000000000000"
    ): LiveData<List<PollVote>>


    @Query(
        """
        SELECT pv.*, ${MessageDao.PREFIX_MESSAGE_COLUMNS} FROM ${PollVote.TABLE_NAME} AS pv 
        INNER JOIN ${Message.TABLE_NAME} AS mess
        ON mess.id = pv.${PollVote.MESSAGE_ID}
        INNER JOIN ${Discussion.TABLE_NAME} AS disc
        ON disc.id = mess.${Message.DISCUSSION_ID}
        WHERE disc.${Discussion.BYTES_OWNED_IDENTITY} = :bytesOwnedIdentity
        AND disc.${Discussion.DISCUSSION_TYPE} = ${Discussion.TYPE_GROUP_V2}
        AND disc.${Discussion.BYTES_DISCUSSION_IDENTIFIER} = :bytesGroupIdentifier
        AND mess.${Message.TIMESTAMP} > :pendingCreationTimestamp
        AND mess.${Message.TIMESTAMP} < :creationTimestamp
        AND pv.${PollVote.VOTER} = :bytesOwnedIdentity
        AND pv.${PollVote.VOTED} = 1
        """
    )
    fun getAllMineInGroupV2DiscussionWithinTimeInterval(
        bytesOwnedIdentity: ByteArray,
        bytesGroupIdentifier: ByteArray,
        pendingCreationTimestamp: Long,
        creationTimestamp: Long,
    ): List<PollVoteAndMessage>

    data class PollVoteAndMessage(
        @Embedded val pollVote: PollVote,
        @Embedded(prefix = "mess_") val message: Message
    )
}