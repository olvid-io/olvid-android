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

package io.olvid.messenger.history_transfer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager.LayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.CustomDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidOutlinedActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.history_transfer.HistoryTransferActivity.Companion.TRANSFER_ID_INTENT_EXTRA
import io.olvid.messenger.history_transfer.types.TransferProgress
import io.olvid.messenger.settings.SettingsActivity.Companion.overrideContextScales


class WebrtcIncomingTransferActivity : AppCompatActivity() {
    var abortOnDismiss = true
    var transferId : String? = null

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(overrideContextScales(baseContext))
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        @Suppress("DEPRECATION")
        window.addFlags(
            LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or LayoutParams.FLAG_KEEP_SCREEN_ON
                    or LayoutParams.FLAG_TURN_SCREEN_ON
        )

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )

        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        transferId = intent?.getStringExtra(TRANSFER_ID_INTENT_EXTRA) ?: run {
            finish()
            return
        }

        setContent {
            val progress = TransferService.getTransferProgress(transferId!!)?.third
            // observe the transferInProgress state and dismiss if the transfer is interrupted.
            LaunchedEffect(TransferService.transferInProgress.value, progress?.value) {
                if (TransferService.transferInProgress.value == null ||
                    (progress?.value as? TransferProgress.DestinationWaitingForConfirmation) == null
                ) {
                    finish()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                DialogSecure(
                    onDismissRequest = {},
                    properties = DialogProperties(dismissOnClickOutside = false)
                ) {
                    IncomingTransferConfirmationDialog(
                        onDismiss = {
                            finish()
                        },
                        onConfirm = {
                            abortOnDismiss = false
                            TransferService.acceptTransferRequest()
                            startActivity(
                                Intent(
                                    this@WebrtcIncomingTransferActivity,
                                    HistoryTransferActivity::class.java
                                ).apply {
                                    putExtra(TRANSFER_ID_INTENT_EXTRA, transferId)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            finish()
                        },
                        deviceName = (progress?.value as? TransferProgress.DestinationWaitingForConfirmation)?.sourceDeviceName,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && abortOnDismiss) {
            startService(Intent(this, TransferNotificationService::class.java).apply {
                putExtra(TransferNotificationService.EXTRA_TRANSFER_ID, transferId)
                action = TransferNotificationService.ACTION_ABORT
            })
        }
    }
}

@Composable
fun IncomingTransferConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    showCloseButton: Boolean = true,
    deviceName: String?,
) {
    CustomDialogContent {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(min = 200.dp, max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showCloseButton) {
                IconButton(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.End),
                    onClick = onDismiss,
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.content_description_close_button)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = deviceName ?: stringResource(R.string.label_your_other_device),
                style = OlvidTypography.h2,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.dialog_message_wants_to_transfer_history),
                style = OlvidTypography.body1.copy(
                    color = colorResource(R.color.greyTint)
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            OlvidActionButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                text = stringResource(R.string.button_label_start_transfer),
                large = true,
                onClick = onConfirm,
            )

            Spacer(Modifier.height(12.dp))

            OlvidOutlinedActionButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                text = stringResource(R.string.button_label_cancel),
                large = true,
                onClick = onDismiss,
            )
        }
    }
}

@Preview(device = "spec:width=1080px,height=1200px,dpi=440")
@Preview(device = "spec:width=1080px,height=1200px,dpi=440",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL, locale = "fr"
)
@Composable
private fun PreviewIncomingTransferConfirmationDialog() {
    IncomingTransferConfirmationDialog(
        onDismiss = { },
        onConfirm = { },
        deviceName = "Old device"
    )
}