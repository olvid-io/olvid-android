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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.main.invitations.getAnnotatedDate
import io.olvid.messenger.main.invitations.getAnnotatedTitle
import io.olvid.messenger.main.search.GlobalSearchScreen
import io.olvid.messenger.main.search.GlobalSearchViewModel

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
    discussionMenu: DiscussionMenu
) {
    val context = LocalContext.current
    val discussionsAndLastMessages by discussionListViewModel.discussions.observeAsState()
    val invitations by invitationListViewModel.invitations.observeAsState()
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)

    AppCompatTheme {
        if (globalSearchViewModel.filter.isNullOrEmpty().not()) {
            GlobalSearchScreen(globalSearchViewModel, linkPreviewViewModel)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(refreshState)
            ) {
                val lazyListState = rememberLazyListState()
                discussionsAndLastMessages?.let { list ->
                    if (list.isEmpty().not()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = lazyListState,
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            item(key = -1) {
                                Box(
                                    modifier = Modifier
                                        .requiredHeight(1.dp)
                                        .fillMaxWidth()
                                ) {}
                            }

                            itemsIndexed(
                                items = list,
                                key = { _, item -> item.discussion.id }) { index, discussionAndLastMessage ->
                                with(discussionAndLastMessage) {
                                    Box(modifier = Modifier.animateItemPlacement()) {
                                        val invitation by remember {
                                            derivedStateOf {
                                                invitations?.sortedBy { it.invitationTimestamp }
                                                    ?.find { it.discussionId == discussion.id }
                                            }
                                        }
                                        DiscussionListItem(
                                            title = invitation?.getAnnotatedTitle(context)
                                                .takeIf { discussion.isPreDiscussion }
                                                ?: getAnnotatedTitle(context),
                                            body = invitation?.let { AnnotatedString(it.statusText) }
                                                ?: getAnnotatedBody(context),
                                            date = invitation?.getAnnotatedDate(context)
                                                ?: getAnnotatedDate(context),
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
                                            pinned = discussion.pinned,
                                            locationsShared = locationsShared,
                                            attachmentCount = if (message?.isLocationMessage == true) 0 else message?.totalAttachmentCount
                                                ?: 0,
                                            onClick = { onClick(discussion) },
                                            isPreDiscussion = discussion.isPreDiscussion,
                                            onMarkAllDiscussionMessagesRead = {
                                                discussionMenu.markAllDiscussionMessagesRead(
                                                    discussion.id
                                                )
                                            },
                                            onMarkDiscussionAsUnread = {
                                                discussionMenu.markDiscussionAsUnread(
                                                    discussion.id
                                                )
                                            },
                                            onPinDiscussion = { pinned ->
                                                discussionMenu.pinDiscussion(
                                                    discussionId = discussion.id,
                                                    pinned = pinned
                                                )
                                            },
                                            onMuteDiscussion = { muted ->
                                                discussionMenu.muteDiscussion(
                                                    discussionId = discussion.id,
                                                    muted = muted
                                                )
                                            },
                                            onDeleteDiscussion = {
                                                discussionMenu.deleteDiscussion(
                                                    discussion
                                                )
                                            },
                                            renameActionName = if (discussion.isNormalOrReadOnly) {
                                                when (discussion.discussionType) {
                                                    Discussion.TYPE_CONTACT ->
                                                        stringResource(id = R.string.menu_action_rename_contact)

                                                    Discussion.TYPE_GROUP, Discussion.TYPE_GROUP_V2 ->
                                                        stringResource(id = R.string.menu_action_rename_group)

                                                    else -> stringResource(id = R.string.menu_action_rename)
                                                }
                                            } else {
                                                stringResource(id = R.string.menu_action_rename)
                                            },
                                            onRenameDiscussion = {
                                                discussionMenu.renameDiscussion(
                                                    discussion
                                                )
                                            },
                                            onOpenSettings = {
                                                discussionMenu.openSettings(
                                                    discussion.id
                                                )
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