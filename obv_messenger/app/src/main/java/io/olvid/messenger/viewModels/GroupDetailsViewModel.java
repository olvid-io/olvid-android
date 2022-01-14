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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.ContactGroupJoinDao;
import io.olvid.messenger.databases.dao.PendingGroupMemberDao;
import io.olvid.messenger.databases.entity.Group;


public class GroupDetailsViewModel extends ViewModel {
    public byte[] bytesOwnedIdentity;
    public byte[] groupId;

    private LiveData<Group> group;
    private LiveData<List<ContactGroupJoinDao.ContactAndTimestamp>> groupMembers;
    private LiveData<List<PendingGroupMemberDao.PendingGroupMemberAndContact>> pendingGroupMembers;

    public void setGroup(byte[] bytesOwnedIdentity, byte[] groupId) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.groupId = groupId;
        this.group = AppDatabase.getInstance().groupDao().getLiveData(bytesOwnedIdentity, groupId);
        this.groupMembers = AppDatabase.getInstance().contactGroupJoinDao().getGroupContactsWithTimestamp(bytesOwnedIdentity, groupId);
        this.pendingGroupMembers = AppDatabase.getInstance().pendingGroupMemberDao().getGroupPendingMemberAndContactsLiveData(bytesOwnedIdentity, groupId);
    }

    public LiveData<Group> getGroup() {
        return group;
    }

    public LiveData<List<ContactGroupJoinDao.ContactAndTimestamp>> getGroupMembers() {
        return groupMembers;
    }

    public LiveData<List<PendingGroupMemberDao.PendingGroupMemberAndContact>> getPendingGroupMembers() {
        return pendingGroupMembers;
    }
}
