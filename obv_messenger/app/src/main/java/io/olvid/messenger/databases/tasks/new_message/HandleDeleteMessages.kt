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

import io.olvid.engine.datatypes.containers.GroupV2
import io.olvid.engine.engine.types.ObvMessage
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest
import io.olvid.messenger.databases.entity.jsons.JsonDeleteMessages
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity.Companion.retainRemoteDeletedMessages


fun handleDeleteMessages(
    db: AppDatabase,
    jsonDeleteMessages: JsonDeleteMessages,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    if (putMessageOnHoldIfDiscussionIsMissing(
            db,
            obvMessage.identifier,
            obvMessage.serverTimestamp,
            messageSender,
            jsonDeleteMessages.oneToOneIdentifier,
            jsonDeleteMessages.groupOwner,
            jsonDeleteMessages.groupUid,
            jsonDeleteMessages.groupV2Identifier
        )
    ) {
        return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
    }

    jsonDeleteMessages.messageReferences.takeIf { it.isNullOrEmpty().not() }
        ?.let { messageReferences ->

            getDiscussion(db, jsonDeleteMessages.groupUid, jsonDeleteMessages.groupOwner, jsonDeleteMessages.groupV2Identifier, jsonDeleteMessages.oneToOneIdentifier, messageSender, null)
                ?.let { discussion ->

                    val remoteDeletePermission = getDiscussion(db, jsonDeleteMessages.groupUid, jsonDeleteMessages.groupOwner, jsonDeleteMessages.groupV2Identifier, jsonDeleteMessages.oneToOneIdentifier, messageSender, GroupV2.Permission.REMOTE_DELETE_ANYTHING) != null
                    val editAndDeleteOwnMessagesPermission = getDiscussion(db, jsonDeleteMessages.groupUid, jsonDeleteMessages.groupOwner, jsonDeleteMessages.groupV2Identifier, jsonDeleteMessages.oneToOneIdentifier, messageSender, GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES) != null

                    for (messageReference in messageReferences) {
                        db.messageDao().getBySenderSequenceNumber(messageReference.senderSequenceNumber, messageReference.senderThreadIdentifier, messageReference.senderIdentifier, discussion.id)
                            ?.also { message ->
                                // only delete if the user has the permission to delete other users' messages
                                if (messageSender.type == MessageSender.Type.OWNED_IDENTITY
                                    || remoteDeletePermission
                                    || (editAndDeleteOwnMessagesPermission && message.senderIdentifier.contentEquals(messageSender.senderIdentity))
                                ) {
                                    // stop sharing location if needed
                                    if (message.isCurrentSharingOutboundLocationMessage) {
                                        UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(discussion.id, true)
                                    }

                                    if (messageSender.type == MessageSender.Type.OWNED_IDENTITY
                                        || message.senderIdentifier.contentEquals(messageSender.senderIdentity)
                                        || !retainRemoteDeletedMessages
                                    ) {
                                        db.runInTransaction { message.delete(db) }
                                    } else {
                                        db.runInTransaction {
                                            message.remoteDelete(db, messageSender.senderIdentity, obvMessage.serverTimestamp)
                                            message.deleteAttachments(db)
                                        }
                                    }
                                    AndroidNotificationManager.remoteDeleteMessageNotification(discussion, message.id)
                                }
                            } ?: run {
                            db.remoteDeleteAndEditRequestDao().getBySenderSequenceNumber(messageReference.senderSequenceNumber, messageReference.senderThreadIdentifier, messageReference.senderIdentifier, discussion.id)
                                ?.let { remoteDeleteAndEditRequest ->
                                    // an edit/delete request already exists
                                    if (remoteDeleteAndEditRequest.requestType != RemoteDeleteAndEditRequest.TYPE_DELETE
                                        || messageSender.type == MessageSender.Type.OWNED_IDENTITY
                                        || remoteDeletePermission
                                        || (editAndDeleteOwnMessagesPermission && messageReference.getSenderIdentifier().contentEquals(messageSender.senderIdentity))
                                    ) {
                                        // delete the edit, replace it with our new delete
                                        db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest)
                                    } else {
                                        // we already have a delete and the new one does not have the proper permission --> do nothing
                                        return@run
                                    }
                                }

                            val remoteDeleteAndEditRequest = RemoteDeleteAndEditRequest(discussion.id, messageReference.senderIdentifier, messageReference.senderThreadIdentifier, messageReference.senderSequenceNumber, obvMessage.serverTimestamp, RemoteDeleteAndEditRequest.TYPE_DELETE, null, null, messageSender.senderIdentity)
                            db.remoteDeleteAndEditRequestDao().insert(remoteDeleteAndEditRequest)
                        }
                    }
                }
        }
    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}