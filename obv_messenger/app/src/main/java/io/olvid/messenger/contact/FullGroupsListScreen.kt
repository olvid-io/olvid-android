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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.main.contacts.ContactListItem
import io.olvid.messenger.main.contacts.highlight
import io.olvid.messenger.main.discussions.getAnnotatedTitle

@Composable
fun FullGroupsListScreen(
    contactDetailsViewModel: ContactDetailsViewModel,
) {
    val context = LocalContext.current
    val groupsLiveData = contactDetailsViewModel.groupDiscussions?.observeAsState()
    val fullGroupsListViewModel = viewModel<FullGroupsListViewModel>()

    LaunchedEffect(groupsLiveData?.value) {
        fullGroupsListViewModel.setGroups(groupsLiveData?.value.orEmpty())
    }

    BackHandler(enabled = fullGroupsListViewModel.currentFilter != null) {
        fullGroupsListViewModel.setSearchFilter(null)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SearchBar(
            modifier = Modifier.padding(8.dp),
            searchText = fullGroupsListViewModel.currentFilter.orEmpty(),
            placeholderText = stringResource(R.string.hint_search_group_name),
            onSearchTextChanged = { fullGroupsListViewModel.setSearchFilter(it) },
            onClearClick = { fullGroupsListViewModel.setSearchFilter(null) }
        )
        fullGroupsListViewModel.filteredGroups.takeIf { it.isNotEmpty() }
            ?.let { filteredGroups ->
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        .asPaddingValues() + PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(
                        items = filteredGroups,
                        key = { _, group -> group.discussion.id }) { index, group ->
                        val isFirst = index == 0
                        val isLast = index == filteredGroups.lastIndex
                        val shape = when {
                            isFirst && isLast -> RoundedCornerShape(10.dp)
                            isFirst -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                            isLast -> RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                            else -> RectangleShape
                        }
                        ContactListItem(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    color = colorResource(R.color.lighterGrey),
                                    shape = shape
                                )
                                .clip(shape),
                            padding = PaddingValues(4.dp),
                            title = group.discussion.getAnnotatedTitle(context).highlight(
                                spanStyle = SpanStyle(
                                    background = colorResource(id = R.color.searchHighlightColor),
                                    color = colorResource(id = R.color.black)
                                ),
                                patterns = fullGroupsListViewModel.filterPatterns
                            ),
                            body = group.groupMemberNames?.let {
                                AnnotatedString(it).highlight(
                                    spanStyle = SpanStyle(
                                        background = colorResource(id = R.color.searchHighlightColor),
                                        color = colorResource(id = R.color.black)
                                    ),
                                    patterns = fullGroupsListViewModel.filterPatterns
                                )
                            },
                            onClick = {
                                App.openGroupV2DiscussionActivity(
                                    context,
                                    group.discussion.bytesOwnedIdentity,
                                    group.discussion.bytesDiscussionIdentifier
                                )
                            },
                            initialViewSetup = { initialView ->
                                initialView.setDiscussion(group.discussion)
                            }
                        )
                    }
                }
            } ?: run {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        color = colorResource(R.color.lighterGrey),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 20.dp),
                text = stringResource(R.string.explanation_no_group_match_filter),
                textAlign = TextAlign.Center
            )
        }
    }
}
