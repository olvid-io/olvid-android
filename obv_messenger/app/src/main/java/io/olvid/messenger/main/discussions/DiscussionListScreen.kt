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

package io.olvid.messenger.main.discussions

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.material3.SnackbarResult.Dismissed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.tasks.PropagatePinnedDiscussionsChangeTask
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.message.OutboundMessageStatus
import io.olvid.messenger.discussion.message.SwipeForActionBox
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator
import io.olvid.messenger.main.archived.ArchiveSettingsContent
import io.olvid.messenger.main.archived.ArchivedDiscussionsActivity
import io.olvid.messenger.main.archived.SwipeActionBackground
import io.olvid.messenger.main.bookmarks.BookmarkedMessagesActivity
import io.olvid.messenger.main.bookmarks.BookmarksViewModel
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.main.invitations.getAnnotatedDate
import io.olvid.messenger.main.invitations.getAnnotatedTitle
import io.olvid.messenger.main.search.GlobalSearchScreen
import io.olvid.messenger.main.search.GlobalSearchViewModel
import io.olvid.messenger.main.tips.TipItem
import io.olvid.messenger.main.tips.TipsViewModel
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun DiscussionListScreen(
    globalSearchViewModel: GlobalSearchViewModel,
    discussionListViewModel: DiscussionListViewModel,
    invitationListViewModel: InvitationListViewModel,
    linkPreviewViewModel: LinkPreviewViewModel,
    bookmarksViewModel: BookmarksViewModel,
    tipsViewModel: TipsViewModel,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onClick: (discussion: Discussion) -> Unit,
    onLongClick: (discussion: Discussion) -> Unit,
    onMarkAsRead: (discussionId: Long) -> Unit,
    onMarkAsUnread: (discussionId: Long) -> Unit,
    invalidateActionMode: () -> Unit,
) {
    val context = LocalContext.current
    val discussionsAndLastMessages by discussionListViewModel.discussions.observeAsState()
    val invitations by invitationListViewModel.invitations.observeAsState()
    val archivedDiscussions by discussionListViewModel.archivedDiscussions.observeAsState()
    val bookmarkedMessages by bookmarksViewModel.bookmarkedMessages.observeAsState()
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)
    val hapticFeedback = LocalHapticFeedback.current

    AppCompatTheme {
        if (globalSearchViewModel.filter.isNullOrEmpty().not()) {
            GlobalSearchScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                globalSearchViewModel = globalSearchViewModel,
                linkPreviewViewModel = linkPreviewViewModel
            )
        } else {
            LaunchedEffect(discussionsAndLastMessages) {
                discussionListViewModel.reorderList = discussionsAndLastMessages
                if (discussionListViewModel.selection.isEmpty().not()) {
                    invalidateActionMode()
                }
            }

            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        modifier = Modifier.padding(bottom = 80.dp),
                        hostState = snackbarHostState
                    ) { snackbarData ->
                        Snackbar(
                            contentColor = colorResource(R.color.alwaysWhite),
                            actionColor = colorResource(R.color.olvid_gradient_light),
                            snackbarData = snackbarData
                        )
                    }
                },
            ) { contentPadding ->
                // Screen content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = colorResource(id = R.color.almostWhite))
                        .pullRefresh(refreshState)
                ) {
                    val lazyListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = 1,
                        initialFirstVisibleItemScrollOffset = 1,
                    )

                    val reorderableState = rememberReorderableLazyListState(
                        lazyListState = lazyListState
                    ) { from, to ->
                        discussionListViewModel.reorderList =
                            discussionListViewModel.reorderList?.toMutableList()?.apply {
                                val fromIndex = indexOfFirst { it.discussion.id == from.key }
                                var toIndex = indexOfFirst { it.discussion.id == to.key }
                                val item = removeAt(fromIndex.coerceAtMost(size))
                                if ((to.key as Long) < 0) {
                                    if (item.discussion.pinned != 0) {
                                        item.discussion.pinned = 0
                                        toIndex = (fromIndex - 1).coerceAtLeast(0)
                                    } else {
                                        item.discussion.pinned = 1
                                        toIndex = (fromIndex + 1).coerceAtMost(size)
                                    }
                                }
                                add(toIndex, item)
                            }
                    }

                    val reorderable = discussionListViewModel.selection.isEmpty().not()
                            && (discussionListViewModel.reorderList?.size ?: 0) > 1
                    var showArchiveSettings by remember { mutableStateOf(false) }
                    if (showArchiveSettings) {
                        Dialog(
                            onDismissRequest = { showArchiveSettings = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            ArchiveSettingsContent(onOptionChosen = {
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .edit {
                                        putBoolean(
                                            SettingsActivity.USER_DIALOG_HIDE_UNARCHIVE_SETTINGS,
                                            true
                                        )
                                    }
                                showArchiveSettings = false
                            })
                        }
                    }

                    LaunchedEffect(discussionListViewModel.cancelableArchivedDiscussions) {
                        if (discussionListViewModel.cancelableArchivedDiscussions.isNotEmpty()) {
                            if (!showArchiveSettings) {
                                showArchiveSettings =
                                    PreferenceManager.getDefaultSharedPreferences(context)
                                        .getBoolean(
                                            SettingsActivity.USER_DIALOG_HIDE_UNARCHIVE_SETTINGS,
                                            false
                                        ).not()
                            }

                            scope.launch {
                                snackbarHostState.currentSnackbarData?.apply {
                                    dismiss()
                                }
                                val count =
                                    discussionListViewModel.cancelableArchivedDiscussions.size
                                val result = snackbarHostState.showSnackbar(
                                    message = context.resources.getQuantityString(
                                        R.plurals.label_discussion_archive_done,
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
                                            archived = false,
                                            cancelable = false
                                        )
                                        discussionListViewModel.cancelableArchivedDiscussions =
                                            emptyList()
                                    }

                                    Dismissed -> {
                                        // unpin pinned discussion if archiving is not canceled
                                        if (discussionListViewModel.cancelableArchivedDiscussions.size == count) {
                                            val toUnpin =
                                                discussionListViewModel.cancelableArchivedDiscussions
                                            App.runThread {
                                                toUnpin.forEach { discussion ->
                                                    AppDatabase.getInstance().discussionDao()
                                                        .updatePinned(discussion.id, 0)
                                                    PropagatePinnedDiscussionsChangeTask(discussion.bytesOwnedIdentity).run()
                                                }
                                            }
                                            discussionListViewModel.cancelableArchivedDiscussions =
                                                emptyList()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    discussionListViewModel.reorderList?.partition { it.discussion.pinned != 0 }
                        ?.toList()?.let { grouped ->
                            if (grouped.all { it.isEmpty() }.not()
                                || archivedDiscussions.isNullOrEmpty().not()
                                || bookmarkedMessages.isNullOrEmpty().not()
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .navigationBarsPadding(),
                                    state = lazyListState,
                                    contentPadding = PaddingValues(bottom = 64.dp),
                                ) {

                                    item(key = -5L) {
                                        if (archivedDiscussions.isNullOrEmpty()
                                                .not() && discussionListViewModel.selection.isEmpty()
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            context.startActivity(
                                                                Intent(
                                                                    context,
                                                                    ArchivedDiscussionsActivity::class.java
                                                                )
                                                            )
                                                        }
                                                        .background(
                                                            colorResource(
                                                                id = R.color.almostWhite
                                                            )
                                                        )
                                                        .padding(
                                                            top = 16.dp,
                                                            bottom = 16.dp,
                                                            start = 16.dp,
                                                            end = 4.dp
                                                        )
                                                        .cutoutHorizontalPadding(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        modifier = Modifier.padding(
                                                            start = 16.dp,
                                                            end = 32.dp,
                                                        ),
                                                        painter = painterResource(id = R.drawable.ic_archive),
                                                        tint = colorResource(R.color.almostBlack),
                                                        contentDescription = null
                                                    )
                                                    Text(
                                                        modifier = Modifier.weight(1f),
                                                        text = stringResource(R.string.activity_title_archived_discussions),
                                                        color = colorResource(id = R.color.almostBlack),
                                                        style = OlvidTypography.h3,
                                                    )
                                                    val unreadCount =
                                                        archivedDiscussions?.count { it.unreadCount > 0 || it.discussion.unread }
                                                            ?: 0
                                                    AnimatedVisibility(visible = unreadCount > 0) {
                                                        Text(
                                                            modifier = Modifier
                                                                .padding(horizontal = 6.dp),
                                                            text = "$unreadCount",
                                                            style = OlvidTypography.body1.copy(
                                                                fontWeight = FontWeight.Medium
                                                            ),
                                                            color = colorResource(id = R.color.olvid_gradient_light)
                                                        )
                                                    }
                                                }
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = 84.dp,
                                                            end = 12.dp
                                                        )
                                                        .requiredHeight(1.dp)
                                                        .background(
                                                            color = colorResource(id = R.color.lightGrey)
                                                        )
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .requiredHeight(1.dp)
                                                    .fillMaxWidth()
                                            ) {}
                                        }
                                    }

                                    item(key = -4L) {
                                        Box(
                                            modifier = Modifier
                                                .requiredHeight(1.dp)
                                                .fillMaxWidth()
                                        ) {}
                                    }

                                    tipsViewModel.tipToShow?.let { tip ->
                                        item(key = -3L) {
                                            val activity = LocalActivity.current
                                            Column {
                                                TipItem(
                                                    refreshTipState = {
                                                        (activity as? MainActivity)?.let {
                                                            App.runThread { tipsViewModel.refreshTipToShow(it) }
                                                        }
                                                    },
                                                    tipToShow = tip,
                                                    expirationDays = tipsViewModel.deviceExpirationDays,
                                                )
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = 84.dp,
                                                            end = 12.dp
                                                        )
                                                        .requiredHeight(1.dp)
                                                        .background(
                                                            color = colorResource(id = R.color.lightGrey)
                                                        )
                                                )
                                            }
                                        }
                                    }

                                    if (bookmarkedMessages.isNullOrEmpty()
                                            .not() && discussionListViewModel.selection.isEmpty()
                                    ) {
                                        item(key = -2L) {
                                            AnimatedVisibility(
                                                discussionListViewModel.selection.isEmpty(),
                                                enter = slideInVertically(),
                                                exit = slideOutVertically()
                                            ) {
                                                Column {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                context.startActivity(
                                                                    Intent(
                                                                        context,
                                                                        BookmarkedMessagesActivity::class.java
                                                                    )
                                                                )
                                                            }
                                                            .background(
                                                                colorResource(
                                                                    id = R.color.almostWhite
                                                                )
                                                            )
                                                            .padding(
                                                                top = 16.dp,
                                                                bottom = 16.dp,
                                                                start = 16.dp,
                                                                end = 4.dp
                                                            )
                                                            .cutoutHorizontalPadding(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            modifier = Modifier.padding(
                                                                start = 16.dp,
                                                                end = 32.dp,
                                                            ),
                                                            painter = painterResource(id = R.drawable.ic_star),
                                                            tint = colorResource(R.color.almostBlack),
                                                            contentDescription = null
                                                        )
                                                        Text(
                                                            modifier = Modifier.weight(1f),
                                                            text = stringResource(R.string.activity_title_bookmarks),
                                                            color = colorResource(id = R.color.almostBlack),
                                                            style = OlvidTypography.h3,
                                                        )
                                                        Text(
                                                            modifier = Modifier
                                                                .padding(horizontal = 6.dp),
                                                            text = "${bookmarkedMessages?.size ?: ""}",
                                                            style = OlvidTypography.body1.copy(
                                                                fontWeight = FontWeight.Medium
                                                            ),
                                                            color = colorResource(id = R.color.olvid_gradient_light)
                                                        )
                                                    }
                                                    Spacer(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(
                                                                start = 84.dp,
                                                                end = 12.dp
                                                            )
                                                            .requiredHeight(1.dp)
                                                            .background(
                                                                color = colorResource(id = R.color.lightGrey)
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    grouped.forEachIndexed { groupIndex, list ->
                                        if (reorderable && groupIndex > 0) {
                                            item(key = -1L) {
                                                ReorderableItem(
                                                    enabled = true,
                                                    state = reorderableState,
                                                    key = -1L
                                                ) {
                                                    PinDivider()
                                                }
                                            }
                                        }
                                        itemsIndexed(
                                            items = list,
                                            key = { _, item -> item.discussion.id }) { index, discussionAndLastMessage ->
                                            with(discussionAndLastMessage) {
                                                val unread =
                                                    unreadCount > 0 || discussion.unread
                                                ReorderableItem(
                                                    enabled = reorderable,
                                                    state = reorderableState,
                                                    key = discussion.id
                                                ) {
                                                    SwipeForActionBox(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        maxOffset = 96.dp,
                                                        enabledFromStartToEnd = discussionListViewModel.selection.isEmpty() && discussion.isPreDiscussion.not(),
                                                        enabledFromEndToStart = discussionListViewModel.selection.isEmpty() && discussion.isPreDiscussion.not(),
                                                        callbackStartToEnd = {
                                                            if (unread) {
                                                                onMarkAsRead.invoke(discussion.id)
                                                            } else {
                                                                onMarkAsUnread.invoke(discussion.id)
                                                            }
                                                        },
                                                        callbackEndToStart = {
                                                            discussionListViewModel.archiveDiscussion(
                                                                discussion,
                                                                archived = true,
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
                                                                    R.string.menu_action_archive
                                                                ),
                                                                icon = R.drawable.ic_archive,
                                                                backgroundColor = R.color.olvid_gradient_dark,
                                                                progress = progress,
                                                                fromStartToEnd = false
                                                            )
                                                        }
                                                    )
                                                    {
                                                        val invitation by remember {
                                                            derivedStateOf {
                                                                invitations?.sortedBy { it.invitationTimestamp }
                                                                    ?.find { it.discussionId == discussion.id }
                                                            }
                                                        }
                                                        DiscussionListItem(
                                                            modifier = Modifier
                                                                .background(
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    color =
                                                                        if (discussionListViewModel.isSelected(
                                                                                discussion
                                                                            )
                                                                        ) {
                                                                            colorResource(id = R.color.greySubtleOverlay)
                                                                        } else {
                                                                            colorResource(
                                                                                id = R.color.almostWhite
                                                                            )
                                                                        }
                                                                )
                                                                .cutoutHorizontalPadding(),
                                                            title = invitation?.getAnnotatedTitle(
                                                                context
                                                            )
                                                                .takeIf { discussion.isPreDiscussion }
                                                                ?: discussion.getAnnotatedTitle(
                                                                    context
                                                                ),
                                                            body = invitation?.let {
                                                                AnnotatedString(
                                                                    it.statusText
                                                                )
                                                            }
                                                                ?: discussion.getAnnotatedBody(
                                                                    context,
                                                                    message
                                                                ),
                                                            date = invitation?.getAnnotatedDate(
                                                                context
                                                            )
                                                                ?: discussion.getAnnotatedDate(
                                                                    context,
                                                                    message
                                                                ),
                                                            initialViewSetup = { initialView ->
                                                                invitation?.takeIf { discussion.isPreDiscussion }
                                                                    ?.let {
                                                                        invitationListViewModel.initialViewSetup(
                                                                            initialView,
                                                                            it
                                                                        )
                                                                    } ifNull {
                                                                    initialView.setDiscussion(
                                                                        discussion
                                                                    )
                                                                }
                                                            },
                                                            customColor = discussionCustomization?.colorJson?.color?.minus(
                                                                0x1000000
                                                            ) ?: 0x00ffffff,
                                                            backgroundImageUrl = App.absolutePathFromRelative(
                                                                discussionCustomization?.backgroundImageUrl
                                                            ),
                                                            unread = (invitation?.requiresAction() == true) || discussion.unread,
                                                            unreadCount = unreadCount,
                                                            muted = discussionCustomization?.shouldMuteNotifications() == true,
                                                            locked = discussion.isLocked && invitation == null,
                                                            mentioned = unreadMention,
                                                            pinned = discussion.pinned != 0,
                                                            reorderableScope = this@ReorderableItem.takeIf { reorderable && discussion.isPreDiscussion.not() },
                                                            locationsShared = locationsShared,
                                                            attachmentCount = if (message?.isLocationMessage == true) 0 else message?.totalAttachmentCount
                                                                ?: 0,
                                                            onClick = { onClick(discussion) },
                                                            selected = discussionListViewModel.isSelected(
                                                                discussion
                                                            ),
                                                            onLongClick = {
                                                                hapticFeedback.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                                onLongClick(discussion)
                                                            },
                                                            onDragStopped = {
                                                                discussionListViewModel.syncPinnedDiscussions()
                                                            },
                                                            lastOutboundMessageStatus = {
                                                                message?.let { lastMessage ->
                                                                    OutboundMessageStatus(
                                                                        modifier = Modifier.padding(
                                                                            end = 4.dp
                                                                        ),
                                                                        size = 14.dp,
                                                                        message = lastMessage
                                                                    )
                                                                }
                                                            }
                                                        )
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
                                    }
                                }
                            } else {
                                Column{
                                    tipsViewModel.tipToShow?.let { tip ->
                                        val activity = LocalActivity.current
                                        Column {
                                            TipItem(
                                                refreshTipState = {
                                                    (activity as? MainActivity)?.let {
                                                        App.runThread {
                                                            tipsViewModel.refreshTipToShow(
                                                                it
                                                            )
                                                        }
                                                    }
                                                },
                                                tipToShow = tip,
                                                expirationDays = tipsViewModel.deviceExpirationDays,
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MainScreenEmptyList(
                                            icon = R.drawable.tab_discussions,
                                            iconPadding = 4.dp,
                                            title = R.string.explanation_empty_discussion_list,
                                            subtitle = R.string.explanation_empty_discussion_list_sub
                                        )
                                    }
                                }
                            }
                        }

                    RefreshingIndicator(
                        refreshing = refreshing,
                        refreshState = refreshState,
                    )
                }
            }
        }
    }
}

@Composable
fun PinDivider() {
    Row(
        modifier = Modifier
            .background(color = colorResource(id = R.color.lightGrey))
            .fillMaxWidth()
            .requiredHeight(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Rounded.KeyboardArrowUp, contentDescription = "up")
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(id = R.string.label_discussion_list_pin_divider),
            style = OlvidTypography.body2.copy(
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
            painter = painterResource(id = R.drawable.ic_pinned),
            contentDescription = "pinned"
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(imageVector = Icons.Rounded.KeyboardArrowUp, contentDescription = "up")
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PinDividerPreview() {
    AppCompatTheme {
        PinDivider()
    }
}