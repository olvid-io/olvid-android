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

package io.olvid.messenger.viewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Message;


public class DiscussionSettingsViewModel extends ViewModel {
    private final AppDatabase db;
    private final MutableLiveData<Long> discussionIdLiveData;
    private final MutableLiveData<byte[]> bytesGroupOwnerAndUidLiveData;
    private final LiveData<DiscussionCustomization> discussionCustomization;
    private final LiveData<Group> discussionGroup;
    private byte[] bytesOwnedIdentity;
    private boolean locked;

    private byte[] bytesGroupOwnerAndUid;
    private boolean settingsModified;
    private boolean settingsReadOnce;
    private Long settingsVisibilityDuration;
    private Long settingsExistenceDuration;

    public DiscussionSettingsViewModel() {
        db = AppDatabase.getInstance();
        discussionIdLiveData = new MutableLiveData<>();
        discussionCustomization = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionCustomizationDao().getLiveData(discussionId);
        });
        bytesGroupOwnerAndUidLiveData = new MutableLiveData<>();
        discussionGroup = Transformations.switchMap(bytesGroupOwnerAndUidLiveData, bytesGroupOwnerAndUid -> {
            if (bytesGroupOwnerAndUid == null || bytesOwnedIdentity == null) {
                return null;
            }
            return db.groupDao().getLiveData(bytesOwnedIdentity, bytesGroupOwnerAndUid);
        });

        settingsModified = false;
        settingsReadOnce = false;
        settingsVisibilityDuration = null;
        settingsExistenceDuration = null;
    }

    public void updateCustomizationAndNotifyPeers() {
        App.runThread(() -> {
            Long discussionId = this.discussionIdLiveData.getValue();
            if (discussionId == null || bytesOwnedIdentity == null) {
                return;
            }

            // first check whether you have permission to modify these settings
            Discussion discussion = db.discussionDao().getById(discussionId);
            if (discussion == null) {
                return;
            }

            if (discussion.bytesGroupOwnerAndUid != null) {
                // group discussion, check if you are the owner
                Group group = db.groupDao().get(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
                if (group == null || group.bytesGroupOwnerIdentity != null) {
                    // no group, or joined group
                    return;
                }
            }

            DiscussionCustomization discussionCustomization = this.discussionCustomization.getValue();
            if (discussionCustomization == null) {
                discussionCustomization = new DiscussionCustomization(discussionId);
                db.discussionCustomizationDao().insert(discussionCustomization);
            }

            int version = 0;
            if (discussionCustomization.sharedSettingsVersion != null) {
                version = discussionCustomization.sharedSettingsVersion + 1;
            }

            // update our own discussion
            discussionCustomization.sharedSettingsVersion = version;
            discussionCustomization.settingReadOnce = settingsReadOnce;
            discussionCustomization.settingVisibilityDuration = settingsVisibilityDuration;
            discussionCustomization.settingExistenceDuration = settingsExistenceDuration;

            DiscussionCustomization.JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();

            // send the json to others
            Message message = Message.createDiscussionSettingsUpdateMessage(discussionId, jsonSharedSettings, bytesOwnedIdentity, true, null);
            if (message != null) {
                db.discussionCustomizationDao().update(discussionCustomization); // only update the discussion if we could successfully build the message
                message.id = db.messageDao().insert(message);
                message.post(false, false);
            }
        });
    }



    public void setDiscussionId(long discussionId) {
        discussionIdLiveData.postValue(discussionId);
    }

    public Long getDiscussionId() {
        return discussionIdLiveData.getValue();
    }

    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
    }

    public void setBytesGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) {
        this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
        bytesGroupOwnerAndUidLiveData.postValue(bytesGroupOwnerAndUid);
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isGroup() {
        return bytesGroupOwnerAndUid != null;
    }

    public LiveData<DiscussionCustomization> getDiscussionCustomization() {
        return discussionCustomization;
    }

    public LiveData<Group> getDiscussionGroup() {
        return discussionGroup;
    }

    public void updateSettingsFromCustomization(DiscussionCustomization discussionCustomization) {
        if (!settingsModified) {
            if (discussionCustomization != null) {
                this.settingsReadOnce = discussionCustomization.settingReadOnce;
                this.settingsVisibilityDuration = discussionCustomization.settingVisibilityDuration;
                this.settingsExistenceDuration = discussionCustomization.settingExistenceDuration;
            } else {
                this.settingsReadOnce = false;
                this.settingsVisibilityDuration = null;
                this.settingsExistenceDuration = null;
            }
        }
    }

    public boolean isSettingsModified() {
        return settingsModified;
    }

    public void setSettingsReadOnce(boolean settingsReadOnce) {
        if (settingsReadOnce ^ this.settingsReadOnce) {
            this.settingsModified = true;
            this.settingsReadOnce = settingsReadOnce;
        }
    }

    public void setSettingsVisibilityDuration(Long settingsVisibilityDuration) {
        if (!Objects.equals(settingsVisibilityDuration, this.settingsVisibilityDuration)) {
            this.settingsModified = true;
            this.settingsVisibilityDuration = settingsVisibilityDuration;
        }
    }

    public void setSettingsExistenceDuration(Long settingsExistenceDuration) {
        if (!Objects.equals(settingsExistenceDuration, this.settingsExistenceDuration)) {
            this.settingsModified = true;
            this.settingsExistenceDuration = settingsExistenceDuration;
        }
    }

    public boolean getSettingsReadOnce() {
        return settingsReadOnce;
    }

    public Long getSettingsVisibilityDuration() {
        return settingsVisibilityDuration;
    }

    public Long getSettingsExistenceDuration() {
        return settingsExistenceDuration;
    }
}
