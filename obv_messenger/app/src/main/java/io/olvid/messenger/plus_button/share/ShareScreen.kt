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

package io.olvid.messenger.plus_button.share

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.components.DarkGradientBackground
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.settings.SettingsActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    ownedIdentity: OwnedIdentity?,
    onDone: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
) {
    Scaffold(
        containerColor = colorResource(R.color.blackDarkOverlay),
        topBar = {
            TopAppBar(
                modifier = Modifier.cutoutHorizontalPadding(),
                title = {
                    Text(
                        text = stringResource(R.string.label_share_profile),
                        style = OlvidTypography.h3.copy(color = Color.White)
                    )
                },
                actions = {
                    IconButton(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .border(
                                1.dp, Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(20.dp)
                            ),
                        onClick = onDone,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = Color.White
                        ),
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.content_description_close_button)
                        )
                    }
                },
                expandedHeight = dimensionResource(R.dimen.tab_bar_size),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
    ) { paddingValues ->
        DarkGradientBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Profile Info
                        InitialView(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, false)
                            .height(160.dp),
                            initialViewSetup = {
                                ownedIdentity?.let { identity -> it.setOwnedIdentity(identity) }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = ownedIdentity?.customDisplayName ?: ownedIdentity?.displayName.orEmpty(),
                            style = OlvidTypography.h2.copy(color = Color.White),
                            textAlign = TextAlign.Center
                        )
                        ownedIdentity
                            ?.getIdentityDetails()
                            ?.formatPositionAndCompany(SettingsActivity.contactDisplayNameFormat)
                            ?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = it,
                                    style = OlvidTypography.body1.copy(color = Color.White.copy(alpha = 0.7f)),
                                    textAlign = TextAlign.Center
                                )
                            }
                    }
                }
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShareActionButton(
                        R.drawable.ic_share,
                        text = stringResource(R.string.menu_action_share),
                        onClick = onShare
                    )
                    ShareActionButton(
                        R.drawable.mime_type_icon_link,
                        text = stringResource(R.string.button_label_copy),
                        onClick = onCopy
                    )
                    ShareActionButton(
                            icon = R.drawable.ic_download,
                        text = stringResource(R.string.button_label_download),
                        onClick = onDownload
                    )
                }
            }
        }
    }
}

@Composable
fun ShareActionButton(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        IconButton(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp)),
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.White
            ),
        ) {
            Icon(
                modifier = Modifier.size(28.dp),
                painter = painterResource(icon),
                contentDescription = text
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = text, color = Color.White.copy(alpha = 0.7f), style = OlvidTypography.body2)
    }
}

@Preview
@Preview(device = "spec:width=800px,height=1280px,dpi=440,orientation=landscape")
@Composable
private fun ShareScreenPreview() {
    // Create a mock OwnedIdentity for previewing
    val mockIdentity = OwnedIdentity(
        ByteArray(0),
        "John Doe",
        null,
        0,
        0,
        null,
        0,
        null,
        false,
        true,
        null,
        null,
        null,
        false,
        true,
        null,
        false,
        false,
        false,
        false

    )
    mockIdentity.displayName = "John Doe"

    ShareScreen(
        ownedIdentity = mockIdentity,
        onDone = {},
        onShare = {},
        onCopy = {},
        onDownload = {}
    )
}