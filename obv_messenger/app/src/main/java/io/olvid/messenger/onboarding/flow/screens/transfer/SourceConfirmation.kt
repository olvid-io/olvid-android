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

package io.olvid.messenger.onboarding.flow.screens.transfer

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.onboarding.flow.Device
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.settings.SettingsActivity

fun NavGraphBuilder.sourceConfirmation(
    onboardingFlowViewModel: OnboardingFlowViewModel,
    onFinalize: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    ownedIdentity: OwnedIdentity?
) {
    composable(
        OnboardingRoutes.TRANSFER_SOURCE_CONFIRMATION,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        var clicked by remember { mutableStateOf(false) }
        val dbDevices = AppSingleton.getBytesCurrentIdentity()?.let {
            AppDatabase.getInstance().ownedDeviceDao().getAllSorted(it)
        }?.observeAsState()

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = R.string.onboarding_transfer_source_confirmation_title),
            ),
            onBack = onBack,
            onClose = onClose
        ) {
            Column (
                modifier = Modifier
                    .widthIn(max = 350.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringResource(id = R.string.onboarding_transfer_source_confirmation_activating_profile),
                    style = OlvidTypography.h3.copy(
                        color = colorResource(id = R.color.almostBlack),
                        textAlign = TextAlign.Center
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(
                            width = 1.dp,
                            brush = SolidColor(value = Color(0xFF8B8D97)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InitialView(
                        modifier = Modifier
                            .padding(
                                horizontal = 8.dp,
                                vertical = 4.dp,
                            )
                            .requiredSize(40.dp),
                        initialViewSetup = { initialView ->
                            if (ownedIdentity != null) {
                                initialView.setOwnedIdentity(ownedIdentity)
                            } else {
                                initialView.setUnknown()
                            }
                        },
                    )

                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .padding(start = 8.dp)
                            .weight(1f)
                    ) {
                        val line1: String
                        val line2: String?
                        if (ownedIdentity != null) {
                            val identityDetails = ownedIdentity.getIdentityDetails()
                            val customDisplayName = ownedIdentity.customDisplayName
                            if (customDisplayName == null) {
                                line1 = identityDetails?.formatFirstAndLastName(SettingsActivity.contactDisplayNameFormat, SettingsActivity.uppercaseLastName) ?: ownedIdentity.displayName
                                line2 = identityDetails?.formatPositionAndCompany(SettingsActivity.contactDisplayNameFormat)
                            } else {
                                line1 = customDisplayName
                                line2 = identityDetails?.formatDisplayName(
                                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                    SettingsActivity.uppercaseLastName
                                ) ?: ownedIdentity.displayName
                            }
                        } else {
                            line1 = ""
                            line2 = null
                        }

                        Text(
                            text = line1,
                            color = colorResource(id = R.color.almostBlack),
                            style = OlvidTypography.h3,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        line2?.let {
                            Text(
                                text = it,
                                color = colorResource(id = R.color.greyTint),
                                style = OlvidTypography.body2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Text(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .padding(horizontal = 8.dp),
                    text = stringResource(id = R.string.onboarding_transfer_source_confirmation_on_device),
                    style = OlvidTypography.h3.copy(
                        color = colorResource(id = R.color.almostBlack),
                        textAlign = TextAlign.Center
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(
                            width = 1.dp,
                            brush = SolidColor(value = Color(0xFF8B8D97)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        modifier = Modifier.padding(12.dp),
                        painter = painterResource(id = R.drawable.ic_device),
                        contentDescription = "device"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = onboardingFlowViewModel.deviceName,
                            style = OlvidTypography.h3.copy(
                                color = colorResource(id = R.color.almostBlack),
                            ),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(id = R.string.text_new_device),
                            style = OlvidTypography.body2.copy(
                                color = Color(0xFF8B8D97),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (onboardingFlowViewModel.transferMultiDevice.not()) {
                    onboardingFlowViewModel.transferSelectedDevice.let { device ->
                        if (device?.uid != null) {
                            Text(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .padding(horizontal = 8.dp),
                                text = stringResource(id = R.string.onboarding_transfer_source_confirmation_other_device_remain_active),
                                style = OlvidTypography.h3.copy(
                                    color = colorResource(id = R.color.almostBlack),
                                    textAlign = TextAlign.Center
                                )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                        width = 1.dp,
                                        brush = SolidColor(value = Color(0xFF8B8D97)),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    modifier = Modifier.padding(12.dp),
                                    painter = painterResource(id = R.drawable.ic_device),
                                    contentDescription = "device"
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = OlvidTypography.h3.copy(
                                            color = colorResource(id = R.color.almostBlack),
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val deviceStatus =
                                        if (device.uid.contentEquals(dbDevices?.value?.first { it.currentDevice }?.bytesDeviceUid))
                                            stringResource(id = R.string.text_this_device)
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
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    device.expirationTimestamp?.let {
                                        Row {
                                            Image(painter = painterResource(id = R.drawable.ic_device_expiration), contentDescription = "")
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
                        } else {
                            Text(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .padding(horizontal = 8.dp),
                                text = stringResource(id = R.string.onboarding_transfer_source_confirmation_new_device_remain_active),
                                style = OlvidTypography.h3.copy(
                                    color = colorResource(id = R.color.almostBlack),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }

                if (onboardingFlowViewModel.transferRestricted) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Image(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(20.dp),
                            painter = painterResource(id = R.drawable.ic_question_mark_white),
                            colorFilter = ColorFilter.tint(colorResource(R.color.greyTint)),
                            contentDescription = ""
                        )
                        Text(
                            modifier = Modifier.weight(1f, true),
                            style = OlvidTypography.body2.copy(
                                color = colorResource(R.color.greyTint)
                            ),
                            text = AnnotatedString(stringResource(R.string.explanation_transfer_restriction_authentication_needed)).formatMarkdown()
                        )
                    }
                }
            }

            OutlinedButton(
                modifier = Modifier.padding(top = 24.dp),
                onClick = {
                    if (clicked.not()) {
                        clicked = true
                        onFinalize.invoke()
                    }
                },
                border = null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.olvid_gradient_light),
                    contentColor = colorResource(R.color.alwaysWhite),
                ),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                AnimatedVisibility(visible = clicked) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 3.dp,
                        color = colorResource(id = R.color.alwaysWhite)
                    )
                }
                AnimatedVisibility(visible = clicked.not()) {
                    Text(
                        text = stringResource(id = R.string.button_label_activate_profile_on_xxx, onboardingFlowViewModel.deviceName),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun Preview() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = OnboardingRoutes.TRANSFER_SOURCE_CONFIRMATION
    ) {
        sourceConfirmation(
            onboardingFlowViewModel = OnboardingFlowViewModel().apply {
                this.updateDeviceName("My Phone")
                this.updateTransferSelectedDevice(
                    Device(
                        name = "Selected device",
                        uid = ByteArray(0)
                    )
                )
            },
            onFinalize = {},
            onBack = {},
            onClose = {},
            ownedIdentity = null
        )
    }
}