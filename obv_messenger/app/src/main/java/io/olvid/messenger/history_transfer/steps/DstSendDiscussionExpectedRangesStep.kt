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

package io.olvid.messenger.history_transfer.steps

import io.olvid.engine.Logger
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.history_transfer.json.DstDiscussionExpectedRanges
import io.olvid.messenger.history_transfer.json.SrcDiscussionRanges
import io.olvid.messenger.history_transfer.types.DstTransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import java.util.UUID


class DstSendDiscussionExpectedRangesStep(
    val dstTransferProtocolState: DstTransferProtocolState,
    val srcDiscussionRanges: SrcDiscussionRanges,
    val transferTransportDelegate: TransferTransportDelegate
) : Runnable {
    override fun run() {
        Logger.i("🫠 Running step DstSendDiscussionExpectedRangesStep")
        val db = AppDatabase.getInstance()

        val bytesOwnedIdentity = dstTransferProtocolState.bytesOwnedIdentity ?: return

        val jsonDiscussionIdentifier = srcDiscussionRanges.discussion ?: return
        val ranges = srcDiscussionRanges.getRangesByThreadAndSender() ?: return

        // make sure DstSendExpectedSha256Step was run before
        if (dstTransferProtocolState.srcDiscussionIdentifiers == null || dstTransferProtocolState.expectedSha256s == null) {
            return
        }

        // discussion should have been announced
        if (dstTransferProtocolState.srcDiscussionIdentifiers?.contains(jsonDiscussionIdentifier) != true) {
            return
        }

        dstTransferProtocolState.receivedSrcDiscussionTitles[jsonDiscussionIdentifier] = srcDiscussionRanges.title

        val discussion: Discussion? = jsonDiscussionIdentifier.getDiscussion(db, bytesOwnedIdentity)

        val expectedRanges: Map<ObvBytesKey, Map<UUID, List<List<Long>>>>
        if (discussion == null) {
            expectedRanges = ranges
        } else {
            val knownRanges = discussion.computeMessageRanges(db)
            expectedRanges = computeDiscussionRangesDiff(ranges, knownRanges)
        }

        dstTransferProtocolState.expectedDiscussionRanges[jsonDiscussionIdentifier] = expectedRanges
        dstTransferProtocolState.totalMessageCount += countMessagesInRanges(expectedRanges)

        transferTransportDelegate.sendJsonMessage(
            messageType = TransferMessageType.DST_DISCUSSION_EXPECTED_RANGES,
            serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                DstDiscussionExpectedRanges().apply {
                    this.discussion = jsonDiscussionIdentifier
                    this.setRangesByThreadAndSender(expectedRanges)
                }
            )
        )
    }
}