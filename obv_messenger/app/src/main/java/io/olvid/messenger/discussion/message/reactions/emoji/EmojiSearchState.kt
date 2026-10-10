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

package io.olvid.messenger.discussion.message.reactions.emoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.olvid.messenger.customClasses.EmojiList
import io.olvid.messenger.customClasses.LanguageUtils
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.emoji_search.EmojiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// composition-scoped on purpose: each picker gets its own search state, so a query can never
// leak from one picker into the next — it simply dies with the composable hosting it
@Stable
class EmojiSearchState(private val scope: CoroutineScope) {
    private val emojiDao = EmojiDatabase.getInstance().emojiDao()
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
            scope.launch(Dispatchers.IO) {
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

@Composable
fun rememberEmojiSearchState(): EmojiSearchState {
    val scope = rememberCoroutineScope()
    return remember { EmojiSearchState(scope) }
}
