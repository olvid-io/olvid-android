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
package io.olvid.messenger.group.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.jsonIdentityDetails
import io.olvid.messenger.databases.dao.Group2MemberDao.Group2MemberOrPending
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class GroupMember(
    val bytesIdentity: ByteArray,
    val contact: Contact?,
    val jsonIdentityDetails: JsonIdentityDetails?,
    val fullSearchDisplayName: String,
    val pending: Boolean,
    val isAdmin: Boolean,
    val isYou: Boolean = false,
    val removableFromRow: Boolean = true,
    var selected: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupMember) return false

        if (pending != other.pending) return false
        if (isAdmin != other.isAdmin) return false
        if (removableFromRow != other.removableFromRow) return false
        if (selected != other.selected) return false
        if (!bytesIdentity.contentEquals(other.bytesIdentity)) return false
        if (contact != other.contact) return false
        if (jsonIdentityDetails != other.jsonIdentityDetails) return false
        if (fullSearchDisplayName != other.fullSearchDisplayName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pending.hashCode()
        result = 31 * result + isAdmin.hashCode()
        result = 31 * result + removableFromRow.hashCode()
        result = 31 * result + selected.hashCode()
        result = 31 * result + bytesIdentity.contentHashCode()
        result = 31 * result + (contact?.hashCode() ?: 0)
        result = 31 * result + (jsonIdentityDetails?.hashCode() ?: 0)
        result = 31 * result + fullSearchDisplayName.hashCode()
        return result
    }
}

class GroupMembersViewModel : ViewModel() {
    var filteredMembers by mutableStateOf(emptyList<GroupMember>())
        private set
    var allMembers by mutableStateOf(emptyList<GroupMember>())
        private set
    var selectedMembers by mutableStateOf<Set<BytesKey>?>(null)
        private set

    var currentFilter by mutableStateOf<String?>(null)
        private set
    var selectAllFilterText by mutableStateOf(false)
    var filterPatterns: List<Pattern> by mutableStateOf(emptyList())
        private set

    fun setSearchFilter(filter: String?) {
        currentFilter = filter

        filterPatterns = if (filter.isNullOrBlank()) {
            emptyList()
        } else {
            filter.trim().split("\\s+".toRegex()).mapNotNull { part ->
                if (part.isNotEmpty()) {
                    Pattern.compile(Pattern.quote(StringUtils.unAccent(part)))
                } else {
                    null
                }
            }
        }

        filterMembers()
    }

    private fun filterMembers() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = if (filterPatterns.isEmpty()) {
                allMembers
            } else {
                allMembers.filter { member ->
                    filterPatterns.all { pattern ->
                        pattern.matcher(
                            member.fullSearchDisplayName
                        ).find()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                filteredMembers = result
            }
        }
    }

    fun toggleMemberSelection(memberBytes: ByteArray) {
        val key = BytesKey(memberBytes)
        val currentSelection =
            selectedMembers ?: allMembers.filter { it.selected }.map { BytesKey(it.bytesIdentity) }
                .toSet()

        selectedMembers = if (currentSelection.contains(key)) {
            currentSelection - key
        } else {
            currentSelection + key
        }
        updateMembersSelection()
    }

    private fun updateMembersSelection() {
        val selection = selectedMembers ?: return
        allMembers = allMembers.map {
            it.copy(selected = selection.contains(BytesKey(it.bytesIdentity)))
        }
        selectAllFilterText = true
        filterMembers()
    }

    fun setMembers(members: List<GroupMember>) {
        val selection = selectedMembers
        allMembers = if (selection == null) {
            members
        } else {
            members.map {
                it.copy(selected = selection.contains(BytesKey(it.bytesIdentity)))
            }
        }
        filterMembers()
    }

    fun setSelectedMembers(selectedMembersIdentities: List<ByteArray>?, onlyOnce: Boolean = false) {
        if (onlyOnce && selectedMembers != null) return
        selectedMembers = selectedMembersIdentities?.map { BytesKey(it) }?.toSet()
        updateMembersSelection()
    }
}

// Extensions
fun Group2MemberOrPending.toGroupMember(
    selected: Boolean = false,
    removableFromRow: Boolean = false
): GroupMember {
    return GroupMember(
        bytesIdentity = bytesContactIdentity,
        contact = contact,
        jsonIdentityDetails = identityDetails?.jsonIdentityDetails(),
        fullSearchDisplayName = contact?.fullSearchDisplayName
            ?: StringUtils.unAccent(
                identityDetails.jsonIdentityDetails()
                    ?.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FOR_SEARCH, false)
                    .orEmpty()
            ),
        pending = pending,
        isAdmin = permissionAdmin,
        removableFromRow = removableFromRow,
        selected = selected
    )
}

fun Contact.toGroupMember(selected: Boolean = false): GroupMember {
    return GroupMember(
        bytesIdentity = bytesContactIdentity,
        contact = this,
        isAdmin = false,
        fullSearchDisplayName = fullSearchDisplayName,
        jsonIdentityDetails = identityDetails?.jsonIdentityDetails(),
        pending = false,
        selected = selected
    )
}

fun GroupMember.getDisplayName(context: Context): String {
    return contact?.customDisplayName ?: jsonIdentityDetails?.formatFirstAndLastName(
        SettingsActivity.contactDisplayNameFormat,
        SettingsActivity.uppercaseLastName
    ) ?: context.getString(R.string.text_unable_to_display_contact_name)
}