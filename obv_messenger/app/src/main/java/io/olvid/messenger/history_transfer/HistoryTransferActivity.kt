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

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import io.olvid.engine.datatypes.EtaEstimator
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatBytesSpeed
import io.olvid.messenger.customClasses.formatEtaSeconds
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidOutlinedSecondaryButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.history_transfer.types.TransferFailReason
import io.olvid.messenger.history_transfer.types.TransferProgress
import io.olvid.messenger.history_transfer.types.TransferRole
import io.olvid.messenger.history_transfer.types.TransferTransportType
import io.olvid.messenger.history_transfer.types.getDescription
import io.olvid.messenger.lock_screen.LockableActivity


class HistoryTransferActivity: LockableActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)

        val transferId : String = intent?.getStringExtra(TRANSFER_ID_INTENT_EXTRA) ?: run {
            finish()
            return
        }

        val role: TransferRole
        val transportType: TransferTransportType
        val transferProgress: State<TransferProgress>

        TransferService.getTransferProgress(transferId)?.let {
            role = it.first
            transportType = it.second
            transferProgress = it.third
        } ?: run {
            finish()
            return
        }

        if (TransferService.transferInProgress.value != null) {
            runCatching {
                startService(Intent(this, TransferNotificationService::class.java).apply {
                    action = TransferNotificationService.ACTION_START
                })
            }
        }

        setContent {
            var showInterruptDialog by remember { mutableStateOf(false) }

            val messagesEta = remember { TransferService.getTransferMessagesEta(transferId) }
            val filesEta = remember { TransferService.getTransferFilesEta(transferId) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorResource(R.color.almostWhite))
                    .safeDrawingPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 96.dp, start = 16.dp, end = 16.dp, bottom = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Carrousel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        background = colorResource(R.color.almostWhite),
                        role = role,
                        transportType = transportType,
                        progress = transferProgress.value
                    )

                    Spacer(Modifier.height(32.dp))

                    DetailedProgress(
                        role = role,
                        transportType = transportType,
                        progress = transferProgress.value,
                        messagesEta = messagesEta,
                        filesEta = filesEta,
                    )
                }

                // Top right close button
                IconButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp),
                    onClick = {
                        finish()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = colorResource(R.color.almostBlack)
                    ),
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(id = R.string.button_label_cancel),
                    )
                }

                // bottom button to interrupt or close depending on state
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(colorResource(R.color.whiteOverlay))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    when (transferProgress.value) {
                        is TransferProgress.Failed, is TransferProgress.Finished -> {
                            OlvidActionButton(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                text = stringResource(R.string.button_label_ok),
                                large = true,
                            ) {
                                finish()
                            }
                        }
                        else -> {
                            OlvidOutlinedSecondaryButton(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                text = stringResource(
                                    if (transportType is TransferTransportType.WebRtcWithOwnedDevice)
                                        R.string.button_label_interrupt_and_continue_later
                                    else if (role == TransferRole.SOURCE)
                                        R.string.button_label_abort_export
                                    else
                                        R.string.button_label_abort_import

                                ),
                                large = true,
                                allowTwoLines = true,
                            ) {
                                showInterruptDialog = true
                            }
                        }
                    }
                }


                // confirmation dialog when interrupting a transfer
                if (showInterruptDialog) {
                    AbortTransferConfirmationDialog(
                        role = role,
                        transportType = transportType,
                        onDismiss = {
                            showInterruptDialog = false
                        },
                        onAbort = {
                            showInterruptDialog = false
                            TransferService.abortOngoingTransfer(transferId)
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val TRANSFER_ID_INTENT_EXTRA = "transfer_id"
    }
}

@Composable
private fun AbortTransferConfirmationDialog(
    role: TransferRole,
    transportType: TransferTransportType,
    onDismiss: () -> Unit,
    onAbort: () -> Unit,
) {
    DialogSecure(
        onDismissRequest = onDismiss,
    ) {
        BaseDialogContent(
            title = stringResource(
                if (transportType is TransferTransportType.WebRtcWithOwnedDevice)
                    R.string.dialog_title_abort_history_transfer
                else if (role == TransferRole.SOURCE)
                    R.string.dialog_title_abort_history_transfer_export
                else
                    R.string.dialog_title_abort_history_transfer_import
            ),
            content = {
                Text(
                    text = stringResource(
                        if (transportType is TransferTransportType.WebRtcWithOwnedDevice)
                            R.string.dialog_message_abort_history_transfer_webrtc
                        else if (role == TransferRole.SOURCE)
                            R.string.dialog_message_abort_history_transfer_zip_export
                        else
                            R.string.dialog_message_abort_history_transfer_zip_import
                    ).formatMarkdownToAnnotatedString(),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint),
                )
            },
            actions = {
                OlvidOutlinedSecondaryButton(
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(8.dp),
                    text = stringResource(R.string.button_label_cancel),
                    onClick = onDismiss
                )
                OlvidActionButton(
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(8.dp),
                    text = stringResource(R.string.button_label_abort),
                    onClick = onAbort
                )
            }
        )
    }
}


enum class CarrouselItemType {
    WAITING_FOR_OTHER_DEVICE,
    CONNECTING,
    NEGOTIATING,
    TRANSFERRING,
    FINISHED,
}

@Composable
private fun Carrousel(
    modifier: Modifier = Modifier,
    background: Color,
    role: TransferRole,
    transportType: TransferTransportType,
    progress: TransferProgress,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val items = remember(transportType) {
        when (transportType) {
            is TransferTransportType.WebRtcWithOwnedDevice -> {
                listOf(
                    CarrouselItemType.WAITING_FOR_OTHER_DEVICE,
                    CarrouselItemType.CONNECTING,
                    CarrouselItemType.NEGOTIATING,
                    CarrouselItemType.TRANSFERRING,
                    CarrouselItemType.FINISHED,
                )
            }

            else -> {
                listOf(
                    CarrouselItemType.CONNECTING,
                    CarrouselItemType.NEGOTIATING,
                    CarrouselItemType.TRANSFERRING,
                    CarrouselItemType.FINISHED,
                )
            }
        }
    }

    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(items, progress) {
        index = when (progress) {
            is TransferProgress.DestinationWaitingForConfirmation,
            TransferProgress.ContactingOtherDevice -> items.indexOf(CarrouselItemType.WAITING_FOR_OTHER_DEVICE)
            TransferProgress.Connecting -> items.indexOf(CarrouselItemType.CONNECTING)
            TransferProgress.Negotiating -> items.indexOf(CarrouselItemType.NEGOTIATING)
            is TransferProgress.Transferring,
            is TransferProgress.ProcessingReceivedData -> items.indexOf(CarrouselItemType.TRANSFERRING)
            is TransferProgress.Failed,
            TransferProgress.Finished -> items.indexOf(CarrouselItemType.FINISHED)
        }.coerceAtLeast(0)
    }

    LaunchedEffect(items, index) {
        with(density) {
            scrollState.animateScrollTo(((index.coerceAtLeast(0) * 48).dp).roundToPx())
        }
    }

    Box(
        modifier = modifier
            .height(192.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = scrollState, enabled = false)
        ) {
            Spacer(Modifier.height(96.dp))

            items.forEach {
                CarrouselItem(
                    carrouselItem = it,
                    role = role,
                    transportType = transportType,
                    failed = progress is TransferProgress.Failed
                )
            }

            Spacer(Modifier.height(48.dp))
        }

        // add a fading overlay on top

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        0f to background,
                        0.25f to background.copy(alpha = background.alpha * .7f),
                        0.5f to background.copy(alpha = background.alpha * .3f),
                        0.50001f to Color.Transparent,
                        0.75f to Color.Transparent,
                        0.750001f to background.copy(alpha = background.alpha * .3f),
                        1f to background
                    )
                )
        )
    }
}


@Composable
private fun CarrouselItem(
    carrouselItem: CarrouselItemType,
    role: TransferRole,
    transportType: TransferTransportType,
    failed: Boolean,
) {
    Crossfade(targetState = carrouselItem == CarrouselItemType.FINISHED && failed) { failed ->
        Row(
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = colorResource(
                            if (failed)
                                R.color.red
                            else
                                R.color.olvid_gradient_light
                        ),
                        shape = CircleShape
                    )
                    .padding(4.dp),
                painter = painterResource(
                    if (failed)
                        R.drawable.ic_close
                    else
                        R.drawable.ic_check
                ),
                tint = colorResource(R.color.alwaysWhite),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    when (carrouselItem) {
                        CarrouselItemType.WAITING_FOR_OTHER_DEVICE -> R.string.history_transfer_step_contacting_other_device
                        CarrouselItemType.CONNECTING -> {
                            if (transportType is TransferTransportType.WebRtcWithOwnedDevice) {
                                R.string.history_transfer_step_connecting
                            } else if (role == TransferRole.SOURCE) {
                                R.string.history_transfer_step_connecting_export
                            } else {
                                R.string.history_transfer_step_connecting_import
                            }
                        }
                        CarrouselItemType.NEGOTIATING -> {
                            if (transportType is TransferTransportType.WebRtcWithOwnedDevice) {
                                R.string.history_transfer_step_negotiating
                            } else if (role == TransferRole.SOURCE) {
                                R.string.history_transfer_step_negotiating_export
                            } else {
                                R.string.history_transfer_step_negotiating_import
                            }
                        }
                        CarrouselItemType.TRANSFERRING -> {
                            if (transportType is TransferTransportType.WebRtcWithOwnedDevice) {
                                R.string.history_transfer_step_transferring
                            } else if (role == TransferRole.SOURCE) {
                                R.string.history_transfer_step_transferring_export
                            } else {
                                R.string.history_transfer_step_transferring_import
                            }
                        }
                        CarrouselItemType.FINISHED -> {
                            if (transportType is TransferTransportType.WebRtcWithOwnedDevice) {
                                if (failed)
                                    R.string.history_transfer_step_failed
                                else
                                    R.string.history_transfer_step_finished
                            } else if (role == TransferRole.SOURCE) {
                                if (failed)
                                    R.string.history_transfer_step_failed_export
                                else
                                    R.string.history_transfer_step_finished_export
                            } else {
                                if (failed)
                                    R.string.history_transfer_step_failed_import
                                else
                                    R.string.history_transfer_step_finished_import
                            }
                        }
                    }
                ),
                style = OlvidTypography.h2,
                color = colorResource(R.color.almostBlack),
                maxLines = 2,
            )
        }
    }
}


@Composable
fun ColumnScope.DetailedProgress(
    role: TransferRole,
    transportType: TransferTransportType,
    progress: TransferProgress,
    messagesEta: State<EtaEstimator.SpeedAndEta?>?,
    filesEta: State<EtaEstimator.SpeedAndEta?>?,
) {
    when(progress) {
        is TransferProgress.DestinationWaitingForConfirmation,
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

        is TransferProgress.Transferring -> {
            ProgressBars(
                messagesProgress = progress.messagesProgress,
                messagesTotal = progress.messagesTotal,
                filesProgress = progress.filesProgress,
                filesTotal = progress.filesTotal,
                role = role,
                transportType = transportType,
                messagesEta = messagesEta,
                filesEta = filesEta
            )
        }

        is TransferProgress.ProcessingReceivedData -> {
            ProgressBars(
                messagesProgress = progress.messagesProgress,
                messagesTotal = progress.messagesTotal,
                filesProgress = progress.filesProgress,
                filesTotal = progress.filesTotal,
                role = role,
                transportType = transportType,
                messagesEta = messagesEta,
                filesEta = filesEta
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

            Spacer(Modifier.height(16.dp))

            val alpha = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                alpha.animateTo(1f)
            }

            Text(
                modifier = Modifier.alpha(alpha = alpha.value),
                text = progress.reason.getDescription(),
                style = OlvidTypography.body1.copy(
                    color = colorResource(R.color.almostBlack),
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
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
    }
}


@Composable
private fun ColumnScope.ProgressBars(
    messagesProgress: Int,
    messagesTotal: Int,
    filesProgress: Long,
    filesTotal: Long,
    role: TransferRole,
    transportType: TransferTransportType,
    messagesEta: State<EtaEstimator.SpeedAndEta?>?,
    filesEta: State<EtaEstimator.SpeedAndEta?>?,
) {
    val context = LocalContext.current

    if (messagesTotal > 0) {
        Text(
            modifier = Modifier.align(Alignment.Start),
            text = stringResource(
                if (role == TransferRole.SOURCE)
                    if (transportType is TransferTransportType.ZipFileExport)
                        R.string.transfer_label_exporting_messages
                    else
                        R.string.transfer_label_sending_messages
                else
                    if (transportType is TransferTransportType.ZipFileImport)
                        R.string.transfer_label_importing_messages
                    else
                        R.string.transfer_label_receiving_messages
            ),
            style = OlvidTypography.body1,
            maxLines = 1,
            color = colorResource(R.color.almostBlack),
        )


        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            progress = {
                messagesProgress.toFloat() / messagesTotal.toFloat()
            },
            trackColor = colorResource(R.color.mediumGrey),
            color = colorResource(R.color.green),
            strokeCap = StrokeCap.Round,
            gapSize = (-6).dp,
            drawStopIndicator = {}
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            messagesEta?.value?.takeIf {
                messagesProgress < messagesTotal
            }?.let { speedAndEta ->
                val remaining = speedAndEta.etaSeconds.formatEtaSeconds(context) ?: "-"
                val throughput = "~%.0f".format(speedAndEta.speedBps) + "/s"

                Text(
                    text = stringResource(R.string.label_remaining_and_throughput, remaining, throughput),
                    style = OlvidTypography.subtitle1,
                    maxLines = 1,
                    color = colorResource(R.color.greyTint)
                )
            }

            Spacer(Modifier.weight(1f, true))

            Text(
                text = "${messagesProgress}/${messagesTotal}",
                style = OlvidTypography.subtitle1,
                maxLines = 1,
                color = colorResource(R.color.greyTint)
            )
        }

    }


    if (messagesTotal > 0 && filesTotal > 0) {
        Spacer(Modifier.height(24.dp))
    }


    if (filesTotal > 0) {
        Text(
            modifier = Modifier.align(Alignment.Start),
            text = stringResource(
                if (role == TransferRole.SOURCE)
                    if (transportType is TransferTransportType.ZipFileExport)
                        R.string.transfer_label_exporting_attachments
                    else
                        R.string.transfer_label_sending_attachments
                else
                    if (transportType is TransferTransportType.ZipFileImport)
                        R.string.transfer_label_importing_attachments
                    else
                        R.string.transfer_label_receiving_attachments
            ),
            style = OlvidTypography.body1,
            maxLines = 1,
            color = colorResource(R.color.almostBlack),
        )

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            progress = {
                filesProgress.toFloat() / filesTotal.toFloat()
            },
            trackColor = colorResource(R.color.mediumGrey),
            color = colorResource(R.color.olvid_gradient_light),
            strokeCap = StrokeCap.Round,
            gapSize = (-6).dp,
            drawStopIndicator = {}
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            filesEta?.value?.takeIf {
                filesProgress < filesTotal
            }?.let { speedAndEta ->
                val remaining = speedAndEta.etaSeconds.formatEtaSeconds(context) ?: "-"
                val throughput = speedAndEta.speedBps.formatBytesSpeed(context) ?: "-"

                Text(
                    text = stringResource(R.string.label_remaining_and_throughput, remaining, throughput),
                    style = OlvidTypography.subtitle1,
                    maxLines = 1,
                    color = colorResource(R.color.greyTint)
                )
            }

            Spacer(Modifier.weight(1f, true))

            Text(
                text = Formatter.formatShortFileSize(context, filesProgress) +
                        "/" +
                        Formatter.formatShortFileSize(context, filesTotal),
                style = OlvidTypography.subtitle1,
                maxLines = 1,
                color = colorResource(R.color.greyTint)
            )
        }
    }
}




@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun AbortTransferConfirmationDialogPreview() {
    AbortTransferConfirmationDialog(
        role = TransferRole.SOURCE,
        transportType = TransferTransportType.ZipFileExport(
            bytesOwnedIdentity = ByteArray(0),
            zipWritableFileUri = "".toUri(),
            password = null,
        ),
        onDismiss = {},
        onAbort = {}
    )
}


@Preview
@Composable
private fun DetailedProgressPreview() {
    Column(
        modifier = Modifier
            .background(colorResource(R.color.almostWhite))
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailedProgress(
            role = TransferRole.SOURCE,
            transportType = TransferTransportType.ZipFileExport(
                bytesOwnedIdentity = ByteArray(0),
                zipWritableFileUri = "".toUri(),
                password = null,
            ),
            progress = TransferProgress.Negotiating,
            messagesEta = null,
            filesEta = null,
        )
    }
}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun DetailedProgressPreviewFailed() {
    Column(
        modifier = Modifier
            .background(colorResource(R.color.almostWhite))
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailedProgress(
            role = TransferRole.SOURCE,
            transportType = TransferTransportType.ZipFileExport(
                bytesOwnedIdentity = ByteArray(0),
                zipWritableFileUri = "".toUri(),
                password = null,
            ),
            progress = TransferProgress.Failed(TransferFailReason.OWNED_IDENTITY_MISMATCH),
            messagesEta = null,
            filesEta = null,
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun DetailedProgressPreviewTransferring() {
    Column(
        modifier = Modifier
            .background(colorResource(R.color.almostWhite))
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailedProgress(
            role = TransferRole.SOURCE,
            transportType = TransferTransportType.ZipFileExport(
                bytesOwnedIdentity = ByteArray(0),
                zipWritableFileUri = "".toUri(),
                password = null,
            ),
            progress = TransferProgress.Transferring(
                messagesProgress = 127,
                messagesTotal = 478,
                filesProgress = 7500000L,
                filesTotal = 276000000L,
            ),
            messagesEta = remember { mutableStateOf(EtaEstimator.SpeedAndEta(
                57f,
                3
            ))},
            filesEta =  remember { mutableStateOf(EtaEstimator.SpeedAndEta(
                8300000f,
                67
            ))},
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun CarrouselPreview() {
    Column(
        modifier = Modifier
            .background(colorResource(R.color.almostWhite))
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Carrousel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            background = colorResource(R.color.almostWhite),
            role = TransferRole.SOURCE,
            transportType = TransferTransportType.ZipFileExport(
                bytesOwnedIdentity = ByteArray(0),
                zipWritableFileUri = "".toUri(),
                password = null,
            ),
            progress = TransferProgress.Negotiating,
        )


        Carrousel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            background = colorResource(R.color.almostWhite),
            role = TransferRole.DESTINATION,
            transportType = TransferTransportType.WebRtcWithOwnedDevice(
                bytesOwnedIdentity = ByteArray(0),
                bytesOtherDeviceUid = ByteArray(0),
            ),
            progress = TransferProgress.Transferring(12, 24, 0, 4000),
        )
    }
}