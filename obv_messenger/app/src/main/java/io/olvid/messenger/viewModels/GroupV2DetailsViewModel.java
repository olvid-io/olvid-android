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

package io.olvid.messenger.viewModels;

import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.Group2MemberDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group2;


public class GroupV2DetailsViewModel extends ViewModel {
    private byte[] bytesOwnedIdentity;
    private byte[] bytesGroupIdentifier;

    private final HashMap<BytesKey, Group2MemberDao.Group2MemberOrPending> dbGroupMembers;
    private boolean editingGroupMembers;
    private final MutableLiveData<Boolean> editingGroupMembersLiveData;
    private boolean publishingGroupMembers;
    private final ChangeSet changeSet;
    private final MutableLiveData<ChangeSet> changeSetLiveData;
    private final MutableLiveData<Pair<byte[], byte[]>> bytesOwnedIdentityAndGroupIdentifierLiveData;
    private final LiveData<Group2> group;
    private final EditedGroupMembersLiveData editedGroupMembersLiveData;

    private int membersCount = 0;
    private int membersAndPendingCount = 0;

    public GroupV2DetailsViewModel() {
        this.dbGroupMembers = new HashMap<>();
        this.changeSet = new ChangeSet();
        this.editingGroupMembers = false;
        this.editingGroupMembersLiveData = new MutableLiveData<>(false);
        this.publishingGroupMembers = false;
        this.changeSetLiveData = new MutableLiveData<>(changeSet);

        this.bytesOwnedIdentityAndGroupIdentifierLiveData = new MutableLiveData<>(null);
        this.group = Transformations.switchMap(bytesOwnedIdentityAndGroupIdentifierLiveData, (Pair<byte[], byte[]> bytesOwnedIdentityAndGroupIdentifier) -> {
            if (bytesOwnedIdentityAndGroupIdentifier == null) {
                return null;
            }
            return AppDatabase.getInstance().group2Dao().getLiveData(bytesOwnedIdentityAndGroupIdentifier.first, bytesOwnedIdentityAndGroupIdentifier.second);
        });
        // we add a map around the switch map to be able to update our dbGroupMembers HashMap. As long as someone is observing groupMembers, it will be updated
        //  check for members added both locally and externally
        //  check for already removed members
        LiveData<List<Group2MemberDao.Group2MemberOrPending>> groupMembers = Transformations.map(Transformations.switchMap(bytesOwnedIdentityAndGroupIdentifierLiveData, (Pair<byte[], byte[]> bytesOwnedIdentityAndGroupIdentifier) -> {
            if (bytesOwnedIdentityAndGroupIdentifier == null) {
                return null;
            }
            return AppDatabase.getInstance().group2MemberDao().getGroupMembersAndPending(bytesOwnedIdentityAndGroupIdentifier.first, bytesOwnedIdentityAndGroupIdentifier.second);
        }), (List<Group2MemberDao.Group2MemberOrPending> group2MemberOrPendings) -> {

            synchronized (changeSet) {
                dbGroupMembers.clear();
                if (group2MemberOrPendings != null) {
                    membersAndPendingCount = group2MemberOrPendings.size();
                    membersCount = 0;
                    for (Group2MemberDao.Group2MemberOrPending group2MemberOrPending : group2MemberOrPendings) {
                        if (!group2MemberOrPending.pending) {
                            membersCount++;
                        }
                        BytesKey key = new BytesKey(group2MemberOrPending.bytesContactIdentity);
                        dbGroupMembers.put(key, group2MemberOrPending);
                        // check for members added both locally and externally
                        Group2MemberDao.Group2MemberOrPending addedMember = changeSet.membersAdded.remove(key);
                        if (addedMember != null && addedMember.permissionAdmin && !group2MemberOrPending.permissionAdmin) {
                            changeSet.adminChanges.put(key, true);
                        }
                    }
                }
                // check for already removed members
                for (BytesKey key : new HashSet<>(changeSet.membersRemoved)) {
                    if (!dbGroupMembers.containsKey(key)) {
                        changeSet.membersRemoved.remove(key);
                    }
                }
            }
            return group2MemberOrPendings;
        });

        editedGroupMembersLiveData = new EditedGroupMembersLiveData(groupMembers, changeSetLiveData, editingGroupMembersLiveData);
    }

    public void setGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            bytesOwnedIdentityAndGroupIdentifierLiveData.postValue(null);
        } else {
            bytesOwnedIdentityAndGroupIdentifierLiveData.postValue(new Pair<>(bytesOwnedIdentity, bytesGroupIdentifier));
        }
    }

    public byte[] getBytesOwnedIdentity() {
        return bytesOwnedIdentity;
    }

    public byte[] getBytesGroupIdentifier() {
        return bytesGroupIdentifier;
    }

    public LiveData<Boolean> isEditingGroupMembersLiveData() {
        return editingGroupMembersLiveData;
    }

    public LiveData<Group2> getGroup() {
        return group;
    }

    public LiveData<List<Group2MemberDao.Group2MemberOrPending>> getGroupMembers() {
        return editedGroupMembersLiveData;
    }

    public int getMembersCount() {
        return membersCount;
    }

    public int getMembersAndPendingCount() {
        return membersAndPendingCount;
    }

    public void adminChanged(byte[] bytesContactIdentity, boolean isAdmin) {
        if (!editingGroupMembers || publishingGroupMembers) {
            return;
        }
        synchronized (changeSet) {
            BytesKey key = new BytesKey(bytesContactIdentity);
            Group2MemberDao.Group2MemberOrPending groupMember = dbGroupMembers.get(key);
            if (groupMember != null) {
                if (groupMember.permissionAdmin ^ isAdmin) {
                    changeSet.adminChanges.put(key, isAdmin);
                } else {
                    changeSet.adminChanges.remove(key);
                }
            }
            Group2MemberDao.Group2MemberOrPending addedMember = changeSet.membersAdded.get(key);
            if (addedMember != null) {
                addedMember.permissionAdmin = isAdmin;
            }
        }
        changeSetLiveData.postValue(changeSet);
    }

    public void memberRemoved(byte[] bytesContactIdentity) {
        if (!editingGroupMembers || publishingGroupMembers) {
            return;
        }
        synchronized (changeSet) {
            BytesKey key = new BytesKey(bytesContactIdentity);
            if (dbGroupMembers.containsKey(key)) {
                changeSet.membersRemoved.add(key);
            } else {
                changeSet.membersAdded.remove(key);
            }
            changeSet.adminChanges.remove(key);
        }
        changeSetLiveData.postValue(changeSet);
    }

    public void membersAdded(List<Contact> contacts) {
        if (!editingGroupMembers || publishingGroupMembers) {
            return;
        }
        synchronized (changeSet) {
            for (Contact contact : contacts) {
                BytesKey key = new BytesKey(contact.bytesContactIdentity);
                if (!dbGroupMembers.containsKey(key) && !changeSet.membersAdded.containsKey(key)) {
                    Group2MemberDao.Group2MemberOrPending memberOrPending = new Group2MemberDao.Group2MemberOrPending();
                    memberOrPending.contact = contact;
                    memberOrPending.bytesContactIdentity = contact.bytesContactIdentity;
                    memberOrPending.sortDisplayName = contact.sortDisplayName;
                    memberOrPending.identityDetails = contact.identityDetails;
                    memberOrPending.permissionAdmin = false;
                    changeSet.membersAdded.put(key, memberOrPending);
                }
                changeSet.membersRemoved.remove(key);
            }
        }
        changeSetLiveData.postValue(changeSet);
    }

    public void startEditingMembers() {
        if (this.editingGroupMembers || publishingGroupMembers) {
            return;
        }
        editingGroupMembers = true;
        editingGroupMembersLiveData.postValue(true);
    }

    public boolean discardGroupEdits() {
        if (!editingGroupMembers || publishingGroupMembers) {
            return false;
        }
        synchronized (changeSet) {
            changeSet.adminChanges.clear();
            changeSet.membersAdded.clear();
            changeSet.membersRemoved.clear();
        }
        changeSetLiveData.postValue(changeSet);
        editingGroupMembers = false;
        editingGroupMembersLiveData.postValue(false);
        return true;
    }

    public void publishGroupEdits() {
        if (!this.editingGroupMembers || publishingGroupMembers) {
            return;
        }
        publishingGroupMembers = true;

        ObvGroupV2.ObvGroupV2ChangeSet obvChangeSet = new ObvGroupV2.ObvGroupV2ChangeSet();

        synchronized (changeSet) {
            for (Group2MemberDao.Group2MemberOrPending group2Member: changeSet.membersAdded.values()) {
                HashSet<GroupV2.Permission> permissions = new HashSet<>();
                if (group2Member.permissionAdmin) {
                    permissions.addAll(Arrays.asList(GroupV2.Permission.DEFAULT_ADMIN_PERMISSIONS));
                } else {
                    permissions.addAll(Arrays.asList(GroupV2.Permission.DEFAULT_MEMBER_PERMISSIONS));
                }
                obvChangeSet.addedMembersWithPermissions.put(new ObvBytesKey(group2Member.bytesContactIdentity), permissions);
            }

            for (BytesKey bytesKey : changeSet.membersRemoved) {
                obvChangeSet.removedMembers.add(bytesKey.bytes);
            }

            for (Map.Entry<BytesKey, Boolean> entry : changeSet.adminChanges.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                HashSet<GroupV2.Permission> permissions = new HashSet<>();
                if (entry.getValue()) {
                    permissions.addAll(Arrays.asList(GroupV2.Permission.DEFAULT_ADMIN_PERMISSIONS));
                } else {
                    permissions.addAll(Arrays.asList(GroupV2.Permission.DEFAULT_MEMBER_PERMISSIONS));
                }
                obvChangeSet.permissionChanges.put(new ObvBytesKey(entry.getKey().bytes), permissions);
            }
        }

        if (obvChangeSet.isEmpty()) {
            publishingGroupMembers = false;
            discardGroupEdits();
            return;
        }

        // until we have a UI to modify this, always reset own permissions to default admin, just in case!
        obvChangeSet.permissionChanges.put(new ObvBytesKey(bytesOwnedIdentity), new HashSet<>(Arrays.asList(GroupV2.Permission.DEFAULT_ADMIN_PERMISSIONS)));

        App.runThread(() -> {
            try {
                AppSingleton.getEngine().initiateGroupV2Update(bytesOwnedIdentity, bytesGroupIdentifier, obvChangeSet);
            } catch (Exception e) {
                e.printStackTrace();
                // an error occurred --> notify the user
                App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                publishingGroupMembers = false;
            }
        });
    }

    public ChangeSet getChangeSet() {
        return changeSet;
    }

    public void publicationFinished() {
        if (publishingGroupMembers) {
            synchronized (changeSet) {
                changeSet.adminChanges.clear();
                changeSet.membersAdded.clear();
                changeSet.membersRemoved.clear();
            }
            changeSetLiveData.postValue(changeSet);
            editingGroupMembers = false;
            editingGroupMembersLiveData.postValue(false);
            publishingGroupMembers = false;
        }
    }

    public static class ChangeSet {
        public final HashMap<BytesKey, Boolean> adminChanges = new HashMap<>();
        public final HashSet<BytesKey> membersRemoved = new HashSet<>();
        public final HashMap<BytesKey, Group2MemberDao.Group2MemberOrPending> membersAdded = new HashMap<>();
    }


    public static class EditedGroupMembersLiveData extends MediatorLiveData<List<Group2MemberDao.Group2MemberOrPending>> {
        @NonNull private ChangeSet changeSet;
        @NonNull private List<Group2MemberDao.Group2MemberOrPending> groupMembers;
        private boolean editingMembers;

        public EditedGroupMembersLiveData(LiveData<List<Group2MemberDao.Group2MemberOrPending>> groupMembersLiveData, LiveData<ChangeSet> changeSetLiveData, LiveData<Boolean> editingMembersLiveData) {
            changeSet = new ChangeSet();
            groupMembers = new ArrayList<>();
            editingMembers = false;
            addSource(groupMembersLiveData, this::onGroupMembersChanged);
            addSource(changeSetLiveData, this::onChangeSetChanged);
            addSource(editingMembersLiveData, this::onEditingMembersChanged);
        }

        private void onGroupMembersChanged(List<Group2MemberDao.Group2MemberOrPending> groupMembers) {
            if (groupMembers == null) {
                this.groupMembers = new ArrayList<>();
            } else {
                this.groupMembers = groupMembers;
            }
            updateValue();
        }

        private void onChangeSetChanged(ChangeSet changeSet) {
            if (changeSet == null) {
                this.changeSet = new ChangeSet();
            } else {
                this.changeSet = changeSet;
            }
            updateValue();
        }

        private void onEditingMembersChanged(Boolean editingMembers) {
            this.editingMembers = editingMembers != null && editingMembers;
            updateValue();
        }

        private void updateValue() {
            if (!editingMembers) {
                setValue(groupMembers);
            } else {
                HashMap<BytesKey, Group2MemberDao.Group2MemberOrPending> editedMembers = new HashMap<>();
                for (Group2MemberDao.Group2MemberOrPending group2MemberOrPending : groupMembers) {
                    BytesKey key = new BytesKey(group2MemberOrPending.bytesContactIdentity);
                    if (changeSet.membersRemoved.contains(key)) {
                        continue;
                    }
                    if (changeSet.adminChanges.containsKey(key)) {
                        Group2MemberDao.Group2MemberOrPending adminChangedGroup2MemberOrPending = new Group2MemberDao.Group2MemberOrPending();
                        adminChangedGroup2MemberOrPending.contact = group2MemberOrPending.contact;
                        adminChangedGroup2MemberOrPending.bytesContactIdentity = group2MemberOrPending.bytesContactIdentity;
                        adminChangedGroup2MemberOrPending.sortDisplayName = group2MemberOrPending.sortDisplayName;
                        adminChangedGroup2MemberOrPending.identityDetails = group2MemberOrPending.identityDetails;
                        Boolean admin = changeSet.adminChanges.get(key);
                        adminChangedGroup2MemberOrPending.permissionAdmin = admin != null && admin;
                        editedMembers.put(key, adminChangedGroup2MemberOrPending);
                    } else {
                        editedMembers.put(key, group2MemberOrPending);
                    }
                }

                editedMembers.putAll(changeSet.membersAdded);

                List<Group2MemberDao.Group2MemberOrPending> editedMembersList = new ArrayList<>(editedMembers.size());
                editedMembersList.addAll(editedMembers.values());

                Collections.sort(editedMembersList, (Group2MemberDao.Group2MemberOrPending member1, Group2MemberDao.Group2MemberOrPending member2) -> {
                    int minLen = Math.min(member1.sortDisplayName.length, member2.sortDisplayName.length);
                    for (int i=0; i<minLen; i++) {
                        if (member1.sortDisplayName[i] != member2.sortDisplayName[i]) {
                            return (member1.sortDisplayName[i] & 0xff) - (member2.sortDisplayName[i] & 0xff);
                        }
                    }
                    // if all the first bytes are equal,  the largest is the longest
                    return Integer.compare(member1.sortDisplayName.length, member2.sortDisplayName.length);
                });

                setValue(editedMembersList);
            }
        }
    }
}
