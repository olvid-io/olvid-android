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

import androidx.core.util.Pair
import androidx.preference.PreferenceManager
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.engine.engine.types.RegisterApiKeyResult
import io.olvid.engine.engine.types.identities.ObvIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import io.olvid.messenger.settings.SettingsActivity
import net.openid.appauth.AuthState
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.lang.HashUtil
import org.json.JSONException
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.math.max
import androidx.core.content.edit

object KeycloakManager {
    private val ownedIdentityStates: HashMap<BytesKey, KeycloakManagerState?>
    private val currentlySyncingOwnedIdentities: HashSet<BytesKey?>
    val authenticationRequiredOwnedIdentities: HashSet<BytesKey?>
    private val executor: NoExceptionSingleThreadExecutor
    private val retryTimer: Timer


    init {
        ownedIdentityStates = HashMap<BytesKey, KeycloakManagerState?>()
        currentlySyncingOwnedIdentities = HashSet<BytesKey?>()
        authenticationRequiredOwnedIdentities = HashSet<BytesKey?>()
        executor = NoExceptionSingleThreadExecutor("KeycloakManager-Executor")
        retryTimer = Timer("KeycloakManager-Retry timer")
    }

    // region public methods
    @JvmStatic
    fun registerKeycloakManagedIdentity(
        obvIdentity: ObvIdentity,
        keycloakServerUrl: String,
        clientId: String,
        clientSecret: String?,
        jwks: JsonWebKeySet?,
        signatureKey: JsonWebKey?,
        serializedKeycloakState: String?,
        transferRestricted: Boolean,
        ownApiKey: String?,
        latestRevocationListTimestamp: Long,
        latestGroupUpdateTimestamp: Long,
        firstKeycloakBinding: Boolean
    ) {
        executor.execute {
            var authState: AuthState? = null
            if (serializedKeycloakState != null) {
                try {
                    authState = AuthState.jsonDeserialize(serializedKeycloakState)
                } catch (_: JSONException) {
                    Logger.d("Error deserializing AuthState")
                }
            }
            val keycloakManagerState = KeycloakManagerState(
                obvIdentity,
                keycloakServerUrl,
                clientId,
                clientSecret,
                jwks,
                signatureKey,
                authState,
                ownApiKey,
                transferRestricted,
                latestRevocationListTimestamp,
                latestGroupUpdateTimestamp
            )

            val identityBytesKey = BytesKey(keycloakManagerState.bytesOwnedIdentity)
            ownedIdentityStates[identityBytesKey] = keycloakManagerState
            if (!firstKeycloakBinding) {
                synchronizeIdentityWithKeycloak(identityBytesKey, 0)
            }
        }
    }

    @JvmStatic
    fun unregisterKeycloakManagedIdentity(bytesOwnedIdentity: ByteArray) {
        executor.execute {
            val identityBytesKey = BytesKey(bytesOwnedIdentity)
            ownedIdentityStates.remove(identityBytesKey)
            currentlySyncingOwnedIdentities.remove(identityBytesKey)
            authenticationRequiredOwnedIdentities.remove(identityBytesKey)
            AndroidNotificationManager.clearKeycloakAuthenticationRequiredNotification(
                bytesOwnedIdentity
            )
        }
    }

    @JvmStatic
    fun reAuthenticationSuccessful(
        bytesOwnedIdentity: ByteArray?,
        jwks: JsonWebKeySet,
        authState: AuthState
    ) {
        executor.execute {
            val identityBytesKey = BytesKey(bytesOwnedIdentity)
            AndroidNotificationManager.clearKeycloakAuthenticationRequiredNotification(
                bytesOwnedIdentity
            )

            authenticationRequiredOwnedIdentities.remove(identityBytesKey)
            val kms = ownedIdentityStates.get(identityBytesKey)
                ?: return@execute
            // reset the synchronization time to force a full re-sync
            kms.lastSynchronization = 0
            kms.jwks = jwks
            kms.authState = authState

            try {
                AppSingleton.getEngine().saveKeycloakJwks(bytesOwnedIdentity, jwks.toJson())
                AppSingleton.getEngine()
                    .saveKeycloakAuthState(bytesOwnedIdentity, authState.jsonSerializeString())
            } catch (_: Exception) {
                // failed to save to engine
            }

            // after any authentication, re-sync details
            synchronizeIdentityWithKeycloak(identityBytesKey, 0)
        }
    }


    fun uploadOwnIdentity(bytesOwnedIdentity: ByteArray, callback: KeycloakCallback<Void?>) {
        executor.execute {
            val identityBytesKey = BytesKey(bytesOwnedIdentity)
            val kms = ownedIdentityStates[identityBytesKey]
            if (kms == null) {
                // this identity is not managed, fail
                callback.failed(KeycloakTasks.RFC_IDENTITY_NOT_MANAGED)
                return@execute
            }

            if (kms.authState == null) {
                // authentication required
                callback.failed(KeycloakTasks.RFC_AUTHENTICATION_REQUIRED)
                synchronizeIdentityWithKeycloak(identityBytesKey, 0)
                return@execute
            }

            // This method is only called after user consent for revocation, so do not ask next time
            kms.autoRevokeOnNextSync = true
            KeycloakTasks.uploadOwnIdentity(
                App.getContext(),
                kms.serverUrl,
                kms.authState!!,
                kms.clientSecret,
                bytesOwnedIdentity,
                KeycloakCallbackWrapper<Void?>(identityBytesKey, object : KeycloakCallback<Void?> {
                    override fun success(result: Void?) {
                        callback.success(result)

                        // once an identity is uploaded, rerun a synchronization to download the new API key and update local identity
                        kms.autoRevokeOnNextSync = false
                        synchronizeIdentityWithKeycloak(identityBytesKey, 0)
                    }

                    override fun failed(rfc: Int) {
                        when (rfc) {
                            KeycloakTasks.RFC_AUTHENTICATION_REQUIRED -> selfTestAndPromptForAuthentication(
                                identityBytesKey
                            )

                            KeycloakTasks.RFC_IDENTITY_ALREADY_UPLOADED -> App.openAppDialogKeycloakIdentityReplacementForbidden(
                                bytesOwnedIdentity
                            )

                            KeycloakTasks.RFC_IDENTITY_REVOKED -> {}
                            KeycloakTasks.RFC_SERVER_ERROR, KeycloakTasks.RFC_NETWORK_ERROR, KeycloakTasks.RFC_UNKNOWN_ERROR ->                             // retry with a delay
                                retryTimer.schedule(object : TimerTask() {
                                    override fun run() {
                                        synchronizeIdentityWithKeycloak(identityBytesKey, 1)
                                    }
                                }, 500)

                            else ->
                                retryTimer.schedule(object : TimerTask() {
                                    override fun run() {
                                        synchronizeIdentityWithKeycloak(identityBytesKey, 1)
                                    }
                                }, 500)

                        }

                        callback.failed(rfc)
                    }
                })
            )
        }
    }


    fun search(
        bytesOwnedIdentity: ByteArray?,
        searchQuery: String?,
        callback: KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Int>?>
    ) {
        executor.execute {
            val identityBytesKey = BytesKey(bytesOwnedIdentity)
            val kms = ownedIdentityStates[identityBytesKey]
            if (kms == null) {
                // this identity is not managed, fail
                callback.failed(KeycloakTasks.RFC_IDENTITY_NOT_MANAGED)
                return@execute
            }

            if (kms.authState == null) {
                callback.failed(KeycloakTasks.RFC_AUTHENTICATION_REQUIRED)
                synchronizeIdentityWithKeycloak(identityBytesKey, 0)
                return@execute
            }
            KeycloakTasks.search(
                App.getContext(),
                kms.serverUrl,
                kms.authState!!,
                kms.clientSecret,
                searchQuery,
                KeycloakCallbackWrapper<Pair<List<JsonKeycloakUserDetails>, Int>?>(
                    identityBytesKey,
                    object : KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Int>?> {
                        override fun success(result: Pair<List<JsonKeycloakUserDetails>, Int>?) {
                            callback.success(result)
                        }

                        override fun failed(rfc: Int) {
                            Logger.d("Search failed with rfc $rfc")

                            when (rfc) {
                                KeycloakTasks.RFC_AUTHENTICATION_REQUIRED -> selfTestAndPromptForAuthentication(
                                    identityBytesKey
                                )

                                KeycloakTasks.RFC_NETWORK_ERROR, KeycloakTasks.RFC_SERVER_ERROR, KeycloakTasks.RFC_UNKNOWN_ERROR -> {}
                                else -> {}
                            }

                            callback.failed(rfc)
                        }
                    })
            )
        }
    }


    fun addContact(
        bytesOwnedIdentity: ByteArray,
        contactUserId: String?,
        bytesContactIdentity: ByteArray?,
        callback: KeycloakCallback<Void?>
    ) {
        executor.execute {
            val identityBytesKey = BytesKey(bytesOwnedIdentity)
            val kms = ownedIdentityStates[identityBytesKey]
            if (kms == null || kms.authState == null || kms.jwks == null) {
                // this identity is not managed, fail
                callback.failed(KeycloakTasks.RFC_IDENTITY_NOT_MANAGED)
                return@execute
            }
            KeycloakTasks.addContact(
                App.getContext(),
                kms.serverUrl,
                kms.authState!!,
                kms.clientSecret,
                kms.jwks!!,
                kms.signatureKey,
                bytesOwnedIdentity,
                contactUserId,
                bytesContactIdentity,
                KeycloakCallbackWrapper<Void?>(identityBytesKey, object : KeycloakCallback<Void?> {
                    override fun success(result: Void?) {
                        callback.success(null)
                    }

                    override fun failed(rfc: Int) {
                        Logger.d("Add contact failed with rfc $rfc")

                        when (rfc) {
                            KeycloakTasks.RFC_AUTHENTICATION_REQUIRED -> selfTestAndPromptForAuthentication(
                                identityBytesKey
                            )

                            KeycloakTasks.RFC_NETWORK_ERROR, KeycloakTasks.RFC_SERVER_ERROR, KeycloakTasks.RFC_UNKNOWN_ERROR, KeycloakTasks.RFC_INVALID_SIGNATURE -> {}
                            else -> {}
                        }

                        callback.failed(rfc)
                    }
                })
            )
        }
    }

    // endregion
    private fun selfTestAndPromptForAuthentication(identityBytesKey: BytesKey) {
        executor.execute {
            val kms = ownedIdentityStates[identityBytesKey]
            if (kms != null) {
                val nonce = AppSingleton.getEngine()
                    .getOwnedIdentityKeycloakSelfRevocationTestNonce(
                        kms.bytesOwnedIdentity,
                        kms.serverUrl
                    )
                KeycloakTasks.selfRevocationTest(
                    kms.serverUrl,
                    nonce ?: "",
                    object : KeycloakCallback<Boolean?> {
                        override fun success(result: Boolean?) {
                            // only unbind if nonce is non-null
                            if (nonce != null && result != null && result) {
                                // the server returned true --> the identity is no longer managed
                                AppSingleton.getEngine()
                                    .unbindOwnedIdentityFromKeycloak(kms.bytesOwnedIdentity)
                                App.openAppDialogKeycloakIdentityRevoked(kms.bytesOwnedIdentity)
                            } else {
                                // require an authentication: either nonce still exists, or nonce is null
                                authenticationRequiredOwnedIdentities.add(identityBytesKey)
                                AndroidNotificationManager.displayKeycloakAuthenticationRequiredNotification(
                                    identityBytesKey.bytes
                                )
                                App.openAppDialogKeycloakAuthenticationRequired(
                                    kms.bytesOwnedIdentity,
                                    kms.clientId,
                                    kms.clientSecret,
                                    kms.serverUrl
                                )
                            }
                        }

                        override fun failed(rfc: Int) {
                            // in case of failure, we do nothing --> this is probably only a network error, and it will be tried again
                            // we do not want to prompt the user to authenticate in case of permanent connection error with the keycloak
                        }
                    })
            }
        }
    }

    private fun synchronizeAllManagedIdentities() {
        executor.execute {
            for (identityBytesKey in ownedIdentityStates.keys) {
                synchronizeIdentityWithKeycloak(identityBytesKey, 0)
            }
        }
    }


    private fun synchronizeIdentityWithKeycloak(identityBytesKey: BytesKey, failedAttempts: Int) {
        executor.execute {
            val kms = ownedIdentityStates[identityBytesKey]
                ?: return@execute

            if (System.currentTimeMillis() - kms.lastSynchronization < SYNCHRONIZATION_INTERVAL_MS) {
                return@execute
            }

            /**////// */
            // mark the identity as currently syncing --> un-mark it as soon as success or failure
            if (!currentlySyncingOwnedIdentities.add(identityBytesKey)) {
                // if we are already syncing, return
                return@execute
            }

            if (kms.jwks == null || kms.authState == null || !kms.authState!!.isAuthorized) {
                // if jwks is null, or if we are not authenticated --> full authentication round
                currentlySyncingOwnedIdentities.remove(identityBytesKey)

                // authentication required --> open authentication app dialog
                selfTestAndPromptForAuthentication(identityBytesKey)
                return@execute
            }
            KeycloakTasks.getOwnDetails(
                App.getContext(),
                kms.serverUrl,
                kms.authState!!,
                kms.clientSecret,
                kms.jwks!!,
                max(
                    0,
                    kms.latestRevocationListTimestamp - REVOCATION_LIST_LATEST_TIMESTAMP_OVERLAP_MILLIS
                ),
                KeycloakCallbackWrapper<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?>(
                    identityBytesKey,
                    object :
                        KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?> {
                        override fun success(result: Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?) {
                            executor.execute {
                                currentlySyncingOwnedIdentities.remove(identityBytesKey)
                                val keycloakUserDetailsAndStuff = result?.first
                                val keycloakServerRevocationsAndStuff = result?.second

                                if (keycloakUserDetailsAndStuff == null || keycloakUserDetailsAndStuff.userDetails == null || keycloakServerRevocationsAndStuff == null) {
                                    // this should never happen --> failed
                                    failed(KeycloakTasks.RFC_BAD_RESPONSE)
                                    return@execute
                                }

                                Logger.d("Successfully downloaded own details from keycloak server")

                                val kms = ownedIdentityStates[identityBytesKey]
                                    ?: return@execute

                                if (kms.authState == null) {
                                    synchronizeIdentityWithKeycloak(identityBytesKey, 0)
                                    return@execute
                                }

                                // check if version is outdated
                                if (keycloakServerRevocationsAndStuff.minimumBuildVersions != null) {
                                    val minimumBuildVersion =
                                        keycloakServerRevocationsAndStuff.minimumBuildVersions["android"]
                                    if (minimumBuildVersion != null && minimumBuildVersion > BuildConfig.VERSION_CODE) {
                                        PreferenceManager.getDefaultSharedPreferences(App.getContext()).takeIf {
                                            // only update the min version if Keycloak imposes a greater version number than the server
                                            it.getInt(SettingsActivity.PREF_KEY_MIN_APP_VERSION, -1) < minimumBuildVersion
                                        }?.edit {
                                            putInt(
                                                SettingsActivity.PREF_KEY_MIN_APP_VERSION,
                                                minimumBuildVersion
                                            )
                                        }
                                    }
                                }

                                try {
                                    // verify that the signature key matches what is stored, ask for user confirmation otherwise
                                    if (keycloakUserDetailsAndStuff.signatureKey != null) {
                                        val previousKey = AppSingleton.getEngine()
                                            .getOwnedIdentityKeycloakSignatureKey(kms.bytesOwnedIdentity)
                                        if (previousKey == null) {
                                            AppSingleton.getEngine()
                                                .setOwnedIdentityKeycloakSignatureKey(
                                                    kms.bytesOwnedIdentity,
                                                    keycloakUserDetailsAndStuff.signatureKey
                                                )
                                            kms.signatureKey =
                                                keycloakUserDetailsAndStuff.signatureKey
                                        } else if (!previousKey.calculateThumbprint(HashUtil.SHA_256)
                                                .contentEquals(
                                                    keycloakUserDetailsAndStuff.signatureKey.calculateThumbprint(
                                                        HashUtil.SHA_256
                                                    )
                                                )
                                        ) {
                                            App.openAppDialogKeycloakSignatureKeyChanged(kms.bytesOwnedIdentity)
                                            return@execute
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // in case of exception, do nothing
                                    return@execute
                                }

                                val userDetails = keycloakUserDetailsAndStuff.userDetails

                                try {
                                    val previousId = AppSingleton.getEngine()
                                        .getOwnedIdentityKeycloakUserId(kms.bytesOwnedIdentity)
                                    if (previousId == null) {
                                        AppSingleton.getEngine().setOwnedIdentityKeycloakUserId(
                                            kms.bytesOwnedIdentity,
                                            userDetails.getId()
                                        )
                                    } else if (previousId != userDetails.getId()) {
                                        // user Id changed on keycloak --> probably an authentication with the wrong login
                                        // check the identity and only update id locally if the identity is the same
                                        if (userDetails.getIdentity()
                                                .contentEquals(kms.bytesOwnedIdentity)
                                        ) {
                                            AppSingleton.getEngine().setOwnedIdentityKeycloakUserId(
                                                kms.bytesOwnedIdentity,
                                                userDetails.getId()
                                            )
                                        } else {
                                            App.openAppDialogKeycloakUserIdChanged(
                                                kms.bytesOwnedIdentity,
                                                kms.clientId,
                                                kms.clientSecret,
                                                kms.serverUrl
                                            )
                                            return@execute
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // in case of exception, do nothing
                                    return@execute
                                }

                                // check if ownedIdentity was never uploaded
                                if (userDetails.getIdentity() == null || userDetails.getIdentity().size == 0 || (kms.autoRevokeOnNextSync && keycloakServerRevocationsAndStuff.revocationAllowed && !userDetails.getIdentity()
                                        .contentEquals(identityBytesKey.bytes))
                                ) {
                                    currentlySyncingOwnedIdentities.add(identityBytesKey)

                                    // upload the key to the server
                                    KeycloakTasks.uploadOwnIdentity(
                                        App.getContext(),
                                        kms.serverUrl,
                                        kms.authState!!,
                                        kms.clientSecret,
                                        identityBytesKey.bytes,
                                        object : KeycloakCallback<Void?> {
                                            override fun success(result: Void?) {
                                                Logger.d("Successfully uploaded own key")
                                                currentlySyncingOwnedIdentities.remove(
                                                    identityBytesKey
                                                )

                                                // uploaded our own key --> re-sync
                                                kms.autoRevokeOnNextSync = false
                                                synchronizeIdentityWithKeycloak(identityBytesKey, 0)
                                            }

                                            override fun failed(rfc: Int) {
                                                Logger.d("Failed to upload own key $rfc")
                                                currentlySyncingOwnedIdentities.remove(
                                                    identityBytesKey
                                                )

                                                // we retry with a delay
                                                when (rfc) {
                                                    KeycloakTasks.RFC_AUTHENTICATION_REQUIRED, KeycloakTasks.RFC_IDENTITY_REVOKED -> kms.authState =
                                                        null

                                                    KeycloakTasks.RFC_IDENTITY_ALREADY_UPLOADED, KeycloakTasks.RFC_SERVER_ERROR, KeycloakTasks.RFC_NETWORK_ERROR, KeycloakTasks.RFC_UNKNOWN_ERROR -> {}
                                                }
                                                if (failedAttempts < MAX_FAIL_COUNT) {
                                                    retryTimer.schedule(object : TimerTask() {
                                                        override fun run() {
                                                            synchronizeIdentityWithKeycloak(
                                                                identityBytesKey,
                                                                failedAttempts + 1
                                                            )
                                                        }
                                                    }, 500L shl failedAttempts)
                                                }
                                            }
                                        })
                                    return@execute
                                } else if (!userDetails.getIdentity()
                                        .contentEquals(identityBytesKey.bytes) && keycloakServerRevocationsAndStuff.revocationAllowed
                                ) {
                                    // revocation is possible but was not requested
                                    App.openAppDialogKeycloakIdentityReplacement(
                                        identityBytesKey.bytes,
                                        kms.serverUrl,
                                        kms.clientSecret,
                                        kms.authState!!.jsonSerializeString()
                                    )
                                    return@execute
                                } else if (!userDetails.getIdentity()
                                        .contentEquals(identityBytesKey.bytes)
                                ) {
                                    // revocation required, but not possible
                                    App.openAppDialogKeycloakIdentityReplacementForbidden(
                                        identityBytesKey.bytes
                                    )
                                    return@execute
                                }


                                /**///// */
                                // if we reach this point, our correct identity is on the server
                                // --> compare the details and update locally if needed
                                val serverJsonIdentityDetails =
                                    userDetails.getIdentityDetails(keycloakUserDetailsAndStuff.signedUserDetails)
                                if ((kms.identityDetails != serverJsonIdentityDetails) || (kms.ownDetailsSignatureTimestamp == null && userDetails.getTimestamp() != null)
                                    || (kms.ownDetailsSignatureTimestamp != null && userDetails.getTimestamp() != null && kms.ownDetailsSignatureTimestamp!! + OWN_SIGNED_DETAILS_RENEWAL_INTERVAL_MILLIS < userDetails.getTimestamp())
                                ) {
                                    try {
                                        Logger.i("Refreshing keycloak owned details in engine")
                                        AppSingleton.getEngine().updateLatestIdentityDetails(
                                            identityBytesKey.bytes,
                                            serverJsonIdentityDetails
                                        )
                                        AppSingleton.getEngine()
                                            .publishLatestIdentityDetails(identityBytesKey.bytes)
                                        kms.identityDetails = serverJsonIdentityDetails
                                        kms.ownDetailsSignatureTimestamp =
                                            userDetails.getTimestamp()
                                    } catch (_: Exception) {
                                        // engine exception --> simply retry
                                        if (failedAttempts < MAX_FAIL_COUNT) {
                                            retryTimer.schedule(object : TimerTask() {
                                                override fun run() {
                                                    synchronizeIdentityWithKeycloak(
                                                        identityBytesKey,
                                                        failedAttempts + 1
                                                    )
                                                }
                                            }, 500L shl failedAttempts)
                                        }
                                        return@execute
                                    }
                                }

                                // update API key if needed
                                if (keycloakUserDetailsAndStuff.apiKey != null) {
                                    // update API key if needed
                                    try {
                                        val newApiKey =
                                            UUID.fromString(keycloakUserDetailsAndStuff.apiKey)
                                        if (Logger.getUuidString(newApiKey) != kms.ownApiKey) {
                                            App.runThread {
                                                registerMeApiKeyOnServer(
                                                    kms,
                                                    identityBytesKey,
                                                    newApiKey
                                                )
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // do nothing
                                    }
                                }

                                if (kms.transferRestricted != keycloakServerRevocationsAndStuff.transferRestricted) {
                                    AppSingleton.getEngine()
                                        .updateKeycloakTransferRestrictedIfNeeded(
                                            kms.bytesOwnedIdentity,
                                            kms.serverUrl,
                                            keycloakServerRevocationsAndStuff.transferRestricted
                                        )
                                    kms.transferRestricted =
                                        keycloakServerRevocationsAndStuff.transferRestricted
                                }

                                AppSingleton.getEngine().updateKeycloakPushTopicsIfNeeded(
                                    kms.bytesOwnedIdentity,
                                    kms.serverUrl,
                                    keycloakUserDetailsAndStuff.pushTopics
                                )
                                AppSingleton.getEngine()
                                    .setOwnedIdentityKeycloakSelfRevocationTestNonce(
                                        kms.bytesOwnedIdentity,
                                        kms.serverUrl,
                                        keycloakUserDetailsAndStuff.selfRevocationTestNonce
                                    )

                                // update revocation list and latest revocation list timestamp in a transaction
                                if (keycloakServerRevocationsAndStuff.signedRevocations != null) {
                                    AppSingleton.getEngine().updateKeycloakRevocationList(
                                        identityBytesKey.bytes,
                                        keycloakServerRevocationsAndStuff.currentServerTimestamp,
                                        keycloakServerRevocationsAndStuff.signedRevocations
                                    )
                                    kms.latestRevocationListTimestamp =
                                        keycloakServerRevocationsAndStuff.currentServerTimestamp
                                }

                                /**//////////// */
                                // now synchronize groups too
                                KeycloakTasks.getGroups(
                                    App.getContext(),
                                    kms.serverUrl,
                                    kms.authState,
                                    kms.clientSecret,
                                    kms.bytesOwnedIdentity,
                                    max(
                                        0,
                                        kms.latestGroupUpdateTimestamp - REVOCATION_LIST_LATEST_TIMESTAMP_OVERLAP_MILLIS
                                    ),
                                    KeycloakCallbackWrapper(
                                        identityBytesKey,
                                        object : KeycloakCallback<Long?> {
                                            override fun success(result: Long?) {
                                                if (result != null) {
                                                    kms.latestGroupUpdateTimestamp = result
                                                }
                                            }

                                            override fun failed(rfc: Int) {
                                                // do nothing, this might be an old keycloak server with no groups support
                                            }
                                        })
                                )

                                /**////// */
                                // synchronization finished successfully!!!
                                // --> update the last sync timestamp
                                kms.lastSynchronization = System.currentTimeMillis()
                                retryTimer.schedule(object : TimerTask() {
                                    override fun run() {
                                        synchronizeIdentityWithKeycloak(identityBytesKey, 0)
                                    }
                                }, SYNCHRONIZATION_INTERVAL_MS + 1000)
                            }
                        }

                        override fun failed(rfc: Int) {
                            Logger.d("Failed to download Keycloak owned details, rfc: $rfc")
                            currentlySyncingOwnedIdentities.remove(identityBytesKey)

                            when (rfc) {
                                KeycloakTasks.RFC_AUTHENTICATION_REQUIRED -> selfTestAndPromptForAuthentication(
                                    identityBytesKey
                                )

                                KeycloakTasks.RFC_SERVER_ERROR, KeycloakTasks.RFC_INVALID_SIGNATURE, KeycloakTasks.RFC_BAD_RESPONSE, KeycloakTasks.RFC_NETWORK_ERROR, KeycloakTasks.RFC_UNKNOWN_ERROR ->                             // retry after a small delay
                                    if (failedAttempts < MAX_FAIL_COUNT) {
                                        retryTimer.schedule(object : TimerTask() {
                                            override fun run() {
                                                synchronizeIdentityWithKeycloak(
                                                    identityBytesKey,
                                                    failedAttempts + 1
                                                )
                                            }
                                        }, 500L shl failedAttempts)
                                    }
                            }
                        }
                    })
            )
        }
    }

    private class KeycloakManagerState(
        obvIdentity: ObvIdentity,
        serverUrl: String,
        clientId: String,
        clientSecret: String?,
        jwks: JsonWebKeySet?,
        signatureKey: JsonWebKey?,
        authState: AuthState?,
        ownApiKey: String?,
        transferRestricted: Boolean,
        latestRevocationListTimestamp: Long,
        latestGroupUpdateTimestamp: Long
    ) {
        val bytesOwnedIdentity: ByteArray = obvIdentity.bytesIdentity
        var identityDetails: JsonIdentityDetails = obvIdentity.getIdentityDetails()
        var ownDetailsSignatureTimestamp: Long? = null
        val serverUrl: String
        val clientId: String
        val clientSecret: String?
        var jwks: JsonWebKeySet?
        var signatureKey: JsonWebKey?
        var authState: AuthState?
        var ownApiKey: String?
        var transferRestricted: Boolean
        var lastSynchronization: Long
        var autoRevokeOnNextSync: Boolean
        var latestRevocationListTimestamp: Long
        var latestGroupUpdateTimestamp: Long

        init {

            if (identityDetails.getSignedUserDetails() != null) {
                try {
                    val jwtConsumer = JwtConsumerBuilder()
                        .setSkipSignatureVerification()
                        .setSkipAllValidators()
                        .build()
                    val context = jwtConsumer.process(identityDetails.getSignedUserDetails())
                    val jsonKeycloakUserDetails = AppSingleton.getJsonObjectMapper()
                        .readValue(
                            context.jwtClaims.rawJson,
                            JsonKeycloakUserDetails::class.java
                        )
                    ownDetailsSignatureTimestamp = jsonKeycloakUserDetails.getTimestamp()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ownDetailsSignatureTimestamp = null
                }
            } else {
                ownDetailsSignatureTimestamp = null
            }

            this.serverUrl = serverUrl
            this.clientId = clientId
            this.clientSecret = clientSecret
            this.jwks = jwks
            this.signatureKey = signatureKey
            this.authState = authState
            this.ownApiKey = ownApiKey
            this.transferRestricted = transferRestricted
            this.lastSynchronization = 0
            this.autoRevokeOnNextSync = false
            this.latestRevocationListTimestamp = latestRevocationListTimestamp
            this.latestGroupUpdateTimestamp = latestGroupUpdateTimestamp
        }
    }

    class KeycloakCallbackWrapper<T>(
        private val identityBytesKey: BytesKey,
        private val callback: KeycloakCallback<T?>
    ) : KeycloakCallback<T?> {
        private val oldAccessToken: String?

        init {
            // capture the current value of the access token, so that at success time we can check whether it was refreshed and save it to engine
            val kms = ownedIdentityStates[identityBytesKey]
            if (kms != null && kms.authState != null) {
                this.oldAccessToken = kms.authState!!.getAccessToken()
            } else {
                this.oldAccessToken = null
            }
        }

        override fun success(result: T?) {
            executor.execute {
                val kms = ownedIdentityStates[identityBytesKey]
                if (kms != null && kms.authState != null && (kms.authState!!.getAccessToken() != oldAccessToken)) {
                    // access token was refreshed --> update authState in engine
                    try {
                        AppSingleton.getEngine().saveKeycloakAuthState(
                            identityBytesKey.bytes,
                            kms.authState!!.jsonSerializeString()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // we successfully called an authenticated API point --> clear any authentication required notification
                AndroidNotificationManager.clearKeycloakAuthenticationRequiredNotification(
                    identityBytesKey.bytes
                )
                authenticationRequiredOwnedIdentities.remove(identityBytesKey)

                // call the wrapped callback
                callback.success(result)
            }
        }

        override fun failed(rfc: Int) {
            executor.execute { callback.failed(rfc) }
        }
    }


    interface KeycloakCallback<T> {
        fun success(result: T?)
        fun failed(rfc: Int)
    }

    const val MAX_FAIL_COUNT: Int = 5
    const val SYNCHRONIZATION_INTERVAL_MS: Long =
        21600000L // synchronize with keycloak server every 6 hours
    const val OWN_SIGNED_DETAILS_RENEWAL_INTERVAL_MILLIS: Long =
        7 * 86400000L // refresh our own details if they are more than 1 week old
    const val REVOCATION_LIST_LATEST_TIMESTAMP_OVERLAP_MILLIS: Long =
        3600000L // time to subtract from latest query time when getting revocation list --> this leaves 1h for the server to finish writing down any new revocation

    @JvmStatic
    fun isOwnedIdentityTransferRestricted(bytesOwnedIdentity: ByteArray): Boolean {
        val kms: KeycloakManagerState? =
            ownedIdentityStates[BytesKey(bytesOwnedIdentity)]
        return kms != null && kms.transferRestricted
    }

    @JvmStatic
    fun syncAllManagedIdentities() {
        synchronizeAllManagedIdentities()
    }

    @JvmStatic
    fun forceSyncManagedIdentity(bytesOwnedIdentity: ByteArray?) {
        val identityBytesKey = BytesKey(bytesOwnedIdentity)
        val kms: KeycloakManagerState? = ownedIdentityStates[identityBytesKey]
        if (kms != null) {
            kms.lastSynchronization = 0
            synchronizeIdentityWithKeycloak(identityBytesKey, 0)
        }
    }

    @JvmStatic
    fun resetLatestGroupDownloadTimestamp(bytesOwnedIdentity: ByteArray?) {
        val identityBytesKey = BytesKey(bytesOwnedIdentity)
        val kms: KeycloakManagerState? = ownedIdentityStates[identityBytesKey]
        if (kms != null) {
            kms.latestGroupUpdateTimestamp = 0
        }
    }

    @JvmStatic
    fun forceSelfTestAndReauthentication(bytesOwnedIdentity: ByteArray?) {
        val identityBytesKey = BytesKey(bytesOwnedIdentity)
        val kms: KeycloakManagerState? = ownedIdentityStates[identityBytesKey]
        if (kms != null) {
            selfTestAndPromptForAuthentication(identityBytesKey)
        }
    }

    @JvmStatic
    fun processPushTopicNotification(pushTopic: String?) {
        try {
            val obvIdentities =
                AppSingleton.getEngine().getOwnedIdentitiesWithKeycloakPushTopic(pushTopic)
            for (obvIdentity in obvIdentities) {
                forceSyncManagedIdentity(obvIdentity.bytesIdentity)
            }
        } catch (e: Exception) {
            Logger.d("Failed to retrieve identities with a push topic...")
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun showAuthenticationRequiredNotificationForSelectedIdentityIfNeeded(bytesOwnedIdentity: ByteArray?) {
        if (authenticationRequiredOwnedIdentities.contains(
                BytesKey(
                    bytesOwnedIdentity
                )
            )
        ) {
            AndroidNotificationManager.displayKeycloakAuthenticationRequiredNotification(
                bytesOwnedIdentity
            )
        }
    }

    private fun registerMeApiKeyOnServer(
        kms: KeycloakManagerState,
        identityBytesKey: BytesKey,
        apiKey: UUID
    ) {
        // retry at most 10 times
        for (i in 0..9) {
            val registerApiKeyResult = AppSingleton.getEngine()
                .registerOwnedIdentityApiKeyOnServer(identityBytesKey.bytes, apiKey)
            Logger.d("Registering Keycloak api key on server: $registerApiKeyResult")
            when (registerApiKeyResult) {
                RegisterApiKeyResult.SUCCESS -> try {
                    AppSingleton.getEngine().saveKeycloakApiKey(
                        identityBytesKey.bytes,
                        Logger.getUuidString(apiKey)
                    )
                    kms.ownApiKey = Logger.getUuidString(apiKey)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                RegisterApiKeyResult.WAIT_FOR_SERVER_SESSION ->                     // wait a bit, then try again
                    try {
                        Thread.sleep(((3 + i) * 1000).toLong())
                    } catch (_: InterruptedException) {
                    }

                RegisterApiKeyResult.INVALID_KEY, RegisterApiKeyResult.FAILED ->                     // non-retriable failure, abort
                    return
            }
        }
    }
}
