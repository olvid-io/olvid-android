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
import io.olvid.messenger.databases.entity.jsons.JsonLimitedVisibilityMessageOpened
import io.olvid.messenger.databases.tasks.InboundEphemeralMessageClicked


fun handleLimitedVisibilityMessageOpened(
    db: AppDatabase,
    jsonLimitedVisibilityMessageOpened: JsonLimitedVisibilityMessageOpened,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    jsonLimitedVisibilityMessageOpened.takeIf {
        messageSender.type == MessageSender.Type.OWNED_IDENTITY
    }?.messageReference?.let { messageReference ->
        if (putMessageOnHoldIfDiscussionIsMissing(
                db,
                obvMessage.identifier,
                obvMessage.serverTimestamp,
                messageSender,
                jsonLimitedVisibilityMessageOpened.oneToOneIdentifier,
                jsonLimitedVisibilityMessageOpened.groupOwner,
                jsonLimitedVisibilityMessageOpened.groupUid,
                jsonLimitedVisibilityMessageOpened.groupV2Identifier
            )
        ) {
            return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
        }

        getDiscussion(
            db,
            jsonLimitedVisibilityMessageOpened.groupUid,
            jsonLimitedVisibilityMessageOpened.groupOwner,
            jsonLimitedVisibilityMessageOpened.groupV2Identifier,
            jsonLimitedVisibilityMessageOpened.oneToOneIdentifier,
            messageSender,
            null
        )?.let { discussion ->

            db.messageDao().getBySenderSequenceNumber(
                messageReference.senderSequenceNumber,
                messageReference.senderThreadIdentifier,
                messageReference.senderIdentifier,
                discussion.id
            )?.also { message ->
                InboundEphemeralMessageClicked(
                    messageSender.bytesOwnedIdentity,
                    message,
                    (obvMessage.downloadTimestamp - obvMessage.serverTimestamp).coerceAtLeast(0) // time elapsed on the server
                            + (System.currentTimeMillis() - obvMessage.localDownloadTimestamp).coerceAtLeast(0) // time elapsed in the app
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