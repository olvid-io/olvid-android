/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.databases.tasks;

import android.location.Location;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.services.UnifiedForegroundService;

public class UpdateLocationMessageTask implements Runnable {
    final long discussionId;
    final long messageId;
    final Location location;

    // normal update location message (will update location and increment count field)
    public static UpdateLocationMessageTask createPostSharingLocationUpdateMessage(long discussionId, long messageId, @NonNull Location location) {
        return new UpdateLocationMessageTask(discussionId, messageId, location);
    }

    // end of sharing location message (send when stop sharing: will set sharingExpiration to null and increment count)
    public static UpdateLocationMessageTask createPostEndOfSharingMessageTask(long discussionId, long messageId) {
        return new UpdateLocationMessageTask(discussionId, messageId, null);
    }

    public UpdateLocationMessageTask(long discussionId, long messageId, Location location) {
        this.discussionId = discussionId;
        this.messageId = messageId;
        this.location = location;
    }


    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Message originalMessage = db.messageDao().get(messageId);
        if (originalMessage == null || originalMessage.wipeStatus == Message.WIPE_STATUS_WIPED || originalMessage.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
            if (location != null) {
                // If the message can no longer be found (typically because it expired, was deleted, or was remote deleted) --> stop sharing
                // Only do this for createPostSharingLocationUpdateMessage to avoid infinite loop ;)
                UnifiedForegroundService.LocationSharingSubService.stopSharingLocationSync(discussionId);
            }
            return;
        }

        // check message is a sharing location one
        if (originalMessage.jsonLocation == null) {
            Logger.e("UpdateLocationMessageTask: trying to update a message that is not a location message");
            return;
        }
        if (originalMessage.locationType != Message.LOCATION_TYPE_SHARE && originalMessage.locationType != Message.LOCATION_TYPE_SHARE_FINISHED) {
            Logger.e("UpdateLocationMessageTask: trying to update a message that is not location sharing");
            return;
        }

        // just show an error in logs, this shall not happen
        if (originalMessage.locationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
            Logger.e("UpdateLocationMessageTask: trying to update a sharing location message that is already finished");
        }

        // create jsonLocation depending on update message type
        Message.JsonLocation jsonLocation;
        // update location message
        if (location != null) {
            jsonLocation = Message.JsonLocation.updateSharingLocationMessage(originalMessage.getJsonLocation(), location);
            // update message body
            originalMessage.contentBody = jsonLocation.getLocationMessageBody();
        } else {
            // end sharing message
            jsonLocation = Message.JsonLocation.endOfSharingLocationMessage(originalMessage.getJsonLocation().getCount());
        }

        // serialize new jsonLocation
        try {
            originalMessage.jsonLocation = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonLocation);
        } catch (JsonProcessingException e) {
            Logger.e("UpdateLocationMessageTask: Impossible to serialize jsonLocation to update location message", e);
            return;
        }

        // try to post update message, abort if unable to post
        boolean success = Message.postUpdateMessageMessage(originalMessage);
        if (!success) {
            Logger.e("UpdateLocationMessageTask: Unable to update location message");
            return;
        }

        // update message locally and update metadata
        // share location update
        if (location != null) {
            // update message
            db.messageDao().updateLocation(originalMessage.id, originalMessage.contentBody, originalMessage.jsonLocation);
            // update or insert metadata
            MessageMetadata messageMetadata = db.messageMetadataDao().getByKind(originalMessage.id, MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE);
            if (messageMetadata != null) {
                db.messageMetadataDao().updateTimestamp(messageMetadata.id, System.currentTimeMillis());
            } else {
                db.messageMetadataDao().insert(new MessageMetadata(originalMessage.id, MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE, System.currentTimeMillis()));
            }
        } else {
            // if end of sharing only update location type (do not erase previous location)
            db.messageDao().updateLocationType(originalMessage.id, Message.LOCATION_TYPE_SHARE_FINISHED);
            db.messageMetadataDao().insert(new MessageMetadata(originalMessage.id, MessageMetadata.KIND_LOCATION_SHARING_END, System.currentTimeMillis()));
        }
    }
}
