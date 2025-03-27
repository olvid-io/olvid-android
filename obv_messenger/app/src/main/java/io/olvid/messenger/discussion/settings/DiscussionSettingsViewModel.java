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

package io.olvid.messenger.discussion.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.HashSet;
import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.Group2Dao;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;


public class DiscussionSettingsViewModel extends ViewModel {
    private final DiscussionSettingsDataStore discussionSettingsDataStore;
    private final AppDatabase db;
    private final MutableLiveData<Long> discussionIdLiveData;
    private final LiveData<Discussion> discussionLiveData;
    private final LiveData<DiscussionCustomization> discussionCustomization;
    private final LiveData<Boolean> discussionLockedLiveData;
    private final LiveData<Boolean> nonAdminGroupDiscussionLiveData;
    private final HashSet<SettingsChangedListener> settingsChangedListeners;

    // custom notifications
    private boolean messageNotificationModified;
    private boolean useCustomMessageNotification;
    private String messageRingtone;
    private String messageVibrationPattern;
    private String messageLedColor;


    public DiscussionSettingsViewModel() {
        discussionSettingsDataStore = new DiscussionSettingsDataStore(this);
        db = AppDatabase.getInstance();
        discussionIdLiveData = new MutableLiveData<>();
        discussionCustomization = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionCustomizationDao().getLiveData(discussionId);
        });
        discussionLiveData = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionDao().getByIdAsync(discussionId);
        });
        discussionLockedLiveData = Transformations.map(discussionLiveData, (Discussion discussion) -> {
            if (discussion == null) {
                return false;
            }
            return discussion.isLocked();
        });
        nonAdminGroupDiscussionLiveData = Transformations.map(Transformations.switchMap(discussionLiveData, (Discussion discussion) -> {
            if (discussion != null) {
                switch (discussion.discussionType) {
                    case Discussion.TYPE_GROUP:
                        return db.groupDao().getGroupOrGroup2LiveData(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    case Discussion.TYPE_GROUP_V2:
                        return db.group2Dao().getGroupOrGroup2LiveData(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    case Discussion.TYPE_CONTACT:
                    default:
                }
            }
            return new MutableLiveData<>(null);
        }), (Group2Dao.GroupOrGroup2 group) -> {
            if (group != null && group.group != null) {
                return group.group.bytesGroupOwnerIdentity != null;
            } else if (group != null && group.group2 != null) {
                return !group.group2.ownPermissionChangeSettings;
            }
            return false;
        });

        settingsChangedListeners = new HashSet<>();

        messageNotificationModified = false;
        useCustomMessageNotification = false;
        messageRingtone = null;
        messageVibrationPattern = null;
        messageLedColor = null;
    }

    void addSettingsChangedListener(@NonNull SettingsChangedListener settingsChangedListener) {
        synchronized (settingsChangedListeners) {
            settingsChangedListeners.add(settingsChangedListener);
            settingsChangedListener.onSettingsChanged(discussionCustomization.getValue());
            settingsChangedListener.onLockedOrGroupAdminChanged(isLocked(), isNonAdminGroupDiscussion());
        }
    }

    void removeSettingsChangedListener(@NonNull SettingsChangedListener settingsChangedListener) {
        synchronized (settingsChangedListeners) {
            settingsChangedListeners.remove(settingsChangedListener);
        }
    }

    void notifyLockedOrNonGroupAdminChanged() {
        synchronized (settingsChangedListeners) {
            for (SettingsChangedListener settingsChangedListener : settingsChangedListeners) {
                settingsChangedListener.onLockedOrGroupAdminChanged(isLocked(), isNonAdminGroupDiscussion());
            }
        }
    }

    void notifySettingsChangedListeners(DiscussionCustomization discussionCustomization) {
        synchronized (settingsChangedListeners) {
            for (SettingsChangedListener settingsChangedListener : settingsChangedListeners) {
                settingsChangedListener.onSettingsChanged(discussionCustomization);
            }
        }
    }

    public DiscussionSettingsDataStore getDiscussionSettingsDataStore() {
        return discussionSettingsDataStore;
    }

    public void setDiscussionId(long discussionId) {
        discussionIdLiveData.postValue(discussionId);
    }

    public Long getDiscussionId() {
        return discussionIdLiveData.getValue();
    }

    public LiveData<Boolean> getDiscussionLockedLiveData() {
        return discussionLockedLiveData;
    }

    public boolean isLocked() {
        Boolean locked = discussionLockedLiveData.getValue();
        return locked != null && locked;
    }

    public LiveData<Boolean> getNonAdminGroupDiscussionLiveData() {
        return nonAdminGroupDiscussionLiveData;
    }

    public boolean isNonAdminGroupDiscussion() {
        Boolean nonAdmin = nonAdminGroupDiscussionLiveData.getValue();
        return nonAdmin != null && nonAdmin;
    }

    public LiveData<Discussion> getDiscussionLiveData() {
        return discussionLiveData;
    }

    public LiveData<DiscussionCustomization> getDiscussionCustomization() {
        return discussionCustomization;
    }

    // region Custom notifications

    void updateNotificationsFromCustomization(DiscussionCustomization discussionCustomization) {
        if (!messageNotificationModified) {
            if (discussionCustomization != null) {
                this.useCustomMessageNotification = discussionCustomization.prefUseCustomMessageNotification;
                this.messageRingtone = discussionCustomization.prefMessageNotificationRingtone;
                this.messageVibrationPattern = discussionCustomization.prefMessageNotificationVibrationPattern;
                this.messageLedColor = discussionCustomization.prefMessageNotificationLedColor;
            } else {
                this.useCustomMessageNotification = false;
                this.messageRingtone = null;
                this.messageVibrationPattern = null;
                this.messageLedColor = null;
            }
        }
    }

    void saveCustomMessageNotification() {
        Long discussionId = discussionIdLiveData.getValue();
        if (discussionId == null) {
            return;
        }
        App.runThread(() -> {
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
            if (discussionCustomization == null) {
                discussionCustomization = new DiscussionCustomization(discussionId);
                db.discussionCustomizationDao().insert(discussionCustomization);
            }

            discussionCustomization.prefUseCustomMessageNotification = useCustomMessageNotification;
            discussionCustomization.prefMessageNotificationRingtone = (useCustomMessageNotification && messageRingtone == null) ? SettingsActivity.PREF_KEY_MESSAGE_RINGTONE_DEFAULT : messageRingtone;
            discussionCustomization.prefMessageNotificationVibrationPattern = (useCustomMessageNotification && messageVibrationPattern == null) ? SettingsActivity.PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT : messageVibrationPattern;
            discussionCustomization.prefMessageNotificationLedColor = messageLedColor;

            messageNotificationModified = false;
            db.discussionCustomizationDao().update(discussionCustomization);
            AndroidNotificationManager.updateDiscussionChannel(discussionId, true);
        });
    }

    public boolean isMessageNotificationModified() {
        return messageNotificationModified;
    }

    boolean useCustomMessageNotification() {
        return useCustomMessageNotification;
    }

    public void setUseCustomMessageNotification(boolean useCustomMessageNotification) {
        if (useCustomMessageNotification ^ this.useCustomMessageNotification) {
            this.messageNotificationModified = true;
            this.useCustomMessageNotification = useCustomMessageNotification;
        }
    }

    public String getMessageRingtone() {
        return messageRingtone;
    }

    public void setMessageRingtone(String messageRingtone) {
        if (!Objects.equals(this.messageRingtone, messageRingtone)) {
            this.messageNotificationModified = true;
            this.messageRingtone = messageRingtone;
        }
    }

    public String getMessageVibrationPattern() {
        return messageVibrationPattern;
    }

    public void setMessageVibrationPattern(String messageVibrationPattern) {
        if (!Objects.equals(this.messageVibrationPattern, messageVibrationPattern)) {
            this.messageNotificationModified = true;
            this.messageVibrationPattern = messageVibrationPattern;
        }
    }

    public String getMessageLedColor() {
        return messageLedColor;
    }

    public void setMessageLedColor(String messageLedColor) {
        if (!Objects.equals(this.messageLedColor, messageLedColor)) {
            this.messageNotificationModified = true;
            this.messageLedColor = messageLedColor;
        }
    }

    // endregion

    // region Ephemeral settings

    void saveEphemeralSettingsAndNotifyPeers(boolean readOnce, Long visibilityDuration, Long existenceDuration) {
        Discussion discussion = discussionLiveData.getValue();
        if (discussion == null) {
            return;
        }

        App.runThread(() -> {
            // first check whether you have permission to modify these settings
            switch (discussion.discussionType) {
                case Discussion.TYPE_GROUP: {
                    Group group = db.groupDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    if (group == null || group.bytesGroupOwnerIdentity != null) {
                        // no group, or joined group
                        return;
                    }
                    break;
                }
                case Discussion.TYPE_GROUP_V2: {
                    Group2 group2 = db.group2Dao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    if (group2 == null || !group2.ownPermissionChangeSettings) {
                        // no group, or not allowed to change settings
                        return;
                    }
                }
            }


            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussion.id);
            if (discussionCustomization == null) {
                discussionCustomization = new DiscussionCustomization(discussion.id);
                db.discussionCustomizationDao().insert(discussionCustomization);
            }

            int version = 0;
            if (discussionCustomization.sharedSettingsVersion != null) {
                version = discussionCustomization.sharedSettingsVersion + 1;
            }

            // update our own discussion
            discussionCustomization.sharedSettingsVersion = version;
            discussionCustomization.settingReadOnce = readOnce;
            discussionCustomization.settingVisibilityDuration = visibilityDuration;
            discussionCustomization.settingExistenceDuration = existenceDuration;

            JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();

            // send the json to others
            Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, discussion.bytesOwnedIdentity, true, null);
            if (message != null) {
                db.discussionCustomizationDao().update(discussionCustomization); // only update the discussion if we could successfully build the message
                message.id = db.messageDao().insert(message);
                message.postSettingsMessage(false, null);
            }
        });
    }

    // endregion

    public interface SettingsChangedListener{
        void onSettingsChanged(@Nullable DiscussionCustomization discussionCustomization);
        void onLockedOrGroupAdminChanged(boolean locked, boolean nonAdminGroup);
    }
}
