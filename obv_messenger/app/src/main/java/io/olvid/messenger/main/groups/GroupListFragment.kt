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

package io.olvid.messenger.main.groups

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.Group2Dao.GroupOrGroup2
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.tasks.GroupCloningTasks
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.main.RefreshingFragment

class GroupListFragment : RefreshingFragment(), OnRefreshListener, EngineNotificationListener, GroupMenu {

    private val groupListViewModel: GroupListViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            consumeWindowInsets = false
            setContent {
                val refreshing by refreshingViewModel.isRefreshing.collectAsStateWithLifecycle()
                GroupListScreen(
                    groupListViewModel = groupListViewModel,
                    refreshing = refreshing,
                    onRefresh = ::onRefresh,
                    onNewGroupClick = ::newGroupClicked,
                    onGroupClick = ::groupClicked,
                    groupMenu = this@GroupListFragment
                )
            }
        }
    }

    private fun newGroupClicked() {
        App.openGroupCreationActivity(activity)
    }

    private fun groupClicked(group: GroupOrGroup2) {
        if (group.group != null) {
            App.openGroupDetailsActivity(
                requireContext(),
                group.group.bytesOwnedIdentity,
                group.group.bytesGroupOwnerAndUid
            )
        } else if (group.group2 != null) {
            App.openGroupV2DetailsActivity(
                requireContext(),
                group.group2.bytesOwnedIdentity,
                group.group2.bytesGroupIdentifier
            )
        }
    }

    override fun rename(groupOrGroup2: GroupOrGroup2) {
        if (groupOrGroup2.group != null) {
            val group = groupOrGroup2.group
            if (group.bytesGroupOwnerIdentity == null) {
                // you own the group --> show group details and open edit details
                App.openGroupDetailsActivityForEditDetails(
                    activity,
                    group.bytesOwnedIdentity,
                    group.bytesGroupOwnerAndUid
                )
            } else {
                val editNameAndPhotoDialogFragment =
                    EditNameAndPhotoDialogFragment.newInstance(activity, group)
                editNameAndPhotoDialogFragment.show(childFragmentManager, "dialog")
            }
        } else if (groupOrGroup2.group2 != null) {
            val group2 = groupOrGroup2.group2
            if (group2.ownPermissionAdmin) {
                // you own the group --> show group details and open edit details
                App.openGroupV2DetailsActivityForEditDetails(
                    activity,
                    group2.bytesOwnedIdentity,
                    group2.bytesGroupIdentifier
                )
            } else {
                val editNameAndPhotoDialogFragment =
                    EditNameAndPhotoDialogFragment.newInstance(activity, group2)
                editNameAndPhotoDialogFragment.show(childFragmentManager, "dialog")
            }
        }
    }

    override fun clone(groupOrGroup2: GroupOrGroup2) {
            App.runThread {
                val cloneAbilityOutput = GroupCloningTasks.getClonability(
                    groupOrGroup2
                )
                Handler(Looper.getMainLooper()).post {
                    GroupCloningTasks.initiateGroupCloningOrWarnUser(
                        activity,
                        cloneAbilityOutput
                    )
                }
            }
    }

    override fun leave(groupOrGroup2: GroupOrGroup2) {
        if (groupOrGroup2.group != null) {
            val group = groupOrGroup2.group
            if (group.bytesGroupOwnerIdentity == null) {
                return
            }
            val builder = SecureAlertDialogBuilder(
                requireActivity(), R.style.CustomAlertDialog
            )
                .setTitle(R.string.dialog_title_leave_group)
                .setMessage(getString(R.string.dialog_message_leave_group, group.getCustomName()))
                .setPositiveButton(R.string.button_label_ok) { _, _: Int ->
                    try {
                        AppSingleton.getEngine()
                            .leaveGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                        App.toast(R.string.toast_message_leaving_group, Toast.LENGTH_SHORT)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
        } else if (groupOrGroup2.group2 != null) {
            val group2 = groupOrGroup2.group2
            if (group2.keycloakManaged) {
                return
            }
            App.runThread {
                if (group2.ownPermissionAdmin && group2.updateInProgress != Group2.UPDATE_SYNCING) {
                    // check you are not the only admin (among members only, as pending members could decline)
                    // only check if group update is not in progress: sometimes you can get locked in update and there is no way to leave/disband the group
                    var otherAdmin = false
                    val group2Members = AppDatabase.getInstance().group2MemberDao()
                        .getGroupMembers(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier)
                    for (group2Member in group2Members) {
                        if (group2Member.permissionAdmin) {
                            otherAdmin = true
                            break
                        }
                    }
                    if (!otherAdmin) {
                        // you are the only admin --> cannot leave the group
                        // check if there is a pending admin to change the error message
                        var pendingAdmin = false
                        val group2PendingMembers =
                            AppDatabase.getInstance().group2PendingMemberDao()
                                .getGroupPendingMembers(
                                    group2.bytesOwnedIdentity,
                                    group2.bytesGroupIdentifier
                                )
                        for (group2Member in group2PendingMembers) {
                            if (group2Member.permissionAdmin) {
                                pendingAdmin = true
                                break
                            }
                        }
                        val builder = SecureAlertDialogBuilder(
                            requireActivity(), R.style.CustomAlertDialog
                        )
                            .setTitle(R.string.dialog_title_unable_to_leave_group)
                            .setPositiveButton(R.string.button_label_ok, null)
                        if (pendingAdmin) {
                            builder.setMessage(R.string.dialog_message_unable_to_leave_group_pending_admin)
                        } else {
                            builder.setMessage(R.string.dialog_message_unable_to_leave_group)
                        }
                        Handler(Looper.getMainLooper()).post { builder.create().show() }
                        return@runThread
                    }
                }
                val groupName: String = group2.getCustomName().ifEmpty {
                    getString(R.string.text_unnamed_group)
                }
                val builder = SecureAlertDialogBuilder(
                    requireActivity(), R.style.CustomAlertDialog
                )
                    .setTitle(R.string.dialog_title_leave_group)
                    .setMessage(getString(R.string.dialog_message_leave_group, groupName))
                    .setPositiveButton(R.string.button_label_ok) { _, _: Int ->
                        try {
                            AppSingleton.getEngine().leaveGroupV2(
                                group2.bytesOwnedIdentity,
                                group2.bytesGroupIdentifier
                            )
                            App.toast(R.string.toast_message_leaving_group_v2, Toast.LENGTH_SHORT)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                Handler(Looper.getMainLooper()).post { builder.create().show() }
            }
        }
    }

    override fun disband(groupOrGroup2: GroupOrGroup2) {
        if (groupOrGroup2.group != null) {
            val group = groupOrGroup2.group
            if (group.bytesGroupOwnerIdentity != null) {
                return
            }
            val builder = SecureAlertDialogBuilder(
                requireActivity(), R.style.CustomAlertDialog
            )
                .setTitle(R.string.dialog_title_disband_group)
                .setMessage(
                    getString(
                        R.string.dialog_message_disband_group,
                        group.getCustomName()
                    )
                )
                .setPositiveButton(R.string.button_label_ok) { _, _: Int ->
                    App.runThread {
                        val groupMembers = AppDatabase.getInstance().contactGroupJoinDao()
                            .getGroupContactsSync(
                                group.bytesOwnedIdentity,
                                group.bytesGroupOwnerAndUid
                            )
                        val pendingGroupMembers =
                            AppDatabase.getInstance().pendingGroupMemberDao()
                                .getGroupPendingMembers(
                                    group.bytesOwnedIdentity,
                                    group.bytesGroupOwnerAndUid
                                )
                        if (groupMembers.isEmpty() && pendingGroupMembers.isEmpty()) {
                            try {
                                AppSingleton.getEngine().disbandGroup(
                                    group.bytesOwnedIdentity,
                                    group.bytesGroupOwnerAndUid
                                )
                                App.toast(
                                    R.string.toast_message_group_disbanded,
                                    Toast.LENGTH_SHORT
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            val confirmationBuilder = SecureAlertDialogBuilder(
                                requireActivity(), R.style.CustomAlertDialog
                            )
                                .setTitle(R.string.dialog_title_disband_group)
                                .setMessage(
                                    getString(
                                        R.string.dialog_message_disband_non_empty_group_confirmation,
                                        group.getCustomName(),
                                        groupMembers.size,
                                        pendingGroupMembers.size
                                    )
                                )
                                .setPositiveButton(R.string.button_label_ok) { _, _: Int ->
                                    // delete group
                                    try {
                                        AppSingleton.getEngine().disbandGroup(
                                            group.bytesOwnedIdentity,
                                            group.bytesGroupOwnerAndUid
                                        )
                                        App.toast(
                                            R.string.toast_message_group_disbanded,
                                            Toast.LENGTH_SHORT
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                .setNegativeButton(R.string.button_label_cancel, null)
                            Handler(Looper.getMainLooper()).post {
                                confirmationBuilder.create().show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
        } else if (groupOrGroup2.group2 != null) {
            val group2 = groupOrGroup2.group2
            if (group2.keycloakManaged || !group2.ownPermissionAdmin) {
                return
            }
            val groupName: String = group2.getCustomName().ifEmpty {
                getString(R.string.text_unnamed_group)
            }
            val builder = SecureAlertDialogBuilder(
                requireActivity(), R.style.CustomAlertDialog
            )
                .setTitle(R.string.dialog_title_disband_group)
                .setMessage(getString(R.string.dialog_message_disband_group, groupName))
                .setPositiveButton(R.string.button_label_ok) { _, _: Int ->
                    App.runThread {
                        val groupMembers = AppDatabase.getInstance().group2MemberDao()
                            .getGroupMembersAndPendingSync(
                                group2.bytesOwnedIdentity,
                                group2.bytesGroupIdentifier
                            )
                        if (groupMembers.isEmpty()) {
                            // group is empty, just delete it
                            try {
                                AppSingleton.getEngine().disbandGroupV2(
                                    group2.bytesOwnedIdentity,
                                    group2.bytesGroupIdentifier
                                )
                                App.toast(
                                    R.string.toast_message_group_disbanded,
                                    Toast.LENGTH_SHORT
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            // group is not empty, second confirmation
                            val confirmationBuilder = SecureAlertDialogBuilder(
                                requireActivity(), R.style.CustomAlertDialog
                            )
                                .setTitle(R.string.dialog_title_disband_group)
                                .setMessage(
                                    getString(
                                        R.string.dialog_message_disband_non_empty_group_v2_confirmation,
                                        groupName,
                                        groupMembers.size
                                    )
                                )
                                .setPositiveButton(R.string.button_label_ok) { _, _: Int ->
                                    // disband group
                                    try {
                                        AppSingleton.getEngine().disbandGroupV2(
                                            group2.bytesOwnedIdentity,
                                            group2.bytesGroupIdentifier
                                        )
                                        App.toast(
                                            R.string.toast_message_group_disbanded,
                                            Toast.LENGTH_SHORT
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                .setNegativeButton(R.string.button_label_cancel, null)
                            Handler(Looper.getMainLooper()).post {
                                confirmationBuilder.create().show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
        }
    }
}