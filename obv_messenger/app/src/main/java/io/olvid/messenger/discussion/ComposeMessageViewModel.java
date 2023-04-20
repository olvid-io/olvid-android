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

package io.olvid.messenger.discussion;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;

public class ComposeMessageViewModel extends ViewModel {
    private final AppDatabase db;
    private final LiveData<Long> discussionIdLiveData;
    @NonNull
    private final MutableLiveData<Boolean> recordingLiveData;

    @NonNull
    private final LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> draftMessageFyles;
    private final MediatorLiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> draftMessageFylesEditMode = new MediatorLiveData<>();

    @NonNull
    private final LiveData<Message> draftMessage;
    private final MediatorLiveData<Message> draftMessageEditMode = new MediatorLiveData<>();
    @NonNull
    private final LiveData<Message> draftMessageReply;
    private final MediatorLiveData<Message> draftMessageReplyEditMode = new MediatorLiveData<>();
    @NonNull
    private final MutableLiveData<Message> draftMessageEdit = new MutableLiveData<>(null);
    @NonNull
    private final LiveData<Boolean> ephemeralSettingsChanged;

    private CharSequence newMessageText;

    private Uri photoOrVideoUri;
    private File photoOrVideoFile;


    public ComposeMessageViewModel(LiveData<Long> discussionIdLiveData, LiveData<DiscussionCustomization> discussionCustomization) {
        db = AppDatabase.getInstance();
        this.discussionIdLiveData = discussionIdLiveData;
        this.recordingLiveData = new MutableLiveData<>(false);

        draftMessageFyles = Transformations.switchMap(discussionIdLiveData, (Long discussionId) -> {
            if (discussionId == null) {
                return null;
            }
            return db.fyleDao().getDiscussionDraftFyles(discussionId);
        });

        draftMessage = Transformations.switchMap(discussionIdLiveData, (Long discussionId) -> {
            if (discussionId == null) {
                return null;
            }
            return db.messageDao().getDiscussionDraftMessage(discussionId);
        });

        draftMessageReply = Transformations.switchMap(draftMessage, draftMessage -> {
            if (draftMessage == null || draftMessage.jsonReply == null) {
                return new MutableLiveData<>(null);
            }
            Message.JsonMessageReference jsonReply;
            try {
                jsonReply = AppSingleton.getJsonObjectMapper().readValue(draftMessage.jsonReply, Message.JsonMessageReference.class);
            } catch (Exception e) {
                return new MutableLiveData<>(null);
            }
            return db.messageDao().getBySenderSequenceNumberAsync(jsonReply.getSenderSequenceNumber(), jsonReply.getSenderThreadIdentifier(), jsonReply.getSenderIdentifier(), draftMessage.discussionId);
        });

        draftMessageEditMode.addSource(draftMessage, draft -> draftMessageEditMode.setValue(draftMessageEdit.getValue() != null ? null : draft));
        draftMessageEditMode.addSource(draftMessageEdit, edit -> draftMessageEditMode.setValue(edit != null ? null : draftMessage.getValue()));

        draftMessageFylesEditMode.addSource(draftMessageFyles, draftFyles -> draftMessageFylesEditMode.setValue(draftMessageEdit.getValue() != null ? null : draftFyles));
        draftMessageFylesEditMode.addSource(draftMessageEdit, edit -> draftMessageFylesEditMode.setValue(edit != null ? null : draftMessageFyles.getValue()));

        draftMessageReplyEditMode.addSource(draftMessageReply, message -> draftMessageReplyEditMode.setValue(draftMessageEdit.getValue() != null ? null : message));
        draftMessageReplyEditMode.addSource(draftMessageEdit, edit -> draftMessageReplyEditMode.setValue(edit != null ? null : draftMessageReply.getValue()));

        ephemeralSettingsChanged = new EphemeralSettingsChangedLiveData(draftMessage, discussionCustomization, draftMessageEdit);
    }


    public void setRecording(boolean recording) {
        recordingLiveData.postValue(recording);
    }

    @NonNull
    public LiveData<Boolean> getRecordingLiveData() {
        return recordingLiveData;
    }

    public void setNewMessageText(CharSequence newMessageText) {
        this.newMessageText = newMessageText;
    }

    public CharSequence getRawNewMessageText() {
        return newMessageText;
    }

    public String getTrimmedNewMessageText() {
        if ((newMessageText != null) && (newMessageText.toString().trim().length() > 0)) {
            return newMessageText.toString().trim();
        }
        return null;
    }

    @NonNull
    public LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> getDraftMessageFyles() {
        return draftMessageFylesEditMode;
    }

    @NonNull
    public LiveData<Message> getDraftMessage() {
        return draftMessageEditMode;
    }

    @NonNull
    public LiveData<Message> getDraftMessageReply() {
        return draftMessageReplyEditMode;
    }

    @NonNull
    public LiveData<Message> getDraftMessageEdit() {
        return draftMessageEdit;
    }

    public void setDraftMessageEdit(@NonNull Message message) {
        draftMessageEdit.setValue(message);
    }

    public void clearDraftMessageEdit() {
        draftMessageEdit.setValue(null);
    }

    public boolean hasAttachments() {
        return (draftMessageFyles.getValue() != null) && (draftMessageFyles.getValue().size() > 0);
    }

    @NonNull
    public LiveData<Boolean> getEphemeralSettingsChanged() {
        return ephemeralSettingsChanged;
    }

    public static class EphemeralSettingsChangedLiveData extends MediatorLiveData<Boolean> {
        private Message.JsonExpiration draftMessageExpiration;
        private Message.JsonExpiration discussionCustomizationExpiration;
        private boolean isEditing;

        public EphemeralSettingsChangedLiveData(LiveData<Message> draftMessage, LiveData<DiscussionCustomization> discussionCustomization, LiveData<Message> draftMessageEdit) {
            addSource(draftMessage, this::onDraftMessageChanged);
            addSource(discussionCustomization, this::onDiscussionCustomizationChanged);
            addSource(draftMessageEdit, this::onDraftMessageEditChanged);
        }


        private void onDraftMessageChanged(Message message) {
            if (message == null || message.jsonExpiration == null) {
                draftMessageExpiration = null;
            } else {
                try {
                    draftMessageExpiration = AppSingleton.getJsonObjectMapper().readValue(message.jsonExpiration, Message.JsonExpiration.class);
                } catch (Exception e) {
                    draftMessageExpiration = null;
                }
            }
            compareJsonExpirations();
        }

        private void onDiscussionCustomizationChanged(DiscussionCustomization discussionCustomization) {
            if (discussionCustomization == null) {
                discussionCustomizationExpiration = null;
            } else {
                discussionCustomizationExpiration = discussionCustomization.getExpirationJson();
            }
            compareJsonExpirations();
        }

        private void onDraftMessageEditChanged(Message editedMessage) {
            isEditing = editedMessage != null;
        }

        private void compareJsonExpirations() {
            setValue(!isEditing
                    && draftMessageExpiration != null
                    && (discussionCustomizationExpiration != null || !draftMessageExpiration.likeNull())
                    && !draftMessageExpiration.equals(discussionCustomizationExpiration));
        }
    }

    // region take picture from discussion

    public Uri getPhotoOrVideoUri() {
        return photoOrVideoUri;
    }

    public void setPhotoOrVideoUri(Uri photoOrVideoUri) {
        this.photoOrVideoUri = photoOrVideoUri;
    }

    public File getPhotoOrVideoFile() {
        return photoOrVideoFile;
    }

    public void setPhotoOrVideoFile(File photoOrVideoFile) {
        this.photoOrVideoFile = photoOrVideoFile;
    }

    public Long getDiscussionId() {
        return discussionIdLiveData.getValue();
    }

    // endregion
}