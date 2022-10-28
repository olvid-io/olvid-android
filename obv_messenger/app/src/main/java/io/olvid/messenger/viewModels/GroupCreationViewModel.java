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

import android.util.Pair;

import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.GroupCreationActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;


public class GroupCreationViewModel extends ViewModel {
    private List<Contact> selectedContacts = null;
    private final MutableLiveData<Integer> selectedContactCount = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedTab = new MutableLiveData<>();
    private final SubtitleLiveData subtitleLiveData = new SubtitleLiveData(selectedContactCount, selectedTab);
    private final MutableLiveData<Boolean> searchOpenedLiveData = new MutableLiveData<>(false);
    private final ShowGroupV2WarningLiveData showGroupV2WarningLiveData = new ShowGroupV2WarningLiveData(searchOpenedLiveData, Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity currentOwnedIdentity) -> AppDatabase.getInstance().contactDao().nonGroupV2ContactExists(currentOwnedIdentity.bytesOwnedIdentity)));

    public List<Contact> getSelectedContacts() {
        return selectedContacts;
    }

    public void setSelectedContacts(List<Contact> selectedContacts) {
        this.selectedContacts = selectedContacts;
        if (selectedContacts == null) {
            selectedContactCount.postValue(0);
        } else {
            selectedContactCount.postValue(selectedContacts.size());
        }
    }

    public void setSelectedTab(int selectedTab) {
        this.selectedTab.postValue(selectedTab);
    }

    public SubtitleLiveData getSubtitleLiveData() {
        return subtitleLiveData;
    }

    public void setSearchOpened(boolean opened) {
        searchOpenedLiveData.postValue(opened);
    }

    public LiveData<Boolean> getShowGroupV2WarningLiveData() {
        return showGroupV2WarningLiveData;
    }

    public static class SubtitleLiveData extends MediatorLiveData<Pair<Integer, Integer>> {
        int selectedContactCount = 0;
        int selectedTab = GroupCreationActivity.CONTACTS_SELECTION_TAB;

        public SubtitleLiveData(MutableLiveData<Integer> selectedContactCount, MutableLiveData<Integer> selectedTab) {
            addSource(selectedContactCount, this::selectedContactCountChanged);
            addSource(selectedTab, this::selectedTabChanged);
        }

        private void selectedContactCountChanged(Integer selectedContactCount) {
            this.selectedContactCount = selectedContactCount == null ? 0 : selectedContactCount;
            postValue(new Pair<>(this.selectedTab, this.selectedContactCount));
        }

        private void selectedTabChanged(Integer selectedTab) {
            this.selectedTab = selectedTab == null ? GroupCreationActivity.CONTACTS_SELECTION_TAB : selectedTab;
            postValue(new Pair<>(this.selectedTab, this.selectedContactCount));
        }
    }

    public static class ShowGroupV2WarningLiveData extends MediatorLiveData<Boolean> {
        boolean searchOpened = false;
        boolean nonGroupV2Contact = false;

        public ShowGroupV2WarningLiveData(LiveData<Boolean> searchOpenedLiveData, LiveData<Boolean> nonGroupV2ContactLiveData) {
            addSource(searchOpenedLiveData, this::searchOpenedChanged);
            addSource(nonGroupV2ContactLiveData, this::nonGroupV2ContactChanged);
        }

        private void searchOpenedChanged(Boolean searchOpened) {
            this.searchOpened = searchOpened != null && searchOpened;
            postValue(this.nonGroupV2Contact && !this.searchOpened);
        }

        private void nonGroupV2ContactChanged(Boolean nonGroupV2Contact) {
            this.nonGroupV2Contact = nonGroupV2Contact != null && nonGroupV2Contact;
            postValue(this.nonGroupV2Contact && !this.searchOpened);
        }
    }
}
