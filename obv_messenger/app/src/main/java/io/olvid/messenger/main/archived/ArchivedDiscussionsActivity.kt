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

package io.olvid.messenger.main.archived

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.material3.SnackbarResult.Dismissed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.discussion.message.SwipeForActionBox
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.cutoutHorizontalPadding
import io.olvid.messenger.main.discussions.DiscussionListItem
import io.olvid.messenger.main.discussions.DiscussionListViewModel
import io.olvid.messenger.main.discussions.getAnnotatedBody
import io.olvid.messenger.main.discussions.getAnnotatedDate
import io.olvid.messenger.main.discussions.getAnnotatedTitle
import io.olvid.messenger.notifications.NotificationActionService
import kotlinx.coroutines.launch

class ArchivedDiscussionsActivity : LockableActivity() {

    private val discussionListViewModel: DiscussionListViewModel by viewModels()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.elevation = 0f
        setContent {
            val context = LocalContext.current
            val hapticFeedback = LocalHapticFeedback.current
            val archivedDiscussions by discussionListViewModel.archivedDiscussions.observeAsState()
            AppCompatTheme {
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    modifier = Modifier.background(color = colorResource(id = R.color.almostWhite)),
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                            Snackbar(
                                contentColor = colorResource(R.color.alwaysWhite),
                                actionColor = colorResource(R.color.olvid_gradient_light),
                                snackbarData = snackbarData
                            )
                        }
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color = colorResource(R.color.olvid_gradient_dark))
                            .statusBarsPadding()
                            .background(color = colorResource(R.color.almostWhite))
                            .navigationBarsPadding()
                    ) {
                        LaunchedEffect(discussionListViewModel.cancelableArchivedDiscussions) {
                            if (discussionListViewModel.cancelableArchivedDiscussions.isNotEmpty()) {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.apply {
                                        dismiss()
                                    }
                                    val count = discussionListViewModel.cancelableArchivedDiscussions.size
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.resources.getQuantityString(
                                            R.plurals.label_discussion_unarchive_done,
                                            count,
                                            count
                                        ),
                                        actionLabel = context.getString(R.string.snackbar_action_label_undo),
                                        duration = Short
                                    )
                                    when (result) {
                                        ActionPerformed -> {
                                            discussionListViewModel.archiveDiscussion(
                                                *discussionListViewModel.cancelableArchivedDiscussions.toTypedArray(),
                                                archived = true,
                                                cancelable = false
                                            )
                                            discussionListViewModel.cancelableArchivedDiscussions = emptyList()
                                        }

                                        Dismissed -> {
                                            if (discussionListViewModel.cancelableArchivedDiscussions.size == count) {
                                                discussionListViewModel.cancelableArchivedDiscussions = emptyList()
                                            }
                                        }
                                    }
                                }
                            }
                        }


                        archivedDiscussions?.filter { it.discussion.archived }
                            ?.takeIf { it.isNotEmpty() }?.let { list ->
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(list, key = {_, item -> item.discussion.id }) { index, discussionAndMessage ->
                                        var menuExpanded by remember { mutableStateOf(false) }
                                        val unread = discussionAndMessage.unreadCount > 0 || discussionAndMessage.discussion.unread
                                        SwipeForActionBox(
                                            modifier = Modifier.fillMaxWidth().animateItem(),
                                            maxOffset = 96.dp,
                                            enabledFromStartToEnd = true,
                                            enabledFromEndToStart = true,
                                            callbackStartToEnd = {
                                                if (unread) {
                                                    App.runThread {
                                                        NotificationActionService.markAllDiscussionMessagesRead(discussionAndMessage.discussion.id)
                                                    }
                                                } else {
                                                    App.runThread {
                                                        AppDatabase.getInstance().discussionDao().updateDiscussionUnreadStatus(discussionAndMessage.discussion.id, true)
                                                    }
                                                }
                                            },
                                            callbackEndToStart = {
                                                discussionListViewModel.archiveDiscussion(discussionAndMessage.discussion, archived = false, cancelable = true)
                                            },
                                            backgroundContentFromStartToEnd = { progress ->
                                                SwipeActionBackground(
                                                    label = stringResource(
                                                        if (unread) R.string.menu_action_discussion_mark_as_read else R.string.menu_action_discussion_mark_as_unread
                                                    ),
                                                    icon = if (unread) R.drawable.ic_action_mark_read else R.drawable.ic_action_mark_unread,
                                                    backgroundColor = R.color.golden,
                                                    progress = progress,
                                                    fromStartToEnd = true
                                                )
                                            },
                                            backgroundContentFromEndToStart = { progress ->
                                                SwipeActionBackground(
                                                    label = stringResource(
                                                        R.string.menu_action_unarchive
                                                    ),
                                                    icon = R.drawable.ic_unarchive,
                                                    backgroundColor = R.color.olvid_gradient_dark,
                                                    progress = progress,
                                                    fromStartToEnd = false
                                                )
                                            }
                                        ) {
                                            DropdownMenu(
                                                expanded = menuExpanded,
                                                onDismissRequest = { menuExpanded = false }) {
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.menu_action_unarchive)) },
                                                    onClick = {
                                                        discussionListViewModel.archiveDiscussion(discussionAndMessage.discussion, archived = false, cancelable = true)
                                                        menuExpanded = false
                                                    }
                                                )
                                            }
                                            DiscussionListItem(
                                                modifier = Modifier
                                                    .background(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = colorResource(R.color.almostWhite)
                                                    )
                                                    .cutoutHorizontalPadding(),
                                                title = discussionAndMessage.discussion.getAnnotatedTitle(
                                                    context
                                                ),
                                                body = discussionAndMessage.discussion.getAnnotatedBody(
                                                    context,
                                                    discussionAndMessage.message
                                                ),
                                                date = discussionAndMessage.discussion.getAnnotatedDate(
                                                    context,
                                                    discussionAndMessage.message
                                                ),
                                                initialViewSetup = { initialView ->
                                                    initialView.setDiscussion(discussionAndMessage.discussion)
                                                },
                                                customColor = discussionAndMessage.discussionCustomization?.colorJson?.color?.minus(
                                                    0x1000000
                                                ) ?: 0x00ffffff,
                                                backgroundImageUrl = App.absolutePathFromRelative(
                                                    discussionAndMessage.discussionCustomization?.backgroundImageUrl
                                                ),
                                                unread = discussionAndMessage.discussion.unread,
                                                unreadCount = discussionAndMessage.unreadCount,
                                                muted = discussionAndMessage.discussionCustomization?.shouldMuteNotifications() == true,
                                                locked = discussionAndMessage.discussion.isLocked,
                                                mentioned = discussionAndMessage.unreadMention,
                                                pinned = discussionAndMessage.discussion.pinned != 0,
                                                reorderableScope = null,
                                                locationsShared = discussionAndMessage.locationsShared,
                                                attachmentCount = if (discussionAndMessage.message?.isLocationMessage == true) 0 else discussionAndMessage.message?.totalAttachmentCount
                                                    ?: 0,
                                                onClick = {
                                                    App.openDiscussionActivity(
                                                        context,
                                                        discussionAndMessage.discussion.id
                                                    )
                                                },
                                                selected = false,
                                                onLongClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                    menuExpanded = true
                                                },
                                                onDragStopped = {}
                                            )
                                            if (index < list.size - 1) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = 84.dp,
                                                            end = 12.dp
                                                        )
                                                        .requiredHeight(1.dp)
                                                        .align(Alignment.BottomStart)
                                                        .background(
                                                            color = colorResource(id = R.color.lightGrey)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            } ?: run {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color = colorResource(R.color.olvid_gradient_dark))
                                    .statusBarsPadding()
                                    .background(color = colorResource(R.color.almostWhite))
                                    .navigationBarsPadding(),
                                contentAlignment = Alignment.Center
                            ) {
                                MainScreenEmptyList(
                                    modifier = Modifier.align(Alignment.Center),
                                    icon = R.drawable.ic_archive,
                                    title = R.string.explanation_empty_archived_discussion
                                )
                            }
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