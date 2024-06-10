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

package io.olvid.messenger.main.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.StringUtils2
import io.olvid.messenger.customClasses.fullTextSearchEscape
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.GlobalSearchTokenizer
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndOrigin
import io.olvid.messenger.databases.dao.MessageDao.DiscussionAndMessage
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GlobalSearchViewModel : ViewModel() {
    companion object {
        const val MESSAGE_SEARCH_LIMIT: Int = 50
        const val ATTACHMENT_SEARCH_LIMIT: Int = 30
    }
    var filter by mutableStateOf<String?>(null)
        private set
    private var filterRegexes by mutableStateOf<List<Regex>?>(null)
    var currentSearchTask by mutableStateOf<SearchTask?>(null)

    var contactsFound by mutableStateOf<List<Contact>?>(null)
    var groupsFound by mutableStateOf<List<SearchableDiscussion>?>(null)
    var otherDiscussionsFound by mutableStateOf<List<SearchableDiscussion>?>(null)
    var messagesFound by mutableStateOf<List<DiscussionAndMessage>?>(null)
    var bookmarksFound by mutableStateOf<List<DiscussionAndMessage>?>(null)
    var fylesFound by mutableStateOf<List<FyleAndOrigin>?>(null)

    val searching by derivedStateOf { currentSearchTask != null }
    var noResults = derivedStateOf {
        contactsFound.isNullOrEmpty() && groupsFound.isNullOrEmpty() && otherDiscussionsFound.isNullOrEmpty() && messagesFound.isNullOrEmpty() && bookmarksFound.isNullOrEmpty() && fylesFound.isNullOrEmpty()
    }
    var messageLimitReachedCount : String? by mutableStateOf(null)
    var attachmentLimitReachedCount : String? by mutableStateOf(null)

    fun search(bytesOwnedIdentity: ByteArray, text: String) {
        filter = text
        currentSearchTask?.cancel()
        currentSearchTask = SearchTask(bytesOwnedIdentity, text)
    }

    fun clear() {
        filter = null
        filterRegexes = null
        currentSearchTask?.cancel()
        currentSearchTask = null

        messagesFound = null
        bookmarksFound = null
        contactsFound = null
        groupsFound = null
        otherDiscussionsFound = null
        messageLimitReachedCount = null
        attachmentLimitReachedCount = null
    }

    @Composable
    fun highlight(content: String): AnnotatedString {
        return highlight(content = AnnotatedString(content))
    }

    @Composable
    fun highlight(content: AnnotatedString) : AnnotatedString {
        return AnnotatedString.Builder(content).apply {
            filterRegexes?.let {
                StringUtils2.computeHighlightRanges(content.toString(), it).forEach { range ->
                    addStyle(
                        SpanStyle(
                            background = colorResource(id = R.color.searchHighlightColor),
                            color = colorResource(id = R.color.black)
                        ),
                        range.first,
                        range.second
                    )
                }
            }
        }.toAnnotatedString()
    }

    @Composable
    fun truncateMessageBody(body: String): String {
         filterRegexes?.let {
            val ranges = StringUtils2.computeHighlightRanges(body, it)
            if (ranges.isNotEmpty()) {
                val pos = body.lastIndexOf("\n", ranges.first().first)
                    .coerceAtLeast(ranges.first().first - 16)
                    .coerceAtLeast(body.lastIndexOf("\r", ranges.first().first)) + 1
                if (pos > 0) {
                    return ("…" + body.substring(pos))
                }
            }
        }
        return body
    }

    inner class SearchTask(val bytesOwnedIdentity: ByteArray, val text: String) {
        private var cancelled = false

        init {
            App.runThread(this::run)
        }

        fun run() {
            filterRegexes = filter
                ?.trim()
                ?.split("\\s+".toRegex())
                ?.filter { it.isNotEmpty() }
                ?.map { Regex.fromLiteral(StringUtils.unAccent(it)) }

            val contacts = AppDatabase.getInstance().contactDao()
                .getAllForOwnedIdentitySync(bytesOwnedIdentity)
                .filter { contact ->
                    filterRegexes?.all { it.containsMatchIn(contact.fullSearchDisplayName) } == true
                }
            if (cancelled) {
                return
            }
            contactsFound = contacts

            val groups: MutableList<SearchableDiscussion> = ArrayList()
            val otherDiscussions: MutableList<SearchableDiscussion> = ArrayList()
            AppDatabase.getInstance().discussionDao()
                .getAllForGlobalSearch(bytesOwnedIdentity)
                .filter { !it.discussion.isNormalOrReadOnly || it.discussion.discussionType != Discussion.TYPE_CONTACT } // filter out normal contact discussions
                .map { SearchableDiscussion(it) }
                .forEach { searchableDiscussion ->
                    if (filterRegexes?.all { it.containsMatchIn(searchableDiscussion.patternMatchingField) } == true) {
                        if (searchableDiscussion.isGroupDiscussion) {
                            groups.add(searchableDiscussion)
                        } else {
                            otherDiscussions.add(searchableDiscussion)
                        }
                    }
                }
            if (cancelled) {
                return
            }
            groupsFound = groups
            otherDiscussionsFound = otherDiscussions

            val tokenizedQuery = GlobalSearchTokenizer.tokenize(text).fullTextSearchEscape()
            val messages = AppDatabase.getInstance().messageDao()
                .globalSearch(bytesOwnedIdentity, tokenizedQuery, MESSAGE_SEARCH_LIMIT)
            if (cancelled) {
                return
            }
            messagesFound = messages

            messageLimitReachedCount =
                if (messages.size >= MESSAGE_SEARCH_LIMIT) {
                    val count = AppDatabase.getInstance().messageDao().globalSearchCount(bytesOwnedIdentity, tokenizedQuery, 5 * MESSAGE_SEARCH_LIMIT + 1)
                    if (count > 5 * MESSAGE_SEARCH_LIMIT) {
                        "${5 * MESSAGE_SEARCH_LIMIT}+"
                    } else if (count > MESSAGE_SEARCH_LIMIT) {
                        count.toString()
                    } else {
                        null
                    }
                } else {
                    null
                }

            val fyles = AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .globalSearch(bytesOwnedIdentity, tokenizedQuery, ATTACHMENT_SEARCH_LIMIT)
            if (cancelled) {
                return
            }
            fylesFound = fyles

            attachmentLimitReachedCount =
                if (fyles.size >= ATTACHMENT_SEARCH_LIMIT) {
                    val count = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().globalSearchCount(bytesOwnedIdentity, tokenizedQuery, 5 * ATTACHMENT_SEARCH_LIMIT + 1)
                    if (count > 5 * ATTACHMENT_SEARCH_LIMIT) {
                        "${5 * ATTACHMENT_SEARCH_LIMIT}+"
                    } else if (count > ATTACHMENT_SEARCH_LIMIT) {
                        count.toString()
                    } else {
                        null
                    }
                } else {
                    null
                }

            if (!cancelled) {
                currentSearchTask = null
            }
        }

        fun cancel() {
            cancelled = true
        }
    }
}
