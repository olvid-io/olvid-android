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

package io.olvid.messenger.widget;

import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.Arrays;
import java.util.List;

import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class ActionShortcutConfigurationViewModel extends ViewModel {
    private final MutableLiveData<byte[]> bytesOwnedIdentityLiveData;
    private final LiveData<OwnedIdentity> ownedIdentityLiveData;
    private final MutableLiveData<Long> actionDiscussionIdLiveData;
    private final LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>> discussionListLiveData;
    private final LiveData<DiscussionDao.DiscussionAndGroupMembersNames> discussionLiveData;
    private String actionMessage = null;
    private boolean actionConfirmBeforeSending = false;
    private boolean actionVibrateAfterSending = true;

    private final MutableLiveData<String> widgetLabelLiveData;
    private final MutableLiveData<String> widgetIconLiveData;
    private final MutableLiveData<Integer> widgetTintLiveData;
    private final IconAndTintLiveData widgetIconAndTintLiveData;
    private final MutableLiveData<Boolean> widgetShowBadgeLiveData;

    private final MutableLiveData<Boolean> validLiveData;

    public ActionShortcutConfigurationViewModel() {
        bytesOwnedIdentityLiveData = new MutableLiveData<>(null);
        ownedIdentityLiveData = Transformations.switchMap(bytesOwnedIdentityLiveData, (byte[] bytesOwnedIdentity) -> {
            if (bytesOwnedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().ownedIdentityDao().getLiveData(bytesOwnedIdentity);
        });
        actionDiscussionIdLiveData = new MutableLiveData<>(null);
        discussionListLiveData = Transformations.switchMap(bytesOwnedIdentityLiveData, (byte[] bytesOwnedIdentity) -> {
            if (bytesOwnedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().discussionDao().getAllNotLockedWithGroupMembersNames(bytesOwnedIdentity);
        });

        discussionLiveData = Transformations.switchMap(actionDiscussionIdLiveData, (Long discussionId) -> {
            if (discussionId == null) {
                return new MutableLiveData<>(null);
            }
            return AppDatabase.getInstance().discussionDao().getWithGroupMembersNames(discussionId);
        });

        widgetLabelLiveData = new MutableLiveData<>(null);
        widgetIconLiveData = new MutableLiveData<>(ActionShortcutConfiguration.JsonConfiguration.ICON_SEND);
        widgetTintLiveData = new MutableLiveData<>(null);
        widgetShowBadgeLiveData = new MutableLiveData<>(true);
        widgetIconAndTintLiveData = new IconAndTintLiveData(widgetIconLiveData, widgetTintLiveData);
        validLiveData = new MutableLiveData<>(false);
    }

    private void checkValid() {
        if (actionDiscussionIdLiveData.getValue() == null || actionMessage == null || actionMessage.trim().length() == 0) {
            validLiveData.postValue(false);
        } else {
            validLiveData.postValue(true);
        }
    }

    // to call only on the main thread!
    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        if (this.bytesOwnedIdentityLiveData.getValue() != null && !Arrays.equals(bytesOwnedIdentity, this.bytesOwnedIdentityLiveData.getValue())) {
            this.actionDiscussionIdLiveData.setValue(null);
            checkValid();
        }
        this.bytesOwnedIdentityLiveData.setValue(bytesOwnedIdentity);
    }

    // to call only on the main thread!
    public void setActionDiscussionId(long discussionId) {
        this.actionDiscussionIdLiveData.setValue(discussionId);
        checkValid();
    }

    public LiveData<OwnedIdentity> getOwnedIdentityLiveData() {
        return ownedIdentityLiveData;
    }

    public LiveData<Long> getActionDiscussionIdLiveData() {
        return actionDiscussionIdLiveData;
    }

    public LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>> getDiscussionListLiveData() {
        return discussionListLiveData;
    }

    public LiveData<DiscussionDao.DiscussionAndGroupMembersNames> getDiscussionLiveData() {
        return discussionLiveData;
    }

    public String getActionMessage() {
        return actionMessage;
    }

    public void setActionMessage(String actionMessage) {
        this.actionMessage = actionMessage;
        checkValid();
    }

    public boolean isActionConfirmBeforeSending() {
        return actionConfirmBeforeSending;
    }

    public void setActionConfirmBeforeSending(boolean actionConfirmBeforeSending) {
        this.actionConfirmBeforeSending = actionConfirmBeforeSending;
    }

    public boolean isActionVibrateAfterSending() {
        return actionVibrateAfterSending;
    }

    public void setActionVibrateAfterSending(boolean actionVibrateAfterSending) {
        this.actionVibrateAfterSending = actionVibrateAfterSending;
    }

    public LiveData<Boolean> getValidLiveData() {
        return validLiveData;
    }

    public LiveData<String> getWidgetLabelLiveData() {
        return widgetLabelLiveData;
    }

    public LiveData<String> getWidgetIconLiveData() {
        return widgetIconLiveData;
    }

    public LiveData<Integer> getWidgetTintLiveData() {
        return widgetTintLiveData;
    }

    public LiveData<Pair<String, Integer>> getWidgetIconAndTineLiveData() {
        return widgetIconAndTintLiveData;
    }

    public LiveData<Boolean> getWidgetShowBadgeLiveData() {
        return widgetShowBadgeLiveData;
    }

    public void setWidgetLabel(String widgetLabel) {
        this.widgetLabelLiveData.postValue(widgetLabel);
    }

    public void setWidgetIcon(String widgetIcon) {
        this.widgetIconLiveData.postValue(widgetIcon);
    }

    public void setWidgetTint(Integer tint) {
        this.widgetTintLiveData.postValue(tint);
    }

    public void setWidgetShowBadge(boolean showBadge) {
        this.widgetShowBadgeLiveData.postValue(showBadge);
    }

    public static class IconAndTintLiveData extends MediatorLiveData<Pair<String, Integer>> {
        private String icon;
        private Integer tint;

        public IconAndTintLiveData(LiveData<String> iconLiveData, LiveData<Integer> tineLiveData) {
            addSource(iconLiveData, this::onIconChanged);
            addSource(tineLiveData, this::onTintChanged);
        }


        private void onIconChanged(String icon) {
            this.icon = icon;
            setValue(new Pair<>(icon, tint));
        }

        private void onTintChanged(Integer tint) {
            this.tint = tint;
            setValue(new Pair<>(icon, tint));
        }
    }
}
