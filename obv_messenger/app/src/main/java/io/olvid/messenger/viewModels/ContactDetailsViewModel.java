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

package io.olvid.messenger.viewModels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Invitation;


public class ContactDetailsViewModel extends ViewModel {
    private LiveData<List<DiscussionDao.DiscussionAndContactDisplayNames>> groupDiscussions;
    private LiveData<ContactAndInvitation> contactAndInvitation;


    public void setContactBytes(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        this.groupDiscussions = AppDatabase.getInstance().discussionDao().getContactActiveGroupDiscussionsWithContactNames(bytesContactIdentity, bytesOwnedIdentity, App.getContext().getString(R.string.text_contact_names_separator));
        this.contactAndInvitation = new ContactAndInvitationLiveData(
                AppDatabase.getInstance().contactDao().getAsync(bytesOwnedIdentity, bytesContactIdentity),
                AppDatabase.getInstance().invitationDao().getContactOneToOneInvitation(bytesOwnedIdentity, bytesContactIdentity)
        );
    }

    public LiveData<List<DiscussionDao.DiscussionAndContactDisplayNames>> getGroupDiscussions() {
        return groupDiscussions;
    }

    public LiveData<ContactAndInvitation> getContactAndInvitation() {
        return contactAndInvitation;
    }

    public static class ContactAndInvitationLiveData extends MediatorLiveData<ContactAndInvitation> {
        @Nullable Contact contact;
        @Nullable Invitation invitation;

        public ContactAndInvitationLiveData(LiveData<Contact> contactLiveData, LiveData<Invitation> invitationLiveData) {
            addSource(contactLiveData, this::updateContact);
            addSource(invitationLiveData, this::updateInvitation);
        }

        private void updateContact(Contact contact) {
            this.contact = contact;
            if (contact == null) {
                setValue(null);
            } else {
                setValue(new ContactAndInvitation(contact, invitation));
            }
        }

        private void updateInvitation(Invitation invitation) {
            this.invitation = invitation;
            if (contact == null) {
                setValue(null);
            } else {
                setValue(new ContactAndInvitation(contact, invitation));
            }
        }
    }

    public static class ContactAndInvitation {
        @NonNull public final Contact contact;
        @Nullable public final Invitation invitation;

        public ContactAndInvitation(@NonNull Contact contact, @Nullable Invitation invitation) {
            this.contact = contact;
            this.invitation = invitation;
        }
    }
}
