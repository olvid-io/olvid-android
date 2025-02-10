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

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakTasks
import net.openid.appauth.AuthState
import org.jose4j.jwk.JsonWebKeySet
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
fun NavGraphBuilder.targetKeycloakAuthenticationRequired(
    onboardingFlowViewModel : OnboardingFlowViewModel,
    onAuthenticated: (authState: AuthState, authenticationProof: String) -> Unit, // will not be called on ui thread
    onClose: () -> Unit) {
    composable(
        OnboardingRoutes.TRANSFER_TARGET_KEYCLOAK_AUTHENTICATION_PROOF_REQUIRED,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        var fragment: KeycloakAuthenticationStartFragment? by remember { mutableStateOf(null) }
        var clicked: Boolean by remember { mutableStateOf(false) }
        var errorMessage: Int? by remember { mutableStateOf(null) }

        OnboardingScreen(
            step = OnboardingStep(title = stringResource(id = R.string.onboarding_authentication_required_title)),
            onClose = onClose
        ) {
            Box(
                modifier = Modifier.heightIn(min = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidFragment(
                    modifier = Modifier.fillMaxWidth(),
                    clazz = KeycloakAuthenticationStartFragment::class.java
                ) { frag ->
                    fragment = frag
                }

                androidx.compose.animation.AnimatedVisibility(visible = !clicked) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(),
                        text = stringResource(R.string.onboarding_transfer_keycloak_authentication_required),
                        style = OlvidTypography.body1.copy(
                            color = colorResource(R.color.greyTint)
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        text = stringResource(it),
                        style = OlvidTypography.body1.copy(
                            color = colorResource(R.color.red)
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f, true),
                    elevation = null,
                    enabled = !clicked,
                    onClick = {
                        clicked = true
                        errorMessage = null
                        onboardingFlowViewModel.keycloakServerUrl?.let { serverUrl ->
                            onboardingFlowViewModel.keycloakClientId?.let { clientId ->
                                onboardingFlowViewModel.keycloakSas?.let { sas ->
                                    onboardingFlowViewModel.keycloakSessionNumber?.let { sessionNumber ->
                                        KeycloakTasks.discoverKeycloakServer(
                                            serverUrl,
                                            object : KeycloakTasks.DiscoverKeycloakServerCallback {
                                                override fun success(
                                                    serverUrl: String,
                                                    discoveryAuthState: AuthState,
                                                    jwks: JsonWebKeySet
                                                ) {
                                                    Handler(Looper.getMainLooper()).run {
                                                        fragment?.authenticate(
                                                            discoveryAuthState.jsonSerializeString(),
                                                            clientId,
                                                            onboardingFlowViewModel.keycloakClientSecret,
                                                            object :
                                                                KeycloakTasks.AuthenticateCallback {
                                                                override fun success(authState: AuthState) {
                                                                    KeycloakTasks.getAuthenticationProof(
                                                                        serverUrl,
                                                                        authState,
                                                                        sas,
                                                                        String.format(
                                                                            Locale.ENGLISH,
                                                                            "%08d",
                                                                            sessionNumber
                                                                        ),
                                                                        object :
                                                                            KeycloakManager.KeycloakCallback<String> {
                                                                            override fun success(
                                                                                authenticationProof: String
                                                                            ) {
                                                                                onAuthenticated(
                                                                                    authState,
                                                                                    authenticationProof
                                                                                )
                                                                                clicked = false
                                                                            }

                                                                            override fun failed(rfc: Int) {
                                                                                errorMessage =
                                                                                    R.string.onboarding_transfer_keycloak_error_message_authentication_proof_error
                                                                                clicked = false
                                                                            }
                                                                        })
                                                                }

                                                                override fun failed(rfc: Int) {
                                                                    errorMessage =
                                                                        R.string.onboarding_transfer_keycloak_error_message_authentication_failed
                                                                    clicked = false
                                                                }
                                                            }
                                                        )
                                                    }
                                                }

                                                override fun failed() {
                                                    errorMessage =
                                                        R.string.onboarding_transfer_keycloak_error_message_unreachable
                                                    clicked = false
                                                }
                                            })
                                    }
                                }
                            }
                        }
                    },
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.button_label_authenticate),
                        textAlign = TextAlign.Center
                    )
                }

                val context = LocalContext.current
                Button(elevation = null,
                    enabled = !clicked,
                    onClick = {
                        KeycloakBrowserChooserDialog.openBrowserChoiceDialog(context)
                    }
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.content_description_authentication_browser_choice)
                    )
                }
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
            startDestination = OnboardingRoutes.TRANSFER_TARGET_KEYCLOAK_AUTHENTICATION_PROOF_REQUIRED
        ) {
            targetKeycloakAuthenticationRequired(
                onboardingFlowViewModel = OnboardingFlowViewModel(),
                onAuthenticated = {a, b -> },
                onClose = {}
            )
        }
    }
}