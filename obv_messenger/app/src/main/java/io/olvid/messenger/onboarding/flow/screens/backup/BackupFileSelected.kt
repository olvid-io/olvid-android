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

package io.olvid.messenger.onboarding.flow.screens.backup

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.backupFileSelected(onboardingFlowViewModel : OnboardingFlowViewModel, onBackupKeySelected: () -> Unit, onBack: () -> Unit,
                                       onClose: () -> Unit,) {
    composable(
        OnboardingRoutes.BACKUP_FILE_SELECTED,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = if (onboardingFlowViewModel.backupType == OnboardingFlowViewModel.BACKUP_TYPE_FILE) string.text_title_backup_file_selected else string.text_title_backup_cloud_account_selected),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_proceed)),
                        type = BUTTON,
                        onClick = onBackupKeySelected
                    ),
                )
            ),
            onBack = onBack,
            onClose = onClose
        ) {
            Row(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE1E2E9),
                        shape = RoundedCornerShape(size = 8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = drawable.ic_file_mime_type),
                    contentDescription = ""
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = onboardingFlowViewModel.backupName ?: "",
                        color = colorResource(id = color.almostBlack),
                        style = OlvidTypography.body1,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = Formatter.formatShortFileSize(
                            LocalContext.current,
                            onboardingFlowViewModel.backupContent?.size?.toLong()
                                ?: 0
                        ),
                        style = OlvidTypography.subtitle1.copy(
                            color = Color(0xFF8B8D97),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}