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

package io.olvid.messenger.onboarding.flow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.ObvDialog
import io.olvid.engine.engine.types.ObvTransferStep.SourceDisplaySessionNumber
import io.olvid.engine.engine.types.ObvTransferStep.SourceSasInput
import io.olvid.engine.engine.types.ObvTransferStep.Step.FAIL
import io.olvid.engine.engine.types.ObvTransferStep.Step.ONGOING_PROTOCOL
import io.olvid.engine.engine.types.ObvTransferStep.Step.SOURCE_DISPLAY_SESSION_NUMBER
import io.olvid.engine.engine.types.ObvTransferStep.Step.SOURCE_SAS_INPUT
import io.olvid.engine.engine.types.ObvTransferStep.Step.SOURCE_SNAPSHOT_SENT
import io.olvid.engine.engine.types.ObvTransferStep.Step.SOURCE_WAIT_FOR_SESSION_NUMBER
import io.olvid.engine.engine.types.ObvTransferStep.Step.TARGET_SESSION_NUMBER_INPUT
import io.olvid.engine.engine.types.ObvTransferStep.Step.TARGET_SHOW_SAS
import io.olvid.engine.engine.types.ObvTransferStep.Step.TARGET_SNAPSHOT_RECEIVED
import io.olvid.engine.engine.types.ObvTransferStep.TargetShowSas
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.R.string
import io.olvid.messenger.onboarding.OnboardingActivity
import io.olvid.messenger.onboarding.flow.OnboardingActionType.CHOICE
import io.olvid.messenger.onboarding.flow.screens.backup.backupChooseFile
import io.olvid.messenger.onboarding.flow.screens.backup.backupFileSelected
import io.olvid.messenger.onboarding.flow.screens.backup.backupKeyValidation
import io.olvid.messenger.onboarding.flow.screens.newProfileScreen
import io.olvid.messenger.onboarding.flow.screens.profile.existingProfile
import io.olvid.messenger.onboarding.flow.screens.profile.identityCreation
import io.olvid.messenger.onboarding.flow.screens.profile.profilePicture
import io.olvid.messenger.onboarding.flow.screens.transfer.activeDeviceSelection
import io.olvid.messenger.onboarding.flow.screens.transfer.sourceConfirmation
import io.olvid.messenger.onboarding.flow.screens.transfer.sourceSession
import io.olvid.messenger.onboarding.flow.screens.transfer.targetDeviceName
import io.olvid.messenger.onboarding.flow.screens.transfer.targetRestoreSuccessful
import io.olvid.messenger.onboarding.flow.screens.transfer.targetSessionInput
import io.olvid.messenger.onboarding.flow.screens.transfer.targetShowSas
import io.olvid.messenger.onboarding.flow.screens.welcomeScreen
import io.olvid.messenger.services.MDMConfigurationSingleton
import io.olvid.messenger.settings.SettingsActivity
import java.util.UUID

enum class OnboardingActionType {
    CHOICE,
    TEXT,
    BUTTON,
    BUTTON_OUTLINED,
}

data class OnboardingAction(
    val label: AnnotatedString,
    val description: AnnotatedString? = null,
    val type: OnboardingActionType = CHOICE,
    @DrawableRes val icon: Int? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

data class OnboardingStep(
    val title: String = "",
    val subtitle: String = "",
    val actions: List<OnboardingAction> = listOf()
)

class OnboardingFlowActivity : AppCompatActivity() {
    companion object {
        const val TRANSFER_SOURCE_INTENT_EXTRA = "transfer_source"
        const val TRANSFER_TARGET_INTENT_EXTRA = "transfer_target"
        const val NEW_PROFILE_INTENT_EXTRA = "new_profile"
    }

    private var reEnableDialogsOnFinish = true

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        App.setAppDialogsBlocked(true)

        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            // todo use WindowInsets for android 30
        }

        val transferSource = intent.getBooleanExtra(TRANSFER_SOURCE_INTENT_EXTRA, false)
        val transferTarget = intent.getBooleanExtra(TRANSFER_TARGET_INTENT_EXTRA, false)
        val newProfile = intent.getBooleanExtra(NEW_PROFILE_INTENT_EXTRA, false)

        val startDestination = if(transferSource)
            OnboardingRoutes.TRANSFER_SOURCE_SESSION
        else if (newProfile)
            OnboardingRoutes.NEW_PROFILE_SCREEN
        else if (transferTarget)
            OnboardingRoutes.TRANSFER_TARGET_DEVICE_NAME
        else
            OnboardingRoutes.WELCOME_SCREEN


        setContent {
            val navController = rememberNavController()
            val onboardingFlowViewModel: OnboardingFlowViewModel by viewModels()

            AppCompatTheme {
                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {

                    welcomeScreen(onExistingProfile = {
                        navController.navigate(OnboardingRoutes.EXISTING_PROFILE)
                    },
                        onNewProfile = {
                            // mdm forwards to legacy activity
                            try {
                                if (MDMConfigurationSingleton.getKeycloakConfigurationUri() != null) {
                                    startActivity(
                                        Intent(this@OnboardingFlowActivity, OnboardingActivity::class.java)
                                            .putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, true)
                                    )
                                    finish()
                                } else {
                                    navController.navigate(OnboardingRoutes.IDENTITY_CREATION)
                                }
                            } catch (_: Exception) {
                                navController.navigate(OnboardingRoutes.IDENTITY_CREATION)
                            }
                        },
                        onClose = { finish() })
                    newProfileScreen(
                        onImportProfile = {
                            navController.navigate(OnboardingRoutes.TRANSFER_TARGET_DEVICE_NAME)
                        },
                        onNewProfile = {
                            startActivity(
                                Intent(this@OnboardingFlowActivity, OnboardingActivity::class.java)
                                    .putExtra(OnboardingActivity.PROFILE_CREATION, true)
                            )
                            finish()
                        },
                        onClose = {finish()}
                    )
                    identityCreation(onboardingFlowViewModel = onboardingFlowViewModel,
                        onIdentityCreated = {
                            runOnUiThread {
                                navController.navigate(
                                    OnboardingRoutes.PROFILE_PICTURE
                                )
                            }
                        },
                        onBack = { navController.navigateUp() },
                        onClose = { finish() })
                    profilePicture(this@OnboardingFlowActivity, onboardingFlowViewModel)
                    existingProfile(onReactivate = { navController.navigate(OnboardingRoutes.TRANSFER_TARGET_DEVICE_NAME) },
                        onRestoreBackup = { navController.navigate(OnboardingRoutes.BACKUP_CHOOSE_FILE) },
                        onBack = { navController.navigateUp() },
                        onClose = { finish() })

                    backupChooseFile(onboardingFlowViewModel = onboardingFlowViewModel,
                        onBackupCloudSelected = {
                            onboardingFlowViewModel.selectBackupCloud(this@OnboardingFlowActivity) {
                                runOnUiThread { navController.navigate(OnboardingRoutes.BACKUP_FILE_SELECTED) }
                            }
                        },
                        onBackupFileSelected = {
                            runOnUiThread {
                                navController.navigate(OnboardingRoutes.BACKUP_FILE_SELECTED)
                            }
                        },
                        onBack = {
                            onboardingFlowViewModel.clearSelectedBackup()
                            navController.navigateUp()
                        },
                        onClose = { finish() })
                    backupFileSelected(onboardingFlowViewModel = onboardingFlowViewModel,
                        onBackupKeySelected = {
                            navController.navigate(OnboardingRoutes.BACKUP_KEY_VALIDATION)
                        },
                        onBack = {
                            navController.navigateUp()
                        },
                        onClose = { finish() })

                    backupKeyValidation(onboardingFlowViewModel = onboardingFlowViewModel,
                        onBackupKeyValidation = {
                            onboardingFlowViewModel.validateBackupSeed()
                            if (onboardingFlowViewModel.backupKeyValid) {
                                AppSingleton.getInstance().restoreBackup(
                                    this@OnboardingFlowActivity,
                                    onboardingFlowViewModel.backupSeed,
                                    onboardingFlowViewModel.backupContent, { finish() }
                                ) {
                                    App.toast(
                                        R.string.toast_message_unable_to_load_backup_configuration,
                                        Toast.LENGTH_SHORT
                                    )
                                }
                            } else {
                                App.toast(
                                    R.string.text_backup_key_verification_failed,
                                    Toast.LENGTH_SHORT
                                )
                            }
                        }, onBack = { navController.navigateUp() },
                        onClose = { finish() })

                    sourceSession(onboardingFlowViewModel = onboardingFlowViewModel,
                        onSasValidated = { navController.navigate(OnboardingRoutes.TRANSFER_ACTIVE_DEVICES) },
                        onClose = {
                            onboardingFlowViewModel.abortTransfer()
                            finish()
                        })
                    targetDeviceName(onboardingFlowViewModel = onboardingFlowViewModel,
                        onDeviceNameValidated = {
                            navController.navigate(OnboardingRoutes.TRANSFER_TARGET_SESSION_INPUT)
                            App.runThread {
                                AppSingleton.getEngine()
                                    .initiateOwnedIdentityTransferProtocolOnTargetDevice(
                                        onboardingFlowViewModel.deviceName.trim().ifEmpty { AppSingleton.DEFAULT_DEVICE_DISPLAY_NAME }
                                    )
                            }
                        },
                        onBack = {
                            navController.navigateUp()
                        },
                        onClose = { finish() })
                    targetSessionInput(onboardingFlowViewModel = onboardingFlowViewModel,
                        onDeviceSessionValidated = {
                            try {
                                onboardingFlowViewModel.dialog?.apply {
                                    setTransferSessionNumber(
                                        try {
                                            onboardingFlowViewModel.sessionNumber.toLong()
                                        } catch (e: Exception) {
                                            0L
                                        }
                                    )
                                    AppSingleton.getEngine().respondToDialog(this)
                                }
                            } catch (ex: Exception) {
                                onboardingFlowViewModel.updateValidationInProgress(false)
                                App.toast(R.string.toast_message_profile_activation_failed, Toast.LENGTH_SHORT, Gravity.BOTTOM)
                            }
                        },
                        onClose = {
                            onboardingFlowViewModel.abortTransfer()
                            finish()
                        })
                    targetShowSas(
                        onboardingFlowViewModel = onboardingFlowViewModel,
                        onClose = {
                            onboardingFlowViewModel.abortTransfer()
                            finish()
                        })
                    targetRestoreSuccessful(
                        onNewTransfer = {
                            reEnableDialogsOnFinish = false
                            finish()
                            Handler(Looper.getMainLooper()).postDelayed({
                                startActivity(
                                    Intent(this@OnboardingFlowActivity, OnboardingFlowActivity::class.java)
                                        .putExtra(TRANSFER_TARGET_INTENT_EXTRA, true)
                                )
                            }, 300);
                        },
                        onClose = {
                            App.openCurrentOwnedIdentityDetails(this@OnboardingFlowActivity)
                            finish()
                        })
                    sourceConfirmation(
                        onboardingFlowViewModel = onboardingFlowViewModel,
                        onFinalize = {
                            onboardingFlowViewModel.finalizeTransfer()
                        },
                        onBack = { navController.navigateUp() },
                        onClose = {
                            onboardingFlowViewModel.abortTransfer()
                            finish()
                        },
                        ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
                    )
                    activeDeviceSelection(
                        onboardingFlowViewModel = onboardingFlowViewModel,
                        onProceed = {
                            navController.navigate(OnboardingRoutes.TRANSFER_SOURCE_CONFIRMATION)
                        },
                        onClose = {
                            onboardingFlowViewModel.abortTransfer()
                            finish()
                        })
                    composable(
                        "scan",
                        enterTransition = { slideIntoContainer(SlideDirection.Start) },
                        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
                        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
                        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
                    ) {
                        OnboardingScreen(
                            step = OnboardingStep(
                                title = stringResource(id = string.activity_title_scan_configuration),
                            ),
                            onBack = { navController.navigateUp() },
                            onClose = { finish() }
                        ) {

                        }
                    }
                }
            }

            TransferListener { dialog ->
                onboardingFlowViewModel.dialog = dialog
                runOnUiThread {
                    when (dialog.category.obvTransferStep.step) {
                        FAIL -> {
                            onboardingFlowViewModel.updateValidationInProgress(false)
                            App.toast(getString(string.toast_message_profile_activation_failed), Toast.LENGTH_SHORT, Gravity.BOTTOM)
                        }

                        TARGET_SESSION_NUMBER_INPUT -> {
                            onboardingFlowViewModel.updateValidationError(onboardingFlowViewModel.validationInProgress)
                            onboardingFlowViewModel.updateValidationInProgress(false)
                        }

                        TARGET_SHOW_SAS -> {
                            onboardingFlowViewModel.updateValidationInProgress(false)
                            onboardingFlowViewModel.updateSas(
                                (dialog.category?.obvTransferStep as? TargetShowSas)?.sas
                                    ?: ""
                            )
                            navController.navigate(OnboardingRoutes.TRANSFER_TARGET_SHOW_SAS)
                        }

                        TARGET_SNAPSHOT_RECEIVED -> {
                            onboardingFlowViewModel.updateValidationInProgress(false)
                            navController.navigate(OnboardingRoutes.TRANSFER_TARGET_RESTORE_SUCCESSFUL)
                        }

                        ONGOING_PROTOCOL -> {
                            onboardingFlowViewModel.updateValidationInProgress(true)
                        }

                        SOURCE_WAIT_FOR_SESSION_NUMBER -> {}
                        SOURCE_DISPLAY_SESSION_NUMBER -> {
                            onboardingFlowViewModel.updateValidationInProgress(false)
                            onboardingFlowViewModel.updateSessionNumber(
                                (dialog.category?.obvTransferStep as? SourceDisplaySessionNumber)?.sessionNumber?.run {
                                    "%08d".format(
                                        this
                                    )
                                }
                                    ?: ""
                            )
                        }


                        SOURCE_SAS_INPUT -> {
                            onboardingFlowViewModel.updateValidationInProgress(false)
                            onboardingFlowViewModel.updateDeviceName(
                                (dialog.category?.obvTransferStep as? SourceSasInput)?.targetDeviceName?.toString()
                                    ?: ""
                            )
                            onboardingFlowViewModel.updateCorrectSas((dialog.category?.obvTransferStep as? SourceSasInput)?.correctSas?.toString())
                        }

                        SOURCE_SNAPSHOT_SENT -> {
                            finish()
                        }

                        else -> {
                            onboardingFlowViewModel.updateValidationInProgress(false)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (reEnableDialogsOnFinish) {
            App.setAppDialogsBlocked(false)
        }
    }
}



@Composable
private fun TransferListener(onTransferEvent: (ObvDialog) -> Unit) {
    val context = LocalContext.current

    val currentOnTransferEvent by rememberUpdatedState(onTransferEvent)

    DisposableEffect(context) {
        val transferListener =
            object : SimpleEngineNotificationListener(EngineNotifications.UI_DIALOG) {
                override fun callback(userInfo: HashMap<String, Any>) {
                    val dialogUuid = userInfo[EngineNotifications.UI_DIALOG_UUID_KEY] as? UUID
                    val dialog = userInfo[EngineNotifications.UI_DIALOG_DIALOG_KEY] as? ObvDialog
                    if (dialogUuid == null || dialog == null) {
                        return
                    }
                    if (dialog.category.id == ObvDialog.Category.TRANSFER_DIALOG_CATEGORY) {
                        currentOnTransferEvent.invoke(dialog)
                    }
                }
            }
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.UI_DIALOG, transferListener)

        onDispose {
            AppSingleton.getEngine()
                .removeNotificationListener(EngineNotifications.UI_DIALOG, transferListener)
        }
    }
}