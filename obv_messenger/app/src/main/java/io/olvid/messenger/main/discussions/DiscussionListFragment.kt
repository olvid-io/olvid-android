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

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog.Builder
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
import io.olvid.messenger.R.style
import io.olvid.messenger.customClasses.MuteNotificationDialog
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.Type.DISCUSSION
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.DiscussionDao.DiscussionAndLastMessage
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.main.RefreshingFragment
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.main.search.GlobalSearchViewModel
import io.olvid.messenger.notifications.NotificationActionService


class DiscussionListFragment : RefreshingFragment() {

    private val globalSearchViewModel: GlobalSearchViewModel by activityViewModels()
    private val discussionListViewModel: DiscussionListViewModel by activityViewModels()
    private val invitationListViewModel: InvitationListViewModel by activityViewModels()
    private val linkPreviewViewModel: LinkPreviewViewModel by activityViewModels()

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
                    refreshing = refreshing,
                    onRefresh = ::onRefresh,
                    onClick = ::discussionClicked,
                    onLongClick = ::discussionLongClicked,
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

    fun markAllDiscussionMessagesRead(discussionId: Long) {
        App.runThread {
            NotificationActionService.markAllDiscussionMessagesRead(
                discussionId
            )
        }
    }

    fun markDiscussionAsUnread(discussionId: Long) {
        App.runThread {
            AppDatabase.getInstance().discussionDao().updateDiscussionUnreadStatus(
                discussionId, true
            )
        }
    }

    fun muteSelectedDiscussions(
        discussionsAndLastMessage: List<DiscussionAndLastMessage>,
        muted: Boolean
    ) {
        App.runThread {
            val context: Context = this.context ?: return@runThread
            if (muted) {
                Handler(Looper.getMainLooper()).post {
                    val muteNotificationDialog = MuteNotificationDialog(
                        context,
                        { muteExpirationTimestamp: Long?, _: Boolean, muteExceptMentioned: Boolean ->
                            actionMode?.finish()
                            App.runThread {
                                discussionsAndLastMessage.forEach { selected ->
                                    AppDatabase.getInstance()
                                        .discussionCustomizationDao()[selected.discussion.id]?.let {
                                        AppDatabase.getInstance().discussionCustomizationDao()
                                            .update(it.apply {
                                                prefMuteNotifications = muted
                                                prefMuteNotificationsTimestamp =
                                                    muteExpirationTimestamp
                                                prefMuteNotificationsExceptMentioned =
                                                    muteExceptMentioned
                                            })
                                    } ifNull {
                                        AppDatabase.getInstance().discussionCustomizationDao()
                                            .insert(DiscussionCustomization(selected.discussion.id).apply {
                                                prefMuteNotifications = muted
                                                prefMuteNotificationsTimestamp =
                                                    muteExpirationTimestamp
                                                prefMuteNotificationsExceptMentioned =
                                                    muteExceptMentioned
                                            })
                                    }
                                }
                            }
                        },
                        MuteNotificationDialog.MuteType.DISCUSSIONS,
                        discussionsAndLastMessage.firstOrNull()?.discussionCustomization?.prefMuteNotificationsExceptMentioned != false
                    )
                    muteNotificationDialog.show()
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    val builder: Builder =
                        SecureAlertDialogBuilder(context, style.CustomAlertDialog)
                            .setTitle(string.dialog_title_unmute_notifications)
                            .setPositiveButton(string.button_label_unmute_notifications) { _, _ ->
                                actionMode?.finish()
                                App.runThread {
                                    discussionsAndLastMessage.forEach { selected ->
                                        AppDatabase.getInstance()
                                            .discussionCustomizationDao()[selected.discussion.id]?.let {
                                            AppDatabase.getInstance().discussionCustomizationDao()
                                                .update(it.apply {
                                                    prefMuteNotifications = false
                                                })
                                        }
                                    }
                                }
                            }
                            .setNegativeButton(string.button_label_cancel, null)
                    builder.setMessage(string.dialog_message_unmute_notifications)
                    builder.create().show()
                }
            }
        }
    }

    fun deleteDiscussions(discussions: List<Discussion>) {
        if (discussions.isEmpty()) {
            return
        }
        App.runThread {
            // canRemoteDelete if all discussion are locked, have no members, or groupV2 with correct permission
            // couldOfferToRemoteDelete if at least one discussion has members (only relevant if canRemoteDelete is true)
            var canRemoteDelete = true
            var couldOfferToRemoteDelete = false
            for (discussion in discussions) {
                if (discussion.isNormalOrReadOnly) {
                    if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                        val group2 = AppDatabase.getInstance()
                            .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                        if (group2 != null) {
                            if (AppDatabase.getInstance().group2MemberDao().groupHasMembers(
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            ) {
                                couldOfferToRemoteDelete = true
                                if (!group2.ownPermissionRemoteDeleteAnything) {
                                    canRemoteDelete = false
                                    break
                                }
                            }
                        }
                    } else if (discussion.discussionType == Discussion.TYPE_GROUP) {
                        if (AppDatabase.getInstance().contactGroupJoinDao().groupHasMembers(
                                discussion.bytesOwnedIdentity,
                                discussion.bytesDiscussionIdentifier
                            )
                        ) {
                            canRemoteDelete = false;
                            break
                        }
                    } else {
                        canRemoteDelete = false
                        break
                    }
                }
            }

            val builder = SecureDeleteEverywhereDialogBuilder(
                requireActivity(),
                DISCUSSION,
                discussions.size,
                canRemoteDelete && couldOfferToRemoteDelete,
                true
            )
                .setDeleteCallback { deletionChoice: SecureDeleteEverywhereDialogBuilder.DeletionChoice ->
                    actionMode?.finish()
                    App.runThread {
                        discussions.forEach { discussion ->
                            DeleteMessagesTask(
                                discussion.id,
                                deletionChoice,
                                false
                            ).run()
                        }
                    }
                }
            Handler(Looper.getMainLooper()).post { builder.create().show() }
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
                if (discussionListViewModel.selection.any { it.discussion.unread }) {
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
                            markAllDiscussionMessagesRead(it.discussion.id)
                        }
                        mode.finish()
                        return true
                    }

                    R.id.popup_action_discussion_mark_as_unread -> {
                        discussionListViewModel.selection.forEach {
                            markDiscussionAsUnread(it.discussion.id)
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

                    R.id.popup_action_discussion_mute_notifications -> {
                        muteSelectedDiscussions(discussionListViewModel.selection, true)
                        return true
                    }

                    R.id.popup_action_discussion_unmute_notifications -> {
                        muteSelectedDiscussions(discussionListViewModel.selection, false)
                        return true
                    }

                    R.id.action_delete_messages -> {
                        deleteDiscussions(discussionListViewModel.selection.map { it.discussion })
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