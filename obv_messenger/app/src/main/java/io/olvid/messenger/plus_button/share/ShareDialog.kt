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

package io.olvid.messenger.plus_button.share

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.DialogFullScreen
import java.util.EnumMap
import java.util.regex.Pattern

@Composable
fun ShareDialog(onDismissRequest: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current

    val saveQrCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
        onResult = { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            val ownedIdentity = 
                AppSingleton.getCurrentIdentityLiveData().value
                    ?: return@rememberLauncherForActivityResult

            runCatching {
                val urlIdentity = 
                    ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName)
                val qrCodeData = urlIdentity.getUrlRepresentation(false)

                val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
                hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
                hints[EncodeHintType.MARGIN] = 1
                val qrcode = MultiFormatWriter().encode(
                    qrCodeData,
                    BarcodeFormat.QR_CODE,
                    512,
                    512,
                    hints
                )
                val w = qrcode.width
                val h = qrcode.height
                val pixels = IntArray(w * h)
                for (y in 0 until h) {
                    val offset = y * w
                    for (x in 0 until w) {
                        pixels[offset + x] = if (qrcode[x, y]) Color.BLACK else Color.WHITE
                    }
                }
                val bitmap = createBitmap(w, h)
                bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                App.toast(
                    R.string.toast_message_qr_code_saved,
                    Toast.LENGTH_SHORT,
                    Gravity.TOP
                )
            }.onFailure { e ->
                e.printStackTrace()
                App.toast(
                    R.string.toast_message_failed_to_save_qr_code, Toast.LENGTH_SHORT,
                    Gravity.TOP
                )
            }
        }
    )

    DialogFullScreen(onDismissRequest = onDismissRequest) {
        ShareScreen(
            ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value,
            onDone = onDismissRequest,
            onCopy = {
                val ownedIdentity = 
                    AppSingleton.getCurrentIdentityLiveData().value ?: return@ShareScreen
                val urlIdentity = 
                    ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName)
                clipboardManager.nativeClipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "uri",
                        urlIdentity.getUrlRepresentation(false)
                    )
                )
                App.toast(
                    R.string.toast_message_clipboard_copied, Toast.LENGTH_SHORT,
                    Gravity.TOP
                )
            },
            onDownload = {
                val ownedIdentity = 
                    AppSingleton.getCurrentIdentityLiveData().value ?: return@ShareScreen
                val sanitizedDisplayName = sanitizeFileName(ownedIdentity.displayName)
                saveQrCodeLauncher.launch("${sanitizedDisplayName}_olvid_qr_code.png")
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                val ownedIdentity = 
                    AppSingleton.getCurrentIdentityLiveData().value ?: return@ShareScreen
                val urlIdentity: ObvUrlIdentity
                val inviteName: String
                val identityDetails = ownedIdentity.getIdentityDetails()
                if (identityDetails != null) {
                    urlIdentity = ObvUrlIdentity(
                        ownedIdentity.bytesOwnedIdentity,
                        identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                            false
                        )
                    )
                    inviteName = identityDetails.formatDisplayName(
                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                        false
                    )
                } else {
                    urlIdentity = 
                        ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName)
                    inviteName = ownedIdentity.displayName
                }
                intent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    context.getString(R.string.message_user_invitation_subject, inviteName)
                )
                intent.putExtra(
                    Intent.EXTRA_TEXT,
                    context.getString(
                        R.string.message_user_invitation,
                        inviteName,
                        urlIdentity.getUrlRepresentation(false)
                    )
                )
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.getString(R.string.title_invite_chooser)
                    )
                )
            }
        )
    }
}

private fun sanitizeFileName(fileName: String): String {
    val invalidCharsPattern = Pattern.compile("[\\\\/:*?\"<>| ]")
    return invalidCharsPattern.matcher(fileName).replaceAll("_").lowercase()
}