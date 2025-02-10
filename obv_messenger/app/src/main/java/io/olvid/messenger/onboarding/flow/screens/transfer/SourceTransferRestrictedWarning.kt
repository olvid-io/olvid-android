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

package io.olvid.messenger.onboarding.flow.screens.transfer

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON_OUTLINED
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.sourceTransferRestrictedWarning(
    onContinue: () -> Unit,
    onClose: () -> Unit,
    ) {
    composable(
        OnboardingRoutes.TRANSFER_RESTRICTED_WARNING,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(R.string.onboarding_transfer_restricted_title),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = R.string.button_label_cancel)),
                        type = BUTTON_OUTLINED,
                        onClick = onClose
                    ),
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = R.string.button_label_ok)),
                        icon = R.drawable.ic_message_status_delivered_and_read_one,
                        type = BUTTON,
                        onClick = onContinue
                    )
                )
            ),
            onClose = onClose
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp)
                    .widthIn(max = 350.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(32.dp),
                        painter = painterResource(id = R.drawable.ic_shield_outline),
                        colorFilter = ColorFilter.tint(color = colorResource(R.color.olvid_gradient_light)),
                        contentDescription = ""
                    )
                    Column(
                        modifier = Modifier.weight(1f, true)
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            style = OlvidTypography.body1,
                            fontWeight = FontWeight.Bold,
                            text = stringResource(R.string.explanation_transfer_restriction_title_1)
                        )
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            style = OlvidTypography.body1.copy(
                                color = colorResource(R.color.greyTint)
                            ),
                            text = stringResource(R.string.explanation_transfer_restriction_text_1)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(32.dp),
                        painter = painterResource(id = R.drawable.ic_question_mark_white),
                        colorFilter = ColorFilter.tint(color = colorResource(R.color.olvid_gradient_light)),
                        contentDescription = ""
                    )
                    Column(
                        modifier = Modifier.weight(1f, true)
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            style = OlvidTypography.body1,
                            fontWeight = FontWeight.Bold,
                            text = stringResource(R.string.explanation_transfer_restriction_title_2)
                        )
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            style = OlvidTypography.body1.copy(
                                color = colorResource(R.color.greyTint)
                            ),
                            text = stringResource(R.string.explanation_transfer_restriction_text_2)
                        )
                    }
                }
            }
        }
    }
}