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
import androidx.room.PrimaryKey


@Entity(
    tableName = Reaction.TABLE_NAME,
    foreignKeys = [ForeignKey(
        entity = Message::class,parentColumns = ["id"],
        childColumns = [Reaction.MESSAGE_ID],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(Reaction.MESSAGE_ID),
        Index(value = [Reaction.MESSAGE_ID, Reaction.BYTES_IDENTITY], unique = true)
    ]
)
data class Reaction(
    @JvmField @ColumnInfo(name = MESSAGE_ID) var messageId: Long,
    @JvmField @ColumnInfo(name = BYTES_IDENTITY) var bytesIdentity: ByteArray? = null,
    @JvmField @ColumnInfo(name = EMOJI) var emoji: String? = null,
    @JvmField @ColumnInfo(name = TIMESTAMP) var timestamp: Long
) {
    @JvmField
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    override fun toString(): String {
        return "Reaction: $emoji | messageid: $messageId"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reaction

        if (messageId != other.messageId) return false
        if (bytesIdentity != null) {
            if (other.bytesIdentity == null) return false
            if (!bytesIdentity.contentEquals(other.bytesIdentity)) return false
        } else if (other.bytesIdentity != null) return false
        if (emoji != other.emoji) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + (bytesIdentity?.contentHashCode() ?: 0)
        result = 31 * result + (emoji?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }

    companion object {
        const val TABLE_NAME = "reactions_table"
        const val MESSAGE_ID = "message_id"
        const val BYTES_IDENTITY = "bytes_identity"
        const val EMOJI = "emoji"
        const val TIMESTAMP = "timestamp"
    }
}