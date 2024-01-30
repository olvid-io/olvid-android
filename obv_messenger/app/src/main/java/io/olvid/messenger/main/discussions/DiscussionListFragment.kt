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
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.MuteNotificationDialog
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.TYPE.DISCUSSION
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.databases.tasks.PropagatePinnedDiscussionsChangeTask
import io.olvid.messenger.discussion.settings.DiscussionSettingsActivity
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.main.RefreshingFragment
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.notifications.NotificationActionService


class DiscussionListFragment : RefreshingFragment(), DiscussionMenu {

    private val discussionListViewModel: DiscussionListViewModel by activityViewModels()
    private val invitationListViewModel: InvitationListViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            consumeWindowInsets = false
            setContent {
                val refreshing by refreshingViewModel.isRefreshing.collectAsStateWithLifecycle()
                DiscussionListScreen(
                    discussionListViewModel = discussionListViewModel,
                    invitationListViewModel = invitationListViewModel,
                    refreshing = refreshing,
                    onRefresh = ::onRefresh,
                    onClick = ::discussionClicked,
                    discussionMenu = this@DiscussionListFragment
                )
            }
        }
    }

    private fun discussionClicked(discussion: Discussion) {
        App.openDiscussionActivity(context, discussion.id)
    }

    override fun markAllDiscussionMessagesRead(discussionId: Long) {
        App.runThread {
            NotificationActionService.markAllDiscussionMessagesRead(
                discussionId
            )
        }
    }

    override fun markDiscussionAsUnread(discussionId: Long) {
        App.runThread {
            AppDatabase.getInstance().discussionDao().updateDiscussionUnreadStatus(
                discussionId, true
            )
        }
    }

    override fun pinDiscussion(discussionId: Long, pinned: Boolean) {
        App.runThread {
            AppDatabase.getInstance().discussionDao().updatePinned(discussionId, pinned)
            AppDatabase.getInstance().discussionDao().getById(discussionId)?.let {
                PropagatePinnedDiscussionsChangeTask(it.bytesOwnedIdentity).run()
            }
        }
    }

    override fun muteDiscussion(discussionId: Long, muted: Boolean) {
        App.runThread {
            val context: Context = this.context ?: return@runThread
            val discussionCustomization : DiscussionCustomization? = AppDatabase.getInstance().discussionCustomizationDao()[discussionId];
            if (muted) {
                // if discussion already muted, do nothing
                if (discussionCustomization?.shouldMuteNotifications() == true) {
                    return@runThread
                }
                // else open a mute notification dialog for this discussion only
                Handler(Looper.getMainLooper()).post {
                    val muteNotificationDialog = MuteNotificationDialog(context, { muteExpirationTimestamp: Long?, _: Boolean, muteExceptMentioned: Boolean ->
                        App.runThread {
                            AppDatabase.getInstance().discussionCustomizationDao()[discussionId]?.let {
                                AppDatabase.getInstance().discussionCustomizationDao().update(it.apply {
                                    prefMuteNotifications = muted
                                    prefMuteNotificationsTimestamp = muteExpirationTimestamp
                                    prefMuteNotificationsExceptMentioned = muteExceptMentioned
                                })
                            } ifNull {
                                AppDatabase.getInstance().discussionCustomizationDao().insert(DiscussionCustomization(discussionId).apply {
                                    prefMuteNotifications = muted
                                    prefMuteNotificationsTimestamp = muteExpirationTimestamp
                                    prefMuteNotificationsExceptMentioned = muteExceptMentioned
                                })
                            }
                        }
                    }, MuteNotificationDialog.MuteType.DISCUSSION, discussionCustomization?.prefMuteNotificationsExceptMentioned != false)
                    muteNotificationDialog.show()
                }
            } else {
                // if discussion is not muted, do nothing
                if (discussionCustomization?.shouldMuteNotifications() != true) {
                    return@runThread
                }
                // else, show confirmation dialog
                Handler(Looper.getMainLooper()).post {
                    val builder: AlertDialog.Builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_unmute_notifications)
                        .setPositiveButton(R.string.button_label_unmute_notifications) { _, _ ->
                            App.runThread {
                                AppDatabase.getInstance().discussionCustomizationDao()[discussionId]?.let {
                                    AppDatabase.getInstance().discussionCustomizationDao().update(it.apply {
                                        prefMuteNotifications = false
                                    })
                                }
                            }
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    val timestamp = discussionCustomization.prefMuteNotificationsTimestamp
                    if (timestamp == null) {
                        builder.setMessage(R.string.dialog_message_unmute_notifications)
                    } else {
                        builder.setMessage(getString(R.string.dialog_message_unmute_notifications_muted_until, StringUtils.getLongNiceDateString(context, timestamp)))
                    }
                    builder.create().show()
                }
            }
        }
    }

    override fun deleteDiscussion(discussion: Discussion) {
        App.runThread {
            val title: String = discussion.title.ifEmpty {
                getString(R.string.text_unnamed_discussion)
            }
            val canRemoteDelete =
                if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                    val group2 = AppDatabase.getInstance()
                        .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                    group2?.ownPermissionRemoteDeleteAnything ?: false
                } else {
                    discussion.isNormal
                }
            val builder = if (canRemoteDelete) {
                SecureDeleteEverywhereDialogBuilder(
                    requireActivity(),
                    R.style.CustomAlertDialog
                )
                    .setTitle(R.string.dialog_title_delete_discussion)
                    .setMessage(getString(R.string.dialog_message_delete_discussion, title))
                    .setType(DISCUSSION)
                    .setDeleteCallback { deleteEverywhere: Boolean ->
                        App.runThread(
                            DeleteMessagesTask(
                                discussion.bytesOwnedIdentity,
                                discussion.id,
                                deleteEverywhere,
                                false
                            )
                        )
                    }
            } else {
                SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_discussion)
                    .setMessage(getString(R.string.dialog_message_delete_discussion, title))
                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                        App.runThread(
                            DeleteMessagesTask(
                                discussion.bytesOwnedIdentity,
                                discussion.id,
                                false,
                                false
                            )
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
            }
            Handler(Looper.getMainLooper()).post { builder.create().show() }
        }
    }

    override fun renameDiscussion(discussion: Discussion) {
        if (discussion.isNormalOrReadOnly) {
            when (discussion.discussionType) {
                Discussion.TYPE_CONTACT -> {
                    App.runThread {
                        val contact = AppDatabase.getInstance()
                            .contactDao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                        if (contact != null) {
                            Handler(Looper.getMainLooper()).post {
                                val editNameAndPhotoDialogFragment =
                                    EditNameAndPhotoDialogFragment.newInstance(
                                        activity,
                                        contact
                                    )
                                editNameAndPhotoDialogFragment.show(
                                    childFragmentManager,
                                    "dialog"
                                )
                            }
                        }
                    }
                }

                Discussion.TYPE_GROUP -> {
                    App.runThread {
                        val group = AppDatabase.getInstance()
                            .groupDao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                        if (group != null) {
                            if (group.bytesGroupOwnerIdentity == null) {
                                // you own the group --> show group details and open edit details
                                App.openGroupDetailsActivityForEditDetails(
                                    activity,
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            } else {
                                Handler(Looper.getMainLooper()).post {
                                    val editNameAndPhotoDialogFragment =
                                        EditNameAndPhotoDialogFragment.newInstance(
                                            activity,
                                            group
                                        )
                                    editNameAndPhotoDialogFragment.show(
                                        childFragmentManager,
                                        "dialog"
                                    )
                                }
                            }
                        }
                    }
                }

                Discussion.TYPE_GROUP_V2 -> {
                    App.runThread {
                        val group2 = AppDatabase.getInstance()
                            .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                        if (group2 != null) {
                            if (group2.ownPermissionAdmin) {
                                // you own the group --> show group details and open edit details
                                App.openGroupV2DetailsActivityForEditDetails(
                                    activity,
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            } else {
                                Handler(Looper.getMainLooper()).post {
                                    val editNameAndPhotoDialogFragment =
                                        EditNameAndPhotoDialogFragment.newInstance(
                                            activity,
                                            group2
                                        )
                                    editNameAndPhotoDialogFragment.show(
                                        childFragmentManager,
                                        "dialog"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // locked discussion
            val editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(
                activity,
                discussion
            )
            editNameAndPhotoDialogFragment.show(childFragmentManager, "dialog")
        }
    }

    override fun openSettings(discussionId: Long) {
        val intent = Intent(context, DiscussionSettingsActivity::class.java)
        intent.putExtra(
            DiscussionSettingsActivity.DISCUSSION_ID_INTENT_EXTRA,
            discussionId
        )
        startActivity(intent)

    }
}