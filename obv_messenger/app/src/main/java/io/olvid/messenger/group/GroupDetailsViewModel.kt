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
package io.olvid.messenger.group

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.ContactGroupJoinDao.ContactAndTimestamp
import io.olvid.messenger.databases.dao.PendingGroupMemberDao.PendingGroupMemberAndContact
import io.olvid.messenger.databases.entity.Group

class GroupDetailsViewModel : ViewModel() {
    var bytesOwnedIdentity: ByteArray? = null
    private var groupId: ByteArray? = null
    var group: LiveData<Group?>? = null
        private set
    var groupMembers: LiveData<List<ContactAndTimestamp>?>? = null
        private set
    var pendingGroupMembers: LiveData<List<PendingGroupMemberAndContact>?>? = null
        private set

    fun setGroup(bytesOwnedIdentity: ByteArray, groupId: ByteArray) {
        this.bytesOwnedIdentity = bytesOwnedIdentity
        this.groupId = groupId
        group = AppDatabase.getInstance().groupDao().getLiveData(bytesOwnedIdentity, groupId)
        groupMembers = AppDatabase.getInstance().contactGroupJoinDao()
            .getGroupContactsWithTimestamp(bytesOwnedIdentity, groupId)
        pendingGroupMembers = AppDatabase.getInstance().pendingGroupMemberDao()
            .getGroupPendingMemberAndContactsLiveData(bytesOwnedIdentity, groupId)
    }
}