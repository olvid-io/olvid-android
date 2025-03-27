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

import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;

public class UpdateContactDisplayNameAndPhotoTask implements Runnable {
    private final byte[] bytesIdentity;
    private final byte[] bytesOwnedIdentity;
    private final JsonIdentityDetailsWithVersionAndPhoto identityDetails;

    public UpdateContactDisplayNameAndPhotoTask(byte[] bytesContactIdentity, byte[] bytesOwnedIdentity, JsonIdentityDetailsWithVersionAndPhoto identityDetails) {
        this.bytesIdentity = bytesContactIdentity;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.identityDetails = identityDetails;
    }

    @Override
    public void run() {
        if (identityDetails == null || identityDetails.getIdentityDetails() == null || identityDetails.getIdentityDetails().isEmpty()) {
            return;
        }
        AppDatabase db = AppDatabase.getInstance();
        Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesIdentity);
        if (contact != null) {
            try {
                contact.setIdentityDetailsAndDisplayName(identityDetails.getIdentityDetails());
                db.contactDao().updateAllDisplayNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.identityDetails, contact.displayName, contact.firstName, contact.customDisplayName, contact.sortDisplayName, contact.fullSearchDisplayName);

                new UpdateAllGroupMembersNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity).run();
            } catch (Exception e) {
                // do nothing
            }
            contact.photoUrl = identityDetails.getPhotoUrl();
            db.contactDao().updatePhotoUrl(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.photoUrl);
            contact.newPublishedDetails = Contact.PUBLISHED_DETAILS_NOTHING_NEW;
            db.contactDao().updatePublishedDetailsStatus(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.newPublishedDetails);

            if (Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                AppSingleton.updateCachedCustomDisplayName(contact.bytesContactIdentity, contact.getCustomDisplayName(), contact.getFirstNameOrCustom());
                AppSingleton.updateCachedPhotoUrl(contact.bytesContactIdentity, contact.getCustomPhotoUrl());
            }

            // rename the corresponding one-to-one discussion
            Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
            if (discussion != null) {
                discussion.title = contact.getCustomDisplayName();
                discussion.photoUrl = contact.getCustomPhotoUrl();
                db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                ShortcutActivity.updateShortcut(discussion);

                // delete all contact details updated messages from the discussion
                db.messageDao().deleteAllDiscussionNewPublishedDetailsMessages(discussion.id);
            }
        }
    }
}
