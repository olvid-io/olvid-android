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

package io.olvid.messenger.plus_button;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.jose4j.jwk.JsonWebKeySet;

import java.util.List;

import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff;

public class PlusButtonViewModel extends ViewModel {
    public enum SEARCH_STATUS {
        NONE,
        SEARCHING,
        DONE,
    }

    @Nullable private OwnedIdentity currentIdentity;
    private String scannedUri = null;
    private boolean deepLinked = false;

    private final MutableLiveData<List<JsonKeycloakUserDetails>> keycloakSearchResult = new MutableLiveData<>();
    private final MutableLiveData<Integer> keycloakSearchMissingResults = new MutableLiveData<>();
    private final MutableLiveData<SEARCH_STATUS> keycloakSearchStatus = new MutableLiveData<>(SEARCH_STATUS.NONE);
    private String keycloakSearchString = null;

    private String currentIdentityServer;
    private String keycloakServerUrl;
    private String keycloakSerializedAuthState;
    private JsonWebKeySet keycloakJwks;
    private String keycloakClientId;
    private String keycloakClientSecret;
    private KeycloakUserDetailsAndStuff keycloakUserDetails;
    private boolean keycloakRevocationAllowed;

    private String fullScreenQrCodeUrl;
    private ObvMutualScanUrl mutualScanUrl;
    private byte[] mutualScanBytesContactIdentity;

    public void setKeycloakData(String serverUrl, String serializedAuthState, JsonWebKeySet jwks, String clientId, String clientSecret) {
        this.keycloakServerUrl = serverUrl;
        this.keycloakSerializedAuthState = serializedAuthState;
        this.keycloakJwks = jwks;
        this.keycloakClientId = clientId;
        this.keycloakClientSecret = clientSecret;
    }

    public void setCurrentIdentity(@Nullable OwnedIdentity currentIdentity) {
        this.currentIdentity = currentIdentity;
    }

    @Nullable public OwnedIdentity getCurrentIdentity() {
        return currentIdentity;
    }

    public void setCurrentIdentityServer(String currentIdentityServer) {
        this.currentIdentityServer = currentIdentityServer;
    }

    public String getCurrentIdentityServer() {
        return currentIdentityServer;
    }

    public String getKeycloakServerUrl() {
        return keycloakServerUrl;
    }

    public String getKeycloakSerializedAuthState() {
        return keycloakSerializedAuthState;
    }

    public void setKeycloakSerializedAuthState(String keycloakSerializedAuthState) {
        this.keycloakSerializedAuthState = keycloakSerializedAuthState;
    }

    public JsonWebKeySet getKeycloakJwks() {
        return keycloakJwks;
    }

    public String getKeycloakClientId() {
        return keycloakClientId;
    }

    public String getKeycloakClientSecret() {
        return keycloakClientSecret;
    }


    public void setKeycloakUserDetails(KeycloakUserDetailsAndStuff keycloakUserDetails) {
        this.keycloakUserDetails = keycloakUserDetails;
    }

    public KeycloakUserDetailsAndStuff getKeycloakUserDetails() {
        return keycloakUserDetails;
    }

    public boolean isKeycloakRevocationAllowed() {
        return keycloakRevocationAllowed;
    }

    public void setKeycloakRevocationAllowed(boolean keycloakRevocationAllowed) {
        this.keycloakRevocationAllowed = keycloakRevocationAllowed;
    }

    public String getScannedUri() {
        return scannedUri;
    }

    public void setScannedUri(String scannedUri) {
        this.scannedUri = scannedUri;
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "RedundantSuppression"})
    public boolean isDeepLinked() {
        return deepLinked;
    }

    public void setDeepLinked(boolean deepLinked) {
        this.deepLinked = deepLinked;
    }

    public LiveData<List<JsonKeycloakUserDetails>> getKeycloakSearchResult() {
        return keycloakSearchResult;
    }

    public void setKeycloakSearchResult(List<JsonKeycloakUserDetails> keycloakSearchResult) {
        this.keycloakSearchResult.postValue(keycloakSearchResult);
    }

    public LiveData<Integer> getKeycloakSearchMissingResults() {
        return keycloakSearchMissingResults;
    }

    public void setKeycloakSearchMissingResults(int keycloakSearchResult) {
        this.keycloakSearchMissingResults.postValue(keycloakSearchResult);
    }


    public LiveData<SEARCH_STATUS> getKeycloakSearchStatus() {
        return keycloakSearchStatus;
    }

    public void setKeycloakSearchStatus(SEARCH_STATUS keycloakSearchStatus) {
        this.keycloakSearchStatus.postValue(keycloakSearchStatus);
    }

    public String getKeycloakSearchString() {
        return keycloakSearchString;
    }

    public void setKeycloakSearchString(String keycloakSearchString) {
        this.keycloakSearchString = keycloakSearchString;
    }

    public String getFullScreenQrCodeUrl() {
        return fullScreenQrCodeUrl;
    }

    public void setFullScreenQrCodeUrl(String fullScreenQrCodeUrl) {
        this.fullScreenQrCodeUrl = fullScreenQrCodeUrl;
    }

    public ObvMutualScanUrl getMutualScanUrl() {
        return mutualScanUrl;
    }

    public byte[] getMutualScanBytesContactIdentity() {
        return mutualScanBytesContactIdentity;
    }

    public void setMutualScanUrl(ObvMutualScanUrl mutualScanUrl, byte[] mutualScanBytesContactIdentity) {
        this.mutualScanUrl = mutualScanUrl;
        this.mutualScanBytesContactIdentity = mutualScanBytesContactIdentity;
    }
}
