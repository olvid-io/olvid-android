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

package io.olvid.messenger.databases.emoji_search

import androidx.room.Dao
import androidx.room.Query

@Dao
interface EmojiDao {
    @Query("SELECT emojis.emoji FROM emojis JOIN emojis_fts ON emojis.rowid = (emojis_fts.docid >> 8) WHERE emojis_fts.keywords MATCH :query AND emojis_fts.lid = :languageId")
    fun search(query: String, languageId: Int): List<Emoji>
}
