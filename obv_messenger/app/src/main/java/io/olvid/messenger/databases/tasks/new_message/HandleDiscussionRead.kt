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
import io.olvid.messenger.UnreadCountsSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.jsons.JsonDiscussionRead
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.settings.SettingsActivity


fun handleDiscussionRead(
    db: AppDatabase,
    jsonDiscussionRead: JsonDiscussionRead,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    // ignore any message that is not coming from another owned device
    if (messageSender.type != MessageSender.Type.OWNED_IDENTITY) {
        return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
    }

    if (putMessageOnHoldIfDiscussionIsMissing(
            db,
            obvMessage.identifier,
            obvMessage.serverTimestamp,
            messageSender,
            jsonDiscussionRead.oneToOneIdentifier,
            jsonDiscussionRead.groupOwner,
            jsonDiscussionRead.groupUid,
            jsonDiscussionRead.groupV2Identifier
        )
    ) {
        return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
    }

    getDiscussion(
        db,
        jsonDiscussionRead.groupUid,
        jsonDiscussionRead.groupOwner,
        jsonDiscussionRead.groupV2Identifier,
        jsonDiscussionRead.oneToOneIdentifier,
        messageSender,
        null
    )?.let { discussion ->
        db.messageDao().markDiscussionMessagesReadUpTo(discussion.id, jsonDiscussionRead.lastReadMessageServerTimestamp)
        UnreadCountsSingleton.markDiscussionRead(discussion.id, jsonDiscussionRead.lastReadMessageServerTimestamp)

        if (db.messageDao().getServerTimestampOfLatestUnreadInboundMessageInDiscussion(discussion.id) == null) {
            AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussion.id)
            AndroidNotificationManager.clearMissedCallNotification(discussion.id)

            if (SettingsActivity.isNotificationContentHidden) {
                // if displaying only neutral notifications, check if there is a point in still showing one
                if (!db.messageDao().unreadMessagesOrInvitationsExist()) {
                    AndroidNotificationManager.clearNeutralNotification()
                }
            }
        }
    }

    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}