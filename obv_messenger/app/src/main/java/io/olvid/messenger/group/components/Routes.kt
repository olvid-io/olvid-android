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

package io.olvid.messenger.group.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.CustomGroup
import io.olvid.messenger.group.GroupCreationViewModel
import io.olvid.messenger.group.GroupTypeModel
import io.olvid.messenger.group.GroupV2DetailsViewModel
import io.olvid.messenger.group.OwnedGroupDetailsViewModel
import io.olvid.messenger.group.PrivateGroup
import io.olvid.messenger.group.ReadOnlyGroup
import io.olvid.messenger.group.SimpleGroup
import io.olvid.messenger.group.clone
import io.olvid.messenger.main.invitations.InvitationRepository

object Routes {
    const val GROUP_DETAILS = "group_details"
    const val FULL_GROUP_MEMBERS = "full_group_members"
    const val INVITE_GROUP_MEMBERS = "invite_group_members"
    const val EDIT_GROUP_MEMBERS = "edit_group_members"
    const val EDIT_GROUP_DETAILS = "edit_group_details"
    const val ADD_GROUP_MEMBERS = "add_group_members"
    const val REMOVE_GROUP_MEMBERS = "remove_group_members"
    const val EDIT_ADMINS = "edit_group_admins"
    const val CHOOSE_NEW_GROUP_ADMINS = "choose_new_group_admins"
    const val GROUP_TYPE = "group_type"
}

fun NavGraphBuilder.groupDetails(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    call: (Group2) -> Unit = {},
    imageClick: (String?) -> Unit = {},
    onFullMembersList: () -> Unit = {},
    onInviteMembers: () -> Unit = {},
    onEditMembers: () -> Unit = {},
    onEditAdmins: () -> Unit = {},
    onGroupType: () -> Unit = {}
) {
    composable(
        Routes.GROUP_DETAILS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        GroupDetailsScreen(
            groupV2DetailsViewModel = groupV2DetailsViewModel,
            call = call,
            imageClick = imageClick,
            onInviteMembers = onInviteMembers,
            onFullMembersList = onFullMembersList,
            onEditMembers = onEditMembers,
            onEditAdmins = onEditAdmins,
            onGroupType = onGroupType
        )
    }
}


fun NavGraphBuilder.inviteGroupMembers(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    onBack: () -> Unit,
) {
    composable(
        Routes.INVITE_GROUP_MEMBERS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        val group by groupV2DetailsViewModel.group.observeAsState()
        val members by groupV2DetailsViewModel.groupMembers.observeAsState()
        val invitations by InvitationRepository.invitations.observeAsState()
        val allMembersInvited = remember(members, invitations) {
            members?.all { member ->
                invitations?.any { invitation -> invitation.bytesContactIdentity.contentEquals(member.bytesContactIdentity) } ?: false
            } ?: false
        }

        BackHandler {
            onBack()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            val invitationMembers = members?.filter {
                it.contact?.let { contact ->
                    (contact.hasChannelOrPreKey() || contact.keycloakManaged) && contact.active && contact.oneToOne.not()
                } ?: false
            }
            invitationMembers?.let {
                val nonAdminsReadOnly =
                    remember(groupV2DetailsViewModel.groupType) { groupV2DetailsViewModel.groupType.areNonAdminsReadOnly() }
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        .asPaddingValues() + PaddingValues(top = 16.dp, bottom = 64.dp)
                ) {
                    item(key = ByteArray(0)) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            text = stringResource(R.string.explanation_invite_group_members_list),
                            style = OlvidTypography.body2,
                            color = colorResource(R.color.almostBlack)
                        )
                    }
                    itemsIndexed(items = it, key = {index, member -> member.bytesContactIdentity }) { index, member ->
                        GroupMemberItem(
                            modifier = Modifier
                                .then(
                                    if (index == 0) {
                                        if (it.size == 1) {
                                            Modifier.clip(RoundedCornerShape(16.dp))
                                        } else {
                                            Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        }
                                    } else if (index == it.size - 1) {
                                        Modifier.clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .background(colorResource(R.color.lighterGrey)),
                            member = member.toGroupMember(),
                            keycloakManaged = group?.keycloakManaged == true,
                            nonAdminsReadOnly = nonAdminsReadOnly,
                            onInvite = { groupV2DetailsViewModel.invite(it) }
                        )
                    }
                }
            }

            if (invitationMembers.isNullOrEmpty().not() && allMembersInvited.not()) {
                val context = LocalContext.current
                OlvidActionButton(
                    modifier = Modifier
                        .background(colorResource(R.color.whiteOverlay))
                        .safeDrawingPadding()
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    icon = R.drawable.ic_invite_all,
                    text = stringResource(R.string.menu_action_invite_all_members),
                    onClick = { groupV2DetailsViewModel.inviteAllMembers(context) }
                )
            }
        }
    }
}

fun NavGraphBuilder.fullGroupMembers(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    onBack: () -> Unit,
) {
    composable(
        Routes.FULL_GROUP_MEMBERS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        val group by groupV2DetailsViewModel.group.observeAsState()
        val members by groupV2DetailsViewModel.groupMembers.observeAsState()
        BackHandler {
            onBack()
        }
        members?.let {
            val nonAdminsReadOnly =
                remember(groupV2DetailsViewModel.groupType) { groupV2DetailsViewModel.groupType.areNonAdminsReadOnly() }
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    .asPaddingValues()
            ) {
                AppSingleton.getCurrentIdentityLiveData().value?.let { ownIdentity ->
                    item(key = ownIdentity.bytesOwnedIdentity) {
                        GroupMemberItem(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (it.isEmpty()) 16.dp else 0.dp,
                                        bottomEnd = if (it.isEmpty()) 16.dp else 0.dp,
                                    )
                                )
                                .background(colorResource(R.color.lighterGrey)),
                            member = GroupMember(
                                bytesIdentity = ownIdentity.bytesOwnedIdentity,
                                contact = null,
                                jsonIdentityDetails = ownIdentity.getIdentityDetails(),
                                fullSearchDisplayName = "",
                                isAdmin = group?.ownPermissionAdmin == true,
                                isYou = true,
                                pending = false,
                                selected = false
                            ),
                            keycloakManaged = ownIdentity.keycloakManaged,
                            nonAdminsReadOnly = nonAdminsReadOnly
                        )
                    }
                }
                items(items = it, key = { it.bytesContactIdentity }) { member ->
                    GroupMemberItem(
                        modifier = Modifier
                            .then(
                                if (member.bytesContactIdentity.contentEquals(it.lastOrNull()?.bytesContactIdentity)) {
                                    Modifier
                                        .padding(bottom = 16.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 0.dp,
                                                topEnd = 0.dp,
                                                bottomStart = 16.dp,
                                                bottomEnd = 16.dp,
                                            )
                                        )
                                } else {
                                    Modifier
                                }
                            )
                            .background(colorResource(R.color.lighterGrey)),
                        member = member.toGroupMember(),
                        keycloakManaged = group?.keycloakManaged == true,
                        nonAdminsReadOnly = nonAdminsReadOnly
                    )
                }
            }
        }
    }
}


fun NavGraphBuilder.editGroupDetails(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    editGroupDetailsViewModel: OwnedGroupDetailsViewModel,
    onTakePicture: () -> Unit,
    content: (@Composable () -> Unit)? = null,
    isGroupCreation: Boolean = false,
    isGroupV2: Boolean = true,
    onBack: () -> Unit,
    onValidate: () -> Unit
) {
    composable(
        Routes.EDIT_GROUP_DETAILS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        BackHandler {
            editGroupDetailsViewModel.initialized = false
            onBack()
        }
        LaunchedEffect(Unit) {
            if (editGroupDetailsViewModel.cloning.value.not()) {
                editGroupDetailsViewModel.setGroupV2(isGroupV2)
                editGroupDetailsViewModel.bytesOwnedIdentity =
                    groupV2DetailsViewModel.group.value?.bytesOwnedIdentity ?: byteArrayOf()
                editGroupDetailsViewModel.setBytesGroupOwnerAndUidOrIdentifier(
                    groupV2DetailsViewModel.group.value?.bytesGroupIdentifier ?: byteArrayOf()
                )
                editGroupDetailsViewModel.setOwnedGroupDetailsV2(
                    groupDetails = groupV2DetailsViewModel.getPublishedJsonDetails()
                        ?: JsonGroupDetails(),
                    photoUrl = groupV2DetailsViewModel.detailsAndPhotos?.publishedPhotoUrl
                        ?: groupV2DetailsViewModel.detailsAndPhotos?.photoUrl
                        ?: groupV2DetailsViewModel.group.value?.photoUrl,
                    personalNote = groupV2DetailsViewModel.group.value?.personalNote
                )
            }
        }
        EditGroupDetailsScreen(
            editGroupDetailsViewModel = editGroupDetailsViewModel,
            onTakePicture = onTakePicture,
            content = content,
            isGroupCreation = isGroupCreation,
            onValidate = onValidate
        )
    }
}

fun NavGraphBuilder.editGroupMembers(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    onAddMembers: () -> Unit,
    onRemoveMembers: () -> Unit
) {
    composable(
        Routes.EDIT_GROUP_MEMBERS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        MembersMainScreen(
            contacts = groupV2DetailsViewModel.groupMembers.value?.map { it.toGroupMember() }
                .orEmpty(),
            groupAdmin = groupV2DetailsViewModel.group.value?.ownPermissionAdmin == true,
            keycloakCertified = groupV2DetailsViewModel.group.value?.keycloakManaged == true,
            nonAdminsReadOnly = groupV2DetailsViewModel.groupType.areNonAdminsReadOnly(),
            gotoAddMembers = onAddMembers,
            gotoRemoveMembers = onRemoveMembers
        )
    }
}

fun NavGraphBuilder.addMembers(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    groupCreationViewModel: GroupCreationViewModel? = null,
    onValidate: (List<GroupMember>) -> Unit = {}
) {
    composable(
        Routes.ADD_GROUP_MEMBERS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        AddMembersScreen(
            groupV2DetailsViewModel = groupV2DetailsViewModel,
            preselectedContacts = groupCreationViewModel?.selectedContacts?.value.orEmpty(),
            allowEmptyGroup = groupCreationViewModel != null,
            onValidate = onValidate
        )
    }
}


fun NavGraphBuilder.removeMembers(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    onValidate: () -> Unit = {}
) {
    composable(
        Routes.REMOVE_GROUP_MEMBERS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        RemoveMembersScreen(
            groupV2DetailsViewModel = groupV2DetailsViewModel,
            onValidate = onValidate
        )
    }
}

fun NavGraphBuilder.editAdmins(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    onValidate: () -> Unit = {}
) {
    composable(
        Routes.EDIT_ADMINS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        EditGroupAdminsScreen(
            groupV2DetailsViewModel = groupV2DetailsViewModel,
            onValidate = onValidate
        )
    }
}

fun NavGraphBuilder.chooseNewGroupAdmins(
    groupCreationViewModel: GroupCreationViewModel,
    onValidate: () -> Unit = {}
) {
    composable(
        Routes.CHOOSE_NEW_GROUP_ADMINS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        ChooseGroupAdminsScreen(
            groupCreationViewModel = groupCreationViewModel,
            onValidate = onValidate
        )
    }
}

fun NavGraphBuilder.groupType(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    content: @Composable () -> Unit = {},
    showTitle: Boolean = false,
    isGroupCreation: Boolean = false,
    validationLabel: String,
    onSelectAdmins: () -> Unit = {},
    onBack: () -> Unit,
    onValidate: () -> Unit = {}
) {
    composable(
        Routes.GROUP_TYPE,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        BackHandler {
            groupV2DetailsViewModel.selectedGroupType = null
            onBack()
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorResource(id = R.color.almostWhite)
        ) {
            LaunchedEffect(groupV2DetailsViewModel.groupType) {
                if (groupV2DetailsViewModel.selectedGroupType == null) groupV2DetailsViewModel.selectedGroupType =
                    groupV2DetailsViewModel.groupType
            }
            GroupTypeSelection(
                content = content,
                groupTypes = listOf(
                    SimpleGroup,
                    PrivateGroup,
                    ReadOnlyGroup,
                    remember {
                        if (groupV2DetailsViewModel.groupType.type == GroupTypeModel.GroupType.CUSTOM) groupV2DetailsViewModel.groupType.clone() else CustomGroup()
                    }
                ),
                selectedGroupType = groupV2DetailsViewModel.selectedGroupType
                    ?: groupV2DetailsViewModel.groupType,
                selectGroupType = {
                    groupV2DetailsViewModel.selectedGroupType = it
                },
                updateReadOnly = {
                    groupV2DetailsViewModel.selectedGroupType =
                        groupV2DetailsViewModel.selectedGroupType?.clone()
                            ?.apply { readOnlySetting = it }
                },
                updateRemoteDelete = {
                    groupV2DetailsViewModel.selectedGroupType =
                        groupV2DetailsViewModel.selectedGroupType?.clone()
                            ?.apply { remoteDeleteSetting = it }
                },
                onSelectAdmins = onSelectAdmins,
                validationLabel = validationLabel,
                onValidate = {
                    if (groupV2DetailsViewModel.selectedGroupType != null && groupV2DetailsViewModel.selectedGroupType != groupV2DetailsViewModel.initialGroupType) {
                        groupV2DetailsViewModel.groupType =
                            groupV2DetailsViewModel.selectedGroupType!!
                    }
                    onValidate()
                },
                getPermissionsChanges = groupV2DetailsViewModel::getPermissionsChangeAlert,
                initialGroupType = groupV2DetailsViewModel.groupType,
                showTitle = showTitle,
                isGroupCreation = isGroupCreation,
            )
        }
    }
}