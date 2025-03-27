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

package io.olvid.messenger.databases.tasks;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonMessage;

public class ReplaceDiscussionDraftTask implements Runnable {
    private final long discussionId;
    private final @NonNull String draftText;
    private final List<Uri> draftFiles;

    public ReplaceDiscussionDraftTask(long discussionId, @NonNull String draftText, List<Uri> draftFiles) {
        this.discussionId = discussionId;
        this.draftText = draftText;
        this.draftFiles = draftFiles;
    }

    @Override
    public void run() {
        final AppDatabase db = AppDatabase.getInstance();
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion == null) {
            Logger.w("Trying to replace draft in a non-existing discussion");
            return;
        }
        db.runInTransaction(() -> {
            // delete existing draft if there is one
            Message message = db.messageDao().getDiscussionDraftMessageSync(discussionId);
            if (message != null) {
                db.messageDao().delete(message);
            }

            // create new draft with the shared text and files
            Message newDraft = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
            newDraft.setJsonMessage(new JsonMessage(draftText));
            newDraft.id = db.messageDao().insert(newDraft);
        });
        if (draftFiles != null) {
            for (Uri draftFile : draftFiles) {
                App.runThread(new AddFyleToDraftFromUriTask(draftFile, discussionId));
            }
        }
    }
}
