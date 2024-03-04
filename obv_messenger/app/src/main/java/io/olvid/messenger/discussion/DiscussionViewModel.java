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

package io.olvid.messenger.discussion;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.text.CollationKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.dao.Group2MemberDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;


public class DiscussionViewModel extends ViewModel {
    private final AppDatabase db;
    private boolean selectingForDeletion;
    private List<Long> messageIdsToForward;
    @NonNull
    private final MutableLiveData<Long> discussionIdLiveData;
    @NonNull
    private final MutableLiveData<List<Long>> selectedMessageIds;
    @NonNull
    private final HashSet<Long> nonForwardableSelectedMessageIds;
    @NonNull
    private final MutableLiveData<byte[]> forwardMessageBytesOwnedIdentityLiveData;


    @NonNull
    private final LiveData<Discussion> discussionLiveData;
    @NonNull
    private final LiveData<DiscussionDao.DiscussionAndGroupMembersCount> discussionGroupMemberCountLiveData;
    @NonNull
    private final LiveData<List<Message>> messages;
    @NonNull
    private final LiveData<List<Invitation>> invitations;
    @NonNull
    private final LiveData<List<Contact>> discussionContacts;
    @NonNull
    private final LiveData<List<Contact>> mentionCandidatesLiveData;
    @NonNull
    private final LiveData<MessageDao.UnreadCountAndFirstMessage> unreadCountAndFirstMessage;
    @NonNull
    private final LiveData<DiscussionCustomization> discussionCustomization;
    @NonNull
    private final LiveData<Integer> newDetailsUpdate;
    @NonNull
    private final LiveData<OwnedIdentity> forwardMessageOwnedIdentityLiveData;
    @NonNull
    private final LiveData<List<Message>> currentlySharingLocationMessagesLiveData;

    public DiscussionViewModel() {
        db = AppDatabase.getInstance();
        selectingForDeletion = false;
        discussionIdLiveData = new MutableLiveData<>();
        selectedMessageIds = new MutableLiveData<>();
        nonForwardableSelectedMessageIds = new HashSet<>();
        forwardMessageBytesOwnedIdentityLiveData = new MutableLiveData<>();


        messages = Transformations.switchMap(discussionIdLiveData, (Long discussionId) -> {
            if (discussionId == null) {
                return null;
            }
            if (SettingsActivity.getHideGroupMemberChanges()) {
                return db.messageDao().getDiscussionMessagesWithoutGroupMemberChanges(discussionId);
            } else {
                return db.messageDao().getDiscussionMessages(discussionId);
            }
        });

        discussionLiveData = Transformations.switchMap(discussionIdLiveData, (Long discussionId) -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionDao().getByIdAsync(discussionId);
        });

        discussionGroupMemberCountLiveData = Transformations.switchMap(discussionIdLiveData, (Long discussionId) -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionDao().getWithGroupMembersCount(discussionId);
        });

        invitations = Transformations.switchMap(discussionLiveData, discussion -> {
            if (discussion == null) {
                return null;
            }
            return db.invitationDao().getByDiscussionId(discussion.id);
        });

        mentionCandidatesLiveData = new MentionCandidatesLiveData(discussionLiveData, AppSingleton.getCurrentIdentityLiveData());

        discussionContacts = Transformations.switchMap(discussionLiveData, discussion -> {
            if (discussion == null || discussion.isLocked()) {
                return new MutableLiveData<>(null);
            }
            if (discussion.isNormalOrReadOnly()) {
                switch (discussion.discussionType) {
                    case Discussion.TYPE_CONTACT:
                        return db.contactDao().getAsList(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    case Discussion.TYPE_GROUP:
                        return db.contactGroupJoinDao().getGroupContacts(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    case Discussion.TYPE_GROUP_V2:
                        return db.group2MemberDao().getGroupMemberContacts(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                }
            }
            return null;
        });

        unreadCountAndFirstMessage = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.messageDao().getUnreadCountAndFirstMessage(discussionId);
        });

        discussionCustomization = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionCustomizationDao().getLiveData(discussionId);
        });

        newDetailsUpdate = Transformations.switchMap(discussionLiveData, discussion -> {
            if (discussion != null && discussion.isNormalOrReadOnly()) {
                switch (discussion.discussionType) {
                    case Discussion.TYPE_CONTACT:
                        return Transformations.map(AppDatabase.getInstance().contactDao().getAsync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier), (Contact contact) -> {
                            if (contact != null) {
                                return contact.newPublishedDetails;
                            }
                            return null;
                        });
                    case Discussion.TYPE_GROUP:
                        return Transformations.map(AppDatabase.getInstance().groupDao().getLiveData(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier), (Group group) -> {
                            if (group != null) {
                                return group.newPublishedDetails;
                            }
                            return null;
                        });
                    case Discussion.TYPE_GROUP_V2:
                        return Transformations.map(AppDatabase.getInstance().group2Dao().getLiveData(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier), (Group2 group2) -> {
                            if (group2 != null) {
                                return group2.newPublishedDetails;
                            }
                            return null;
                        });
                }
            }
            return new MutableLiveData<>(Contact.PUBLISHED_DETAILS_NOTHING_NEW);
        });

        forwardMessageOwnedIdentityLiveData = Transformations.switchMap(forwardMessageBytesOwnedIdentityLiveData, (byte[] bytesOwnedIdentity) -> {
            if (bytesOwnedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().ownedIdentityDao().getLiveData(bytesOwnedIdentity);
        });

        currentlySharingLocationMessagesLiveData = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.messageDao().getCurrentlySharingLocationMessagesInDiscussionLiveData(discussionId);
        });
    }


    public void setDiscussionId(Long discussionId) {
        discussionIdLiveData.postValue(discussionId);
    }

    public Long getDiscussionId() {
        return discussionIdLiveData.getValue();
    }

    @NonNull
    public LiveData<Discussion> getDiscussion() {
        return discussionLiveData;
    }

    @NonNull
    public LiveData<DiscussionDao.DiscussionAndGroupMembersCount> getDiscussionGroupMemberCountLiveData() {
        return discussionGroupMemberCountLiveData;
    }

    @NonNull
    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    @NonNull
    public LiveData<List<Contact>> getDiscussionContacts() {
        return discussionContacts;
    }

    @NonNull
    public LiveData<List<Contact>> getMentionCandidatesLiveData() {
        return mentionCandidatesLiveData;
    }

    @NonNull
    public LiveData<MessageDao.UnreadCountAndFirstMessage> getUnreadCountAndFirstMessage() {
        return unreadCountAndFirstMessage;
    }

    @NonNull
    public LiveData<DiscussionCustomization> getDiscussionCustomization() {
        return discussionCustomization;
    }

    @NonNull
    public LiveData<Integer> getNewDetailsUpdate() {
        return newDetailsUpdate;
    }

    @NonNull
    public LiveData<List<Message>> getCurrentlySharingLocationMessagesLiveData() {
        return currentlySharingLocationMessagesLiveData;
    }



    public static class MentionCandidatesLiveData extends MediatorLiveData<List<Contact>> {
        private List<Contact> contactList;
        private Contact ownedIdentityContact;

        public MentionCandidatesLiveData(LiveData<Discussion> discussionLiveData, LiveData<OwnedIdentity> ownedIdentityLiveData) {
            AppDatabase db = AppDatabase.getInstance();
            LiveData<List<Contact>> discussionContactsAndPending = Transformations.switchMap(discussionLiveData, discussion -> {
                if (discussion == null || discussion.isLocked()) {
                    return new MutableLiveData<>(new ArrayList<>());
                }
                if (discussion.isNormal()) {
                    switch (discussion.discussionType) {
                        case Discussion.TYPE_CONTACT:
                            return db.contactDao().getAsList(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                        case Discussion.TYPE_GROUP:
                            return db.contactGroupJoinDao().getGroupContacts(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                        case Discussion.TYPE_GROUP_V2:
                            return Transformations.map(db.group2MemberDao().getGroupMembersAndPendingForMention(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier), group2MemberOrPendingsForMention -> {
                                List<Contact> contacts = new ArrayList<>();
                                if (group2MemberOrPendingsForMention == null) {
                                    return contacts;
                                }
                                for (Group2MemberDao.Group2MemberOrPendingForMention member : group2MemberOrPendingsForMention) {
                                    if (member.contact != null) {
                                        contacts.add(member.contact);
                                    } else {
                                        Contact contact = Contact.createFake(member.bytesContactIdentity,
                                                discussion.bytesOwnedIdentity,
                                                member.sortDisplayName,
                                                member.fullSearchDisplayName,
                                                member.identityDetails);
                                        if (contact != null) {
                                            contacts.add(contact);
                                        }
                                    }
                                }
                                return contacts;
                            });
                    }
                }
                return new MutableLiveData<>(new ArrayList<>());
            });
            addSource(discussionContactsAndPending, this::onContactListChanged);
            addSource(ownedIdentityLiveData, this::onOwnedIdentityChanged);
        }

        private void onContactListChanged(List<Contact> contactList) {
            this.contactList = contactList;
            merge();
        }

        private void onOwnedIdentityChanged(OwnedIdentity ownedIdentity) {
            if (ownedIdentity == null) {
                this.ownedIdentityContact = null;
            } else {
                this.ownedIdentityContact = Contact.createFakeFromOwnedIdentity(ownedIdentity);
            }
            merge();
        }



        private void merge() {
            if (ownedIdentityContact == null) {
                setValue(contactList);
            } else if (contactList == null) {
                setValue(Collections.singletonList(ownedIdentityContact));
            } else {
                List<Contact> mergedList = new ArrayList<>();
                for (int i = 0; i < contactList.size(); i++) {
                    Contact contact = contactList.get(i);
                    if (firstIsLarger(contact.sortDisplayName, ownedIdentityContact.sortDisplayName)) {
                        // we have reached the spot where ownedIdentity should be added
                        mergedList.add(ownedIdentityContact);
                        mergedList.addAll(contactList.subList(i, contactList.size()));
                        setValue(mergedList);
                        return;
                    }
                    mergedList.add(contact);
                }
                // if we reach this point, it means we have not yet added our ownedIdentity --> add it now
                mergedList.add(ownedIdentityContact);
                setValue(mergedList);
            }
        }

        /**
        * method used to compare two sortDisplayNames (see {@link CollationKey#toByteArray})
        */
        private boolean firstIsLarger(byte[] sortDisplayName1, byte[] sortDisplayName2) {
            int len = Math.min(sortDisplayName1.length, sortDisplayName2.length);
            for (int i = 0; i < len; i++) {
                if (sortDisplayName1[i] > sortDisplayName2[i]) {
                    return true;
                } else if (sortDisplayName1[i] < sortDisplayName2[i]) {
                    return false;
                }
            }
            return sortDisplayName1.length > sortDisplayName2.length;
        }
    }

    // region select for deletion
    public boolean isSelectingForDeletion() {
        return selectingForDeletion;
    }

    public void selectMessageId(long messageId, boolean forwardable) {
        List<Long> ids;
        if (selectedMessageIds.getValue() == null) {
            ids = new ArrayList<>();
        } else {
            ids = new ArrayList<>(selectedMessageIds.getValue().size());
            ids.addAll(selectedMessageIds.getValue());
        }
        if (ids.remove(messageId)) {
            nonForwardableSelectedMessageIds.remove(messageId);
            if (ids.size() == 0) {
                selectingForDeletion = false;
            }
        } else {
            ids.add(messageId);
            if (!forwardable) {
                nonForwardableSelectedMessageIds.add(messageId);
            }
            selectingForDeletion = true;
        }
        selectedMessageIds.postValue(ids);
    }

    public void unselectMessageId(long messageId) {
        List<Long> ids = selectedMessageIds.getValue();
        if (ids != null) {
            ids.remove(messageId);
            nonForwardableSelectedMessageIds.remove(messageId);
            selectedMessageIds.postValue(ids);
        }
    }


    public LiveData<List<Long>> getSelectedMessageIds() {
        return selectedMessageIds;
    }

    public boolean areAllSelectedMessagesForwardable() {
        return nonForwardableSelectedMessageIds.isEmpty();
    }

    public void deselectAll() {
        selectingForDeletion = false;
        nonForwardableSelectedMessageIds.clear();
        selectedMessageIds.postValue(new ArrayList<>());
    }

    public void setMessageIdsToForward(List<Long> messageIds) {
        this.messageIdsToForward = messageIds;
    }

    public List<Long> getMessageIdsToForward() {
        return messageIdsToForward;
    }

    public LiveData<Long> getDiscussionIdLiveData() {
        return discussionIdLiveData;
    }

    public void setForwardMessageBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        this.forwardMessageBytesOwnedIdentityLiveData.postValue(bytesOwnedIdentity);
    }

    @NonNull
    public LiveData<OwnedIdentity> getForwardMessageOwnedIdentityLiveData() {
        return forwardMessageOwnedIdentityLiveData;
    }

    @NonNull
    public LiveData<List<Invitation>> getInvitations() {
        return invitations;
    }

    // endregion
}
