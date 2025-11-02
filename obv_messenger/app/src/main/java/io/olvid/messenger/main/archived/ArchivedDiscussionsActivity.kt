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

import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.designsystem.components.SelectionTopAppBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.discussion.message.SwipeForActionBox
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.discussions.DiscussionListItem
import io.olvid.messenger.main.discussions.DiscussionListViewModel
import io.olvid.messenger.main.discussions.getAnnotatedBody
import io.olvid.messenger.main.discussions.getAnnotatedDate
import io.olvid.messenger.main.discussions.getAnnotatedTitle
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.notifications.NotificationActionService
import kotlinx.coroutines.launch
import kotlin.getValue

class ArchivedDiscussionsActivity : LockableActivity() {

    private val invitationListViewModel: InvitationListViewModel by viewModels()
    private val discussionListViewModel: DiscussionListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES

        setContent {
            val context = LocalContext.current
            val resources = LocalResources.current
            val hapticFeedback = LocalHapticFeedback.current
            val invitations by invitationListViewModel.invitations.observeAsState()
            val archivedDiscussions by discussionListViewModel.archivedDiscussions.observeAsState()
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

            Scaffold(
                containerColor = colorResource(R.color.almostWhite),
                contentColor = colorResource(R.color.almostBlack),
                topBar = {
                    SelectionTopAppBar(
                        title = stringResource(R.string.activity_title_archived_discussions),
                        selection = discussionListViewModel.selection,
                        actions = buildList {
                            add(R.drawable.ic_delete_outline to {
                                discussionListViewModel.deleteDiscussions(
                                    discussions = discussionListViewModel.selection.map { it.discussion },
                                    context = context,
                                    onDelete = { discussionListViewModel.clearSelection() })
                            })
                            if (discussionListViewModel.selection.any { it.discussionCustomization?.shouldMuteNotifications() == true }) {
                                add(R.drawable.ic_action_unmute to {
                                    discussionListViewModel.muteSelectedDiscussions(
                                        context = context,
                                        discussionsAndLastMessage = discussionListViewModel.selection,
                                        muted = false,
                                        onActionDone = { discussionListViewModel.clearSelection() })
                                })
                            } else {
                                add(R.drawable.ic_action_mute to {
                                    discussionListViewModel.muteSelectedDiscussions(
                                        context = context,
                                        discussionsAndLastMessage = discussionListViewModel.selection,
                                        muted = true,
                                        onActionDone = { discussionListViewModel.clearSelection() })
                                })
                            }
                            if (discussionListViewModel.selection.any { it.discussion.unread || it.unreadCount > 0 }) {
                                add(R.drawable.ic_action_mark_read to {
                                    discussionListViewModel.selection.forEach {
                                        discussionListViewModel.markAllDiscussionMessagesRead(
                                            it.discussion.id
                                        )
                                    }
                                    discussionListViewModel.clearSelection()
                                })
                            } else {
                                add(R.drawable.ic_action_mark_unread to {
                                    discussionListViewModel.selection.forEach {
                                        discussionListViewModel.markDiscussionAsUnread(
                                            it.discussion.id
                                        )
                                    }
                                    discussionListViewModel.clearSelection()
                                })
                            }
                            add(R.drawable.ic_unarchive to {
                                discussionListViewModel.archiveDiscussion(
                                    *discussionListViewModel.selection.map { it.discussion }
                                        .toTypedArray(),
                                    archived = false,
                                    cancelable = true
                                )
                                discussionListViewModel.clearSelection()
                            })
                        },
                        onBackPressed = {
                            if (discussionListViewModel.selection.isEmpty()) {
                                onBackPressedDispatcher.onBackPressed()
                            } else {
                                discussionListViewModel.clearSelection()
                            }
                        }
                    )
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
                val layoutDirection = LocalLayoutDirection.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            top = contentPadding.calculateTopPadding(),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                            bottom = 0.dp,
                        )
                ) {
                    LaunchedEffect(discussionListViewModel.cancelableArchivedDiscussions) {
                        if (discussionListViewModel.cancelableArchivedDiscussions.isNotEmpty()) {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.apply {
                                    dismiss()
                                }
                                val count =
                                    discussionListViewModel.cancelableArchivedDiscussions.size
                                val result = snackbarHostState.showSnackbar(
                                    message = resources.getQuantityString(
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
                                        discussionListViewModel.cancelableArchivedDiscussions =
                                            emptyList()
                                    }

                                    Dismissed -> {
                                        if (discussionListViewModel.cancelableArchivedDiscussions.size == count) {
                                            discussionListViewModel.cancelableArchivedDiscussions =
                                                emptyList()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    archivedDiscussions?.filter { it.discussion.archived }
                        ?.takeIf { it.isNotEmpty() }?.let { list ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Bottom)),
                                contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
                            ) {
                                itemsIndexed(
                                    list,
                                    key = { _, item -> item.discussion.id }) { index, discussionAndMessage ->
                                    val unread =
                                        discussionAndMessage.unreadCount > 0 || discussionAndMessage.discussion.unread
                                    SwipeForActionBox(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(),
                                        maxOffset = 96.dp,
                                        enabledFromStartToEnd = discussionListViewModel.selection.isEmpty(),
                                        enabledFromEndToStart = discussionListViewModel.selection.isEmpty(),
                                        callbackStartToEnd = {
                                            if (unread) {
                                                App.runThread {
                                                    NotificationActionService.markAllDiscussionMessagesRead(
                                                        discussionAndMessage.discussion.id
                                                    )
                                                }
                                            } else {
                                                App.runThread {
                                                    AppDatabase.getInstance().discussionDao()
                                                        .updateDiscussionUnreadStatus(
                                                            discussionAndMessage.discussion.id,
                                                            true
                                                        )
                                                }
                                            }
                                        },
                                        callbackEndToStart = {
                                            discussionListViewModel.archiveDiscussion(
                                                discussionAndMessage.discussion,
                                                archived = false,
                                                cancelable = true
                                            )
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
                                        val invitation by remember {
                                            derivedStateOf {
                                                invitations?.sortedBy { it.invitationTimestamp }
                                                    ?.find { it.discussionId == discussionAndMessage.discussion.id }
                                            }
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
                                            body = invitation?.let {
                                                AnnotatedString(it.statusText)
                                            } ?: discussionAndMessage.discussion.getAnnotatedBody(
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
                                            unread = (invitation?.requiresAction() == true) || discussionAndMessage.discussion.unread,
                                            unreadCount = discussionAndMessage.unreadCount,
                                            muted = discussionAndMessage.discussionCustomization?.shouldMuteNotifications() == true,
                                            locked = discussionAndMessage.discussion.isLocked && (invitation == null || invitation?.requiresAction() == false),
                                            mentioned = discussionAndMessage.unreadMention,
                                            pinned = discussionAndMessage.discussion.pinned != 0,
                                            reorderableScope = null,
                                            locationsShared = discussionAndMessage.locationsShared,
                                            pendingContact = discussionAndMessage.discussion.isLocked && invitation != null,
                                            attachmentCount = if (discussionAndMessage.message?.isLocationMessage == true) 0 else discussionAndMessage.message?.totalAttachmentCount
                                                ?: 0,
                                            imageAndVideoCount = if (discussionAndMessage.message?.isLocationMessage == true) 0 else discussionAndMessage.message?.imageCount
                                                ?: 0,
                                            videoCount = discussionAndMessage.message?.videoCount ?: 0,
                                            audioCount = discussionAndMessage.message?.audioCount ?: 0,
                                            firstFileName = discussionAndMessage.message?.firstAttachmentName,
                                            onClick = {
                                                if (discussionListViewModel.selection.isEmpty()) {
                                                    App.openDiscussionActivity(
                                                        context,
                                                        discussionAndMessage.discussion.id
                                                    )
                                                } else {
                                                    discussionListViewModel.toggleSelection(
                                                        discussionAndMessage.discussion
                                                    )
                                                }
                                            },
                                            selected = discussionListViewModel.selection.contains(
                                                discussionAndMessage
                                            ),
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                if (discussionListViewModel.selection.isEmpty()) {
                                                    discussionListViewModel.enableSelection(
                                                        discussionAndMessage.discussion
                                                    )
                                                } else {
                                                    discussionListViewModel.toggleSelection(
                                                        discussionAndMessage.discussion
                                                    )
                                                }
                                            },
                                            onDragStopped = {}
                                        )
                                    }
                                }
                            }
                        } ?: run {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color = colorResource(R.color.almostWhite)),
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}