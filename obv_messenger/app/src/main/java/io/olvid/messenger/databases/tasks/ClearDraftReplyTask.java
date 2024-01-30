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


import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;

public class ClearDraftReplyTask implements Runnable {
    private final Long discussionId;


    public ClearDraftReplyTask(Long discussionId) {
        this.discussionId = discussionId;
    }

    @Override
    public void run() {
        if (discussionId == null) {
            return;
        }

        AppDatabase db = AppDatabase.getInstance();
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion == null) {
            return;
        }
        Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
        if (draftMessage != null) {
            draftMessage.jsonReply = null;
            db.messageDao().update(draftMessage);
        }
    }
}
