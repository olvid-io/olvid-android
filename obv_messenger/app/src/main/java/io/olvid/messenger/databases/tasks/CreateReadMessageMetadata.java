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


import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.MessageMetadata;

public class CreateReadMessageMetadata implements Runnable {
    private final long messageId;

    public CreateReadMessageMetadata(long messageId) {
        this.messageId = messageId;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        if (!db.messageMetadataDao().messageMetadataExists(messageId, MessageMetadata.KIND_READ)) {
            MessageMetadata messageMetadata = new MessageMetadata(messageId, MessageMetadata.KIND_READ, System.currentTimeMillis());
            db.messageMetadataDao().insert(messageMetadata);
        }
    }
}
