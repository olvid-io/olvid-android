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

package io.olvid.messenger.main.bookmarks

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.material3.SnackbarResult.Dismissed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.designsystem.components.SelectionTopAppBar
import io.olvid.messenger.discussion.message.SwipeForActionBox
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.archived.SwipeActionBackground
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.main.search.SearchResult
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
class BookmarkedMessagesActivity : LockableActivity() {

    private val bookmarksViewModel: BookmarksViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        setContent {
            AppCompatTheme {
                val context = LocalContext.current
                val hapticFeedback = LocalHapticFeedback.current
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val bookmarks by bookmarksViewModel.bookmarkedMessages.observeAsState()
                Scaffold(
                    containerColor = colorResource(R.color.almostWhite),
                    contentColor = colorResource(R.color.almostBlack),
                    topBar = {
                        SelectionTopAppBar(
                            title = stringResource(R.string.activity_title_bookmarks),
                            selection = bookmarksViewModel.selection,
                            actions = listOf(R.drawable.ic_star_off to {
                                bookmarksViewModel.bookmarkMessage(
                                    *bookmarksViewModel.selection.toTypedArray(),
                                    bookmarked = false,
                                    cancelable = true
                                )
                            })
                        ) {
                            if (bookmarksViewModel.selection.isEmpty()) {
                                onBackPressedDispatcher.onBackPressed()
                            } else {
                                bookmarksViewModel.clearSelection()
                            }
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                            Snackbar(
                                contentColor = colorResource(R.color.alwaysWhite),
                                actionColor = colorResource(R.color.olvid_gradient_light),
                                snackbarData = snackbarData
                            )
                        }
                    },
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
//                            .systemBarsPadding()
                            .padding(contentPadding)
                    ) {
                        LaunchedEffect(bookmarksViewModel.cancelableBookmarkedMessages) {
                            if (bookmarksViewModel.cancelableBookmarkedMessages.isNotEmpty()) {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.apply {
                                        dismiss()
                                    }
                                    val count = bookmarksViewModel.cancelableBookmarkedMessages.size
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.resources.getQuantityString(
                                            R.plurals.label_message_unbookmark_done, count, count
                                        ),
                                        actionLabel = context.getString(R.string.snackbar_action_label_undo),
                                        duration = Short
                                    )
                                    when (result) {
                                        ActionPerformed -> {
                                            bookmarksViewModel.bookmarkMessage(
                                                *bookmarksViewModel.cancelableBookmarkedMessages.toTypedArray(),
                                                bookmarked = true,
                                                cancelable = false
                                            )
                                            bookmarksViewModel.cancelableBookmarkedMessages =
                                                emptyList()
                                        }

                                        Dismissed -> {
                                            if (bookmarksViewModel.cancelableBookmarkedMessages.size == count) {
                                                bookmarksViewModel.cancelableBookmarkedMessages =
                                                    emptyList()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        bookmarks?.takeIf { it.isNotEmpty() }?.let { bookmarks ->
                            LazyColumn {
                                items(bookmarks, key = { it.message.id }) { discussionAndMessage ->
                                    SwipeForActionBox(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(),
                                        maxOffset = 96.dp,
                                        enabledFromStartToEnd = bookmarksViewModel.selection.isEmpty(),
                                        enabledFromEndToStart = bookmarksViewModel.selection.isEmpty(),
                                        callbackStartToEnd = {
                                            bookmarksViewModel.bookmarkMessage(
                                                discussionAndMessage.message,
                                                bookmarked = false,
                                                cancelable = true
                                            )
                                        },
                                        callbackEndToStart = {
                                            bookmarksViewModel.bookmarkMessage(
                                                discussionAndMessage.message,
                                                bookmarked = false,
                                                cancelable = true
                                            )
                                        },
                                        backgroundContentFromEndToStart = { progress ->
                                            SwipeActionBackground(
                                                label = stringResource(
                                                    R.string.menu_action_unbookmark
                                                ),
                                                icon = R.drawable.ic_star_off,
                                                progress = progress,
                                                fromStartToEnd = false
                                            )
                                        },
                                        backgroundContentFromStartToEnd = { progress ->
                                            SwipeActionBackground(
                                                label = stringResource(
                                                    R.string.menu_action_unbookmark
                                                ),
                                                icon = R.drawable.ic_star_off,
                                                progress = progress,
                                                fromStartToEnd = true
                                            )
                                        }) {
                                        SearchResult(
                                            modifier = Modifier
                                                .background(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = colorResource(R.color.almostWhite)
                                                )
                                                .cutoutHorizontalPadding(),
                                            discussionAndMessage = discussionAndMessage,
                                            selected = bookmarksViewModel.selection.contains(
                                                discussionAndMessage.message
                                            ),
                                            onClick = if (bookmarksViewModel.selection.isEmpty()) {
                                                null
                                            } else {
                                                {
                                                    bookmarksViewModel.toggleSelection(
                                                        discussionAndMessage.message
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                if (bookmarksViewModel.selection.isEmpty()) {
                                                    bookmarksViewModel.enableSelection(
                                                        discussionAndMessage.message
                                                    )
                                                } else {
                                                    bookmarksViewModel.toggleSelection(
                                                        discussionAndMessage.message
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        } ?: run {
                            NoBookmarksFound()
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}

@Composable
private fun NoBookmarksFound() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        MainScreenEmptyList(
            icon = R.drawable.ic_star, title = R.string.explanation_empty_bookmarks
        )
    }
}
