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

package io.olvid.messenger.main;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.GroupDao;
import io.olvid.messenger.databases.entity.OwnedIdentity;


public class GroupListViewModel extends ViewModel {
    private final LiveData<List<GroupDao.GroupAndContactDisplayNames>> groups;

    public GroupListViewModel() {
        final String joiner = App.getContext().getString(R.string.text_contact_names_separator);
        groups = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().groupDao().getAllOwnedThenJoined(ownedIdentity.bytesOwnedIdentity, joiner);
        });
    }

    public LiveData<List<GroupDao.GroupAndContactDisplayNames>> getGroups() {
        return groups;
    }
}
