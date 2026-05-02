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
import io.olvid.messenger.App
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.history_transfer.json.JsonDiscussionIdentifier
import io.olvid.messenger.history_transfer.json.SrcDiscussionList
import io.olvid.messenger.history_transfer.json.SrcDiscussionRanges
import io.olvid.messenger.history_transfer.types.SrcTransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferScope
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import kotlinx.coroutines.Runnable
import java.io.File
import java.util.concurrent.Executor


class SrcSendDiscussionsStep(
    val srcTransferProtocolState: SrcTransferProtocolState,
    val transferTransportDelegate: TransferTransportDelegate,
    val executor: Executor,
) : Runnable {

    override fun run() {
        Logger.i("🫠 Running step SrcSendDiscussionsStep")
        val db = AppDatabase.getInstance()

        val discussions = mutableListOf<Discussion>()
        val discussionIdentifiers = mutableListOf<JsonDiscussionIdentifier>()
        val fyles: List<Fyle>
        when (srcTransferProtocolState.transferScope) {
            is TransferScope.Discussions -> {
                srcTransferProtocolState.transferScope.discussionIds.forEach {
                    db.discussionDao().getById(it)?.let { discussion ->
                        // we ignore pre-discussions
                        if (discussion.isPreDiscussion.not()) {
                            discussions.add(discussion)
                            discussionIdentifiers.add(JsonDiscussionIdentifier(discussion))
                        }
                    }
                }
                fyles = if (srcTransferProtocolState.transferScope.messagesOnly)
                    emptyList()
                else
                    srcTransferProtocolState.transferScope.discussionIds.chunked(100).flatMap { batch ->
                        db.fyleDao().getAllTransferableForDiscussionIds(batch)
                    }
            }

            is TransferScope.Profile -> {
                db.discussionDao().getAllNotPreDiscussion(srcTransferProtocolState.bytesOwnedIdentity).forEach { discussion ->
                    discussions.add(discussion)
                    discussionIdentifiers.add(JsonDiscussionIdentifier(discussion))
                }
                fyles = if (srcTransferProtocolState.transferScope.messagesOnly)
                    emptyList()
                else
                    db.fyleDao().getAllTransferableForOwnedIdentity(srcTransferProtocolState.bytesOwnedIdentity)
            }
        }

        val sha256Map: MutableMap<ObvBytesKey, Long> = mutableMapOf()
        fyles.forEach { fyle ->
            try {
                fyle.filePath?.let { App.absolutePathFromRelative(it) }?.let { File(it) }?.takeIf { it.isFile && it.canRead() }?.length()?.let { length ->
                    sha256Map[ObvBytesKey(fyle.sha256)] = length
                }
            } catch (_: Exception) { }
        }

        srcTransferProtocolState.discussionIdentifiers = discussionIdentifiers.toSet()

        transferTransportDelegate.sendJsonMessage(
            messageType = TransferMessageType.SRC_DISCUSSION_LIST,
            serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                SrcDiscussionList().apply {
                    this.discussions = discussionIdentifiers
                    this.sha256s = sha256Map
                }
            )
        )

        // offload to another thread to allow processing of incoming messages while we are still sending
        App.runThread {
            discussions.forEach { discussion ->
                val jsonDiscussionIdentifier = JsonDiscussionIdentifier(discussion)
                val ranges = discussion.computeMessageRanges(db)
                executor.execute {
                    transferTransportDelegate.sendJsonMessage(
                        messageType = TransferMessageType.SRC_DISCUSSION_RANGES,
                        serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                            SrcDiscussionRanges().apply {
                                this.discussion = jsonDiscussionIdentifier
                                this.title = discussion.title
                                this.setRangesByThreadAndSender(ranges)
                            }
                        )
                    )
                }
            }
        }
    }
}