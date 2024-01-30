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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON_OUTLINED
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

fun NavGraphBuilder.targetRestoreSuccessful(onNewTransfer: () -> Unit, onClose: () -> Unit,) {
    composable(
        OnboardingRoutes.TRANSFER_TARGET_RESTORE_SUCCESSFUL,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = string.onboarding_transfer_successful_title),
                subtitle = stringResource(id = string.onboarding_transfer_successful_subtitle),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_activate_another_profile)),
                        type = BUTTON_OUTLINED
                    ) {
                        onNewTransfer.invoke()
                    },
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_done)),
                        type = BUTTON
                    ) {
                        onClose.invoke()
                    }
                )
            ),
            onClose = onClose
        ) {
            val screenWidth = LocalConfiguration.current.screenWidthDp
            var opened by remember { mutableStateOf(false) }
            val offset: Dp by animateDpAsState(
                targetValue = if (opened) 0.dp else screenWidth.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "animate"
            )

            LaunchedEffect(Unit) {
                delay(500.milliseconds)
                opened = true
            }
            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ){
                Image(
                    modifier = Modifier.size(120.dp).offset(x = -offset),
                    colorFilter = ColorFilter.tint(colorResource(id = color.grey)),
                    painter = painterResource(id = drawable.ic_device),
                    contentDescription = "")

                Image(
                    modifier = Modifier.size(100.dp).offset(x = offset),
                    colorFilter = ColorFilter.tint(colorResource(id = color.green)),
                    painter = painterResource(id = drawable.ic_ok_green),
                    contentDescription = "")
            }
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
            startDestination = OnboardingRoutes.TRANSFER_TARGET_RESTORE_SUCCESSFUL
        ) {
            targetRestoreSuccessful(
                onNewTransfer = {},
                onClose = {}
            )
        }
    }
}