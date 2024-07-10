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

package io.olvid.messenger.onboarding.flow.screens.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.R.color
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.OnboardingActivity
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingActionType.TEXT
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

@OptIn(ExperimentalComposeUiApi::class)
fun NavGraphBuilder.identityCreation(onboardingFlowViewModel: OnboardingFlowViewModel,
                                     onIdentityCreated : () -> Unit,
                                     onBack : () -> Unit,
                                     onClose : () -> Unit,
                                     ) {
    composable(
        OnboardingRoutes.IDENTITY_CREATION,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val context = LocalContext.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val scanLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {}
        OnboardingScreen(
            step = OnboardingStep(title = stringResource(id = string.onboarding_welcome_among_us),
                actions = listOf(
                    OnboardingAction(
                        label = buildAnnotatedString {
                            append(stringResource(id = string.onboarding_managed_profile_question))
                            append(" ")
                            withStyle(SpanStyle(color = colorResource(id = color.blueOrWhite))) {
                                append(stringResource(id = string.onboarding_managed_profile_hyperlink))
                            }
                        },
                        type = TEXT,
                        onClick = { scanLauncher.launch(Intent(context, OnboardingActivity::class.java)) }
                    ),
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_create_new_profile)),
                        type = BUTTON,
                        enabled = onboardingFlowViewModel.creatingSimpleIdentity.not() && (onboardingFlowViewModel.firstName.isNotEmpty() || onboardingFlowViewModel.lastName.isNotEmpty())
                    ) {
                        keyboardController?.hide()
                        onboardingFlowViewModel.createSimpleIdentity(onSuccess = { onIdentityCreated.invoke() })
                    }
                )
            ),
            onBack = onBack,
            onClose = onClose
        ) {
            OutlinedTextField(
                value = onboardingFlowViewModel.firstName,
                onValueChange = onboardingFlowViewModel::updateFirstName,
                textStyle = TextStyle(fontSize = 20.sp),
                singleLine = true,
                label = {
                    Text(text = stringResource(id = string.hint_first_name))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Words),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedLabelColor = colorResource(id = color.olvid_gradient_contrasted), focusedBorderColor = colorResource(id = color.olvid_gradient_contrasted), cursorColor = colorResource(id = color.olvid_gradient_contrasted))
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = onboardingFlowViewModel.lastName,
                onValueChange = onboardingFlowViewModel::updateLastName,
                textStyle = TextStyle(fontSize = 20.sp),
                singleLine = true,
                label = {
                    Text(
                        text = stringResource(id = string.hint_last_name)
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Words),
                keyboardActions = KeyboardActions(
                    onDone = { onboardingFlowViewModel.createSimpleIdentity(onSuccess = { onIdentityCreated.invoke() }) }
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedLabelColor = colorResource(id = color.olvid_gradient_contrasted), focusedBorderColor = colorResource(id = color.olvid_gradient_contrasted), cursorColor = colorResource(id = color.olvid_gradient_contrasted))
            )
        }
    }
}