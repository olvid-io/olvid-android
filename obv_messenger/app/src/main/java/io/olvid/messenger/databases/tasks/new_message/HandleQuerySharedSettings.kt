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

import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonQuerySharedSettings


@Suppress("SameReturnValue")
fun handleQuerySharedSettings(
    db: AppDatabase,
    jsonQuerySharedSettings: JsonQuerySharedSettings,
    messageSender: MessageSender
): HandleMessageOutput {
    // the query only makes sense for discussion you know about
    getDiscussion(
        db,
        jsonQuerySharedSettings.groupUid,
        jsonQuerySharedSettings.groupOwner,
        jsonQuerySharedSettings.groupV2Identifier,
        jsonQuerySharedSettings.oneToOneIdentifier,
        messageSender,
        null
    )?.id?.let { discussionId ->
        // the query only makes sense if the discussion has some customization with some shared settings
        db.discussionCustomizationDao()
            .get(discussionId)?.sharedSettingsJson?.let { sharedSettings ->

                jsonQuerySharedSettings.knownSharedSettingsVersion?.takeIf { it > sharedSettings.version }?.let {
                    // the user has a more recent version of the settings --> no need to send anything
                    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                }

                jsonQuerySharedSettings.knownSharedSettingsVersion?.takeIf {
                    it == sharedSettings.version && sharedSettings.jsonExpiration == jsonQuerySharedSettings.knownSharedExpiration
                }?.let {
                    // the user already has the latest version --> no need to send anything
                    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
                }

                Message.createDiscussionSettingsUpdateMessage(
                    db,
                    discussionId,
                    sharedSettings,
                    messageSender.bytesOwnedIdentity,
                    true,
                    null
                )?.postSettingsMessage(true, messageSender.senderIdentity)
        }
    }
    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}