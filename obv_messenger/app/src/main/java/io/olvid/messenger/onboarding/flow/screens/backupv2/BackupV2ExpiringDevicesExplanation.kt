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

package io.olvid.messenger.onboarding.flow.screens.backupv2

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.engine.engine.types.ObvDeviceList
import io.olvid.engine.engine.types.identities.ObvOwnedDevice
import io.olvid.messenger.R
import io.olvid.messenger.billing.SubscriptionOfferDialog
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen


fun NavGraphBuilder.backupV2ExpiringDevicesExplanation(
    firstAndLastName: State<String>,
    nickname: State<String?>,
    devices: State<ObvDeviceList?>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_V2_EXPIRING_DEVICES_EXPLANATION,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        // this never happens as this screen is only loaded when there are expiring devices
        LaunchedEffect(devices.value) {
            if (devices.value == null) {
                onCancel.invoke()
            }
        }

        OnboardingScreen(
            step = null,
            onClose = onClose,
            footer = {
                val context = LocalContext.current
                val googleServicesAvailable =
                    remember { GoogleServicesUtils.googleServicesAvailable(context) }
                var showPurchaseFragment by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(max = 400.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.olvid_gradient_light),
                            contentColor = colorResource(R.color.alwaysWhite),
                        ),
                        onClick = onConfirm,
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_restore_my_profile)
                        )
                    }

                    if (googleServicesAvailable) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = null,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, colorResource(R.color.greyTint)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorResource(R.color.almostBlack),
                            ),
                            onClick = {
                                showPurchaseFragment = true
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.button_label_keep_all_with_olvid_plus)
                            )
                        }
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, colorResource(R.color.greyTint)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorResource(R.color.almostBlack),
                        ),
                        onClick = onCancel,
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_cancel)
                        )
                    }


                    if (showPurchaseFragment) {
                        SubscriptionOfferDialog(
                            activity = LocalActivity.current,
                            onDismissCallback = { showPurchaseFragment = false },
                            onPurchaseCallback = onConfirm)
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier.widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = spacedBy(16.dp),
            ) {
                Image(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(colorResource(R.color.lightGrey))
                        .padding(24.dp)
                        .size(56.dp),
                    painter = painterResource(R.drawable.ic_danger),
                    contentDescription = null,
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.explanation_backup_v2_device_expiration_start))
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(firstAndLastName.value)
                        }
                        nickname.value?.let {
                            withStyle(style = SpanStyle(color = colorResource(R.color.greyTint))) {
                                append(stringResource(R.string.explanation_backup_v2_device_expiration_nickname, it))
                            }
                        }
                        val count = devices.value?.deviceUidsAndServerInfo?.size ?: 1
                        append(pluralStringResource(R.plurals.explanation_backup_v2_device_expiration_end, count))
                    },
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.almostBlack),
                )

                val separator = stringResource(R.string.text_date_time_separator)
                devices.value?.deviceUidsAndServerInfo?.values?.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorResource(R.color.lightGrey))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Image(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.almostWhite))
                                .padding(all = 10.dp),
                            painter = painterResource(R.drawable.ic_device),
                            colorFilter = ColorFilter.tint(colorResource(R.color.almostBlack)),
                            contentDescription = null,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f, true)
                                .heightIn(min = 40.dp)
                                .padding(start = 16.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceAround,
                        ) {
                            Text(
                                text = device.displayName,
                                style = OlvidTypography.body1,
                                color = colorResource(R.color.almostBlack),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )

                            device.expirationTimestamp?.let {
                                Row(
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Image(
                                        modifier = Modifier.size(16.dp),
                                        painter = painterResource(R.drawable.ic_device_expiration),
                                        contentDescription = null,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        modifier = Modifier.weight(1f, true),
                                        text = stringResource(
                                            R.string.text_deactivates_on,
                                            StringUtils.getPreciseAbsoluteDateString(LocalContext.current, it, separator)
                                        ),
                                        style = OlvidTypography.body2,
                                        color = colorResource(R.color.greyTint),
                                    )
                                }
                            }

                            device.lastRegistrationTimestamp?.let {
                                Text(
                                    text = stringResource(
                                        R.string.text_last_online,
                                        StringUtils.getPreciseAbsoluteDateString(LocalContext.current, it, separator)
                                    ),
                                    style = OlvidTypography.body2,
                                    color = colorResource(R.color.greyTint),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.explanation_backup_v2_choose_device_after_restore),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint),
                )
            }
        }
    }
}

@Preview
@Composable
fun ExpiringDevicesExplanationPreview() {
    val map = HashMap<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo>()
    map.put(ObvBytesKey(ByteArray(2)), ObvOwnedDevice.ServerDeviceInfo("iPhone", 1747927594000, 1742570794000))
    map.put(ObvBytesKey(ByteArray(3)), ObvOwnedDevice.ServerDeviceInfo("mac", null, null))

    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.BACKUP_V2_EXPIRING_DEVICES_EXPLANATION,
    ) {
        backupV2ExpiringDevicesExplanation(
            mutableStateOf("Alice Johnson"),
            mutableStateOf("Personal"),
            mutableStateOf(ObvDeviceList(
                false,
                map
            )),
            {}, {}, {})
    }
}