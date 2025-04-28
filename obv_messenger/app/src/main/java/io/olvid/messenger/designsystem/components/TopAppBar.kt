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

package io.olvid.messenger.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SimpleTopAppBar(
    title: String,
    selection: List<T> = emptyList(),
    actions: List<Pair<Int, () -> Unit>> = emptyList(),
    onBackPressed: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Crossfade(targetState = selection.isEmpty(), label = "title_transition") { normalMode ->
                if (normalMode) {
                    Text(
                        text = title,
                        maxLines = 1,
                        style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium)
                    )
                } else {
                    Text(
                        text = pluralStringResource(
                            R.plurals.action_mode_title_discussion_list,
                            selection.size,
                            selection.size
                        ),
                        maxLines = 1,
                        style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorResource(id = R.color.olvid_gradient_dark),
            titleContentColor = colorResource(id = R.color.alwaysWhite)
        ),
        navigationIcon = {
            onBackPressed?.let {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        tint = colorResource(id = R.color.alwaysWhite),
                        contentDescription = stringResource(R.string.content_description_back_button)
                    )
                }
            }
        },
        actions = {
            AnimatedVisibility(visible = selection.isNotEmpty()) {
                Row {
                    actions.forEach {
                        IconButton(onClick = it.second) {
                            Icon(
                                painter = painterResource(it.first),
                                tint = Color.White,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    )
}

@Preview
@Composable
private fun SimpleTopAppBarPreview() {
    AppCompatTheme {
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            SimpleTopAppBar<String>("Title") {}
            SimpleTopAppBar(
                selection = listOf(""),
                title = "Title",
                actions = listOf(R.drawable.ic_star_off to {}, R.drawable.ic_action_mark_read to {})
            ) {}
        }
    }
}
