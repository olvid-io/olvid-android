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

package io.olvid.messenger.onboarding.flow.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.R.color
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.OnboardingActivity
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.newProfileScreen(
    onImportProfile: () -> Unit,
    onNewProfile: () -> Unit,
    onClose: () -> Unit
) {
    composable(
        OnboardingRoutes.NEW_PROFILE_SCREEN,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        val context = LocalContext.current
        val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
        val activity = LocalContext.current as? Activity

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = string.onboarding_new_profile_title),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_activate_profile)),
                        description = AnnotatedString(stringResource(id = string.button_description_activate_profile)),
                        onClick = onImportProfile
                    ),
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.onboarding_action_create_new_profile)),
                        onClick = onNewProfile
                    ),
                    OnboardingAction(
                        label = buildAnnotatedString {
                            append(stringResource(id = string.onboarding_managed_profile_question))
                            append(" ")
                            withStyle(SpanStyle(color = colorResource(id = color.blueOrWhite))) {
                                append(stringResource(id = string.onboarding_managed_profile_hyperlink))
                            }
                        },
                        type = OnboardingActionType.TEXT,
                        onClick = {
                            scanLauncher.launch(Intent(context, OnboardingActivity::class.java))
                            activity?.finish()
                        }
                    ),
                )
            ),
            onClose = onClose
        )
    }
}