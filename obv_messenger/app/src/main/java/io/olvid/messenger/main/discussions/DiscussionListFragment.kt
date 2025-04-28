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

import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.ActionMode.Callback
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.R.string
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.main.RefreshingFragment
import io.olvid.messenger.main.bookmarks.BookmarksViewModel
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.main.search.GlobalSearchViewModel
import io.olvid.messenger.main.tips.TipsViewModel


class DiscussionListFragment : RefreshingFragment() {

    private val globalSearchViewModel: GlobalSearchViewModel by activityViewModels()
    private val discussionListViewModel: DiscussionListViewModel by activityViewModels()
    private val invitationListViewModel: InvitationListViewModel by activityViewModels()
    private val linkPreviewViewModel: LinkPreviewViewModel by activityViewModels()
    private val bookmarksViewModel: BookmarksViewModel by activityViewModels()
    private val tipsViewModel: TipsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            consumeWindowInsets = false
            setContent {
                val refreshing by refreshingViewModel.isRefreshing.collectAsStateWithLifecycle()

                LaunchedEffect(discussionListViewModel.selection) {
                    actionMode?.invalidate()
                    if (discussionListViewModel.selection.isEmpty()) {
                        actionMode?.finish()
                    } else {
                        actionMode?.title = resources.getQuantityString(
                            R.plurals.action_mode_title_discussion_list,
                            discussionListViewModel.selection.size,
                            discussionListViewModel.selection.size
                        )
                    }
                }

                DiscussionListScreen(
                    globalSearchViewModel = globalSearchViewModel,
                    discussionListViewModel = discussionListViewModel,
                    invitationListViewModel = invitationListViewModel,
                    linkPreviewViewModel = linkPreviewViewModel,
                    bookmarksViewModel = bookmarksViewModel,
                    tipsViewModel = tipsViewModel,
                    refreshing = refreshing,
                    onRefresh = ::onRefresh,
                    onClick = ::discussionClicked,
                    onLongClick = ::discussionLongClicked,
                    onMarkAsRead = { discussionListViewModel.markAllDiscussionMessagesRead(it) },
                    onMarkAsUnread = { discussionListViewModel.markDiscussionAsUnread(it) },
                    invalidateActionMode = {
                        discussionListViewModel.refreshSelection()
                        actionMode?.invalidate()
                    }
                )
            }
        }
    }

    private fun discussionLongClicked(discussion: Discussion) {
        if (discussion.isPreDiscussion.not()) {
            if (discussionListViewModel.selection.isEmpty()) {
                startSelectionActionMode()
                discussionListViewModel.enableSelection(discussion)
            } else {
                discussionListViewModel.toggleSelection(discussion)
            }
        }
    }

    private fun discussionClicked(discussion: Discussion) {
        if (discussionListViewModel.selection.isEmpty().not()) {
            if (discussion.isPreDiscussion.not()) {
                discussionListViewModel.toggleSelection(discussion)
            }
        } else {
            App.openDiscussionActivity(context, discussion.id)
        }
    }

    var actionMode: ActionMode? = null
    private val actionModeCallback: Callback by lazy {
        object : Callback {
            private lateinit var inflater: MenuInflater

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                inflater = mode.menuInflater
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                inflater.inflate(R.menu.action_menu_delete, menu)
                menu.findItem(R.id.action_delete_messages)
                    ?.setTitle(SpannableString(getString(string.menu_action_delete)).apply {
                        setSpan(
                            ForegroundColorSpan(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.red
                                )
                            ),
                            0,
                            length,
                            0
                        )
                    })
                if (discussionListViewModel.selection.any { it.discussion.pinned == 0 }) {
                    inflater.inflate(R.menu.popup_discussion_pin, menu)
                } else {
                    inflater.inflate(R.menu.popup_discussion_unpin, menu)
                }
                inflater.inflate(R.menu.popup_discussion_archive, menu)
                if (discussionListViewModel.selection.any { it.discussion.unread || it.unreadCount > 0 }) {
                    inflater.inflate(R.menu.popup_discussion_mark_as_read, menu)
                } else {
                    inflater.inflate(R.menu.popup_discussion_mark_as_unread, menu)
                }
                if (discussionListViewModel.selection.any { it.discussionCustomization?.shouldMuteNotifications() == true }) {
                    inflater.inflate(R.menu.popup_discussion_unmute, menu)
                } else {
                    inflater.inflate(R.menu.popup_discussion_mute, menu)
                }
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.popup_action_discussion_mark_as_read -> {
                        discussionListViewModel.selection.forEach {
                            discussionListViewModel.markAllDiscussionMessagesRead(it.discussion.id)
                        }
                        mode.finish()
                        return true
                    }

                    R.id.popup_action_discussion_mark_as_unread -> {
                        discussionListViewModel.selection.forEach {
                            discussionListViewModel.markDiscussionAsUnread(it.discussion.id)
                        }
                        mode.finish()
                        return true
                    }

                    R.id.popup_action_discussion_pin -> {
                        discussionListViewModel.pinSelectedDiscussions()
                        mode.finish()
                        return true
                    }

                    R.id.popup_action_discussion_unpin -> {
                        discussionListViewModel.unpinSelectedDiscussions()
                        mode.finish()
                        return true
                    }

                    R.id.popup_action_discussion_archive -> {
                        discussionListViewModel.archiveSelectedDiscussions(true)
                        mode.finish()
                        return true
                    }

                    R.id.popup_action_discussion_mute_notifications -> {
                        context?.let {
                            discussionListViewModel.muteSelectedDiscussions(
                                context = it,
                                discussionsAndLastMessage = discussionListViewModel.selection,
                                muted = true,
                                onActionDone = { actionMode?.finish() })
                        }
                        return true
                    }

                    R.id.popup_action_discussion_unmute_notifications -> {
                        context?.let {
                            discussionListViewModel.muteSelectedDiscussions(
                                context = it,
                                discussionsAndLastMessage = discussionListViewModel.selection,
                                muted = false,
                                onActionDone = { actionMode?.finish() })
                        }
                        return true
                    }

                    R.id.action_delete_messages -> {
                        discussionListViewModel.deleteDiscussions(
                            discussions = discussionListViewModel.selection.map { it.discussion },
                            context = requireActivity(),
                            onDelete = { actionMode?.finish() })
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                discussionListViewModel.clearSelection()
                actionMode = null
            }
        }
    }

    private fun startSelectionActionMode() {
        actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
    }

}