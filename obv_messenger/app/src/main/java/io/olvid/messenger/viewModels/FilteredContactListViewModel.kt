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
package io.olvid.messenger.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.messenger.App
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.entity.Contact
import java.util.regex.Pattern

class FilteredContactListViewModel : ViewModel() {
    val filteredContacts = MutableLiveData<List<SelectableContact>?>()
    val selectedContacts = MutableLiveData<List<Contact>>(emptyList())
    private var unfilteredContacts: List<Contact>? = null
    var filter by mutableStateOf<String?>(null)
        private set
    var filterPatterns: MutableList<Pattern>? = null
        private set
    private val selectedContactsHashSet = LinkedHashSet<Contact>()


    fun setUnfilteredContacts(unfilteredContacts: List<Contact>?) {
        this.unfilteredContacts = unfilteredContacts
        setSearchFilter(filter)
    }

    fun setSearchFilter(filter: String?) {
        this.filter = filter
        if (filter == null) {
            filterPatterns = null
        } else {
            val parts =
                filter.trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            filterPatterns = ArrayList<Pattern>(parts.size).apply {
                for (part in parts) {
                    if (part.isNotEmpty()) {
                        add(Pattern.compile(Pattern.quote(StringUtils.unAccent(part))))
                    }
                }
            }

        }
        if (unfilteredContacts != null) {
            App.runThread(
                FilterContactListTask(
                    filterPatterns,
                    filteredContacts,
                    unfilteredContacts,
                    selectedContactsHashSet
                )
            )
        }
    }

    fun selectContact(contact: Contact) {
        if (!selectedContactsHashSet.remove(contact)) {
            selectedContactsHashSet.add(contact)
        }
        selectedContacts.postValue(ArrayList(selectedContactsHashSet))
        refreshSelectedContacts()
    }

    fun setSelectedContacts(selectedContacts: List<Contact>) {
        selectedContactsHashSet.clear()
        selectedContactsHashSet.addAll(selectedContacts)
        this.selectedContacts.postValue(selectedContacts)
        refreshSelectedContacts()
    }

    private fun refreshSelectedContacts() {
        val filteredContacts = filteredContacts.value
        if (filteredContacts != null) {
            val updatedList: MutableList<SelectableContact> = ArrayList(filteredContacts.size)
            for (filteredContact in filteredContacts) {
                updatedList.add(
                    SelectableContact(
                        filteredContact.contact,
                        selectedContactsHashSet.contains(filteredContact.contact)
                    )
                )
            }
            this.filteredContacts.postValue(updatedList)
        }
    }


    private class FilterContactListTask(
        filterPatterns: List<Pattern>?,
        filteredContacts: MutableLiveData<List<SelectableContact>?>,
        unfilteredContacts: List<Contact>?,
        selectedContactsHashSet: HashSet<Contact>
    ) :
        Runnable {
        private var filterPatterns: List<Pattern>? = null
        private val filteredContacts: MutableLiveData<List<SelectableContact>?>
        private val unfilteredContacts: List<Contact?>?
        private val selectedContactsHashSet: HashSet<Contact>


        init {
            if (filterPatterns == null) {
                this.filterPatterns = ArrayList(0)
            } else {
                this.filterPatterns = filterPatterns
            }
            this.filteredContacts = filteredContacts
            this.unfilteredContacts = unfilteredContacts
            this.selectedContactsHashSet =
                HashSet(selectedContactsHashSet) // copy the set to avoid concurrent modification
        }

        override fun run() {
            if (unfilteredContacts == null) {
                filteredContacts.postValue(null)
                return
            }
            val list: MutableList<SelectableContact> = ArrayList()

            for (contact in unfilteredContacts) {
                var matches = true
                for (pattern in filterPatterns!!) {
                    val matcher = pattern.matcher(contact?.fullSearchDisplayName.orEmpty())
                    if (!matcher.find()) {
                        matches = false
                        break
                    }
                }
                if (matches && contact != null) {
                    list.add(SelectableContact(contact, selectedContactsHashSet.contains(contact)))
                }
            }
            val filter = filterPatterns!!.firstOrNull()?.toString()?.removePrefix("\\Q")?.removeSuffix("\\E")
            filter?.let {
                list.sortByDescending { contact -> 1 /
                        StringUtils.unAccent(contact.contact.customDisplayName.orEmpty() + " " + contact.contact.displayName).trim().split(
                        " "
                    ).indexOfFirst { it.startsWith(filter) }.toFloat()
                }
            }
            filteredContacts.postValue(list)
        }
    }

    class SelectableContact(@JvmField val contact: Contact, @JvmField val selected: Boolean)
}
