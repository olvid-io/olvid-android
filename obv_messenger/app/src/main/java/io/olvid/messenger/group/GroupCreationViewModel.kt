/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExistingDiscussion(
    val discussion: Discussion,
    val lastMessage: Message?,
)

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
    val existingDiscussions = mutableStateOf(emptyList<ExistingDiscussion>())

    fun searchExistingDiscussions(onResult: (List<ExistingDiscussion>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val matches = runCatching { findExistingDiscussions() }.getOrDefault(emptyList())
            existingDiscussions.value = matches
            withContext(Dispatchers.Main) {
                onResult(matches)
            }
        }
    }

    // find discussions of (v1 or v2) groups whose members (including pending ones) are exactly the selected contacts
    private fun findExistingDiscussions(): List<ExistingDiscussion> {
        val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity() ?: return emptyList()
        val selectedIdentities =
            selectedContacts.value.map { BytesKey(it.bytesContactIdentity) }.toHashSet()
        val bytesFirstContactIdentity = selectedIdentities.firstOrNull()?.bytes ?: return emptyList()

        val db = AppDatabase.getInstance()
        val matches = mutableListOf<ExistingDiscussion>()
        db.group2Dao().getAllForContact(bytesOwnedIdentity, bytesFirstContactIdentity).forEach { group ->
            val memberIdentities = db.group2MemberDao()
                .getGroupMembersAndPendingSync(bytesOwnedIdentity, group.bytesGroupIdentifier)
                .map { BytesKey(it.bytesContactIdentity) }
                .toHashSet()
            if (memberIdentities == selectedIdentities) {
                db.discussionDao()
                    .getByGroupIdentifier(bytesOwnedIdentity, group.bytesGroupIdentifier)
                    ?.let {
                        matches.add(
                            ExistingDiscussion(
                                discussion = it,
                                lastMessage = db.messageDao().getLastDiscussionMessage(it.id)
                            )
                        )
                    }
            }
        }
        db.groupDao().getAllForContact(bytesOwnedIdentity, bytesFirstContactIdentity).forEach { group ->
            val memberIdentities = (db.contactGroupJoinDao()
                .getGroupContactsSync(bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                .map { BytesKey(it.bytesContactIdentity) }
                    + db.pendingGroupMemberDao()
                .getGroupPendingMembers(bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                .map { BytesKey(it.bytesIdentity) })
                .toHashSet()
            if (memberIdentities == selectedIdentities) {
                db.discussionDao()
                    .getByGroupOwnerAndUid(bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                    ?.let {
                        matches.add(
                            ExistingDiscussion(
                                discussion = it,
                                lastMessage = db.messageDao().getLastDiscussionMessage(it.id)
                            )
                        )
                    }
            }
        }
        return matches.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
    }
}

object GroupClone {
    var preselectedGroupMembers: List<Contact> = emptyList()
    var preselectedGroupAdminMembers: List<Contact> = emptyList()
    fun clear() {
        preselectedGroupMembers = emptyList()
        preselectedGroupAdminMembers = emptyList()
    }
}
