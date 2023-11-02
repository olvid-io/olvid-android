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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.flow.BoxedCharTextField
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingExplanationSteps
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.sourceSession(
    onboardingFlowViewModel: OnboardingFlowViewModel,
    onSasValidated: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.TRANSFER_SOURCE_SESSION,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {

        val keyboardController = LocalSoftwareKeyboardController.current
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            if (onboardingFlowViewModel.dialog == null) {
                AppSingleton.getEngine()
                    .initiateOwnedIdentityTransferProtocolOnSourceDevice(AppSingleton.getBytesCurrentIdentity())
            }
        }
        LaunchedEffect(onboardingFlowViewModel.correctSas) {
            onboardingFlowViewModel.correctSas?.let {
                focusRequester.requestFocus()
            }
        }

        OnboardingScreen(
            step = OnboardingStep(
                title = if (onboardingFlowViewModel.correctSas == null) stringResource(id = string.onboarding_transfer_source_session_show_title)
                else stringResource(id = string.onboarding_transfer_source_session_sas),
                actions = if (onboardingFlowViewModel.correctSas != null) listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_validate)),
                        type = BUTTON,
                        enabled = onboardingFlowViewModel.sas.length == 8 && onboardingFlowViewModel.sas.isDigitsOnly()
                    ) {
                        keyboardController?.hide()
                        if (onboardingFlowViewModel.sas == onboardingFlowViewModel.correctSas) {
                            onSasValidated.invoke()
                        } else {
                            onboardingFlowViewModel.updateValidationError(true)
                        }
                    }
                ) else listOf()
            ),
            onClose = onClose
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = onboardingFlowViewModel.correctSas == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OnboardingExplanationSteps(
                            steps = stringResource(id = string.onboarding_transfer_source_session_show_explanation).split('\n')
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        BoxedCharTextField(
                            text = onboardingFlowViewModel.sessionNumber,
                            shimmer = onboardingFlowViewModel.sessionNumber.isEmpty() || onboardingFlowViewModel.validationInProgress,
                            enabled = false
                        )
                    }
                }
                AnimatedVisibility(visible = onboardingFlowViewModel.correctSas != null) {
                    BoxedCharTextField(modifier = Modifier.focusRequester(focusRequester),
                        text = onboardingFlowViewModel.sas,
                        enabled = onboardingFlowViewModel.sessionNumber.length == 8 && onboardingFlowViewModel.sessionNumber.isDigitsOnly() && onboardingFlowViewModel.validationInProgress.not(),
                        error = onboardingFlowViewModel.validationError,
                        shimmer = onboardingFlowViewModel.validationInProgress,
                        onTextChange = { onboardingFlowViewModel.updateSas(it) }
                    )
                }
                AnimatedVisibility(visible = onboardingFlowViewModel.validationError) {
                    AnimatedVisibility(visible = onboardingFlowViewModel.validationError) {
                        Text(
                            modifier = Modifier.padding(vertical = 8.dp),
                            text = stringResource(id = string.onboarding_transfer_code_error),
                            color = Color(0xFFE2594E)
                        )
                    }
                }
            }
        }
    }
}