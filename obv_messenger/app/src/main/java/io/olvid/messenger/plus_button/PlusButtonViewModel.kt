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
package io.olvid.messenger.plus_button

import android.content.res.Resources
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import io.olvid.messenger.settings.SettingsActivity.Companion.qrCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jose4j.jwk.JsonWebKeySet
import kotlin.math.min

// Define ScanState
sealed class ScanUiState {
    object IdleScanning : ScanUiState()
    data class InvitationScanned(val remoteInvitation: Boolean, val contactUrlIdentity: ObvUrlIdentity) : ScanUiState()
    object ConfigurationScanned : ScanUiState()
    object WebClientScanned : ScanUiState()
    object UrlScan : ScanUiState()
    object TextScan : ScanUiState()

    // States for Mutual Scan Flow
    data class MutualScanProcessing(val url: ObvMutualScanUrl, val contactName: String) :
        ScanUiState()

    data class MutualScanPending(val contactName: String) : ScanUiState()
    data class MutualScanSuccess(val contactName: String, val discussionId: Long, val timeout : Boolean = false) : ScanUiState()
    data class MutualScanError(val message: String) : ScanUiState()
}

class PlusButtonViewModel : ViewModel() {

    var qrImageBitmap by mutableStateOf<ImageBitmap?>(null)

    @JvmField
    var currentIdentity: OwnedIdentity? = null

    @JvmField
    var scannedUri: String? = null
    var isDeepLinked: Boolean = false

    @JvmField
    var currentIdentityServer: String? = null
    var keycloakServerUrl: String? = null
        private set

    var keycloakSerializedAuthState: String? by mutableStateOf(null)
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

    var mutualScanUrl: ObvMutualScanUrl? = null
        private set
    var mutualScanBytesContactIdentity: ByteArray? = null
        private set
    private var mutualScanContactName: String? = null
    var mutualScanDiscussionId: Long? by mutableStateOf(null)

    private val _scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.IdleScanning)
    val scanUiState = _scanUiState.asStateFlow()

    fun updateScanState(newState: ScanUiState) {
        _scanUiState.value = newState
    }

    fun processMutualScan(activity: AppCompatActivity, url: ObvMutualScanUrl, contactName: String) {
        this.mutualScanUrl = url
        updateScanState(ScanUiState.MutualScanProcessing(url, contactName))
        this.mutualScanContactName = contactName
        this.mutualScanBytesContactIdentity = url.bytesIdentity
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val bytesOwnedIdentity = currentIdentity?.bytesOwnedIdentity ?: return@launch
            val bytesContactIdentity = url.bytesIdentity

            if (bytesOwnedIdentity.contentEquals(bytesContactIdentity)) {
                updateScanState(
                    ScanUiState.MutualScanError(
                        App.getContext()
                            .getString(R.string.text_explanation_warning_cannot_invite_yourself)
                    )
                )
                return@launch
            }

            if (AppSingleton.getEngine()
                    .verifyMutualScanSignedNonceUrl(bytesOwnedIdentity, mutualScanUrl)
            ) {
                // Check if discussion exists to decide which listener to use
                val existingDiscussion = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance().discussionDao()
                        .getByContactWithAnyStatus(bytesOwnedIdentity, bytesContactIdentity)
                }

                if (existingDiscussion == null || !existingDiscussion.isNormal) {
                    // Not a contact yet, observe LiveData for discussion creation
                    val discussionLiveData = AppDatabase.getInstance().discussionDao()
                        .getByContactLiveData(bytesOwnedIdentity, bytesContactIdentity)
                    withContext(Dispatchers.Main) {
                        discussionLiveData.observe(activity) { discussion ->
                            if (discussion != null && discussion.isNormal) {
                                updateScanState(ScanUiState.MutualScanSuccess(contactName, discussion.id, System.currentTimeMillis() - start > 5000))
                                discussionLiveData.removeObservers(activity)
                            }
                        }
                    }
                } else {
                    mutualScanDiscussionId = existingDiscussion.id
                }

                // Start the protocol
                runCatching {
                    AppSingleton.getEngine().startMutualScanTrustEstablishmentProtocol(
                        bytesOwnedIdentity,
                        bytesContactIdentity,
                        url.signature
                    )
                }.onFailure {
                    updateScanState(
                        ScanUiState.MutualScanError(
                            App.getContext()
                                .getString(R.string.toast_message_failed_to_invite_contact)
                        )
                    )
                    return@launch
                }

                // 5-second timeout
                delay(5000)
                // Check if we are still in the processing state before changing to pending
                if (_scanUiState.value is ScanUiState.MutualScanProcessing) {
                   updateScanState(ScanUiState.MutualScanPending(contactName))
                }
            } else {
                updateScanState(ScanUiState.MutualScanError(
                    App.getContext().getString(
                        R.string.text_explanation_invalid_mutual_scan_qr_code,
                        contactName
                    )
                ))
                return@launch
            }

        }
    }

    fun resetScanState() {
        updateScanState(ScanUiState.IdleScanning)
    }

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

    fun getQrImage(qrCodeData: String) {
        qrImageBitmap = runCatching {
            val hints = HashMap<EncodeHintType?, Any?>()
            hints.put(EncodeHintType.MARGIN, 0)

            when (qrCorrectionLevel) {
                "L" -> hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
                "Q" -> hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q)
                "H" -> hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                "M" -> hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                else -> hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            }
            val qrcode = MultiFormatWriter().encode(qrCodeData, BarcodeFormat.QR_CODE, 0, 0, hints)
            val w = qrcode.width
            val h = qrcode.height
            val onColor = ContextCompat.getColor(App.getContext(), R.color.black)

            val pixels = IntArray(h * w)
            var offset = 0
            for (y in 0..<h) {
                for (x in 0..<w) {
                    pixels[offset++] = if (qrcode.get(x, y)) onColor else 0
                }
            }
            val smallQrCodeBitmap = createBitmap(w, h)
            smallQrCodeBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            val metrics = Resources.getSystem().displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val size = min(height, width)
            smallQrCodeBitmap.scale(size, size, false).asImageBitmap()
        }.getOrNull()
    }

    fun handleLink(activity: AppCompatActivity, link: String) {
        val mutualScanMatcher =
            ObvMutualScanUrl.MUTUAL_SCAN_PATTERN.matcher(link)
        val invitationMatcher =
            ObvLinkActivity.INVITATION_PATTERN.matcher(link)
        val configurationMatcher =
            ObvLinkActivity.CONFIGURATION_PATTERN.matcher(link)
        val webClientMatcher =
            ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(link)
        val urlMatcher = Patterns.WEB_URL.matcher(link)

        if (mutualScanMatcher.find()) {
            val url = ObvMutualScanUrl.fromUrlRepresentation(
                mutualScanMatcher.group(0)
            )
            processMutualScan(activity, url, StringUtils.removeCompanyFromDisplayName(url.displayName))
        } else if (invitationMatcher.find()) {
            scannedUri = invitationMatcher.group(0)
            val bytesOwnedIdentity = currentIdentity?.bytesOwnedIdentity ?: return
            val contactUrlIdentity = ObvUrlIdentity.fromUrlRepresentation(scannedUri) ?: return
            var remoteInvitation = false
            try {
                remoteInvitation = invitationMatcher.group(2) != "1"
            } catch (_: Exception) { }

            if (bytesOwnedIdentity.contentEquals(contactUrlIdentity.bytesIdentity)) {
                updateScanState(
                    ScanUiState.MutualScanError(
                        App.getContext()
                            .getString(R.string.text_explanation_warning_cannot_invite_yourself)
                    )
                )
            } else {
                mutualScanUrl = AppSingleton.getEngine().computeMutualScanSignedNonceUrl(
                    contactUrlIdentity.bytesIdentity,
                    currentIdentity?.bytesOwnedIdentity,
                    currentIdentity?.getIdentityDetails()?.formatDisplayName(
                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                        false
                    ) ?: currentIdentity?.displayName
                )?.apply {
                    getQrImage(this.urlRepresentation)
                }
                mutualScanBytesContactIdentity = contactUrlIdentity.bytesIdentity
                updateScanState(ScanUiState.InvitationScanned(remoteInvitation, contactUrlIdentity))
            }
        } else if (configurationMatcher.find()) {
            scannedUri = configurationMatcher.group(0)
            updateScanState(ScanUiState.ConfigurationScanned)
        } else if (webClientMatcher.find()) {
            scannedUri = webClientMatcher.group(0)
            updateScanState(ScanUiState.WebClientScanned)
        } else if (urlMatcher.find()) {
            scannedUri = urlMatcher.group(0)
            updateScanState(ScanUiState.UrlScan)
        } else {
            scannedUri = link
            updateScanState(ScanUiState.TextScan)
        }
    }
}
