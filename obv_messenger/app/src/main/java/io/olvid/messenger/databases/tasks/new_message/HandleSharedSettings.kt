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
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings


fun handleSharedSettings(
    db: AppDatabase,
    jsonSharedSettings: JsonSharedSettings,
    messageSender: MessageSender,
    obvMessage: ObvMessage
): HandleMessageOutput {
    if (putMessageOnHoldIfDiscussionIsMissing(
            db,
            obvMessage.identifier,
            obvMessage.serverTimestamp,
            messageSender,
            jsonSharedSettings.oneToOneIdentifier,
            jsonSharedSettings.groupOwner,
            jsonSharedSettings.groupUid,
            jsonSharedSettings.groupV2Identifier
        )
    ) {
        return HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION
    }

    getDiscussion(db, jsonSharedSettings.groupUid, jsonSharedSettings.groupOwner, jsonSharedSettings.groupV2Identifier, jsonSharedSettings.getOneToOneIdentifier(), messageSender, GroupV2.Permission.CHANGE_SETTINGS)?.let { discussion ->

        var discussionCustomization = db.discussionCustomizationDao().get(discussion.id)
        if (discussionCustomization == null) {
            discussionCustomization = DiscussionCustomization(discussion.id)
            db.discussionCustomizationDao().insert(discussionCustomization)
        }

        val oldReadOnce = discussionCustomization.settingReadOnce
        val oldVisibilityDuration = discussionCustomization.settingVisibilityDuration
        val oldExistenceDuration = discussionCustomization.settingExistenceDuration

        var resendSettings = false
        var gcdWasComputed = false


        // check the version of the discussion customization
        val localVersion = discussionCustomization.sharedSettingsVersion
        if (localVersion == null || localVersion < jsonSharedSettings.version) {
            // the settings are newer --> replace them
            discussionCustomization.sharedSettingsVersion = jsonSharedSettings.version
            if (jsonSharedSettings.jsonExpiration == null) {
                discussionCustomization.settingReadOnce = false
                discussionCustomization.settingVisibilityDuration = null
                discussionCustomization.settingExistenceDuration = null
            } else {
                discussionCustomization.settingReadOnce = jsonSharedSettings.jsonExpiration!!.getReadOnce() != null && jsonSharedSettings.jsonExpiration!!.getReadOnce()
                discussionCustomization.settingVisibilityDuration = jsonSharedSettings.jsonExpiration!!.getVisibilityDuration()
                discussionCustomization.settingExistenceDuration = jsonSharedSettings.jsonExpiration!!.getExistenceDuration()
            }
        } else if (localVersion == jsonSharedSettings.version) {
            // versions are the same, compute the "gcd" of settings
            jsonSharedSettings.jsonExpiration?.also { expiration ->
                // if sharedSettingsVersion is non null, getExpirationJson() never returns null)
                val gcdExpiration = expiration.computeGcd(discussionCustomization.expirationJson)
                discussionCustomization.settingReadOnce = gcdExpiration.readOnce == true
                discussionCustomization.settingVisibilityDuration = gcdExpiration.visibilityDuration
                discussionCustomization.settingExistenceDuration = gcdExpiration.existenceDuration
                // also send GCDed settings to everyone, just in case, but only do this if the GCD actually changed something to the received settings
                resendSettings = expiration != gcdExpiration
                gcdWasComputed = true
            } ?: run {
                // received settings impose no constraint --> do nothing
                return@let
            }
        } else {
            // we received an older version --> ignore it and, for one-to-one or group v2 (when I have the permission) discussions, resend our current discussion settings, just in case
            resendSettings = true
        }

        // if resendSettings is true, we should resend a SharedSettings message to the sender as their settings are out of date
        if (resendSettings) {
            if (discussion.discussionType == Discussion.TYPE_CONTACT) {
                discussionCustomization.sharedSettingsJson?.let { jsonSharedSettings ->
                    Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, messageSender.bytesOwnedIdentity, true, null)
                        ?.postSettingsMessage(true, null)
                }
            } else if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                db.group2Dao()
                    .get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier)
                    ?.takeIf { it.ownPermissionChangeSettings }
                    ?.let {
                        discussionCustomization.sharedSettingsJson?.let { jsonSharedSettings ->
                            Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, messageSender.bytesOwnedIdentity, true, null)
                                ?.postSettingsMessage(true, if (gcdWasComputed) null else messageSender.senderIdentity)
                        }
                    }
            }
        }

        // if we arrive here, settings might have been updated
        if (oldReadOnce != discussionCustomization.settingReadOnce
            || oldVisibilityDuration != discussionCustomization.settingVisibilityDuration
            || oldExistenceDuration != discussionCustomization.settingExistenceDuration) {
            // there was indeed a change save it and add a message in the discussion
            Message.createDiscussionSettingsUpdateMessage(db, discussion.id, discussionCustomization.sharedSettingsJson, messageSender.senderIdentity, messageSender.type == MessageSender.Type.OWNED_IDENTITY, obvMessage.serverTimestamp)
                ?.let { message ->
                    db.messageDao().insert(message)
                    db.discussionCustomizationDao().update(discussionCustomization)
                    AppSingleton.getEngine().profileBackupNeeded(discussion.bytesOwnedIdentity)
                }
        }
    }

    return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
}