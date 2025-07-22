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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.GroupCreationViewModel
import io.olvid.messenger.group.GroupV2DetailsViewModel
import io.olvid.messenger.main.contacts.ContactListItem
import io.olvid.messenger.main.contacts.highlight
import io.olvid.messenger.settings.SettingsActivity
import java.util.regex.Pattern

@Composable
fun EditGroupAdminsScreen(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    onValidate: () -> Unit = {}
) {
    val members by groupV2DetailsViewModel.groupMembers.observeAsState()
    val groupMembersViewModel = viewModel<GroupMembersViewModel>()

    LaunchedEffect(members) {
        groupMembersViewModel.setMembers(members?.map {
            it.toGroupMember().apply { selected = it.permissionAdmin }
        }.orEmpty())
    }

    LaunchedEffect(Unit) {
        groupV2DetailsViewModel.startEditingMembers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.almostWhite)),
    ) {
        var selectAllBeacon by remember { mutableIntStateOf(0) }
        SearchBar(
            modifier = Modifier.padding(8.dp),
            searchText = groupMembersViewModel.currentFilter.orEmpty(),
            placeholderText = stringResource(R.string.hint_search_contact_name),
            onSearchTextChanged = { groupMembersViewModel.setSearchFilter(it) },
            onClearClick = { groupMembersViewModel.setSearchFilter(null) },
            selectAllBeacon = selectAllBeacon)
        Box(modifier = Modifier.weight(1f)) {
            GroupAdminsSelection(
                members = groupMembersViewModel.filteredMembers,
                showSelectAll = groupMembersViewModel.currentFilter.isNullOrEmpty(),
                selectAdmins = {
                    groupMembersViewModel.setSelectedMembers(it.toList())
                    selectAllBeacon++
                },
                filterPatterns = groupMembersViewModel.filterPatterns
            )
            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = groupMembersViewModel.allMembers.any { it.selected xor it.isAdmin },
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorResource(R.color.whiteOverlay)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    OlvidActionButton(
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .safeDrawingPadding(),
                        text = stringResource(R.string.button_label_validate)
                    ) {
                        groupMembersViewModel.allMembers.filter { it.selected xor it.isAdmin }
                            .forEach {
                                groupV2DetailsViewModel.permissionChanged(
                                    it.bytesIdentity,
                                    it.selected
                                )
                            }
                        groupV2DetailsViewModel.publishGroupEdits()
                        onValidate()
                    }
                }
            }
        }
    }
}


@Composable
fun ChooseGroupAdminsScreen(
    groupCreationViewModel: GroupCreationViewModel,
    onValidate: () -> Unit = {}
) {
    val groupMembersViewModel = viewModel<GroupMembersViewModel>()
    LaunchedEffect(groupCreationViewModel.selectedContacts.value) {
        groupMembersViewModel.setMembers(groupCreationViewModel.selectedContacts.value.map {
            it.toGroupMember(selected = groupCreationViewModel.admins.value?.any { admin ->
                it.bytesContactIdentity.contentEquals(admin.bytesContactIdentity)
            } == true)
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.almostWhite)),
    ) {
        var selectAllBeacon by remember { mutableIntStateOf(0) }
        SearchBar(
            modifier = Modifier.padding(8.dp),
            searchText = groupMembersViewModel.currentFilter.orEmpty(),
            placeholderText = stringResource(R.string.hint_search_contact_name),
            onSearchTextChanged = { groupMembersViewModel.setSearchFilter(it) },
            onClearClick = { groupMembersViewModel.setSearchFilter(null) },
            selectAllBeacon = selectAllBeacon)
        Box(modifier = Modifier.weight(1f)) {
            GroupAdminsSelection(
                members = groupMembersViewModel.filteredMembers,
                showSelectAll = groupMembersViewModel.currentFilter.isNullOrEmpty(),
                selectAdmins = {
                    groupMembersViewModel.setSelectedMembers(it.toList())
                    groupCreationViewModel.admins.value =
                        groupMembersViewModel.allMembers.filter { it.selected }.mapNotNull { it.contact }.toHashSet()
                    selectAllBeacon++
                },
                filterPatterns = groupMembersViewModel.filterPatterns
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colorResource(R.color.whiteOverlay)),
                contentAlignment = Alignment.BottomCenter
            ) {
                OlvidActionButton(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .safeDrawingPadding(),
                    text = stringResource(R.string.button_label_next)
                ) {
                    onValidate()
                }
            }
        }
    }
}

@Composable
fun GroupAdminsSelection(
    members: List<GroupMember>?,
    showSelectAll: Boolean = true,
    selectAdmins: (HashSet<ByteArray>) -> Unit,
    filterPatterns: List<Pattern> = emptyList()
) {
    val context = LocalContext.current
    Column {
        AnimatedVisibility(showSelectAll) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = colorResource(R.color.greyOverlay))
                    ) {
                        if (members?.all { it.selected } == false) {
                            selectAdmins(members.map { it.bytesIdentity }.toHashSet())
                        } else {
                            selectAdmins(hashSetOf())
                        }
                    }
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.menu_action_select_all),
                    style = OlvidTypography.h3
                )
                Switch(
                    checked = members?.all { it.selected } == true,
                    onCheckedChange = null,
                    enabled = members?.size != 0,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colorResource(R.color.olvid_gradient_light)
                    )
                )
            }
        }
        members?.takeIf { it.isNotEmpty() }?.let {
            LazyColumn(
                contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues() + PaddingValues(top = 16.dp, bottom = 64.dp)
            ) {
                items(it) { member ->
                    ContactListItem(
                        padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        title = AnnotatedString(ContactCacheSingleton.getContactDetailsFirstLine(member.bytesIdentity) ?: member.getDisplayName(context = context)).highlight(
                            SpanStyle(
                                background = colorResource(id = R.color.searchHighlightColor),
                                color = colorResource(id = R.color.black)
                            ),
                            filterPatterns
                        ),
                        body = (ContactCacheSingleton.getContactDetailsSecondLine(member.bytesIdentity)
                            ?: member.jsonIdentityDetails?.formatPositionAndCompany(""))
                            ?.let {
                            AnnotatedString(it).highlight(
                                SpanStyle(
                                    background = colorResource(id = R.color.searchHighlightColor),
                                    color = colorResource(id = R.color.black)
                                ),
                                filterPatterns
                            )
                        },
                        onClick = {
                            val selectedAdmins =
                                members.filter { it.selected }.map { it.bytesIdentity }
                                    .toMutableList()
                            if (selectedAdmins.remove(member.bytesIdentity).not()) {
                                selectedAdmins.add(member.bytesIdentity)
                            }
                            selectAdmins(selectedAdmins.toHashSet())
                        },
                        initialViewSetup = { initialView ->
                            member.contact?.let {
                                initialView.setContact(member.contact)
                            } ?: run {
                                member.jsonIdentityDetails?.let {
                                    initialView.setInitial(
                                        member.bytesIdentity,
                                        StringUtils.getInitial(
                                            it.formatDisplayName(
                                                SettingsActivity.contactDisplayNameFormat,
                                                SettingsActivity.uppercaseLastName
                                            )
                                        )
                                    )
                                } ifNull {
                                    initialView.setUnknown()
                                }
                            }
                        },
                        endContent = {
                            Switch(
                                checked = member.selected,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = colorResource(id = R.color.olvid_gradient_light)
                                )
                            )
                        }
                    )
                }
            }
        } ?: run {
            Text(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), text = stringResource(R.string.explanation_no_contact_match_filter), textAlign = TextAlign.Center)
        }
    }
}