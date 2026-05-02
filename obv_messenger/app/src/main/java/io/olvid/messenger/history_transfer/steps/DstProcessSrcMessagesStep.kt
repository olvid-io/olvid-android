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
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.MessageMetadata
import io.olvid.messenger.databases.entity.PollVote
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.entity.jsons.JsonMessage
import io.olvid.messenger.databases.entity.jsons.JsonUserMention
import io.olvid.messenger.databases.tasks.UpdateReactionsTask
import io.olvid.messenger.databases.tasks.new_message.ProcessReadyToProcessOnHoldMessagesTask
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.history_transfer.json.DstRequestSha256
import io.olvid.messenger.history_transfer.json.JsonAttachment
import io.olvid.messenger.history_transfer.json.JsonMessageInThread
import io.olvid.messenger.history_transfer.json.SrcMessages
import io.olvid.messenger.history_transfer.types.DstTransferProtocolState
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import io.olvid.messenger.services.MessageExpirationService
import kotlinx.coroutines.Runnable
import java.util.UUID


class DstProcessSrcMessagesStep(
    val dstTransferProtocolState: DstTransferProtocolState,
    val srcMessages: SrcMessages,
    val transferTransportDelegate: TransferTransportDelegate,
) : Runnable {

    override fun run() {
        Logger.i("🫠 Running step DstProcessSrcMessagesStep")
        val db = AppDatabase.getInstance()

        // this step should normally only be run after receiving message ranges
        if (dstTransferProtocolState.expectedDiscussionRanges.isEmpty() || dstTransferProtocolState.expectedSha256s == null) {
            return
        }

        val bytesOwnedIdentity = dstTransferProtocolState.bytesOwnedIdentity ?: return

        // srcMessages must be complete
        val discussionIdentifier = srcMessages.discussion ?: return
        val discussionType = discussionIdentifier.getDiscussionType() ?: return
        val bytesIdentifier = discussionIdentifier.identifier ?: return
        val threadId = srcMessages.threadId ?: return
        val sender = srcMessages.sender ?: return
        val messages = srcMessages.messages ?: return
        val missingMessageCount = srcMessages.missingMessageCount ?: 0

        if (dstTransferProtocolState.expectedDiscussionRanges.containsKey(discussionIdentifier)
                .not()
        ) {
            Logger.w("🫠 DstProcessSrcMessagesStep: received messages for unexpected discussion")
            return
        }

        val discussion = db.runInTransaction<Discussion?> {
            val discussion = discussionIdentifier.getDiscussion(db, bytesOwnedIdentity)
                ?: Discussion.createLocked(
                    db,
                    bytesOwnedIdentity,
                    discussionType,
                    bytesIdentifier,
                    dstTransferProtocolState.receivedSrcDiscussionTitles[discussionIdentifier]
                )

            if (discussion == null) {
                // failed to create discussion --> abort, but still count the messages as received for the progress indicator
                dstTransferProtocolState.receivedMessageCount += messages.size + missingMessageCount
                if (dstTransferProtocolState.updateProgress()) {
                    DstRequestMissingSha256Step(
                        dstTransferProtocolState,
                        transferTransportDelegate
                    ).run()
                }
                return@runInTransaction null
            }

            if (discussion.isPreDiscussion) {
                dstTransferProtocolState.receivedSrcDiscussionTitles[discussionIdentifier]?.let {
                    discussion.title = it
                    db.discussionDao().updateTitleAndPhotoUrl(
                        discussion.id,
                        discussion.title,
                        discussion.photoUrl
                    )
                }
                discussion.status = Discussion.STATUS_LOCKED
                db.discussionDao().updateStatus(discussion.id, discussion.status)
            }

            return@runInTransaction discussion
        } ?: return

        var oneMessageHasExpiration = false
        var someOnHoldMessagesCanBeProcessed = false

        messages.forEach { jsonMessageInThread ->
            var attachmentCount = 0
            var imageAndVideoCount = 0
            var videoCount = 0
            var audioCount = 0
            var firstAttachmentName: String? = null
            val attachments = mutableListOf<JsonAttachment>()
            jsonMessageInThread.attachments?.forEach { attachment ->
                if (attachment.size == null || attachment.number == null || attachment.sha256 == null || attachment.filename == null || attachment.mimeType == null) {
                    return@forEach
                }

                try {
                    if (attachment.mimeType == OpenGraph.MIME_TYPE) {
                        attachments.add(attachment)
                        return@forEach
                    } else if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(attachment.mimeType!!)) {
                        imageAndVideoCount++
                        if (attachment.mimeType!!.startsWith("video/")) {
                            videoCount++
                        }
                    } else if (attachment.mimeType!!.startsWith("audio/")) {
                        audioCount++
                    } else {
                        if (firstAttachmentName == null) {
                            firstAttachmentName = attachment.filename
                        }
                    }
                    attachmentCount++
                } catch (_: Exception) {
                }
                attachments.add(attachment)
            }

            // returns the messageId if insertion was successful
            val transactionResult: Long? = db.runInTransaction<Long?> {
                val messageType: Int =
                    if (sender.contentEquals(bytesOwnedIdentity)) Message.TYPE_OUTBOUND_MESSAGE else Message.TYPE_INBOUND_MESSAGE
                val messageStatus =
                    JsonMessageInThread.messageStatusFromJsonMessageStatus(jsonMessageInThread.status, messageType)

                val jsonMessage: JsonMessage = JsonMessage().apply {
                    body = jsonMessageInThread.body
                    jsonReply = jsonMessageInThread.reply
                    jsonExpiration = jsonMessageInThread.expiration?.let { expiration ->
                        JsonExpiration().apply {
                            existenceDuration = expiration - System.currentTimeMillis()
                        }
                    }
                    // convert location sharing into one-shot location send: this is already done at the source, but let's be cautious 😁
                    jsonLocation = jsonMessageInThread.location?.apply {
                        this.type = JsonLocation.TYPE_SEND
                        this.count = null
                        this.quality = null
                        this.sharingExpiration = null
                    }
                    jsonUserMentions = jsonMessageInThread.mentions
                    jsonPoll = jsonMessageInThread.poll
                }

                val message = Message(
                    db,
                    jsonMessageInThread.sequenceNumber ?: return@runInTransaction null,
                    jsonMessage,
                    null,
                    jsonMessageInThread.timestamp ?: return@runInTransaction null,
                    Message.STATUS_SENT_FROM_ANOTHER_DEVICE, // we set this to avoid weird sort indexes on owned messages
                    messageType,
                    discussion.id,
                    jsonMessageInThread.uidFromServer,
                    sender,
                    UUID.fromString(threadId),
                    attachmentCount,
                    imageAndVideoCount,
                    videoCount,
                    audioCount,
                    firstAttachmentName
                )
                message.status = messageStatus
                message.mentioned = JsonUserMention.isIdentityMentioned(
                    jsonMessageInThread.mentions,
                    bytesOwnedIdentity
                )
                message.forwarded = jsonMessageInThread.forwarded == true
                if (jsonMessageInThread.edited == true) {
                    message.edited = Message.EDITED_SEEN
                }
                message.bookmarked = jsonMessageInThread.bookmarked == true

                message.id = db.messageDao().insert(message)

                if (discussion.updateLastMessageTimestamp(message.timestamp)) {
                    db.discussionDao()
                        .updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)
                }

                db.messageMetadataDao().insert(
                    MessageMetadata(
                        message.id,
                        MessageMetadata.KIND_UPLOADED,
                        message.timestamp
                    )
                )

                jsonMessageInThread.expiration?.let { expirationTimestamp ->
                    val messageExpiration =
                        MessageExpiration(0, message.id, expirationTimestamp, false)
                    db.messageExpirationDao().insert(messageExpiration)
                }

                // mark as ready to process any message that was on hold, waiting for this new message
                val count = db.onHoldInboxMessageDao().markAsReadyToProcessForMessage(
                    discussion.bytesOwnedIdentity,
                    message.senderIdentifier,
                    message.senderThreadIdentifier,
                    message.senderSequenceNumber
                )
                if (count > 0) {
                    someOnHoldMessagesCanBeProcessed = true
                }

                return@runInTransaction message.id
            }

            // check if message was properly inserted
            val message = db.messageDao().get(transactionResult ?: return@forEach) ?: return@forEach

            if (jsonMessageInThread.expiration != null) {
                oneMessageHasExpiration = true
            }

            jsonMessageInThread.reactions?.forEach { reaction ->
                UpdateReactionsTask(
                    message.id,
                    reaction.reaction ?: return@forEach,
                    (reaction.sender
                        ?: return@forEach).takeIf { it.contentEquals(bytesOwnedIdentity).not() },
                    reaction.timestamp ?: return@forEach,
                    false
                ).run()
            }

            jsonMessageInThread.poll?.let { poll ->
                jsonMessageInThread.pollVotes?.forEach { jsonPollVoteForMessage ->
                    val version = jsonPollVoteForMessage.version ?: return@forEach
                    val timestamp = jsonPollVoteForMessage.timestamp ?: return@forEach
                    val voted = jsonPollVoteForMessage.voted ?: return@forEach
                    val sender = jsonPollVoteForMessage.sender ?: return@forEach
                    val candidate = jsonPollVoteForMessage.candidate ?: return@forEach
                    if (poll.multipleChoice) {
                        var pollVote = db.pollVoteDao().get(
                            message.id,
                            sender,
                            candidate
                        )
                        if (pollVote != null) {
                            if (pollVote.version > version) {
                                return@forEach
                            }
                            pollVote.serverTimestamp = timestamp
                            pollVote.version = version
                            pollVote.voted = voted
                        } else {
                            pollVote = PollVote(
                                message.id,
                                timestamp,
                                version,
                                candidate,
                                sender,
                                voted
                            )
                        }

                        db.pollVoteDao().upsert(pollVote)
                    } else {
                        val pollVotes = db.pollVoteDao().getAllByVoter(message.id, sender)
                        for (pollVote in pollVotes) {
                            if (pollVote.version <= version) {
                                db.pollVoteDao().delete(pollVote)
                            } else {
                                return@forEach
                            }
                        }

                        // if we reach this point, all previous poll votes for this sender had a smaller version and were deleted
                        val pollVote = PollVote(
                            message.id,
                            timestamp,
                            version,
                            candidate,
                            sender,
                            voted
                        )

                        db.pollVoteDao().insert(pollVote)
                    }
                }
            }


            jsonMessageInThread.attachments?.forEach { attachment ->
                val sha256 = attachment.sha256 ?: return@forEach
                val number = attachment.number ?: return@forEach
                val size = attachment.size ?: return@forEach
                val mimeType = attachment.mimeType ?: return@forEach
                val filename = attachment.filename ?: return@forEach
                try {
                    try {
                        Fyle.acquireLock(sha256)
                        // a null imageResolution means it should be computed, empty string means there is nothing to compute
                        val imageResolution =
                            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(mimeType)) null else ""
                        var fyleId: Long
                        val fyle = db.fyleDao().getBySha256(sha256)
                        if (fyle != null) {
                            // we know this attachment
                            fyleId = fyle.id
                            if (fyle.isComplete) {
                                // the fyle is already complete, simply link it
                                val fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                                    fyle.id,
                                    message.id,
                                    bytesOwnedIdentity,
                                    fyle.filePath!!,
                                    filename,
                                    mimeType,
                                    FyleMessageJoinWithStatus.STATUS_COMPLETE,
                                    size,
                                    null,
                                    number,
                                    imageResolution
                                )
                                fyleMessageJoinWithStatus.wasOpened = true
                                db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus)
                                fyleMessageJoinWithStatus.computeTextContentForFullTextSearchOnOtherThread(
                                    db,
                                    fyle
                                )
                            } else {
                                // the fyle is incomplete, so no need to create the Fyle, but still create a FyleMessageJoinWithStatus
                                val fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                                    fyle.id,
                                    message.id,
                                    bytesOwnedIdentity,
                                    "transfer",
                                    filename,
                                    mimeType,
                                    FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED,
                                    size,
                                    null,
                                    number,
                                    imageResolution
                                )
                                fyleMessageJoinWithStatus.wasOpened = true
                                db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus)
                            }
                        } else {
                            // the file is unknown, create it and a FyleMessageJoinWithStatus
                            // this is the "normal" case
                            val newFyle = Fyle(sha256)
                            newFyle.id = db.fyleDao().insert(newFyle)
                            fyleId = newFyle.id
                            val fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                                newFyle.id,
                                message.id,
                                bytesOwnedIdentity,
                                "transfer",
                                filename,
                                mimeType,
                                FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED,
                                size,
                                null,
                                number,
                                imageResolution
                            )
                            fyleMessageJoinWithStatus.wasOpened = true
                            db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus)
                        }
                        if (message.linkPreviewFyleId == null && mimeType == OpenGraph.MIME_TYPE) {
                            message.linkPreviewFyleId = fyleId
                            db.messageDao().updateLinkPreviewFyleId(message.id, fyleId)
                        }
                    } finally {
                        Fyle.releaseLock(sha256)
                    }
                    val sha256Key = ObvBytesKey(sha256)
                    if (dstTransferProtocolState.expectedSha256s?.contains(sha256Key) == true
                        && dstTransferProtocolState.requestedSha256.contains(sha256Key).not()
                    ) {
                        // we didn't request this sha256 from the source yet --> do so
                        transferTransportDelegate.sendJsonMessage(
                            messageType = TransferMessageType.DST_REQUEST_SHA256,
                            serializedMessage = transferTransportDelegate.objectMapper.writeValueAsBytes(
                                DstRequestSha256().apply {
                                    this.sha256 = sha256
                                }
                            )
                        )
                        dstTransferProtocolState.requestedSha256.add(sha256Key)
                    }
                } catch (e: Exception) {
                    Logger.x(e)
                }
            }
        }

        dstTransferProtocolState.receivedMessageCount += messages.size + missingMessageCount
        if (dstTransferProtocolState.updateProgress()) {
            DstRequestMissingSha256Step(dstTransferProtocolState, transferTransportDelegate).run()
        }

        if (oneMessageHasExpiration) {
            App.runThread { MessageExpirationService.scheduleNextExpiration() }
        }
        if (someOnHoldMessagesCanBeProcessed) {
            // trigger the processing of any on hold message we might have marked as ready to process
            AppSingleton.getEngine().runTaskOnEngineNotificationQueue(ProcessReadyToProcessOnHoldMessagesTask(AppSingleton.getEngine(), db, discussion.bytesOwnedIdentity))
        }
    }
}