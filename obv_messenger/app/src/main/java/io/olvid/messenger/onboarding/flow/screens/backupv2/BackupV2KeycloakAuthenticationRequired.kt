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

package io.olvid.messenger.onboarding.flow.screens.backupv2

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.KeycloakInfo
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog
import io.olvid.messenger.openid.KeycloakTasks
import net.openid.appauth.AuthState
import org.jose4j.jwk.JsonWebKeySet


fun NavGraphBuilder.backupV2KeycloakAuthenticationRequired(
    keycloakInfo: State<KeycloakInfo?>,
    onAuthenticationSuccess: (AuthState) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_V2_KEYCLOAK_AUTHENTICATION_REQUIRED,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        var fragment: KeycloakAuthenticationStartFragment? by remember { mutableStateOf(null) }
        var clicked: Boolean by remember { mutableStateOf(false) }
        var errorMessage: Int? by remember { mutableStateOf(null) }

        // this never happens as this screen is only loaded when keycloakInfo is available
        LaunchedEffect(keycloakInfo.value) {
            if (keycloakInfo.value == null) {
                onBack.invoke()
            }
        }

        OnboardingScreen(
            step = OnboardingStep(title = stringResource(id = R.string.onboarding_authentication_required_title)),
            onBack = onBack,
            onClose = onClose
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .heightIn(min = 120.dp),
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
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f, true),
                    elevation = null,
                    enabled = !clicked,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.olvid_gradient_light),
                        contentColor = colorResource(R.color.alwaysWhite),
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
                    onClick = {
                        clicked = true
                        errorMessage = null
                        keycloakInfo.value?.let { info ->
                            KeycloakTasks.discoverKeycloakServer(
                                info.serverUrl,
                                object : KeycloakTasks.DiscoverKeycloakServerCallback {
                                    override fun success(
                                        serverUrl: String,
                                        discoveryAuthState: AuthState,
                                        jwks: JsonWebKeySet
                                    ) {
                                        Handler(Looper.getMainLooper()).run {
                                            fragment?.authenticate(
                                                discoveryAuthState.jsonSerializeString(),
                                                info.clientId,
                                                info.clientSecret,
                                                object :
                                                    KeycloakTasks.AuthenticateCallback {
                                                    override fun success(authState: AuthState) {
                                                        onAuthenticationSuccess.invoke(authState)
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
                    },
                ) {
                    Text(
                        text = stringResource(id = R.string.button_label_authenticate),
                    )
                }

                val context = LocalContext.current
                Button(
                    elevation = null,
                    enabled = !clicked,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.greyTint)
                    ),
                    contentPadding = PaddingValues(10.dp),
                    onClick = {
                        KeycloakBrowserChooserDialog.openBrowserChoiceDialog(context)
                    }
                ) {
                    Image(
                        modifier = Modifier.size(32.dp),
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
fun KeycloakAuthenticationRequiredPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.BACKUP_V2_KEYCLOAK_AUTHENTICATION_REQUIRED,
    ) {
        backupV2KeycloakAuthenticationRequired(mutableStateOf(KeycloakInfo("", "", null)), {}, {},{})
    }
}