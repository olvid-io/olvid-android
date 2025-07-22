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
import io.olvid.engine.engine.types.ObvMessage
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.UnreadCountsSingleton.removeLocationSharingMessage
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageMetadata
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.entity.jsons.JsonUpdateMessage
import io.olvid.messenger.databases.entity.jsons.JsonUserMention
import io.olvid.messenger.notifications.AndroidNotificationManager


fun handleUpdateMessage(
    db: AppDatabase,
    jsonUpdateMessage: JsonUpdateMessage,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    jsonUpdateMessage.messageReference.takeIf {
        // only the original author can edit a message
        it.senderIdentifier.contentEquals(messageSender.senderIdentity)
    }?.let { messageReference ->
        if (putMessageOnHoldIfDiscussionIsMissing(
                db,
                obvMessage.identifier,
                obvMessage.serverTimestamp,
                messageSender,
                jsonUpdateMessage.oneToOneIdentifier,
                jsonUpdateMessage.groupOwner,
                jsonUpdateMessage.groupUid,
                jsonUpdateMessage.groupV2Identifier
            )
        ) {
            return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
        }


        getDiscussion(db, jsonUpdateMessage.groupUid, jsonUpdateMessage.groupOwner, jsonUpdateMessage.groupV2Identifier, jsonUpdateMessage.oneToOneIdentifier, messageSender, GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES)
            ?.let { discussion ->
                val newBody = jsonUpdateMessage.body?.trim()
                var newMentions: String? = null
                try {
                    jsonUpdateMessage.sanitizeJsonUserMentions()
                    newMentions = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonUpdateMessage.jsonUserMentions)
                } catch (ex: Exception) {
                    Logger.w("In handleUpdateMessage: unable to serialize updated mentions")
                    Logger.x(ex)
                }

                db.messageDao().getBySenderSequenceNumber(messageReference.senderSequenceNumber, messageReference.senderThreadIdentifier, messageReference.senderIdentifier, discussion.id)
                    ?.let { message ->

                        if (message.wipeStatus != Message.WIPE_STATUS_NONE) {
                            return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                        }

                        // normal updateMessage message
                        if (jsonUpdateMessage.jsonLocation == null) {
                            val mentions: MutableList<JsonUserMention?>? = message.mentions
                            val mentionSet = mentions?.toHashSet()
                            val newMentionSet: HashSet<JsonUserMention>? = jsonUpdateMessage.jsonUserMentions?.toHashSet()
                            val mentionsChanged = mentionSet != newMentionSet
                            if (message.contentBody == newBody && !mentionsChanged) {
                                // no update needed --> do nothing
                                return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                            }

                            db.runInTransaction {
                                message.jsonMentions = newMentions
                                db.messageDao().updateMentions(message.id, newMentions)
                                db.messageDao().updateMentioned(message.id, message.isIdentityMentioned(discussion.bytesOwnedIdentity) || message.isOwnMessageReply(discussion.bytesOwnedIdentity))
                                message.contentBody = newBody
                                db.messageDao().updateBody(message.id, newBody)
                                if (message.forwarded) {
                                    db.messageDao().updateForwarded(message.id, false)
                                }
                                db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_EDITED, obvMessage.serverTimestamp))
                            }

                            // never edit the content of an hidden message notification!
                            if (message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE && messageSender.type == MessageSender.Type.CONTACT) {
                                val editMentionsMyself: Boolean
                                // check if I was mentioned in the update but not in the original message
                                if (mentionsChanged && newMentionSet != null) {
                                    if (mentionSet != null) {
                                        newMentionSet.removeAll(mentionSet)
                                    }
                                    editMentionsMyself = newMentionSet.any { it.userIdentifier.contentEquals(discussion.bytesOwnedIdentity) }
                                } else {
                                    editMentionsMyself = false
                                }
                                // we know the messageSender.type is CONTACT, so messageSender.contact is non-null
                                AndroidNotificationManager.editMessageNotification(discussion, message, messageSender.contact!!, newBody, editMentionsMyself)
                            }
                        } else { // update location message
                            // check message is a sharing location one
                            if (message.jsonLocation == null || (message.locationType != Message.LOCATION_TYPE_SHARE && message.locationType != Message.LOCATION_TYPE_SHARE_FINISHED)) {
                                Logger.w("HandleNewMessageTask: trying to update a message that is not location sharing")
                                return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                            }

                            val jsonLocation = jsonUpdateMessage.jsonLocation
                            if (jsonLocation.type == JsonLocation.TYPE_END_SHARING) {
                                // handle end of sharing messages
                                db.messageDao().updateLocationType(message.id, Message.LOCATION_TYPE_SHARE_FINISHED)
                                db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_LOCATION_SHARING_END, obvMessage.serverTimestamp))
                                removeLocationSharingMessage(message.discussionId, message.id)
                            } else if (jsonLocation.type == JsonLocation.TYPE_SHARING) {
                                // handle simple location update messages
                                val oldJsonLocation = message.getJsonLocation()
                                val count = oldJsonLocation?.count ?: -1

                                // check count is valid
                                jsonLocation.count?.takeIf { it > count }?.let {
                                    db.runInTransaction {
                                        try {
                                            // update json location data and compute new sharing expiration
                                            val jsonLocationString = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonLocation)
                                            db.messageDao().updateLocation(message.id, newBody, jsonLocationString)

                                            // create or update location metadata for message
                                            db.messageMetadataDao().getByKind(message.id, MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE)?.also { messageMetadata ->
                                                db.messageMetadataDao().updateTimestamp(messageMetadata.id, obvMessage.serverTimestamp)
                                            } ?: run {
                                                db.messageMetadataDao().insert(MessageMetadata(message.id, MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE, obvMessage.serverTimestamp))
                                            }
                                        } catch (e: Exception) {
                                            Logger.x(e)
                                        }
                                    }
                                }
                            }
                        }
                    } ?: run {
                    db.remoteDeleteAndEditRequestDao().getBySenderSequenceNumber(messageReference.senderSequenceNumber, messageReference.senderThreadIdentifier, messageReference.senderIdentifier, discussion.id)
                        ?.let { remoteDeleteAndEditRequest ->
                            // an edit/delete request already exists
                            if (remoteDeleteAndEditRequest.requestType == RemoteDeleteAndEditRequest.TYPE_DELETE
                                || remoteDeleteAndEditRequest.serverTimestamp > obvMessage.serverTimestamp) {
                                // we have a delete or a newer edit --> ignore it
                                return@run
                            } else {
                                // the new edit will replace this one
                                db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest)
                            }
                        }

                    db.remoteDeleteAndEditRequestDao()
                        .insert(RemoteDeleteAndEditRequest(
                            discussion.id,
                            messageReference.senderIdentifier,
                            messageReference.senderThreadIdentifier,
                            messageReference.senderSequenceNumber,
                            obvMessage.serverTimestamp,
                            RemoteDeleteAndEditRequest.TYPE_EDIT,
                            newBody,
                            newMentions,
                            null)
                        )
                }
            }
    }
    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}