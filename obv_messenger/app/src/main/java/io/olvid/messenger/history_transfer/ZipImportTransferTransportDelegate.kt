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
import android.net.Uri
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.messenger.history_transfer.json.DstDiscussionExpectedRanges
import io.olvid.messenger.history_transfer.json.DstDoNotRequestSha256
import io.olvid.messenger.history_transfer.json.DstExpectedSha256
import io.olvid.messenger.history_transfer.json.DstRequestSha256
import io.olvid.messenger.history_transfer.json.JsonDiscussionIdentifier
import io.olvid.messenger.history_transfer.json.JsonZipExport
import io.olvid.messenger.history_transfer.json.SrcDiscussionDone
import io.olvid.messenger.history_transfer.json.SrcDiscussionList
import io.olvid.messenger.history_transfer.json.SrcDiscussionRanges
import io.olvid.messenger.history_transfer.json.SrcMessages
import io.olvid.messenger.history_transfer.json.SrcTransferDone
import io.olvid.messenger.history_transfer.steps.countMessagesInRanges
import io.olvid.messenger.history_transfer.types.AttachmentProgressListener
import io.olvid.messenger.history_transfer.types.SrcTransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferAbort
import io.olvid.messenger.history_transfer.types.TransferFailReason
import io.olvid.messenger.history_transfer.types.TransferListener
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferRole
import io.olvid.messenger.history_transfer.types.TransferScope
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import io.olvid.messenger.history_transfer.types.TransferTransportLayerState
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.LocalFileHeader
import java.io.InputStream
import java.util.UUID


class ZipImportTransferTransportDelegate(
    role: TransferRole,
    transferListener: TransferListener,
    objectMapper: ObjectMapper,
    val bytesOwnedIdentity: ByteArray,
    val zipReadableFileUri: Uri,
    val password: String?,
    val context: Context,
) : TransferTransportDelegate(role = role, transferListener = transferListener, objectMapper = objectMapper) {
    private var inputStream: InputStream? = null
    private var zipInputStream: ZipInputStream? = null
    private var aborted = TransferAbort.NONE

    private val executor = NoExceptionSingleThreadExecutor("ZipImportTransferTransportDelegate")

    private var jsonZipExport: JsonZipExport? = null
    private var srcTransferState: SrcTransferProtocolState? = null
    private val pendingSha256 = mutableSetOf<ObvBytesKey>()

    init {
        executor.execute {
            // set the state to initializing to show the ongoing import notification
            transferListener.onTransportLayerStateChange(TransferTransportLayerState.INITIALIZING)

            try {
                inputStream = context.contentResolver.openInputStream(zipReadableFileUri)
                zipInputStream = ZipInputStream(inputStream, password?.toCharArray())
                var foundAnAttachmentBeforeTheMessages = false
                try {
                    zipInputStream?.let { zip ->
                        try {
                            // first find the JSON and read it
                            var zipEntry: LocalFileHeader? = zip.nextEntry
                            while (zipEntry != null) {
                                if (zipEntry.fileName == JsonZipExport.DISCUSSION_AND_MESSAGES_JSON_FILE_NAME) {
                                    jsonZipExport = objectMapper.reader()
                                        .without(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                                        .readValue(zip, JsonZipExport::class.java)
                                    break
                                } else if (zipEntry.fileName.startsWith(JsonZipExport.ATTACHMENTS_DIRECTORY_NAME)) {
                                    Logger.e("ZIP if badly ordered")
                                    foundAnAttachmentBeforeTheMessages = true
                                }
                                zipEntry = zip.nextEntry
                            }

                            jsonZipExport?.let { jsonZipExport ->
                                // check that the ownedIdentity matches what was passed
                                if (jsonZipExport.bytesOwnedIdentity.contentEquals(
                                        bytesOwnedIdentity
                                    )
                                ) {
                                    // pass this owned identity to the transfer service so the dstState can be properly initialized
                                    transferListener.onOwnedIdentityFound(bytesOwnedIdentity)
                                    // only create the source state if the identity exists
                                    srcTransferState = SrcTransferProtocolState(
                                        TransferScope.Profile(messagesOnly = false),
                                        bytesOwnedIdentity
                                    )
                                } else {
                                    transferListener.setFailReason(TransferFailReason.OWNED_IDENTITY_MISMATCH)
                                }
                            } ?: {
                                transferListener.setFailReason(TransferFailReason.BAD_ZIP_FORMAT)
                            }
                        } catch (e: ZipException) {
                            Logger.x(e)
                            transferListener.setFailReason(TransferFailReason.BAD_ZIP_PASSWORD)
                        }
                    }
                } finally {
                    if (foundAnAttachmentBeforeTheMessages) {
                        Logger.e("ZIP closing input streams")
                        zipInputStream?.close()
                        inputStream?.close()
                        zipInputStream = null
                        inputStream = null
                    }
                }

                if (srcTransferState == null) {
                    // if we did not initialize the state
                    transferListener.onTransportLayerStateChange(TransferTransportLayerState.CLOSED)
                } else {
                    if (inputStream == null) {
                        inputStream = context.contentResolver.openInputStream(zipReadableFileUri)
                        zipInputStream = ZipInputStream(inputStream, password?.toCharArray())
                    }
                    transferListener.onTransportLayerStateChange(TransferTransportLayerState.READY)

                    // compute the initial SrcTransferProtocolState from the jsonZipExport
                    srcTransferState?.discussionIdentifiers = jsonZipExport?.discussions?.mapNotNull { it.discussion }?.toSet()


                    // send the first message to start the protocol
                    transferListener.onJsonMessage(
                        messageType = TransferMessageType.SRC_DISCUSSION_LIST,
                        serializedMessage = objectMapper.writeValueAsBytes(
                            SrcDiscussionList().apply {
                                this.discussions = jsonZipExport?.discussions?.mapNotNull { it.discussion }
                                this.sha256s = jsonZipExport?.sha256s
                            }
                        )
                    )

                    val allDiscussionRanges = mutableMapOf<JsonDiscussionIdentifier, Map<ObvBytesKey, Map<UUID, List<List<Long>>>>>()

                    jsonZipExport?.messages?.forEach { jsonZipMessages ->
                        val discussionIdentifier = jsonZipMessages.discussion ?: return@forEach
                        val senderBytesKey = jsonZipMessages.sender?.let { ObvBytesKey(it) } ?: return@forEach
                        val threadId = UUID.fromString(jsonZipMessages.threadId) ?: return@forEach

                        val discussionMap = allDiscussionRanges[discussionIdentifier]?.toMutableMap() ?: mutableMapOf()
                        val senderMap = discussionMap[senderBytesKey]?.toMutableMap() ?: mutableMapOf()
                        val threadList = mutableListOf<List<Long>>()
                        var currentStride: MutableList<Long>? = null

                        // sort sequence numbers and compute ranges, as in RangeUtils Discussion.computeMessageRanges()
                        jsonZipMessages.messages?.mapNotNull { it.sequenceNumber }?.sorted()?.forEach { sequenceNumber ->
                            if (currentStride != null && (sequenceNumber - 1 < currentStride[1])) {
                                // this only happens if messages were not properly sorted, or if we have duplicate sequence numbers
                                // ==> simply ignore this message by doing nothing
                            } else if (sequenceNumber - 1 == currentStride?.get(1)) {
                                currentStride[1]++
                            } else {
                                currentStride = mutableListOf(sequenceNumber, sequenceNumber)
                                threadList.add(currentStride)
                            }
                        }

                        senderMap[threadId] = threadList
                        discussionMap[senderBytesKey] = senderMap
                        allDiscussionRanges[discussionIdentifier] = discussionMap
                    }

                    // also add empty discussions that are listed in the zip to the srcDiscussionRanges
                    // otherwise we never reach the readyToSendMessages() state
                    srcTransferState?.discussionIdentifiers?.forEach { discussionIdentifier ->
                        if (!allDiscussionRanges.contains(discussionIdentifier)) {
                            allDiscussionRanges[discussionIdentifier] = emptyMap()
                        }
                    }


                    allDiscussionRanges.forEach { (discussionIdentifier, ranges) ->
                        // send discussion ranges
                        transferListener.onJsonMessage(
                            messageType = TransferMessageType.SRC_DISCUSSION_RANGES,
                            serializedMessage = objectMapper.writeValueAsBytes(
                                SrcDiscussionRanges().apply {
                                    this.discussion = discussionIdentifier
                                    this.title = jsonZipExport?.discussions?.find { it.discussion == discussionIdentifier }?.title
                                    setRangesByThreadAndSender(ranges)
                                }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.x(e)
                transferListener.onTransportLayerStateChange(TransferTransportLayerState.CLOSED)
            }
        }
    }


    override fun sendJsonMessage(messageType: TransferMessageType, serializedMessage: ByteArray) {
        executor.execute {
            when(messageType) {
                // we will not send these messages as we are a destination
                TransferMessageType.ACK, // WebRTC only message type
                TransferMessageType.SRC_SHA256, // WebRTC only message type
                TransferMessageType.SRC_DISCUSSION_LIST,
                TransferMessageType.SRC_DISCUSSION_RANGES,
                TransferMessageType.SRC_MESSAGES,
                TransferMessageType.SRC_DISCUSSION_DONE,
                TransferMessageType.SRC_TRANSFER_DONE -> Unit

                TransferMessageType.DST_EXPECTED_SHA256 -> {
                    val dstExpectedSha256 = objectMapper.readValue(serializedMessage, DstExpectedSha256::class.java)
                    srcTransferState?.expectedSha256s = dstExpectedSha256.expectedSha256s
                    srcTransferState?.totalBytes = dstExpectedSha256.expectedSha256s?.values?.sum() ?: 0L

                    if (srcTransferState?.readyToSendMessages() == true) {
                        processZipMessages()
                    }
                }

                TransferMessageType.DST_DISCUSSION_EXPECTED_RANGES -> {
                    val dstDiscussionExpectedRanges = objectMapper.readValue(serializedMessage, DstDiscussionExpectedRanges::class.java)

                    val discussionIdentifier = dstDiscussionExpectedRanges.discussion ?: return@execute
                    val expectedRanges = dstDiscussionExpectedRanges.getRangesByThreadAndSender() ?: emptyMap()

                    if (srcTransferState?.discussionIdentifiers?.contains(discussionIdentifier) != true
                        || srcTransferState?.expectedDiscussionRanges?.contains(discussionIdentifier) != false) {
                        // only accept discussionIdentifier for which we have sent a range and not already received one
                        return@execute
                    }

                    srcTransferState?.expectedDiscussionRanges[discussionIdentifier] = expectedRanges
                    srcTransferState?.totalMessageCount += countMessagesInRanges(rangesByThreadAndSender = expectedRanges)

                    if (srcTransferState?.readyToSendMessages() == true) {
                        processZipMessages()
                    }
                }

                // when receiving these, wait for all the requests to have been received so we can process ZipEntry in order
                TransferMessageType.DST_REQUEST_SHA256 -> {
                    val dstRequestSha256 = objectMapper.readValue(serializedMessage, DstRequestSha256::class.java)
                    val sha256 = dstRequestSha256.sha256 ?: return@execute
                    val sha256key = ObvBytesKey(sha256)

                    pendingSha256.add(sha256key)

                    if (pendingSha256.size == srcTransferState?.let { (it.expectedSha256s?.size ?: 0) - it.sentSha256.size} ) {
                        sendPendingAttachments()
                    }
                }

                TransferMessageType.DST_DO_NOT_REQUEST_SHA256 -> {
                    val dstDoNotRequestSha256 = objectMapper.readValue(serializedMessage, DstDoNotRequestSha256::class.java)
                    val sha256 = dstDoNotRequestSha256.sha256 ?: return@execute
                    val sha256key = ObvBytesKey(sha256)
                    // only send sha256 that were planned
                    val size = srcTransferState?.expectedSha256s?.get(sha256key) ?: return@execute

                    if (srcTransferState?.sentSha256?.add(sha256key) == true) {
                        srcTransferState?.sentBytes += size

                        if (pendingSha256.size == srcTransferState?.let { (it.expectedSha256s?.size ?: 0) - it.sentSha256.size} ) {
                            sendPendingAttachments()
                        }
                    }
                }
            }
        }
    }

    override fun sendAttachment(
        sha256: ByteArray,
        size: Long,
        inputStream: InputStream,
        attachmentProgressListener: AttachmentProgressListener?
    ) {
        // nothing to do here, we are the destination side
    }

    override fun queueSendAttachmentTask(sendAttachmentRunnable: Runnable) {
        // nothing to do here, we are the destination side
    }

    override fun getMessageBatchSize(): Int? {
        return null
    }

    override fun abort(userInitiated: Boolean) {
        aborted = if (userInitiated) TransferAbort.USER_ABORT else TransferAbort.DISCONNECT
        cleanup()
    }

    override fun isAborted(): Boolean {
        return aborted != TransferAbort.NONE
    }

    override fun cleanup() {
        zipInputStream?.let {
            zipInputStream = null
            it.close()
        }
        inputStream?.let {
            inputStream = null
            it.close()
        }
        transferListener.onTransportLayerStateChange(TransferTransportLayerState.CLOSED)
    }

    var processZipMessagesCalled: Boolean = false

    private fun processZipMessages() {
        if (processZipMessagesCalled) {
            return
        }
        processZipMessagesCalled = true
        jsonZipExport?.messages?.forEach { jsonZipMessages ->
            val discussionIdentifier = jsonZipMessages.discussion ?: return@forEach
            val senderIdentifier = jsonZipMessages.sender ?: return@forEach
            val threadId = jsonZipMessages.threadId ?: return@forEach

            val sequenceNumbers = srcTransferState?.expectedDiscussionRanges
                ?.get(discussionIdentifier)
                ?.get(ObvBytesKey(senderIdentifier))
                ?.get(UUID.fromString(threadId))
                ?.flatMap { it[0]..it[1] }
                ?.takeIf { it.isNotEmpty() }
                ?.toSet()
                ?: return@forEach

            val messages = jsonZipMessages.messages?.filter { sequenceNumbers.contains(it.sequenceNumber) } ?: return@forEach
            transferListener.onJsonMessage(
                messageType = TransferMessageType.SRC_MESSAGES,
                serializedMessage = objectMapper.writeValueAsBytes(
                    SrcMessages().apply {
                        this.discussion = discussionIdentifier
                        this.sender = senderIdentifier
                        this.threadId = threadId
                        this.messages = messages
                        this.missingMessageCount = (sequenceNumbers.size - messages.size).coerceAtLeast(0)
                    }
                )
            )
            srcTransferState?.sentMessageCount += sequenceNumbers.size
        }

        // for discussions where no messages were expected, send a SrcDiscussionDone message.
        // Otherwise, if an import has no message to import, it remains stuck on "negotiating"
        srcTransferState?.expectedDiscussionRanges?.filter { countMessagesInRanges(it.value) == 0 }?.forEach {
            transferListener.onJsonMessage(
                messageType = TransferMessageType.SRC_DISCUSSION_DONE,
                serializedMessage = objectMapper.writeValueAsBytes(
                    SrcDiscussionDone().apply {
                        this.discussion = it.key
                        this.missingMessageCount = 0
                    }
                )
            )
        }

        // if there are no attachments to send, notify the transfer is done
        if (srcTransferState?.updateProgress() == true) {
            transferListener.onJsonMessage(
                messageType = TransferMessageType.SRC_TRANSFER_DONE,
                serializedMessage = objectMapper.writeValueAsBytes(
                    SrcTransferDone()
                )
            )
        }
    }

    var sendPendingAttachmentsCalled: Boolean = false

    private fun sendPendingAttachments() {
        if (sendPendingAttachmentsCalled) {
            return
        }
        sendPendingAttachmentsCalled = true
        zipInputStream?.let { zip ->
            runCatching {
                var zipEntry: LocalFileHeader? = zip.nextEntry
                while (zipEntry != null) {
                    // skip entries that are not files
                    if (zipEntry.fileName.startsWith(JsonZipExport.ATTACHMENTS_DIRECTORY_NAME)) {
                        try {
                            val sha256 =
                                Logger.fromHexString(zipEntry.fileName.substring(JsonZipExport.ATTACHMENTS_DIRECTORY_NAME.length))
                            val sha256key = ObvBytesKey(sha256)
                            if (pendingSha256.contains(sha256key)) {
                                transferListener.onNewAttachment(sha256)?.let { triple ->
                                    val outputStream = triple.first
                                    val attachmentProgressListener = triple.third

                                    var totalSize = 0L
                                    val buffer = ByteArray(65536)
                                    var c = zip.read(buffer)
                                    while (c >= 0) {
                                        outputStream.write(buffer, 0, c)
                                        totalSize += c
                                        attachmentProgressListener?.bytesTransferred(c.toLong())
                                        c = zip.read(buffer)
                                    }
                                    transferListener.onAttachmentComplete(sha256)
                                    srcTransferState?.sentBytes += totalSize
                                }
                            }
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }
                    zipEntry = zip.nextEntry
                }
            }
        }

        // this test should always be true. Anyway, we have nothing more to send!
        // ==> notify the transfer is done
        if (srcTransferState?.updateProgress() == true) {
            transferListener.onJsonMessage(
                messageType = TransferMessageType.SRC_TRANSFER_DONE,
                serializedMessage = objectMapper.writeValueAsBytes(
                    SrcTransferDone()
                )
            )
        }
    }
}