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

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.datatypes.ObvBase64;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.ObvBackupKeyVerificationOutput;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.ConfigurationKeycloakPojo;
import io.olvid.messenger.customClasses.ConfigurationPojo;
import io.olvid.messenger.services.BackupCloudProviderService;


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

    private String invitationLink = null;

    private int backupType;
    private final MutableLiveData<String> backupName = new MutableLiveData<>();
    private final MutableLiveData<byte[]> backupContent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> backupReady = new MutableLiveData<>(false);

    private String backupSeed;
    private final MutableLiveData<Boolean> backupKeyValid = new MutableLiveData<>(false);

    public static final int BACKUP_TYPE_FILE = 1;
    public static final int BACKUP_TYPE_CLOUD = 2;

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

    public String getInvitationLink() {
        return invitationLink;
    }

    public void setInvitationLink(String invitationLink) {
        this.invitationLink = invitationLink;
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





    // region Backup File

    void clearSelectedBackup() {
        backupName.setValue(null);
        backupContent.setValue(null);
        backupReady.postValue(false);
    }

    MutableLiveData<String> getBackupName() {
        return backupName;
    }

    MutableLiveData<byte[]> getBackupContent() {
        return backupContent;
    }

    MutableLiveData<Boolean> getBackupReady() {
        return backupReady;
    }

    int getBackupType() {
        return backupType;
    }

    void selectBackupFile(Uri backupFileUri, String fileName) throws Exception {
        ContentResolver contentResolver = App.getContext().getContentResolver();
        try (InputStream is = contentResolver.openInputStream(backupFileUri)) {
            if (is == null) {
                throw new Exception("Unable to read from provided Uri");
            }
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4_096];
                int c;
                while ((c = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, c);
                }
                backupContent.postValue(baos.toByteArray());
                backupName.postValue(fileName);
                backupType = BACKUP_TYPE_FILE;
                backupReady.postValue(true);
            }
        }
    }

    void setBackupCloud(byte[] backupContent, BackupCloudProviderService.CloudProviderConfiguration configuration, String device, String timestamp) {
        this.backupContent.postValue(backupContent);
        switch (configuration.provider) {
            case BackupCloudProviderService.CloudProviderConfiguration.PROVIDER_WEBDAV:
                backupName.postValue(App.getContext().getString(R.string.text_description_webdav_backup, configuration.account + " @ " + configuration.serverUrl, device, timestamp));
                break;
            case BackupCloudProviderService.CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                backupName.postValue(App.getContext().getString(R.string.text_description_google_drive_backup, configuration.account, device, timestamp));
                break;
        }
        backupType = BACKUP_TYPE_CLOUD;
        backupReady.postValue(true);
    }

    // endregion

    // region Backup Key

    void setBackupSeed(String backupSeed) {
        this.backupSeed = backupSeed;
        this.backupKeyValid.setValue(false);
    }

    public String getBackupSeed() {
        return backupSeed;
    }

    public MutableLiveData<Boolean> getBackupKeyValid() {
        return backupKeyValid;
    }

    int validateBackupSeed() {
        if (backupSeed == null || backupContent.getValue() == null) {
            this.backupKeyValid.setValue(false);
            return -1;
        } else {
            ObvBackupKeyVerificationOutput verificationOutput = AppSingleton.getEngine().validateBackupSeed(backupSeed, backupContent.getValue());
            backupKeyValid.setValue(verificationOutput.verificationStatus == ObvBackupKeyVerificationOutput.STATUS_SUCCESS);
            return verificationOutput.verificationStatus;
        }
    }


    // endregion
}
