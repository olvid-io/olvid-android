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

package io.olvid.messenger.databases.tasks.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;

@JsonIgnoreProperties(ignoreUnknown = true)
class AppBackupPojo_0 {
    public SettingsPojo_0 settings;
    public List<OwnedIdentityPojo_0> owned_identities;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class OwnedIdentityPojo_0 {
    public byte[] owned_identity;
    public String custom_name;
    public byte[] unlock_password;
    public byte[] unlock_salt;
    public Boolean mute_notifications;
    public Boolean mute_notifications_except_mentioned;
    public Long mute_notification_timestamp;
    public Boolean show_neutral_notification_when_hidden;
    public List<ContactPojo_0> contacts;
    public List<GroupPojo_0> groups;
    public List<Group2Pojo_0> groups2;

    @JsonIgnore
    boolean isEmpty() {
        return (contacts == null || contacts.isEmpty()) &&
                (groups == null || groups.isEmpty()) &&
                (groups2 == null || groups2.isEmpty()) &&
                custom_name == null &&
                unlock_password == null &&
                unlock_salt == null &&
                (mute_notifications == null || !mute_notifications) &&
                (show_neutral_notification_when_hidden == null || !show_neutral_notification_when_hidden);
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ContactPojo_0 {
    public byte[] contact_identity;
    public String custom_name;
    public Integer custom_hue;
    public String personal_note;
    public DiscussionCustomizationPojo_0 discussion_customization;

    @JsonIgnore
    boolean isEmpty() {
        return custom_name == null &&
                custom_hue == null &&
                personal_note == null &&
                discussion_customization == null;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GroupPojo_0 {
    public byte[] group_uid; // only the uid, not the owner
    public byte[] group_owner_identity;
    public String custom_name;
    public String personal_note;
    public DiscussionCustomizationPojo_0 discussion_customization;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @JsonIgnore
    boolean isEmpty() {
        return custom_name == null &&
                personal_note == null &&
                discussion_customization == null;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Group2Pojo_0 {
    public byte[] group_identifier;
    public String custom_name;
    public String personal_note;
    public DiscussionCustomizationPojo_0 discussion_customization;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @JsonIgnore
    boolean isEmpty() {
        return custom_name == null &&
                personal_note == null &&
                discussion_customization == null;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class DiscussionCustomizationPojo_0 {
    public String serialized_color_json;
    public Boolean send_read_receipt;
    public boolean mute_notifications;
    public Long mute_notification_timestamp;
    public Boolean auto_open_limited_visibility;
    public Boolean retain_wiped_outbound;
    public Long retention_count;
    public Long retention_duration;
    public Integer shared_settings_version;
    public Long settings_existence_duration;
    public Long settings_visibility_duration;
    public boolean settings_read_once;
    public boolean pinned;

    public DiscussionCustomizationPojo_0() {
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @JsonIgnore
    boolean isEmpty() {
        return !pinned &&
                serialized_color_json == null &&
                send_read_receipt == null &&
                !mute_notifications &&
                auto_open_limited_visibility == null &&
                retain_wiped_outbound == null &&
                retention_count == null &&
                retention_duration == null &&
                (shared_settings_version == null || (settings_existence_duration == null && settings_visibility_duration == null && !settings_read_once));
    }

    @JsonIgnore
    public static DiscussionCustomizationPojo_0 of(@NonNull Discussion discussion, @Nullable DiscussionCustomization discussionCustomization, boolean backupSharedSettings) {
        DiscussionCustomizationPojo_0 discussionCustomizationPojo = new DiscussionCustomizationPojo_0();
        if (discussionCustomization != null) {
            discussionCustomizationPojo.serialized_color_json = discussionCustomization.serializedColorJson;
            discussionCustomizationPojo.send_read_receipt = discussionCustomization.prefSendReadReceipt;
            if (discussionCustomization.shouldMuteNotifications()) {
                discussionCustomizationPojo.mute_notifications = true;
                discussionCustomizationPojo.mute_notification_timestamp = discussionCustomization.prefMuteNotificationsTimestamp;
            }
            discussionCustomizationPojo.auto_open_limited_visibility = discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages;
            discussionCustomizationPojo.retain_wiped_outbound = discussionCustomization.prefRetainWipedOutboundMessages;
            discussionCustomizationPojo.retention_count = discussionCustomization.prefDiscussionRetentionCount;
            discussionCustomizationPojo.retention_duration = discussionCustomization.prefDiscussionRetentionDuration;
            if (backupSharedSettings) {
                JsonExpiration expiration = discussionCustomization.getExpirationJson();
                if (expiration != null) {
                    discussionCustomizationPojo.settings_existence_duration = expiration.getExistenceDuration();
                    discussionCustomizationPojo.settings_visibility_duration = expiration.getVisibilityDuration();
                    discussionCustomizationPojo.settings_read_once = expiration.getReadOnce();
                }
            }
        }
        discussionCustomizationPojo.pinned = discussion.pinned != 0;

        if (discussionCustomizationPojo.isEmpty()) {
            return null;
        }
        return discussionCustomizationPojo;
    }

    // returns true if some shared settings were restored
    public boolean applyTo(@NonNull DiscussionCustomization discussionCustomization, @NonNull Discussion discussion, boolean restoreSharedSettings) {
        boolean sharedSettingsRestored = false;
        discussionCustomization.serializedColorJson = serialized_color_json;
        discussionCustomization.prefSendReadReceipt = send_read_receipt;
        discussionCustomization.prefMuteNotifications = mute_notifications;
        if (discussionCustomization.prefMuteNotifications) {
            discussionCustomization.prefMuteNotificationsTimestamp = mute_notification_timestamp;
        }
        discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = auto_open_limited_visibility;
        discussionCustomization.prefRetainWipedOutboundMessages = retain_wiped_outbound;
        discussionCustomization.prefDiscussionRetentionCount = retention_count;
        discussionCustomization.prefDiscussionRetentionDuration = retention_duration;
        if (restoreSharedSettings &&
                shared_settings_version != null &&
                (discussionCustomization.sharedSettingsVersion == null || shared_settings_version > discussionCustomization.sharedSettingsVersion)) {
            sharedSettingsRestored = true;
            discussionCustomization.sharedSettingsVersion = shared_settings_version;
            discussionCustomization.settingExistenceDuration = settings_existence_duration;
            discussionCustomization.settingVisibilityDuration = settings_visibility_duration;
            discussionCustomization.settingReadOnce = settings_read_once;
        }
        discussion.pinned = pinned ? 1 : 0;
        return sharedSettingsRestored;
    }
}