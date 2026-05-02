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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.MutableState
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
import io.olvid.messenger.databases.entity.jsons.JsonPayload
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcHistoryTransferControl
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
import io.olvid.messenger.history_transfer.types.TransferAbort
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
import java.util.UUID


class TransferService(
    val role: TransferRole,
    val transferId: String,
    val transferTransportType: TransferTransportType,
    val transferProtocolState: TransferProtocolState
): TransferListener {
    var aborted = TransferAbort.NONE
    var currentFailReason: TransferFailReason? = null
    val executor = NoExceptionSingleThreadExecutor("History transfer executor")
    val fileOutputStreams: MutableMap<BytesKey, FileOutputStream> = mutableMapOf()

    var transferTransportLayerState = TransferTransportLayerState.NOT_STARTED

    val transferTransportDelegate: TransferTransportDelegate = when(transferTransportType)  {
            is TransferTransportType.WebRtcWithOwnedDevice -> WebRTCTransferTransportDelegate(
                role = role,
                transferListener = this,
                objectMapper = AppSingleton.getJsonObjectMapper(),
                transferId = transferId,
                bytesOwnedIdentity = transferTransportType.bytesOwnedIdentity,
                bytesOtherDeviceUid = transferTransportType.bytesOtherDeviceUid,
                context = App.getContext(),
            )
            is TransferTransportType.ZipFileExport -> ZipExportTransferTransportDelegate(
                role = role,
                transferListener = this,
                bytesOwnedIdentity = transferTransportType.bytesOwnedIdentity,
                zipWritableFileUri = transferTransportType.zipWritableFileUri,
                password = transferTransportType.password,
                context = App.getContext(),
            )
            is TransferTransportType.ZipFileImport -> ZipImportTransferTransportDelegate(
                role = role,
                transferListener = this,
                objectMapper = AppSingleton.getJsonObjectMapper(),
                bytesOwnedIdentity = transferTransportType.bytesOwnedIdentity,
                zipReadableFileUri = transferTransportType.zipReadableFileUri,
                password = transferTransportType.password,
                context = App.getContext(),
            )
        }

    override fun onTransportLayerStateChange(state: TransferTransportLayerState) {
        if (transferTransportLayerState == state) {
            return
        }
        Logger.i("\uD83E\uDDF6 onTransportLayerStateChange $state")
        transferTransportLayerState = state

        when(state) {
            TransferTransportLayerState.NOT_STARTED -> {
                // nothing to do here
            }
            TransferTransportLayerState.INITIALIZING -> {
                executor.execute {
                    transferProtocolState.transferProgress.value = TransferProgress.ContactingOtherDevice
                }
                Handler(Looper.getMainLooper()).post {
                    try {
                        App.getContext().startService(Intent(App.getContext(), TransferNotificationService::class.java).apply {
                            action = TransferNotificationService.ACTION_START
                        })
                    } catch (e: Exception) {
                        Logger.x(e)
                    }
                }
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
                currentFailReason?.let {
                    transferProtocolState.transferProgress.value = TransferProgress.Failed(it)
                } ?: run {
                    Logger.w("onTransportLayerStateChange CLOSED without a failReason")
                    // if the connection is closed before it is finished and no failReason was set, try to infer the fail reason
                    if (transferProtocolState.transferProgress.value != TransferProgress.Finished) {
                        transferProtocolState.transferProgress.value =
                            if (aborted == TransferAbort.USER_ABORT)
                                TransferProgress.Failed(TransferFailReason.ABORTED)
                            else
                                TransferProgress.Failed(TransferFailReason.UNKNOWN_REASON)
                    }
                }
                // connection was closed, clean up everything
                cleanup()
            }
        }
    }

    override fun setFailReason(failReason: TransferFailReason) {
        // never overwrite a fail reason once it was set
        if (this.currentFailReason == null) {
            this.currentFailReason = failReason
        }
    }

    private fun abort(transferId: String) {
        instance?.let {
            // we make sure this instance is the one referenced in the TransferService
            // and that it's id is the one we want to abort
            if (it.transferTransportType == transferTransportType &&
                it.transferId == transferId) {
                sendWebRTCTransferControlMessage(JsonWebrtcHistoryTransferControl.REJECT_OR_ABORT_TRANSFER)
                aborted = TransferAbort.USER_ABORT
                transferTransportDelegate.abort(true)
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
            if (aborted == TransferAbort.USER_ABORT) {
                executor.shutdownNow()
            }
        }
    }

    override fun onJsonMessage(
        messageType: TransferMessageType,
        serializedMessage: ByteArray
    ) {
        if (aborted != TransferAbort.NONE) {
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

    override fun onNewAttachment(sha256: ByteArray): Triple<OutputStream, Long, AttachmentProgressListener?>? {
        if (aborted != TransferAbort.NONE) {
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
        val size: Long = (transferProtocolState as? DstTransferProtocolState)?.let { dstTransferProtocolState ->
            dstTransferProtocolState.expectedSha256s?.get(ObvBytesKey(sha256))
        } ?: 0L
        return Triple(fos, size, object : AttachmentProgressListener {
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

    override fun onAttachmentComplete(sha256: ByteArray) {
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
        if (aborted != TransferAbort.NONE) {
            return
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
                transferInProgress.value = value?.transferId
                if (value != null) {
                    // save the progress state to a cache so we can still access it after the transfer is done
                    transferProgressCache[value.transferId] = Triple(
                        value.role,
                        value.transferTransportType,
                        value.transferProtocolState.transferProgress
                    )

                    // for WebRTC transfers, request a Wi-Fi wake lock
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

        val transferInProgress = mutableStateOf<String?>(null)
        val transferProgressCache = mutableMapOf<String, Triple<TransferRole, TransferTransportType, MutableState<TransferProgress>>>()

        // this method returns the transferId if a new transfer instance was indeed created
        fun initiateHistoryTransferToOtherDevice(transferTransportType: TransferTransportType, transferScope: TransferScope) : String? {
            // If an instance has only received a notification (and never actually started) and we receive a new one, delete the old instance
            instance?.let {
                if (it.transferTransportType != transferTransportType
                    && it.transferTransportLayerState == TransferTransportLayerState.NOT_STARTED) {
                    it.cleanup()
                }
            }

            instance?.let {
                Logger.w("Unable to run two history transfers in parallel")
                return null
            }
            when(transferTransportType) {
                is TransferTransportType.WebRtcWithOwnedDevice -> {
                    val transferId = Logger.getUuidString(UUID.randomUUID())
                    instance = TransferService(
                        role = TransferRole.SOURCE,
                        transferId = transferId,
                        transferTransportType = transferTransportType,
                        transferProtocolState = SrcTransferProtocolState(
                            transferScope,
                            transferTransportType.bytesOwnedIdentity
                        )
                    )
                    sendWebRTCTransferControlMessage(JsonWebrtcHistoryTransferControl.REQUEST_TRANSFER)
                    return transferId
                }

                is TransferTransportType.ZipFileExport -> {
                    if (!StringUtils.validateUri(transferTransportType.zipWritableFileUri)) {
                        return null
                    }

                    val transferId = Logger.getUuidString(UUID.randomUUID())
                    instance = TransferService(
                        role = TransferRole.SOURCE,
                        transferId = transferId,
                        transferTransportType = transferTransportType,
                        transferProtocolState = SrcTransferProtocolState(
                            transferScope,
                            transferTransportType.bytesOwnedIdentity
                        )
                    )
                    return transferId
                }
                is TransferTransportType.ZipFileImport -> {
                    if (!StringUtils.validateUri(transferTransportType.zipReadableFileUri)) {
                        return null
                    }

                    val transferId = Logger.getUuidString(UUID.randomUUID())
                    instance = TransferService(
                        role = TransferRole.DESTINATION,
                        transferId = transferId,
                        transferTransportType = transferTransportType,
                        transferProtocolState = DstTransferProtocolState()
                    )
                    return transferId
                }
            }
        }

        fun handleJsonHistoryTransferControl(jsonWebrtcHistoryTransferControl: JsonWebrtcHistoryTransferControl, bytesOwnedIdentity: ByteArray, bytesOtherDeviceUid: ByteArray) {
            jsonWebrtcHistoryTransferControl.transferId?.let { transferId ->
                val transferTransportType = TransferTransportType.WebRtcWithOwnedDevice(
                    bytesOwnedIdentity = bytesOwnedIdentity,
                    bytesOtherDeviceUid = bytesOtherDeviceUid,
                )

                // If an instance has only received a notification (and never actually started) and we receive a new one, delete the old instance
                instance?.let {
                    if (it.transferTransportType != transferTransportType
                        && it.transferTransportLayerState == TransferTransportLayerState.NOT_STARTED) {
                        it.cleanup()
                    }
                }

                when(jsonWebrtcHistoryTransferControl.type) {
                    JsonWebrtcHistoryTransferControl.REQUEST_TRANSFER -> {
                        instance?.also {
                            Logger.w("Received a JsonWebrtcHistoryTransferControl of type REQUEST_TRANSFER while an instance is already running")
                        } ?: run {
                            val otherDeviceName = AppDatabase.getInstance().ownedDeviceDao()
                                .get(bytesOwnedIdentity, bytesOtherDeviceUid)
                                ?.getDisplayNameOrDeviceHexName(App.getContext())

                            // create an instance and wait for user to accept or reject the transfer
                            instance = TransferService(
                                role = TransferRole.DESTINATION,
                                transferId = transferId,
                                transferTransportType = transferTransportType,
                                transferProtocolState = DstTransferProtocolState().apply {
                                    this.bytesOwnedIdentity = bytesOwnedIdentity
                                    this.transferProgress.value = TransferProgress.DestinationWaitingForConfirmation(otherDeviceName)
                                }
                            )


                            // try to start the activity to track progress. It may fail if the app is in background
                            Handler(Looper.getMainLooper()).post {
                                runCatching {
                                    App.getContext().startService(
                                        Intent(
                                            App.getContext(),
                                            TransferNotificationService::class.java
                                        ).apply {
                                            action = TransferNotificationService.ACTION_INCOMING
                                            putExtra(TransferNotificationService.EXTRA_TRANSFER_ID, transferId)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    JsonWebrtcHistoryTransferControl.ACCEPT_TRANSFER -> {
                        instance?.also {
                            if (it.transferTransportType == transferTransportType &&
                                it.transferId == transferId
                            ) {
                                // If I'm indeed the initiator, send the sdp to start the actual transfer!
                                if (it.role == TransferRole.SOURCE) {
                                    (it.transferTransportDelegate as? WebRTCTransferTransportDelegate)?.transferAcceptedByTheTargetDevice()
                                } else {
                                    Logger.w("Received a JsonWebrtcHistoryTransferControl of type ACCEPT_TRANSFER while an instance where I am the TARGET is running")
                                }
                            } else {
                                Logger.w("Received a JsonWebrtcHistoryTransferControl of type ACCEPT_TRANSFER while another transfer is running")
                            }
                        } ?: run {
                            Logger.w("Received a JsonWebrtcHistoryTransferControl of type ACCEPT_TRANSFER while no instance exists")
                        }
                    }

                    JsonWebrtcHistoryTransferControl.REJECT_OR_ABORT_TRANSFER -> {
                        instance?.let {
                            if (it.transferTransportType == transferTransportType &&
                                it.transferId == transferId
                            ) {
                                it.aborted = TransferAbort.USER_ABORT
                                it.transferTransportDelegate.abort(true)
                                return@let Unit
                            } else {
                                return@let null
                            }
                        } ?: run {
                            // we received an abort while no instance is running or another instance is running
                            // --> simply try to update the fail reason
                            transferProgressCache[transferId]?.third?.value = TransferProgress.Failed(TransferFailReason.ABORTED)
                        }
                    }
                }
            }
        }


        fun handleJsonHistoryTransferMessage(jsonWebrtcHistoryTransferMessage: JsonWebrtcHistoryTransferMessage, bytesOwnedIdentity: ByteArray, bytesOtherDeviceUid: ByteArray) {
            jsonWebrtcHistoryTransferMessage.transferId?.let { transferId ->
                val transferTransportType = TransferTransportType.WebRtcWithOwnedDevice(
                    bytesOwnedIdentity = bytesOwnedIdentity,
                    bytesOtherDeviceUid = bytesOtherDeviceUid,
                )
                instance?.also {
                    if (it.transferTransportType == transferTransportType && it.transferId == transferId) {
                        (it.transferTransportDelegate as? WebRTCTransferTransportDelegate)
                            ?.handleJsonHistoryTransferMessage(jsonWebrtcHistoryTransferMessage)
                    } else {
                        Logger.w("Unable to run two history transfers in parallel")
                    }
                } ?: run {
                    Logger.w("Received a JsonWebrtcHistoryTransferMessage but no transfer is in progress")
                }
            }
        }

        fun abortOngoingTransfer(transferId: String) {
            instance?.abort(transferId)
        }

        fun acceptTransferRequest() {
            sendWebRTCTransferControlMessage(JsonWebrtcHistoryTransferControl.ACCEPT_TRANSFER)
        }

        fun getTransferProgress(transferId: String): Triple<TransferRole, TransferTransportType, State<TransferProgress>>? {
            return transferProgressCache[transferId]
        }

        fun getCurrentTransferIdAndProgressState(): Pair<String, State<TransferProgress>>? {
            return instance?.let {
                it.transferId to it.transferProtocolState.transferProgress
            }
        }

        fun getTransferMessagesEta(transferId: String): State<EtaEstimator.SpeedAndEta?>? {
            return instance?.takeIf { it.transferId == transferId }?.transferProtocolState?.messagesSpeedAndEta
        }

        fun getTransferFilesEta(transferId: String): State<EtaEstimator.SpeedAndEta?>? {
            return instance?.takeIf { it.transferId == transferId }?.transferProtocolState?.filesSpeedAndEta
        }

        private fun sendWebRTCTransferControlMessage(controlMessageType: Int) {
            instance?.let {
                (it.transferTransportDelegate as? WebRTCTransferTransportDelegate)?.let { delegate ->
                    // When accepting a transfer, we notify that the delegate as this is what
                    // distinguishes between the "awaiting confirmation" and the "ongoing transfer" state
                    if (controlMessageType == JsonWebrtcHistoryTransferControl.ACCEPT_TRANSFER) {
                        delegate.acceptTheTransferAsTargetDevice()
                    }

                    val jsonPayload = JsonPayload().apply {
                        jsonWebrtcHistoryTransferControl =
                            JsonWebrtcHistoryTransferControl().apply {
                                transferId = delegate.transferId
                                type = controlMessageType
                            }
                    }

                    AppSingleton.getEngine().postToSpecificDevices(
                        AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                        listOf(delegate.bytesOwnedIdentity),
                        listOf(delegate.bytesOtherDeviceUid),
                        delegate.bytesOwnedIdentity,
                        controlMessageType == JsonWebrtcHistoryTransferControl.REQUEST_TRANSFER, // true only for the first request message
                        false
                    )
                }
            }
        }
    }
}