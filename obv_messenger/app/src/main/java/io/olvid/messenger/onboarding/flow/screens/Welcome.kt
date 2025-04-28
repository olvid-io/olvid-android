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

package io.olvid.messenger.onboarding.flow.screens

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.welcomeScreen(
    onExistingProfile: () -> Unit,
    onNewProfile: () -> Unit,
    onClose: () -> Unit
) {
    composable(
        OnboardingRoutes.WELCOME_SCREEN,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = string.onboarding_welcome_title),
                subtitle = stringResource(id = string.onboarding_welcome_subtitle),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.onboarding_action_existing_profile_label)),
                        onClick = onExistingProfile
                    ),
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.onboarding_action_new_profile_label)),
                        onClick = onNewProfile
                    ),
                )
            ),
            onClose = onClose
        )
    }
}

@Preview
@Composable
fun WelcomeScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.WELCOME_SCREEN,
    ) {
        welcomeScreen({}, {}, {})
    }
}