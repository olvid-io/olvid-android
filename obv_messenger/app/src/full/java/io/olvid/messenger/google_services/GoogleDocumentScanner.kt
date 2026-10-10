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

package io.olvid.messenger.google_services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.olvid.engine.Logger

object GoogleDocumentScanner {
    private val scanner: GmsDocumentScanner by lazy {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .build()
        )
    }

    fun isAvailable(context: Context): Boolean =
        GoogleServicesUtils.googleServicesAvailable(context)

    // getStartScanIntent succeeds even when the scanner module is not installed: it then opens a Play services
    // screen waiting for the download, which never ends where downloads are blocked. Check the module first.
    fun getStartScanIntent(
        activity: Activity,
        onSuccess: (IntentSender) -> Unit,
        onFailure: () -> Unit,
    ) {
        val moduleInstallClient = ModuleInstall.getClient(activity)
        moduleInstallClient.areModulesAvailable(scanner)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    scanner.getStartScanIntent(activity)
                        .addOnSuccessListener(onSuccess)
                        .addOnFailureListener {
                            Logger.w("Unable to start document scanner: $it")
                            onFailure()
                        }
                } else {
                    Logger.i("Document scanner module not installed, requesting a background download")
                    moduleInstallClient.deferredInstall(scanner)
                    onFailure()
                }
            }
            .addOnFailureListener {
                Logger.w("Unable to check document scanner module availability: $it")
                onFailure()
            }
    }

    fun getPdfUri(data: Intent?): Uri? =
        GmsDocumentScanningResult.fromActivityResultIntent(data)?.pdf?.uri
}
