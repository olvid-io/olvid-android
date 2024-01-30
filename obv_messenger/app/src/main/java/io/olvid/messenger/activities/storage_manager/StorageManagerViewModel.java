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

package io.olvid.messenger.activities.storage_manager;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.HashSet;
import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class StorageManagerViewModel extends ViewModel {
    SortOrder currentSortOrder = new SortOrder(SortKey.SIZE, false);
    final MutableLiveData<SortOrder> sortOrderMutableLiveData = new MutableLiveData<>(currentSortOrder);
    final OwnedIdentityAndSortOrderLiveData ownedIdentityAndSortOrderLiveData = new OwnedIdentityAndSortOrderLiveData(AppSingleton.getCurrentIdentityLiveData(), sortOrderMutableLiveData);
    final HashSet<FyleMessageJoinWithStatusDao.FyleAndStatus> selectedFyles = new HashSet<>();
    final MutableLiveData<Integer> selectedCountLiveData = new MutableLiveData<>(0);

    public void setSortOrder(SortOrder sortOrder) {
        currentSortOrder = sortOrder;
        sortOrderMutableLiveData.postValue(sortOrder);
    }

    public SortOrder getCurrentSortOrder() {
        return currentSortOrder;
    }

    public LiveData<Pair<OwnedIdentity, SortOrder>> getOwnedIdentityAndSortOrderLiveData() {
        return ownedIdentityAndSortOrderLiveData;
    }

    public void selectFyle(FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) {
        if (!selectedFyles.remove(fyleAndStatus)) {
            selectedFyles.add(fyleAndStatus);
        }
        selectedCountLiveData.postValue(selectedFyles.size());
    }

    public void clearSelectedFyles() {
        if (!selectedFyles.isEmpty()) {
            selectedFyles.clear();
            selectedCountLiveData.postValue(0);
        }
    }

    public boolean isSelected(FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) {
        return selectedFyles.contains(fyleAndStatus);
    }

    public boolean isSelecting() {
        return !selectedFyles.isEmpty();
    }

    public LiveData<Integer> getSelectedCountLiveData() {
        return selectedCountLiveData;
    }

    public void selectAllFyles(@NonNull List<FyleMessageJoinWithStatusDao.FyleAndOrigin> fyleAndOrigins) {
        for (FyleMessageJoinWithStatusDao.FyleAndOrigin fyleAndOrigin : fyleAndOrigins) {
            selectedFyles.add(fyleAndOrigin.fyleAndStatus);
        }
        selectedCountLiveData.postValue(fyleAndOrigins.size());
    }

    public enum SortKey {
        SIZE,
        DATE,
        NAME,
    }

    public static class SortOrder {
        public final SortKey sortKey;
        public final boolean ascending;

        public SortOrder(SortKey sortKey, boolean ascending) {
            this.sortKey = sortKey;
            this.ascending = ascending;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof SortOrder)) {
                return false;
            }
            SortOrder other = (SortOrder) obj;
            return (other.sortKey == sortKey) && (other.ascending == ascending);
        }
    }

    public static class OwnedIdentityAndSortOrderLiveData extends MediatorLiveData<Pair<OwnedIdentity, SortOrder>> {
        private OwnedIdentity ownedIdentity = null;
        private SortOrder sortOrder = null;

        public OwnedIdentityAndSortOrderLiveData(LiveData<OwnedIdentity> currentIdentityLiveData, LiveData<SortOrder> sortOrderLiveData) {
            addSource(currentIdentityLiveData, this::currentIdentityObserver);
            addSource(sortOrderLiveData, this::sortOrderObserver);
        }

        private void currentIdentityObserver(OwnedIdentity ownedIdentity) {
            this.ownedIdentity = ownedIdentity;
            setValue(new Pair<>(ownedIdentity, sortOrder));
        }

        private void sortOrderObserver(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
            setValue(new Pair<>(ownedIdentity, sortOrder));
        }
    }
}
