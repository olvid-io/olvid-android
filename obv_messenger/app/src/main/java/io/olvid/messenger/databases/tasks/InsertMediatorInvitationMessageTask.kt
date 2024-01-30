/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message

class InsertMediatorInvitationMessageTask(
    private val bytesOwnedIdentity: ByteArray,
    private val bytesContactIdentity: ByteArray,
    private val type: Int,
    private val displayName: String,
) : Runnable {
    override fun run() {
        when (type) {
            Message.TYPE_MEDIATOR_INVITATION_SENT,
            Message.TYPE_MEDIATOR_INVITATION_ACCEPTED,
            Message.TYPE_MEDIATOR_INVITATION_IGNORED -> {
                val db = AppDatabase.getInstance()
                try {
                    val discussion = db.discussionDao().getByContact(
                        bytesOwnedIdentity, bytesContactIdentity
                    )
                    discussion?.let {
                        val message = Message.createMediatorInvitationMessage(
                            db,
                            type,
                            discussion.id,
                            bytesContactIdentity,
                            displayName,
                            System.currentTimeMillis()
                        )
                        message.id = db.messageDao().insert(message)

                        // we do not update the discussion timestamp for type TYPE_MEDIATOR_INVITATION_SENT,
                        // for other messages, we update only if timestamp was 0 (0 corresponds to discussions hidden from the main list)
                        if (type != Message.TYPE_MEDIATOR_INVITATION_SENT
                            && discussion.lastMessageTimestamp != 0L
                            && discussion.updateLastMessageTimestamp(message.timestamp)
                        ) {
                            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("Unable to insert mediator invitation message.")
                    e.printStackTrace()
                }
            }
        }
    }
}