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
import androidx.room.PrimaryKey
import io.olvid.messenger.databases.entity.Emoji.Companion.TABLE_NAME

@Entity(tableName = TABLE_NAME)
data class Emoji(
    @PrimaryKey @ColumnInfo(name = EMOJI)
    val emoji: String,
    @ColumnInfo(name = IS_FAVORITE)
    val isFavorite: Boolean = false,
    @ColumnInfo(name = LAST_USED)
    val lastUsed: Long = 0,
) {
    companion object {
        const val TABLE_NAME = "emojis"
        const val EMOJI = "emoji"
        const val IS_FAVORITE = "is_favorite"
        const val LAST_USED = "last_used"
    }
}