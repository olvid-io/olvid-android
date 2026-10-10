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

package io.olvid.messenger.contact

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.designsystem.components.CircleCheckBox
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.main.contacts.ContactListItem
import io.olvid.messenger.main.contacts.highlight

@Composable
fun AddContactToGroupsScreen(
    contactDetailsViewModel: ContactDetailsViewModel,
    onDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val addToGroupsViewModel = viewModel<AddToGroupsViewModel>()
    val groups = contactDetailsViewModel.adminGroups?.observeAsState()?.value
    val contact: Contact? by contactDetailsViewModel.contactAndInvitation?.map { it?.contact }
        ?.observeAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(groups) {
        addToGroupsViewModel.setGroups(groups.orEmpty())
    }
    LaunchedEffect(addToGroupsViewModel.currentFilter) {
        if (addToGroupsViewModel.currentFilter == null) {
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = addToGroupsViewModel.currentFilter != null) {
        addToGroupsViewModel.setSearchFilter(null)
    }

    Column {
        SearchBar(
            modifier = Modifier.padding(8.dp),
            searchText = addToGroupsViewModel.currentFilter.orEmpty(),
            placeholderText = stringResource(R.string.hint_search_group_name),
            onSearchTextChanged = { addToGroupsViewModel.setSearchFilter(it) },
            onClearClick = { addToGroupsViewModel.setSearchFilter(null) }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            addToGroupsViewModel.filteredGroups.takeIf { it.isNotEmpty() }?.let { filteredGroups ->
                LazyColumn(
                    contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        .asPaddingValues() + PaddingValues(bottom = 64.dp)
                ) {
                    itemsIndexed(
                        items = filteredGroups,
                        key = { _, group -> group.bytesGroupIdentifier }) { index, group ->
                        ContactListItem(
                            modifier = Modifier
                                .animateItem()
                                .then(
                                    when (index) {
                                        0 -> Modifier.clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (filteredGroups.lastIndex == 0) 16.dp else 0.dp,
                                                bottomEnd = if (filteredGroups.lastIndex == 0) 16.dp else 0.dp,
                                            )
                                        )

                                        filteredGroups.lastIndex -> Modifier.clip(
                                            RoundedCornerShape(
                                                bottomStart = 16.dp,
                                                bottomEnd = 16.dp,
                                            )
                                        )

                                        else -> Modifier
                                    }
                                )
                                .background(colorResource(R.color.lighterGrey)),
                            padding = PaddingValues(4.dp),
                            title = AnnotatedString(group.truncatedCustomName).highlight(
                                SpanStyle(
                                    background = colorResource(id = R.color.searchHighlightColor),
                                    color = colorResource(id = R.color.black)
                                ),
                                addToGroupsViewModel.filterPatterns
                            ),
                            body = group.groupMembersNames.takeIf { it.isNotEmpty() }?.let {
                                AnnotatedString(it).highlight(
                                    SpanStyle(
                                        background = colorResource(id = R.color.searchHighlightColor),
                                        color = colorResource(id = R.color.black)
                                    ),
                                    addToGroupsViewModel.filterPatterns
                                )
                            },
                            onClick = {
                                contactDetailsViewModel.toggleGroupToAdd(group.bytesGroupIdentifier)
                            },
                            avatar = group,
                            endContent = {
                                Spacer(modifier = Modifier.width(4.dp))
                                CircleCheckBox(
                                    checked = BytesKey(group.bytesGroupIdentifier) in contactDetailsViewModel.selectedGroupsToAdd,
                                    onCheckedChange = {
                                        contactDetailsViewModel.toggleGroupToAdd(group.bytesGroupIdentifier)
                                    },
                                    color = colorResource(R.color.olvid_gradient_light)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    text = if (groups.isNullOrEmpty()) {
                        stringResource(
                            R.string.explanation_no_admin_group_to_add_contact,
                            contact?.firstNameOrCustom.orEmpty()
                        )
                    } else {
                        stringResource(R.string.explanation_no_group_match_filter)
                    },
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center
                )
            }

            val selectedCount = contactDetailsViewModel.selectedGroupsToAdd.size
            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = selectedCount > 0,
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
                        text = pluralStringResource(
                            R.plurals.button_label_add_to_groups,
                            selectedCount,
                            selectedCount
                        )
                    ) {
                        contactDetailsViewModel.addContactToSelectedGroups(onFinished = onDone)
                    }
                }
            }
        }
    }
}
