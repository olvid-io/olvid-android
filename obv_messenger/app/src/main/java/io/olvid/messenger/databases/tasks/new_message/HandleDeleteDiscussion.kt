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
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonDeleteDiscussion
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.services.UnifiedForegroundService


fun handleDeleteDiscussion(
    db: AppDatabase,
    jsonDeleteDiscussion: JsonDeleteDiscussion,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    if (putMessageOnHoldIfDiscussionIsMissing(
            db,
            obvMessage.identifier,
            obvMessage.serverTimestamp,
            messageSender,
            jsonDeleteDiscussion.oneToOneIdentifier,
            jsonDeleteDiscussion.groupOwner,
            jsonDeleteDiscussion.groupUid,
            jsonDeleteDiscussion.groupV2Identifier
        )
    ) {
        return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
    }

    getDiscussion(db, jsonDeleteDiscussion.groupUid, jsonDeleteDiscussion.groupOwner, jsonDeleteDiscussion.groupV2Identifier, jsonDeleteDiscussion.oneToOneIdentifier, messageSender, GroupV2.Permission.REMOTE_DELETE_ANYTHING)
        ?.let { discussion ->
            if (discussion.lastRemoteDeleteTimestamp < obvMessage.serverTimestamp) {
                db.discussionDao().updateLastRemoteDeleteTimestamp(discussion.id, obvMessage.serverTimestamp)
            }

            val messagesToDelete = db.messageDao().countMessagesInDiscussion(discussion.id)

            DeleteMessagesTask(discussion.id, SecureDeleteEverywhereDialogBuilder.DeletionChoice.LOCAL, true).run()

            // stop sharing location if needed
            if (UnifiedForegroundService.LocationSharingSubService.isDiscussionSharingLocation(discussion.id)) {
                UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(discussion.id, true)
            }

            // clear notifications if needed
            AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussion.id)

            // reload the discussion
            if (messagesToDelete > 0) {
                db.discussionDao().getById(discussion.id)
                    ?.let { discuss ->
                        Message.createDiscussionRemotelyDeletedMessage(db, discuss.id, messageSender.senderIdentity, obvMessage.serverTimestamp)
                            ?.let { message ->
                                db.messageDao().insert(message)
                                if (discuss.updateLastMessageTimestamp(obvMessage.serverTimestamp)) {
                                    db.discussionDao().updateLastMessageTimestamp(discuss.id, discuss.lastMessageTimestamp)
                                }
                            }
                    }
            }
        }

    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}