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
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.components.CircleCheckBox
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.group.GroupV2DetailsViewModel
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.contacts.ContactListItem
import io.olvid.messenger.main.contacts.highlight
import io.olvid.messenger.settings.SettingsActivity

data class GroupMemberAction(
    val onContactClick: ((GroupMember) -> Unit)? = null,
    @PluralsRes
    val actionPluralRes: Int,
    val actionColor: Color,
    val onActionClick: ((List<GroupMember>) -> Unit)? = null,
    val startGravity: Boolean = true
)

@Composable
fun MembersScreenContainer(
    members: List<GroupMember>,
    ownGroupMember: GroupMember? = null,
    preselectedMembers: List<ByteArray> = emptyList(),
    keycloakCertified: Boolean,
    nonAdminsReadOnly: Boolean,
    groupMemberAction: GroupMemberAction? = null,
    allowEmptyGroup: Boolean = false,
    content: @Composable (groupMembersViewModel: GroupMembersViewModel) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val groupMembersViewModel = viewModel<GroupMembersViewModel>()

    LaunchedEffect(members) {
        groupMembersViewModel.setMembers(members)
    }
    LaunchedEffect(preselectedMembers) {
        groupMembersViewModel.setSelectedMembers(preselectedMembers, true)
    }
    LaunchedEffect(groupMembersViewModel.currentFilter) {
        if (groupMembersViewModel.currentFilter == null) {
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = groupMembersViewModel.currentFilter != null) {
        groupMembersViewModel.setSearchFilter(null)
    }

    Column {
        var selectAllBeacon by remember { mutableIntStateOf(0) }
        SearchBar(
            modifier = Modifier.padding(8.dp),
            searchText = groupMembersViewModel.currentFilter.orEmpty(),
            placeholderText = stringResource(R.string.hint_search_contact_name),
            onSearchTextChanged = { groupMembersViewModel.setSearchFilter(it) },
            onClearClick = { groupMembersViewModel.setSearchFilter(null) },
            selectAllBeacon = selectAllBeacon
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            content(groupMembersViewModel)

            Box(modifier = Modifier.weight(1f)) {
                groupMembersViewModel.filteredMembers.takeIf { it.isNotEmpty() }?.let {
                    LazyColumn(
                        contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                            .asPaddingValues() + PaddingValues(bottom = if (groupMemberAction == null) 16.dp else 64.dp)
                    ) {
                        itemsIndexed(
                            items = ownGroupMember?.let { listOf(it)}.orEmpty() + it,
                            key = {_, member -> member.bytesIdentity }) { index, member ->
                            ContactListItem(
                                modifier = Modifier
                                    .animateItem()
                                    .then(
                                        if (index == 0)
                                            Modifier.clip(
                                                RoundedCornerShape(
                                                    topStart = 16.dp,
                                                    topEnd = 16.dp,
                                                    bottomStart = if (it.isEmpty()) 16.dp else 0.dp,
                                                    bottomEnd = if (it.isEmpty()) 16.dp else 0.dp,
                                                )
                                            )
                                        else if (index == it.size)
                                            Modifier.clip(
                                                RoundedCornerShape(
                                                    bottomStart = 16.dp,
                                                    bottomEnd = 16.dp,
                                                )
                                            )
                                        else
                                            Modifier
                                    )
                                    .background(colorResource(R.color.lighterGrey)),
                                padding = PaddingValues(4.dp),
                                title = AnnotatedString(
                                    ContactCacheSingleton.getContactDetailsFirstLine(member.bytesIdentity)
                                        ?: member.getDisplayName(context = context)
                                )
                                    .highlight(
                                        SpanStyle(
                                            background = colorResource(id = R.color.searchHighlightColor),
                                            color = colorResource(id = R.color.black)
                                        ),
                                        groupMembersViewModel.filterPatterns
                                    ),
                                body = (ContactCacheSingleton.getContactDetailsSecondLine(member.bytesIdentity)
                                    ?: member.jsonIdentityDetails?.formatPositionAndCompany(""))
                                    ?.let {
                                        AnnotatedString(it).highlight(
                                            SpanStyle(
                                                background = colorResource(id = R.color.searchHighlightColor),
                                                color = colorResource(id = R.color.black)
                                            ),
                                            groupMembersViewModel.filterPatterns
                                        )
                                    },
                                onClick = {
                                    groupMemberAction?.let {
                                        groupMemberAction.onContactClick?.invoke(member)
                                            ?: run {
                                                groupMembersViewModel.toggleMemberSelection(member.bytesIdentity)
                                            }
                                    }
                                    selectAllBeacon++
                                },
                                initialViewSetup = { initialView ->
                                    if (member.isYou) {
                                        initialView.setFromCache(member.bytesIdentity)
                                    } else {
                                        member.contact?.let { contact ->
                                            initialView.setContact(
                                                contact
                                            )
                                        }
                                            ?: run {
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
                                                initialView.setKeycloakCertified(keycloakCertified = keycloakCertified)
                                            }
                                    }
                                },
                                startContent = {
                                    if (groupMemberAction?.onActionClick != null && groupMemberAction.startGravity) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CircleCheckBox(
                                            checked = member.selected,
                                            onCheckedChange = {
                                                groupMembersViewModel.toggleMemberSelection(
                                                    member.bytesIdentity
                                                )
                                                selectAllBeacon++
                                            },
                                            color = groupMemberAction.actionColor
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                },
                                endContent = {
                                    if (groupMemberAction?.onActionClick != null && groupMemberAction.startGravity.not()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        CircleCheckBox(
                                            checked = member.selected,
                                            onCheckedChange = {
                                                groupMembersViewModel.toggleMemberSelection(
                                                    member.bytesIdentity
                                                )
                                                selectAllBeacon++
                                            },
                                            color = groupMemberAction.actionColor
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        AdminEndLabel(
                                            admin = member.isAdmin,
                                            pending = member.pending,
                                            nonAdminsReadOnly = nonAdminsReadOnly
                                        )
                                    }
                                }
                            )
                        }
                    }
                } ?: run {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = colorResource(R.color.lighterGrey),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(vertical = 20.dp),
                        text = stringResource(R.string.explanation_no_contact_match_filter),
                        textAlign = TextAlign.Center
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    modifier = Modifier
                        .align(Alignment.BottomCenter),
                    visible = groupMembersViewModel.allMembers.any { it.selected } || allowEmptyGroup,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val count = groupMembersViewModel.allMembers.count { it.selected }
                    val text = if (allowEmptyGroup && count == 0) {
                        stringResource(R.string.text_empty_group)
                    } else {
                        groupMemberAction?.actionPluralRes?.let {
                            pluralStringResource(it, count, count)
                        } ?: stringResource(R.string.button_label_done)
                    }
                    groupMemberAction?.let {
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
                                text = text,
                                containerColor = groupMemberAction.actionColor
                            ) {
                                groupMemberAction.onActionClick?.invoke(groupMembersViewModel.allMembers.filter { it.selected })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MembersRow(
    modifier: Modifier = Modifier,
    members: List<GroupMember>,
    onMemberRemoved: ((GroupMember) -> Unit)? = null
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = colorResource(R.color.lighterGrey),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = members.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.explanation_choose_members),
                textAlign = TextAlign.Center,
                style = OlvidTypography.body2.copy(color = colorResource(R.color.greyTint))
            )
        }
        AnimatedVisibility(
            visible = members.isEmpty().not(),
            enter = slideInVertically(initialOffsetY = { height -> height }),
            exit = slideOutVertically(targetOffsetY = { height -> height })
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                item(key = byteArrayOf()) {
                    Spacer(modifier = Modifier.height(1.dp))
                }
                items(
                    items = members,
                    key = { it.bytesIdentity }) { member ->
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            InitialView(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(40.dp),
                                initialViewSetup = { initialView ->
                                    member.contact?.let { initialView.setContact(it) } ?: run {
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
                                }
                            )
                            onMemberRemoved?.let {
                                if (member.removableFromRow) {
                                    Icon(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = -(6.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(
                                                    bounded = false,
                                                    radius = 16.dp,
                                                    color = colorResource(R.color.red)
                                                )
                                            ) {
                                                onMemberRemoved(member)
                                            }
                                            .size(24.dp)
                                            .padding(4.dp)
                                            .background(
                                                colorResource(R.color.lighterGrey),
                                                shape = CircleShape
                                            )
                                            .padding(2.dp)
                                            .background(
                                                colorResource(R.color.greyTint),
                                                shape = CircleShape
                                            )
                                            .padding(2.dp),
                                        painter = painterResource(R.drawable.ic_close),
                                        tint = Color.White,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            modifier = Modifier.widthIn(max = 48.dp),
                            minLines = 2,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            fontSize = constantSp(12),
                            lineHeight = constantSp(14),
                            color = colorResource(R.color.almostBlack),
                            text = member.getDisplayName(context)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddMembersScreen(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    preselectedContacts: List<Contact> = emptyList(),
    allowEmptyGroup: Boolean = false,
    onValidate: (members: List<GroupMember>) -> Unit
) {
    val group by groupV2DetailsViewModel.group.observeAsState()
    val contacts by
    AppSingleton.getCurrentIdentityLiveData()
        .switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().group2Dao()
                .getAllValidContactsNotInGroup(
                    ownedIdentity.bytesOwnedIdentity,
                    group?.bytesGroupIdentifier ?: byteArrayOf(),
                    groupV2DetailsViewModel.groupMembers.value?.map { it.bytesContactIdentity }
                        .orEmpty(),
                    emptyList()
                )
        }.observeAsState()

    MembersScreenContainer(
        members = contacts.orEmpty().map {
            it.toGroupMember(selected = preselectedContacts.contains(it))
        },
        preselectedMembers = preselectedContacts.map { it.bytesContactIdentity },
        keycloakCertified = group?.keycloakManaged == true,
        nonAdminsReadOnly = groupV2DetailsViewModel.groupType.areNonAdminsReadOnly(),
        allowEmptyGroup = allowEmptyGroup,
        groupMemberAction = GroupMemberAction(
            actionPluralRes = R.plurals.label_add_members,
            actionColor = colorResource(R.color.olvid_gradient_light),
            startGravity = false,
            onActionClick = onValidate
        )
    ) { groupMembersViewModel ->
        MembersRow(
            modifier = Modifier.padding(bottom = 16.dp),
            members = groupMembersViewModel.allMembers.filter { it.selected }
                .plus(groupV2DetailsViewModel.groupMembers.value?.map {
                    it.toGroupMember(
                        selected = true,
                        removableFromRow = false
                    )
                }
                    .orEmpty())
        ) {
            groupMembersViewModel.toggleMemberSelection(it.bytesIdentity)
        }
    }
}


@Composable
fun RemoveMembersScreen(
    groupV2DetailsViewModel: GroupV2DetailsViewModel,
    onValidate: () -> Unit
) {
    MembersScreenContainer(
        members = groupV2DetailsViewModel.groupMembers.value?.map { it.toGroupMember() }.orEmpty(),
        keycloakCertified = groupV2DetailsViewModel.group.value?.keycloakManaged == true,
        nonAdminsReadOnly = groupV2DetailsViewModel.groupType.areNonAdminsReadOnly(),
        groupMemberAction = GroupMemberAction(
            actionPluralRes = R.plurals.label_remove_members,
            actionColor = colorResource(R.color.red),
            onActionClick = { selectedContacts ->
                groupV2DetailsViewModel.startEditingMembers()
                selectedContacts.forEach {
                    groupV2DetailsViewModel.memberRemoved(it.bytesIdentity)
                }
                onValidate()
            })
    )
}

@Composable
fun EditMembersNavigationRow(
    text: String,
    @DrawableRes icon: Int,
    textColor: Color,
    onNavigate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = textColor)
            ) {
                onNavigate()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = colorResource(R.color.lightGrey),
                    shape = CircleShape
                )
                .padding(8.dp),
            tint = colorResource(R.color.greyTint),
            painter = painterResource(icon),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            maxLines = 1,
            style = OlvidTypography.body1.copy(
                color = textColor,
                fontWeight = FontWeight.Medium
            ),
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MembersMainScreen(
    contacts: List<GroupMember>,
    groupAdmin: Boolean,
    keycloakCertified: Boolean,
    nonAdminsReadOnly: Boolean,
    gotoAddMembers: () -> Unit,
    gotoRemoveMembers: () -> Unit
) {
    MembersScreenContainer(
        members = contacts,
        ownGroupMember = AppSingleton.getCurrentIdentityLiveData().value?.let { ownedIdentity->
            GroupMember(
                bytesIdentity = ownedIdentity.bytesOwnedIdentity,
                contact = null,
                jsonIdentityDetails = ownedIdentity.getIdentityDetails(),
                fullSearchDisplayName = "",
                isAdmin = groupAdmin,
                isYou = true,
                pending = false,
                selected = false
            )
        },
        keycloakCertified = keycloakCertified,
        nonAdminsReadOnly = nonAdminsReadOnly,
    ) {
        if (groupAdmin) {
            EditMembersNavigationRow(
                text = stringResource(R.string.button_label_add_members),
                icon = R.drawable.ic_add_member,
                textColor = colorResource(R.color.olvid_gradient_light)
            ) {
                gotoAddMembers()
            }
            EditMembersNavigationRow(
                text = stringResource(R.string.button_label_remove_members),
                icon = R.drawable.ic_remove_member,
                textColor = colorResource(R.color.red)
            ) {
                gotoRemoveMembers()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}