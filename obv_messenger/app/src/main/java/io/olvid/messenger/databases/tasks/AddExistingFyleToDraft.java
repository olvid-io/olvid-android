/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifFileAlreadyExistsOuterClass;

public class AddExistingFyleToDraft implements Runnable {

    private final Fyle fyle; // always a real File, with absolute path
    private final long discussionId;
    private final String mimeType;
    private final String fileName;
    private final long size;
    private final byte[] sha256;
    private final WebClientManager manager;
    private final long localId;

    public AddExistingFyleToDraft(Fyle fyle, long discussionId, String mimeType, String fileName, long size, byte[] sha256, WebClientManager manager, long localId) {
        this.fyle = fyle;
        this.discussionId = discussionId;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.size = size;
        this.sha256 = sha256;
        this.manager = manager;
        this.localId = localId;
    }

    public void run() {
        final AppDatabase db = AppDatabase.getInstance();
        final Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion == null) {
            return;
        }
        db.runInTransaction(() -> {
            Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
            if (draftMessage == null) {
                draftMessage = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
                db.messageDao().insert(draftMessage);
            }
        });
        Message draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
        try {
            final Fyle finalFyle = fyle;
            final String finalOutputFile = Fyle.buildFylePath(sha256);
            Boolean alreadyAttached = db.runInTransaction(() -> {
                if (db.fyleMessageJoinWithStatusDao().get(finalFyle.id, draftMessage.id) != null) {
                    // file already attached
                    return true;
                }
                // Fyle is already complete, we can simply "hard-link" it
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = FyleMessageJoinWithStatus.createDraft(finalFyle.id,
                        draftMessage.id,
                        draftMessage.senderIdentifier,
                        finalOutputFile,
                        fileName,
                        mimeType,
                        size
                );
                db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
                draftMessage.recomputeAttachmentCount(db);
                db.messageDao().updateAttachmentCount(draftMessage.id, draftMessage.totalAttachmentCount, draftMessage.imageCount, 0, draftMessage.imageResolutions);
                return false;
            });
            if (alreadyAttached == null || alreadyAttached) {
                NotifFileAlreadyExistsOuterClass.NotifFileAlreadyAttached.Builder notifBuilder;
                ColissimoOuterClass.Colissimo.Builder colissimoBuilder;
                notifBuilder = NotifFileAlreadyExistsOuterClass.NotifFileAlreadyAttached.newBuilder();
                colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
                notifBuilder.setLocalId(localId);
                notifBuilder.setDiscussionId(discussionId);
                colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_FILE_ALREADY_ATTACHED);
                colissimoBuilder.setNotifFileAlreadyAttached(notifBuilder);
                this.manager.sendColissimo(colissimoBuilder.build());
            } else {
                NotifFileAlreadyExistsOuterClass.NotifFileAlreadyExists.Builder notifBuilder;
                ColissimoOuterClass.Colissimo.Builder colissimoBuilder;
                notifBuilder = NotifFileAlreadyExistsOuterClass.NotifFileAlreadyExists.newBuilder();
                colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
                notifBuilder.setLocalId(localId);
                notifBuilder.setDiscussionId(discussionId);
                colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_FILE_ALREADY_EXISTS);
                colissimoBuilder.setNotifFileAlreadyExists(notifBuilder);
                this.manager.sendColissimo(colissimoBuilder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
