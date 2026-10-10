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

package io.olvid.messenger.discussion.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.ProvideOlvidRipple
import io.olvid.messenger.lock_screen.LockableActivity
import io.olvid.messenger.troubleshooting.startSettingsActivity
import kotlinx.coroutines.launch

/** In-house document scanner: returns the PDF file uri as the result data. */
class DocumentScannerActivity : LockableActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)
        val viewModel by viewModels<DocumentScannerViewModel>()
        setContent {
            ProvideOlvidRipple {
                DocumentScanner(
                    viewModel = viewModel,
                    onClose = { finish() },
                    onDone = { uri ->
                        setResult(RESULT_OK, Intent().setData(uri))
                        finish()
                    },
                )
            }
        }
    }
}

private enum class ScannerScreen { CAMERA, REVIEW }

@Composable
private fun DocumentScanner(viewModel: DocumentScannerViewModel, onClose: () -> Unit, onDone: (Uri) -> Unit) {
    val activity = LocalActivity.current as LockableActivity
    fun cameraGranted() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(cameraGranted()) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    // no rationale after a denial: the system will not show the dialog again
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        permissionBlocked = !granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionDenied) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // back from the app settings
    LifecycleResumeEffect(Unit) {
        hasPermission = cameraGranted()
        onPauseOrDispose { }
    }
    val requestCameraPermission = {
        if (permissionBlocked) {
            App.prepareForStartActivityForResult(activity)
            activity.startSettingsActivity()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var screen by rememberSaveable { mutableStateOf(ScannerScreen.CAMERA) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var croppedPageId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val requestClose = {
        if (viewModel.pages.isEmpty()) onClose() else showDiscardDialog = true
    }

    val croppedPage = croppedPageId?.let { id -> viewModel.pages.firstOrNull { it.id == id } }
    when {
        croppedPage != null -> {
            BackHandler { croppedPageId = null }
            ScanCropScreen(
                page = croppedPage,
                onCancel = { croppedPageId = null },
                onApply = { quad, rotation ->
                    viewModel.update(croppedPage.copy(quad = quad, rotation = rotation))
                    croppedPageId = null
                },
            )
        }

        screen == ScannerScreen.REVIEW && viewModel.pages.isNotEmpty() -> {
            BackHandler { requestClose() }
            ScanReviewScreen(
                viewModel = viewModel,
                currentPage = currentPage.coerceIn(0, viewModel.pages.lastIndex),
                onCurrentPageChange = { currentPage = it },
                onDiscard = requestClose,
                onAddPage = {
                    viewModel.retakeIndex = null
                    screen = ScannerScreen.CAMERA
                },
                onRetake = { index ->
                    viewModel.retakeIndex = index
                    screen = ScannerScreen.CAMERA
                },
                onCrop = { croppedPageId = it.id },
                onDelete = { page ->
                    viewModel.delete(page)
                    if (viewModel.pages.isEmpty()) screen = ScannerScreen.CAMERA
                },
                onSave = {
                    scope.launch {
                        viewModel.exporting = true
                        val file = viewModel.exportPdf()
                        viewModel.exporting = false
                        if (file != null) {
                            onDone(Uri.fromFile(file))
                        } else {
                            App.toast(R.string.toast_message_scan_failed, Toast.LENGTH_SHORT)
                        }
                    }
                },
            )
        }

        else -> {
            val backToPages = {
                viewModel.retakeIndex = null
                screen = ScannerScreen.REVIEW
            }
            BackHandler {
                if (viewModel.pages.isEmpty()) onClose() else backToPages()
            }
            ScanCameraScreen(
                viewModel = viewModel,
                hasCameraPermission = hasPermission,
                cameraPermissionDenied = permissionDenied,
                permissionBlocked = permissionBlocked,
                automatic = viewModel.automatic,
                onRequestCameraPermission = requestCameraPermission,
                onAutomaticChange = { automatic ->
                    viewModel.automatic = automatic
                },
                onClose = { if (viewModel.pages.isEmpty()) onClose() else backToPages() },
                onCaptured = { index ->
                    currentPage = index
                    screen = ScannerScreen.REVIEW
                },
                onShowPages = backToPages,
            )
        }
    }

    if (showDiscardDialog) {
        DialogSecure(onDismissRequest = { showDiscardDialog = false }) {
            BaseDialogContent(
                title = stringResource(R.string.dialog_title_discard_scan),
                content = {
                    Text(
                        text = stringResource(R.string.dialog_message_discard_scan),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                },
                actions = {
                    Spacer(Modifier.weight(1f, true))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        contentColor = colorResource(R.color.greyTint)
                    ) { showDiscardDialog = false }
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_discard),
                        contentColor = colorResource(R.color.red),
                    ) {
                        showDiscardDialog = false
                        onClose()
                    }
                },
            )
        }
    }
}
