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


class SrcTransferProtocolState(
    val transferScope: TransferScope,
    val bytesOwnedIdentity: ByteArray
) : TransferProtocolState() {
    var discussionIdentifiers: Set<JsonDiscussionIdentifier>? = null

    var expectedSha256s: Map<ObvBytesKey, Long>? = null
    var expectedDiscussionRanges = mutableMapOf<JsonDiscussionIdentifier, Map<ObvBytesKey, Map<UUID, List<List<Long>>>>>()
    var totalMessageCount = 0
    var sentMessageCount = 0

    val sentSha256 = mutableSetOf<ObvBytesKey>()
    var totalBytes = 0L
    var sentBytes = 0L

    fun readyToSendMessages(): Boolean {
        return expectedSha256s != null &&
                expectedDiscussionRanges.keys == discussionIdentifiers
    }

    // return true if the transfer is finished --> send the TRANSFER_DONE message
    override fun updateProgress(): Boolean {
        messagesEtaEstimator?.apply {
            update(sentMessageCount.toLong())
            messagesSpeedAndEta.value = speedAndEta
        } ?: run {
            if (readyToSendMessages()) {
                messagesEtaEstimator = EtaEstimator(sentMessageCount.toLong(), totalMessageCount.toLong())
            }
        }
        filesEtaEstimator?.apply {
            update(sentBytes)
            filesSpeedAndEta.value = speedAndEta
        } ?: run {
            if (readyToSendMessages()) {
                filesEtaEstimator = EtaEstimator(sentBytes, totalBytes)
            }
        }

        if (transferProgress.value is TransferProgress.Failed) {
            return false
        } else if (!readyToSendMessages()) {
            transferProgress.value = TransferProgress.Negotiating
        } else if (sentMessageCount < totalMessageCount || sentBytes < totalBytes) {
            transferProgress.value = TransferProgress.Transferring(sentMessageCount, totalMessageCount, sentBytes, totalBytes)
        } else {
            transferProgress.value = TransferProgress.Finished
            return true
        }
        return false
    }
}