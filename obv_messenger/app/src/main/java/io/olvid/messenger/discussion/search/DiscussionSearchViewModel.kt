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

package io.olvid.messenger.discussion.search

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.engine.Logger
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.StringUtils2.Companion.computeHighlightRanges
import io.olvid.messenger.customClasses.fullTextSearchEscape
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.GlobalSearchTokenizer
import io.olvid.messenger.databases.dao.GlobalSearchDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import java.util.concurrent.CancellationException

class DiscussionSearchViewModel : ViewModel() {
    private var currentPosition: Int = 0
    var focusSearchOnOpen by mutableStateOf(true)
    var searchExpanded by mutableStateOf(false)
    var searchText by mutableStateOf("")
    var filterRegexes by mutableStateOf<List<Regex>?>(null)

    var hasNext by mutableStateOf(false)
    var hasPrevious by mutableStateOf(false)

    var matchedMessageAndFyleIds by mutableStateOf<List<Pair<Long, Pair<Long?, Double>>>>(emptyList()) // messageId, fyleId

    var initialFoundItem by mutableStateOf<Long?>(null)

    var filterJob: Job? by mutableStateOf(null)

    fun reset() {
        currentPosition = 0
        focusSearchOnOpen = true
        searchExpanded = false
        searchText = ""
        filterRegexes = null
        hasNext = false
        hasPrevious = false
        matchedMessageAndFyleIds = emptyList()
        initialFoundItem = null
    }

    fun next(): Long? {
        matchedMessageAndFyleIds.getOrNull(currentPosition)?.first?.let { currentMessageId ->
            // search for the largest smaller index that points to a different messageId
            var pos = currentPosition + 1
            while (pos < matchedMessageAndFyleIds.size && matchedMessageAndFyleIds[pos].first == currentMessageId) {
                pos++
            }

            if (pos < matchedMessageAndFyleIds.size) {
                currentPosition = pos
                updateHasNextAndPrevious()
                return matchedMessageAndFyleIds[currentPosition].first
            }
        }
        return null
    }

    fun previous(): Long? {
        matchedMessageAndFyleIds.getOrNull(currentPosition)?.first?.let { currentMessageId ->
            // search for the largest smaller index that points to a different messageId
            var pos = currentPosition - 1
            while (pos >= 0 && matchedMessageAndFyleIds[pos].first == currentMessageId) {
                pos--
            }

            if (pos >= 0) {
                currentPosition = pos
                updateHasNextAndPrevious()
                return matchedMessageAndFyleIds[currentPosition].first
            }
        }
        return null
    }

    private fun updateHasNextAndPrevious() {
        // only show next/previous if there is an item in the list AND this item points to a different message
        hasNext = (currentPosition in 0..< matchedMessageAndFyleIds.lastIndex)
                && (matchedMessageAndFyleIds.last().first != matchedMessageAndFyleIds[currentPosition].first)
        hasPrevious = (currentPosition in 1.. matchedMessageAndFyleIds.lastIndex)
                && (matchedMessageAndFyleIds.first().first != matchedMessageAndFyleIds[currentPosition].first)
    }

    var lastSearchTokenizedQuery: String? = null

    fun filter(
        discussionId: Long,
        filterString: String?,
        firstVisibleMessageMessageId: Long? = null,
        messageIdToSetAsCurrent: Long? = null
    ) {
        // do not start a new search if the filterString did not change
        val tokenizedQuery = filterString?.let {
            GlobalSearchTokenizer.tokenize(filterString).filter { it.length > 1 }.fullTextSearchEscape()
        }

        if (tokenizedQuery == lastSearchTokenizedQuery) {
            return
        }
        filterJob?.cancel()
        filterJob = null
        lastSearchTokenizedQuery = tokenizedQuery

        if (tokenizedQuery.isNullOrBlank()) {
            matchedMessageAndFyleIds = emptyList()
            currentPosition = 0
            updateHasNextAndPrevious()
            filterRegexes = null
        } else {
            filterJob = viewModelScope.launch(Dispatchers.IO) {
                supervisorScope {
                    try {
                        var resultMessages = emptyList<GlobalSearchDao.MessageIdAndTimestamp>()
                        var resultAttachments = emptyList<GlobalSearchDao.MessageIdAndTimestamp>()
                        var firstMessageSortIndex: Double? = null
                        val deferredSearches = listOf(
                            async(Dispatchers.IO) {
                                val startTime = System.currentTimeMillis()
                                resultMessages = AppDatabase.getInstance().globalSearchDao()
                                    .discussionSearchMessages(discussionId, tokenizedQuery, 50)
                                Logger.d("🤡 Message search took ${System.currentTimeMillis() - startTime}ms")
                            },
                            async(Dispatchers.IO) {
                                val startTime = System.currentTimeMillis()
                                resultAttachments = AppDatabase.getInstance().globalSearchDao()
                                    .discussionSearchAttachments(discussionId, tokenizedQuery, 50)
                                Logger.d("🤡 Attachment search took ${System.currentTimeMillis() - startTime}ms")
                            },
                            async(Dispatchers.IO) {
                                firstVisibleMessageMessageId?.let {
                                    firstMessageSortIndex = AppDatabase.getInstance().messageDao().get(it)?.sortIndex
                                }
                            }
                        )
                        deferredSearches.awaitAll()

                        val result = (resultMessages + resultAttachments).sortedBy { -it.sortIndex }.map { it.id to Pair(it.fyleId, it.sortIndex) }
                        filterRegexes = filterString
                            .trim()
                            .split("\\s+".toRegex())
                            .filter { it.length > 1 }
                            .map {
                                Regex(
                                    """(\b|(?<=_)(?!_))${Regex.escape(StringUtils.unAccent(it))}""",
                                    RegexOption.IGNORE_CASE
                                )
                            }

                        if (tokenizedQuery == lastSearchTokenizedQuery
                            && (matchedMessageAndFyleIds != result || messageIdToSetAsCurrent != null)) {
                            // get a last chance to process a cancellation before setting search results
                            yield()

                            matchedMessageAndFyleIds = result
                            val found: Boolean
                            // if the discussion was opened with a target message, always select this one
                            if (messageIdToSetAsCurrent != null) {
                                result.indexOfFirst { it.first == messageIdToSetAsCurrent }.let {
                                    if (it == -1) {
                                        found = false
                                    } else {
                                        found = true
                                        currentPosition = it
                                        updateHasNextAndPrevious()
                                    }
                                }
                            } else {
                                found = false
                            }


                            // if the discussion wasn't opened with a target message or if the target message was not found,
                            // - pick the next visible message in the discussion (the forwardMatch)
                            // - if no match, pick the message 0 (the most recent)
                            if (!found) {
                                val forwardMatch =
                                    matchedMessageAndFyleIds.indexOfLast { messageAndFyleId ->
                                        messageAndFyleId.second.second >= (firstMessageSortIndex ?: Double.MAX_VALUE)
                                    }
                                currentPosition = if (forwardMatch != -1) forwardMatch else 0
                                updateHasNextAndPrevious()
                            }

                            // if the current position is in the right interval (it should always be!!) instruct the discussion to scroll
                            if (currentPosition in 0..<matchedMessageAndFyleIds.size) {
                                initialFoundItem = matchedMessageAndFyleIds[currentPosition].first
                            }
                        }
                    } catch (_: CancellationException) {
                        // if the job was canceled, make sure not to set filterJob to null
                        return@supervisorScope
                    } catch (e: Exception) {
                        Logger.x(e)
                    }
                    if (isActive) {
                        filterJob = null
                    }
                }
            }
        }
    }

    fun highlightColored(
        context: Context,
        content: AnnotatedString,
        @ColorRes textColor: Int = R.color.black,
        backgroundAlpha: Float = 1f
    ): AnnotatedString {
        return AnnotatedString.Builder(content).apply {
            filterRegexes?.let {
                computeHighlightRanges(content.toString(), it).forEach { range ->
                    addStyle(
                        SpanStyle(
                            background = Color(
                                ContextCompat.getColor(
                                    context,
                                    R.color.searchHighlightColor
                                )
                            ).copy(alpha = backgroundAlpha),
                            color = Color(ContextCompat.getColor(context, textColor))
                        ),
                        range.first,
                        range.second
                    )
                }
            }
        }.toAnnotatedString()
    }

    fun highlight(context: Context, content: AnnotatedString): AnnotatedString {
        return highlightColored(context, content)
    }
}