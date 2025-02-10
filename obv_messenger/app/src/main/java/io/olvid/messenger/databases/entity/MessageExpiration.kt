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
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = MessageExpiration.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = [MessageExpiration.MESSAGE_ID],
            onDelete = CASCADE
        )],
    indices = [Index(
        MessageExpiration.MESSAGE_ID
    ), Index(MessageExpiration.EXPIRATION_TIMESTAMP)]
)
data class MessageExpiration(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = MESSAGE_ID) val messageId: Long,
    @ColumnInfo(name = EXPIRATION_TIMESTAMP) var expirationTimestamp: Long,
    @ColumnInfo(name = WIPE_ONLY) var wipeOnly: Boolean
) {
    companion object {
        const val TABLE_NAME: String = "message_expiration_table"
        const val MESSAGE_ID: String = "message_id"
        const val EXPIRATION_TIMESTAMP: String = "expiration_timestamp"
        const val WIPE_ONLY: String = "wipe_only"
    }
}
