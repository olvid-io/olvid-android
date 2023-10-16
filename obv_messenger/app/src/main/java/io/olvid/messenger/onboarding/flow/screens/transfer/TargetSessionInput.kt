/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.R
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.flow.BoxedCharTextField
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingExplanationSteps
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

@OptIn(ExperimentalComposeUiApi::class)
fun NavGraphBuilder.targetSessionInput(
    onboardingFlowViewModel: OnboardingFlowViewModel,
    onDeviceSessionValidated: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.TRANSFER_TARGET_SESSION_INPUT,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = string.onboarding_transfer_target_session_title),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_validate)),
                        type = BUTTON,
                        enabled = onboardingFlowViewModel.sessionNumber.length == 8 && onboardingFlowViewModel.sessionNumber.isDigitsOnly() && onboardingFlowViewModel.validationInProgress.not()
                    ) {
                        keyboardController?.hide()
                        onboardingFlowViewModel.updateValidationInProgress(true)
                        onDeviceSessionValidated.invoke()
                    }
                )
            ),
            onClose = onClose
        ) {
            OnboardingExplanationSteps(steps = stringResource(id = string.onboarding_transfer_target_session_explanation).split('\n'))

            Spacer(modifier = Modifier.height(24.dp))

            BoxedCharTextField(
                text = onboardingFlowViewModel.sessionNumber,
                enabled = onboardingFlowViewModel.validationInProgress.not(),
                error = onboardingFlowViewModel.validationError,
                shimmer = onboardingFlowViewModel.validationInProgress,
                onTextChange = { text ->
                    onboardingFlowViewModel.updateSessionNumber(
                        text.take(8)
                    )
                })
            AnimatedVisibility(visible = onboardingFlowViewModel.validationError) {
                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = stringResource(id = R.string.onboarding_transfer_code_error),
                    color = Color(0xFFE2594E)
                )
            }
        }
    }
}