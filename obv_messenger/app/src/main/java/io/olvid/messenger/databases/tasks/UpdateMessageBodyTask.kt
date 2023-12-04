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

import android.widget.Toast
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageMetadata
import io.olvid.messenger.databases.entity.jsons.JsonUserMention

class UpdateMessageBodyTask(val messageId: Long, val body: String, val mentions: List<JsonUserMention>?) : Runnable {
    override fun run() {
        if (body.trim().isEmpty()) {
            return
        }
        val db = AppDatabase.getInstance()
        val message = db.messageDao().get(messageId) ?: return
        val newBody = body.trim()
        message.contentBody = newBody
        try {
            message.jsonMentions = mentions?.let {
                AppSingleton.getJsonObjectMapper().writeValueAsString(it.filter { jsonUserMention -> jsonUserMention.userIdentifier != null })
            }
        } catch (ex: Exception) {
            Logger.w("Unable to serialize mentions")
        }
        val success = Message.postUpdateMessageMessage(message).isMessagePostedForAtLeastOneContact
        if (!success) {
            App.toast(R.string.toast_message_unable_to_update_message, Toast.LENGTH_SHORT)
            return
        }
        db.messageDao().updateBody(message.id, body)
        db.messageDao().updateMentions(message.id, message.jsonMentions)
        db.messageMetadataDao().insert(
            MessageMetadata(
                message.id,
                MessageMetadata.KIND_EDITED,
                System.currentTimeMillis()
            )
        )
    }
}