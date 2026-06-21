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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.history_transfer.HistoryTransferRoutes


fun NavGraphBuilder.pickTargetDeviceScreen(
    onBackPressed: () -> Unit,
    onDeviceSelected: (OwnedDevice) -> Unit,
    selectedOwnedIdentity: State<OwnedIdentity?>,
    deviceListLiveData: LiveData<List<OwnedDevice>>,
) {
    composable(
        HistoryTransferRoutes.PICK_TARGET_DEVICE_SCREEN,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) },
    ) {
        val context = LocalContext.current
        val deviceList = deviceListLiveData.observeAsState()

        val displayName = remember {
            val details = selectedOwnedIdentity.value?.getIdentityDetails()

            return@remember selectedOwnedIdentity.value?.customDisplayName
                ?: details?.formatFirstAndLastName(
                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                    false
                )
                ?: selectedOwnedIdentity.value?.displayName
                ?: ""
        }

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
                    text = stringResource(R.string.history_transfer_pick_target_device_title),
                    style = OlvidTypography.h1.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(R.string.history_transfer_pick_target_device_text, displayName).formatMarkdownToAnnotatedString(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            deviceList.value?.let { deviceList ->
                deviceList.filter { it.currentDevice.not() }.takeIf { it.isNotEmpty() }?.forEach { ownedDevice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorResource(R.color.almostWhite))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                            ) {
                                onDeviceSelected.invoke(ownedDevice)
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            ),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.lightGrey))
                                .padding(all = 10.dp),
                            painter = painterResource(R.drawable.ic_device),
                            tint = colorResource(R.color.almostBlack),
                            contentDescription = null,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f, true)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceAround,
                        ) {
                            Text(
                                text = ownedDevice.getDisplayNameOrDeviceHexName(
                                    context
                                ),
                                style = OlvidTypography.body1,
                                color = colorResource(R.color.almostBlack),
                                maxLines = 2,
                            )
                            ownedDevice.lastRegistrationTimestamp?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.text_last_online,
                                        StringUtils.getDateString(
                                            context,
                                            it
                                        )
                                    ),
                                    style = OlvidTypography.body2,
                                    color = colorResource(R.color.greyTint),
                                    maxLines = 2,
                                )
                            }
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            tint = colorResource(R.color.almostBlack),
                            contentDescription = null,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                } ?: run {
                    // state contains only the currentDevice --> no other device available
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorResource(R.color.almostWhite))
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                modifier = Modifier.size(40.dp),
                                painter = painterResource(R.drawable.ic_question_mark),
                                tint = colorResource(R.color.orange),
                                contentDescription = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                modifier = Modifier.weight(1f, true),
                                text = stringResource(R.string.history_transfer_no_other_device_explanation, displayName).formatMarkdownToAnnotatedString(),
                                style = OlvidTypography.body1,
                                color = colorResource(R.color.almostBlack),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        OlvidActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.button_label_go_back),
                            large = true,
                            onClick = onBackPressed,
                        )
                    }
                }
            } ?: run {
                // state is null, device list not loaded yet
                OlvidCircularProgress()
            }
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun PickTargetDeviceScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = HistoryTransferRoutes.PICK_TARGET_DEVICE_SCREEN,
    ) {
        pickTargetDeviceScreen(
            onBackPressed = {},
            onDeviceSelected = {},
            selectedOwnedIdentity = mutableStateOf(
                OwnedIdentity(
                    ByteArray(2),
                    "Lisa Martin",
                    null,
                    0,
                    0,
                    null,
                    0,
                    null,
                    true,
                    true,
                    "Lisa 💗",
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    false,
                    true,
                    true,
                    true,
                ),
            ),
            deviceListLiveData = MutableLiveData(
                listOf(
                    OwnedDevice(
                        ByteArray(0),
                        ByteArray(0),
                        "iPhone 17",
                        true,
                        true,
                        true,
                        true,
                        null,
                        null
                    ),

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
                    ),

                    OwnedDevice(
                        ByteArray(0),
                        ByteArray(0),
                        "Samsung galaxy S17",
                        false,
                        true,
                        true,
                        true,
                        1735021390001,
                        null
                    )
                )
            ),
        )
    }
}