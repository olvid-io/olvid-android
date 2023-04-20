/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason.REVOKED
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message

class InsertContactRevokedMessageTask(
    private val bytesOwnedIdentity: ByteArray,
    private val bytesContactIdentity: ByteArray
) : Runnable {
    override fun run() {
        val db = AppDatabase.getInstance()
        try {
            val discussion = db.discussionDao().getByContact(
                bytesOwnedIdentity, bytesContactIdentity
            )
           discussion?.let {
                val message = Message.createContactInactiveReasonMessage(
                    db,
                    discussion.id,
                    bytesContactIdentity,
                    REVOKED
                )
                message.id = db.messageDao().insert(message)
                if (discussion.updateLastMessageTimestamp(message.timestamp)) {
                    db.discussionDao()
                        .updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)
                }
            }
        } catch (e: Exception) {
            Logger.e("Unable to insert contact revoked message.")
            e.printStackTrace()
        }
    }
}