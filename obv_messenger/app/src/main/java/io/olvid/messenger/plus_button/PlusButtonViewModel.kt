/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
package io.olvid.messenger.plus_button

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import io.olvid.messenger.plus_button.PlusButtonViewModel.SEARCH_STATUS.NONE
import org.jose4j.jwk.JsonWebKeySet

class PlusButtonViewModel : ViewModel() {
    enum class SEARCH_STATUS {
        NONE,
        SEARCHING,
        DONE,
    }

    @JvmField
    var currentIdentity: OwnedIdentity? = null
    @JvmField
    var scannedUri: String? = null
    var isDeepLinked: Boolean = false

    private val keycloakSearchResult = MutableLiveData<List<JsonKeycloakUserDetails>>()
    private val keycloakSearchMissingResults = MutableLiveData<Int>()
    private val keycloakSearchStatus = MutableLiveData(NONE)
    @JvmField
    var keycloakSearchString: String? = null

    @JvmField
    var currentIdentityServer: String? = null
    var keycloakServerUrl: String? = null
        private set
    @JvmField
    var keycloakSerializedAuthState: String? = null
    var keycloakJwks: JsonWebKeySet? = null
        private set
    var keycloakClientId: String? = null
        private set
    var keycloakClientSecret: String? = null
        private set
    @JvmField
    var keycloakUserDetails: KeycloakUserDetailsAndStuff? = null
    var isKeycloakRevocationAllowed: Boolean = false
    var isKeycloakTransferRestricted: Boolean = false

    @JvmField
    var fullScreenQrCodeUrl: String? = null
    var mutualScanUrl: ObvMutualScanUrl? = null
        private set
    var isDismissOnMutualScanFinished: Boolean = false
    var mutualScanBytesContactIdentity: ByteArray? = null
        private set

    fun setKeycloakData(
        serverUrl: String?,
        serializedAuthState: String?,
        jwks: JsonWebKeySet?,
        clientId: String?,
        clientSecret: String?
    ) {
        this.keycloakServerUrl = serverUrl
        this.keycloakSerializedAuthState = serializedAuthState
        this.keycloakJwks = jwks
        this.keycloakClientId = clientId
        this.keycloakClientSecret = clientSecret
    }


    fun getKeycloakSearchResult(): LiveData<List<JsonKeycloakUserDetails>> {
        return keycloakSearchResult
    }

    fun setKeycloakSearchResult(keycloakSearchResult: List<JsonKeycloakUserDetails>) {
        this.keycloakSearchResult.postValue(keycloakSearchResult)
    }

    fun getKeycloakSearchMissingResults(): LiveData<Int> {
        return keycloakSearchMissingResults
    }

    fun setKeycloakSearchMissingResults(keycloakSearchResult: Int) {
        keycloakSearchMissingResults.postValue(keycloakSearchResult)
    }


    fun getKeycloakSearchStatus(): LiveData<SEARCH_STATUS> {
        return keycloakSearchStatus
    }

    fun setKeycloakSearchStatus(keycloakSearchStatus: SEARCH_STATUS) {
        this.keycloakSearchStatus.postValue(keycloakSearchStatus)
    }

    fun setMutualScanUrl(
        mutualScanUrl: ObvMutualScanUrl?,
        mutualScanBytesContactIdentity: ByteArray?
    ) {
        this.mutualScanUrl = mutualScanUrl
        this.mutualScanBytesContactIdentity = mutualScanBytesContactIdentity
    }
}
