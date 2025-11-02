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

package io.olvid.messenger.discussion.message.reactions

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Emoji
import kotlinx.coroutines.launch

class ReactionViewModel : ViewModel() {

    private val emojiDao = AppDatabase.getInstance().emojiDao()

    val preferredReactions: LiveData<List<String>> =  emojiDao.getFavoriteEmojis().map { it.map { it.emoji } }
    val recentReactions: LiveData<List<String>> = emojiDao.getRecentEmojis().map { it.map { it.emoji } }

    fun toggleFavorite(emoji: String) {
        viewModelScope.launch {
            val existingEmoji = emojiDao.getEmoji(emoji)
            if (existingEmoji != null) {
                emojiDao.setFavorite(emoji, !existingEmoji.isFavorite)
            } else {
                emojiDao.upsert(
                    Emoji(
                        emoji = emoji,
                        isFavorite = true,
                        lastUsed = 0L
                    )
                )
            }
        }
    }

    fun onReact(emoji: String) {
        viewModelScope.launch {
            val existingEmoji = emojiDao.getEmoji(emoji)
            if (existingEmoji != null) {
                emojiDao.updateLastUsed(emoji, System.currentTimeMillis())
            } else {
                emojiDao.upsert(Emoji(emoji = emoji, lastUsed = System.currentTimeMillis()))
            }
        }
    }
}