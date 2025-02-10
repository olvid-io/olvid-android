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

import android.graphics.Rect
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import io.olvid.messenger.databases.entity.TextBlock.Companion.FYLE_ID
import io.olvid.messenger.databases.entity.TextBlock.Companion.MESSAGE_ID
import io.olvid.messenger.databases.entity.TextBlock.Companion.TABLE_NAME

@Entity(
    tableName = TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Fyle::class,
            parentColumns = ["id"],
            childColumns = [FYLE_ID],
            onDelete = CASCADE
        ),
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = [MESSAGE_ID],
            onDelete = CASCADE
        ),
    ],
    indices = [
        Index(FYLE_ID),
        Index(MESSAGE_ID),
    ]
)
data class TextBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = MESSAGE_ID) val messageId: Long,
    @ColumnInfo(name = FYLE_ID) val fyleId: Long,
    @ColumnInfo(name = TEXT) val text: String,
    @ColumnInfo(name = BOUNDING_BOX) var boundingBox: Rect?,
    @ColumnInfo(name = IS_BLOCK) val isBlock: Boolean,
    @ColumnInfo(name = PARENT_BLOCK_ID) val parentBlockId: Long?
) {
    companion object {
        const val TABLE_NAME: String = "fyle_message_text_block"
        const val FYLE_ID: String = "fyle_id"
        const val MESSAGE_ID: String = "message_id"
        const val BYTES_OWNED_IDENTITY: String = "bytes_owned_identity"
        const val TEXT: String = "text"
        const val BOUNDING_BOX: String = "bounding_box"
        const val PARENT_BLOCK_ID: String = "parent_block_id"
        const val IS_BLOCK: String = "is_block"
    }
}
