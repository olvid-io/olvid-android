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

import com.fasterxml.jackson.core.type.TypeReference
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.entity.jsons.JsonMessageReference
import io.olvid.messenger.databases.entity.jsons.JsonUserMention
import io.olvid.messenger.history_transfer.json.JsonAttachment
import io.olvid.messenger.history_transfer.json.JsonMessageInThread
import io.olvid.messenger.history_transfer.json.JsonPollVoteForMessage
import io.olvid.messenger.history_transfer.json.JsonReactionToMessage
import io.olvid.messenger.history_transfer.json.SrcDiscussionDone
import io.olvid.messenger.history_transfer.json.SrcMessages
import io.olvid.messenger.history_transfer.json.SrcTransferDone
import io.olvid.messenger.history_transfer.types.SrcTransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import kotlinx.coroutines.Runnable
import java.util.concurrent.Executor


class SrcSendMessagesStep(
    val srcTransferProtocolState: SrcTransferProtocolState,
    val transferTransportDelegate: TransferTransportDelegate,
    val executor: Executor,
) : Runnable {

    override fun run() {
        Logger.i("🫠 Running step SrcSendMessagesStep")

        val batchSize = transferTransportDelegate.getMessageBatchSize()

        val db = AppDatabase.getInstance()
        // we get messages one by one for now, we'll see if we need to do better...
        srcTransferProtocolState.expectedDiscussionRanges.forEach { (discussionIdentifier, senderMap) ->
            // if we can't find the discussion, skip to next discussion
            val discussion = discussionIdentifier.getDiscussion(db, srcTransferProtocolState.bytesOwnedIdentity)

            if (discussion == null || countMessagesInRanges(rangesByThreadAndSender = senderMap) == 0) {
                // count the messages as sent so progress actually reaches 100%
                executor.execute {
                    val missingMessageCount = countMessagesInRanges(rangesByThreadAndSender = senderMap)
                    srcTransferProtocolState.sentMessageCount += missingMessageCount
                    transferTransportDelegate.sendJsonMessage(
                        messageType = TransferMessageType.SRC_DISCUSSION_DONE,
                        serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                            SrcDiscussionDone().apply {
                                this.discussion = discussionIdentifier
                                this.missingMessageCount = missingMessageCount
                            }
                        )
                    )
                }
                return@forEach
            }

            senderMap.forEach { (senderIdentifierBytesKey, threadIdMap) ->
                val senderIdentifier = senderIdentifierBytesKey.bytes
                threadIdMap.forEach { (threadId, ranges) ->
                    ranges.flatMap { it[0]..it[1] }.chunked(batchSize ?: Int.MAX_VALUE).forEach { sequenceNumbers ->
                        val messages: List<JsonMessageInThread> = sequenceNumbers.mapNotNull { sequenceNumber ->
                            // if message is no longer found, skip it
                            db.messageDao().getBySenderSequenceNumber(sequenceNumber, threadId, senderIdentifier, discussion.id)?.let { message ->
                                JsonMessageInThread().apply {
                                    this.sequenceNumber = sequenceNumber
                                    this.uidFromServer = message.inboundMessageEngineIdentifier
                                    this.timestamp = message.timestamp
                                    if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                                        this.status = JsonMessageInThread.jsonMessageStatusFromOutboundMessageStatus(message.status)
                                        if (this.status == null) {
                                            Logger.e("OUTBOUND NULL! ${message.status}")
                                        }
                                    }
                                    this.body = message.contentBody
                                    message.jsonReply?.let {
                                        try {
                                            this.reply = transferTransportDelegate.objectMapper.readValue(it, JsonMessageReference::class.java)
                                        } catch (_: Exception) { }
                                    }
                                    db.messageExpirationDao().get(messageId = message.id)?.let {
                                        this.expiration = it.expirationTimestamp
                                    }
                                    this.mentions = runCatching {
                                        message.jsonMentions?.let {
                                            AppSingleton.getJsonObjectMapper().readValue(it, object: TypeReference<List<JsonUserMention>>(){})
                                        }
                                    }.getOrNull()
                                    if (message.forwarded) {
                                        this.forwarded = true
                                    }
                                    this.edited = message.edited != Message.EDITED_NONE
                                    if (message.bookmarked) {
                                        this.bookmarked = true
                                    }
                                    // convert location sharing into one-shot location send
                                    this.location = message.getJsonLocation()?.apply {
                                        this.type = JsonLocation.TYPE_SEND
                                        this.count = null
                                        this.quality = null
                                        this.sharingExpiration = null
                                    }
                                    this.poll = message.getPoll()
                                    this.reactions = db.reactionDao().getAllForMessage(messageId = message.id).takeIf { it.isNotEmpty() }?.let { reactions ->
                                        val jsonReactionsToMessage = mutableListOf<JsonReactionToMessage>()
                                        reactions.forEach { reaction ->
                                            JsonReactionToMessage().apply {
                                                this.reaction = reaction.emoji ?: return@apply // do not append empty reactions
                                                this.sender = reaction.bytesIdentity ?: srcTransferProtocolState.bytesOwnedIdentity
                                                this.timestamp = reaction.timestamp
                                                jsonReactionsToMessage.add(this)
                                            }
                                        }
                                        return@let jsonReactionsToMessage
                                    }
                                    if (message.isPollMessage) {
                                        this.pollVotes = db.pollVoteDao().getAllForMessage(message.id).takeIf { it.isNotEmpty() }?.let { pollVotes ->
                                            val jsonPollVotesForMessage = mutableListOf<JsonPollVoteForMessage>()
                                            pollVotes.forEach { pollVote ->
                                                JsonPollVoteForMessage().apply {
                                                    this.candidate = pollVote.voteUuid
                                                    this.voted = pollVote.voted
                                                    this.version = pollVote.version
                                                    this.sender = pollVote.voter
                                                    this.timestamp = pollVote.serverTimestamp
                                                    jsonPollVotesForMessage.add(this)
                                                }
                                            }
                                            return@let jsonPollVotesForMessage
                                        }
                                    }

                                    if (message.hasAttachments() || message.linkPreviewFyleId != null) {
                                        this.attachments = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(message.id).mapNotNull { fyleAndStatus ->
                                            JsonAttachment().apply {
                                                this.sha256 = fyleAndStatus.fyle.sha256 ?: return@mapNotNull null
                                                this.number = fyleAndStatus.fyleMessageJoinWithStatus.engineNumber
                                                this.size = fyleAndStatus.fyleMessageJoinWithStatus.size
                                                this.mimeType = fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType
                                                this.filename = fyleAndStatus.fyleMessageJoinWithStatus.fileName
                                            }
                                        }.takeIf { it.isNotEmpty() }
                                    }
                                }
                            }
                        }

                        executor.execute {
                            // only actually send a message if we have some messages to send!
                            if (messages.isNotEmpty()) {
                                transferTransportDelegate.sendJsonMessage(
                                    messageType = TransferMessageType.SRC_MESSAGES,
                                    serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                                        SrcMessages().apply {
                                            this.discussion = discussionIdentifier
                                            this.sender = senderIdentifier
                                            this.threadId = Logger.getUuidString(threadId)
                                            this.messages = messages
                                            this.missingMessageCount = (sequenceNumbers.size - messages.size).coerceAtLeast(0)
                                        }
                                    )
                                )
                            }
                            srcTransferProtocolState.sentMessageCount += sequenceNumbers.size
                            if (srcTransferProtocolState.updateProgress()) {
                                transferTransportDelegate.sendJsonMessage(
                                    messageType = TransferMessageType.SRC_TRANSFER_DONE,
                                    serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                                        SrcTransferDone()
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // if there is nothing to send and updateProgress was never called, have a chance to call it now
        executor.execute {
            if (srcTransferProtocolState.updateProgress()) {
                transferTransportDelegate.sendJsonMessage(
                    messageType = TransferMessageType.SRC_TRANSFER_DONE,
                    serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                        SrcTransferDone()
                    ),
                )
            }
        }
    }
}