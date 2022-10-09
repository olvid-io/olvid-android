/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;

public class InsertContactRevokedMessageTask implements Runnable {
    private final byte[] bytesOwnedIdentity;
    private final byte[] bytesContactIdentity;

    public InsertContactRevokedMessageTask(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        try {
            Discussion discussion = db.discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
            if (discussion != null) {
                Message message = Message.createContactInactiveReasonMessage(db, discussion.id, bytesContactIdentity, ObvContactActiveOrInactiveReason.REVOKED);
                message.id = db.messageDao().insert(message);
                if (discussion.updateLastMessageTimestamp(message.timestamp)) {
                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                }
            }
        } catch (Exception e) {
            Logger.e("Unable to insert contact revoked message.");
            e.printStackTrace();
        }
    }
}
