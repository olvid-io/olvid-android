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

public class UpdateContactKeycloakManagedTask implements Runnable {
    private final byte[] bytesOwnedIdentity;
    private final byte[] bytesContactIdentity;
    private final boolean keycloakManaged;

    public UpdateContactKeycloakManagedTask(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, boolean keycloakManaged) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.keycloakManaged = keycloakManaged;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
        if (contact != null && contact.keycloakManaged != keycloakManaged) {
            contact.keycloakManaged = keycloakManaged;
            db.contactDao().updateKeycloakManaged(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.keycloakManaged);
            db.discussionDao().updateKeycloakManaged(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.keycloakManaged);

            if (Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                ContactCacheSingleton.INSTANCE.updateContactCachedInfo(contact);
            }
        }
    }
}
