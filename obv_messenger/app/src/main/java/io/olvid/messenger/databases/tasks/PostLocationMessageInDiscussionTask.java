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

package io.olvid.messenger.databases.tasks;

import android.location.Location;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import io.olvid.engine.Logger;
import io.olvid.messenger.customClasses.LocationShareQuality;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonLocation;
import io.olvid.messenger.databases.entity.jsons.JsonMessage;
import io.olvid.messenger.services.UnifiedForegroundService;

public class PostLocationMessageInDiscussionTask implements Runnable {
    private final AppDatabase db;
    private final long discussionId;
    private final boolean showToast;
    private final Location location;
    private final Long shareExpirationInMs;
    private final LocationShareQuality quality;
    private final boolean isSharingLocationMessage;

    // post simple location message
    public static PostLocationMessageInDiscussionTask postSendLocationMessageInDiscussionTask(Location location, long discussionId, boolean showToast) {
        return new PostLocationMessageInDiscussionTask(location, discussionId, showToast, null, null, false);
    }

    // post sharing location message
    public static PostLocationMessageInDiscussionTask startLocationSharingInDiscussionTask(Location location, long discussionId, boolean showToast, @Nullable Long shareExpiration, @NotNull LocationShareQuality quality) {
        return new PostLocationMessageInDiscussionTask(location, discussionId, showToast, shareExpiration, quality, true);
    }

    private PostLocationMessageInDiscussionTask(@NotNull Location location, long discussionId, boolean showToast, @Nullable Long shareExpirationInMs, @Nullable LocationShareQuality quality, boolean isSharingLocationMessage) {
        this.db = AppDatabase.getInstance();
        this.discussionId = discussionId;
        this.showToast = showToast;
        this.location = location;
        this.shareExpirationInMs = shareExpirationInMs;
        this.quality = quality;
        this.isSharingLocationMessage = isSharingLocationMessage;
    }

    @Override
    public void run() {
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (!discussion.isNormal()) {
            Logger.w("A message was posted in a discussion where you cannot post!!!");
            return;
        }

        JsonExpiration discussionDefaultJsonExpiration = null;
        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
        if (discussionCustomization != null) {
            discussionDefaultJsonExpiration = discussionCustomization.getExpirationJson();
        }

        final JsonMessage jsonMessage = new JsonMessage();
        if (isSharingLocationMessage) {
            jsonMessage.setJsonLocation(JsonLocation.startSharingLocationMessage(shareExpirationInMs, quality, location));
        } else {
            jsonMessage.setJsonLocation(JsonLocation.sendLocationMessage(location));
        }
        jsonMessage.setBody(jsonMessage.getJsonLocation().getLocationMessageBody());

        if (discussionDefaultJsonExpiration != null) {
            jsonMessage.setJsonExpiration(discussionDefaultJsonExpiration);
        }

        db.runInTransaction(() -> {
            discussion.lastOutboundMessageSequenceNumber++;
            db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
            discussion.updateLastMessageTimestamp(System.currentTimeMillis());
            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
            Message message = new Message(
                    db,
                    discussion.lastOutboundMessageSequenceNumber,
                    jsonMessage,
                    null,
                    System.currentTimeMillis(),
                    Message.STATUS_UNPROCESSED,
                    Message.TYPE_OUTBOUND_MESSAGE,
                    discussionId,
                    null,
                    discussion.bytesOwnedIdentity,
                    discussion.senderThreadIdentifier,
                    0,
                    0
            );
            message.id = db.messageDao().insert(message);
            message.post(showToast, null);

            // start sharing location service for this discussion
            if (isSharingLocationMessage) {
                UnifiedForegroundService.LocationSharingSubService.startSharingInDiscussion(discussionId, shareExpirationInMs, quality, message.id);
            }
        });
    }
}
