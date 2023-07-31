/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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
import androidx.lifecycle.switchMap
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.settings.SettingsActivity

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

    // ephemeral settings
    var settingsReadOnce = SettingsActivity.getDefaultDiscussionReadOnce()
    var settingsVisibilityDuration: Long? =
        SettingsActivity.getDefaultDiscussionVisibilityDuration()
    var settingsExistenceDuration: Long? = SettingsActivity.getDefaultDiscussionExistenceDuration()

    private val selectedTab = MutableLiveData<Int>()
    val subtitleLiveData = SubtitleLiveData(selectedContactCount, selectedTab)
    private val searchOpenedLiveData = MutableLiveData(false)
    val showGroupV2WarningLiveData = ShowGroupV2WarningLiveData(
        searchOpenedLiveData,
        AppSingleton.getCurrentIdentityLiveData().switchMap { currentOwnedIdentity: OwnedIdentity ->
            AppDatabase.getInstance().contactDao()
                .nonGroupV2ContactExists(currentOwnedIdentity.bytesOwnedIdentity)
        })

    fun isCustomGroup(): LiveData<Boolean> {
        return customGroup
    }

    fun setIsCustomGroup(custom: Boolean) {
        customGroup.postValue(custom)
    }
    fun setSelectedTab(selectedTab: Int) {
        this.selectedTab.postValue(selectedTab)
    }

    fun setSearchOpened(opened: Boolean) {
        searchOpenedLiveData.postValue(opened)
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

    class ShowGroupV2WarningLiveData(
        searchOpenedLiveData: LiveData<Boolean>,
        nonGroupV2ContactLiveData: LiveData<Boolean>
    ) : MediatorLiveData<Boolean?>() {
        private var searchOpened = false
        private var nonGroupV2Contact = false

        init {
            addSource(
                searchOpenedLiveData, ::searchOpenedChanged
            )
            addSource(
                nonGroupV2ContactLiveData, ::nonGroupV2ContactChanged
            )
        }

        private fun searchOpenedChanged(searchOpened: Boolean?) {
            this.searchOpened = searchOpened != null && searchOpened
            postValue(nonGroupV2Contact && !this.searchOpened)
        }

        private fun nonGroupV2ContactChanged(nonGroupV2Contact: Boolean?) {
            this.nonGroupV2Contact = nonGroupV2Contact != null && nonGroupV2Contact
            postValue(this.nonGroupV2Contact && !searchOpened)
        }
    }
}