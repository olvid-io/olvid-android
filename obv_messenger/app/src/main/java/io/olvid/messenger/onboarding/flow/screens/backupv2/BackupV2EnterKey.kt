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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.flow.BackupKeyCheckState
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.settings.composables.BackupKeyTextField


fun NavGraphBuilder.backupV2EnterKey(
    newProfile: Boolean,
    backupSeed: MutableState<String?>,
    backupKeyState: MutableState<BackupKeyCheckState>,
    onValidateSeed: (seed: String?) -> Unit,
    onDeviceBackupLoaded: () -> Unit,
    onRestoreLegacyBackup: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_V2_ENTER_KEY,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        val backupSeedError = remember { mutableStateOf(false) }
        val keyboard = LocalSoftwareKeyboardController.current

        var keyLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(backupKeyState.value) {
            if (backupKeyState.value == BackupKeyCheckState.CHECKING) {
                keyLoaded = true
            } else if (backupKeyState.value == BackupKeyCheckState.DEVICE_KEY) {
                if (keyLoaded) {
                    keyLoaded = false
                    onDeviceBackupLoaded.invoke()
                }
            }
        }

        OnboardingScreen(
            step = OnboardingStep(title = stringResource(id = R.string.onboarding_backup_v2_enter_key_title),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(R.string.button_label_validate)),
                        type = OnboardingActionType.BUTTON,
                        enabled = backupKeyState.value != BackupKeyCheckState.CHECKING,
                        onClick = {
                            if (validateBackupSeedLength(backupSeed.value)) {
                                onValidateSeed(backupSeed.value)
                                keyboard?.hide()
                            } else {
                                backupSeedError.value = true
                            }
                        }
                    )
                ),
            ),
            onBack = onBack,
            onClose = onClose
        ) {
            BackupKeyTextField(
                backupSeed = backupSeed,
                backupSeedError = backupSeedError,
                onValidateSeed = {
                    if (validateBackupSeedLength(backupSeed.value)) {
                        onValidateSeed(backupSeed.value)
                        keyboard?.hide()
                    } else {
                        backupSeedError.value = true
                    }
                },
            )
            AnimatedVisibility(
                visible = backupKeyState.value == BackupKeyCheckState.CHECKING
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 5.dp,
                        color = colorResource(id = R.color.olvid_gradient_light)
                    )
                }
            }
            AnimatedVisibility(
                visible = backupKeyState.value == BackupKeyCheckState.ERROR
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        modifier = Modifier.widthIn(max = 400.dp),
                        text = stringResource(R.string.explanation_unable_to_check_backup_key),
                        color = colorResource(R.color.red),
                        style = OlvidTypography.body2,
                    )
                }
            }
            AnimatedVisibility(
                visible = backupKeyState.value == BackupKeyCheckState.UNKNOWN
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    if (newProfile) {
                        Text(
                            modifier = Modifier.widthIn(max = 400.dp),
                            text = AnnotatedString(stringResource(R.string.explanation_no_backup_found_try_legacy_add_profile)).formatMarkdown(),
                            color = colorResource(R.color.red),
                            style = OlvidTypography.body2,
                        )
                    } else {
                        Text(
                            modifier = Modifier.widthIn(max = 400.dp),
                            text = AnnotatedString(stringResource(R.string.explanation_no_backup_found_try_legacy)).formatMarkdown(),
                            color = colorResource(R.color.red),
                            style = OlvidTypography.body2,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            elevation = null,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, colorResource(R.color.olvid_gradient_light)),
                            onClick = onRestoreLegacyBackup,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorResource(id = R.color.blueOrWhite),
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.button_label_restore_legacy_file),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun validateBackupSeedLength(backupSeed: String?) : Boolean {
    backupSeed?.let {
        if (it.replace("[^A-Za-z0-9]".toRegex(), "").length == 32) {
            return true
        }
    }
    return false
}

@Preview
@Composable
fun EnterKeyProfilePreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.BACKUP_V2_ENTER_KEY,
    ) {
        backupV2EnterKey(false, mutableStateOf("ACBD"), mutableStateOf(BackupKeyCheckState.UNKNOWN), {}, {}, {}, {}, {})
    }
}