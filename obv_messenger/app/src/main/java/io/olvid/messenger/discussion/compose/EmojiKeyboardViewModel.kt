/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.discussion.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Emoji
import kotlinx.coroutines.launch

class EmojiKeyboardViewModel : ViewModel() {

    private val emojiDao = AppDatabase.getInstance().emojiDao()

    // recent emojis typed with the emoji keyboard, kept separate from recent reaction emojis;
    // frozen while the keyboard is open (usage is still recorded) so the column never reorders
    // under the user's finger — call refreshRecentEmojis() when the keyboard is (re)opened
    var recentEmojis: List<String> by mutableStateOf(emptyList())
        private set

    fun refreshRecentEmojis() {
        viewModelScope.launch {
            recentEmojis = emojiDao.getRecentKeyboardEmojis().map { it.emoji }
        }
    }

    fun onEmojiInserted(emoji: String) {
        viewModelScope.launch {
            val existingEmoji = emojiDao.getEmoji(emoji)
            if (existingEmoji != null) {
                emojiDao.updateLastUsedKeyboard(emoji, System.currentTimeMillis())
            } else {
                emojiDao.upsert(Emoji(emoji = emoji, lastUsedKeyboard = System.currentTimeMillis()))
            }
        }
    }
}
