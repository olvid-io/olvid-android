/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.Contact;


public class ContactDetailsViewModel extends ViewModel {
    private byte[] contactBytesIdentity;
    private byte[] contactBytesOwnedIdentity;
    private LiveData<Contact> contact;
    private LiveData<List<DiscussionDao.DiscussionAndContactDisplayNames>> groupDiscussions;

    public void setContactBytes(byte[] contactBytesIdentity, byte[] contactBytesOwnedIdentity) {
        this.contactBytesIdentity = contactBytesIdentity;
        this.contactBytesOwnedIdentity = contactBytesOwnedIdentity;
        this.contact = AppDatabase.getInstance().contactDao().getAsync(contactBytesOwnedIdentity, contactBytesIdentity);
        this.groupDiscussions = AppDatabase.getInstance().discussionDao().getContactActiveGroupDiscussionsWithContactNames(contactBytesIdentity, contactBytesOwnedIdentity, App.getContext().getString(R.string.text_contact_names_separator));
    }

    public LiveData<Contact> getContact() {
        return contact;
    }

    public LiveData<List<DiscussionDao.DiscussionAndContactDisplayNames>> getGroupDiscussions() {
        return groupDiscussions;
    }
}
