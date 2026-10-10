/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonMessage;
import io.olvid.messenger.databases.entity.jsons.JsonPoll;
import io.olvid.messenger.databases.entity.jsons.JsonUserMention;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;

public class PostMessageInDiscussionTask implements Runnable {
    private final AppDatabase db;
    private final String body;
    private final long discussionId;
    private final boolean showToast;
    private final OpenGraph openGraph;
    private final List<JsonUserMention> mentions;
    private final JsonPoll poll;

    // resolve the expiration to apply to an outgoing message posted outside the draft flow (location messages):
    // the ephemeral settings selected in the compose area (stored on the discussion draft) combined with the discussion default settings,
    // exactly like the draft-consuming path in run() does
    @Nullable
    static JsonExpiration computeOutboundExpiration(@NonNull AppDatabase db, long discussionId) {
        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
        JsonExpiration discussionDefaultJsonExpiration = discussionCustomization == null ? null : discussionCustomization.getExpirationJson();
        Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
        JsonExpiration draftJsonExpiration = draftMessage == null ? null : draftMessage.getJsonMessage().getJsonExpiration();
        return draftJsonExpiration == null ? discussionDefaultJsonExpiration : draftJsonExpiration.computeGcd(discussionDefaultJsonExpiration);
    }

    public PostMessageInDiscussionTask(String body, long discussionId, boolean showToast, OpenGraph openGraph, List<JsonUserMention> mentions, JsonPoll poll) {
        this.db = AppDatabase.getInstance();
        this.body = body;
        this.discussionId = discussionId;
        this.showToast = showToast;
        this.openGraph = openGraph;
        this.mentions = mentions;
        this.poll = poll;
    }

    @Override
    public void run() {
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion == null || !discussion.isNormal()) {
            Logger.w("A message was posted in a locked discussion!!!");
            return;
        }

        // attach link preview if any
        if (openGraph != null && !openGraph.isEmpty()) {
            try {
                File cacheDir = new File(App.getContext().getCacheDir(), App.CAMERA_PICTURE_FOLDER);
                File payloadFile = new File(cacheDir, Logger.getUuidString(UUID.randomUUID()));
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
                if (!payloadFile.createNewFile()) {
                    throw new FileNotFoundException();
                }
                FileOutputStream fos = new FileOutputStream(payloadFile);
                fos.write(openGraph.encode().getBytes());
                fos.flush();
                fos.close();

                // no need to delete payloadFile: AddFyleToDraftFromUriTask already moves it
                new AddFyleToDraftFromUriTask(payloadFile, openGraph.fileName(), OpenGraph.MIME_TYPE, discussionId).run();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        JsonExpiration discussionDefaultJsonExpiration = null;
        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
        if (discussionCustomization != null) {
            discussionDefaultJsonExpiration = discussionCustomization.getExpirationJson();
        }

        final Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
        if (draftMessage == null) {
            final JsonMessage jsonMessage;
            if (body == null) {
                jsonMessage = new JsonMessage();
            } else {
                jsonMessage = new JsonMessage(body);
            }
            jsonMessage.setJsonUserMentions(mentions);
            jsonMessage.setJsonPoll(poll);

            if (discussionDefaultJsonExpiration != null) {
                jsonMessage.setJsonExpiration(discussionDefaultJsonExpiration);
            }

            db.runInTransaction(() -> {
                long senderSequenceNumber = discussion.lastOutboundMessageSequenceNumber + 1;
                Message message = new Message(
                        db,
                        senderSequenceNumber,
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
                        0,
                        0,
                        0,
                        null);
                message.mentioned = JsonUserMention.isIdentityMentioned(jsonMessage.jsonUserMentions, message.senderIdentifier);
                if (message.isEmpty()) {
                    return;
                }
                discussion.lastOutboundMessageSequenceNumber = senderSequenceNumber;
                db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
                discussion.updateLastMessageTimestamp(System.currentTimeMillis());
                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                message.id = db.messageDao().insert(message);
                message.post(showToast, null);
            });
        } else {
            final JsonMessage jsonMessage = draftMessage.getJsonMessage();
            jsonMessage.setBody(body);
            jsonMessage.setJsonUserMentions(mentions);
            jsonMessage.jsonPoll = poll;

            if (discussionDefaultJsonExpiration != null) {
                if (jsonMessage.getJsonExpiration() == null) {
                    jsonMessage.setJsonExpiration(discussionDefaultJsonExpiration);
                } else {
                    // compute min
                    jsonMessage.setJsonExpiration(jsonMessage.getJsonExpiration().computeGcd(discussionDefaultJsonExpiration));
                }
            }

            db.runInTransaction(() -> {
                draftMessage.setJsonMessage(jsonMessage);
                draftMessage.recomputeAttachmentCount(db);
                if (draftMessage.isEmpty()) {
                    return;
                }
                discussion.lastOutboundMessageSequenceNumber++;
                db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
                discussion.updateLastMessageTimestamp(System.currentTimeMillis());
                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                draftMessage.senderSequenceNumber = discussion.lastOutboundMessageSequenceNumber;
                draftMessage.mentioned = JsonUserMention.isIdentityMentioned(jsonMessage.jsonUserMentions, draftMessage.senderIdentifier);
                draftMessage.status = Message.STATUS_UNPROCESSED;
                draftMessage.timestamp = System.currentTimeMillis();
                draftMessage.computeOutboundSortIndex(db);
                db.messageDao().update(draftMessage);
                draftMessage.post(showToast, null);
            });
        }
    }
}
