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
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.history_transfer.json.DstDiscussionExpectedRanges
import io.olvid.messenger.history_transfer.json.DstExpectedSha256
import io.olvid.messenger.history_transfer.json.DstRequestSha256
import io.olvid.messenger.history_transfer.json.JsonZipContact
import io.olvid.messenger.history_transfer.json.JsonZipDiscussion
import io.olvid.messenger.history_transfer.json.JsonZipExport
import io.olvid.messenger.history_transfer.json.JsonZipMessages
import io.olvid.messenger.history_transfer.json.SrcDiscussionDone
import io.olvid.messenger.history_transfer.json.SrcDiscussionList
import io.olvid.messenger.history_transfer.json.SrcDiscussionRanges
import io.olvid.messenger.history_transfer.json.SrcMessages
import io.olvid.messenger.history_transfer.steps.countMessagesInRanges
import io.olvid.messenger.history_transfer.types.AttachmentProgressListener
import io.olvid.messenger.history_transfer.types.DstTransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferAbort
import io.olvid.messenger.history_transfer.types.TransferListener
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferRole
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import io.olvid.messenger.history_transfer.types.TransferTransportLayerState
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.AesVersion
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.InputStream
import java.io.OutputStream


class ZipExportTransferTransportDelegate(
    role: TransferRole,
    transferListener: TransferListener,
    val bytesOwnedIdentity: ByteArray,
    val zipWritableFileUri: Uri,
    val password: String?,
    val context: Context,
): TransferTransportDelegate(role = role, transferListener = transferListener, objectMapper = ObjectMapper().apply {
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
    // we use a specific objectMapper that does not close the output stream after writing
    factory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
}) {
    private var outputStream: OutputStream? = null
    private var zipOutputStream: ZipOutputStream? = null
    private var aborted = TransferAbort.NONE

    private val executor = NoExceptionSingleThreadExecutor("ZipExportTransferTransportDelegate")

    // this transfer state is simply used as temporary internal storage and to track progress
    private val dstTransferState: DstTransferProtocolState = DstTransferProtocolState()
    private val jsonZipExport = JsonZipExport()
    // this set is used to collect all contact identities in order to create the JsonZipExport.contacts list
    private val allMessageSenders = mutableSetOf<BytesKey>()

    private val baseZipParameters: ZipParameters

    init {
        // set the state to initializing to show the ongoing export notification
        transferListener.onTransportLayerStateChange(TransferTransportLayerState.INITIALIZING)

        jsonZipExport.bytesOwnedIdentity = bytesOwnedIdentity
        baseZipParameters = ZipParameters().apply {
            password?.let {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                aesVersion = AesVersion.TWO
            }
        }

        executor.execute {
            try {
                outputStream = context.contentResolver.openOutputStream(zipWritableFileUri)
                zipOutputStream = ZipOutputStream(outputStream, password?.toCharArray())
                transferListener.onTransportLayerStateChange(TransferTransportLayerState.READY)
            } catch (e: Exception) {
                Logger.x(e)
                transferListener.onTransportLayerStateChange(TransferTransportLayerState.CLOSED)
            }
        }
    }


    override fun sendJsonMessage(messageType: TransferMessageType, serializedMessage: ByteArray) {
        executor.execute {
            when (messageType) {
                // we will not send these messages as we are a source
                TransferMessageType.ACK, // WebRTC only message type
                TransferMessageType.SRC_SHA256, // WebRTC only message type
                TransferMessageType.DST_EXPECTED_SHA256,
                TransferMessageType.DST_DISCUSSION_EXPECTED_RANGES,
                TransferMessageType.DST_REQUEST_SHA256,
                TransferMessageType.DST_DO_NOT_REQUEST_SHA256 -> Unit

                TransferMessageType.SRC_DISCUSSION_LIST -> {
                    val srcDiscussionList = objectMapper.readValue(serializedMessage, SrcDiscussionList::class.java)
                    dstTransferState.srcDiscussionIdentifiers = srcDiscussionList.discussions?.toSet() ?: emptySet()
                    dstTransferState.expectedSha256s = srcDiscussionList.sha256s ?: emptyMap()
                    dstTransferState.totalBytes = srcDiscussionList.sha256s?.values?.sum() ?: 0L

                    jsonZipExport.sha256s = dstTransferState.expectedSha256s

                    // respond with an empty list of known sha256
                    transferListener.onJsonMessage(
                        TransferMessageType.DST_EXPECTED_SHA256,
                        objectMapper.writeValueAsBytes(
                            DstExpectedSha256().apply {
                                expectedSha256s = dstTransferState.expectedSha256s ?: emptyMap()
                            }
                        )
                    )
                }

                TransferMessageType.SRC_DISCUSSION_RANGES -> {
                    val srcDiscussionRanges = objectMapper.readValue(
                        serializedMessage,
                        SrcDiscussionRanges::class.java
                    )
                    val ranges = srcDiscussionRanges.getRangesByThreadAndSender() ?: return@execute
                    // add the discussion to the export
                    val jsonZipDiscussion = JsonZipDiscussion().apply {
                        discussion = srcDiscussionRanges.discussion
                        title = srcDiscussionRanges.title
                    }
                    jsonZipExport.discussions?.add(jsonZipDiscussion) ?: run {
                        jsonZipExport.discussions = mutableListOf(jsonZipDiscussion)
                    }
                    // also update state titles and count the number of messages to ba able to detect that the JSON is complete
                    srcDiscussionRanges.discussion?.let { dstTransferState.receivedSrcDiscussionTitles[it] = srcDiscussionRanges.title }
                    dstTransferState.totalMessageCount += countMessagesInRanges(ranges)

                    // respond with received ranges
                    transferListener.onJsonMessage(
                        TransferMessageType.DST_DISCUSSION_EXPECTED_RANGES,
                        objectMapper.writeValueAsBytes(
                            DstDiscussionExpectedRanges().apply {
                                discussion = srcDiscussionRanges.discussion
                                rangesByThreadAndSender = srcDiscussionRanges.rangesByThreadAndSender
                            }
                        )
                    )
                }

                TransferMessageType.SRC_MESSAGES -> {
                    val srcMessages = objectMapper.readValue(serializedMessage, SrcMessages::class.java)

                    dstTransferState.receivedMessageCount += srcMessages.messages?.size ?: 0
                    dstTransferState.receivedMessageCount += srcMessages.missingMessageCount
                        ?: 0

                    // save the messages to the main JSON
                    val jsonZipMessages = JsonZipMessages().apply {
                        discussion = srcMessages.discussion
                        threadId = srcMessages.threadId
                        sender = srcMessages.sender
                        messages = srcMessages.messages
                    }
                    srcMessages.sender?.let {
                        allMessageSenders.add(BytesKey(it))
                    }
                    jsonZipExport.messages?.add(jsonZipMessages) ?: run {
                        jsonZipExport.messages = mutableListOf(jsonZipMessages)
                    }


                    if (dstTransferState.updateProgress()) {
                        // we received all the messages --> the jsonZipMessage is complete and can be written to the zip
                        writeJsonZipExportAndRequestSha256s()
                    }
                }

                TransferMessageType.SRC_DISCUSSION_DONE -> {
                    val srcDiscussionDone = objectMapper.readValue(serializedMessage, SrcDiscussionDone::class.java)

                    // simply update the progress
                    dstTransferState.receivedMessageCount += srcDiscussionDone.missingMessageCount ?: 0
                    if (dstTransferState.updateProgress()) {
                        // we received all the messages --> the jsonZipMessage is complete and can be written to the zip
                        writeJsonZipExportAndRequestSha256s()
                    }
                }

                TransferMessageType.SRC_TRANSFER_DONE -> {
                    // cleanup to properly close the zip file
                    cleanup()
                }
            }
        }
    }

    override fun sendAttachment(sha256: ByteArray, size: Long, inputStream: InputStream, attachmentProgressListener: AttachmentProgressListener?) {
        zipOutputStream?.let { zip ->
            // create the entry
            zip.putNextEntry(ZipParameters(baseZipParameters).apply {
                fileNameInZip = JsonZipExport.ATTACHMENTS_DIRECTORY_NAME + Logger.toHexString(sha256).lowercase()
            })
            // copy the bytes
            val buffer = ByteArray(65536)
            var c = inputStream.read(buffer)
            while (c >= 0) {
                zip.write(buffer, 0, c)
                attachmentProgressListener?.bytesTransferred(c.toLong())
                c = inputStream.read(buffer)
            }
            zip.closeEntry()
        }
    }

    override fun queueSendAttachmentTask(sendAttachmentRunnable: Runnable) {
        executor.execute(sendAttachmentRunnable)
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
        zipOutputStream?.let {
            zipOutputStream = null
            it.close()
        }
        outputStream?.let {
            outputStream = null
            it.close()
        }
        transferListener.onTransportLayerStateChange(TransferTransportLayerState.CLOSED)
    }

    var jsonWritten = false
    private fun writeJsonZipExportAndRequestSha256s() {
        if (jsonWritten) {
            return
        }
        zipOutputStream?.let { zip ->
            Logger.i("Writing json to zip")
            jsonWritten = true

            // before writing the JSON to the zip, we enrich it with the list of contact names
            jsonZipExport.contacts = mutableListOf()
            allMessageSenders.forEach { sender ->
                val contactDisplayName = if (sender.bytes.contentEquals(bytesOwnedIdentity)) {
                    AppDatabase.getInstance().ownedIdentityDao().get(bytesOwnedIdentity)?.displayName
                } else {
                    AppDatabase.getInstance().contactDao().get(bytesOwnedIdentity, sender.bytes)?.displayName
                }

                contactDisplayName?.let {
                    jsonZipExport.contacts?.add(
                        JsonZipContact().apply {
                            contact = sender.bytes
                            displayName = it
                        }
                    )
                }
            }

            zip.putNextEntry(ZipParameters(baseZipParameters).apply {
                fileNameInZip = JsonZipExport.DISCUSSION_AND_MESSAGES_JSON_FILE_NAME
            })
            objectMapper.writeValue(zip, jsonZipExport)
            zip.closeEntry()

            context.assets.list("export_viewer")?.forEach { fileName ->
                try {
                    context.assets.open("export_viewer/$fileName").use { inputStream ->
                        zip.putNextEntry(ZipParameters(baseZipParameters).apply {
                            fileNameInZip = fileName
                        })
                        val buffer = ByteArray(65536)
                        var c = inputStream.read(buffer)
                        while (c >= 0) {
                            zip.write(buffer, 0, c)
                            c = inputStream.read(buffer)
                        }
                        zip.closeEntry()
                    }
                } catch (e: Exception) {
                    Logger.x(e)
                }
            }

            dstTransferState.expectedSha256s
                ?.takeIf { it.isNotEmpty() }
                ?.also {
                    // if we have some sha256, add the folder to the zip
                    zip.putNextEntry(ZipParameters(baseZipParameters).apply {
                        fileNameInZip = JsonZipExport.ATTACHMENTS_DIRECTORY_NAME
                    })
                    zip.closeEntry()

                    // request all sha256
                    it.forEach { sha256key ->
                        transferListener.onJsonMessage(
                            TransferMessageType.DST_REQUEST_SHA256,
                            objectMapper.writeValueAsBytes(
                                DstRequestSha256().apply {
                                    sha256 = sha256key.key.bytes
                                }
                            )
                        )
                    }
                }

        }
    }
}