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

package io.olvid.messenger.databases.tasks

import io.olvid.engine.Logger
import io.olvid.engine.engine.types.ObvOutboundAttachment
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Group2Member
import io.olvid.messenger.databases.entity.jsons.JsonMessageReference
import io.olvid.messenger.databases.entity.jsons.JsonPayload
import io.olvid.messenger.databases.entity.jsons.JsonPollVote
import io.olvid.messenger.databases.entity.jsons.JsonReaction


class ResendReactionsAndPollVotesForGroupV2MemberTask(
    val group2Member: Group2Member
) : Runnable {
    override fun run() {
        // make sure we have a pendingCreationTimestamp
        val pendingCreationTimestamp = group2Member.pendingCreationTimestamp ?: return

        val taskId = Triple(
            BytesKey(group2Member.bytesOwnedIdentity),
            BytesKey(group2Member.bytesContactIdentity),
            BytesKey(group2Member.bytesGroupIdentifier),
        )

        // first check if this task is not already running
        synchronized(currentlyRunningTasks) {
            if (currentlyRunningTasks.contains(taskId)) {
                return
            }
            currentlyRunningTasks.add(taskId)
        }

        try {
            val db = AppDatabase.getInstance()

            // check if the contact has some channels, otherwise we cannot resend them the reactions and poll votes
            val contact = db.contactDao().get(group2Member.bytesOwnedIdentity, group2Member.bytesContactIdentity)
            if (contact == null || contact.hasChannelOrPreKey().not()) {
                Logger.i("Started a ResendReactionsAndPollVotesForGroupV2MemberTask, but the contact has no channel (or was not found) --> aborting")
                return
            }

            db.reactionDao().getAllMineInGroupV2DiscussionWithinTimeInterval(
                group2Member.bytesOwnedIdentity,
                group2Member.bytesGroupIdentifier,
                pendingCreationTimestamp,
                group2Member.creationTimestamp,
            ).forEach { reactionAndMessage ->
                if (reactionAndMessage.reaction.emoji != null) {
                    val jsonReaction = JsonReaction()
                    jsonReaction.reaction = reactionAndMessage.reaction.emoji
                    jsonReaction.groupV2Identifier = group2Member.bytesGroupIdentifier
                    jsonReaction.messageReference = JsonMessageReference.of(reactionAndMessage.message)
                    jsonReaction.originalServerTimestamp = reactionAndMessage.reaction.timestamp
                    val jsonPayload = JsonPayload()
                    jsonPayload.jsonReaction = jsonReaction

                    val postMessageOutput = AppSingleton.getEngine().post(
                        AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                        null,
                        arrayOfNulls<ObvOutboundAttachment>(0),
                        listOf(group2Member.bytesContactIdentity),
                        group2Member.bytesOwnedIdentity,
                        true,
                        false
                    )

                    if (postMessageOutput.isMessagePostedForAtLeastOneContact.not()) {
                        // error posting one item --> abort the whole task
                        Logger.w("In ResendReactionsAndPollVotesForGroupV2MemberTask, error posting a reaction --> aborting")
                        return
                    }
                }
            }


            db.pollVoteDao().getAllMineInGroupV2DiscussionWithinTimeInterval(
                group2Member.bytesOwnedIdentity,
                group2Member.bytesGroupIdentifier,
                pendingCreationTimestamp,
                group2Member.creationTimestamp,
            ).forEach { pollVoteAndMessage ->
                if (pollVoteAndMessage.pollVote.voted) {
                    val jsonPollVote = JsonPollVote()
                    jsonPollVote.pollCandidateUuid = pollVoteAndMessage.pollVote.voteUuid
                    jsonPollVote.voted = true
                    jsonPollVote.version = pollVoteAndMessage.pollVote.version
                    jsonPollVote.groupV2Identifier = group2Member.bytesGroupIdentifier
                    jsonPollVote.messageReference = JsonMessageReference.of(pollVoteAndMessage.message)
                    jsonPollVote.originalServerTimestamp = pollVoteAndMessage.pollVote.serverTimestamp

                    val jsonPayload = JsonPayload()
                    jsonPayload.jsonPollVote = jsonPollVote

                    val postMessageOutput = AppSingleton.getEngine().post(
                        AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                        null,
                        arrayOfNulls<ObvOutboundAttachment>(0),
                        listOf(group2Member.bytesContactIdentity),
                        group2Member.bytesOwnedIdentity,
                        true,
                        false
                    )

                    if (postMessageOutput.isMessagePostedForAtLeastOneContact.not()) {
                        // error posting one item --> abort the whole task
                        Logger.w("In ResendReactionsAndPollVotesForGroupV2MemberTask, error posting a poll vote --> aborting")
                        return
                    }
                }
            }

            // if we reach this point, we were able to resend all reactions and poll votes --> we can remove the pendingCreationTimestamp from the group2Member
            db.group2MemberDao().clearPendingCreationTimestamp(group2Member.bytesOwnedIdentity, group2Member.bytesGroupIdentifier, group2Member.bytesContactIdentity)
        } finally {
            currentlyRunningTasks.remove(taskId)
        }
    }

    companion object {
        private val currentlyRunningTasks: MutableSet<Triple<BytesKey, BytesKey, BytesKey>> = mutableSetOf()
    }
}