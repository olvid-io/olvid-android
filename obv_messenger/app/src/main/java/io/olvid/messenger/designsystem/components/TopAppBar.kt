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

package io.olvid.messenger.designsystem.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun <T> SelectionTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    content: (@Composable () -> Unit)? = null,
    selection: List<T> = emptyList(),
    selectedStringResource: Int = R.plurals.action_mode_title_discussion_list,
    selectionActions: List<Pair<Int, () -> Unit>> = emptyList(),
    actions: List<Pair<Int, () -> Unit>> = emptyList(),
    otherActions: List<Pair<Int, () -> Unit>> = emptyList(),
    redItems: List<Int> = emptyList(),
    disabledItems: List<Int> = emptyList(),
    transparent: Boolean = false,
    onBackPressed: (() -> Unit)? = null
) {
    OlvidTopAppBar(
        modifier = modifier,
        transparent = transparent,
        title = {
            Crossfade(
                targetState = selection.isEmpty(),
                label = "title_transition"
            ) { notSelecting ->
                // we add a special selectionSize to avoid a flicker in the CrossFade when exiting selection
                var selectionSize by remember { mutableIntStateOf(1) }
                LaunchedEffect(selection.size) {
                    if (selection.isNotEmpty()) {
                        selectionSize = selection.size
                    }
                }

                if (notSelecting) {
                    if (content != null) {
                        content()
                    } else {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                } else {
                    Text(
                        text = pluralStringResource(
                            selectedStringResource,
                            selectionSize,
                            selectionSize
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        },
        onBackPressed = onBackPressed,
        actions = {
            AnimatedVisibility(visible = selection.isNotEmpty() || selectionActions.isNotEmpty() || otherActions.isNotEmpty()) {
                CompositionLocalProvider(LocalContentColor provides colorResource(id = R.color.almostBlack)) {
                    Row {
                        (if (selection.isEmpty()) actions else selectionActions).forEach {
                            IconButton(onClick = it.second, enabled = it.first !in disabledItems) {
                                Icon(
                                    modifier = Modifier.size(24.dp),
                                    painter = painterResource(it.first),
                                    contentDescription = null
                                )
                            }
                        }
                        if (selection.isEmpty() && otherActions.isNotEmpty()) {
                            var mainMenuOpened by remember {
                                mutableStateOf(false)
                            }
                            IconButton(onClick = {
                                mainMenuOpened = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    tint = colorResource(R.color.almostBlack),
                                    contentDescription = "menu"
                                )
                                OlvidDropdownMenu(
                                    expanded = mainMenuOpened,
                                    onDismissRequest = { mainMenuOpened = false }
                                ) {
                                    otherActions.forEach { menuItem ->
                                        OlvidDropdownMenuItem(
                                            text = stringResource(menuItem.first),
                                            onClick = {
                                                mainMenuOpened = false
                                                menuItem.second()
                                            },
                                            textColor = if (menuItem.first in redItems
                                            )
                                                colorResource(R.color.red)
                                            else
                                                null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OlvidTopAppBar(
    modifier: Modifier = Modifier,
    titleText: String? = null,
    title: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    transparent: Boolean = false,
    onBackPressed: (() -> Unit)? = null
) {
    TopAppBar(
        modifier = modifier.shadow(4.dp),
        expandedHeight = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 48.dp else 56.dp,
        title = {
            titleText?.let {
                Text(
                    text = titleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium)
                )
            } ?: title?.invoke()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorResource(id = if (transparent) R.color.whiteOverlay else R.color.almostWhite),
            titleContentColor = colorResource(id = R.color.almostBlack)
        ),
        navigationIcon = {
            onBackPressed?.let {
                CompositionLocalProvider(LocalContentColor provides colorResource(id = R.color.almostBlack)) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back_white),
                            contentDescription = stringResource(R.string.content_description_back_button)
                        )
                    }
                }
            }
        },
        actions = actions,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    )
}

@Preview
@Composable
private fun SelectionTopAppBarPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        SelectionTopAppBar<String>(title = "Title") {}
        SelectionTopAppBar(
            selection = listOf(""),
            title = "Title",
            selectionActions = listOf(R.drawable.ic_star_off to {}, R.drawable.ic_action_mark_read to {})
        ) {}
    }
}
