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

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.graphics.createBitmap
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DeviceBackupProfile
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.onboarding.flow.BackupKeyCheckState
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.settings.composables.BackupKeyTextField
import kotlin.Float


fun NavGraphBuilder.backupV2SelectProfile(
    profileBackups: MutableState<List<DeviceBackupProfile>>,
    onProfileSelected: (DeviceBackupProfile) -> Unit,
    onManuallyEnterKey: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_V2_SELECT_PROFILE,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        val context = LocalContext.current

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = R.string.onboarding_backup_v2_select_profile_title),
            ),
            footer = {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = buildAnnotatedString {
                        append(stringResource(id = R.string.label_manage_backups_for_another_key))
                        append(" ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "",
                                styles = TextLinkStyles(SpanStyle(color = colorResource(id = R.color.blueOrWhite))),
                                linkInteractionListener = {
                                    onManuallyEnterKey.invoke()
                                }
                            ),
                        ) {
                            append(stringResource(id = R.string.label_manage_backups_for_another_key_blue_part))
                        }
                    },
                    textAlign = TextAlign.Center,
                    color = colorResource(id = R.color.greyTint),
                    style = OlvidTypography.body2,
                )
            },
            onBack = onBack,
            onClose = onClose,
        ) {
            val backups = profileBackups.value
            Text(
                text = pluralStringResource(R.plurals.label_profiles_found, backups.size, backups.size),
                style = OlvidTypography.body1,
                color = colorResource(R.color.greyTint),
            )
            backups.forEach { deviceBackupProfile ->
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorResource(R.color.lightGrey))
                        .then(
                            if (deviceBackupProfile.profileAlreadyPresent) {
                                Modifier
                            } else {
                                Modifier.clickable {
                                    onProfileSelected.invoke(deviceBackupProfile)
                                }
                            }
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        ),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (deviceBackupProfile.profileAlreadyPresent)
                                        colorResource(R.color.mediumGrey)
                                    else
                                        Color(
                                            InitialView.getLightColor(
                                                context = context,
                                                bytes = deviceBackupProfile.bytesProfileIdentity
                                            )
                                        )
                                ),
                            text = StringUtils.getInitial(
                                deviceBackupProfile.nickName
                                    ?: deviceBackupProfile.identityDetails.formatFirstAndLastName(
                                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                                        false
                                    )
                            ),
                            color =
                                if (deviceBackupProfile.profileAlreadyPresent)
                                    colorResource(R.color.lightGrey)
                                else
                                    Color(
                                        InitialView.getDarkColor(
                                            context = context,
                                            bytes = deviceBackupProfile.bytesProfileIdentity
                                        )
                                    ),
                            textAlign = TextAlign.Center,
                            fontSize = constantSp(26),
                            fontWeight = FontWeight.Medium,
                            lineHeight = constantSp(40),
                        )

                        if (deviceBackupProfile.photo != null) {
                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                imageLoader = App.imageLoader,
                                model = deviceBackupProfile.photo,
                                contentDescription = null,
                                colorFilter = if (deviceBackupProfile.profileAlreadyPresent) {
                                    val back = colorResource(R.color.lightGrey).toArgb()
                                    ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                                        .166f, .166f, .166f, 0f, ((back shr 17) and 0x7f).toFloat(),
                                        .166f, .166f, .166f, 0f, ((back shr 9) and 0x7f).toFloat(),
                                        .166f, .166f, .166f, 0f, ((back shr 1) and 0x7f).toFloat(),
                                        .0f, 0f, 0f, 0f, 255f)))
                                } else {
                                    null
                                },
                            )
                        }

                        if (deviceBackupProfile.keycloakManaged) {
                            Image(
                                modifier = Modifier
                                    .size(13.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(4.dp, (-3).dp),
                                painter = painterResource(R.drawable.ic_keycloak_certified),
                                contentDescription = null,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f, true)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.SpaceAround,
                    ) {
                        Text(
                            text = deviceBackupProfile.nickName
                                ?: deviceBackupProfile.identityDetails.formatFirstAndLastName(
                                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                                    false
                                ),
                            style = OlvidTypography.body1,
                            color =
                                if (deviceBackupProfile.profileAlreadyPresent)
                                    colorResource(R.color.greyTint)
                                else
                                    colorResource(R.color.almostBlack),
                            maxLines = 1,
                        )
                        (if (deviceBackupProfile.nickName == null)
                            deviceBackupProfile.identityDetails.formatPositionAndCompany(
                                JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY
                            )
                        else
                            deviceBackupProfile.identityDetails.formatDisplayName(
                                JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                false
                            )
                                )?.let {
                                Text(
                                    text = it,
                                    style = OlvidTypography.body2,
                                    color =
                                        if (deviceBackupProfile.profileAlreadyPresent)
                                            colorResource(R.color.mediumGrey)
                                        else
                                            colorResource(R.color.greyTint),
                                    maxLines = 1,
                                )
                            }
                    }
                    if (deviceBackupProfile.profileAlreadyPresent) {
                        Text(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(CircleShape)
                                .background(colorResource(R.color.greyTint))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            text = stringResource(R.string.label_already_active),
                            fontWeight = FontWeight.Normal,
                            lineHeight = constantSp(10),
                            fontSize = constantSp(10),
                            color = colorResource(R.color.alwaysWhite),
                            maxLines = 1,
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.pref_widget_chevron_right),
                            colorFilter = ColorFilter.tint(colorResource(R.color.almostBlack)),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun SelectProfilePreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.BACKUP_V2_SELECT_PROFILE,
    ) {
        backupV2SelectProfile(mutableStateOf(listOf(
            DeviceBackupProfile(
                bytesProfileIdentity = ByteArray(5),
                nickName = null,
                identityDetails = JsonIdentityDetails("Lola", null, null, null),
                keycloakManaged = true,
                photo = null,
                profileAlreadyPresent = false,
                profileBackupSeed = "1234 4567 8910 1234 1234 4567 8910 1234"
            ),
            DeviceBackupProfile(
                bytesProfileIdentity = ByteArray(7),
                nickName = null,
                identityDetails = JsonIdentityDetails("Ariana", "de Palma", "Figma", "Lead Designer"),
                keycloakManaged = false,
                photo = null,
                profileAlreadyPresent = true,
                profileBackupSeed = "1234 4567 8910 1234 1234 4567 8910 1234"
            )
        )), {}, {}, {}, {})
    }
}