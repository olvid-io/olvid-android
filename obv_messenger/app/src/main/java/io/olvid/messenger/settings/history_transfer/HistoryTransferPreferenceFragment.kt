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

package io.olvid.messenger.settings.history_transfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.history_transfer.HistoryTransferActivity
import io.olvid.messenger.history_transfer.IncomingTransferConfirmationDialog
import io.olvid.messenger.history_transfer.TransferNotificationService
import io.olvid.messenger.history_transfer.TransferService
import io.olvid.messenger.history_transfer.types.TransferProgress
import io.olvid.messenger.history_transfer.types.TransferScope
import io.olvid.messenger.history_transfer.types.TransferTransportType
import io.olvid.messenger.settings.history_transfer.composables.exportScopeScreen
import io.olvid.messenger.settings.history_transfer.composables.exportZipScreen
import io.olvid.messenger.settings.history_transfer.composables.importZipScreen
import io.olvid.messenger.settings.history_transfer.composables.pickProfileScreen
import io.olvid.messenger.settings.history_transfer.composables.pickTargetDeviceScreen
import io.olvid.messenger.settings.history_transfer.composables.pickTransferMethodScreen
import io.olvid.messenger.settings.history_transfer.composables.webRTCInstructionsScreen
import io.olvid.messenger.settings.history_transfer.composables.welcomeScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class HistoryTransferPreferenceFragment : Fragment() {
    private val viewModel: HistoryTransferViewModel by activityViewModels()
    private var  exportToZipSuccessCallback: (() -> Unit)? = null

    private var exportToZipLauncher: ActivityResultLauncher<String?> = registerForActivityResult<String?, Uri?>(
        CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            exportToZipSuccessCallback?.let {
                exportToZipSuccessCallback = null
                it.invoke()
            }
            onExportToZipFileSelected(
                writableZipUri = uri,
                password = viewModel.zipExportPassword,
                exportScope = viewModel.exportScope.value
            )
        }
    }

    private var importFromZipLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult: ActivityResult? ->
        viewModel.pickingFile.value = false
        if (activityResult?.data == null || activityResult.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        activityResult.data?.data?.let { zipFileUri ->
            viewModel.selectedZipUri.value = zipFileUri
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(layoutInflater.context).apply {
            setContent {
                val navController = rememberNavController()
                val ownedIdentityList = viewModel.ownedIdentityListLiveData.observeAsState()

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        startDestination = HistoryTransferRoutes.WELCOME_SCREEN
                    ) {
                        welcomeScreen(
                            onImport = {
                                viewModel.importMode.value = true
                                navController.navigate(HistoryTransferRoutes.PICK_PROFILE_SCREEN)
                            },
                            onExport = {
                                viewModel.importMode.value = false
                                navController.navigate(HistoryTransferRoutes.PICK_PROFILE_SCREEN)
                            },
                        )

                        pickProfileScreen(
                            onProfileSelected = { ownedIdentity ->
                                viewModel.selectedOwnedIdentity.value = ownedIdentity
                                navController.navigate(HistoryTransferRoutes.PICK_TRANSFER_METHOD_SCREEN)
                            },
                            importMode = viewModel.importMode,
                            ownedIdentityList = ownedIdentityList
                        )

                        pickTransferMethodScreen(
                            onMethodSelected = { method ->
                                viewModel.transferMethod.value = method
                                when (method) {
                                    TransferMethod.ZIP -> {
                                        if (viewModel.importMode.value) {
                                            navController.navigate(HistoryTransferRoutes.IMPORT_ZIP_SCREEN)
                                        } else {
                                            navController.navigate(HistoryTransferRoutes.EXPORT_SCOPE_SCREEN)
                                        }
                                    }

                                    TransferMethod.WEBRTC -> {
                                        if (viewModel.importMode.value) {
                                            navController.navigate(HistoryTransferRoutes.WEBRTC_INSTRUCTIONS_SCREEN)
                                        } else {
                                            navController.navigate(HistoryTransferRoutes.PICK_TARGET_DEVICE_SCREEN)
                                        }
                                    }
                                }
                            },
                            importMode = viewModel.importMode,
                            selectedOwnedIdentity = viewModel.selectedOwnedIdentity
                        )

                        importZipScreen(
                            onChooseZipFile = {
                                viewModel.pickingFile.value = true
                                viewModel.selectedZipUri.value = null
                                importFromZipLauncher.launch(
                                    Intent(Intent.ACTION_GET_CONTENT)
                                        .setType("application/zip")
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                                )
                            },
                            selectedOwnedIdentity = viewModel.selectedOwnedIdentity,
                            pickingFile = viewModel.pickingFile,
                            selectedZipUri = viewModel.selectedZipUri,
                            onZipPasswordFound = { password ->
                                val zipUri =
                                    viewModel.selectedZipUri.value ?: return@importZipScreen
                                val bytesOwnedIdentity =
                                    viewModel.selectedOwnedIdentity.value?.bytesOwnedIdentity
                                        ?: return@importZipScreen

                                // clear the zip uri for following imports
                                viewModel.selectedZipUri.value = null

                                // return to the start screen
                                navController.popBackStack(
                                    HistoryTransferRoutes.WELCOME_SCREEN,
                                    false
                                )

                                onImportFromZipFileSelected(
                                    bytesOwnedIdentity = bytesOwnedIdentity,
                                    readableZipUri = zipUri,
                                    password = password
                                )
                            },
                        )

                        exportScopeScreen(
                            onChooseExportScope = { exportScope ->
                                viewModel.exportScope.value = exportScope

                                when (viewModel.transferMethod.value) {
                                    TransferMethod.ZIP -> {
                                        navController.navigate(HistoryTransferRoutes.EXPORT_ZIP_SCREEN)
                                    }

                                    TransferMethod.WEBRTC -> {
                                        navController.navigate(HistoryTransferRoutes.WEBRTC_INSTRUCTIONS_SCREEN)
                                    }
                                }
                            },
                            transferMethod = viewModel.transferMethod,
                        )

                        exportZipScreen(
                            onChooseZipFile = { password ->
                                viewModel.zipExportPassword = password
                                exportToZipSuccessCallback = {
                                    navController.popBackStack(
                                        HistoryTransferRoutes.WELCOME_SCREEN,
                                        false
                                    )
                                }
                                exportToZipLauncher.launch(
                                    System.currentTimeMillis().toExportFileName()
                                )
                            },
                            selectedOwnedIdentity = viewModel.selectedOwnedIdentity,
                            exportScope = viewModel.exportScope,
                            discussionCountLiveData = viewModel.discussionCountLiveData,
                            messageCountLiveData = viewModel.messageCountLiveData,
                            sha256sMapLiveData = viewModel.sha256sMapLiveData,
                        )

                        pickTargetDeviceScreen(
                            onBackPressed = {
                                navController.popBackStack()
                            },
                            onDeviceSelected = { ownedDevice ->
                                viewModel.selectedWebRTCTargetOwnedDevice.value = ownedDevice

                                navController.navigate(HistoryTransferRoutes.EXPORT_SCOPE_SCREEN)
                            },
                            selectedOwnedIdentity = viewModel.selectedOwnedIdentity,
                            deviceListLiveData = viewModel.deviceListLiveData,
                        )

                        webRTCInstructionsScreen(
                            onImportBackPressed = {
                                navController.popBackStack(
                                    HistoryTransferRoutes.WELCOME_SCREEN,
                                    false
                                )
                            },
                            onExportProceedPressed = {
                                viewModel.selectedWebRTCTargetOwnedDevice.value?.let { ownedDevice ->
                                    startWebRtcExport(
                                        bytesCurrentIdentity = ownedDevice.bytesOwnedIdentity,
                                        otherDeviceUid = ownedDevice.bytesDeviceUid,
                                        exportScope = viewModel.exportScope.value,
                                    )

                                    navController.popBackStack(
                                        HistoryTransferRoutes.WELCOME_SCREEN,
                                        false
                                    )
                                }
                            },
                            importMode = viewModel.importMode,
                            selectedOwnedDevice = viewModel.selectedWebRTCTargetOwnedDevice
                        )
                    }

                    val context = LocalContext.current

                    if (TransferService.transferInProgress.value != null) {
                        val ongoingTransferIdAndProgressState = TransferService.getCurrentTransferIdAndProgressState()

                        ongoingTransferIdAndProgressState?.takeIf {
                            it.second.value is TransferProgress.DestinationWaitingForConfirmation
                        }?.let { idAndProgressState ->
                            DialogSecure(
                                onDismissRequest = {},
                                properties = DialogProperties(dismissOnClickOutside = false)
                            ) {
                                IncomingTransferConfirmationDialog(
                                    onDismiss = {
                                        context.startService(Intent(context, TransferNotificationService::class.java).apply {
                                            putExtra(TransferNotificationService.EXTRA_TRANSFER_ID, idAndProgressState.first)
                                            action = TransferNotificationService.ACTION_ABORT
                                        })
                                    },
                                    onConfirm = {
                                        TransferService.acceptTransferRequest()
                                        context.startActivity(
                                            Intent(
                                                context,
                                                HistoryTransferActivity::class.java
                                            ).apply {
                                                putExtra(HistoryTransferActivity.TRANSFER_ID_INTENT_EXTRA, idAndProgressState.first)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                        )
                                    },
                                    deviceName = (idAndProgressState.second.value as? TransferProgress.DestinationWaitingForConfirmation)?.sourceDeviceName
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startWebRtcExport(bytesCurrentIdentity: ByteArray, otherDeviceUid: ByteArray, exportScope: ExportScope) {
        App.runThread {
            TransferService.initiateHistoryTransferToOtherDevice(
                transferTransportType = TransferTransportType.WebRtcWithOwnedDevice(
                    bytesOwnedIdentity = bytesCurrentIdentity,
                    bytesOtherDeviceUid = otherDeviceUid,
                ),
                transferScope = TransferScope.Profile(messagesOnly = exportScope == ExportScope.MESSAGES_ONLY)
            )?.let { transferId ->
                Handler(Looper.getMainLooper()).post {
                    context?.startActivity(
                        Intent(
                            context,
                            HistoryTransferActivity::class.java
                        ).apply {
                            putExtra(HistoryTransferActivity.TRANSFER_ID_INTENT_EXTRA, transferId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                }
            } ?: run {
                App.toast(R.string.toast_message_unable_to_start_transfer, Toast.LENGTH_SHORT)
            }
        }
    }

    private fun onExportToZipFileSelected(writableZipUri: Uri, password: String?, exportScope: ExportScope) {
        App.runThread {
            AppSingleton.getBytesCurrentIdentity()?.let { bytesCurrentIdentity ->
                TransferService.initiateHistoryTransferToOtherDevice(
                    transferTransportType = TransferTransportType.ZipFileExport(
                        bytesOwnedIdentity = bytesCurrentIdentity,
                        zipWritableFileUri = writableZipUri,
                        password = password
                    ),
                    transferScope = TransferScope.Profile(messagesOnly = exportScope == ExportScope.MESSAGES_ONLY)
                )?.let { transferId ->
                    Handler(Looper.getMainLooper()).post {
                        context?.startActivity(
                            Intent(
                                context,
                                HistoryTransferActivity::class.java
                            ).apply {
                                putExtra(
                                    HistoryTransferActivity.TRANSFER_ID_INTENT_EXTRA,
                                    transferId
                                )
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                    }
                } ?: run {
                    App.toast(R.string.toast_message_unable_to_start_transfer, Toast.LENGTH_SHORT)
                }
            }
        }
    }

    private fun onImportFromZipFileSelected(bytesOwnedIdentity: ByteArray, readableZipUri: Uri, password: String?) {
        App.runThread {
            TransferService.initiateHistoryTransferToOtherDevice(
                transferTransportType = TransferTransportType.ZipFileImport(
                    bytesOwnedIdentity = bytesOwnedIdentity,
                    zipReadableFileUri = readableZipUri,
                    password = password
                ),
                transferScope = TransferScope.Profile(messagesOnly = false)
            )?.let { transferId ->
                Handler(Looper.getMainLooper()).post {
                    context?.startActivity(
                        Intent(
                            context,
                            HistoryTransferActivity::class.java
                        ).apply {
                            putExtra(HistoryTransferActivity.TRANSFER_ID_INTENT_EXTRA, transferId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                }
            } ?: run {
                App.toast(R.string.toast_message_unable_to_start_transfer, Toast.LENGTH_SHORT)
            }
        }
    }
}

fun Long.toExportFileName(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd").withZone(ZoneId.systemDefault())
    return "olvid_export_${formatter.format(Instant.ofEpochMilli(this))}.zip"
}