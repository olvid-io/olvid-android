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
package io.olvid.messenger.openid

import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import io.olvid.engine.Logger
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.customClasses.NoExceptionConnectionBuilder
import io.olvid.messenger.settings.SettingsActivity.Companion.preferredKeycloakBrowser
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.browser.BrowserDescriptor
import net.openid.appauth.browser.BrowserSelector
import java.util.UUID


class KeycloakAuthenticationActivity : AppCompatActivity() {
    private lateinit var authorizationService: AuthorizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appAuthConfigBuilder = AppAuthConfiguration.Builder()
            .setConnectionBuilder(NoExceptionConnectionBuilder())

        val preferredBrowser = preferredKeycloakBrowser
        if (preferredBrowser != null) {
            var found = false
            try {
                // first check that the chosen preferred browser is still available
                for (browserDescriptor in BrowserSelector.getAllBrowsers(this)) {
                    if (preferredBrowser == browserDescriptor.packageName) {
                        found = true
                        break
                    }
                }
            } catch (_: Exception) {
                // default to using the preferredBrowser in case our check failed
                found = true
            }

            if (found) {
                appAuthConfigBuilder.setBrowserMatcher { descriptor: BrowserDescriptor -> descriptor.packageName == preferredBrowser }
            }
        }

        authorizationService = AuthorizationService(this, appAuthConfigBuilder.build())
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        authorizationService.dispose()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action == null) {
            finish()
            return
        }

        when (intent.action) {
            AUTHENTICATE_ACTION -> {
                val serializedAuthState = intent.getStringExtra(AUTH_STATE_JSON_INTENT_EXTRA)
                val clientId = intent.getStringExtra(CLIENT_ID_INTENT_EXTRA)
                val clientSecret = intent.getStringExtra(CLIENT_SECRET_INTENT_EXTRA)
                if (serializedAuthState != null && clientId != null) {
                    try {
                        val authState = AuthState.jsonDeserialize(serializedAuthState)
                        authState.getAuthorizationServiceConfiguration()?.let { configuration ->
                            val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
                            val nonce = Logger.getUuidString(UUID.randomUUID())
                            val authorizationRequestBuilder = AuthorizationRequest.Builder(
                                configuration,
                                clientId,
                                ResponseTypeValues.CODE,
                                BuildConfig.KEYCLOAK_REDIRECT_URL.toUri()
                            )
                            authorizationRequestBuilder.setScope("openid")
                            val authorizationRequest = authorizationRequestBuilder
                                .setPrompt("login consent")
                                .setNonce(nonce)
                                .setCodeVerifier(codeVerifier)
                                .build()


                            val successIntent =
                                Intent(this, KeycloakAuthenticationActivity::class.java)
                            successIntent.action = AUTHORIZATION_COMPLETE_ACTION
                            successIntent.putExtra(
                                AUTH_STATE_JSON_INTENT_EXTRA,
                                serializedAuthState
                            )
                            successIntent.putExtra(CLIENT_ID_INTENT_EXTRA, clientId)
                            if (clientSecret != null) {
                                successIntent.putExtra(CLIENT_SECRET_INTENT_EXTRA, clientSecret)
                            }
                            successIntent.putExtra(CODE_VERIFIER_INTENT_EXTRA, codeVerifier)
                            successIntent.putExtra(NONCE_INTENT_EXTRA, nonce)
                            val successPendingIntent = PendingIntent.getActivity(
                                this,
                                3,
                                successIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                            )

                            val cancelledIntent =
                                Intent(this, KeycloakAuthenticationActivity::class.java)
                            cancelledIntent.action = AUTHORIZATION_CANCELLED_ACTION
                            val cancelledPendingIntent = PendingIntent.getActivity(
                                this,
                                4,
                                cancelledIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                            )


                            authorizationService.performAuthorizationRequest(
                                authorizationRequest,
                                successPendingIntent,
                                cancelledPendingIntent
                            )
                            return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Logger.d("Error parsing serialized AuthState : authentication start")
                    }
                }
                finish()
            }

            AUTHORIZATION_COMPLETE_ACTION -> {
                val authorizationResponse = AuthorizationResponse.fromIntent(intent)
                val authorizationException = AuthorizationException.fromIntent(intent)
                val serializedAuthState = intent.getStringExtra(AUTH_STATE_JSON_INTENT_EXTRA)
                val clientId = intent.getStringExtra(CLIENT_ID_INTENT_EXTRA)
                val clientSecret = intent.getStringExtra(CLIENT_SECRET_INTENT_EXTRA)
                val codeVerifier = intent.getStringExtra(CODE_VERIFIER_INTENT_EXTRA)
                val nonce = intent.getStringExtra(NONCE_INTENT_EXTRA)

                if (serializedAuthState != null && codeVerifier != null && clientId != null) {
                    try {
                        val authState = AuthState.jsonDeserialize(serializedAuthState)
                        authState.update(authorizationResponse, authorizationException)
                        if (authState.lastAuthorizationResponse != null && authState.getAuthorizationServiceConfiguration() != null) {
                            // openid authentication successful, let's retrieve the authentication token
                            val additionalParameters = HashMap<String?, String?>()
                            if (clientSecret != null) {
                                additionalParameters.put("client_secret", clientSecret)
                            }

                            val tokenRequestBuilder = TokenRequest.Builder(
                                authState.getAuthorizationServiceConfiguration()!!,
                                clientId
                            )
                                .setCodeVerifier(codeVerifier)
                                .setNonce(nonce)
                                .setAuthorizationCode(authState.lastAuthorizationResponse!!.authorizationCode)
                                .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
                                .setAdditionalParameters(additionalParameters)
                                .setRedirectUri(BuildConfig.KEYCLOAK_REDIRECT_URL.toUri())

                            authorizationService.performTokenRequest(
                                /* request = */ tokenRequestBuilder.build()
                            )
                            /* callback = */ { tokenResponse: TokenResponse?, ex: AuthorizationException? ->
                                if (tokenResponse != null) {
                                    authState.update(tokenResponse, ex)
                                    val resultIntent =
                                        Intent(this, KeycloakAuthenticationActivity::class.java)
                                    resultIntent.putExtra(
                                        AUTH_STATE_JSON_INTENT_EXTRA,
                                        authState.jsonSerializeString()
                                    )
                                    setResult(RESULT_OK, resultIntent)
                                } else if (ex != null) {
                                    ex.printStackTrace()
                                    if (ex.code == AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR.code) {
                                        // an error occurred while validating the received ID token, this is probably the phone's clock that is off
                                        setResult(RESULT_CODE_TIME_OFFSET)
                                    }
                                }
                                finish()
                            }
                            // break to avoid finishing now
                            return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Logger.d("Error parsing serialized AuthState : authorization complete")
                    }
                }
                finish()
            }

            AUTHORIZATION_CANCELLED_ACTION -> {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        const val AUTHENTICATE_ACTION: String = "authenticate_action"
        const val AUTHORIZATION_COMPLETE_ACTION: String = "authorization_complete_action"
        const val AUTHORIZATION_CANCELLED_ACTION: String = "authorization_cancelled_action"

        const val AUTH_STATE_JSON_INTENT_EXTRA: String = "auth_state_json"
        const val CLIENT_ID_INTENT_EXTRA: String = "client_id"
        const val CLIENT_SECRET_INTENT_EXTRA: String = "client_secret"
        const val CODE_VERIFIER_INTENT_EXTRA: String = "code_verifier"
        const val NONCE_INTENT_EXTRA: String = "nonce"

        const val RESULT_CODE_TIME_OFFSET: Int = 187
    }
}
