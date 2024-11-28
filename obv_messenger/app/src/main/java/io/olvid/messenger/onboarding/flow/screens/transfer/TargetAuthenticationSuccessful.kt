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

package io.olvid.messenger.onboarding.flow.screens.transfer

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.targetAuthenticationSuccessful(onClose: () -> Unit,) {
    composable(
        OnboardingRoutes.TRANSFER_TARGET_AUTHENTICATION_SUCCESSFUL,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = R.string.onboarding_transfer_authentication_successful_title),
                subtitle = stringResource(id = R.string.onboarding_transfer_authentication_successful)
            ),
            onClose = onClose
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.CenterHorizontally),
                color = colorResource(id = R.color.olvid_gradient_light),
                strokeWidth = 5.dp,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    val navController = rememberNavController()

    AppCompatTheme {
        NavHost(
            navController = navController,
            startDestination = OnboardingRoutes.TRANSFER_TARGET_AUTHENTICATION_SUCCESSFUL
        ) {
            targetAuthenticationSuccessful(
                onClose = {}
            )
        }
    }
}