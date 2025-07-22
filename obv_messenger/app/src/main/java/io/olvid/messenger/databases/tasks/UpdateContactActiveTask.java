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

import java.util.Arrays;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.ContactCacheSingleton;
import io.olvid.messenger.databases.entity.Contact;

public class UpdateContactActiveTask implements Runnable {
    private final byte[] bytesOwnedIdentity;
    private final byte[] bytesContactIdentity;
    private final boolean active;

    public UpdateContactActiveTask(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, boolean active) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.active = active;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
        if (contact != null && contact.active != this.active) {
            contact.active = active;
            db.contactDao().updateActive(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.active);
            db.discussionDao().updateActive(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.active);
            
            if (Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                ContactCacheSingleton.INSTANCE.updateContactCachedInfo(contact);
            }
        }
    }
}
