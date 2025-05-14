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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import coil.compose.AsyncImage
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DeviceBackupProfile
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.onboarding.flow.BackupRestoreState
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen


fun NavGraphBuilder.backupV2RestoreResult(
    backupRestoreState: MutableState<BackupRestoreState>,
    restoredProfile: MutableState<DeviceBackupProfile?>,
    onOpenProfile: () -> Unit,
    onRestoreOther: () -> Unit,
    onBackAfterFail: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_V2_RESTORE_RESULT,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        val context = LocalContext.current

        OnboardingScreen(
            step = null,
            onClose = onClose
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.lightGrey))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))

                restoredProfile.value?.let { profile ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Color(
                                        InitialView.getLightColor(
                                            context = context,
                                            bytes = profile.bytesProfileIdentity
                                        )
                                    )
                                ),
                            text = StringUtils.getInitial(
                                profile.nickName
                                    ?: profile.identityDetails.formatDisplayName(
                                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                        false
                                    )
                            ),
                            color = Color(
                                InitialView.getDarkColor(
                                    context = context,
                                    bytes = profile.bytesProfileIdentity
                                )
                            ),
                            textAlign = TextAlign.Center,
                            fontSize = constantSp(52),
                            fontWeight = FontWeight.Medium,
                            lineHeight = constantSp(80),
                        )

                        if (profile.photo != null) {
                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                imageLoader = App.imageLoader,
                                model = profile.photo,
                                contentDescription = null,
                            )
                        }

                        if (profile.keycloakManaged) {
                            Image(
                                modifier = Modifier
                                    .size(26.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(8.dp, (-6).dp),
                                painter = painterResource(R.drawable.ic_keycloak_certified),
                                contentDescription = null,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = profile.nickName
                            ?: profile.identityDetails.formatFirstAndLastName(
                                JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                                false
                            ),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.almostBlack),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    (if (profile.nickName == null)
                        profile.identityDetails.formatPositionAndCompany(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY
                        )
                    else
                        profile.identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                            false
                        )
                            )?.let {
                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = it,
                                style = OlvidTypography.body2,
                                color = colorResource(R.color.greyTint),
                                maxLines = 1,
                            )
                        }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier.height(112.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    when(backupRestoreState.value) {
                        BackupRestoreState.RESTORING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(bottom = 24.dp).size(64.dp),
                                strokeWidth = 5.dp,
                                color = colorResource(id = R.color.olvid_gradient_light)
                            )
                        }
                        BackupRestoreState.SUCCESS -> {
                            Column {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    elevation = null,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        contentColor = colorResource(R.color.alwaysWhite),
                                        containerColor = colorResource(R.color.olvid_gradient_light),
                                    ),
                                    onClick = onOpenProfile,
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_label_open_profile),
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    elevation = null,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, colorResource(R.color.greyTint)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = colorResource(R.color.almostBlack),
                                    ),
                                    onClick = onRestoreOther,
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_label_restore_other_profile),
                                    )
                                }
                            }
                        }
                        BackupRestoreState.FAILED -> {
                            Column {
                                Text(
                                    text = stringResource(R.string.explanation_failed_to_restore_backup),
                                    color = colorResource(R.color.red),
                                    style = OlvidTypography.body2,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    elevation = null,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, colorResource(R.color.greyTint)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = colorResource(R.color.almostBlack),
                                    ),
                                    onClick = onBackAfterFail,
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_label_go_back),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RestoreResultPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.BACKUP_V2_RESTORE_RESULT,
    ) {
        backupV2RestoreResult(mutableStateOf(BackupRestoreState.SUCCESS), mutableStateOf(
            DeviceBackupProfile(
                bytesProfileIdentity = ByteArray(5),
                nickName = null,
                identityDetails = JsonIdentityDetails("Lola", null, null, null),
                keycloakManaged = true,
                photo = null,
                profileAlreadyPresent = false,
                profileBackupSeed = "1234 4567 8910 1234 1234 4567 8910 1234"
            ),
        ), {}, {}, {}, {})
    }
}