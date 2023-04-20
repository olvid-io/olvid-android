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


import androidx.annotation.NonNull;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;

public class SetDraftJsonExpirationTask implements Runnable {
    private final Long discussionId;
    private final Message.JsonExpiration jsonExpiration;


    public SetDraftJsonExpirationTask(long discussionId, @NonNull Message.JsonExpiration jsonExpiration) {
        this.discussionId = discussionId;
        this.jsonExpiration = jsonExpiration;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion == null) {
            return;
        }

        Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
        if (draftMessage == null) {
            // check if expiration adds some constraints
            if ((jsonExpiration.getReadOnce() == null || !jsonExpiration.getReadOnce())
                    && jsonExpiration.getVisibilityDuration() == null
                    && jsonExpiration.getExistenceDuration() == null) {
                // do not add a null expiration to non existing drafts
                return;
            }

            // create an empty draft
            draftMessage = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
            draftMessage.id = db.messageDao().insert(draftMessage);
        }

        try {
            draftMessage.jsonExpiration = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonExpiration);
            draftMessage.timestamp = System.currentTimeMillis();
            draftMessage.sortIndex = draftMessage.timestamp;
            db.messageDao().update(draftMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
