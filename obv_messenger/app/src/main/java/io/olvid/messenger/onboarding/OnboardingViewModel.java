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

package io.olvid.messenger.onboarding;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.datatypes.ObvBase64;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.customClasses.ConfigurationKeycloakPojo;
import io.olvid.messenger.customClasses.ConfigurationPojo;


public class OnboardingViewModel extends ViewModel {
    private boolean firstIdentity = true;

    private String unvalidatedServer = null;
    private String server = null;
    private String unformattedApiKey = null;
    private UUID apiKey = null;
    private UUID lastValidatedApiKey = null;
    private String lastFailedApiKey = null;
    private final MutableLiveData<Pair<VALIDATED_STATUS, VALIDATED_STATUS>> validatedStatus = new MutableLiveData<>();
    private final Set<String> validatedServers = new HashSet<>(Collections.singleton(BuildConfig.SERVER_NAME));
    private final Set<String> invalidatedServers = new HashSet<>();
    private String currentlyCheckingServer = null;
    private UUID currentlyCheckingApiKey = null;
    private final HashMap<UUID, String> queriedUnformattedApiKeys = new HashMap<>();

    private String keycloakSerializedAuthState = null; // Not null means there is a keycloak server configured
    private String keycloakServer = null;
    private String keycloakClientId = null;
    private String keycloakClientSecret = null;
    private JsonWebKeySet keycloakJwks = null;
    private JsonKeycloakUserDetails keycloakUserDetails = null;
    private boolean keycloakRevocationAllowed = false;
    private JsonWebKey keycloakSignatureKey = null;

    private String lastValidatedKeycloak = null;
    private String lastFailedKeycloak = null;
    private String currentlyCheckingKeycloak = null;
    private final MutableLiveData<VALIDATED_STATUS> keycloakValidatedStatus = new MutableLiveData<>();

    private final MutableLiveData<Boolean> forceDisabled = new MutableLiveData<>(false);

    private boolean deepLinked;
    private boolean configuredFromMdm;

    public enum VALIDATED_STATUS {
        UNCHECKED,
        CHECKING,
        VALID,
        INVALID
    }


    // region Options


    public boolean isFirstIdentity() {
        return firstIdentity;
    }

    public void setFirstIdentity(boolean firstIdentity) {
        this.firstIdentity = firstIdentity;
    }

    public void setDeepLinked(boolean deepLinked) {
        this.deepLinked = deepLinked;
    }

    public boolean isDeepLinked() {
        return deepLinked;
    }


    public boolean isConfiguredFromMdm() {
        return configuredFromMdm;
    }

    public void setConfiguredFromMdm(boolean configuredFromMdm) {
        this.configuredFromMdm = configuredFromMdm;
    }


    @SuppressWarnings("SameParameterValue")
    void setServer(String server) {
        this.unvalidatedServer = server == null ? null : server.trim();
        updateValidatedStatus();
    }

    public String getServer() {
        return server;
    }

    public String getKeycloakSerializedAuthState() {
        return keycloakSerializedAuthState;
    }

    public void setKeycloakSerializedAuthState(String keycloakSerializedAuthState) {
        this.keycloakSerializedAuthState = keycloakSerializedAuthState;
    }

    public UUID getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        unformattedApiKey = apiKey == null ? null : apiKey.trim();
        try {
            this.apiKey = UUID.fromString(unformattedApiKey);
        } catch (Exception e) {
            this.apiKey = null;
        }
        updateValidatedStatus();
    }

    public void clearValidatedApiKey() {
        lastValidatedApiKey = null;
        lastFailedApiKey = null;
        currentlyCheckingApiKey = null;
    }

    @Nullable
    public String getKeycloakServer() {
        return keycloakServer;
    }

    public void setKeycloakServer(String keycloakServer) {
        this.keycloakServer = keycloakServer;
        if (keycloakServer == null) {
            keycloakSerializedAuthState = null;
            keycloakClientId = null;
            keycloakClientSecret = null;
            keycloakJwks = null;
            keycloakUserDetails = null;
            keycloakSignatureKey = null;
        }
        updateKeycloakValidatedStatus();
    }

    public JsonWebKeySet getKeycloakJwks() {
        return keycloakJwks;
    }

    @Nullable
    public String getKeycloakClientId() {
        return keycloakClientId;
    }

    public void setKeycloakClientId(String keycloakClientId) {
        this.keycloakClientId = keycloakClientId;
    }

    @Nullable
    public String getKeycloakClientSecret() {
        return keycloakClientSecret;
    }

    public void setKeycloakClientSecret(String keycloakClientSecret) {
        this.keycloakClientSecret = keycloakClientSecret;
    }

    public JsonKeycloakUserDetails getKeycloakUserDetails() {
        return keycloakUserDetails;
    }

    public void setKeycloakUserDetails(JsonKeycloakUserDetails keycloakUserDetails) {
        this.keycloakUserDetails = keycloakUserDetails;
    }

    public boolean isKeycloakRevocationAllowed() {
        return keycloakRevocationAllowed;
    }

    public void setKeycloakRevocationAllowed(boolean keycloakRevocationAllowed) {
        this.keycloakRevocationAllowed = keycloakRevocationAllowed;
    }

    public JsonWebKey getKeycloakSignatureKey() {
        return keycloakSignatureKey;
    }

    public void setKeycloakSignatureKey(JsonWebKey keycloakSignatureKey) {
        this.keycloakSignatureKey = keycloakSignatureKey;
    }

    public String getUnvalidatedServer() {
        return unvalidatedServer;
    }

    public String getUnformattedApiKey() {
        return unformattedApiKey;
    }

    public void apiKeyValidationFinished(@NonNull UUID apiKey, boolean success) {
        if (success) {
            lastValidatedApiKey = apiKey;
        } else {
            lastFailedApiKey = queriedUnformattedApiKeys.get(apiKey);
        }
        if (apiKey.equals(currentlyCheckingApiKey)) {
            currentlyCheckingApiKey = null;
        }
        updateValidatedStatus();
    }

    public void serverValidationFinished(@NonNull String server, boolean success) {
        if (success) {
            validatedServers.add(server);
        } else {
            invalidatedServers.add(server);
        }
        if (server.equals(currentlyCheckingServer)) {
            currentlyCheckingServer = null;
        }
        updateValidatedStatus();
    }

    public void keycloakValidationSuccess(@NonNull String keycloak, @NonNull String normalizedKeycloak, @NonNull String serializedAuthState, @NonNull JsonWebKeySet jwks) {
        if (keycloak.equals(currentlyCheckingKeycloak)) {
            currentlyCheckingKeycloak = null;
        }
        lastValidatedKeycloak = normalizedKeycloak;
        if (Objects.equals(lastFailedKeycloak, lastValidatedKeycloak)) {
            lastFailedKeycloak = null;
        }
        this.keycloakServer = normalizedKeycloak;
        this.keycloakSerializedAuthState = serializedAuthState;
        this.keycloakJwks = jwks;

        updateKeycloakValidatedStatus();
    }

    public void keycloakValidationFailed(@NonNull String keycloak) {
        if (keycloak.equals(currentlyCheckingKeycloak)) {
            currentlyCheckingKeycloak = null;
        }
        lastFailedKeycloak = keycloak;
        if (Objects.equals(lastFailedKeycloak, lastValidatedKeycloak)) {
            lastValidatedKeycloak = null;
        }
        updateKeycloakValidatedStatus();
    }

    public boolean parseScannedConfigurationUri(String base64configuration) {
        try {
            ConfigurationPojo configurationPojo = AppSingleton.getJsonObjectMapper().readValue(ObvBase64.decode(base64configuration), ConfigurationPojo.class);
            if (configurationPojo != null) {
                if (configurationPojo.server != null) {
                    setServer(configurationPojo.server);
                }
                if (configurationPojo.apikey != null) {
                    setApiKey(configurationPojo.apikey);
                }
                ConfigurationKeycloakPojo keycloakPojo = configurationPojo.keycloak;
                if (keycloakPojo != null) {
                    setKeycloakServer(keycloakPojo.getServer());
                    setKeycloakClientId(keycloakPojo.getClientId());
                    setKeycloakClientSecret(keycloakPojo.getClientSecret());
                } else {
                    setKeycloakServer(null);
                }
                return true;
            }
        } catch (Exception e) {
            // nothing to do
        }
        return false;
    }

    private void updateValidatedStatus() {
        boolean serverValidated = validatedServers.contains(unvalidatedServer);
        if (serverValidated) {
            this.server = unvalidatedServer;
        }
        boolean serverInvalidated = unvalidatedServer == null || unvalidatedServer.length() == 0 || invalidatedServers.contains(unvalidatedServer);

        VALIDATED_STATUS serverStatus = serverValidated ? VALIDATED_STATUS.VALID : (serverInvalidated ? VALIDATED_STATUS.INVALID : (Objects.equals(unvalidatedServer, currentlyCheckingServer) ? VALIDATED_STATUS.CHECKING : VALIDATED_STATUS.UNCHECKED));

        VALIDATED_STATUS apiKeyStatus;
        if (lastFailedApiKey != null && lastFailedApiKey.length() > 0 && lastFailedApiKey.equals(unformattedApiKey)) {
            apiKeyStatus = VALIDATED_STATUS.INVALID;
        } else if (apiKey != null && apiKey.equals(lastValidatedApiKey)) {
            apiKeyStatus = VALIDATED_STATUS.VALID;
        } else if (apiKey != null && apiKey.equals(currentlyCheckingApiKey)) {
            apiKeyStatus = VALIDATED_STATUS.CHECKING;
        } else {
            apiKeyStatus = VALIDATED_STATUS.UNCHECKED;
        }

        validatedStatus.postValue(new Pair<>(serverStatus, apiKeyStatus));
    }

    private void updateKeycloakValidatedStatus() {
        VALIDATED_STATUS keycloakStatus = VALIDATED_STATUS.UNCHECKED;
        if (keycloakServer != null) {
            if (keycloakServer.equals(lastFailedKeycloak)) {
                keycloakStatus = VALIDATED_STATUS.INVALID;
            } else if (keycloakServer.equals(lastValidatedKeycloak)) {
                keycloakStatus = VALIDATED_STATUS.VALID;
            } else if (keycloakServer.equals(currentlyCheckingKeycloak)) {
                keycloakStatus = VALIDATED_STATUS.CHECKING;
            }
        }
        keycloakValidatedStatus.postValue(keycloakStatus);
    }

    public LiveData<VALIDATED_STATUS> getKeycloakValidatedStatus() {
        return keycloakValidatedStatus;
    }

    public LiveData<Pair<VALIDATED_STATUS, VALIDATED_STATUS>> getValidatedStatus() {
        return validatedStatus;
    }

    public void checkServerAndApiKey() {
        if (unvalidatedServer == null || unvalidatedServer.length() == 0) {
            return;
        }
        currentlyCheckingServer = unvalidatedServer;
        AppSingleton.getEngine().queryServerWellKnown(unvalidatedServer);

        clearValidatedApiKey();
        if (unformattedApiKey != null) {
            if (apiKey == null) {
                lastFailedApiKey = unformattedApiKey;
            } else {
                queriedUnformattedApiKeys.put(apiKey, unformattedApiKey);
                currentlyCheckingApiKey = apiKey;
                AppSingleton.getEngine().queryApiKeyStatus(unvalidatedServer, apiKey);
            }
        }
        updateValidatedStatus();
    }
    // endregion




    // region Identity Creation


    void setForceDisabled(boolean forceDisabled) {
        this.forceDisabled.postValue(forceDisabled);
    }

    LiveData<Boolean> getForceDisabled() {
        return forceDisabled;
    }

    // endregion
}
