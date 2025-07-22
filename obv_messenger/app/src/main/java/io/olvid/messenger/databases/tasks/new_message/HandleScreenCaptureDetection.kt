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
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonScreenCaptureDetection


fun handleScreenCaptureDetection(
    db: AppDatabase,
    jsonScreenCaptureDetection: JsonScreenCaptureDetection,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    if (putMessageOnHoldIfDiscussionIsMissing(
            db,
            obvMessage.identifier,
            obvMessage.serverTimestamp,
            messageSender,
            jsonScreenCaptureDetection.oneToOneIdentifier,
            jsonScreenCaptureDetection.groupOwner,
            jsonScreenCaptureDetection.groupUid,
            jsonScreenCaptureDetection.groupV2Identifier
        )
    ) {
        return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
    }

    getDiscussion(
        db,
        jsonScreenCaptureDetection.groupUid,
        jsonScreenCaptureDetection.groupOwner,
        jsonScreenCaptureDetection.groupV2Identifier,
        jsonScreenCaptureDetection.oneToOneIdentifier,
        messageSender,
        null
    )?.let { discussion ->
        val message = Message.createScreenShotDetectedMessage(db, discussion.id, messageSender.senderIdentity, obvMessage.serverTimestamp)
        db.messageDao().insert(message)
        if (discussion.updateLastMessageTimestamp(message.timestamp)) {
            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)
        }

    }

    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}