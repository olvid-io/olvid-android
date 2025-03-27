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

import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.MenuItem.OnMenuItemClickListener
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.compose.foundation.lazy.LazyListState
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.FragmentActivity
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.fullTextSearchEscape
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.GlobalSearchTokenizer
import io.olvid.messenger.settings.SettingsActivity

class DiscussionSearch(
    private val activity: FragmentActivity,
    private val menu: Menu,
    val searchItem: MenuItem,
    private val discussionId: Long
) : OnMenuItemClickListener, OnActionExpandListener, OnQueryTextListener {
    private var menuPrev: MenuItem? = null
    private var menuNext: MenuItem? = null
    private var currentPosition: Int = 0
    var scrollTo: ((messageId: Long) -> Unit)? = null
    var lazyListState: LazyListState? = null
    val viewModel: DiscussionSearchViewModel by activity.viewModels()

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
        (searchItem.actionView as? SearchView)?.let {
            if (!it.hasFocus()) {
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }
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

    fun setInitialSearchQuery(filter: String, matchingMessageId: Long?) {
        App.runThread {
            filter(
                filter,
                lazyListState?.layoutInfo?.visibleItemsInfo?.first()?.key as? Long ?: Long.MAX_VALUE,
                matchingMessageId
            )
        }
    }


    override fun onMenuItemClick(item: MenuItem): Boolean {
        try {
            val id = item.itemId
            if (id == R.id.action_prev) {
                if (currentPosition < viewModel.matchedMessageAndFyleIds.lastIndex) {
                    scrollTo?.invoke(viewModel.matchedMessageAndFyleIds[++currentPosition].first)
                }
            } else if (id == R.id.action_next) {
                if (currentPosition > 0) {
                    scrollTo?.invoke(viewModel.matchedMessageAndFyleIds[--currentPosition].first)
                }
            }
            menuPrev?.isEnabled = currentPosition < viewModel.matchedMessageAndFyleIds.lastIndex
            menuNext?.isEnabled = currentPosition > 0
        } catch (ignored: Exception) {
        }
        return true
    }

    @Synchronized
    private fun filter(filterString: String?, firstVisibleMessageId: Long, messageIdToSetAsCurrent: Long? = null) {
        currentPosition = 0
        viewModel.filterRegexes = filterString
            ?.trim()
            ?.split("\\s+".toRegex())
            ?.filter { it.isNotEmpty() }
            ?.map {
                Regex(
                    """(\b|(?<=_)(?!_))${Regex.escape(StringUtils.unAccent(it))}""",
                    RegexOption.IGNORE_CASE
                )
            }
        if (!filterString.isNullOrBlank()) {
            val tokenizedQuery = GlobalSearchTokenizer.tokenize(filterString).fullTextSearchEscape()
            val result = AppDatabase.getInstance().globalSearchDao()
                .discussionSearch(discussionId, tokenizedQuery).map { it.id to it.fyleId }
            if (viewModel.matchedMessageAndFyleIds != result || messageIdToSetAsCurrent != null) {
                viewModel.matchedMessageAndFyleIds = result
                val found: Boolean
                if (messageIdToSetAsCurrent != null) {
                    result.indexOfFirst { it.first == messageIdToSetAsCurrent }.let {
                        if (it == -1) {
                            found = false
                        } else {
                            found = true
                            currentPosition = it
                        }
                    }
                } else {
                    found = false
                }

                if (viewModel.matchedMessageAndFyleIds.isNotEmpty()) {
                    activity.runOnUiThread {
                        menuPrev?.isEnabled =
                            currentPosition < viewModel.matchedMessageAndFyleIds.lastIndex
                        menuNext?.isEnabled = currentPosition > 0
                    }
                    if (!found) {
                        val forwardMatch =
                            viewModel.matchedMessageAndFyleIds.indexOfLast { messageAndFyleId -> messageAndFyleId.first >= firstVisibleMessageId } // TODO: comparing messageIds is a little risky, it would be better to compare their sortIndex...
                        if (forwardMatch != -1) {
                            currentPosition = forwardMatch
                        }
                        scrollTo?.invoke(viewModel.matchedMessageAndFyleIds[currentPosition].first)
                        return
                    }
                }
            }
        }
    }

    init {
        searchItem.setOnActionExpandListener(this)
        (searchItem.actionView as SearchView?)?.apply {
            queryHint = activity.getString(R.string.hint_search_message)
            inputType =
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_FILTER
            if (SettingsActivity.useKeyboardIncognitoMode()) {
                imeOptions = imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            setOnQueryTextListener(this@DiscussionSearch)
        }
    }
}
