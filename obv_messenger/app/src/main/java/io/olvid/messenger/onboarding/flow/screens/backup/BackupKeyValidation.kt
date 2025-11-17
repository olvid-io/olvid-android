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

package io.olvid.messenger.onboarding.flow.screens.backup

import android.graphics.Typeface
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.R
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.backupKeyValidation(
    onboardingFlowViewModel: OnboardingFlowViewModel,
    onBackupKeyValidation: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_KEY_VALIDATION,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val focusRequester = remember { FocusRequester() }
        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = R.string.activity_title_enter_backup_key),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = R.string.button_label_restore_this_backup)),
                        type = BUTTON,
                        onClick = onBackupKeyValidation
                    ),
                )
            ),
            onBack = onBack,
            onClose = onClose
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .focusRequester(focusRequester),
                value = onboardingFlowViewModel.backupSeed ?: "",
                minLines = 2,
                label = {
                    Text(text = stringResource(id = R.string.label_backup_key))
                },
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.hint_backup_key),
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily(Typeface.MONOSPACE)
                    )
                },
                onValueChange = onboardingFlowViewModel::updateBackupSeed,
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily(Typeface.MONOSPACE)
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = colorResource(id = R.color.olvid_gradient_contrasted),
                    focusedBorderColor = colorResource(id = R.color.olvid_gradient_contrasted),
                    cursorColor = colorResource(id = R.color.olvid_gradient_contrasted)
                )
            )
        }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}