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
package io.olvid.messenger.databases.tasks

import androidx.startup.StartupLogger.w
import io.olvid.engine.Logger
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.entity.jsons.JsonMessage
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import java.util.Collections

class ForwardMessagesTask(private val messageIdsToForward: List<Long>, private val discussionsIds: List<Long>) : Runnable {
    private val discussionCache: MutableMap<Long, Discussion?> = mutableMapOf()
    private val discussionExpirationCache: MutableMap<Long, JsonExpiration?> = mutableMapOf()

    override fun run() {
        if (messageIdsToForward.isEmpty() || discussionsIds.isEmpty()) {
            return
        }

        val db = getInstance()

        val messagesToForward: MutableList<Message> = mutableListOf()
        for (messageId in messageIdsToForward) {
            val message = db.messageDao().get(messageId)
            if (message == null) {
                Logger.w("ForwardMessagesTask: message not found")
                continue
            }
            if (!message.isForwardable) {
                Logger.w("ForwardMessagesTask: message not forwardable")
                continue
            }
            messagesToForward.add(message)
        }

        // sort messages so they are forwarded in their original order
        messagesToForward.sortBy { it.sortIndex }

        for (message in messagesToForward) {
            val body = message.contentBody

            var allAttachmentsComplete = true
            val fyleAndStatuses = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(message.id)
            for (fyleAndStatus in fyleAndStatuses) {
                if (!fyleAndStatus.fyle.isComplete() && fyleAndStatus.fyleMessageJoinWithStatus.mimeType != OpenGraph.MIME_TYPE) {
                    // never fail the forward because of an incomplete link preview
                    allAttachmentsComplete = false
                    break
                }
            }

            if (!allAttachmentsComplete) {
                Logger.w("ForwardMessagesTask: skipping message with incomplete attachments")
                continue
            }

            // our message is complete, we can safely iterate over the discussions in which it should be forwarded
            for (discussionId in discussionsIds) {
                val discussion: Discussion?
                if (discussionCache.containsKey(discussionId)) {
                    discussion = discussionCache[discussionId]
                } else {
                    discussion = db.discussionDao().getById(discussionId)
                    discussionCache[discussionId] = discussion
                }

                if (discussion == null) {
                    Logger.w("ForwardMessagesTask: discussion not found")
                    continue
                }

                if (!discussion.isNormal) {
                    Logger.w("ForwardMessagesTask: discussion is read only of locked")
                    continue
                }


                val jsonExpiration: JsonExpiration?
                if (discussionExpirationCache.containsKey(discussionId)) {
                    jsonExpiration = discussionExpirationCache[discussionId]
                } else {
                    jsonExpiration = db.discussionCustomizationDao().get(discussionId)?.getExpirationJson()
                    discussionExpirationCache[discussionId] = jsonExpiration
                }

                val jsonMessage = JsonMessage(body)
                if (message.isLocationMessage) {
                    // manually copy location data to forwarded message (to be sure it becomes a send location message and not a sharing location)
                    val jsonLocation = message.getJsonLocation() ?: continue
                    jsonLocation.setType(JsonLocation.TYPE_SEND)
                    jsonMessage.setJsonLocation(jsonLocation)
                }
                if (jsonExpiration != null) {
                    jsonMessage.setJsonExpiration(jsonExpiration)
                }
                // for now, poll messages cannot be forwarded, but keep this for when we change our mind!
                val jsonPoll = message.getPoll()
                if (jsonPoll != null) {
                    jsonMessage.setJsonPoll(jsonPoll)
                }

                db.runInTransaction {
                    discussion.lastOutboundMessageSequenceNumber++
                    db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber)
                    discussion.updateLastMessageTimestamp(System.currentTimeMillis())
                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)

                    val newMessage = Message(
                        db,
                        discussion.lastOutboundMessageSequenceNumber,
                        jsonMessage,
                        null,
                        System.currentTimeMillis(),
                        Message.STATUS_UNPROCESSED,
                        Message.TYPE_OUTBOUND_MESSAGE,
                        discussionId,
                        null,
                        discussion.bytesOwnedIdentity,
                        discussion.senderThreadIdentifier,
                        0,
                        0,
                        0,
                        0,
                        null
                    )
                    if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE) {
                        newMessage.forwarded = true
                    }
                    newMessage.id = db.messageDao().insert(newMessage)

                    for (fyleAndStatus in fyleAndStatuses) {
                        if (fyleAndStatus.fyle.isComplete) {
                            val fyleMessageJoinWithStatus = FyleMessageJoinWithStatus.createDraft(
                                fyleAndStatus.fyle.id,
                                newMessage.id,
                                discussion.bytesOwnedIdentity,
                                fyleAndStatus.fyle.filePath,
                                fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                                fyleAndStatus.fyleMessageJoinWithStatus.mimeType,
                                fyleAndStatus.fyleMessageJoinWithStatus.size
                            )
                            db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus)
                        }
                    }
                    newMessage.recomputeAttachmentCount(db)
                    db.messageDao().updateAttachmentCount(newMessage.id, newMessage.totalAttachmentCount, newMessage.imageAndVideoCount, message.videoCount, message.audioCount, message.firstAttachmentName, 0, newMessage.imageResolutions)
                    newMessage.post(false, null)
                }
            }
        }
    }
}
