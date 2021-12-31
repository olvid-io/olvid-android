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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.olvid.messenger.databases.entity.Contact;


public class GroupCreationViewModel extends ViewModel {
    private final MutableLiveData<List<Contact>> selectedContacts = new MutableLiveData<>();

    public LiveData<List<Contact>> getSelectedContacts() {
        return selectedContacts;
    }

    public void setSelectedContacts(List<Contact> selectedContacts) {
        this.selectedContacts.postValue(selectedContacts);
    }
}
