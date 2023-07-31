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
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.olvid.engine.datatypes.containers.GroupV2.Permission
import io.olvid.engine.engine.types.JsonGroupType
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.engine.engine.types.identities.ObvGroupV2.ObvGroupV2ChangeSet
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.Group2MemberDao.Group2MemberOrPending
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.group.GroupTypeModel.CustomGroup
import io.olvid.messenger.group.GroupTypeModel.PrivateGroup
import io.olvid.messenger.group.GroupTypeModel.ReadOnlyGroup
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting.ADMINS
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting.EVERYONE
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting.NOBODY
import io.olvid.messenger.group.GroupTypeModel.SimpleGroup
import kotlin.math.min

class GroupV2DetailsViewModel : ViewModel() {
    var bytesOwnedIdentity: ByteArray? = null
        private set
    var bytesGroupIdentifier: ByteArray? = null
        private set
    private val dbGroupMembers: HashMap<BytesKey, Group2MemberOrPending> = HashMap()
    private var editingGroupMembers: Boolean
    private val editingGroupMembersLiveData: MutableLiveData<Boolean?>
    val isEditingGroupCustomSettingsLiveData: MutableLiveData<Boolean>
    private var publishingGroupMembers: Boolean
    val changeSet: ChangeSet
    private val changeSetLiveData: MutableLiveData<ChangeSet>
    private val bytesOwnedIdentityAndGroupIdentifierLiveData: MutableLiveData<Pair<ByteArray, ByteArray>?>
    val group: LiveData<Group2?>
    private val editedGroupMembersLiveData: EditedGroupMembersLiveData
    var membersCount = 0
    var membersAndPendingCount = 0

    var initialGroupType: GroupTypeModel? = null
    var oldCustomGroupType: CustomGroup? = null
    private val groupType = MutableLiveData<GroupTypeModel?>()

    fun updateReadOnly(readOnly: Boolean) {
        groupType.value = groupType.value?.clone()?.apply { readOnlySetting = readOnly }
    }

    fun updateRemoteDelete(remoteDelete: RemoteDeleteSetting) {
        groupType.value = groupType.value?.clone()?.apply { remoteDeleteSetting = remoteDelete }
    }

    init {
        changeSet = ChangeSet()
        editingGroupMembers = false
        editingGroupMembersLiveData = MutableLiveData(false)
        isEditingGroupCustomSettingsLiveData = MutableLiveData(false)
        publishingGroupMembers = false
        changeSetLiveData = MutableLiveData(changeSet)
        bytesOwnedIdentityAndGroupIdentifierLiveData = MutableLiveData(null)
        group =
            bytesOwnedIdentityAndGroupIdentifierLiveData.switchMap { bytesOwnedIdentityAndGroupIdentifier: Pair<ByteArray, ByteArray>? ->
                if (bytesOwnedIdentityAndGroupIdentifier == null) {
                    return@switchMap null
                }
                AppDatabase.getInstance().group2Dao().getLiveData(
                    bytesOwnedIdentityAndGroupIdentifier.first,
                    bytesOwnedIdentityAndGroupIdentifier.second
                )
            }
        // we add a map around the switch map to be able to update our dbGroupMembers HashMap. As long as someone is observing groupMembers, it will be updated
        //  check for members added both locally and externally
        //  check for already removed members
        val groupMembers =
            bytesOwnedIdentityAndGroupIdentifierLiveData.switchMap { bytesOwnedIdentityAndGroupIdentifier: Pair<ByteArray, ByteArray>? ->
                if (bytesOwnedIdentityAndGroupIdentifier == null) {
                    return@switchMap null
                }
                AppDatabase.getInstance().group2MemberDao().getGroupMembersAndPending(
                    bytesOwnedIdentityAndGroupIdentifier.first,
                    bytesOwnedIdentityAndGroupIdentifier.second
                )
            }.map { group2MemberOrPendings: List<Group2MemberOrPending>? ->
                synchronized(changeSet) {
                    dbGroupMembers.clear()
                    if (group2MemberOrPendings != null) {
                        membersAndPendingCount = group2MemberOrPendings.size
                        membersCount = 0
                        for (group2MemberOrPending in group2MemberOrPendings) {
                            if (!group2MemberOrPending.pending) {
                                membersCount++
                            }
                            val key = BytesKey(group2MemberOrPending.bytesContactIdentity)
                            dbGroupMembers[key] = group2MemberOrPending
                            // check for members added both locally and externally
                            val addedMember = changeSet.membersAdded.remove(key)
                            if (addedMember != null && addedMember.permissionAdmin && !group2MemberOrPending.permissionAdmin) {
                                changeSet.adminChanges[key] = true
                            }
                        }
                    }
                    // check for already removed members
                    for (key in HashSet(changeSet.membersRemoved)) {
                        if (!dbGroupMembers.containsKey(key)) {
                            changeSet.membersRemoved.remove(key)
                        }
                    }
                }
                group2MemberOrPendings.orEmpty()
            }
        editedGroupMembersLiveData = EditedGroupMembersLiveData(groupMembers, changeSetLiveData, editingGroupMembersLiveData)
    }

    fun setGroup(bytesOwnedIdentity: ByteArray?, bytesGroupIdentifier: ByteArray?) {
        this.bytesOwnedIdentity = bytesOwnedIdentity
        this.bytesGroupIdentifier = bytesGroupIdentifier
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            bytesOwnedIdentityAndGroupIdentifierLiveData.postValue(null)
        } else {
            bytesOwnedIdentityAndGroupIdentifierLiveData.postValue(
                Pair(
                    bytesOwnedIdentity,
                    bytesGroupIdentifier
                )
            )
        }
    }

    fun setGroupType(value: GroupTypeModel) {
        groupType.value = value
    }

    fun getGroupTypeLiveData(): LiveData<GroupTypeModel?> {
        return groupType
    }

    fun inferGroupType(members: List<Group2MemberOrPending>): GroupTypeModel {
        if (members.all { it.permissionAdmin }) {
            // probably a SimpleGroup
            return SimpleGroup
        } else if (members.none { it.permissionAdmin } && members.all { it.permissionSendMessage }) {
            // probably a private group
            return PrivateGroup
        }

        val readOnly = members.filter { it.permissionAdmin.not() }.all { it.permissionSendMessage.not() }

        val remoteDelete =
            members.filter { it.permissionRemoteDeleteAnything }.let { remoteDeleteMembers ->
                when {
                    remoteDeleteMembers.isEmpty() -> NOBODY

                    remoteDeleteMembers.size == members.size -> EVERYONE

                    else -> ADMINS
                }
            }
        return CustomGroup(
            readOnlySetting = readOnly,
            remoteDeleteSetting = remoteDelete
        )
    }

    fun getPermissions(
        groupType: GroupTypeModel,
        isAdmin: Boolean
    ): java.util.HashSet<Permission> {
        return when (groupType) {
            SimpleGroup -> Permission.DEFAULT_ADMIN_PERMISSIONS.toHashSet()
            ReadOnlyGroup -> if (isAdmin) Permission.DEFAULT_ADMIN_PERMISSIONS.toHashSet() else hashSetOf()
            is CustomGroup -> {
                if (isAdmin) {
                    Permission.DEFAULT_ADMIN_PERMISSIONS.toHashSet()
                } else {
                    if (groupType.readOnlySetting) {
                        HashSet()
                    } else {
                        Permission.DEFAULT_MEMBER_PERMISSIONS.toHashSet()
                    }
                }
            }
            else -> if (isAdmin) Permission.DEFAULT_ADMIN_PERMISSIONS.toHashSet() else Permission.DEFAULT_MEMBER_PERMISSIONS.toHashSet()
        }.apply {
            if (groupType is CustomGroup) {// REMOTE_DELETE_ANYTHING
                when (groupType.remoteDeleteSetting) {
                    ADMINS ->
                        if (isAdmin) {
                            add(Permission.REMOTE_DELETE_ANYTHING)
                        } else {
                            remove(Permission.REMOTE_DELETE_ANYTHING)
                        }
                    NOBODY -> remove(Permission.REMOTE_DELETE_ANYTHING)
                    EVERYONE -> add(Permission.REMOTE_DELETE_ANYTHING)
                }
            }
        }
    }

    fun isEditingGroupMembersLiveData(): LiveData<Boolean?> {
        return editingGroupMembersLiveData
    }

    val groupMembers: LiveData<List<Group2MemberOrPending>?>
        get() = editedGroupMembersLiveData

    fun permissionChanged(bytesContactIdentity: ByteArray?, isAdmin: Boolean) {
        if (!editingGroupMembers || publishingGroupMembers) {
            return
        }
        synchronized(changeSet) {
            val key = BytesKey(bytesContactIdentity)
            val groupMember = dbGroupMembers[key]
            if (groupMember != null) {
                if (groupMember.permissionAdmin xor isAdmin) {
                    changeSet.adminChanges[key] = isAdmin
                } else {
                    changeSet.adminChanges.remove(key)
                }
            }
            val addedMember = changeSet.membersAdded[key]
            if (addedMember != null) {
                addedMember.permissionAdmin = isAdmin
            }
        }
        changeSetLiveData.postValue(changeSet)
    }

    fun memberRemoved(bytesContactIdentity: ByteArray?) {
        if (!editingGroupMembers || publishingGroupMembers) {
            return
        }
        synchronized(changeSet) {
            val key = BytesKey(bytesContactIdentity)
            if (dbGroupMembers.containsKey(key)) {
                changeSet.membersRemoved.add(key)
            } else {
                changeSet.membersAdded.remove(key)
            }
            changeSet.adminChanges.remove(key)
        }
        changeSetLiveData.postValue(changeSet)
    }

    fun membersAdded(contacts: List<Contact>) {
        if (!editingGroupMembers || publishingGroupMembers) {
            return
        }
        synchronized(changeSet) {
            for (contact in contacts) {
                val key = BytesKey(contact.bytesContactIdentity)
                if (!dbGroupMembers.containsKey(key) && !changeSet.membersAdded.containsKey(key)) {
                    val memberOrPending = Group2MemberOrPending()
                    memberOrPending.contact = contact
                    memberOrPending.bytesContactIdentity = contact.bytesContactIdentity
                    memberOrPending.sortDisplayName = contact.sortDisplayName
                    memberOrPending.identityDetails = contact.identityDetails
                    memberOrPending.permissionAdmin = false
                    getGroupTypeLiveData().value?.let {
                        with(getPermissions(groupType = it, isAdmin = false)) {
                            memberOrPending.permissionAdmin = contains(Permission.GROUP_ADMIN)
                            memberOrPending.permissionChangeSettings = contains(Permission.CHANGE_SETTINGS)
                            memberOrPending.permissionRemoteDeleteAnything = contains(Permission.REMOTE_DELETE_ANYTHING)
                            memberOrPending.permissionEditOrRemoteDeleteOwnMessages = contains(Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES)
                            memberOrPending.permissionSendMessage = contains(Permission.SEND_MESSAGE)
                        }
                    }

                    changeSet.membersAdded[key] = memberOrPending
                }
                changeSet.membersRemoved.remove(key)
            }
        }
        changeSetLiveData.postValue(changeSet)
    }

    fun startEditingMembers() {
        if (editingGroupMembers || publishingGroupMembers) {
            return
        }
        editingGroupMembers = true
        editingGroupMembersLiveData.postValue(true)
    }

    fun discardGroupEdits(): Boolean {
        if (!editingGroupMembers || publishingGroupMembers) {
            return false
        }
        synchronized(changeSet) {
            changeSet.adminChanges.clear()
            changeSet.membersAdded.clear()
            changeSet.membersRemoved.clear()
            changeSet.jsonGroupType = null
        }
        changeSetLiveData.postValue(changeSet)
        editingGroupMembers = false
        editingGroupMembersLiveData.postValue(false)
        return true
    }

    fun createGroupeTypeChangeSet(jsonGroupType : JsonGroupType?) {
        synchronized(changeSet) {
            changeSet.adminChanges.clear()
            changeSet.membersAdded.clear()
            changeSet.membersRemoved.clear()
            changeSet.jsonGroupType = jsonGroupType
        }
        changeSetLiveData.postValue(changeSet)
    }

    fun getObvChangeSet() : ObvGroupV2ChangeSet {
        val obvChangeSet = ObvGroupV2ChangeSet()
        var adminPermissions: HashSet<Permission>
        var memberPermissions: HashSet<Permission>

        synchronized(changeSet) {
            val groupType = changeSet.jsonGroupType?.toGroupCreationModel() ?: initialGroupType ?: CustomGroup()
            adminPermissions = getPermissions(groupType = groupType, true)
            memberPermissions = getPermissions(groupType = groupType, false)

            // added members
            for (group2Member in changeSet.membersAdded.values) {
                obvChangeSet.addedMembersWithPermissions[ObvBytesKey(group2Member.bytesContactIdentity)] =
                        if (group2Member.permissionAdmin) adminPermissions else memberPermissions
            }

            // removed members
            for (bytesKey in changeSet.membersRemoved) {
                obvChangeSet.removedMembers.add(bytesKey.bytes)
            }

            // permission/admin changes
            for (groupMemberEntry in dbGroupMembers.entries) {
                if (changeSet.membersRemoved.contains(groupMemberEntry.key)) {
                    continue
                }

                val admin : Boolean = when(groupType) {
                    PrivateGroup -> false
                    SimpleGroup -> true
                    else -> changeSet.adminChanges[groupMemberEntry.key] ?: groupMemberEntry.value.permissionAdmin
                }
                val expectedPermissions = if (admin) adminPermissions else memberPermissions
                if (groupMemberEntry.value.permissionAdmin != expectedPermissions.contains(Permission.GROUP_ADMIN)
                        || groupMemberEntry.value.permissionSendMessage != expectedPermissions.contains(Permission.SEND_MESSAGE)
                        || groupMemberEntry.value.permissionRemoteDeleteAnything != expectedPermissions.contains(Permission.REMOTE_DELETE_ANYTHING)
                        || groupMemberEntry.value.permissionChangeSettings != expectedPermissions.contains(Permission.CHANGE_SETTINGS)
                        || groupMemberEntry.value.permissionEditOrRemoteDeleteOwnMessages != expectedPermissions.contains(Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES)) {
                    obvChangeSet.permissionChanges[ObvBytesKey(groupMemberEntry.key.bytes)] = expectedPermissions
                }
            }

            // groupType change
            if (groupType != initialGroupType) {
                try {
                    obvChangeSet.updatedJsonGroupType = AppSingleton.getJsonObjectMapper().writeValueAsString(groupType.toJsonGroupType())
                } catch (_: Exception) { }
            }
        }

        if (obvChangeSet.isEmpty.not()) {
            // until we have a UI to modify this, always reset own permissions to default admin, just in case!
            obvChangeSet.permissionChanges[ObvBytesKey(bytesOwnedIdentity)] = adminPermissions
        }
        return obvChangeSet
    }

    fun publishGroupEdits() {
        if (!editingGroupMembers || publishingGroupMembers) {
            return
        }
        publishingGroupMembers = true
        val obvChangeSet = getObvChangeSet()


        if (obvChangeSet.isEmpty) {
            publishingGroupMembers = false
            discardGroupEdits()
            return
        }

        App.runThread {
            try {
                AppSingleton.getEngine()
                    .initiateGroupV2Update(bytesOwnedIdentity, bytesGroupIdentifier, obvChangeSet)
            } catch (e: Exception) {
                e.printStackTrace()
                // an error occurred --> notify the user
                App.toast(string.toast_message_error_retry, Toast.LENGTH_SHORT)
                publishingGroupMembers = false
            }
        }
    }

    fun publicationFinished() {
        if (publishingGroupMembers) {
            synchronized(changeSet) {
                changeSet.adminChanges.clear()
                changeSet.membersAdded.clear()
                changeSet.membersRemoved.clear()
                changeSet.jsonGroupType = null
            }
            changeSetLiveData.postValue(changeSet)
            editingGroupMembers = false
            editingGroupMembersLiveData.postValue(false)
            publishingGroupMembers = false
        }
    }

    fun groupTypeChanged(): Boolean {
        return groupType.value?.toJsonGroupType() != initialGroupType
    }

    class ChangeSet {
        val adminChanges = HashMap<BytesKey, Boolean>()
        val membersRemoved = HashSet<BytesKey>()
        val membersAdded = HashMap<BytesKey, Group2MemberOrPending>()
        var jsonGroupType: JsonGroupType? = null
    }

    inner class EditedGroupMembersLiveData(
        groupMembersLiveData: LiveData<List<Group2MemberOrPending>>,
        changeSetLiveData: LiveData<ChangeSet>,
        editingMembersLiveData: LiveData<Boolean?>
    ) : MediatorLiveData<List<Group2MemberOrPending>?>() {
        private var changeSet: ChangeSet
        private var groupMembers: List<Group2MemberOrPending>
        private var editingMembers: Boolean

        init {
            changeSet = ChangeSet()
            groupMembers = ArrayList()
            editingMembers = false

            addSource(groupMembersLiveData) { groupMembers: List<Group2MemberOrPending> ->
                onGroupMembersChanged(groupMembers)
            }
            addSource(changeSetLiveData) { changeSet: ChangeSet -> onChangeSetChanged(changeSet) }
            addSource(editingMembersLiveData) { editingMembers: Boolean? ->
                onEditingMembersChanged(editingMembers)
            }
        }

        private fun onGroupMembersChanged(groupMembers: List<Group2MemberOrPending>?) {
            if (groupMembers == null) {
                this.groupMembers = ArrayList()
            } else {
                this.groupMembers = groupMembers
            }
            updateValue()
        }

        private fun onChangeSetChanged(changeSet: ChangeSet?) {
            if (changeSet == null) {
                this.changeSet = ChangeSet()
            } else {
                this.changeSet = changeSet
            }
            updateValue()
        }

        private fun onEditingMembersChanged(editingMembers: Boolean?) {
            this.editingMembers = editingMembers != null && editingMembers
            updateValue()
        }

        private fun updateValue() {
            if (!editingMembers) {
                value = groupMembers
            } else {
                val readOnly = groupType.value?.let { it == ReadOnlyGroup || (it is CustomGroup && it.readOnlySetting)  } ?: false
                
                val editedMembers = HashMap<BytesKey, Group2MemberOrPending>()
                for (group2MemberOrPending in groupMembers) {
                    val key = BytesKey(group2MemberOrPending.bytesContactIdentity)
                    if (changeSet.membersRemoved.contains(key)) {
                        continue
                    }
                    if (changeSet.adminChanges.containsKey(key)) {
                        val permissionChangedGroup2MemberOrPending = Group2MemberOrPending().apply {
                            contact = group2MemberOrPending.contact
                            bytesContactIdentity = group2MemberOrPending.bytesContactIdentity
                            sortDisplayName = group2MemberOrPending.sortDisplayName
                            identityDetails = group2MemberOrPending.identityDetails
                            permissionAdmin = changeSet.adminChanges[key] == true
                            permissionSendMessage = !readOnly
                        }
                        editedMembers[key] = permissionChangedGroup2MemberOrPending
                    } else {
                        editedMembers[key] = group2MemberOrPending
                    }
                }
                editedMembers.putAll(changeSet.membersAdded)
                val editedMembersList: MutableList<Group2MemberOrPending> =
                    ArrayList(editedMembers.size)
                editedMembersList.addAll(editedMembers.values)
                editedMembersList.sortWith { member1: Group2MemberOrPending, member2: Group2MemberOrPending ->
                    val minLen = min(member1.sortDisplayName.size, member2.sortDisplayName.size)
                    for (i in 0 until minLen) {
                        if (member1.sortDisplayName[i] != member2.sortDisplayName[i]) {
                            return@sortWith (member1.sortDisplayName[i].toInt() and 0xff) - (member2.sortDisplayName[i].toInt() and 0xff)
                        }
                    }
                    return@sortWith member1.sortDisplayName.size.compareTo(member2.sortDisplayName.size)
                }
                value = editedMembersList
            }
        }
    }
}