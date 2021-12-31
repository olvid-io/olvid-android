/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;

public class MessageDetailsViewModel extends ViewModel {
    private final AppDatabase db;
    private final MutableLiveData<Long> messageIdLiveData;

    private final LiveData<Message> message;
    private final LiveData<List<MessageRecipientInfo>> messageRecipientInfos;
    private final LiveData<List<MessageMetadata>> messageMetadata;
    private final LiveData<DiscussionCustomization> discussionCustomization;
    private final LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> attachmentFyles;


    public MessageDetailsViewModel() {
        db = AppDatabase.getInstance();
        messageIdLiveData = new MutableLiveData<>();
        message = Transformations.switchMap(messageIdLiveData, messageId -> {
            if (messageId == null) {
                return null;
            }
            return db.messageDao().getLive(messageId);
        });

        messageRecipientInfos = Transformations.switchMap(messageIdLiveData, messageId -> {
            if (messageId == null) {
                return null;
            }
            return db.messageRecipientInfoDao().getAllByMessageIdLiveAndSorted(messageId);
        });

        discussionCustomization = Transformations.switchMap(message, message -> {
            if (message == null) {
                return null;
            }
            return db.discussionCustomizationDao().getLiveData(message.discussionId);
        });

        attachmentFyles = Transformations.switchMap(message, message -> {
            if (message == null) {
                return null;
            }
            return db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessage(message.id);
        });

        messageMetadata = Transformations.switchMap(messageIdLiveData, messageId -> {
            if (messageId == null) {
                return null;
            }
            return db.messageMetadataDao().getAllForMessage(messageId);
        });
    }

    public void setMessageId(long messageId) {
        messageIdLiveData.postValue(messageId);
    }

    public LiveData<Message> getMessage() {
        return message;
    }

    public LiveData<List<MessageRecipientInfo>> getMessageRecipientInfos() {
        return messageRecipientInfos;
    }

    public LiveData<List<MessageMetadata>> getMessageMetadata() {
        return messageMetadata;
    }

    public LiveData<DiscussionCustomization> getDiscussionCustomization() {
        return discussionCustomization;
    }

    public LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> getAttachmentFyles() {
        return attachmentFyles;
    }
}
