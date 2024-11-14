/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.settings.SettingsActivity;

public class ContactDisplayNameFormatChangedTask implements Runnable {
    @Override
    public void run() {
        synchronized (ContactDisplayNameFormatChangedTask.class) { // we make sure that a single renaming task run at a time
            boolean lastNameSort = SettingsActivity.getSortContactsByLastName();
            boolean uppercaseLastName = SettingsActivity.getUppercaseLastName();
            String contactDisplayNameFormat = SettingsActivity.getContactDisplayNameFormat();
            Collator collator = Collator.getInstance();

            AppDatabase db = AppDatabase.getInstance();

            for (Contact contact : db.contactDao().getAllSync()) {
                try {
                    JsonIdentityDetails identityDetails = contact.getIdentityDetails();
                    if (identityDetails == null) {
                        // at next startup contactDetails will be updated and the names recomputed
                        continue;
                    }

                    /////////
                    // first, compute the displayName
                    contact.displayName = identityDetails.formatDisplayName(contactDisplayNameFormat, uppercaseLastName);

                    /////////
                    // then, compute the sortDisplayName
                    contact.sortDisplayName = computeSortDisplayName(collator, identityDetails, contact.customDisplayName, lastNameSort);

                    ////////
                    // we do this to force a recompute of the full search field
                    contact.setPersonalNote(contact.personalNote);

                    db.contactDao().updateAllDisplayNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.identityDetails, contact.displayName, contact.firstName, contact.customDisplayName, contact.sortDisplayName, contact.fullSearchDisplayName);

                    Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                    if (discussion != null) {
                        discussion.title = contact.getCustomDisplayName();
                        db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                        ShortcutActivity.updateShortcut(discussion);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            new UpdateAllGroupMembersNames().run();

            AppSingleton.reloadCachedDisplayNamesAndHues();
        }
    }


    public static byte[] computeSortDisplayName(@NonNull JsonIdentityDetails identityDetails, @Nullable String customDisplayName, boolean sortContactsByLastName) {
        return computeSortDisplayName(Collator.getInstance(), identityDetails, customDisplayName, sortContactsByLastName);
    }

    private static byte[] computeSortDisplayName(Collator collator, @NonNull JsonIdentityDetails identityDetails, @Nullable String customDisplayName, boolean sortContactsByLastName) {
        if (customDisplayName != null) {
            return collator.getCollationKey(customDisplayName).toByteArray();
        } else {
            String concat = JsonIdentityDetails.joinNames(identityDetails.getFirstName(), identityDetails.getLastName(), sortContactsByLastName, false);
            return collator.getCollationKey(concat).toByteArray();
        }
    }
}
