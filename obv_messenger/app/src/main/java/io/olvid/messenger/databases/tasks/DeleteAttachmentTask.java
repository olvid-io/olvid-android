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
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;

public class DeleteAttachmentTask implements Runnable {
    private final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;

    public DeleteAttachmentTask(FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) {
        this.fyleAndStatus = fyleAndStatus;
    }

    @Override
    public void run() {
        if (fyleAndStatus == null || fyleAndStatus.fyleMessageJoinWithStatus == null || fyleAndStatus.fyle == null) {
            return;
        }

        if (fyleAndStatus.fyleMessageJoinWithStatus.engineNumber != null) {
            switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                    AppSingleton.getEngine().markAttachmentForDeletion(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                    break;
                case FyleMessageJoinWithStatus.STATUS_UPLOADING:
                    AppSingleton.getEngine().cancelAttachmentUpload(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                    break;
            }
        }
        AppDatabase db = AppDatabase.getInstance();
        List<Long> messageIds = db.fyleMessageJoinWithStatusDao().getMessageIdsForFyleSync(fyleAndStatus.fyle.id);
        if ((messageIds.isEmpty()) || ((messageIds.size() == 1) && (messageIds.get(0) == fyleAndStatus.fyleMessageJoinWithStatus.messageId))) {
            if (fyleAndStatus.fyle.sha256 != null) {
                try {
                    Fyle.acquireLock(fyleAndStatus.fyle.sha256);
                    fyleAndStatus.fyle.delete();
                } finally {
                    Fyle.releaseLock(fyleAndStatus.fyle.sha256);
                }
            } else {
                fyleAndStatus.fyle.delete();
            }
        } else {
            db.fyleMessageJoinWithStatusDao().delete(fyleAndStatus.fyleMessageJoinWithStatus);
        }

        Message message = db.messageDao().get(fyleAndStatus.fyleMessageJoinWithStatus.messageId);
        if (message != null) {
            message.recomputeAttachmentCount(db);
            if (message.status != Message.STATUS_DRAFT && message.isEmpty()) {
                db.messageDao().delete(message);
                UnreadCountsSingleton.INSTANCE.messageDeleted(message);
            } else {
                db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageCount, message.videoCount, message.audioCount, message.firstAttachmentName, message.wipedAttachmentCount, message.imageResolutions);
            }
        }
    }
}
