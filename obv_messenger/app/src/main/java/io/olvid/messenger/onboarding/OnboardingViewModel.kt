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
package io.olvid.messenger.onboarding

import android.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.engine.datatypes.ObvBase64
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.customClasses.ConfigurationPojo
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.CHECKING
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.INVALID
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.UNCHECKED
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.VALID
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.JsonWebKeySet
import java.util.UUID

class OnboardingViewModel : ViewModel() {
    // region Options
    var isFirstIdentity: Boolean = true

    var unvalidatedServer: String? = null
        private set
    var server: String? = null
        private set
    var unformattedApiKey: String? = null
        private set
    var apiKey: UUID? = null
        private set
    private var lastValidatedApiKey: UUID? = null
    private var lastFailedApiKey: String? = null
    val validatedStatus = MutableLiveData<Pair<VALIDATED_STATUS, VALIDATED_STATUS>>()
    private val validatedServers: MutableSet<String?> = HashSet(setOf(BuildConfig.SERVER_NAME))
    private val invalidatedServers: MutableSet<String> = HashSet()
    private var currentlyCheckingServer: String? = null
    private var currentlyCheckingApiKey: UUID? = null
    private val queriedUnformattedApiKeys = HashMap<UUID, String>()

    var keycloakSerializedAuthState: String? =
        null // Not null means there is a keycloak server configured
    var keycloakServer: String? = null
        set(value) {
            field = value
            if (value == null) {
                keycloakSerializedAuthState = null
                keycloakClientId = null
                keycloakClientSecret = null
                keycloakJwks = null
                keycloakUserDetails = null
                keycloakSignatureKey = null
            }
            updateKeycloakValidatedStatus()
        }

    var keycloakClientId: String? = null
    var keycloakClientSecret: String? = null
    var keycloakJwks: JsonWebKeySet? = null
        private set
    var keycloakUserDetails: JsonKeycloakUserDetails? = null
    var isKeycloakRevocationAllowed: Boolean = false
    var isKeycloakTransferRestricted: Boolean = false
    var keycloakSignatureKey: JsonWebKey? = null

    private var lastValidatedKeycloak: String? = null
    private var lastFailedKeycloak: String? = null
    private var currentlyCheckingKeycloak: String? = null
    val keycloakValidatedStatus = MutableLiveData<VALIDATED_STATUS>()

    val forceDisabled = MutableLiveData(false)

    var isDeepLinked: Boolean = false
    var isConfiguredFromMdm: Boolean = false

    enum class VALIDATED_STATUS {
        UNCHECKED,
        CHECKING,
        VALID,
        INVALID
    }

    fun validateServer(server: String?) {
        this.unvalidatedServer = server?.trim { it <= ' ' }
        updateValidatedStatus()
    }

    fun setApiKey(apiKey: String?) {
        unformattedApiKey = apiKey?.trim { it <= ' ' }
        try {
            this.apiKey = UUID.fromString(unformattedApiKey)
        } catch (e: Exception) {
            this.apiKey = null
        }
        updateValidatedStatus()
    }

    private fun clearValidatedApiKey() {
        lastValidatedApiKey = null
        lastFailedApiKey = null
        currentlyCheckingApiKey = null
    }

    fun apiKeyValidationFinished(apiKey: UUID, success: Boolean) {
        if (success) {
            lastValidatedApiKey = apiKey
        } else {
            lastFailedApiKey = queriedUnformattedApiKeys[apiKey]
        }
        if (apiKey == currentlyCheckingApiKey) {
            currentlyCheckingApiKey = null
        }
        updateValidatedStatus()
    }

    fun serverValidationFinished(server: String, success: Boolean) {
        if (success) {
            validatedServers.add(server)
        } else {
            invalidatedServers.add(server)
        }
        if (server == currentlyCheckingServer) {
            currentlyCheckingServer = null
        }
        updateValidatedStatus()
    }

    fun keycloakValidationSuccess(
        keycloak: String,
        normalizedKeycloak: String,
        serializedAuthState: String,
        jwks: JsonWebKeySet
    ) {
        if (keycloak == currentlyCheckingKeycloak) {
            currentlyCheckingKeycloak = null
        }
        lastValidatedKeycloak = normalizedKeycloak
        if (lastFailedKeycloak == lastValidatedKeycloak) {
            lastFailedKeycloak = null
        }
        this.keycloakServer = normalizedKeycloak
        this.keycloakSerializedAuthState = serializedAuthState
        this.keycloakJwks = jwks

        updateKeycloakValidatedStatus()
    }

    fun keycloakValidationFailed(keycloak: String) {
        if (keycloak == currentlyCheckingKeycloak) {
            currentlyCheckingKeycloak = null
        }
        lastFailedKeycloak = keycloak
        if (lastFailedKeycloak == lastValidatedKeycloak) {
            lastValidatedKeycloak = null
        }
        updateKeycloakValidatedStatus()
    }

    fun parseScannedConfigurationUri(base64configuration: String?): Boolean {
        try {
            val configurationPojo = AppSingleton.getJsonObjectMapper().readValue(
                ObvBase64.decode(base64configuration),
                ConfigurationPojo::class.java
            )
            if (configurationPojo != null) {
                if (configurationPojo.server != null) {
                    unvalidatedServer = configurationPojo.server
                    server = configurationPojo.server
                }
                if (configurationPojo.apikey != null) {
                    setApiKey(configurationPojo.apikey)
                }
                val keycloakPojo = configurationPojo.keycloak
                if (keycloakPojo != null) {
                    keycloakServer = keycloakPojo.server
                    keycloakClientId = keycloakPojo.clientId
                    keycloakClientSecret = keycloakPojo.clientSecret
                } else {
                    keycloakServer = null
                }
                return true
            }
        } catch (e: Exception) {
            // nothing to do
        }
        return false
    }

    private fun updateValidatedStatus() {
        val serverValidated = validatedServers.contains(unvalidatedServer)
        if (serverValidated) {
            server = unvalidatedServer
        }
        val serverInvalidated =
            unvalidatedServer == null || unvalidatedServer!!.isEmpty() || invalidatedServers.contains(
                unvalidatedServer
            )

        val serverStatus =
            if (serverValidated) VALID else (if (serverInvalidated) INVALID else (if (unvalidatedServer == currentlyCheckingServer) CHECKING else UNCHECKED))
        val apiKeyStatus =
            if (lastFailedApiKey != null && lastFailedApiKey!!.isNotEmpty() && lastFailedApiKey == unformattedApiKey) {
                INVALID
            } else if (apiKey != null && apiKey == lastValidatedApiKey) {
                VALID
            } else if (apiKey != null && apiKey == currentlyCheckingApiKey) {
                CHECKING
            } else {
                UNCHECKED
            }

        validatedStatus.postValue(Pair(serverStatus, apiKeyStatus))
    }

    private fun updateKeycloakValidatedStatus() {
        var keycloakStatus = UNCHECKED
        if (keycloakServer != null) {
            if (keycloakServer == lastFailedKeycloak) {
                keycloakStatus = INVALID
            } else if (keycloakServer == lastValidatedKeycloak) {
                keycloakStatus = VALID
            } else if (keycloakServer == currentlyCheckingKeycloak) {
                keycloakStatus = CHECKING
            }
        }
        keycloakValidatedStatus.postValue(keycloakStatus)
    }

    fun checkServerAndApiKey() {
        if (unvalidatedServer == null || unvalidatedServer!!.isEmpty()) {
            return
        }
        currentlyCheckingServer = unvalidatedServer
        AppSingleton.getEngine().queryServerWellKnown(unvalidatedServer)

        clearValidatedApiKey()
        if (unformattedApiKey != null) {
            if (apiKey == null) {
                lastFailedApiKey = unformattedApiKey
            } else {
                queriedUnformattedApiKeys[apiKey!!] = unformattedApiKey!!
                currentlyCheckingApiKey = apiKey
                AppSingleton.getEngine().queryApiKeyStatus(unvalidatedServer, apiKey)
            }
        }
        updateValidatedStatus()
    }


    // endregion
    // region Identity Creation
    fun setForceDisabled(forceDisabled: Boolean) {
        this.forceDisabled.postValue(forceDisabled)
    }

    fun getForceDisabled(): LiveData<Boolean> {
        return forceDisabled
    } // endregion
}
