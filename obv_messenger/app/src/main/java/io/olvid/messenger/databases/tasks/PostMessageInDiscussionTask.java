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

package io.olvid.messenger.databases.tasks;

import io.olvid.engine.Logger;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;

public class PostMessageInDiscussionTask implements Runnable {
    private final AppDatabase db;
    private final String body;
    private final long discussionId;
    private final boolean showToast;

    public PostMessageInDiscussionTask(String body, long discussionId, boolean showToast) {
        this.db = AppDatabase.getInstance();
        this.body = body;
        this.discussionId = discussionId;
        this.showToast = showToast;
    }

    @Override
    public void run() {
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (!discussion.canPostMessages()) {
            Logger.w("A message was posted in a locked discussion!!!");
            return;
        }

        Message.JsonExpiration discussionDefaultJsonExpiration = null;
        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
        if (discussionCustomization != null) {
            discussionDefaultJsonExpiration = discussionCustomization.getExpirationJson();
        }

        final Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
        if (draftMessage == null) {
            final Message.JsonMessage jsonMessage;
            if (body == null) {
                jsonMessage = new Message.JsonMessage();
            } else {
                jsonMessage = new Message.JsonMessage(body);
            }

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
                        0, 0
                );
                message.id = db.messageDao().insert(message);
                message.post(showToast, null);
            });
        } else {
            final Message.JsonMessage jsonMessage = draftMessage.getJsonMessage();
            jsonMessage.setBody(body);

            if (discussionDefaultJsonExpiration != null) {
                if (jsonMessage.getJsonExpiration() == null) {
                    jsonMessage.setJsonExpiration(discussionDefaultJsonExpiration);
                } else {
                    // compute min
                    jsonMessage.setJsonExpiration(jsonMessage.getJsonExpiration().computeGcd(discussionDefaultJsonExpiration));
                }
            }

            db.runInTransaction(() -> {
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
                draftMessage.post(showToast, null);
            });
        }
    }
}
