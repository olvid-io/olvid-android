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

import io.olvid.engine.datatypes.EtaEstimator
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.messenger.history_transfer.json.JsonDiscussionIdentifier
import java.util.UUID


class DstTransferProtocolState: TransferProtocolState() {
    var bytesOwnedIdentity: ByteArray? = null

    var srcDiscussionIdentifiers: Set<JsonDiscussionIdentifier>? = null
    var receivedSrcDiscussionTitles = mutableMapOf<JsonDiscussionIdentifier, String?>()

    var expectedSha256s: Map<ObvBytesKey, Long>? = null
    var expectedDiscussionRanges = mutableMapOf<JsonDiscussionIdentifier, Map<ObvBytesKey, Map<UUID, List<List<Long>>>>>()
    var totalMessageCount = 0
    var receivedMessageCount = 0

    val requestedSha256 = mutableSetOf<ObvBytesKey>()
    var missingSha256WereRequested = false
    var totalBytes = 0L
    var receivedBytes = 0L

    fun readyToReceiveMessages(): Boolean {
        return expectedSha256s != null &&
                receivedSrcDiscussionTitles.keys == srcDiscussionIdentifiers
    }

    // returns true if all messages have been received --> used to trigger a request for unrequested sha256 (if it makes sense)
    override fun updateProgress(): Boolean {
        messagesEtaEstimator?.apply {
            update(receivedMessageCount.toLong())
            messagesSpeedAndEta.value = speedAndEta
        } ?: run {
            if (readyToReceiveMessages()) {
                messagesEtaEstimator = EtaEstimator(receivedMessageCount.toLong(), totalMessageCount.toLong())
            }
        }
        filesEtaEstimator?.apply {
            update(receivedBytes)
            filesSpeedAndEta.value = speedAndEta
        } ?: run {
            if (readyToReceiveMessages()) {
                filesEtaEstimator = EtaEstimator(receivedBytes, totalBytes)
            }
        }

        if (transferProgress.value is TransferProgress.Failed) {
            return false
        } else if (!readyToReceiveMessages()) {
            transferProgress.value = TransferProgress.Negotiating
        } else if (receivedMessageCount < totalMessageCount || receivedBytes < totalBytes) {
            // if we are in the ProcessingReceivedData state, we stay in it
            transferProgress.value = if (transferProgress.value is TransferProgress.ProcessingReceivedData)
                TransferProgress.ProcessingReceivedData(receivedMessageCount, totalMessageCount, receivedBytes, totalBytes)
            else
                TransferProgress.Transferring(receivedMessageCount, totalMessageCount, receivedBytes, totalBytes)
            return receivedMessageCount == totalMessageCount
        } else {
            transferProgress.value = TransferProgress.Finished
            return true
        }
        return false
    }
}