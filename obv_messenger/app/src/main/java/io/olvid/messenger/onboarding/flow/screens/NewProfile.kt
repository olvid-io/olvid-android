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

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.string
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.OnboardingActivity
import io.olvid.messenger.onboarding.flow.OnboardingAction
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

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = string.onboarding_new_profile_title),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_activate_profile)),
                        onClick = onImportProfile
                    ),
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_create_new_profile)),
                        onClick = onNewProfile
                    ),
                )
            ),
            onClose = onClose,
            footer = {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = buildAnnotatedString {
                        append(stringResource(id = R.string.onboarding_managed_profile_question))
                        append(" ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "",
                                styles = TextLinkStyles(SpanStyle(color = colorResource(id = color.blueOrWhite))),
                                linkInteractionListener = {
                                    scanLauncher.launch(
                                        Intent(
                                            context,
                                            OnboardingActivity::class.java
                                        )
                                    )
                                }
                            ),
                        ) {
                            append(stringResource(id = R.string.onboarding_managed_profile_hyperlink))
                        }
                    },
                    textAlign = TextAlign.Center,
                    color = colorResource(id = R.color.greyTint),
                    style = OlvidTypography.body2,
                )
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
        newProfileScreen({}, {}, {})
    }
}
