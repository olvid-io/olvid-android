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

package io.olvid.messenger.settings

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.history_transfer.HistoryTransferActivity
import io.olvid.messenger.history_transfer.TransferService
import io.olvid.messenger.history_transfer.types.TransferScope
import io.olvid.messenger.history_transfer.types.TransferTransportType


class HistoryTransferPreferenceFragment : Fragment() {
    private val viewModel: HistoryTransferViewModel by activityViewModels()

    private var exportToZipLauncher: ActivityResultLauncher<String?> = registerForActivityResult<String?, Uri?>(
        CreateDocument("application/zip"),
        this::onExportToZipFileSelected
    )
    private var importFromZipLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult: ActivityResult? ->
        if (activityResult?.data == null || activityResult.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        activityResult.data?.data?.let { zipFileUri ->
            onImportFromZipFileSelected(zipFileUri)
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(layoutInflater.context).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(R.color.almostWhite))
                        .verticalScroll(rememberScrollState())
                        .padding(all = 16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var showDeviceSelectionDialog by rememberSaveable { mutableStateOf(false) }

                    Icon(
                        modifier = Modifier
                            .size(128.dp)
                            .background(
                                color = colorResource(R.color.olvid_gradient_light),
                                shape = CircleShape
                            )
                            .padding(16.dp),
                        painter = painterResource(R.drawable.ic_transfer),
                        tint = colorResource(R.color.alwaysWhite),
                        contentDescription = null
                    )

                    Spacer(Modifier.height(32.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            contentColor = colorResource(R.color.almostBlack),
                            containerColor = colorResource(R.color.lightGrey)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = colorResource(R.color.orange)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            text = stringResource(R.string.transfer_disclaimer_beta),
                            style = OlvidTypography.body2
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    OlvidActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colorResource(R.color.olvid_gradient_light),
                        contentColor = colorResource(R.color.alwaysWhite),
                        text = stringResource(R.string.button_label_transfer_through_webrtc),
                        onClick = { showDeviceSelectionDialog = true }
                    )

                    Spacer(Modifier.height(16.dp))

                    Row {
                        OlvidActionButton(
                            modifier = Modifier.weight(1f, true),
                            containerColor = Color.Transparent,
                            contentColor = colorResource(R.color.olvid_gradient_light),
                            outlinedColor = colorResource(R.color.olvid_gradient_light),
                            text = stringResource(R.string.button_label_export_zip)
                        ) {
                            exportToZipLauncher.launch("olvid_export.zip")
                        }

                        Spacer(Modifier.width(16.dp))

                        OlvidActionButton(
                            modifier = Modifier.weight(1f, true),
                            containerColor = Color.Transparent,
                            contentColor = colorResource(R.color.olvid_gradient_light),
                            outlinedColor = colorResource(R.color.olvid_gradient_light),
                            text = stringResource(R.string.button_label_import_zip)
                        ) {
                            importFromZipLauncher.launch(
                                Intent(Intent.ACTION_GET_CONTENT)
                                    .setType("application/zip")
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                            )
                        }
                    }


                    if (showDeviceSelectionDialog) {
                        DialogSecure(
                            onDismissRequest = {
                                showDeviceSelectionDialog = false
                            }
                        ) {
                            BaseDialogContent(
                                title = stringResource(R.string.dialog_title_transfer_choose_target_device),
                                content = {
                                    val context = LocalContext.current
                                    val devices by viewModel.ownedDeviceList.observeAsState()
                                    // if only one device, this is our owned device
                                    if ((devices?.size ?: 0) > 1) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = spacedBy(16.dp)
                                        ) {
                                            devices?.filter { it.currentDevice.not() }
                                                ?.forEach { device ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = ripple()
                                                            ) {
                                                                startWebRtcExport(
                                                                    device.bytesOwnedIdentity,
                                                                    device.bytesDeviceUid
                                                                )
                                                                showDeviceSelectionDialog = false
                                                            }
                                                            .background(colorResource(R.color.lightGrey))
                                                            .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 12.dp
                                                            ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(colorResource(R.color.almostWhite))
                                                                .padding(all = 10.dp),
                                                            painter = painterResource(R.drawable.ic_device),
                                                            tint = colorResource(R.color.almostBlack),
                                                            contentDescription = null,
                                                        )
                                                        Column(
                                                            modifier = Modifier
                                                                .weight(1f, true)
                                                                .padding(horizontal = 16.dp),
                                                            horizontalAlignment = Alignment.Start,
                                                            verticalArrangement = Arrangement.SpaceAround,
                                                        ) {
                                                            Text(
                                                                text = device.getDisplayNameOrDeviceHexName(
                                                                    context
                                                                ),
                                                                style = OlvidTypography.body1,
                                                                color = colorResource(R.color.almostBlack),
                                                                maxLines = 2,
                                                            )
                                                            device.lastRegistrationTimestamp?.let {
                                                                Spacer(Modifier.height(4.dp))
                                                                Text(
                                                                    text = stringResource(
                                                                        R.string.text_last_online,
                                                                        StringUtils.getDateString(
                                                                            context,
                                                                            it
                                                                        )
                                                                    ),
                                                                    style = OlvidTypography.body2,
                                                                    color = colorResource(R.color.greyTint),
                                                                    maxLines = 2,
                                                                )
                                                            }
                                                        }
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_chevron_right),
                                                            tint = colorResource(R.color.almostBlack),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                }
                                        }
                                    } else {
                                        Text(
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            text = stringResource(R.string.transfer_label_no_other_active_device)
                                        )
                                    }
                                }
                            ) {
                                Spacer(Modifier.weight(1f, true))
                                OlvidTextButton(
                                    text = stringResource(R.string.button_label_cancel),
                                ) {
                                    showDeviceSelectionDialog = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startWebRtcExport(bytesCurrentIdentity: ByteArray, otherDeviceUid: ByteArray) {
        App.runThread {
            if (TransferService.initiateHistoryTransferToOtherDevice(
                    transferTransportType = TransferTransportType.WebRtcWithOwnedDevice(
                        bytesOwnedIdentity = bytesCurrentIdentity,
                        bytesOtherDeviceUid = otherDeviceUid
                    ),
                    transferScope = TransferScope.Profile
                )
            ) {
                Handler(Looper.getMainLooper()).post {
                    context?.startActivity(
                        Intent(
                            context,
                            HistoryTransferActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                }
            } else {
                App.toast(R.string.toast_message_unable_to_start_transfer, Toast.LENGTH_SHORT)
            }
        }
    }

    private fun onExportToZipFileSelected(writableZipUri: Uri) {
        App.runThread {
            AppSingleton.getBytesCurrentIdentity()?.let { bytesCurrentIdentity ->
                if (TransferService.initiateHistoryTransferToOtherDevice(
                        transferTransportType = TransferTransportType.ZipFileExport(bytesOwnedIdentity = bytesCurrentIdentity, zipWritableFileUri = writableZipUri),
                        transferScope = TransferScope.Profile
                    )
                ) {
                    Handler(Looper.getMainLooper()).post {
                        context?.startActivity(Intent(context, HistoryTransferActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                } else {
                    App.toast(R.string.toast_message_unable_to_start_transfer, Toast.LENGTH_SHORT)
                }
            }
        }
    }

    private fun onImportFromZipFileSelected(readableZipUri: Uri) {
        App.runThread {
            if (TransferService.initiateHistoryTransferToOtherDevice(
                    transferTransportType = TransferTransportType.ZipFileImport(zipReadableFileUri = readableZipUri),
                    transferScope = TransferScope.Profile
                )
            ) {
                Handler(Looper.getMainLooper()).post {
                    context?.startActivity(Intent(context, HistoryTransferActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            } else {
                App.toast(R.string.toast_message_unable_to_start_transfer, Toast.LENGTH_SHORT)
            }
        }
    }
}