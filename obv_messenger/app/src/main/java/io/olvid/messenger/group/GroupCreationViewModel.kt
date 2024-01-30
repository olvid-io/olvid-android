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

import android.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.messenger.databases.entity.Contact

class GroupCreationViewModel : ViewModel() {
    var selectedContacts: List<Contact>? = null
        set(selectedContacts) {
            field = selectedContacts
            if (selectedContacts == null) {
                selectedContactCount.postValue(0)
            } else {
                selectedContactCount.postValue(selectedContacts.size)
            }
            admins.postValue(
                admins.value?.apply {
                    retainAll((selectedContacts ?: emptyList()).toSet())
                }
            )
        }
    val selectedContactCount = MutableLiveData<Int>(0)
    private val customGroup = MutableLiveData(false)
    val chooseAdmins = MutableLiveData(false)
    val admins = MutableLiveData<HashSet<Contact>>(hashSetOf())

    private val selectedTab = MutableLiveData<Int>()
    val subtitleLiveData = SubtitleLiveData(selectedContactCount, selectedTab)

    fun isCustomGroup(): LiveData<Boolean> {
        return customGroup
    }

    fun setIsCustomGroup(custom: Boolean) {
        customGroup.postValue(custom)
    }
    fun setSelectedTab(selectedTab: Int) {
        this.selectedTab.postValue(selectedTab)
    }

    class SubtitleLiveData(
        selectedContactCount: MutableLiveData<Int>,
        selectedTab: MutableLiveData<Int>
    ) : MediatorLiveData<Pair<Int?, Int?>?>() {
        private var selectedContactCount = 0
        private var selectedTab = GroupCreationActivity.CONTACTS_SELECTION_TAB

        init {
            addSource(selectedContactCount, ::selectedContactCountChanged)
            addSource(selectedTab, ::selectedTabChanged)
        }

        private fun selectedContactCountChanged(selectedContactCount: Int?) {
            this.selectedContactCount = selectedContactCount ?: 0
            postValue(Pair(selectedTab, this.selectedContactCount))
        }

        private fun selectedTabChanged(selectedTab: Int?) {
            this.selectedTab = selectedTab ?: GroupCreationActivity.CONTACTS_SELECTION_TAB
            postValue(Pair(this.selectedTab, selectedContactCount))
        }
    }
}