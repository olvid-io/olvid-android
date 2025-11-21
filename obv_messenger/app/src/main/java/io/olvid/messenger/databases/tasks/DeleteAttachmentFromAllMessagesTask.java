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

import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.UnreadCountsSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;

public class DeleteAttachmentFromAllMessagesTask implements Runnable {
    private final long fyleId;

    public DeleteAttachmentFromAllMessagesTask(long fyleId) {
        this.fyleId = fyleId;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        List<FyleMessageJoinWithStatus> fyleMessageJoinWithStatuses = db.fyleMessageJoinWithStatusDao().getForFyleId(fyleId);
        Fyle fyle = db.fyleDao().getById(fyleId);

        if (fyle == null) {
            return;
        }

        for (FyleMessageJoinWithStatus fyleMessageJoinWithStatus : fyleMessageJoinWithStatuses) {
            if (fyleMessageJoinWithStatus.engineNumber != null) {
                switch (fyleMessageJoinWithStatus.status) {
                    case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                    case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                        AppSingleton.getEngine().markAttachmentForDeletion(fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);
                        break;
                    case FyleMessageJoinWithStatus.STATUS_UPLOADING:
                        AppSingleton.getEngine().cancelAttachmentUpload(fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);
                        break;
                }
            }
        }

        if (fyle.sha256 != null) {
            try {
                Fyle.acquireLock(fyle.sha256);
                fyle.delete();
            } finally {
                Fyle.releaseLock(fyle.sha256);
            }
        } else {
            fyle.delete();
        }

        for (FyleMessageJoinWithStatus fyleMessageJoinWithStatus : fyleMessageJoinWithStatuses) {
            Message message = db.messageDao().get(fyleMessageJoinWithStatus.messageId);
            if (message != null) {
                message.recomputeAttachmentCount(db);
                if (message.status != Message.STATUS_DRAFT && message.isEmpty()) {
                    db.messageDao().delete(message);
                    UnreadCountsSingleton.INSTANCE.messageDeleted(message);
                } else {
                    db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageAndVideoCount, message.videoCount, message.audioCount, message.firstAttachmentName, message.wipedAttachmentCount, message.imageResolutions);
                }
            }
        }
    }
}
