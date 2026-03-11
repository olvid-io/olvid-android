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

package io.olvid.messenger.history_transfer

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import io.olvid.engine.datatypes.EtaEstimator
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.formatBytesSpeed
import io.olvid.messenger.customClasses.formatEtaSeconds
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.history_transfer.types.TransferFailReason
import io.olvid.messenger.history_transfer.types.TransferProgress
import io.olvid.messenger.history_transfer.types.TransferProgress.Connecting.getStepName


class HistoryTransferActivity: LockableActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)

        if (TransferService.transferInProgress.value) {
            runCatching {
                startService(Intent(this, TransferNotificationService::class.java).apply {
                    action = TransferNotificationService.ACTION_START
                })
            }
        }

        setContent {
            val transferProgress = remember { TransferService.getTransferProgress() }
            val messagesEta = remember(transferProgress) { TransferService.getTransferMessagesEta() }
            val filesEta = remember(transferProgress) { TransferService.getTransferFilesEta() }


            TransferProgressDialog(
                transferProgress = transferProgress?.value,
                messagesEta = messagesEta?.value,
                filesEta = filesEta?.value,
                onDismiss = { finish() },
                onAbort = { TransferService.abortOngoingTransfer() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferProgressDialog(
    transferProgress: TransferProgress?,
    messagesEta: EtaEstimator.SpeedAndEta?,
    filesEta: EtaEstimator.SpeedAndEta?,
    onDismiss: () -> Unit,
    onAbort: () -> Unit,
) {
    BasicAlertDialog(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onDismiss,
        properties= DialogProperties(
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .wrapContentHeight()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = colorResource(R.color.dialogBackground),
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            val context = LocalContext.current

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.history_transfer_title),
                    style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium),
                    color = colorResource(R.color.olvid_gradient_light),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = transferProgress.getStepName(false),
                    textAlign = TextAlign.Center,
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.almostBlack),
                )
                Spacer(Modifier.height(24.dp))
                // TODO: use string resources

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (transferProgress) {
                        TransferProgress.ContactingOtherDevice,
                        TransferProgress.Connecting,
                        TransferProgress.Negotiating -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(96.dp)
                                    .padding(8.dp),
                                color = colorResource(id = R.color.olvid_gradient_light),
                                strokeWidth = 8.dp,
                                strokeCap = StrokeCap.Round,
                            )
                        }
                        is TransferProgress.TransferringMessages -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.transfer_label_messages),
                                    style = OlvidTypography.body1,
                                    maxLines = 1,
                                    color = colorResource(R.color.almostBlack),
                                )
                                Text(
                                    text = "${transferProgress.progress}/${transferProgress.total}",
                                    style = OlvidTypography.body1,
                                    maxLines = 1,
                                    color = colorResource(R.color.almostBlack)
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                progress = {
                                    transferProgress.progress.toFloat() / transferProgress.total.toFloat()
                                },
                                trackColor = colorResource(R.color.mediumGrey),
                                color = colorResource(R.color.green),
                                strokeCap = StrokeCap.Round,
                                gapSize = (-6).dp,
                                drawStopIndicator = {}
                            )


                            messagesEta?.let { speedAndEta ->
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.transfer_label_throughput),
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint),
                                    )
                                    Text(
                                        text = "%.1f".format(speedAndEta.speedBps) + "/s",
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.transfer_label_remaining),
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint),
                                    )
                                    Text(
                                        text = speedAndEta.etaSeconds.formatEtaSeconds(context) ?: "-",
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint)
                                    )
                                }
                            }
                        }

                        is TransferProgress.TransferringFiles -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.transfer_label_attachments),
                                    style = OlvidTypography.body1,
                                    maxLines = 1,
                                    color = colorResource(R.color.almostBlack),
                                )
                                Text(
                                    text = Formatter.formatShortFileSize(context, transferProgress.progress) +
                                            "/" +
                                            Formatter.formatShortFileSize(context, transferProgress.total),
                                    style = OlvidTypography.body1,
                                    maxLines = 1,
                                    color = colorResource(R.color.almostBlack)
                                )
                            }
                            Spacer(Modifier.height(8.dp))


                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                progress = {
                                    transferProgress.progress.toFloat() / transferProgress.total.toFloat()
                                },
                                trackColor = colorResource(R.color.mediumGrey),
                                color = colorResource(R.color.olvid_gradient_light),
                                strokeCap = StrokeCap.Round,
                                gapSize = (-6).dp,
                                drawStopIndicator = {}
                            )

                            filesEta?.let { speedAndEta ->
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.transfer_label_throughput),
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint),
                                    )
                                    Text(
                                        text = speedAndEta.speedBps.formatBytesSpeed(context) ?: "-",
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.transfer_label_remaining),
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint),
                                    )
                                    Text(
                                        text = speedAndEta.etaSeconds.formatEtaSeconds(context) ?: "-",
                                        style = OlvidTypography.subtitle1,
                                        maxLines = 1,
                                        color = colorResource(R.color.greyTint)
                                    )
                                }
                            }
                        }

                        TransferProgress.Finished -> {
                            val successComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.checkmark_success))
                            val successProgress by animateLottieCompositionAsState(successComposition)
                            LottieAnimation(
                                modifier = Modifier.size(96.dp),
                                composition = successComposition,
                                progress = { successProgress }
                            )
                        }

                        is TransferProgress.Failed -> {
                            val successComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.error))
                            val successProgress by animateLottieCompositionAsState(successComposition)
                            LottieAnimation(
                                modifier = Modifier.size(96.dp),
                                composition = successComposition,
                                progress = { successProgress }
                            )
                        }

                        else -> {
                            Icon(
                                modifier = Modifier.size(96.dp),
                                painter = painterResource(R.drawable.ic_question_mark),
                                contentDescription = null,
                                tint = colorResource(R.color.mediumGrey),
                            )
                        }
                    }
                }

                when (transferProgress) {
                    TransferProgress.ContactingOtherDevice,
                    TransferProgress.Connecting,
                    TransferProgress.Negotiating,
                    is TransferProgress.TransferringFiles,
                    is TransferProgress.TransferringMessages -> {
                        OlvidTextButton(
                            modifier = Modifier.align(Alignment.End),
                            text = stringResource(R.string.button_label_abort),
                            onClick = onAbort
                        )
                    }
                    TransferProgress.Finished,
                    is TransferProgress.Failed,
                    null -> {
                        OlvidTextButton(
                            modifier = Modifier.align(Alignment.End),
                            text = stringResource(R.string.button_label_ok),
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}


@Preview
@Composable
private fun TransferProgressDialogPreviewNegotiating() {
    TransferProgressDialog(
        transferProgress = TransferProgress.Negotiating,
        messagesEta = null,
        filesEta = null,
        onDismiss = {},
        onAbort = {},
    )
}

@Preview
@Composable
private fun TransferProgressDialogPreviewMessages() {
    TransferProgressDialog(
        transferProgress = TransferProgress.TransferringMessages(50, 100),
        messagesEta = EtaEstimator.SpeedAndEta(37.75f, 15),
        filesEta = null,
        onDismiss = {},
        onAbort = {},
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun TransferProgressDialogPreviewFiles() {
    TransferProgressDialog(
        transferProgress = TransferProgress.TransferringFiles(567568153, 4856352659),
        messagesEta = null,
        filesEta = EtaEstimator.SpeedAndEta(37512364f, 32),
        onDismiss = {},
        onAbort = {},
    )
}


@Preview
@Composable
private fun TransferProgressDialogPreviewFinished() {
    TransferProgressDialog(
        transferProgress = TransferProgress.Finished,
        messagesEta = null,
        filesEta = null,
        onDismiss = {},
        onAbort = {},
    )
}

@Preview
@Composable
private fun TransferProgressDialogPreviewFailed() {
    TransferProgressDialog(
        transferProgress = TransferProgress.Failed(TransferFailReason.UNKNOWN_REASON),
        messagesEta = null,
        filesEta = null,
        onDismiss = {},
        onAbort = {},
    )
}

@Preview
@Composable
private fun TransferProgressDialogPreviewNull() {
    TransferProgressDialog(
        transferProgress = null,
        messagesEta = null,
        filesEta = null,
        onDismiss = {},
        onAbort = {},
    )
}