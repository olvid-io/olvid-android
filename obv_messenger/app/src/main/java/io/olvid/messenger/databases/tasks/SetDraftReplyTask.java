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


import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;

public class SetDraftReplyTask implements Runnable {
    private final Long discussionId;
    private final Long selectedMessageId;
    private final String draftBody;


    public SetDraftReplyTask(Long discussionId, Long selectedMessageId, String draftBody) {
        this.selectedMessageId = selectedMessageId;
        this.discussionId = discussionId;
        this.draftBody = draftBody;
    }

    @Override
    public void run() {
        if ((selectedMessageId == null) || (discussionId == null)) {
            return;
        }

        AppDatabase db = AppDatabase.getInstance();
        Message message = db.messageDao().get(selectedMessageId);
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (message == null || discussion == null) {
            return;
        }

        Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
        if (draftMessage == null) {
            // create an empty draft
            draftMessage = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
            draftMessage.contentBody = draftBody;
            draftMessage.id = db.messageDao().insert(draftMessage);
        }
        Message.JsonMessageReference jsonReply = Message.JsonMessageReference.of(message);
        try {
            draftMessage.jsonReply = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonReply);
            draftMessage.timestamp = System.currentTimeMillis();
            draftMessage.sortIndex = draftMessage.timestamp;
            db.messageDao().update(draftMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
