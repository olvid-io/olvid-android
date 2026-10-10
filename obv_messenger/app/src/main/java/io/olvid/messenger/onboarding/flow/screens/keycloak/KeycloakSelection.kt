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

package io.olvid.messenger.onboarding.flow.screens.keycloak

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.util.Pair
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.openStoreUrlOrFallback
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidPasswordInput
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import io.olvid.messenger.onboarding.OnboardingViewModel
import io.olvid.messenger.onboarding.OnboardingViewModel.ValidatedStatus
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.onboarding.flow.screens.ValidationStatusIndicator
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.openid.KeycloakTasks
import io.olvid.messenger.openid.KeycloakTasks.AuthenticateCallback
import io.olvid.messenger.openid.KeycloakTasks.DiscoverKeycloakServerCallback
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import io.olvid.messenger.openid.jsons.OlvidWellKnownJson
import net.openid.appauth.AuthState
import org.jose4j.jwk.JsonWebKeySet

/**
 * Compose replacement for the legacy KeycloakSelectionFragment: discovers a keycloak server, authenticates
 * (OIDC or magic link), fetches the user details, and proceeds to managed identity creation.
 */
fun NavGraphBuilder.keycloakSelection(
    onboardingViewModel: OnboardingViewModel,
    onIdentityCreation: () -> Unit, // navigate to ADDITIONAL_IDENTITY_CREATION; always invoked on the main thread
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.KEYCLOAK_SELECTION,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val context = LocalContext.current
        val activity = LocalActivity.current
        val status: ValidatedStatus? by onboardingViewModel.keycloakValidatedStatus.observeAsState()

        var fragment by remember { mutableStateOf<KeycloakAuthenticationStartFragment?>(null) }
        var server by remember { mutableStateOf(onboardingViewModel.keycloakServer.orEmpty()) }
        var clientId by remember { mutableStateOf(onboardingViewModel.keycloakClientId.orEmpty()) }
        val clientSecret = remember { mutableStateOf(onboardingViewModel.keycloakClientSecret.orEmpty()) }
        var autoStarted by remember { mutableStateOf(false) }
        // deep-linked / MDM configurations hide the raw keycloak details behind a green checkmark once
        // discovery succeeds; the user can still reveal them, and they show up if discovery fails
        var detailsShown by remember { mutableStateOf(!onboardingViewModel.isDeepLinked) }
        // null = idle; otherwise a string res shown next to a spinner while retrieving details / checking server
        @StringRes var loadingTextRes by remember { mutableStateOf<Int?>(null) }

        val mdm = onboardingViewModel.isConfiguredFromMdm

        val clearViewModelAndGoBack = {
            // clear any details loaded from keycloak
            onboardingViewModel.keycloakServer = null
            onBack()
        }

        fun mainThread(block: () -> Unit) = Handler(Looper.getMainLooper()).post(block)

        fun validateKeycloakServer() {
            val keycloakServerUrl = onboardingViewModel.keycloakServer ?: return
            KeycloakTasks.discoverKeycloakServerOpenidConfiguration(
                keycloakServerUrl,
                object : DiscoverKeycloakServerCallback {
                    override fun success(serverUrl: String, authState: AuthState, jwks: JsonWebKeySet, olvidWellKnown: OlvidWellKnownJson?) {
                        mainThread {
                            server = serverUrl
                            onboardingViewModel.keycloakValidationSuccess(
                                keycloakServerUrl,
                                serverUrl,
                                authState.jsonSerializeString(),
                                jwks,
                                olvidWellKnown?.supportIdentityAuthentication
                            )
                        }
                    }

                    override fun failed() {
                        mainThread {
                            onboardingViewModel.keycloakValidationFailed(keycloakServerUrl)
                            detailsShown = true
                        }
                    }
                })
        }

        // engine well-known listeners: after authentication, if the keycloak provides a server we validate it,
        // then proceed to identity creation
        DisposableEffect(Unit) {
            val engine = AppSingleton.getEngine()
            val success = object : SimpleEngineNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS) {
                override fun callback(userInfo: HashMap<String, Any?>) {
                    (userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY] as? String)?.let {
                        onboardingViewModel.serverValidationFinished(it, true)
                        mainThread {
                            loadingTextRes = null
                            onIdentityCreation()
                        }
                    }
                }
            }
            val failure = object : SimpleEngineNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED) {
                override fun callback(userInfo: HashMap<String, Any?>) {
                    (userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY] as? String)?.let {
                        onboardingViewModel.serverValidationFinished(it, false)
                        mainThread {
                            loadingTextRes = null
                            App.toast(R.string.toast_message_unable_to_connect_to_server, android.widget.Toast.LENGTH_SHORT)
                        }
                    }
                }
            }
            engine.addNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, success)
            engine.addNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, failure)
            onDispose {
                engine.removeNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, success)
                engine.removeNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, failure)
            }
        }

        BackHandler {
            clearViewModelAndGoBack()
        }

        fun onUserDetails(result: Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?) {
            if (result?.first == null || result.second == null) {
                mainThread { loadingTextRes = null }
                App.toast(R.string.toast_message_unable_to_retrieve_details, android.widget.Toast.LENGTH_SHORT)
                return
            }
            val minimumBuildVersion = result.second.minimumBuildVersions?.get("android")
            if (minimumBuildVersion != null && minimumBuildVersion > (BuildConfig.VERSION_CODE / BuildConfig.VERSION_CODE_MULTIPLIER)) {
                mainThread {
                    loadingTextRes = null
                    SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_outdated_version)
                        .setMessage(R.string.explanation_keycloak_olvid_version_outdated)
                        .setPositiveButton(R.string.button_label_update) { _, _ ->
                            context.openStoreUrlOrFallback()
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .create()
                        .show()
                }
                return
            }
            onboardingViewModel.keycloakUserDetails = result.first.userDetails
            onboardingViewModel.isKeycloakRevocationAllowed = result.second.revocationAllowed
            onboardingViewModel.isKeycloakTransferRestricted = result.second.transferRestricted
            onboardingViewModel.keycloakSignatureKey = result.first.signatureKey
            onboardingViewModel.setApiKey(null)

            mainThread {
                if (result.first.server != null) {
                    onboardingViewModel.validateServer(result.first.server)
                    loadingTextRes = R.string.label_checking_server
                    AppSingleton.getEngine().queryServerWellKnown(result.first.server)
                } else {
                    loadingTextRes = null
                    onIdentityCreation()
                }
            }
        }

        fun authenticate() {
            val keycloakServerUrl = onboardingViewModel.keycloakServer
            val serializedAuthState = onboardingViewModel.keycloakSerializedAuthState
            val keycloakMagic = onboardingViewModel.keycloakMagic
            val cid = onboardingViewModel.keycloakClientId
            val csecret = onboardingViewModel.keycloakClientSecret

            val successCallback = { authState: AuthState ->
                onboardingViewModel.keycloakSerializedAuthState = authState.jsonSerializeString()
                val keycloakServer = onboardingViewModel.keycloakServer
                val jwks = onboardingViewModel.keycloakJwks
                if (keycloakServer != null && jwks != null) {
                    mainThread { loadingTextRes = R.string.label_retrieving_user_details }
                    KeycloakTasks.getOwnDetails(
                        context,
                        keycloakServer,
                        authState,
                        onboardingViewModel.getSupportedKeycloakAuthMethods(),
                        null,
                        jwks,
                        null,
                        object : KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?> {
                            override fun success(result: Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?) {
                                onUserDetails(result)
                            }

                            override fun failed(rfc: Int) {
                                mainThread { loadingTextRes = null }
                                App.toast(R.string.toast_message_unable_to_retrieve_details, android.widget.Toast.LENGTH_SHORT)
                            }
                        }
                    )
                }
            }

            if (keycloakServerUrl != null && serializedAuthState != null) {
                if (keycloakMagic != null) {
                    KeycloakTasks.useMagicLink(
                        keycloakServerUrl = keycloakServerUrl,
                        keycloakMagic = keycloakMagic,
                        authState = AuthState.jsonDeserialize(serializedAuthState),
                        callback = object : AuthenticateCallback {
                            override fun success(authState: AuthState) {
                                activity?.runOnUiThread { successCallback(authState) }
                            }

                            override fun failed(rfc: Int) {
                                if (rfc == KeycloakTasks.RFC_INVALID_SIGNATURE) {
                                    App.toast(R.string.toast_message_invalid_link, android.widget.Toast.LENGTH_SHORT)
                                }
                            }
                        }
                    )
                } else if (cid != null) {
                    fragment?.authenticate(serializedAuthState, cid, csecret, object : AuthenticateCallback {
                        override fun success(authState: AuthState) {
                            successCallback(authState)
                        }

                        override fun failed(rfc: Int) {}
                    })
                }
            }
        }

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(R.string.activity_title_identity_provider),
            ),
            onBack = clearViewModelAndGoBack,
            onClose = onClose,
        ) {
            loadingTextRes?.let { res ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colorResource(R.color.olvid_gradient_light),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(res),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                }
            }

            LaunchedEffect(clientSecret.value) {
                onboardingViewModel.keycloakClientSecret = clientSecret.value
            }

            val validButtonLabel = stringResource(
                if (onboardingViewModel.keycloakMagic != null) R.string.button_label_use_magic_link
                else R.string.button_label_authenticate
            )

            if (!detailsShown && status == ValidatedStatus.VALID) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OlvidTextButton(
                        icon = R.drawable.ic_chevron_down,
                        text = stringResource(R.string.button_label_see_details),
                        onClick = { detailsShown = true },
                    )
                }
                Icon(
                    modifier = Modifier
                        .size(96.dp)
                        .background(color = colorResource(if (mdm) R.color.orange else R.color.green), shape = CircleShape)
                        .padding(16.dp),
                    painter = painterResource(if (mdm) R.drawable.ic_organization else R.drawable.ic_check_thick),
                    tint =  colorResource(R.color.alwaysWhite),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text =
                        when {
                            mdm -> stringResource(R.string.text_explanation_onboarding_keycloak_mdm, validButtonLabel)
                            onboardingViewModel.keycloakMagic != null -> stringResource(R.string.text_explanation_onboarding_keycloak_parsed_configuration_magic)
                            else -> stringResource(R.string.text_explanation_onboarding_keycloak_parsed_configuration)
                        }
                    ,
                    textAlign = TextAlign.Center,
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint),
                )
            } else {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(
                        if (mdm) R.string.explanation_identity_provider_mdm
                        else R.string.explanation_identity_provider
                    ),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint),
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (detailsShown) {
                    KeycloakDetailsFields(
                        mdm = mdm,
                        status = status,
                        server = server,
                        onServerChange = {
                            server = it
                            onboardingViewModel.keycloakServer = it
                        },
                        clientId = clientId,
                        onClientIdChange = {
                            clientId = it
                            onboardingViewModel.keycloakClientId = it
                        },
                        clientSecret = clientSecret,
                    )
                } else {
                    // deep-linked discovery in progress: only the status indicator, details stay hidden
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ValidationStatusIndicator(status)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if (status == ValidatedStatus.VALID) {
                    OlvidActionButton(
                        modifier = Modifier.weight(1f),
                        text = validButtonLabel,
                        onClick = { authenticate() },
                    )
                } else {
                    OlvidActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.button_label_check_identity_provider),
                        enabled = status != ValidatedStatus.CHECKING && server.isNotEmpty(),
                        onClick = { validateKeycloakServer() },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // hosts the spinner overlay + performs the OIDC authentication
            Box(modifier = Modifier.heightIn(min = 96.dp)) {
                AndroidFragment(
                    modifier = Modifier.fillMaxWidth(),
                    clazz = KeycloakAuthenticationStartFragment::class.java
                ) { frag ->
                    fragment = frag
                    if (!autoStarted && onboardingViewModel.isDeepLinked && onboardingViewModel.keycloakServer != null) {
                        autoStarted = true
                        validateKeycloakServer()
                    }
                }
            }
        }
    }
}

/** Server / client id / client secret fields of the identity provider configuration. */
@Composable
private fun KeycloakDetailsFields(
    mdm: Boolean,
    status: ValidatedStatus?,
    server: String,
    onServerChange: (String) -> Unit,
    clientId: String,
    onClientIdChange: (String) -> Unit,
    clientSecret: MutableState<String>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = server,
            shape = RoundedCornerShape(12.dp),
            enabled = !mdm,
            onValueChange = onServerChange,
            trailingIcon = {
                ValidationStatusIndicator(status)
            },
            label = { Text(stringResource(R.string.hint_identity_provider_server)) },
            singleLine = true,
            colors = olvidDefaultTextFieldColors(),
            textStyle = OlvidTypography.body1,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = clientId,
            shape = RoundedCornerShape(12.dp),
            enabled = !mdm,
            onValueChange = onClientIdChange,
            label = { Text(stringResource(R.string.hint_identity_provider_client_id)) },
            singleLine = true,
            colors = olvidDefaultTextFieldColors(),
            textStyle = OlvidTypography.body1,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OlvidPasswordInput(
            modifier = Modifier.fillMaxWidth(),
            password = clientSecret,
            enabled = !mdm,
            label = stringResource(R.string.hint_identity_provider_client_secret),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
        )
    }
}
