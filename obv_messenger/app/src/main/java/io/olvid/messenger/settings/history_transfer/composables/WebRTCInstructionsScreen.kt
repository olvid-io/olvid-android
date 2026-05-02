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

package io.olvid.messenger.settings.history_transfer.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.history_transfer.HistoryTransferRoutes


fun NavGraphBuilder.webRTCInstructionsScreen(
    onImportBackPressed: () -> Unit,
    onExportProceedPressed: () -> Unit,
    importMode: State<Boolean>,
    selectedOwnedDevice: State<OwnedDevice?>, // only used in export mode
) {
    composable(
        HistoryTransferRoutes.WEBRTC_INSTRUCTIONS_SCREEN,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.lightGrey))
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
                    .padding(all = 16.dp),
                verticalArrangement = spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier
                        .size(80.dp)
                        .background(colorResource(R.color.olvid_gradient_light), CircleShape)
                        .padding(12.dp),
                    painter = painterResource(R.drawable.ic_wifi),
                    contentDescription = null,
                    tint = colorResource(R.color.almostWhite),
                )

                Text(
                    text = stringResource(R.string.history_transfer_webrtc_instructions_title),
                    style = OlvidTypography.h1.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
                    .padding(all = 16.dp),
                verticalArrangement = spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (importMode.value) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = spacedBy(16.dp),
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.green))
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_power),
                            tint = colorResource(R.color.almostWhite),
                            contentDescription = null
                        )

                        Text(
                            text = stringResource(R.string.history_transfer_instructions_turn_on_other_import),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.almostBlack),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 1.dp,
                        color = colorResource(R.color.mediumGrey)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = spacedBy(16.dp),
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.olvid_gradient_light))
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_wifi),
                            tint = colorResource(R.color.almostWhite),
                            contentDescription = null
                        )

                        Text(
                            text = stringResource(R.string.history_transfer_instructions_same_wifi_import),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.almostBlack),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 1.dp,
                        color = colorResource(R.color.mediumGrey)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = spacedBy(16.dp),
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.golden))
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_finger),
                            tint = colorResource(R.color.almostWhite),
                            contentDescription = null
                        )

                        Text(
                            text = stringResource(R.string.history_transfer_instructions_start_export),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.almostBlack),
                        )
                    }

                    OlvidActionButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        text = stringResource(R.string.button_label_go_back),
                        onClick = onImportBackPressed
                    )
                } else {
                    val deviceName = selectedOwnedDevice.value?.displayName ?: stringResource(R.string.label_your_other_device)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = spacedBy(16.dp),
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.green))
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_power),
                            tint = colorResource(R.color.almostWhite),
                            contentDescription = null
                        )

                        Text(
                            text = stringResource(R.string.history_transfer_instructions_turn_on_other_export, deviceName).formatMarkdownToAnnotatedString(),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.almostBlack),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 1.dp,
                        color = colorResource(R.color.mediumGrey)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = spacedBy(16.dp),
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.olvid_gradient_light))
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_wifi),
                            tint = colorResource(R.color.almostWhite),
                            contentDescription = null
                        )

                        Text(
                            text = stringResource(R.string.history_transfer_instructions_same_wifi_export, deviceName).formatMarkdownToAnnotatedString(),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.almostBlack),
                        )
                    }

                    OlvidActionButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        text = stringResource(R.string.button_label_proceed),
                        onClick = onExportProceedPressed
                    )
                }
            }
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun WebRTCInstructionsScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = HistoryTransferRoutes.WEBRTC_INSTRUCTIONS_SCREEN,
    ) {
        webRTCInstructionsScreen(
            onImportBackPressed = {},
            onExportProceedPressed = {},
            importMode = mutableStateOf(false),
            selectedOwnedDevice = mutableStateOf(
                OwnedDevice(
                    ByteArray(0),
                    ByteArray(0),
                    "iPhone 17",
                    false,
                    true,
                    true,
                    true,
                    1775021390001,
                    null
                )
            ),
        )
    }
}