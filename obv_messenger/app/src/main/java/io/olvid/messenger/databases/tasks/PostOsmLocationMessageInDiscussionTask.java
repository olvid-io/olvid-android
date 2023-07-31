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

import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonLocation;
import io.olvid.messenger.databases.entity.jsons.JsonMessage;

public class PostOsmLocationMessageInDiscussionTask implements Runnable {
    private final AppDatabase db;
    private final long discussionId;
    private final Location location;
    private final @Nullable Bitmap snapshotBitmap;
    private final @Nullable String address;

    public PostOsmLocationMessageInDiscussionTask(@NotNull Location location, long discussionId, @Nullable Bitmap snapshotBitmap, @Nullable String address) {
        this.db = AppDatabase.getInstance();
        this.discussionId = discussionId;
        this.location = location;
        this.snapshotBitmap = snapshotBitmap;
        this.address = address;
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
        JsonLocation jsonLocation = JsonLocation.sendLocationMessage(location);
        jsonLocation.setAddress(address);
        jsonMessage.setJsonLocation(jsonLocation);
        jsonMessage.setBody(jsonMessage.getJsonLocation().getLocationMessageBody());

        if (discussionDefaultJsonExpiration != null) {
            jsonMessage.setJsonExpiration(discussionDefaultJsonExpiration);
        }

        // first, copy file locally
        File imageTmpFile = null;
        if (snapshotBitmap != null) {
            try {
                File photoDir = new File(App.getContext().getCacheDir(), App.CAMERA_PICTURE_FOLDER);
                imageTmpFile = File.createTempFile("map_preview", ".png", photoDir);
                //noinspection ResultOfMethodCallIgnored
                photoDir.mkdirs();
                FileOutputStream fileOutputStream = new FileOutputStream(imageTmpFile.getAbsolutePath());
                snapshotBitmap.compress(Bitmap.CompressFormat.PNG, 100,fileOutputStream);
            }
            catch (Exception e) {
                Logger.e("PostOsmLocationMessageInDiscussionTask: impossible to import preview as an attachment", e);
            }
        }

        File finalImageTmpFile = imageTmpFile;
        db.runInTransaction(() -> {
            // create a new empty draft
            Message draftMessage = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
            draftMessage.id = db.messageDao().insert(draftMessage);

            // add fyle to draft (not very efficient to do this in a transaction, but it avoid to have multiple draft in db at the same time)
            if (finalImageTmpFile != null) {
                new AddFyleToDraftFromUriTask(draftMessage, Uri.fromFile(finalImageTmpFile), "preview.png", "image/png", this.discussionId).run();
            }

            // send message
            discussion.lastOutboundMessageSequenceNumber++;
            db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
            discussion.updateLastMessageTimestamp(System.currentTimeMillis());
            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
            draftMessage.senderSequenceNumber = discussion.lastOutboundMessageSequenceNumber;
            draftMessage.setJsonMessage(jsonMessage);
            draftMessage.status = Message.STATUS_UNPROCESSED;
            draftMessage.timestamp = System.currentTimeMillis();
            draftMessage.computeOutboundSortIndex(db);
            draftMessage.recomputeAttachmentCount(db);
            db.messageDao().update(draftMessage);
            draftMessage.post(true, null);
        });
    }
}
