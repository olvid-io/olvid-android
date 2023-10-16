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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

@OptIn(ExperimentalComposeUiApi::class)
fun NavGraphBuilder.targetDeviceName(onboardingFlowViewModel : OnboardingFlowViewModel, onDeviceNameValidated: () -> Unit, onBack: () -> Unit,
                                     onClose: () -> Unit,) {
    composable(
        OnboardingRoutes.TRANSFER_TARGET_DEVICE_NAME,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        OnboardingScreen(
            step = OnboardingStep(title = stringResource(id = string.onboarding_device_name_title),
                subtitle = stringResource(id = string.onboarding_device_name_subtitle),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_validate)),
                        type = BUTTON
                    ) {
                        keyboardController?.hide()
                        onDeviceNameValidated.invoke()
                    }
                )
            ),
            onBack = onBack,
            onClose = onClose
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = onboardingFlowViewModel.deviceName,
                onValueChange = onboardingFlowViewModel::updateDeviceName,
                textStyle = TextStyle(fontSize = 20.sp),
                label = {
                    Text(text = stringResource(id = string.label_device_name))
                },
                placeholder = {
                    Text(
                        text = AppSingleton.DEFAULT_DEVICE_DISPLAY_NAME
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    onDeviceNameValidated.invoke()
                }),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedLabelColor = colorResource(id = R.color.olvid_gradient_contrasted), focusedBorderColor = colorResource(id = R.color.olvid_gradient_contrasted), cursorColor = colorResource(id = R.color.olvid_gradient_contrasted))
            )
        }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}