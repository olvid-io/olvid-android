/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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
import io.olvid.messenger.R
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.onboarding.flow.screens.profile.ProfileManagedByOrganization

fun NavGraphBuilder.newProfileScreen(
    onImportProfile: () -> Unit,
    onNewProfile: () -> Unit,
    onManagedProfile: () -> Unit,
    onClose: () -> Unit
) {
    composable(
        OnboardingRoutes.NEW_PROFILE_SCREEN,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = R.string.onboarding_new_profile_title),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = R.string.button_label_activate_profile)),
                        onClick = onImportProfile
                    ),
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = R.string.button_label_create_new_profile)),
                        onClick = onNewProfile
                    ),
                )
            ),
            onClose = onClose,
            footer = {
                ProfileManagedByOrganization(onClick = onManagedProfile)
            }
        )
    }
}


@Preview
@Composable
fun ExistingProfilePreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.NEW_PROFILE_SCREEN,
    ) {
        newProfileScreen({}, {}, {}, {})
    }
}
