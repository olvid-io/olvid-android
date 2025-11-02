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

package io.olvid.messenger.discussion.message.reactions.emoji

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.customClasses.EmojiList
import io.olvid.messenger.customClasses.LanguageUtils
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.emoji_search.EmojiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmojiSearchViewModel : ViewModel() {
    private val emojiDao = EmojiDatabase.Companion.getInstance().emojiDao()
    private val languageId = LanguageUtils.getCurrentLanguageId()

    var searchText by mutableStateOf("")

    var emojis by mutableStateOf(EmojiList.EMOJIS)
    val shownEmojiVariants: MutableState<List<String>?> = mutableStateOf(null)

    fun reset() {
        onSearchTextChanged("")
    }

    fun onSearchTextChanged(text: String) {
        if (searchText != text) {
            searchText = text
            viewModelScope.launch(Dispatchers.IO) {
                emojis = if (text.isBlank()) {
                    EmojiList.EMOJIS
                } else {
                    val query = "${
                        StringUtils.unAccent(searchText)
                    }*"

                    val resultsInCurrentLang = emojiDao.search(
                        query = query,
                        languageId = languageId
                    )

                    val finalResults = if (languageId != 0) { // 0 is the ID for English
                        val resultsInEnglish = emojiDao.search(
                            query = query,
                            languageId = 0
                        )
                        (resultsInCurrentLang + resultsInEnglish).distinctBy { it.emoji }
                    } else {
                        resultsInCurrentLang
                    }

                    finalResults.mapNotNull { emoji ->
                        EmojiList.EMOJIS.firstOrNull { it.first() == emoji.emoji }
                    }
                }
            }
        }
    }
}