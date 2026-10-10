/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.share.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.DiscussionDao.DiscussionAndGroupMembersNames
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.components.OlvidDragHandle
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.components.GroupMemberAction
import io.olvid.messenger.group.components.GroupMembersViewModel
import io.olvid.messenger.group.components.MembersRow
import io.olvid.messenger.group.components.MembersScreenContainer
import io.olvid.messenger.group.components.toGroupMember
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDestinationPickerSheet(
    sheetState: SheetState,
    initiallySelected: List<SearchableDiscussion>,
    onConfirm: (List<SearchableDiscussion>) -> Unit,
) {
    val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()
    // remember the switchMap chain: without it every recomposition allocates a fresh LiveData +
    // observer (which momentarily emits null, flashing the list empty) and leaks the old observer.
    val discussionsLiveData = remember {
        AppSingleton.getCurrentIdentityLiveData().switchMap { identity: OwnedIdentity? ->
            if (identity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().discussionDao()
                .getAllWritableWithGroupMembersNamesOrderedByActivity(identity.bytesOwnedIdentity)
        }
    }
    val discussions by discussionsLiveData.observeAsState()

    // Key the GroupMembersViewModel on the active profile so switching identity (which has its
    // own contacts/groups) resolves a fresh instance: selections made under the previous profile
    // can no longer leak into the new one as ghost pre-selections.
    val membersViewModelKey = ownedIdentity?.bytesOwnedIdentity?.let { Logger.toHexString(it) }

    // Resolve the same GroupMembersViewModel instance that MembersScreenContainer uses (same type +
    // key + store owner) so dismissing the sheet can commit the in-progress selection exactly like
    // the CTA does, instead of discarding it.
    val groupMembersViewModel = viewModel<GroupMembersViewModel>(key = membersViewModelKey)
    val confirmSelection: () -> Unit = {
        onConfirm(groupMembersViewModel.allMembers.filter { it.selected }.mapNotNull { it.searchableDiscussion })
    }

    val searchableDiscussions: List<SearchableDiscussion> =
        discussions.orEmpty().map { wrap: DiscussionAndGroupMembersNames -> SearchableDiscussion(wrap) }
    val preselectedIds = initiallySelected.map { it.discussionId }.toSet()

    val compact = LocalWindowInfo.current.containerSize.height < 540 * LocalDensity.current.density

    ModalBottomSheet(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = if (compact) 0.dp else 48.dp),
        onDismissRequest = confirmSelection,
        sheetState = sheetState,
        containerColor = colorResource(R.color.almostWhite),
        contentColor = colorResource(R.color.almostBlack),
        dragHandle = {
            if (compact)
                Box {}
            else
                OlvidDragHandle()
        },
        contentWindowInsets = { WindowInsets() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.dialog_title_share_recipients),
                    style = OlvidTypography.h2.copy(color = colorResource(R.color.almostBlack)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = confirmSelection)
                        .padding(8.dp)
                        .size(24.dp),
                    painter = painterResource(R.drawable.ic_close),
                    tint = colorResource(R.color.almostBlack),
                    contentDescription = stringResource(R.string.content_description_close_button),
                )
            }

            MembersScreenContainer(
                members = searchableDiscussions.map {
                    it.toGroupMember(selected = preselectedIds.contains(it.discussionId))
                },
                preselectedMembers = initiallySelected.map { it.byteIdentifier },
                keycloakCertified = false,
                nonAdminsReadOnly = false,
                groupMemberAction = GroupMemberAction(
                    actionPluralRes = R.plurals.label_share_to_recipients,
                    actionColor = colorResource(R.color.olvid_gradient_light),
                    startGravity = false,
                    onActionClick = { selectedMembers ->
                        val selected = selectedMembers.mapNotNull { it.searchableDiscussion }
                        onConfirm(selected)
                    },
                ),
                requestSearchFocus = true,
                viewModelKey = membersViewModelKey,
                ignoreBottomSafeDrawingPadding = true,
            ) { groupMembersViewModel ->
                MembersRow(
                    modifier = Modifier.padding(bottom = 16.dp),
                    members = groupMembersViewModel.allMembers.filter { it.selected },
                    explanation = stringResource(R.string.explanation_choose_share_recipients),
                ) {
                    groupMembersViewModel.toggleMemberSelection(it.bytesIdentity)
                }
            }
        }
    }
}
