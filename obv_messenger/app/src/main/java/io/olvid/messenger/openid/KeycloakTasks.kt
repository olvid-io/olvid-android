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
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.NoExceptionConnectionBuilder
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallbackWrapper
import io.olvid.messenger.openid.jsons.JsonGroupsRequest
import io.olvid.messenger.openid.jsons.JsonGroupsResponse
import io.olvid.messenger.openid.jsons.JsonMeRequest
import io.olvid.messenger.openid.jsons.JsonMeResponse
import io.olvid.messenger.openid.jsons.JsonSearchRequest
import io.olvid.messenger.openid.jsons.JsonSearchResponse
import io.olvid.messenger.openid.jsons.JsonTransferProofRequest
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthState.AuthStateAction
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jwk.VerificationJwkSelector
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver
import org.jose4j.lang.JoseException
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection


object KeycloakTasks {
    private const val ME_PATH = "olvid-rest/me"
    private const val PUT_KEY_PATH = "olvid-rest/putKey"
    private const val GET_KEY_PATH = "olvid-rest/getKey"
    private const val SEARCH_PATH = "olvid-rest/search"
    private const val REVOCATION_TEST_PATH = "olvid-rest/revocationTest"
    private const val GROUPS_PATH = "olvid-rest/groups"
    private const val TRANSFER_PROOF = "olvid-rest/transferProof"

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
    fun discoverKeycloakServer(
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
                                getJkws(serviceConfiguration.discoveryDoc!!.jwksUri)
                            jwks = JsonWebKeySet(jwksString)
                        } catch (_: JoseException) {
                            jwks = null
                        }
                        if (jwks != null) {
                            callback.success(finalServerUrl, authState, jwks)
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


    fun getOwnDetails(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        clientSecret: String?,
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
        val action =
            AuthStateAction { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                authorizationService.dispose()
                if (ex != null || accessToken == null) {
                    if (ex != null) {
                        ex.printStackTrace()
                        if (ex.code == 3) {
                            callback.failed(RFC_NETWORK_ERROR)
                            return@AuthStateAction
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
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action)
        } else {
            authState.performActionWithFreshTokens(
                authorizationService,
                ClientSecretPost(clientSecret),
                action
            )
        }
    }


    fun uploadOwnIdentity(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        clientSecret: String?,
        bytesOwnedIdentity: ByteArray,
        callback: KeycloakCallback<Void?>
    ) {
        Logger.d("Uploading Olvid ID to keycloak")

        val authorizationService = AuthorizationService(
            context, AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )
        val action =
            AuthStateAction { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                authorizationService.dispose()
                if (ex != null || accessToken == null) {
                    if (ex != null) {
                        ex.printStackTrace()
                        if (ex.code == 3) {
                            callback.failed(RFC_NETWORK_ERROR)
                            return@AuthStateAction
                        }
                    }
                    // by default, assume an authentication is required
                    callback.failed(RFC_AUTHENTICATION_REQUIRED)
                } else {
                    App.runThread {
                        try {
                            val input: MutableMap<String?, Any?> = HashMap()
                            input.put("identity", bytesOwnedIdentity)

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
                                    ERROR_CODE_INTERNAL_ERROR, ERROR_CODE_INVALID_REQUEST -> callback.failed(
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
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action)
        } else {
            authState.performActionWithFreshTokens(
                authorizationService,
                ClientSecretPost(clientSecret),
                action
            )
        }
    }


    // the server groups download timestamp is returned in the callback
    fun getGroups(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState?,
        clientSecret: String?,
        bytesOwnedIdentity: ByteArray?,
        latestGetGroupsTimestamp: Long?,
        callback: KeycloakCallbackWrapper<Long?>
    ) {
        Logger.d("Fetching keycloak groups")

        val authorizationService = AuthorizationService(
            context, AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )
        val action =
            AuthStateAction { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                authorizationService.dispose()
                if (ex != null || accessToken == null) {
                    if (ex != null) {
                        ex.printStackTrace()
                        if (ex.code == 3) {
                            callback.failed(RFC_NETWORK_ERROR)
                            return@AuthStateAction
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
        if (clientSecret == null) {
            authState?.performActionWithFreshTokens(authorizationService, action)
        } else {
            authState?.performActionWithFreshTokens(
                authorizationService,
                ClientSecretPost(clientSecret),
                action
            )
        }
    }


    fun search(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        clientSecret: String?,
        searchQuery: String?,
        callback: KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Int>?>
    ) {
        Logger.d("Performing search on keycloak")

        val authorizationService = AuthorizationService(
            context, AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )
        val action =
            AuthStateAction { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                authorizationService.dispose()
                if (ex != null || accessToken == null) {
                    if (ex != null) {
                        ex.printStackTrace()
                        if (ex.code == 3) {
                            callback.failed(RFC_NETWORK_ERROR)
                            return@AuthStateAction
                        }
                    }
                    // by default, assume an authentication is required
                    callback.failed(RFC_AUTHENTICATION_REQUIRED)
                } else {
                    App.runThread {
                        try {
                            val query = JsonSearchRequest()
                            if (searchQuery == null || searchQuery.isBlank()) {
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
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action)
        } else {
            authState.performActionWithFreshTokens(
                authorizationService,
                ClientSecretPost(clientSecret),
                action
            )
        }
    }


    fun addContact(
        context: Context,
        keycloakServerUrl: String,
        authState: AuthState,
        clientSecret: String?,
        jwks: JsonWebKeySet,
        signatureKey: JsonWebKey?,
        bytesOwnedIdentity: ByteArray,
        contactUserId: String?,
        bytesContactIdentity: ByteArray?,
        callback: KeycloakCallback<Void?>
    ) {
        Logger.d("Retrieving signed contact details")

        val authorizationService = AuthorizationService(
            context, AppAuthConfiguration.Builder()
                .setConnectionBuilder(NoExceptionConnectionBuilder())
                .build()
        )
        val action =
            AuthStateAction { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                authorizationService.dispose()
                if (ex != null || accessToken == null) {
                    if (ex != null) {
                        ex.printStackTrace()
                        if (ex.code == 3) {
                            callback.failed(RFC_NETWORK_ERROR)
                            return@AuthStateAction
                        }
                    }
                    // by default, assume an authentication is required
                    callback.failed(RFC_AUTHENTICATION_REQUIRED)
                } else {
                    App.runThread {
                        try {
                            val query: MutableMap<String?, String?> = HashMap()
                            query.put("user-id", contactUserId)

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
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action)
        } else {
            authState.performActionWithFreshTokens(
                authorizationService,
                ClientSecretPost(clientSecret),
                action
            )
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
                val input: MutableMap<String?, String?> = HashMap()
                input.put("nonce", selfRevocationTestNonce)

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
        val userAgentProperty = System.getProperty("http.agent")
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


    private fun getJkws(jwksUri: Uri?): String? {
        if (jwksUri != null) {
            Logger.d("Fetching JKWS at: $jwksUri")
            var connection: HttpURLConnection? = null
            try {
                connection = NoExceptionConnectionBuilder().openConnection(jwksUri)
                val response = connection.getResponseCode()
                if (response == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                        val sb = StringBuilder()
                        var line: String?
                        while ((reader.readLine().also { line = it }) != null) {
                            sb.append(line)
                        }
                        return sb.toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connection?.disconnect()
            }
        }
        return null
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
                            intent.getStringExtra(KeycloakAuthenticationActivity.Companion.AUTH_STATE_JSON_INTENT_EXTRA)
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
                } else if (result.resultCode == KeycloakAuthenticationActivity.Companion.RESULT_CODE_TIME_OFFSET) {
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
        fun success(serverUrl: String, authState: AuthState, jwks: JsonWebKeySet)
        fun failed()
    }

    interface AuthenticateCallback {
        fun success(authState: AuthState)
        fun failed(rfc: Int)
    }
}
