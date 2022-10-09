/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;


public class DiscussionViewModel extends ViewModel {
    private final AppDatabase db;
    private boolean selectingForDeletion;
    private List<Long> messageIdsToForward;
    @NonNull private final MutableLiveData<Long> discussionIdLiveData;
    @NonNull private final MutableLiveData<List<Long>> selectedMessageIds;
    @NonNull private final HashSet<Long> nonForwardableSelectedMessageIds;
    @NonNull private final MutableLiveData<byte[]> forwardMessageBytesOwnedIdentityLiveData;


    @NonNull private final LiveData<Discussion> discussionLiveData;
    @NonNull private final LiveData<DiscussionDao.DiscussionAndGroupMembersCount> discussionGroupMemberCountLiveData;
    @NonNull private final LiveData<List<Message>> messages;
    @NonNull private final LiveData<List<Contact>> discussionContacts;
    @NonNull private final LiveData<MessageDao.UnreadCountAndFirstMessage> unreadCountAndFirstMessage;
    @NonNull private final LiveData<DiscussionCustomization> discussionCustomization;
    @NonNull private final LiveData<Integer> newDetailsUpdate;
    @NonNull private final LiveData<OwnedIdentity> forwardMessageOwnedIdentityLiveData;
    @NonNull private final LiveData<List<Message>> currentlySharingLocationMessagesLiveData;

    public DiscussionViewModel() {
        db = AppDatabase.getInstance();
        selectingForDeletion = false;
        discussionIdLiveData = new MutableLiveData<>();
        selectedMessageIds = new MutableLiveData<>();
        nonForwardableSelectedMessageIds = new HashSet<>();
        forwardMessageBytesOwnedIdentityLiveData = new MutableLiveData<>();


        messages = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.messageDao().getDiscussionMessages(discussionId);
        });

        discussionLiveData = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionDao().getByIdAsync(discussionId);
        });

        discussionGroupMemberCountLiveData = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionDao().getWithGroupMembersCount(discussionId);
        });

        discussionContacts = Transformations.switchMap(discussionLiveData, discussion -> {
            if (discussion == null || discussion.isLocked()) {
                return new MutableLiveData<>(null);
            }
            if (discussion.canPostMessages()) {
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
            if (discussion != null && discussion.canPostMessages()) {
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

    // endregion

}
