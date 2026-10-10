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

package io.olvid.messenger.onboarding.flow.screens.profile

import android.util.Pair
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.engine.engine.types.EngineAPI
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidOutlinedActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import io.olvid.messenger.onboarding.OnboardingViewModel
import io.olvid.messenger.onboarding.OnboardingViewModel.ValidatedStatus
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.onboarding.flow.screens.ValidationStatusIndicator

/**
 * Compose replacement for the legacy IdentityCreationOptionsFragment: lets the user enter a custom
 * server and license/activation code, validate them (querying the engine well-known and api key status),
 * then continue to identity creation.
 */
fun NavGraphBuilder.identityCreationOptions(
    onboardingViewModel: OnboardingViewModel,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.IDENTITY_CREATION_OPTIONS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val validatedStatus: Pair<ValidatedStatus, ValidatedStatus>? by onboardingViewModel.validatedStatus.observeAsState()
        var server by remember { mutableStateOf(onboardingViewModel.unvalidatedServer.orEmpty()) }
        var apiKey by remember { mutableStateOf(onboardingViewModel.unformattedApiKey.orEmpty()) }

        DisposableEffect(Unit) {
            val engine = AppSingleton.getEngine()
            val listeners = listOf(
                EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS to object :
                    SimpleEngineNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS) {
                    override fun callback(userInfo: HashMap<String, Any?>) {
                        (userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY] as? String)?.let {
                            onboardingViewModel.serverValidationFinished(it, true)
                        }
                    }
                },
                EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED to object :
                    SimpleEngineNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED) {
                    override fun callback(userInfo: HashMap<String, Any?>) {
                        (userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY] as? String)?.let {
                            onboardingViewModel.serverValidationFinished(it, false)
                        }
                    }
                },
                EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS to object :
                    SimpleEngineNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS) {
                    override fun callback(userInfo: HashMap<String, Any?>) {
                        (userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY] as? java.util.UUID)?.let {
                            val success =
                                when (userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY] as? EngineAPI.ApiKeyStatus) {
                                    EngineAPI.ApiKeyStatus.VALID,
                                    EngineAPI.ApiKeyStatus.OPEN_BETA_KEY,
                                    EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY -> true

                                    EngineAPI.ApiKeyStatus.UNKNOWN,
                                    EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED,
                                    EngineAPI.ApiKeyStatus.EXPIRED,
                                    EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD,
                                    EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD,
                                    EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED -> false

                                    else -> false
                                }
                            onboardingViewModel.apiKeyValidationFinished(it, success)
                        }
                    }
                },
                EngineNotifications.API_KEY_STATUS_QUERY_FAILED to object :
                    SimpleEngineNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_FAILED) {
                    override fun callback(userInfo: HashMap<String, Any?>) {
                        (userInfo[EngineNotifications.API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY] as? java.util.UUID)?.let {
                            onboardingViewModel.apiKeyValidationFinished(it, false)
                        }
                    }
                },
            )
            listeners.forEach { engine.addNotificationListener(it.first, it.second) }
            if (onboardingViewModel.isDeepLinked) {
                onboardingViewModel.isDeepLinked = false
                onboardingViewModel.checkServerAndApiKey()
            }
            onDispose {
                listeners.forEach { engine.removeNotificationListener(it.first, it.second) }
            }
        }

        val serverStatus = validatedStatus?.first
        val apiKeyStatus = validatedStatus?.second
        val canContinue = serverStatus == ValidatedStatus.VALID

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(R.string.activity_title_identity_creation_options),
            ),
            onBack = onBack,
            onClose = onClose,
        ) {
            Text(
                text = stringResource(R.string.explanation_identity_creation_options),
                style = OlvidTypography.body2,
                color = colorResource(R.color.greyTint),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = server,
                    shape = RoundedCornerShape(12.dp),
                    onValueChange = {
                        server = it
                        onboardingViewModel.validateServer(it)
                    },
                    trailingIcon = {
                        ValidationStatusIndicator(serverStatus)
                    },
                    label = { Text(stringResource(R.string.hint_olvid_server)) },
                    singleLine = true,
                    colors = olvidDefaultTextFieldColors(),
                    textStyle = OlvidTypography.body1,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                shape = RoundedCornerShape(12.dp),
                onValueChange = {
                    apiKey = it
                    onboardingViewModel.setApiKey(it)
                },
                trailingIcon = {
                    ValidationStatusIndicator(apiKeyStatus)
                },
                label = { Text(stringResource(R.string.hint_license_activation_code)) },
                singleLine = true,
                colors = olvidDefaultTextFieldColors(),
                textStyle = OlvidTypography.body1,
            )
//            when (apiKeyStatus) {
//                ValidatedStatus.CHECKING -> {
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Text(
//                        text = stringResource(R.string.label_checking_license),
//                        style = OlvidTypography.body2,
//                        color = colorResource(R.color.greyTint),
//                    )
//                }
//
//                ValidatedStatus.INVALID -> {
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Text(
//                        text = stringResource(R.string.label_unable_to_check_license_status),
//                        style = OlvidTypography.body2,
//                        color = colorResource(R.color.red),
//                    )
//                }
//
//                else -> {}
//            }
            Spacer(modifier = Modifier.height(24.dp))
            OlvidOutlinedActionButton(
                modifier = Modifier.fillMaxWidth(),
                // mirror the legacy fragment: once the server is valid the button is only useful to
                // (re)check an entered-but-unvalidated api key, otherwise it is a no-op so we disable it
                text = stringResource(R.string.button_label_check_options),
                enabled = when (serverStatus) {
                    ValidatedStatus.CHECKING -> false
                    ValidatedStatus.VALID -> apiKey.isNotEmpty() && apiKeyStatus != ValidatedStatus.VALID
                    else -> server.isNotEmpty()
                },
                onClick = {
                    keyboardController?.hide()
                    onboardingViewModel.checkServerAndApiKey()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            OlvidActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.button_label_continue_as_new_user),
                enabled = canContinue,
                onClick = onContinue,
                large = true,
                allowTwoLines = true
            )
        }
    }
}
