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

package io.olvid.messenger.onboarding.flow.screens.transfer

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.ObvDeviceList
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.billing.SubscriptionOfferDialog
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.onboarding.flow.Device
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.activeDeviceSelection(
    activity: Activity,
    onboardingFlowViewModel: OnboardingFlowViewModel,
    onProceed: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.TRANSFER_ACTIVE_DEVICES,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        val dbDevices = AppDatabase.getInstance().ownedDeviceDao()
            .getAllSorted(AppSingleton.getBytesCurrentIdentity()).observeAsState()
        var loading by remember { mutableStateOf(true) }
        var deviceList: ObvDeviceList? by remember { mutableStateOf(null) }
        val context = LocalContext.current
        val googleServicesAvailable = remember { GoogleServicesUtils.googleServicesAvailable(context) }
        var showPurchaseFragment by remember { mutableStateOf(false) }
        var purchaseEngineListener: EngineNotificationListener? by remember { mutableStateOf(null) }

        fun clearDeviceList() {
            loading = true
            onboardingFlowViewModel.updateTransferSelectedDevice(null)
            deviceList = null
        }

        fun refreshDeviceList() {
            deviceList = AppSingleton.getEngine()
                .queryRegisteredOwnedDevicesFromServer(AppSingleton.getBytesCurrentIdentity())
            deviceList?.let {
                onboardingFlowViewModel.updateTransferMultiDevice(it.multiDevice)
            }
            loading = false
        }


        LaunchedEffect(Unit) {
            refreshDeviceList()
        }

        OnboardingScreen(
            scrollable = true,
            step = OnboardingStep(
                title = if (loading) stringResource(id = R.string.onboarding_transfer_devices_loading_title) else if (onboardingFlowViewModel.transferMultiDevice) stringResource(id = R.string.onboarding_transfer_devices_multi_title) else stringResource(
                    id = R.string.onboarding_transfer_devices_title
                ),
                subtitle = if (loading) "" else if (onboardingFlowViewModel.transferMultiDevice) stringResource(
                    id = R.string.onboarding_transfer_devices_multi_subtitle,
                    onboardingFlowViewModel.deviceName
                ) else stringResource(
                    id = R.string.onboarding_transfer_devices_subtitle
                ),
            ),
            onClose = onClose,
            footer = {
                AnimatedVisibility(
                    visible = !loading && googleServicesAvailable && onboardingFlowViewModel.transferMultiDevice.not(),
                    enter = fadeIn(),
                    exit =  fadeOut(),
                ) {
                    ClickableText(
                        modifier = Modifier.padding(16.dp),
                        text = buildAnnotatedString {
                            append(stringResource(id = R.string.onboarding_transfer_multidevice_purchase_question))
                            append(" ")
                            withStyle(SpanStyle(color = colorResource(id = R.color.blueOrWhite))) {
                                append(stringResource(id = R.string.onboarding_transfer_multidevice_purchase_hyperlink))
                            }
                        },
                        style = OlvidTypography.body2.copy(
                            color = Color(0xFF8B8D97),
                            textAlign = TextAlign.Center
                        )
                    ) {
                        showPurchaseFragment = true
                    }
                }
            }
        ) {
            if (loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(128.dp)
                        .align(Alignment.CenterHorizontally),
                    color = colorResource(id = R.color.olvid_gradient_light),
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                )
            } else {
                val devices = arrayListOf<Device>()
                    deviceList?.deviceUidsAndServerInfo?.let { deviceUidsAndServerInfo ->
                        devices.addAll(deviceUidsAndServerInfo.map {
                            Device(
                                name = it.value.displayName,
                                uid = it.key.bytes,
                                lastRegistrationTimestamp = it.value.lastRegistrationTimestamp,
                                expirationTimestamp = it.value.expirationTimestamp
                            )
                        })

                        if (onboardingFlowViewModel.transferMultiDevice.not()) {
                            devices.add(Device(onboardingFlowViewModel.deviceName, null))
                        }
                    }

                devices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .then(if (onboardingFlowViewModel.transferMultiDevice.not()) Modifier.clickable {
                                onboardingFlowViewModel.updateTransferSelectedDevice(device)
                            } else Modifier)
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onboardingFlowViewModel.transferMultiDevice.not()) {
                            RadioButton(
                                selected = onboardingFlowViewModel.transferSelectedDevice != null && onboardingFlowViewModel.transferSelectedDevice?.uid.contentEquals(
                                    device.uid
                                ),
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colorResource(id = R.color.olvid_gradient_contrasted))
                            )
                        }
                        Image(
                            modifier = Modifier.padding(12.dp),
                            painter = painterResource(id = R.drawable.ic_device),
                            contentDescription = "device"
                        )
                        Column(modifier = Modifier.weight(1f, true)) {
                            Text(
                                text = device.name,
                                style = OlvidTypography.body1.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorResource(id = R.color.almostBlack),
                                )
                            )
                            val deviceStatus =
                                if (device.uid.contentEquals(dbDevices.value?.first { it.currentDevice }?.bytesDeviceUid))
                                    stringResource(id = R.string.text_this_device)
                                else if (device.uid == null)
                                    stringResource(id = R.string.text_new_device)
                                else device.lastRegistrationTimestamp?.let {
                                    stringResource(
                                        id = R.string.text_last_online,
                                        StringUtils.getNiceDateString(
                                            LocalContext.current,
                                            it
                                        ).toString()
                                    )
                                }
                            deviceStatus?.let {
                                Text(
                                    text = it,
                                    style = OlvidTypography.body2.copy(
                                        color = Color(0xFF8B8D97),
                                    )
                                )
                            }
                            device.expirationTimestamp?.let {
                                Row {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_device_expiration),
                                        contentDescription = ""
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(
                                            id = R.string.text_deactivates_on,
                                            StringUtils.getPreciseAbsoluteDateString(
                                                LocalContext.current,
                                                it
                                            )
                                        ),
                                        style = OlvidTypography.body2.copy(
                                            color = Color(0xFF8B8D97),
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    modifier = Modifier.padding(top = 16.dp),
                    elevation = null,
                    onClick = {
                        onProceed.invoke()
                    },
                    enabled = onboardingFlowViewModel.transferMultiDevice || onboardingFlowViewModel.transferSelectedDevice != null,
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    Text(
                        text = if (onboardingFlowViewModel.transferMultiDevice)
                            stringResource(
                                id = R.string.button_label_add_device_xxx,
                                onboardingFlowViewModel.deviceName
                            )
                        else
                            stringResource(id = R.string.button_label_proceed),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
            if (showPurchaseFragment) {
                SubscriptionOfferDialog(
                    activity = activity,
                    onDismissCallback = { showPurchaseFragment = false },
                    onPurchaseCallback = {
                        clearDeviceList()

                        // wait for the purchase acknowledged notification
                        purchaseEngineListener = object: SimpleEngineNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS) {
                            override fun callback(userInfo: HashMap<String, Any>?) {
                                AppSingleton.getEngine().removeNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, purchaseEngineListener)
                                purchaseEngineListener = null
                                refreshDeviceList()
                            }
                        }
                        AppSingleton.getEngine().addNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, purchaseEngineListener)
                    })
            }
        }
    }
}
