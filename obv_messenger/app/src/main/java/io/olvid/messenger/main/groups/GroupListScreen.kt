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

package io.olvid.messenger.main.groups

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.databases.dao.Group2Dao.GroupOrGroup2
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import androidx.compose.foundation.layout.plus
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GroupListScreen(
    groupListViewModel: GroupListViewModel,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onGroupClick: (group: GroupOrGroup2) -> Unit,
    groupMenu: GroupMenu,
) {

    val groups by groupListViewModel.groups.observeAsState()
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)

    AppCompatTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            val lazyListState = rememberLazyListState()
            groups?.let { list ->
                if (list.isEmpty().not()) {
                    // administered groups first, then joined groups, like desktop and iOS
                    val (adminGroups, joinedGroups) = remember(list) { list.partition { it.isAdmin() } }
                    var adminExpanded by rememberSaveable { mutableStateOf(true) }
                    var joinedExpanded by rememberSaveable { mutableStateOf(true) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsHorizontalPadding(),
                        state = lazyListState,
                        contentPadding = WindowInsets.safeDrawing
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues(LocalDensity.current)
                                + PaddingValues(bottom = 80.dp + dimensionResource(R.dimen.tab_bar_size)),
                    ) {
                        groupSection(
                            groups = adminGroups,
                            headerKey = "administeredHeader",
                            labelRes = R.string.label_groups_administered,
                            badgeColorRes = color.groupSectionAdmin,
                            expanded = adminExpanded,
                            onToggle = { adminExpanded = !adminExpanded },
                            onGroupClick = onGroupClick,
                            groupMenu = groupMenu,
                        )
                        groupSection(
                            groups = joinedGroups,
                            headerKey = "joinedHeader",
                            labelRes = R.string.label_groups_joined,
                            badgeColorRes = color.groupSectionJoined,
                            expanded = joinedExpanded,
                            onToggle = { joinedExpanded = !joinedExpanded },
                            onGroupClick = onGroupClick,
                            groupMenu = groupMenu,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MainScreenEmptyList(
                            icon = R.drawable.tab_groups,
                            title = R.string.explanation_empty_group_list,
                            subtitle = R.string.explanation_empty_group_list_sub
                        )
                    }
                }
            }

            RefreshingIndicator(refreshing = refreshing, refreshState = refreshState)
        }
    }
}

private fun LazyListScope.groupSection(
    groups: List<GroupOrGroup2>,
    headerKey: String,
    @StringRes labelRes: Int,
    @ColorRes badgeColorRes: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onGroupClick: (group: GroupOrGroup2) -> Unit,
    groupMenu: GroupMenu,
) {
    if (groups.isEmpty()) {
        return
    }
    item(key = headerKey) {
        GroupSectionHeader(
            modifier = Modifier.animateItem(),
            label = stringResource(id = labelRes),
            badgeColor = colorResource(id = badgeColorRes),
            expanded = expanded,
            onToggle = onToggle,
        )
    }
    if (expanded) {
        itemsIndexed(items = groups, key = { _, group -> group.listKey() }) { index, group ->
            Box(modifier = Modifier.animateItem()) {
                GroupListItem(
                    modifier = Modifier
                        .background(colorResource(id = color.almostWhite))
                        .cutoutHorizontalPadding(),
                    group = group,
                    title = group.getAnnotatedName(LocalContext.current),
                    body = group.getAnnotatedMembers(LocalContext.current),
                    onClick = { onGroupClick(group) },
                    publishedDetails = group.showPublishedDetails(),
                    publishedDetailsNotification = group.showPublishedDetailsNotification(),
                    groupMenu = groupMenu,
                )
                if (index != 0) {
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
    }
}

// section header per Figma "Group creation" node 909-1745: colored 24dp circle with a white
// group glyph, grey semi-bold label, collapse chevron
@Composable
private fun GroupSectionHeader(
    label: String,
    badgeColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(badgeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                modifier = Modifier.size(15.dp),
                painter = painterResource(id = R.drawable.ic_group),
                colorFilter = ColorFilter.tint(Color.White),
                contentDescription = null,
            )
        }
        Spacer(modifier = Modifier.requiredWidth(12.dp))
        Text(
            text = label,
            style = OlvidTypography.body1.copy(fontWeight = FontWeight.SemiBold),
            color = colorResource(id = color.groupSectionHeader),
        )
        Spacer(modifier = Modifier.requiredWidth(8.dp))
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 0f else -90f,
            label = "chevronRotation"
        )
        Image(
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation),
            painter = painterResource(id = R.drawable.ic_chevron_down),
            colorFilter = ColorFilter.tint(colorResource(id = color.groupSectionHeader)),
            contentDescription = null,
        )
    }
}
