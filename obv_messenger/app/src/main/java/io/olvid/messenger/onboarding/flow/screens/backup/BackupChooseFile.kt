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

package io.olvid.messenger.onboarding.flow.screens.backup

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.App
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep

fun NavGraphBuilder.backupChooseFile(onboardingFlowViewModel: OnboardingFlowViewModel, onBackupFileSelected : () -> Unit, onBackupCloudSelected : () -> Unit, onBack: () -> Unit,
                                     onClose: () -> Unit,) {
    composable(
        OnboardingRoutes.BACKUP_CHOOSE_FILE,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val filePicker = rememberLauncherForActivityResult(
            contract = GetContent(),
            onResult = {
                onboardingFlowViewModel.clearSelectedBackup()
                if (StringUtils.validateUri(it)) {
                    App.runThread {
                        val contentResolver = App.getContext().contentResolver
                        var fileName: String? = null
                        val projection =
                            arrayOf(OpenableColumns.DISPLAY_NAME)
                        contentResolver.query(
                            it!!,
                            projection,
                            null,
                            null,
                            null
                        ).use { cursor ->
                            if (cursor != null && cursor.moveToFirst()) {
                                val displayNameIndex =
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (displayNameIndex >= 0) {
                                    fileName = cursor.getString(displayNameIndex)
                                }
                            }
                        }
                        try {
                            onboardingFlowViewModel.selectBackupFile(it, fileName)
                            onBackupFileSelected.invoke()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            App.toast(
                                string.toast_message_error_opening_backup_file,
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                }
            }
        )
        OnboardingScreen(
            step = OnboardingStep(title = stringResource(id = string.onboarding_backup_source_title),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_a_file)),
                        icon = drawable.ic_save_64dp,
                        type = BUTTON
                    ) {
                        filePicker.launch("*/*")
                    },
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = string.button_label_the_cloud)),
                        icon = drawable.ic_cloud_download,
                        type = BUTTON,
                        onClick = onBackupCloudSelected
                    )
                )
            ),
            onBack = onBack,
            onClose = onClose
        )
    }
}