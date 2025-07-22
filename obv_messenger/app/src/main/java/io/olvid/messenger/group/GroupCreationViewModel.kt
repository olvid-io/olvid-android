/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.messenger.databases.entity.Contact

class GroupCreationViewModel : ViewModel() {
    var selectedContacts = mutableStateOf(emptyList<Contact>())
        set(selectedContacts) {
            field = selectedContacts
            selectedContactCount.postValue(selectedContacts.value.size)
            admins.postValue(
                admins.value?.apply {
                    retainAll(selectedContacts.value.toSet())
                }
            )
        }
    val selectedContactCount = MutableLiveData(0)
    val admins = MutableLiveData<HashSet<Contact>>(hashSetOf())
}

object GroupClone {
    var preselectedGroupMembers: List<Contact> = emptyList()
    var preselectedGroupAdminMembers: List<Contact> = emptyList()
    fun clear() {
        preselectedGroupMembers = emptyList()
        preselectedGroupAdminMembers = emptyList()
    }
}