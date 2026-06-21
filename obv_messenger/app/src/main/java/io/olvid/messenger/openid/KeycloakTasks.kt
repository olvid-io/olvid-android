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
package io.olvid.messenger.openid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.net.toUri
import androidx.core.util.Pair
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.fasterxml.jackson.core.type.TypeReference
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.engine.engine.types.ObvKeycloakIdBasedAuthResult
import io.olvid.engine.engine.types.identities.ObvKeycloakAuthType
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.customClasses.ConfigurationPojo
import io.olvid.messenger.customClasses.NoExceptionConnectionBuilder
import io.olvid.messenger.customClasses.NoExceptionConnectionBuilder.Companion.downloadContent
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallbackWrapper
import io.olvid.messenger.openid.jsons.JsonGroupsRequest
import io.olvid.messenger.openid.jsons.JsonGroupsResponse
import io.olvid.messenger.openid.jsons.JsonMagicResponse
import io.olvid.messenger.openid.jsons.JsonMeRequest
import io.olvid.messenger.openid.jsons.JsonMeResponse
import io.olvid.messenger.openid.jsons.JsonSearchRequest
import io.olvid.messenger.openid.jsons.JsonSearchResponse
import io.olvid.messenger.openid.jsons.JsonTransferProofRequest
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import io.olvid.messenger.openid.jsons.OlvidWellKnownJson
import io.olvid.messenger.services.MDMConfigurationSingleton
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthState.AuthStateAction
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jwk.VerificationJwkSelector
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver
import org.jose4j.lang.JoseException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection


object KeycloakTasks {
    private const val ME_PATH = "olvid-rest/me"
    private const val PUT_KEY_PATH = "olvid-rest/putKey"
    private const val GET_KEY_PATH = "olvid-rest/getKey"
    private const val SEARCH_PATH = "olvid-rest/search"
    private const val REVOCATION_TEST_PATH = "olvid-rest/revocationTest"
    private const val GROUPS_PATH = "olvid-rest/groups"
    private const val TRANSFER_PROOF = "olvid-rest/transferProof"
    private const val MAGIC_PATH = "olvid-rest/getMagicSession"

    const val RFC_UNKNOWN_ERROR: Int = 0
    const val RFC_INVALID_AUTH_STATE: Int = 1
    const val RFC_AUTHENTICATION_REQUIRED: Int = 2
    const val RFC_NETWORK_ERROR: Int = 3
    const val RFC_BAD_RESPONSE: Int = 4
    const val RFC_INVALID_SIGNATURE: Int = 5
    const val RFC_SERVER_ERROR: Int = 6
    const val RFC_IDENTITY_NOT_MANAGED: Int = 7

    const val RFC_AUTHENTICATION_CANCELLED: Int = 8
    const val RFC_USER_NOT_AUTHENTICATED: Int = 9
    const val RFC_IDENTITY_ALREADY_UPLOADED: Int = 10
    const val RFC_IDENTITY_REVOKED: Int = 11
    const val RFC_AUTHENTICATION_ERROR_TIME_OFFSET: Int = 12


    const val ERROR_CODE_INTERNAL_ERROR: Int = 1
    const val ERROR_CODE_PERMISSION_DENIED: Int = 2
    const val ERROR_CODE_INVALID_REQUEST: Int = 3
    const val ERROR_CODE_IDENTITY_ALREADY_UPLOADED: Int = 4
    const val ERROR_CODE_IDENTITY_WAS_REVOKED: Int = 6


    @JvmStatic
    fun discoverKeycloakServerOpenidConfiguration(
        keycloakServerUrl: String,
        callback: DiscoverKeycloakServerCallback
    ) {
        var keycloakServerUrl = keycloakServerUrl
        Logger.d("Discovering keycloak server at $keycloakServerUrl")

        keycloakServerUrl = keycloakServerUrl.trim { it <= ' ' }
        if (!keycloakServerUrl.endsWith("/")) {
            keycloakServerUrl += "/"
        }
        val finalServerUrl = keycloakServerUrl
        AuthorizationServiceConfiguration.fetchFromUrl(
            ("$keycloakServerUrl.well-known/openid-configuration").toUri(),
            { serviceConfiguration: AuthorizationServiceConfiguration?, ex: AuthorizationException? ->
                App.runThread {
                    if (ex == null && serviceConfiguration != null && serviceConfiguration.discoveryDoc != null) {
                        val authState = AuthState(serviceConfiguration)

                        var jwks: JsonWebKeySet?
                        try {
                            val jwksString =
                                getJwks(serviceConfiguration.discoveryDoc!!.jwksUri)
                            jwks = JsonWebKeySet(jwksString)
                        } catch (_: JoseException) {
                            jwks = null
                        }
                        if (jwks != null) {
                            val olvidWellKnown = discoverKeycloakServerOlvidWellKnown(keycloakServerUrl)

                            callback.success(finalServerUrl, authState, jwks, olvidWellKnown)
                        } else {
                            callback.failed()
                        }
                        return@runThread
                    }
                    ex?.printStackTrace()
                    callback.failed()
                }
            },
            NoExceptionConnectionBuilder()
        )
    }

    fun discoverKeycloakServerOlvidWellKnown(keycloakServerUrl: String): OlvidWellKnownJson? {
        Logger.d("Fetching Keycloak Olvid well-known at: $keycloakServerUrl")

        var keycloakServerUrl = keycloakServerUrl.trim { it <= ' ' }
        if (!keycloakServerUrl.endsWith("/")) {
            keycloakServerUrl += "/"
        }

        val result = "$keycloakServerUrl.well-known/olvid".toUri().downloadContent()
        if (result is NoExceptionConnectionBuilder.DownloadResult.Success) {
            return AppSingleton.getJsonObjectMapper().readValue(result.output, OlvidWellKnownJson::class.java)
        }
        return null
    }

    fun useMagicLink(
        keycloakServerUrl: String,
        keycloakMagic: ConfigurationPojo.KeycloakMagic,
        authState: AuthState,
        callback: AuthenticateCallback
    ) {
        Logger.d("Using magic link")

        App.runThread {
            val dataToSend = AppSingleton.getJsonObjectMapper().writeValueAsBytes(keycloakMagic)

            val bytes = keycloakApiRequest(
                keycloakServerUrl,
                MAGIC_PATH,
                null,
                dataToSend
            )

            val output = AppSingleton.getJsonObjectMapper()
                .readValue<MutableMap<String?, Any?>>(
                    bytes,
                    object : TypeReference<MutableMap<String?, Any?>?>() {})
            val error = output["error"] as Int?
            if (error != null) {
                callback.failed(RFC_INVALID_SIGNATURE)
                return@runThread
            }

            val response = AppSingleton.getJsonObjectMapper()
                .readValue(bytes, JsonMagicResponse::class.java)

            response.accessToken?.let {
                authState.initializeFromMagicLinkOrIdBasedAuthResponse(it, response.refreshToken, response.clientId, response.clientSecret)
                callback.success(authState)
            } ?: run {
                callback.failed(RFC_INVALID_SIGNATURE)
            }
        }
    }



    fun getOwnDetails(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        supportedAuthenticationMethods: List<ObvKeycloakAuthType>,
        bytesOwnedIdentity: ByteArray?,
        jwks: JsonWebKeySet,
        latestRevocationListTimestamp: Long?,
        callback: KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?>
    ) {
        Logger.d("Fetching keycloak own details")

        val authorizationService = AuthorizationService(
            context, AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )

        authState.performActionWithFreshTokens(
            bytesOwnedIdentity,
            authorizationService,
            supportedAuthenticationMethods
        ) { accessToken: String?, _: String?, ex: AuthorizationException? ->
            authorizationService.dispose()
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace()
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR)
                        return@performActionWithFreshTokens
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED)
            } else {
                App.runThread {
                    try {
                        val dataToSend: ByteArray? = if (latestRevocationListTimestamp == null) {
                            null
                        } else {
                            AppSingleton.getJsonObjectMapper()
                                .writeValueAsBytes(JsonMeRequest(latestRevocationListTimestamp))
                        }

                        val bytes = keycloakApiRequest(
                            keycloakServerUrl,
                            ME_PATH,
                            accessToken,
                            dataToSend
                        )

                        val output = AppSingleton.getJsonObjectMapper()
                            .readValue<MutableMap<String?, Any?>>(
                                bytes,
                                object : TypeReference<MutableMap<String?, Any?>?>() {})
                        val error = output["error"] as Int?
                        if (error != null) {
                            if (error == ERROR_CODE_PERMISSION_DENIED) {
                                callback.failed(RFC_AUTHENTICATION_REQUIRED)
                            } else {
                                callback.failed(RFC_SERVER_ERROR)
                            }
                            return@runThread
                        }
                        val response = AppSingleton.getJsonObjectMapper()
                            .readValue(bytes, JsonMeResponse::class.java)
                        response.signature?.let { signature ->
                            val detailsJsonStringAndSignatureKey =
                                verifySignature(signature, jwks, null)

                            if (detailsJsonStringAndSignatureKey != null) {
                                // signature verification successful, but the signing key might not be the right one --> this is checked in the success callback
                                val userDetails = AppSingleton.getJsonObjectMapper()
                                    .readValue(
                                        detailsJsonStringAndSignatureKey.first,
                                        JsonKeycloakUserDetails::class.java
                                    )
                                callback.success(
                                    Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>(
                                        KeycloakUserDetailsAndStuff(
                                            userDetails,
                                            signature,
                                            detailsJsonStringAndSignatureKey.second,
                                            response.server,
                                            response.apiKey,
                                            response.pushTopics,
                                            response.selfRevocationTestNonce
                                        ),
                                        KeycloakServerRevocationsAndStuff(
                                            response.revocationAllowed != null && response.revocationAllowed == true,
                                            response.transferRestricted != null && response.transferRestricted == true,
                                            response.currentTimestamp,
                                            response.signedRevocations,
                                            response.minimumBuildVersions
                                        )
                                    )
                                )
                            } else {
                                callback.failed(RFC_INVALID_SIGNATURE)
                            }

                        } ?: run {
                            callback.failed(RFC_BAD_RESPONSE)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        callback.failed(RFC_NETWORK_ERROR)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.failed(RFC_UNKNOWN_ERROR)
                    }
                }
            }
        }
    }


    fun uploadOwnIdentity(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        supportedAuthenticationMethods: List<ObvKeycloakAuthType>,
        bytesOwnedIdentity: ByteArray,
        callback: KeycloakCallback<Void?>
    ) {
        Logger.d("Uploading Olvid ID to keycloak")

        val authorizationService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )

        authState.performActionWithFreshTokens(
            bytesOwnedIdentity,
            authorizationService,
            supportedAuthenticationMethods
        ) { accessToken: String?, _: String?, ex: AuthorizationException? ->
            authorizationService.dispose()
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace()
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR)
                        return@performActionWithFreshTokens
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED)
            } else {
                App.runThread {
                    try {
                        val input: MutableMap<String?, Any?> = HashMap()
                        input["identity"] = bytesOwnedIdentity

                        val bytes = keycloakApiRequest(
                            keycloakServerUrl,
                            PUT_KEY_PATH,
                            accessToken,
                            AppSingleton.getJsonObjectMapper().writeValueAsBytes(input)
                        )

                        val output = AppSingleton.getJsonObjectMapper()
                            .readValue<MutableMap<String?, Any?>>(
                                bytes,
                                object : TypeReference<MutableMap<String?, Any?>?>() {})
                        val error = output["error"] as Int?
                        if (error != null) {
                            when (error) {
                                ERROR_CODE_INTERNAL_ERROR, ERROR_CODE_INVALID_REQUEST, ERROR_CODE_PERMISSION_DENIED -> callback.failed(
                                    RFC_SERVER_ERROR
                                )

                                ERROR_CODE_IDENTITY_ALREADY_UPLOADED -> callback.failed(
                                    RFC_IDENTITY_ALREADY_UPLOADED
                                )

                                ERROR_CODE_IDENTITY_WAS_REVOKED -> callback.failed(
                                    RFC_IDENTITY_REVOKED
                                )
                            }
                        } else {
                            callback.success(null)
                        }
                    } catch (_: IOException) {
                        callback.failed(RFC_NETWORK_ERROR)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.failed(RFC_UNKNOWN_ERROR)
                    }
                }
            }
        }
    }


    // the server groups download timestamp is returned in the callback
    fun getGroups(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        supportedAuthenticationMethods: List<ObvKeycloakAuthType>,
        bytesOwnedIdentity: ByteArray,
        latestGetGroupsTimestamp: Long?,
        callback: KeycloakCallbackWrapper<Long?>
    ) {
        Logger.d("Fetching keycloak groups")

        val authorizationService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )

        authState.performActionWithFreshTokens(
            bytesOwnedIdentity,
            authorizationService,
            supportedAuthenticationMethods
        ) { accessToken: String?, _: String?, ex: AuthorizationException? ->
            authorizationService.dispose()
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace()
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR)
                        return@performActionWithFreshTokens
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED)
            } else {
                App.runThread {
                    try {
                        val request = JsonGroupsRequest(latestGetGroupsTimestamp)
                        val bytes: ByteArray?
                        try {
                            bytes = keycloakApiRequest(
                                keycloakServerUrl,
                                GROUPS_PATH,
                                accessToken,
                                AppSingleton.getJsonObjectMapper().writeValueAsBytes(request)
                            )
                        } catch (_: IOException) {
                            callback.failed(RFC_NETWORK_ERROR)
                            return@runThread
                        }

                        val output = AppSingleton.getJsonObjectMapper()
                            .readValue<MutableMap<String?, Any?>>(
                                bytes,
                                object : TypeReference<MutableMap<String?, Any?>?>() {})
                        val error = output["error"] as Int?
                        if (error != null) {
                            callback.failed(RFC_SERVER_ERROR)
                        } else {
                            val response = AppSingleton.getJsonObjectMapper()
                                .readValue(
                                    bytes,
                                    JsonGroupsResponse::class.java
                                )
                            if (AppSingleton.getEngine().updateKeycloakGroups(
                                    bytesOwnedIdentity,
                                    response.signedGroupBlobs,
                                    response.signedGroupDeletions,
                                    response.signedGroupKicks,
                                    response.currentTimestamp
                                )
                            ) {
                                callback.success(response.currentTimestamp)
                            } else {
                                callback.failed(RFC_UNKNOWN_ERROR)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.failed(RFC_UNKNOWN_ERROR)
                    }
                }
            }
        }
    }


    fun search(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        supportedAuthenticationMethods: List<ObvKeycloakAuthType>,
        bytesOwnedIdentity: ByteArray,
        searchQuery: String?,
        callback: KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Int>?>
    ) {
        Logger.d("Performing search on keycloak")

        val authorizationService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )

        authState.performActionWithFreshTokens(
            bytesOwnedIdentity,
            authorizationService,
            supportedAuthenticationMethods
        ) { accessToken: String?, _: String?, ex: AuthorizationException? ->
            authorizationService.dispose()
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace()
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR)
                        return@performActionWithFreshTokens
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED)
            } else {
                App.runThread {
                    try {
                        val query = JsonSearchRequest()
                        if (searchQuery.isNullOrBlank()) {
                            query.filter = null
                        } else {
                            query.filter =
                                searchQuery.trim { it <= ' ' }.split("\\s+".toRegex())
                                    .dropLastWhile { it.isEmpty() }.toTypedArray()
                        }

                        val bytes = keycloakApiRequest(
                            keycloakServerUrl,
                            SEARCH_PATH,
                            accessToken,
                            AppSingleton.getJsonObjectMapper().writeValueAsBytes(query)
                        )

                        try {
                            val output = AppSingleton.getJsonObjectMapper()
                                .readValue<MutableMap<String?, Any?>>(
                                    bytes,
                                    object : TypeReference<MutableMap<String?, Any?>?>() {})
                            val error = output["error"] as Int?
                            if (error != null) {
                                when (error) {
                                    ERROR_CODE_PERMISSION_DENIED -> callback.failed(
                                        RFC_AUTHENTICATION_REQUIRED
                                    )

                                    ERROR_CODE_INTERNAL_ERROR -> callback.failed(
                                        RFC_SERVER_ERROR
                                    )

                                    else -> callback.failed(RFC_SERVER_ERROR)
                                }
                                return@runThread
                            }
                        } catch (_: Exception) {
                            // an exception is normal if there was no error in the server response
                        }
                        val response = AppSingleton.getJsonObjectMapper()
                            .readValue(
                                bytes,
                                JsonSearchResponse::class.java
                            )
                        callback.success(
                            Pair<List<JsonKeycloakUserDetails>, Int>(
                                response.results,
                                response.count
                            )
                        )
                    } catch (_: IOException) {
                        callback.failed(RFC_NETWORK_ERROR)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.failed(RFC_UNKNOWN_ERROR)
                    }
                }
            }
        }
    }


    fun addContact(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        supportedAuthenticationMethods: List<ObvKeycloakAuthType>,
        jwks: JsonWebKeySet,
        signatureKey: JsonWebKey?,
        bytesOwnedIdentity: ByteArray,
        contactUserId: String?,
        bytesContactIdentity: ByteArray?,
        callback: KeycloakCallback<Void?>
    ) {
        Logger.d("Retrieving signed contact details")

        val authorizationService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )

        authState.performActionWithFreshTokens(
            bytesOwnedIdentity,
            authorizationService,
            supportedAuthenticationMethods
        ) { accessToken: String?, _: String?, ex: AuthorizationException? ->
            authorizationService.dispose()
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace()
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR)
                        return@performActionWithFreshTokens
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED)
            } else {
                App.runThread {
                    try {
                        val query: MutableMap<String?, String?> = HashMap()
                        query["user-id"] = contactUserId

                        val bytes = keycloakApiRequest(
                            keycloakServerUrl,
                            GET_KEY_PATH,
                            accessToken,
                            AppSingleton.getJsonObjectMapper().writeValueAsBytes(query)
                        )

                        val output = AppSingleton.getJsonObjectMapper()
                            .readValue<MutableMap<String?, Any?>>(
                                bytes,
                                object : TypeReference<MutableMap<String?, Any?>?>() {
                                })
                        val error = output["error"] as Int?
                        if (error != null) {
                            when (error) {
                                ERROR_CODE_PERMISSION_DENIED -> callback.failed(
                                    RFC_AUTHENTICATION_REQUIRED
                                )

                                ERROR_CODE_INTERNAL_ERROR, ERROR_CODE_INVALID_REQUEST -> callback.failed(
                                    RFC_SERVER_ERROR
                                )

                                else -> callback.failed(RFC_SERVER_ERROR)
                            }
                            return@runThread
                        }
                        val signature = output["signature"] as String?
                        if (signature != null) {
                            val detailsJsonStringAndSignatureKey =
                                verifySignature(signature, jwks, signatureKey)

                            if (detailsJsonStringAndSignatureKey != null) {
                                // signature verification successful, with the right signature key
                                val userDetails = AppSingleton.getJsonObjectMapper()
                                    .readValue(
                                        detailsJsonStringAndSignatureKey.first,
                                        JsonKeycloakUserDetails::class.java
                                    )

                                if (userDetails.getIdentity()
                                        .contentEquals(bytesContactIdentity)
                                ) {
                                    AppSingleton.getEngine().addKeycloakContact(
                                        bytesOwnedIdentity,
                                        bytesContactIdentity,
                                        signature
                                    )
                                    callback.success(null)
                                    return@runThread
                                }
                            } else if (verifySignature(signature, jwks, null) != null) {
                                // signature is valid but with the wrong signature key --> force a resync to detect key change and prompt user with standard dialog
                                KeycloakManager.forceSyncManagedIdentity(
                                    bytesOwnedIdentity
                                )
                            }
                        }
                        callback.failed(RFC_INVALID_SIGNATURE)
                    } catch (_: IOException) {
                        callback.failed(RFC_NETWORK_ERROR)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.failed(RFC_UNKNOWN_ERROR)
                    }
                }
            }
        }
    }


    // returns with success(true) if the user was revoked
    fun selfRevocationTest(
        keycloakServerUrl: String,
        selfRevocationTestNonce: String,
        callback: KeycloakCallback<Boolean?>
    ) {
        Logger.d("Checking keycloak for self revocation")

        App.runThread {
            try {
                val input: Map<String?, String?> = mapOf("nonce" to selfRevocationTestNonce)

                val bytes = keycloakApiRequest(
                    keycloakServerUrl,
                    REVOCATION_TEST_PATH,
                    null,
                    AppSingleton.getJsonObjectMapper().writeValueAsBytes(input)
                )

                callback.success(bytes != null && (bytes.size == 1) and (bytes[0].toInt() == 0x01))
            } catch (_: IOException) {
                callback.failed(RFC_NETWORK_ERROR)
            } catch (e: Exception) {
                e.printStackTrace()
                callback.failed(RFC_UNKNOWN_ERROR)
            }
        }
    }

    fun getAuthenticationProof(
        keycloakServerUrl: String,
        authState: AuthState,
        sas: String,
        sessionNumber: String,
        callback: KeycloakCallback<String?>
    ) {
        Logger.d("Fetching authentication proof")

        App.runThread {
            try {
                val request = JsonTransferProofRequest(sessionNumber, sas)
                val bytes: ByteArray?
                try {
                    bytes = keycloakApiRequest(
                        keycloakServerUrl,
                        TRANSFER_PROOF,
                        authState.getAccessToken(),
                        AppSingleton.getJsonObjectMapper().writeValueAsBytes(request)
                    )
                } catch (_: IOException) {
                    callback.failed(RFC_NETWORK_ERROR)
                    return@runThread
                }

                val output = AppSingleton.getJsonObjectMapper()
                    .readValue<MutableMap<String?, Any?>>(
                        bytes,
                        object : TypeReference<MutableMap<String?, Any?>?>() {})
                val error = output["error"] as Int?
                if (error != null) {
                    callback.failed(RFC_SERVER_ERROR)
                } else {
                    val signature = output["signature"] as String?
                    if (signature != null) {
                        callback.success(signature)
                    } else {
                        callback.failed(RFC_UNKNOWN_ERROR)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback.failed(RFC_UNKNOWN_ERROR)
            }
        }
    }

    @Throws(IOException::class)
    private fun keycloakApiRequest(
        keycloakServer: String,
        path: String,
        accessToken: String?,
        dataToSend: ByteArray?
    ): ByteArray? {
        val requestUrl = URL(keycloakServer + path)
        val connection = requestUrl.openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection && AppSingleton.getSslSocketFactory() != null) {
            connection.sslSocketFactory = AppSingleton.getSslSocketFactory()
        }
        val userAgentProperty = MDMConfigurationSingleton.getUserAgentOverride() ?: System.getProperty("http.agent")
        if (userAgentProperty != null) {
            connection.setRequestProperty("User-Agent", userAgentProperty)
        }
        try {
            connection.connectTimeout = 10000
            if (accessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (dataToSend == null) {
                connection.setDoOutput(false)
            } else {
                connection.setDoOutput(true)
                connection.setFixedLengthStreamingMode(dataToSend.size)
                connection.getOutputStream().use { os ->
                    os.write(dataToSend)
                }
            }

            val serverResponse = connection.getResponseCode()

            if (serverResponse == 200) {
                connection.getInputStream().use { `is` ->
                    BufferedInputStream(`is`).use { bis ->
                        ByteArrayOutputStream().use { byteArrayOutputStream ->
                            var numberOfBytesRead: Int
                            val buffer = ByteArray(32768)

                            while ((bis.read(buffer).also { numberOfBytesRead = it }) != -1) {
                                byteArrayOutputStream.write(buffer, 0, numberOfBytesRead)
                            }
                            byteArrayOutputStream.flush()
                            return byteArrayOutputStream.toByteArray()
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        return null
    }

    private fun verifySignature(
        signature: String,
        jwks: JsonWebKeySet,
        signatureKey: JsonWebKey?
    ): Pair<String?, JsonWebKey?>? {
        val jsonWebKeys = if (signatureKey == null) {
            jwks.jsonWebKeys
        } else {
            mutableListOf(signatureKey)
        }

        val jwksResolver = JwksVerificationKeyResolver(jsonWebKeys)
        val jwtConsumer = JwtConsumerBuilder()
            .setExpectedAudience(false)
            .setVerificationKeyResolver(jwksResolver)
            .build()
        try {
            val context = jwtConsumer.process(signature)
            val jsonWebKey = VerificationJwkSelector().select(
                context.joseObjects[0] as JsonWebSignature?, jsonWebKeys
            )
            return Pair(context.jwtClaims.rawJson, jsonWebKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }


    private fun getJwks(jwksUri: Uri?): String? {
        Logger.d("Fetching JWKS at: $jwksUri")
        return jwksUri?.downloadContent()?.let { result ->
            if (result is NoExceptionConnectionBuilder.DownloadResult.Success)
                String(result.output, StandardCharsets.UTF_8)
            else
                null
        }
    }


    internal class AuthenticationLifecycleObserver(activity: ComponentActivity) :
        DefaultLifecycleObserver {
        private val registry: ActivityResultRegistry = activity.activityResultRegistry
        var authenticationResultHandler: ActivityResultLauncher<Intent>? = null
        private var callback: AuthenticateCallback? = null

        fun setCallback(callback: AuthenticateCallback?) {
            this.callback = callback
        }

        override fun onCreate(owner: LifecycleOwner) {
            authenticationResultHandler = registry.register(
                "authentication",
                StartActivityForResult()
            ) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val intent = result.data
                    if (intent != null) {
                        val updatedSerializedAuthState =
                            intent.getStringExtra(KeycloakAuthenticationActivity.AUTH_STATE_JSON_INTENT_EXTRA)
                        if (updatedSerializedAuthState != null) {
                            try {
                                val authState =
                                    AuthState.jsonDeserialize(updatedSerializedAuthState)
                                if (authState.isAuthorized) {
                                    if (callback != null) {
                                        callback!!.success(authState)
                                    }
                                } else {
                                    Logger.d("User not authenticated")
                                    if (callback != null) {
                                        callback!!.failed(RFC_USER_NOT_AUTHENTICATED)
                                    }
                                }
                            } catch (_: Exception) {
                                Logger.d("Error parsing serialized AuthState: receive browser result")
                                if (callback != null) {
                                    callback!!.failed(RFC_INVALID_AUTH_STATE)
                                }
                            }
                            return@register
                        }
                    }
                } else if (result.resultCode == KeycloakAuthenticationActivity.RESULT_CODE_TIME_OFFSET) {
                    Logger.w("Keycloak authentication failed because of a phone time offset")
                    if (callback != null) {
                        callback!!.failed(RFC_AUTHENTICATION_ERROR_TIME_OFFSET)
                        return@register
                    }
                }
                Logger.d("Keycloak authentication cancelled by user")
                if (callback != null) {
                    callback!!.failed(RFC_AUTHENTICATION_CANCELLED)
                }
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            authenticationResultHandler?.unregister()
            authenticationResultHandler = null
        }
    }

    interface DiscoverKeycloakServerCallback {
        fun success(serverUrl: String, authState: AuthState, jwks: JsonWebKeySet, olvidWellKnown: OlvidWellKnownJson?)
        fun failed()
    }

    interface AuthenticateCallback {
        fun success(authState: AuthState)
        fun failed(rfc: Int)
    }
}

fun AuthState.initializeFromMagicLinkOrIdBasedAuthResponse(accessToken: String, refreshToken: String?, clientId: String?, clientSecret: String?) {
    val configuration = authorizationServiceConfiguration
    if (configuration == null) {
        Logger.e("Called initializeFromMagicLinkOrIdBasedAuthResponse() on an AuthState without an AuthorizationServiceConfiguration!")
        return
    }
    val accessTokenExpiration = runCatching {
        val jwtConsumer = JwtConsumerBuilder()
            .setSkipSignatureVerification()
            .setSkipAllValidators()
            .build()
        val context = jwtConsumer.process(accessToken)
        return@runCatching context.jwtClaims.expirationTime?.valueInMillis
    }.getOrNull()

    // if clientId is null, we will not be able to refresh the accessToken, so treat it as if no refreshToken was given
    if (refreshToken == null || clientId == null) {
        update(
            TokenResponse.Builder(
                TokenRequest.Builder(
                    configuration,
                    "fake"
                )
                    .setGrantType(GrantTypeValues.IMPLICIT)
                    .build()
            )
                .setAccessToken(accessToken)
                .setAccessTokenExpirationTime(accessTokenExpiration)
                .setTokenType(TokenResponse.TOKEN_TYPE_BEARER)
                .build(),
            null
        )
    } else {
        // we simulate a real OpenID Connect authentication with AUTHORIZATION_CODE
        // this way the refreshToken can properly be used
        update(
            AuthorizationResponse.Builder(
                AuthorizationRequest.Builder(
                    configuration,
                    clientId,
                    ResponseTypeValues.CODE,
                    BuildConfig.KEYCLOAK_REDIRECT_URL.toUri()
                )
                    .setScope("openid")
                    .build()
            ).build(),
            null
        )

        update(
            TokenResponse.Builder(
                TokenRequest.Builder(
                    configuration,
                    clientId
                )
                    // if a clientSecret was provided, add it here
                    .setAdditionalParameters(clientSecret?.let { mapOf("client_secret" to it) } ?: emptyMap())
                    .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
                    .setAuthorizationCode("fake") // we don't actually use an authorization code, but this is required by AppAuth
                    .setRedirectUri(BuildConfig.KEYCLOAK_REDIRECT_URL.toUri())
                    .build()
            )
                .setAccessToken(accessToken)
                .setAccessTokenExpirationTime(accessTokenExpiration) // we need to manually set the accessTokenExpiration otherwise AppAuth never attempts to refresh it
                .setRefreshToken(refreshToken)
                .setTokenType(TokenResponse.TOKEN_TYPE_BEARER)
                .build(),
            null
        )
    }
}

fun AuthState.performActionWithFreshTokens(bytesOwnedIdentity: ByteArray?, authorizationService: AuthorizationService, supportedAuthenticationMethods: List<ObvKeycloakAuthType>, action: AuthStateAction) {

    val wrappedAction = if (bytesOwnedIdentity != null // only try ID-based auth if we already have an ID
        && supportedAuthenticationMethods.any { it is ObvKeycloakAuthType.IdBased }) {
        // if Id-based authentication is supported, wrap the action into another action that attempts to re-authenticates with ID in case of failure
        AuthStateAction { accessToken, idToken, ex ->
            when (ex?.code) {
                // if no exception, or network error, pass the result to the original action
                null, 3 -> action.execute(accessToken, idToken, ex)
                // if another error occurred, and we would prompt the user for re-authentication, first try to re-authenticate with ID
                else -> {
                    val idBasedAuthResult = AppSingleton.getEngine().performKeycloakIdBasedAuth(bytesOwnedIdentity)
                    if (idBasedAuthResult.status == ObvKeycloakIdBasedAuthResult.Status.SUCCESS) {
                        // if id-based reauthentication worked, update the current AuthState
                        initializeFromMagicLinkOrIdBasedAuthResponse(idBasedAuthResult.accessToken, idBasedAuthResult.refreshToken, idBasedAuthResult.clientId, idBasedAuthResult.clientSecret)
                        // then perform the action with the fresh token
                        action.execute(idBasedAuthResult.accessToken, idToken, null)
                    } else {
                        // if id-based reauthentication did not work, performed the wrapped action and let it prompt the user for re-authentication
                        action.execute(accessToken, idToken, ex)
                    }
                }
            }
        }
    } else {
        action
    }

    val clientSecret = (supportedAuthenticationMethods.find { it is ObvKeycloakAuthType.OpenIdConnect } as? ObvKeycloakAuthType.OpenIdConnect)?.clientSecret
    if (clientSecret == null) {
        performActionWithFreshTokens(authorizationService, wrappedAction)
    } else {
        performActionWithFreshTokens(
            authorizationService,
            ClientSecretPost(clientSecret),
            wrappedAction
        )
    }
}