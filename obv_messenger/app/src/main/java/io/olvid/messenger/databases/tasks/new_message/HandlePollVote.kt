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
import io.olvid.messenger.databases.entity.PollVote
import io.olvid.messenger.databases.entity.jsons.JsonPollVote
import io.olvid.messenger.notifications.AndroidNotificationManager


fun handlePollVote(
    db: AppDatabase,
    jsonPollVote: JsonPollVote,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    jsonPollVote.takeIf {
        it.pollCandidateUuid != null
                && it.voted != null
                && it.version != null
    }?.messageReference?.let { messageReference ->
        if (putMessageOnHoldIfDiscussionIsMissing(
                db,
                obvMessage.identifier,
                obvMessage.serverTimestamp,
                messageSender,
                jsonPollVote.oneToOneIdentifier,
                jsonPollVote.groupOwner,
                jsonPollVote.groupUid,
                jsonPollVote.groupV2Identifier
            )
        ) {
            return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
        }

        getDiscussion(
            db,
            jsonPollVote.groupUid,
            jsonPollVote.groupOwner,
            jsonPollVote.groupV2Identifier,
            jsonPollVote.oneToOneIdentifier,
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

                val poll = message.poll
                    ?: return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS

                if (poll.multipleChoice) {
                    var pollVote = db.pollVoteDao().get(message.id, messageSender.senderIdentity, jsonPollVote.pollCandidateUuid)
                    if (pollVote != null) {
                        if (pollVote.version > jsonPollVote.version) {
                            return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                        }
                        pollVote.serverTimestamp = obvMessage.serverTimestamp
                        pollVote.version = jsonPollVote.version
                        pollVote.voted = jsonPollVote.voted
                    } else {
                        pollVote = PollVote(
                            message.id,
                            obvMessage.serverTimestamp,
                            jsonPollVote.version,
                            jsonPollVote.pollCandidateUuid,
                            messageSender.senderIdentity,
                            jsonPollVote.voted
                        )
                    }

                    db.pollVoteDao().upsert(pollVote)
                } else {
                    val pollVotes = db.pollVoteDao().getAllByVoter(message.id, messageSender.senderIdentity)
                    for (pollVote in pollVotes) {
                        if (pollVote.version <= jsonPollVote.version) {
                            db.pollVoteDao().delete(pollVote)
                        } else {
                            return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                        }
                    }

                    // if we reach this point, all previous poll votes for this sender had a smaller version and were deleted
                    val pollVote = PollVote(
                        message.id,
                        obvMessage.serverTimestamp,
                        jsonPollVote.version,
                        jsonPollVote.pollCandidateUuid,
                        messageSender.senderIdentity,
                        jsonPollVote.voted
                    )

                    db.pollVoteDao().insert(pollVote)
                }


                // only show a notification if a contact voted to an owned poll
                if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE && messageSender.type == MessageSender.Type.CONTACT) {
                    val ownedIdentity = db.ownedIdentityDao().get(messageSender.bytesOwnedIdentity)
                    AndroidNotificationManager.displayPollVoteNotification(ownedIdentity, discussion, message, jsonPollVote.pollCandidateUuid)
                }
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