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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult.Page
import androidx.paging.PagingState
import androidx.paging.cachedIn
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class GlobalSearchViewModel : ViewModel() {
    companion object {
        const val MESSAGE_SEARCH_LIMIT: Int = 50
        const val ATTACHMENT_SEARCH_LIMIT: Int = 30
    }

    var filter by mutableStateOf<String?>(null)
        private set
    private var filterRegexes by mutableStateOf<List<Regex>?>(null)
    private var searchJob by mutableStateOf<Job?>(null)

    var contactsFound by mutableStateOf<List<Contact>?>(null)
    var groupsFound by mutableStateOf<List<SearchableDiscussion>?>(null)
    var otherDiscussionsFound by mutableStateOf<List<SearchableDiscussion>?>(null)
    var messagesFound by mutableStateOf<Flow<PagingData<DiscussionAndMessage>>?>(null)
    var fylesFound by mutableStateOf<Flow<PagingData<FyleAndOrigin>>?>(null)
    var linksFound by mutableStateOf<Flow<PagingData<FyleAndOrigin>>?>(null)

    val searching by derivedStateOf { searchJob != null }

    fun search(bytesOwnedIdentity: ByteArray, text: String) {
        searchJob?.cancel()
        searchJob = null
        filter = text
        filterRegexes = filter
            ?.trim()
            ?.split("\\s+".toRegex())
            ?.filter { it.isNotEmpty() }
            ?.map { Regex.fromLiteral(StringUtils.unAccent(it)) }
        val tokenizedQuery = GlobalSearchTokenizer.tokenize(text).fullTextSearchEscape()
        searchJob = viewModelScope.launch {
            supervisorScope {
                searchMessages(bytesOwnedIdentity, tokenizedQuery)
                searchFyles(bytesOwnedIdentity, tokenizedQuery)
                searchLinks(bytesOwnedIdentity, tokenizedQuery)
                val deferredSearches = listOf(
                    async(Dispatchers.IO) {
                        searchContacts(bytesOwnedIdentity)
                    },
                    async(Dispatchers.IO) {
                        searchDiscussions(bytesOwnedIdentity)
                    }
                )
                runCatching { deferredSearches.awaitAll() }
                searchJob = null
            }
        }
    }

    fun clear() {
        filter = null
        filterRegexes = null
        searchJob?.cancel()
        searchJob = null

        messagesFound = null
        contactsFound = null
        groupsFound = null
        otherDiscussionsFound = null
        fylesFound = null
        linksFound = null
    }

    @Composable
    fun highlight(content: String): AnnotatedString {
        return highlight(content = AnnotatedString(content))
    }

    @Composable
    fun highlight(content: AnnotatedString): AnnotatedString {
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

    private fun searchContacts(bytesOwnedIdentity: ByteArray) {
        contactsFound = AppDatabase.getInstance().contactDao()
            .getAllForOwnedIdentitySync(bytesOwnedIdentity)
            .filter { contact ->
                filterRegexes?.all { it.containsMatchIn(contact.fullSearchDisplayName) } == true
            }
    }

    private fun searchDiscussions(bytesOwnedIdentity: ByteArray) {
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

        groupsFound = groups
        otherDiscussionsFound = otherDiscussions
    }

    private fun searchMessages(bytesOwnedIdentity: ByteArray, tokenizedQuery: String) {
        messagesFound = pager(MESSAGE_SEARCH_LIMIT) { offset ->
            AppDatabase.getInstance().globalSearchDao().messageGlobalSearch(
                bytesOwnedIdentity,
                tokenizedQuery,
                MESSAGE_SEARCH_LIMIT,
                offset)
        }.flow.cachedIn(viewModelScope)
    }

    private fun searchFyles(bytesOwnedIdentity: ByteArray, tokenizedQuery: String) {
        fylesFound = pager(ATTACHMENT_SEARCH_LIMIT) { offset ->
            AppDatabase.getInstance().globalSearchDao().attachmentsGlobalSearch(bytesOwnedIdentity, tokenizedQuery, ATTACHMENT_SEARCH_LIMIT, offset)
        }.flow.cachedIn(viewModelScope)
    }

    private fun searchLinks(bytesOwnedIdentity: ByteArray, tokenizedQuery: String) {
        linksFound = pager(ATTACHMENT_SEARCH_LIMIT) { offset ->
            AppDatabase.getInstance().globalSearchDao().linksGlobalSearch(bytesOwnedIdentity, tokenizedQuery, ATTACHMENT_SEARCH_LIMIT, offset)
        }.flow.cachedIn(viewModelScope)
    }

    private fun <T : Any>pager(
        searchLimit: Int,
        loadData: suspend (offset: Int) -> List<T>
    ) = Pager(
        config = PagingConfig(
            pageSize = searchLimit,
            prefetchDistance = 3 * searchLimit
        )
    ) {
        object : PagingSource<Int, T>() {
            override fun getRefreshKey(state: PagingState<Int, T>): Int? {
                return state.anchorPosition?.let { anchorPosition ->
                    val anchorPage = state.closestPageToPosition(anchorPosition)
                    anchorPage?.prevKey?.plus(searchLimit)
                        ?: anchorPage?.nextKey?.minus(
                            searchLimit
                        )
                }
            }

            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
                val offset = params.key ?: 0
                val data = loadData(offset)
                return Page(
                    data = data,
                    prevKey = if (offset == 0) null else offset - searchLimit,
                    nextKey = if (data.size < searchLimit) null else offset + searchLimit
                )
            }
        }
    }
}