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
package io.olvid.messenger.databases.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = PollVote.TABLE_NAME,
    primaryKeys = [PollVote.MESSAGE_ID, PollVote.VOTER, PollVote.VOTE_UUID],
    foreignKeys = [ForeignKey(
        entity = Message::class,
        parentColumns = ["id"],
        childColumns = [PollVote.MESSAGE_ID],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(PollVote.MESSAGE_ID),
    ]
)
data class PollVote(
    @JvmField @ColumnInfo(name = MESSAGE_ID) var messageId: Long,
    @JvmField @ColumnInfo(name = SERVER_TIMESTAMP) var serverTimestamp: Long,
    @JvmField @ColumnInfo(name = VERSION) var version: Int,
    @JvmField @ColumnInfo(name = VOTE_UUID) var voteUuid: UUID,
    @JvmField @ColumnInfo(name = VOTER) var voter: ByteArray,
    @JvmField @ColumnInfo(name = VOTED) var voted: Boolean,
    ) {
    companion object {
        const val TABLE_NAME: String = "poll_vote_table"

        const val MESSAGE_ID: String = "message_id"
        const val SERVER_TIMESTAMP: String = "server_timestamp"
        const val VERSION: String = "version"
        const val VOTER: String = "voter"
        const val VOTE_UUID: String = "vote_uuid"
        const val VOTED: String = "vote"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PollVote) return false

        if (messageId != other.messageId) return false
        if (serverTimestamp != other.serverTimestamp) return false
        if (version != other.version) return false
        if (voted != other.voted) return false
        if (voteUuid != other.voteUuid) return false
        if (!voter.contentEquals(other.voter)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + serverTimestamp.hashCode()
        result = 31 * result + version
        result = 31 * result + voted.hashCode()
        result = 31 * result + voteUuid.hashCode()
        result = 31 * result + voter.contentHashCode()
        return result
    }


}