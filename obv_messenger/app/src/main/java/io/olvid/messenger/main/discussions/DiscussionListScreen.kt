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

package io.olvid.messenger.main.discussions

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.main.invitations.getAnnotatedDate
import io.olvid.messenger.main.invitations.getAnnotatedTitle
import io.olvid.messenger.main.search.GlobalSearchScreen
import io.olvid.messenger.main.search.GlobalSearchViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun DiscussionListScreen(
    globalSearchViewModel: GlobalSearchViewModel,
    discussionListViewModel: DiscussionListViewModel,
    invitationListViewModel: InvitationListViewModel,
    linkPreviewViewModel: LinkPreviewViewModel,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onClick: (discussion: Discussion) -> Unit,
    onLongClick: (discussion: Discussion) -> Unit,
    invalidateActionMode: () -> Unit,
) {
    val context = LocalContext.current
    val discussionsAndLastMessages by discussionListViewModel.discussions.observeAsState()
    val invitations by invitationListViewModel.invitations.observeAsState()
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)
    val hapticFeedback = LocalHapticFeedback.current

    AppCompatTheme {
        if (globalSearchViewModel.filter.isNullOrEmpty().not()) {
            GlobalSearchScreen(modifier = Modifier.fillMaxSize().navigationBarsPadding(), globalSearchViewModel = globalSearchViewModel, linkPreviewViewModel = linkPreviewViewModel)
        } else {
            LaunchedEffect(discussionsAndLastMessages) {
                discussionListViewModel.reorderList = discussionsAndLastMessages
                if (discussionListViewModel.selection.isEmpty().not()) {
                    invalidateActionMode()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(refreshState)
            ) {
                val lazyListState = rememberLazyListState()

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

                val reorderable = discussionListViewModel.selection.isEmpty().not() && (discussionListViewModel.reorderList?.size ?:0) > 1

                discussionListViewModel.reorderList?.partition { it.discussion.pinned != 0 }
                    ?.toList()?.let { grouped ->
                        if (grouped.all { it.isEmpty() }.not()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = lazyListState,
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                item(key = -2L) {
                                    Box(
                                        modifier = Modifier
                                            .requiredHeight(1.dp)
                                            .fillMaxWidth()
                                    ) {}
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
                                            ReorderableItem(
                                                enabled = reorderable,
                                                state = reorderableState,
                                                key = discussion.id
                                            ) {
                                                Box {
                                                    val invitation by remember {
                                                        derivedStateOf {
                                                            invitations?.sortedBy { it.invitationTimestamp }
                                                                ?.find { it.discussionId == discussion.id }
                                                        }
                                                    }
                                                    DiscussionListItem(
                                                        title = invitation?.getAnnotatedTitle(
                                                            context
                                                        )
                                                            .takeIf { discussion.isPreDiscussion }
                                                            ?: discussion.getAnnotatedTitle(context),
                                                        body = invitation?.let { AnnotatedString(it.statusText) }
                                                            ?: discussion.getAnnotatedBody(context, message),
                                                        date = invitation?.getAnnotatedDate(context)
                                                            ?: discussion.getAnnotatedDate(context, message),
                                                        initialViewSetup = { initialView ->
                                                            invitation?.takeIf { discussion.isPreDiscussion }
                                                                ?.let {
                                                                    invitationListViewModel.initialViewSetup(
                                                                        initialView,
                                                                        it
                                                                    )
                                                                } ifNull {
                                                                initialView.setDiscussion(discussion)
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
                                                        }
                                                    )
                                                    if (index < list.size - 1) {
                                                        Spacer(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(start = 84.dp, end = 12.dp)
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
                            }
                        } else {
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

                RefreshingIndicator(
                    refreshing = refreshing,
                    refreshState = refreshState,
                )
            }
        }
    }
}

@Composable
fun PinDivider() {
    Row(
        modifier = Modifier
            .alpha(.5f)
            .fillMaxWidth()
            .requiredHeight(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Rounded.KeyboardArrowUp, contentDescription = "up")
        Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(id = R.string.label_discussion_list_pin_divider),
                fontWeight = FontWeight.Medium,
                style = OlvidTypography.body2,
                textAlign = TextAlign.Center
            )
        Spacer(modifier = Modifier.width(8.dp))
            Icon(
                modifier = Modifier.padding(top = 2.dp).size(16.dp),
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