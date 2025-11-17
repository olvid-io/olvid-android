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

import io.olvid.engine.engine.types.ObvMessage
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonReaction
import io.olvid.messenger.databases.tasks.UpdateReactionsTask
import io.olvid.messenger.notifications.AndroidNotificationManager


fun handleReaction(
    db: AppDatabase,
    jsonReaction: JsonReaction,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    jsonReaction.messageReference?.let { messageReference ->
        if (putMessageOnHoldIfDiscussionIsMissing(
                db,
                obvMessage.identifier,
                obvMessage.serverTimestamp,
                messageSender,
                jsonReaction.oneToOneIdentifier,
                jsonReaction.groupOwner,
                jsonReaction.groupUid,
                jsonReaction.groupV2Identifier
            )
        ) {
            return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
        }

        getDiscussion(
            db,
            jsonReaction.groupUid,
            jsonReaction.groupOwner,
            jsonReaction.groupV2Identifier,
            jsonReaction.oneToOneIdentifier,
            messageSender,
            null
        )?.let { discussion ->

            db.messageDao().getBySenderSequenceNumber(
                messageReference.senderSequenceNumber,
                messageReference.senderThreadIdentifier,
                messageReference.senderIdentifier,
                discussion.id
            )?.also { message ->
                if (message.wipeStatus == Message.WIPE_STATUS_WIPED
                    || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
                ) {
                    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                }

                // only show a notification if a contact reacted to an outbound message or a message I am mentioned in
                if (messageSender.type == MessageSender.Type.CONTACT && message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    val ownedIdentity = db.ownedIdentityDao().get(messageSender.bytesOwnedIdentity)
                    AndroidNotificationManager.displayReactionNotification(
                        ownedIdentity,
                        discussion,
                        message,
                        jsonReaction.reaction,
                        messageSender.contact!! // the messageSender type is CONTACT, so contact is non-null
                    )
                }

                var messageServerTimestamp = obvMessage.serverTimestamp
                if (jsonReaction.originalServerTimestamp != null && discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                    messageServerTimestamp = messageServerTimestamp.coerceAtMost(jsonReaction.originalServerTimestamp)
                }


                UpdateReactionsTask(
                    message.id,
                    jsonReaction.reaction,
                    messageSender.senderIdentity.takeIf { messageSender.type == MessageSender.Type.CONTACT },
                    messageServerTimestamp,
                    false
                ).run()
            } ?: run {
                putMessageOnHoldBecauseOfMissingMessage(
                    db,
                    obvMessage.identifier,
                    obvMessage.serverTimestamp,
                    discussion,
                    messageReference
                )
                return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_MESSAGE
            }
        }
    }

    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}