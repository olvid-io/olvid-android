/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.databases.tasks.new_message

import io.olvid.engine.Logger
import io.olvid.engine.datatypes.containers.GroupV2
import io.olvid.engine.engine.Engine
import io.olvid.engine.engine.types.ObvMessage
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.FyleProgressSingleton.updateProgress
import io.olvid.messenger.UnreadCountsSingleton.newLocationSharingMessage
import io.olvid.messenger.UnreadCountsSingleton.newUnreadMessage
import io.olvid.messenger.UnreadCountsSingleton.removeLocationSharingMessage
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.Fyle.JsonMetadata
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.LatestDiscussionSenderSequenceNumber
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.MessageMetadata
import io.olvid.messenger.databases.entity.MessageRecipientInfo
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest
import io.olvid.messenger.databases.entity.jsons.JsonMessage
import io.olvid.messenger.databases.entity.jsons.JsonReturnReceipt
import io.olvid.messenger.databases.tasks.HandleStalledReturnReceipts
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.services.AvailableSpaceHelper
import io.olvid.messenger.services.MessageExpirationService
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity
import java.util.UUID
import java.util.concurrent.Callable


fun handleNormalMessage(
    db: AppDatabase,
    jsonMessage: JsonMessage,
    jsonReturnReceipt: JsonReturnReceipt?,
    messageSender: MessageSender,
    obvMessage: ObvMessage,
    engine: Engine
): HandleMessageOutput {
    if (putMessageOnHoldIfDiscussionIsMissing(
            db,
            obvMessage.identifier,
            obvMessage.serverTimestamp,
            messageSender,
            jsonMessage.oneToOneIdentifier,
            jsonMessage.groupOwner,
            jsonMessage.groupUid,
            jsonMessage.groupV2Identifier
        )
    ) {
        return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
    }

    getDiscussion(
        db,
        jsonMessage.groupUid,
        jsonMessage.groupOwner,
        jsonMessage.groupV2Identifier,
        jsonMessage.oneToOneIdentifier,
        messageSender,
        GroupV2.Permission.SEND_MESSAGE
    )?.takeIf {
        it.lastRemoteDeleteTimestamp <= obvMessage.serverTimestamp // if the message is older than the latest "delete discussion" request, delete the message
    }?.let { discussion ->

        val remoteDeleteAndEditRequest = db.remoteDeleteAndEditRequestDao().getBySenderSequenceNumber(jsonMessage.senderSequenceNumber, jsonMessage.senderThreadIdentifier, messageSender.senderIdentity, discussion.id)
        val jsonExpiration = jsonMessage.jsonExpiration

        if (jsonExpiration?.readOnce == true && messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
            // never display read once messages received from another owned device
            return@let
        }

        var messageShouldBeRemoteDeleted = false
        if (remoteDeleteAndEditRequest?.requestType == RemoteDeleteAndEditRequest.TYPE_DELETE) {
            // check whether the remote delete request comes from another owned device or the message sender or an authorized group member
            if (remoteDeleteAndEditRequest.remoteDeleter.contentEquals(messageSender.bytesOwnedIdentity)) {
                messageShouldBeRemoteDeleted = true
            } else if (remoteDeleteAndEditRequest.remoteDeleter != null && discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                db.group2MemberDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier, remoteDeleteAndEditRequest.remoteDeleter!!)
                    ?.also { group2Member ->
                        messageShouldBeRemoteDeleted = group2Member.permissionRemoteDeleteAnything
                                || ( group2Member.permissionEditOrRemoteDeleteOwnMessages
                                && remoteDeleteAndEditRequest.remoteDeleter.contentEquals(messageSender.senderIdentity) )
                    }
                    ?: run {
                        db.group2PendingMemberDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier, remoteDeleteAndEditRequest.remoteDeleter!!)
                            ?.also { group2PendingMember ->
                                messageShouldBeRemoteDeleted = group2PendingMember.permissionRemoteDeleteAnything
                                        || ( group2PendingMember.permissionEditOrRemoteDeleteOwnMessages
                                        && remoteDeleteAndEditRequest.remoteDeleter.contentEquals(messageSender.senderIdentity) )
                            }
                    }
            }
        }

        if (messageShouldBeRemoteDeleted) {
            // a request to remote delete the received message was already received!
            try {
                db.onHoldInboxMessageDao().getAllForMessage(
                    discussion.bytesOwnedIdentity,
                    messageSender.senderIdentity,
                    jsonMessage.senderThreadIdentifier,
                    jsonMessage.senderSequenceNumber
                ).forEach { onHoldInboxMessage ->
                    if (
                        (discussion.discussionType == Discussion.TYPE_CONTACT && onHoldInboxMessage.bytesContactIdentity.contentEquals(discussion.bytesDiscussionIdentifier))
                        || (discussion.discussionType == Discussion.TYPE_GROUP && onHoldInboxMessage.bytesGroupOwnerAndUid.contentEquals(discussion.bytesDiscussionIdentifier))
                        || (discussion.discussionType == Discussion.TYPE_GROUP_V2 && onHoldInboxMessage.bytesGroupIdentifier.contentEquals(discussion.bytesDiscussionIdentifier))
                    ) {
                        db.onHoldInboxMessageDao().delete(onHoldInboxMessage)
                    }
                }
            } catch (e: Exception) {
                Logger.x(e)
            }

            // check if we should keep a trace of this remote deleted message
            if (remoteDeleteAndEditRequest != null) {
                if (messageSender.bytesOwnedIdentity.contentEquals(remoteDeleteAndEditRequest.remoteDeleter)
                    || !SettingsActivity.retainRemoteDeletedMessages) {
                    // no need to create a "remote deleted message"
                    db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest)

                    if (jsonReturnReceipt != null) {
                        AppSingleton.getEngine().sendReturnReceipt(messageSender.bytesOwnedIdentity, messageSender.senderIdentity, Message.RETURN_RECEIPT_STATUS_DELIVERED, jsonReturnReceipt.nonce, jsonReturnReceipt.key, null)
                    }
                } else {
                    // we create a "remote deleted message"
                    val transactionResult: Pair<Long, Boolean> = db.runInTransaction(Callable {
                        var sendExpireIntent = false
                        val message = Message(
                            db,
                            jsonMessage.senderSequenceNumber,
                            jsonMessage,
                            jsonReturnReceipt,
                            obvMessage.serverTimestamp,
                            if (messageSender.type == MessageSender.Type.CONTACT) Message.STATUS_READ else Message.STATUS_SENT_FROM_ANOTHER_DEVICE,
                            if (messageSender.type == MessageSender.Type.CONTACT) Message.TYPE_INBOUND_MESSAGE else Message.TYPE_OUTBOUND_MESSAGE,
                            discussion.id,
                            obvMessage.identifier,
                            messageSender.senderIdentity,
                            jsonMessage.senderThreadIdentifier,
                            0,
                            0
                        )

                        message.missedMessageCount = processSequenceNumber(db, discussion.id, messageSender.senderIdentity, jsonMessage.senderThreadIdentifier, jsonMessage.senderSequenceNumber)
                        message.contentBody = null
                        message.jsonReply = null
                        message.jsonLocation = null
                        message.locationType = Message.LOCATION_TYPE_NONE
                        message.jsonMentions = null
                        message.jsonPoll = null

                        message.limitedVisibility = false
                        message.wipeStatus = Message.WIPE_STATUS_REMOTE_DELETED
                        message.wipedAttachmentCount = obvMessage.attachments.size

                        message.id = db.messageDao().insert(message)

                        if (discussion.updateLastMessageTimestamp(obvMessage.serverTimestamp)) {
                            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)
                        }

                        db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_UPLOADED, obvMessage.serverTimestamp))
                        db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_DELIVERED, System.currentTimeMillis()))
                        db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_REMOTE_DELETED, remoteDeleteAndEditRequest.serverTimestamp, remoteDeleteAndEditRequest.remoteDeleter))

                        if (jsonExpiration != null) {
                            if (jsonExpiration.getExistenceDuration() != null) {
                                val expirationTimestamp = obvMessage.localDownloadTimestamp + jsonExpiration.getExistenceDuration() * 1000L - (obvMessage.downloadTimestamp - obvMessage.serverTimestamp).coerceAtLeast(0)
                                val messageExpiration = MessageExpiration(0, message.id, expirationTimestamp, false)
                                db.messageExpirationDao().insert(messageExpiration)
                                sendExpireIntent = true
                            }
                        }

                        db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest)
                        Pair(message.id, sendExpireIntent)
                    })

                    if (transactionResult.second) {
                        App.runThread { MessageExpirationService.scheduleNextExpiration() }
                    }

                    val message = db.messageDao().get(transactionResult.first)
                    if (message == null) {
                        Logger.w("Failed to insert new message in db.")
                        return HandleMessageOutput.DO_NOTHING
                    }

                    if (jsonReturnReceipt != null) {
                        // send DELIVERED return receipt
                        message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_DELIVERED)
                    }
                }
            }

            return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
        } else {
            val attachmentMetadatas: Array<JsonMetadata?> = arrayOfNulls(obvMessage.attachments.size)

            var imageCount = 0
            var attachmentCount = 0
            for (i in 0..<obvMessage.attachments.size) {
                try {
                    val metadata = AppSingleton.getJsonObjectMapper().readValue(obvMessage.attachments[i].metadata, JsonMetadata::class.java)
                    val mimeType = PreviewUtils.getNonNullMimeType(metadata.type, metadata.fileName)
                    // correct the received mime type in case it was invalid
                    metadata.type = mimeType
                    if (mimeType == OpenGraph.MIME_TYPE) {
                        continue
                    } else if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(mimeType)) {
                        imageCount++
                    }
                    attachmentCount++
                    attachmentMetadatas[i] = metadata
                } catch (_: Exception) {
                    attachmentMetadatas[i] = null
                }
            }

            val transactionResult: Pair<Long, Boolean> = db.runInTransaction(Callable {
                var sendExpireIntent = false
                val messageType: Int
                val messageStatus: Int

                if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
                    messageType = Message.TYPE_OUTBOUND_MESSAGE
                    messageStatus = Message.STATUS_SENT_FROM_ANOTHER_DEVICE
                    // read once messages have already been discarded
                    // existence duration and visibility duration should be treated the same way --> we move visibility to existence
                    if (jsonExpiration?.visibilityDuration != null) {
                        if (jsonExpiration.existenceDuration == null || jsonExpiration.existenceDuration > jsonExpiration.visibilityDuration) {
                            jsonExpiration.existenceDuration = jsonExpiration.visibilityDuration
                        }
                    }
                } else if (jsonExpiration == null || (jsonExpiration.visibilityDuration == null && jsonExpiration.readOnce != true)) {
                    messageType = Message.TYPE_INBOUND_MESSAGE
                    messageStatus = Message.STATUS_UNREAD
                    jsonMessage.jsonExpiration = null // we clear the jsonExpiration part before creating the message. The existence duration is taken into account a few lines below
                } else {
                    // the message has a visibility duration or is read once
                    messageType = Message.TYPE_INBOUND_EPHEMERAL_MESSAGE
                    messageStatus = Message.STATUS_UNREAD
                }

                var messageServerTimestamp = obvMessage.serverTimestamp
                if (jsonMessage.originalServerTimestamp != null && discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                    messageServerTimestamp = messageServerTimestamp.coerceAtMost(jsonMessage.originalServerTimestamp)
                }

                val message = Message(
                    db,
                    jsonMessage.senderSequenceNumber,
                    jsonMessage,
                    jsonReturnReceipt,
                    messageServerTimestamp,
                    messageStatus,
                    messageType,
                    discussion.id,
                    obvMessage.identifier,
                    messageSender.senderIdentity,
                    jsonMessage.senderThreadIdentifier,
                    attachmentCount,
                    imageCount
                )
                message.missedMessageCount = processSequenceNumber(db, discussion.id, messageSender.senderIdentity, jsonMessage.senderThreadIdentifier, jsonMessage.senderSequenceNumber)
                message.forwarded = jsonMessage.isForwarded == true
                message.mentioned = message.isIdentityMentioned(messageSender.bytesOwnedIdentity) || message.isOwnMessageReply(messageSender.bytesOwnedIdentity)
                var edited = false

                jsonMessage.jsonPoll?.let poll@{ jsonPoll ->
                    // first, tweak the expiration time to compensate for any local clock skew
                    if (jsonPoll.expiration != null) {
                        jsonPoll.expiration += obvMessage.localDownloadTimestamp - obvMessage.downloadTimestamp
                    }

                    try {
                        return@poll AppSingleton.getJsonObjectMapper().writeValueAsString(jsonPoll)
                    } catch (_: Exception) {
                        return@poll null
                    }
                }?.let { serializedJsonPoll ->
                    message.jsonPoll = serializedJsonPoll
                }

                remoteDeleteAndEditRequest?.takeIf {
                    it.requestType == RemoteDeleteAndEditRequest.TYPE_EDIT
                }?.let { remoteDeleteAndEditRequest ->
                    val newBody = remoteDeleteAndEditRequest.body?.trim()
                    val mentions = remoteDeleteAndEditRequest.sanitizedSerializedMentions
                    if (message.contentBody != newBody) {
                        edited = true
                        message.contentBody = newBody
                        message.jsonMentions = mentions
                        message.mentioned = message.isIdentityMentioned(messageSender.bytesOwnedIdentity) || message.isOwnMessageReply(messageSender.bytesOwnedIdentity)
                        message.edited = Message.EDITED_UNSEEN
                        message.forwarded = false
                    }
                }

                // TODO: why stop? We should be able to share/receive share from 2 devices in the same discussion
                // if start sharing location message check this contact is not already sharing their location in this discussion
                // if already sharing force end of previous sharing message
                if (message.locationType == Message.LOCATION_TYPE_SHARE) {
                    if (message.senderIdentifier.contentEquals(messageSender.bytesOwnedIdentity)) {
                        UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(message.discussionId, true)
                    } else {
                        db.messageDao()
                            .getCurrentLocationSharingMessagesOfIdentityInDiscussion(message.senderIdentifier, message.discussionId)
                            .takeIf { it.isNotEmpty() }
                            ?.let { locationSharingMessages ->
                                for (m in locationSharingMessages) {
                                    Logger.e("This identity was already sharing it location, marking sharing as finished for message: " + m.id)
                                    db.messageDao().updateLocationType(m.id, Message.LOCATION_TYPE_SHARE_FINISHED)
                                    removeLocationSharingMessage(m.discussionId, m.id)
                                }
                            }
                    }
                }

                message.id = db.messageDao().insert(message)

                if (discussion.updateLastMessageTimestamp(obvMessage.serverTimestamp)) {
                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)
                }

                db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_UPLOADED, obvMessage.serverTimestamp))
                db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_DELIVERED, System.currentTimeMillis()))
                if (edited) {
                    db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_EDITED, remoteDeleteAndEditRequest?.serverTimestamp ?: obvMessage.serverTimestamp))
                }

                jsonExpiration?.existenceDuration?.let { existenceDuration ->
                    val expirationTimestamp = obvMessage.localDownloadTimestamp + existenceDuration * 1000L- (obvMessage.downloadTimestamp - obvMessage.serverTimestamp).coerceAtLeast(0)
                    val messageExpiration = MessageExpiration(0, message.id, expirationTimestamp, false)
                    db.messageExpirationDao().insert(messageExpiration)
                    sendExpireIntent = true
                }

                remoteDeleteAndEditRequest?.let { db.remoteDeleteAndEditRequestDao().delete(it) }


                if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE && jsonReturnReceipt != null) {
                    val messageRecipientInfoMap = mutableMapOf<BytesKey, MessageRecipientInfo>()

                    when (discussion.discussionType) {
                        Discussion.TYPE_CONTACT -> {
                            val contact = db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier)
                            if (contact != null && contact.active) {
                                messageRecipientInfoMap[BytesKey(contact.bytesContactIdentity)] =
                                    MessageRecipientInfo(message.id, message.totalAttachmentCount, contact.bytesContactIdentity)
                            }
                        }

                        Discussion.TYPE_GROUP -> {
                            val contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier)
                            for (contact in contacts) {
                                if (contact.active) {
                                    messageRecipientInfoMap[BytesKey(contact.bytesContactIdentity)] =
                                        MessageRecipientInfo(message.id, message.totalAttachmentCount, contact.bytesContactIdentity)
                                }
                            }
                        }

                        Discussion.TYPE_GROUP_V2 -> {
                            val contacts = db.group2MemberDao().getGroupMemberContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier)
                            for (contact in contacts) {
                                if (contact.active) {
                                    messageRecipientInfoMap[BytesKey(contact.bytesContactIdentity)] =
                                        MessageRecipientInfo(message.id, message.totalAttachmentCount, contact.bytesContactIdentity)
                                }
                            }
                            for (group2PendingMember in db.group2PendingMemberDao().getGroupPendingMembers(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier)) {
                                messageRecipientInfoMap[BytesKey(group2PendingMember.bytesContactIdentity)] =
                                    MessageRecipientInfo(message.id, message.totalAttachmentCount, group2PendingMember.bytesContactIdentity)
                            }
                        }
                    }
                    if (messageRecipientInfoMap.isNotEmpty()) {
                        // set all required messageRecipientInfo attributes to make sure the message is not resent!
                        for (messageRecipientInfo in messageRecipientInfoMap.values) {
                            messageRecipientInfo.timestampSent = 0L
                            messageRecipientInfo.engineMessageIdentifier = ByteArray(0)
                            messageRecipientInfo.returnReceiptNonce = jsonReturnReceipt.getNonce()
                            messageRecipientInfo.returnReceiptKey = jsonReturnReceipt.getKey()
                        }

                        db.messageRecipientInfoDao().insert(* messageRecipientInfoMap.values.toTypedArray())
                    }
                }

                // mark as ready to process any message that was on hold, waiting for this new message
                val count = db.onHoldInboxMessageDao().markAsReadyToProcessForMessage(
                    discussion.bytesOwnedIdentity,
                    message.senderIdentifier,
                    message.senderThreadIdentifier,
                    message.senderSequenceNumber
                )
                if (count > 0) {
                    Logger.i("⏸️ Marked $count on hold messages as ready to process (message was received)")
                }

                Pair(message.id, sendExpireIntent)
            })

            if (transactionResult.second) {
                App.runThread { MessageExpirationService.scheduleNextExpiration() }
            }

            val message = db.messageDao().get(transactionResult.first)

            if (message == null) {
                Logger.w("Failed to insert new message in db.")
                return HandleMessageOutput.DO_NOTHING
            }

            // if the message was properly inserted, trigger the processing of any on hold message we might have marked as ready to process
            engine.runTaskOnEngineNotificationQueue(ProcessReadyToProcessOnHoldMessagesTask(engine, db, discussion.bytesOwnedIdentity))

            if (message.status == Message.STATUS_UNREAD) {
                newUnreadMessage(message.discussionId, message.id, message.mentioned, message.timestamp)
            }
            if (message.isLocationMessage && message.locationType == Message.LOCATION_TYPE_SHARE) {
                newLocationSharingMessage(message.discussionId, message.id)
            }

            if (jsonReturnReceipt != null) {
                if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    HandleStalledReturnReceipts(engine, messageSender.bytesOwnedIdentity, jsonReturnReceipt.nonce, jsonReturnReceipt.key).run()
                }
                // send DELIVERED return receipt
                message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_DELIVERED)
            }

            val fyleMessageJoinWithStatusesToDownload: MutableList<FyleMessageJoinWithStatus> = ArrayList()

            for (i in 0..<obvMessage.attachments.size) {
                val attachment = obvMessage.attachments[i] ?: continue
                val attachmentMetadata = attachmentMetadatas[i]

                try {
                    if (attachmentMetadata?.fileName == null) {
                        throw Exception()
                    }

                    val sha256 = attachmentMetadata.sha256
                    try {
                        Fyle.acquireLock(sha256)
                        // an null imageResolution means it should be computed, empty string means there is nothing to compute
                        val imageResolution = if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(attachmentMetadata.type, attachmentMetadata.fileName))) null else ""
                        var fyleId: Long
                        val fyle = db.fyleDao().getBySha256(sha256)
                        if (fyle != null) {
                            fyleId = fyle.id
                            // we know this attachment
                            if (fyle.isComplete) {
                                // the fyle is already complete, simply link it and cancel the download
                                val fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                                    fyle.id,
                                    message.id,
                                    messageSender.bytesOwnedIdentity,
                                    fyle.filePath!!,
                                    attachmentMetadata.fileName,
                                    attachmentMetadata.type,
                                    FyleMessageJoinWithStatus.STATUS_COMPLETE,
                                    attachment.expectedLength,
                                    attachment.messageIdentifier,
                                    attachment.number,
                                    imageResolution
                                )
                                if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
                                    fyleMessageJoinWithStatus.wasOpened = true
                                }
                                db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus)
                                fyleMessageJoinWithStatus.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, message)
                                fyleMessageJoinWithStatus.computeTextContentForFullTextSearchOnOtherThread(db, fyle)
                                engine.markAttachmentForDeletion(attachment)
                            } else {
                                // the fyle is incomplete, so no need to create the Fyle, but still create a STATUS_DOWNLOADABLE FyleMessageJoinWithStatus
                                val fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                                    fyle.id,
                                    message.id,
                                    messageSender.bytesOwnedIdentity,
                                    attachment.url,
                                    attachmentMetadata.fileName,
                                    attachmentMetadata.type,
                                    if (attachment.isDownloadRequested) FyleMessageJoinWithStatus.STATUS_DOWNLOADING else FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE,
                                    attachment.expectedLength,
                                    attachment.messageIdentifier,
                                    attachment.number,
                                    imageResolution
                                )
                                if (attachment.receivedLength > 0) {
                                    updateProgress(fyle.id, message.id, attachment.receivedLength.toFloat() / attachment.expectedLength, null)
                                }

                                if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
                                    fyleMessageJoinWithStatus.wasOpened = true
                                }
                                db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus)
                                fyleMessageJoinWithStatusesToDownload.add(fyleMessageJoinWithStatus)
                            }
                        } else {
                            // the file is unknown, create it and a STATUS_DOWNLOADABLE FyleMessageJoinWithStatus
                            // this is the "normal" case
                            val newFyle = Fyle(attachmentMetadata.sha256)
                            newFyle.id = db.fyleDao().insert(newFyle)
                            fyleId = newFyle.id
                            val fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                                newFyle.id,
                                message.id,
                                messageSender.bytesOwnedIdentity,
                                attachment.url,
                                attachmentMetadata.fileName,
                                attachmentMetadata.type,
                                if (attachment.isDownloadRequested) FyleMessageJoinWithStatus.STATUS_DOWNLOADING else FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE,
                                attachment.expectedLength,
                                attachment.messageIdentifier,
                                attachment.number,
                                imageResolution
                            )
                            if (attachment.receivedLength > 0) {
                                updateProgress(newFyle.id, message.id, attachment.receivedLength.toFloat() / attachment.expectedLength, null)
                            }

                            if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
                                fyleMessageJoinWithStatus.wasOpened = true
                            }
                            db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus)
                            fyleMessageJoinWithStatusesToDownload.add(fyleMessageJoinWithStatus)
                        }
                        if (message.linkPreviewFyleId == null && attachmentMetadata.type == OpenGraph.MIME_TYPE) {
                            message.linkPreviewFyleId = fyleId
                            db.messageDao().updateLinkPreviewFyleId(message.id, fyleId)
                        }
                    } finally {
                        Fyle.releaseLock(sha256)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Logger.d("Error reading an attachment or creating the fyle (or message already expired and deleted)")
                    engine.markAttachmentForDeletion(attachment)
                }
            }

            if (messageSender.type == MessageSender.Type.CONTACT) {
                val ownedIdentity = db.ownedIdentityDao().get(messageSender.bytesOwnedIdentity)
                AndroidNotificationManager.displayReceivedMessageNotification(discussion, message, messageSender.contact, ownedIdentity)
            }

            if (fyleMessageJoinWithStatusesToDownload.isNotEmpty()) {
                AvailableSpaceHelper.refreshAvailableSpace(false)
            }

            // auto-download attachments if needed
            val downloadSize = SettingsActivity.autoDownloadSize

            for (fyleMessageJoinWithStatus in fyleMessageJoinWithStatusesToDownload) {
                if (message.linkPreviewFyleId == fyleMessageJoinWithStatus.fyleId // always download link previews
                    || ((downloadSize == -1L || fyleMessageJoinWithStatus.size < downloadSize)
                            && (AvailableSpaceHelper.getAvailableSpace() == null || AvailableSpaceHelper.getAvailableSpace()!! > fyleMessageJoinWithStatus.size)
                            && (!discussion.archived || SettingsActivity.autoDownloadArchivedDiscussion))
                ) {
                    fyleMessageJoinWithStatus.engineNumber?.let { engineNumber ->
                        engine.downloadSmallAttachment(obvMessage.bytesToIdentity, fyleMessageJoinWithStatus.engineMessageIdentifier, engineNumber)
                        fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_DOWNLOADING
                        getInstance().fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus)
                    }
                }
            }
        }

        return HandleMessageOutput.MARK_MESSAGE_FOR_DELETION
    }

    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}

// this must be run inside the transaction where the message is created.
// It updates the latest discussion sequence number and updates the missing message count in the following sequenceNumber message (if this is not the latest)
// It returns the number of missing messages before this one
private fun processSequenceNumber(
    db: AppDatabase,
    discussionId: Long,
    senderIdentifier: ByteArray,
    senderThreadIdentifier: UUID,
    senderSequenceNumber: Long): Long {
    val latestDiscussionSenderSequenceNumber = db.latestDiscussionSenderSequenceNumberDao().get(discussionId, senderIdentifier, senderThreadIdentifier)

    return if (latestDiscussionSenderSequenceNumber != null && senderSequenceNumber < latestDiscussionSenderSequenceNumber.latestSequenceNumber) {
        db.messageDao()
            .getFollowingBySenderSequenceNumber(senderSequenceNumber, senderThreadIdentifier, senderIdentifier, discussionId)
            ?.let { message ->
                if (message.missedMessageCount < (message.senderSequenceNumber - senderSequenceNumber)) {
                    // the message is older than the number of messages missed in the following message --> nothing to do
                    0
                } else {
                    val remainingMissedCount = message.missedMessageCount - (message.senderSequenceNumber - senderSequenceNumber)

                    message.missedMessageCount = message.senderSequenceNumber - 1 - senderSequenceNumber
                    db.messageDao().updateMissedMessageCount(message.id, message.missedMessageCount)

                    remainingMissedCount
                }
            } ?: 0
    } else if (latestDiscussionSenderSequenceNumber != null && senderSequenceNumber > latestDiscussionSenderSequenceNumber.latestSequenceNumber) {
        db.latestDiscussionSenderSequenceNumberDao().updateLatestSequenceNumber(discussionId, senderIdentifier, senderThreadIdentifier, senderSequenceNumber)

        senderSequenceNumber - 1 - latestDiscussionSenderSequenceNumber.latestSequenceNumber
    } else if (latestDiscussionSenderSequenceNumber == null) {
        db.latestDiscussionSenderSequenceNumberDao().insert(
            LatestDiscussionSenderSequenceNumber(discussionId, senderIdentifier, senderThreadIdentifier, senderSequenceNumber))

        0
    } else { // senderSequenceNumber == latestSequenceNumber (this should normally not happen...)
        0
    }
}
