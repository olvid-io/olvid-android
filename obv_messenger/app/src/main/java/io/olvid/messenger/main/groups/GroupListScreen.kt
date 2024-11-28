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

package io.olvid.messenger.main.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.databases.dao.Group2Dao.GroupOrGroup2
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GroupListScreen(
    groupListViewModel: GroupListViewModel,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onNewGroupClick: () -> Unit,
    onGroupClick: (group: GroupOrGroup2) -> Unit,
    groupMenu: GroupMenu,
) {

    val groups by groupListViewModel.groups.observeAsState()
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)

    AppCompatTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .pullRefresh(refreshState)
        ) {
            val lazyListState = rememberLazyListState()
            groups?.let { list ->
                if (list.isEmpty().not()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item {
                            NewGroupButton(onNewGroupClick)
                        }
                        items(items = list) { group ->
                            Box {
                                GroupListItem(
                                    group = group,
                                    title = group.getAnnotatedName(LocalContext.current),
                                    body = group.getAnnotatedMembers(LocalContext.current),
                                    onClick = { onGroupClick(group) },
                                    publishedDetails = group.showPublishedDetails(),
                                    publishedDetailsNotification = group.showPublishedDetailsNotification(),
                                    groupMenu = groupMenu,
                                )
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 68.dp, end = 12.dp)
                                        .requiredHeight(1.dp)
                                        .align(Alignment.TopStart)
                                        .background(
                                            color = colorResource(id = color.lightGrey)
                                        )
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            MainScreenEmptyList(
                                icon = R.drawable.tab_groups,
                                title = R.string.explanation_empty_group_list,
                                subtitle = R.string.explanation_empty_group_list_sub
                            )
                        }
                        NewGroupButton(onNewGroupClick)
                    }
                }
            }

            RefreshingIndicator(refreshing = refreshing, refreshState = refreshState)
        }
    }
}

@Composable
private fun NewGroupButton(onNewGroupClick: () -> Unit) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(id = color.almostWhite))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        colors = buttonColors(
            backgroundColor = colorResource(id = color.olvid_gradient_light),
            contentColor = Color.White
        ),
        onClick = onNewGroupClick,
    ) {
        Icon(
            modifier = Modifier.requiredSize(24.dp),
            painter = painterResource(id = drawable.ic_group_add),
            contentDescription = ""
        )
        Text(
            modifier = Modifier
                .weight(1f),
            text = stringResource(id = string.button_label_new_group).uppercase(),
            textAlign = TextAlign.Center
        )
    }
}