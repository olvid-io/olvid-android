/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
import androidx.room.Fts4
import androidx.room.FtsOptions

@Entity(tableName = FyleMessageJoinWithStatus.FTS_TABLE_NAME)
@Fts4(contentEntity = FyleMessageJoinWithStatus::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=2"])
data class FyleMessageJoinWithStatusFTS(
    @ColumnInfo(name = FyleMessageJoinWithStatus.FILE_NAME)
    val fileName : String,
    @ColumnInfo(name = FyleMessageJoinWithStatus.TEXT_CONTENT)
    val textContent : String
)