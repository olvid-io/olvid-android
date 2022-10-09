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

import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.olvid.messenger.activities.GroupCreationActivity;
import io.olvid.messenger.databases.entity.Contact;


public class GroupCreationViewModel extends ViewModel {
    private List<Contact> selectedContacts = null;
    private final MutableLiveData<Integer> selectedContactCount = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedTab = new MutableLiveData<>();
    private final SubtitleLiveData subtitleLiveData = new SubtitleLiveData(selectedContactCount, selectedTab);

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
}
