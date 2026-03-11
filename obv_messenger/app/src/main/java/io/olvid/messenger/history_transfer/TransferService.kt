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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.core.JsonProcessingException
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.EtaEstimator
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.FyleProgressSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcHistoryTransferMessage
import io.olvid.messenger.history_transfer.json.DstDiscussionExpectedRanges
import io.olvid.messenger.history_transfer.json.DstDoNotRequestSha256
import io.olvid.messenger.history_transfer.json.DstExpectedSha256
import io.olvid.messenger.history_transfer.json.DstRequestSha256
import io.olvid.messenger.history_transfer.json.SrcDiscussionDone
import io.olvid.messenger.history_transfer.json.SrcDiscussionList
import io.olvid.messenger.history_transfer.json.SrcDiscussionRanges
import io.olvid.messenger.history_transfer.json.SrcMessages
import io.olvid.messenger.history_transfer.steps.DstProcessSrcDiscussionDoneStep
import io.olvid.messenger.history_transfer.steps.DstProcessSrcMessagesStep
import io.olvid.messenger.history_transfer.steps.DstSendDiscussionExpectedRangesStep
import io.olvid.messenger.history_transfer.steps.DstSendExpectedSha256Step
import io.olvid.messenger.history_transfer.steps.SrcDoNotSendFileStep
import io.olvid.messenger.history_transfer.steps.SrcProcessDiscussionKnownRangesStep
import io.olvid.messenger.history_transfer.steps.SrcProcessKnownSha256Step
import io.olvid.messenger.history_transfer.steps.SrcSendDiscussionsStep
import io.olvid.messenger.history_transfer.steps.SrcSendFileStep
import io.olvid.messenger.history_transfer.steps.SrcSendMessagesStep
import io.olvid.messenger.history_transfer.types.AttachmentProgressListener
import io.olvid.messenger.history_transfer.types.DstTransferProtocolState
import io.olvid.messenger.history_transfer.types.SrcTransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferFailReason
import io.olvid.messenger.history_transfer.types.TransferListener
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferProgress
import io.olvid.messenger.history_transfer.types.TransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferRole
import io.olvid.messenger.history_transfer.types.TransferScope
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import io.olvid.messenger.history_transfer.types.TransferTransportLayerState
import io.olvid.messenger.history_transfer.types.TransferTransportType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


class TransferService(
    val role: TransferRole,
    val transferTransportType: TransferTransportType,
    val transferProtocolState: TransferProtocolState
): TransferListener {
    val transferTransportDelegate: TransferTransportDelegate
    var aborted = false
    val executor = NoExceptionSingleThreadExecutor("History transfer executor")
    val fileOutputStreams: MutableMap<BytesKey, FileOutputStream> = mutableMapOf()

    var transferTransportLayerState = TransferTransportLayerState.INITIALIZING

    init {
        Handler(Looper.getMainLooper()).post {
            try {
                App.getContext().startService(Intent(App.getContext(), TransferNotificationService::class.java).apply {
                    action = TransferNotificationService.ACTION_START
                })
            } catch (e: Exception) {
                Logger.x(e)
            }
        }

        transferTransportDelegate = when(transferTransportType)  {
            is TransferTransportType.WebRtcWithOwnedDevice -> WebRTCTransferTransportDelegate(
                role = role,
                transferListener = this,
                objectMapper = AppSingleton.getJsonObjectMapper(),
                bytesOwnedIdentity = transferTransportType.bytesOwnedIdentity,
                bytesOtherDeviceUid = transferTransportType.bytesOtherDeviceUid,
                context = App.getContext(),
            )
            is TransferTransportType.ZipFileExport -> ZipExportTransferTransportDelegate(
                role = role,
                transferListener = this,
                bytesOwnedIdentity = transferTransportType.bytesOwnedIdentity,
                zipWritableFileUri = transferTransportType.zipWritableFileUri,
                context = App.getContext(),
            )
            is TransferTransportType.ZipFileImport -> ZipImportTransferTransportDelegate(
                role = role,
                transferListener = this,
                objectMapper = AppSingleton.getJsonObjectMapper(),
                zipReadableFileUri = transferTransportType.zipReadableFileUri,
                context = App.getContext(),
            )
        }

    }

    override fun onTransportLayerStateChange(state: TransferTransportLayerState) {
        if (transferTransportLayerState == state) {
            return
        }
        Logger.i("\uD83E\uDDF6 onTransportLayerStateChange $state")
        transferTransportLayerState = state

        when(state) {
            TransferTransportLayerState.INITIALIZING -> {
                // nothing to do here
            }

            TransferTransportLayerState.CONNECTING -> {
                executor.execute {
                    transferProtocolState.transferProgress.value = TransferProgress.Connecting
                }
            }

            TransferTransportLayerState.READY -> {
                Logger.i("\uD83E\uDDF6 reached the READY state, initiating transfer protocol")
                executor.execute {
                    transferProtocolState.updateProgress()
                }
                if (role == TransferRole.SOURCE) {
                    (transferProtocolState as? SrcTransferProtocolState)?.also {
                        executor.execute(SrcSendDiscussionsStep(srcTransferProtocolState = transferProtocolState, transferTransportDelegate = transferTransportDelegate, executor = executor))
                    } ?: run {
                        cleanup()
                    }
                }
            }

            TransferTransportLayerState.CLOSED -> {
                // if the connection is closed before it is finished --> set to failed state
                if (transferProtocolState.transferProgress.value != TransferProgress.Finished) {
                    transferProtocolState.transferProgress.value = if (aborted)
                        TransferProgress.Failed(TransferFailReason.ABORTED)
                    else if (transferProtocolState is DstTransferProtocolState && transferProtocolState.bytesOwnedIdentity == null)
                        TransferProgress.Failed(TransferFailReason.UNKNOWN_OWNED_IDENTITY)
                    else
                        TransferProgress.Failed(TransferFailReason.UNKNOWN_REASON)
                }
                // connection was closed, clean up everything
                cleanup()
            }
        }
    }

    private fun abort() {
        instance?.let {
            if (transferTransportType == it.transferTransportType) {
                aborted = true
                transferTransportDelegate.abort()
            }
        }
    }

    private fun cleanup() {
        instance?.let {
            if (transferTransportType == it.transferTransportType) {
                transferTransportDelegate.cleanup()
                instance = null
            }
            // also cleanup any open fos
            fileOutputStreams.entries.forEach { stream ->
                try {
                    stream.value.close()
                    val tmpDir = File(App.getContext().cacheDir, App.WEBCLIENT_ATTACHMENT_FOLDER)
                    val tmpFile = File(tmpDir, getHistTransFilename(stream.key.bytes))
                    tmpFile.delete()
                } catch (_: Exception) { }
            }
        }
    }

    override fun onJsonMessage(
        messageType: TransferMessageType,
        serializedMessage: ByteArray
    ) {
        if (aborted) {
            return
        }
        executor.execute {
            try {
                when (messageType) {
                    TransferMessageType.ACK -> Unit // this is handled at the transport layer

                    TransferMessageType.SRC_DISCUSSION_LIST -> {
                        val srcDiscussionList = AppSingleton.getJsonObjectMapper().readValue(serializedMessage, SrcDiscussionList::class.java)
                        (transferProtocolState as? DstTransferProtocolState)?.let { state ->
                            DstSendExpectedSha256Step(state, srcDiscussionList, transferTransportDelegate).run()
                        }
                    }

                    TransferMessageType.SRC_DISCUSSION_RANGES -> {
                        val srcDiscussionRanges = AppSingleton.getJsonObjectMapper().readValue(serializedMessage, SrcDiscussionRanges::class.java)
                        (transferProtocolState as? DstTransferProtocolState)?.let { state ->
                            DstSendDiscussionExpectedRangesStep(state, srcDiscussionRanges, transferTransportDelegate).run()
                        }
                    }

                    TransferMessageType.DST_EXPECTED_SHA256 -> {
                        val dstExpectedSha256 = AppSingleton.getJsonObjectMapper().readValue(serializedMessage, DstExpectedSha256::class.java)
                        (transferProtocolState as? SrcTransferProtocolState)?.let { state ->
                            SrcProcessKnownSha256Step(state, dstExpectedSha256).run()
                            if (state.readyToSendMessages()) {
                                // run this step in the background to allow receiving sha256 request at the same time
                                App.runThread(SrcSendMessagesStep(state, transferTransportDelegate, executor))
                            }
                        }
                    }

                    TransferMessageType.DST_DISCUSSION_EXPECTED_RANGES -> {
                        val dstDiscussionExpectedRanges = AppSingleton.getJsonObjectMapper().readValue(serializedMessage, DstDiscussionExpectedRanges::class.java)
                        (transferProtocolState as? SrcTransferProtocolState)?.let { state ->
                            SrcProcessDiscussionKnownRangesStep(state, dstDiscussionExpectedRanges).run()
                            if (state.readyToSendMessages()) {
                                // run this step in the background to allow receiving sha256 request at the same time
                                App.runThread(SrcSendMessagesStep(state, transferTransportDelegate, executor))
                            }
                        }
                    }

                    TransferMessageType.SRC_MESSAGES -> {
                        val srcMessages = AppSingleton.getJsonObjectMapper().readValue(serializedMessage, SrcMessages::class.java)
                        (transferProtocolState as? DstTransferProtocolState)?.let { state ->
                            // TODO run on executor directly
                            DstProcessSrcMessagesStep(state, srcMessages, transferTransportDelegate).run()
                        }
                    }

                    TransferMessageType.DST_REQUEST_SHA256 -> {
                        val dstRequestSha256 = AppSingleton.getJsonObjectMapper().readValue(serializedMessage, DstRequestSha256::class.java)
                        (transferProtocolState as? SrcTransferProtocolState)?.let { state ->
                            SrcSendFileStep(state, dstRequestSha256, transferTransportDelegate, executor).run()
                        }
                    }

                    TransferMessageType.DST_DO_NOT_REQUEST_SHA256 -> {
                        val dstDoNotRequestSha256 = AppSingleton.getJsonObjectMapper().readValue(serializedMessage, DstDoNotRequestSha256::class.java)
                        (transferProtocolState as? SrcTransferProtocolState)?.let { state ->
                            SrcDoNotSendFileStep(
                                state,
                                dstDoNotRequestSha256,
                                transferTransportDelegate,
                            ).run()
                        }
                    }

                    TransferMessageType.SRC_SHA256 -> Unit // this is handled at the transport layer

                    TransferMessageType.SRC_DISCUSSION_DONE -> {
                        val srcDiscussionDone = AppSingleton.getJsonObjectMapper().readValue(serializedMessage,
                            SrcDiscussionDone::class.java)
                        (transferProtocolState as? DstTransferProtocolState)?.let { state ->
                            DstProcessSrcDiscussionDoneStep(
                                state,
                                srcDiscussionDone,
                                transferTransportDelegate
                            ).run()
                        }
                    }

                    TransferMessageType.SRC_TRANSFER_DONE -> {
                        Logger.i("🫠 Received SRC_TRANSFER_DONE --> cleaning up")
                        cleanup()
                    }
                }
            } catch (e: JsonProcessingException) {
                Logger.x(e)
            }
        }
    }

    private fun getHistTransFilename(sha256: ByteArray): String {
        return Logger.toHexString(sha256) + "_hist_trans"
    }

    override fun onNewAttachment(sha256: ByteArray): Pair<OutputStream, AttachmentProgressListener?>? {
        if (aborted) {
            return null
        }
        val fos: OutputStream
        synchronized(fileOutputStreams) {
            val sha256Key = BytesKey(sha256)
            // close any already existing FileOutputStream
            fileOutputStreams[sha256Key]?.close()

            val localAttachmentFileName = getHistTransFilename(sha256)
            val tmpDir = File(App.getContext().cacheDir, App.WEBCLIENT_ATTACHMENT_FOLDER)
            tmpDir.mkdirs()
            val tmpFile = File(tmpDir, localAttachmentFileName)
            fos = FileOutputStream(tmpFile, false) // open in truncate mode in case the file exists
            fileOutputStreams[sha256Key] = fos
        }
        return Pair(fos, object : AttachmentProgressListener {
            val size: Long = (transferProtocolState as? DstTransferProtocolState)?.let { dstTransferProtocolState ->
                dstTransferProtocolState.expectedSha256s?.get(ObvBytesKey(sha256))
            } ?: 0L

            var transferredCount = 0L

            override fun bytesTransferred(count: Long) {
                executor.execute {
                    (transferProtocolState as? DstTransferProtocolState)?.let { dstTransferProtocolState ->
                        val safeCount = count.coerceAtMost(size - transferredCount)
                        dstTransferProtocolState.receivedBytes += safeCount
                        transferredCount += safeCount
                        dstTransferProtocolState.updateProgress()
                    }
                }
            }
        })
    }

    override fun onAttachmentComplete(sha256: ByteArray, fileSize: Long) {
        val sha256Key = BytesKey(sha256)
        synchronized(fileOutputStreams) {
            val fos = fileOutputStreams[sha256Key]
            if (fos != null) {
                fileOutputStreams.remove(sha256Key)
                fos.close()
            } else {
                return
            }
        }
        if (aborted) {
            return
        }
        executor.execute {
            // count missing bytes if fileSize does not match expected size
            (transferProtocolState as? DstTransferProtocolState)?.let { dstTransferProtocolState ->
                val expectedSize = dstTransferProtocolState.expectedSha256s?.get(ObvBytesKey(sha256)) ?: 0L
                dstTransferProtocolState.receivedBytes += (expectedSize - fileSize).coerceAtLeast(0L)
                dstTransferProtocolState.updateProgress()
            }
        }
        App.runThread {
            val tmpDir = File(App.getContext().cacheDir, App.WEBCLIENT_ATTACHMENT_FOLDER)
            val tmpFile = File(tmpDir, getHistTransFilename(sha256))

            val sizeAndSha256 = Fyle.computeSHA256FromFile(tmpFile.absolutePath)
            if (sizeAndSha256?.sha256?.contentEquals(sha256) == true) {
                try {
                    Fyle.acquireLock(sha256)
                    val db = AppDatabase.getInstance()
                    // only do something if the Fyle actually exists
                    db.fyleDao().getBySha256(sha256)?.let { fyle ->
                        // save the fyle
                        fyle.moveToFyleDirectory(tmpFile.absolutePath)
                        db.fyleDao().update(fyle)
                        // update all fyleMessageJoins to STATUS_COMPLETE
                        db.fyleMessageJoinWithStatusDao().getForFyleId(fyle.id).forEach { fyleMessageJoin ->
                            when (fyleMessageJoin.status) {
                                FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE, FyleMessageJoinWithStatus.STATUS_DOWNLOADING, FyleMessageJoinWithStatus.STATUS_FAILED, FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED -> {
                                    fyleMessageJoin.status = FyleMessageJoinWithStatus.STATUS_COMPLETE
                                    FyleProgressSingleton.finishProgress(fyleMessageJoin.fyleId, fyleMessageJoin.messageId)
                                    fyleMessageJoin.filePath = fyle.filePath!!
                                    fyleMessageJoin.size = sizeAndSha256.fileSize
                                    db.fyleMessageJoinWithStatusDao().update(fyleMessageJoin)

                                    // just in case this also completes another fyle, try to send a return receipt
                                    fyleMessageJoin.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null)
                                    fyleMessageJoin.engineMessageIdentifier?.let { identifier ->
                                        fyleMessageJoin.engineNumber?.let { number ->
                                            AppSingleton.getEngine().markAttachmentForDeletion(fyleMessageJoin.bytesOwnedIdentity, identifier, number)
                                        }
                                    }
                                    fyleMessageJoin.computeTextContentForFullTextSearchOnOtherThread(db, fyle)
                                }
                            }
                        }
                    } ?: {
                        // no fyle exists --> discard the file
                        tmpFile.delete()
                    }
                } finally {
                    Fyle.releaseLock(sha256)
                }
            } else {
                // bad sha256 --> discard the file
                tmpFile.delete()
            }
        }
    }

    override fun onOwnedIdentityFound(bytesOwnedIdentity: ByteArray) {
        (transferProtocolState as? DstTransferProtocolState)?.bytesOwnedIdentity = bytesOwnedIdentity
    }

    companion object {
        const val HISTORY_TRANSFER_MESSAGE_MAX_AGE: Long = 30_000
        private var wifiLock: WifiLock? = null
        private var powerLock: PowerManager.WakeLock? = null


        var instance: TransferService? = null
            @SuppressLint("WifiManagerLeak", "WakelockTimeout")
            set(value) {
                field = value
                transferInProgress.value = value != null
                if (value != null) {
                    // for WebRTC transfers, request a wifi wake lock
                    if (value.transferTransportType is TransferTransportType.WebRtcWithOwnedDevice) {
                        runCatching {
                            @Suppress("DEPRECATION")
                            (App.getContext().getSystemService(Context.WIFI_SERVICE) as WifiManager?)?.createWifiLock(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                                else
                                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                                "io.olvid:wifi_lock_for_transfer"
                            )?.apply {
                                wifiLock = this
                                acquire()
                            }
                        }
                    }

                    // in all cases, request a power wake lock
                    runCatching {
                        (App.getContext().getSystemService(Context.POWER_SERVICE) as PowerManager?)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "io.olvid:power_lock_for_transfer")?.apply {
                            powerLock = this
                            acquire()
                        }
                    }
                } else {
                    wifiLock?.release()
                    wifiLock = null
                    powerLock?.release()
                    powerLock = null
                }
            }

        val transferInProgress = mutableStateOf(false)

        fun initiateHistoryTransferToOtherDevice(transferTransportType: TransferTransportType, transferScope: TransferScope) : Boolean {
            instance?.let {
                Logger.w("Unable to run two history transfers in parallel")
                return false
            }
            when(transferTransportType) {
                is TransferTransportType.WebRtcWithOwnedDevice -> {
                    instance = TransferService(TransferRole.SOURCE, transferTransportType, SrcTransferProtocolState(transferScope, transferTransportType.bytesOwnedIdentity))
                    return true
                }

                is TransferTransportType.ZipFileExport -> {
                    if (!StringUtils.validateUri(transferTransportType.zipWritableFileUri)) {
                        return false
                    }

                    instance = TransferService(TransferRole.SOURCE, transferTransportType, SrcTransferProtocolState(transferScope, transferTransportType.bytesOwnedIdentity))
                    return true
                }
                is TransferTransportType.ZipFileImport -> {
                    if (!StringUtils.validateUri(transferTransportType.zipReadableFileUri)) {
                        return false
                    }

                    instance = TransferService(TransferRole.DESTINATION, transferTransportType, DstTransferProtocolState())
                    return true
                }
            }
        }

        fun handleJsonHistoryTransferMessage(jsonWebrtcHistoryTransferMessage: JsonWebrtcHistoryTransferMessage, bytesOwnedIdentity: ByteArray, bytesOtherDeviceUid: ByteArray) {
            val transferTransportType = TransferTransportType.WebRtcWithOwnedDevice(bytesOwnedIdentity = bytesOwnedIdentity, bytesOtherDeviceUid = bytesOtherDeviceUid)
            instance?.also {
                if (it.transferTransportType == transferTransportType) {
                    (it.transferTransportDelegate as? WebRTCTransferTransportDelegate)?.handleJsonHistoryTransferMessage(jsonWebrtcHistoryTransferMessage)
                } else {
                    Logger.w("Unable to run two history transfers in parallel")
                }
            } ?: run {
                // only create an instance when receiving an SDP message, ICE candidates should only be delivered to existing instances
                if (jsonWebrtcHistoryTransferMessage.sdp != null) {
                    instance = TransferService(
                        role = TransferRole.DESTINATION,
                        transferTransportType = transferTransportType,
                        transferProtocolState = DstTransferProtocolState().apply { this.bytesOwnedIdentity = bytesOwnedIdentity }
                    ).apply {
                        (transferTransportDelegate as? WebRTCTransferTransportDelegate)?.handleJsonHistoryTransferMessage(jsonWebrtcHistoryTransferMessage)
                    }
                    // try to start the activity to track progress. It may fail if the app is in background
                    Handler(Looper.getMainLooper()).post {
                        try {
                            App.getContext().startActivity(Intent(App.getContext(), HistoryTransferActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }
                }
            }
        }

        fun abortOngoingTransfer() {
            instance?.abort()
        }

        fun getTransferProgress(): State<TransferProgress>? {
            return instance?.transferProtocolState?.transferProgress
        }

        fun getTransferMessagesEta(): State<EtaEstimator.SpeedAndEta?>? {
            return instance?.transferProtocolState?.messagesSpeedAndEta
        }

        fun getTransferFilesEta(): State<EtaEstimator.SpeedAndEta?>? {
            return instance?.transferProtocolState?.filesSpeedAndEta
        }
    }
}