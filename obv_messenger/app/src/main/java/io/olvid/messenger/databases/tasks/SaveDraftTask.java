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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;

public class SaveDraftTask implements Runnable {
    private final long discussionId;
    private final String text;
    private final Message previousDraftMessage;
    private final List<Message.JsonUserMention> mentions;

    public SaveDraftTask(long discussionId, String text, Message previousDraftMessage, List<Message.JsonUserMention> mentions) {
        this.discussionId = discussionId;
        this.text = text;
        this.previousDraftMessage = previousDraftMessage;
        this.mentions = mentions;
    }

    @Override
    public void run() {
        final AppDatabase db = AppDatabase.getInstance();
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion  == null || !discussion.canPostMessages()) {
            return;
        }
        db.runInTransaction(() -> {
            Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
            if (draftMessage == null) {
                if (text == null) {
                    // no draft exists, and we don't have any text to save -> nothing to do here!
                    return;
                }
                draftMessage = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
                draftMessage.id = db.messageDao().insert(draftMessage);
            } else {
                if (previousDraftMessage == null || previousDraftMessage.id != draftMessage.id) {
                    // the draft message was updated in the background, we do not overwrite it with our text
                    return;
                }
            }

            Message.JsonMessage jsonMessage = draftMessage.getJsonMessage();
            // build sets to be able to check if mentions changed
            HashSet<Message.JsonUserMention> mentionSet = jsonMessage.getJsonUserMentions() == null ? null : new HashSet<>(jsonMessage.getJsonUserMentions());
            HashSet<Message.JsonUserMention> newMentionSet = mentions == null ? null : new HashSet<>(mentions);
            if (text != null && Objects.equals(jsonMessage.getBody(), text) && Objects.equals(mentionSet, newMentionSet)) {
                // the draft did not change, no need to do anything here
                return;
            } else if (draftMessage.totalAttachmentCount == 0 && text == null) {
                // the draft was cleared --> delete it
                db.messageDao().delete(draftMessage);
                return;
            }
            jsonMessage.setJsonUserMentions(mentions);
            jsonMessage.setBody(text);
            draftMessage.setJsonMessage(jsonMessage);
            draftMessage.timestamp = System.currentTimeMillis();
            draftMessage.sortIndex = draftMessage.timestamp;
            db.messageDao().update(draftMessage);
            if (discussion.updateLastMessageTimestamp(draftMessage.timestamp)) {
                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
            }
        });
    }
}
