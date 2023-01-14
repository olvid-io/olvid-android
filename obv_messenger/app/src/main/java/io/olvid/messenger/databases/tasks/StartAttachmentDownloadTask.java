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
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;

public class StartAttachmentDownloadTask implements Runnable {
    private final FyleMessageJoinWithStatus fyleMessageJoinWithStatus;

    public StartAttachmentDownloadTask(FyleMessageJoinWithStatus fyleMessageJoinWithStatus) {
        this.fyleMessageJoinWithStatus = fyleMessageJoinWithStatus;
    }

    @Override
    public void run() {
        AppSingleton.getEngine().downloadLargeAttachment(fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);
        FyleMessageJoinWithStatus reloadedFyleMessageJoinWithStatus = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().get(fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.messageId);
        reloadedFyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_DOWNLOADING;
        AppDatabase.getInstance().fyleMessageJoinWithStatusDao().updateStatus(reloadedFyleMessageJoinWithStatus.messageId, reloadedFyleMessageJoinWithStatus.fyleId, reloadedFyleMessageJoinWithStatus.status);
    }
}
