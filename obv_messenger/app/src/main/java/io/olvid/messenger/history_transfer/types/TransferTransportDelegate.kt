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

package io.olvid.messenger.history_transfer.types

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream
import java.io.OutputStream


abstract class TransferTransportDelegate(
    val role: TransferRole,
    val transferListener: TransferListener,
    val objectMapper: ObjectMapper,
) {
    // Call this method when you need to send a JSON message to the other party.
    abstract fun sendJsonMessage(messageType: TransferMessageType, serializedMessage: ByteArray)

    // Call this methode when you need to send an Attachment to the other party.
    // The total size should be provided so the transport layer can properly split the attachment in chunks.
    // The InputStream should remain open until the function returns.
    abstract fun sendAttachment(sha256: ByteArray, size: Long, inputStream: InputStream, attachmentProgressListener: AttachmentProgressListener? = null)

    // Call this method to queue a "sendAttachment" task.
    // This allows the TransferTransportDelegate to parallelize the way it wants, or to wait for messages to have been sent
    abstract fun queueSendAttachmentTask(sendAttachmentRunnable: Runnable)

    // This method returns the number of messages to batch in one JSON message during the SrcSendMessagesStep
    // or null if no batching should occur
    abstract fun getMessageBatchSize(): Int?

    // Call this method to abort the ongoing transfer
    abstract fun abort(userInitiated: Boolean)

    // This method should return true if the transfer was aborted
    abstract fun isAborted(): Boolean

    // Call this method when transfer is finished to give the delegate a chance to clean up
    // ⚠️ calling this is mandatory for WebRTC transport delegate
    abstract fun cleanup()
}

interface TransferListener {
    // Called when the transfer layer connects, fails, etc.
    fun onTransportLayerStateChange(state: TransferTransportLayerState)
    fun setFailReason(failReason: TransferFailReason)

    // Called when a JSON message is received by the TransferTransportDelegate
    fun onJsonMessage(messageType: TransferMessageType, serializedMessage: ByteArray)

    // Called when a new sha256 is first received by the TransferTransportDelegate.
    // This should return an OutputStream where the delegate can write the received chunks, in order.
    // The delegate should be able to write to this OutPutStream (i.e. it should not be closed) until it calls onAttachmentComplete()
    // The triple also contains the expected attachment size and a progress listener to call whenever bytes a written to the file
    fun onNewAttachment(sha256: ByteArray) : Triple<OutputStream, Long, AttachmentProgressListener?>?

    // Called when an attachment has been fully received.
    // The OutputStream received from onNewAttachment() will no longer be used by the delegate
    fun onAttachmentComplete(sha256: ByteArray)

    // Called only in Zip Import once the owned identity was extracted from the zip
    fun onOwnedIdentityFound(bytesOwnedIdentity: ByteArray)
}

interface AttachmentProgressListener {
    // This method is called when count bytes have been sent
    // !! This is not cumulative, everytime this is called with argument 1, a new byte was sent
    fun bytesTransferred(count: Long)
}