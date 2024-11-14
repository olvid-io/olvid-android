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
package io.olvid.messenger.discussion

import android.content.Context
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.MenuItem.OnMenuItemClickListener
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.StringUtils2.Companion.computeHighlightRanges
import io.olvid.messenger.customClasses.fullTextSearchEscape
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.GlobalSearchTokenizer
import io.olvid.messenger.settings.SettingsActivity

class DiscussionSearch(
    private val activity: FragmentActivity,
    private val menu: Menu,
    searchItem: MenuItem,
    private val discussionId: Long
) : OnMenuItemClickListener, OnActionExpandListener, OnQueryTextListener {
    private var menuPrev: MenuItem? = null
    private var menuNext: MenuItem? = null
    private var currentPosition: Int = 0
    private var matches: List<Long> = emptyList()
    var scrollTo: ((messageId: Long) -> Unit)? = null
    var lazyListState: LazyListState? = null
    val viewModel: SearchViewModel by activity.viewModels()

    class SearchViewModel: ViewModel() {
        var filterRegexes by mutableStateOf<List<Regex>?>(null)

        fun highlight(context: Context, content: AnnotatedString): AnnotatedString {
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
                                ),
                                color = Color(ContextCompat.getColor(context, R.color.black))
                            ),
                            range.first,
                            range.second
                        )
                    }
                }
            }.toAnnotatedString()
        }
    }

    override fun onMenuItemActionExpand(searchItem: MenuItem): Boolean {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.itemId == R.id.action_call
                || item.itemId == R.id.action_unmute
            ) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
        activity.menuInflater.inflate(R.menu.menu_discussion_search, menu)
        menuPrev = menu.findItem(R.id.action_prev)
        menuPrev?.setOnMenuItemClickListener(this)
        menuNext = menu.findItem(R.id.action_next)
        menuNext?.setOnMenuItemClickListener(this)
        return true
    }

    override fun onMenuItemActionCollapse(searchItem: MenuItem): Boolean {
        activity.invalidateOptionsMenu()
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        App.runThread {
            filter(
                newText,
                lazyListState?.layoutInfo?.visibleItemsInfo?.first()?.key as? Long ?: Long.MAX_VALUE
            )
        }
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        try {
            val id = item.itemId
            if (id == R.id.action_prev) {
                if (currentPosition < matches.lastIndex) {
                    scrollTo?.invoke(matches[++currentPosition])
                }
            } else if (id == R.id.action_next) {
                if (currentPosition > 0) {
                    scrollTo?.invoke(matches[--currentPosition])
                }
            }
            menuPrev?.isEnabled = currentPosition < matches.lastIndex
            menuNext?.isEnabled = currentPosition > 0
        } catch (ignored: Exception) {
        }
        return true
    }

    @Synchronized
    private fun filter(filterString: String?, firstVisibleMessageId: Long) {
        currentPosition = 0
        viewModel.filterRegexes = filterString
            ?.trim()
            ?.split("\\s+".toRegex())
            ?.filter { it.isNotEmpty() }
            ?.map {
                Regex(
                    """\b${Regex.escape(StringUtils.unAccent(it))}""",
                    RegexOption.IGNORE_CASE
                )
            }
        if (filterString != null) {
            val tokenizedQuery = GlobalSearchTokenizer.tokenize(filterString).fullTextSearchEscape()
            matches =
                AppDatabase.getInstance().messageDao()
                    .discussionSearch(discussionId, tokenizedQuery)
            if (matches.isNotEmpty()) {
                activity.runOnUiThread {
                    menuPrev?.isEnabled = currentPosition < matches.lastIndex
                    menuNext?.isEnabled = currentPosition > 0
                }
                val forwardMatch =
                    matches.indexOfLast { messageId -> messageId >= firstVisibleMessageId }
                if (forwardMatch != -1) {
                    currentPosition = forwardMatch
                }
                scrollTo?.invoke(matches[currentPosition])
                return
            }
        }
    }

    init {
        searchItem.setOnActionExpandListener(this)
        val searchView = searchItem.actionView as SearchView?
        if (searchView != null) {
            searchView.queryHint = activity.getString(R.string.hint_search_message)
            searchView.inputType =
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_FILTER
            if (SettingsActivity.useKeyboardIncognitoMode()) {
                searchView.imeOptions =
                    searchView.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            searchView.setOnQueryTextListener(this)
        }
    }
}
