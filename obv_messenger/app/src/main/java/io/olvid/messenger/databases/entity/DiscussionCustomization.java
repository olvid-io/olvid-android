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

package io.olvid.messenger.databases.entity;



import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.File;
import java.util.Arrays;

import io.olvid.messenger.AppSingleton;

@SuppressWarnings("CanBeFinal")
@Entity(tableName = DiscussionCustomization.TABLE_NAME,
        foreignKeys = {
                @ForeignKey(entity = Discussion.class,
                parentColumns = "id",
                childColumns = DiscussionCustomization.DISCUSSION_ID,
                onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(DiscussionCustomization.DISCUSSION_ID),
        })
public class DiscussionCustomization {
    public static final String TABLE_NAME = "discussion_customization_table";

    public static final String DISCUSSION_ID = "discussion_id";
    public static final String SERIALIZED_COLOR_JSON = "serialized_color_json";
    public static final String BACKGROUND_IMAGE_URL = "background_image_url";
    public static final String PREF_SEND_READ_RECEIPT = "pref_send_read_receipt";
    public static final String PREF_MUTE_NOTIFICATIONS = "pref_mute_notifications";
    public static final String PREF_MUTE_NOTIFICATIONS_TIMESTAMP = "pref_mute_notifications_timestamp"; // when to stop muting notifications, null if unlimited
    public static final String PREF_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_MESSAGES = "pref_auto_open_limited_visibility_inbound";
    public static final String PREF_RETAIN_WIPED_OUTBOUND_MESSAGES = "pref_retain_wiped_outbound_messages";
    public static final String PREF_DISCUSSION_RETENTION_COUNT = "pref_discussion_retention_count"; // number of messages to keep -> null = use app setting, 0 = keep everything,
    public static final String PREF_DISCUSSION_RETENTION_DURATION = "pref_discussion_retention_duration"; // duration in seconds -> null = use app setting, 0 = keep everything,

    // shared preferences with other participants
    public static final String SHARED_SETTINGS_VERSION = "shared_settings_version";
    public static final String SETTING_EXISTENCE_DURATION = "setting_existence_duration";
    public static final String SETTING_VISIBILITY_DURATION = "setting_visibility_duration";
    public static final String SETTING_READ_ONCE = "setting_read_once";


    @PrimaryKey
    @ColumnInfo(name = DISCUSSION_ID)
    public long discussionId;

    @ColumnInfo(name = SERIALIZED_COLOR_JSON)
    @Nullable
    public String serializedColorJson;

    @ColumnInfo(name = BACKGROUND_IMAGE_URL)
    @Nullable
    public String backgroundImageUrl;

    @ColumnInfo(name = PREF_SEND_READ_RECEIPT)
    @Nullable
    public Boolean prefSendReadReceipt;

    @ColumnInfo(name = PREF_MUTE_NOTIFICATIONS)
    public boolean prefMuteNotifications;

    @ColumnInfo(name = PREF_MUTE_NOTIFICATIONS_TIMESTAMP)
    @Nullable
    public Long prefMuteNotificationsTimestamp;

    @ColumnInfo(name = PREF_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_MESSAGES)
    @Nullable
    public Boolean prefAutoOpenLimitedVisibilityInboundMessages;

    @ColumnInfo(name = PREF_RETAIN_WIPED_OUTBOUND_MESSAGES)
    @Nullable
    public Boolean prefRetainWipedOutboundMessages;

    @ColumnInfo(name = PREF_DISCUSSION_RETENTION_COUNT)
    @Nullable
    public Long prefDiscussionRetentionCount; // -> null = use app setting, 0 = keep everything,

    @ColumnInfo(name = PREF_DISCUSSION_RETENTION_DURATION)
    @Nullable
    public Long prefDiscussionRetentionDuration; // -> null = use app setting, 0 = keep everything,

    @ColumnInfo(name = SHARED_SETTINGS_VERSION)
    @Nullable
    public Integer sharedSettingsVersion;

    @ColumnInfo(name = SETTING_EXISTENCE_DURATION)
    @Nullable
    public Long settingExistenceDuration;

    @ColumnInfo(name = SETTING_VISIBILITY_DURATION)
    @Nullable
    public Long settingVisibilityDuration;

    @ColumnInfo(name = SETTING_READ_ONCE)
    public boolean settingReadOnce;


    // default constructor required by Room
    public DiscussionCustomization(long discussionId, @Nullable String serializedColorJson, @Nullable String backgroundImageUrl, @Nullable Boolean prefSendReadReceipt, boolean prefMuteNotifications, @Nullable Long prefMuteNotificationsTimestamp, @Nullable Boolean prefAutoOpenLimitedVisibilityInboundMessages, @Nullable Boolean prefRetainWipedOutboundMessages, @Nullable Long prefDiscussionRetentionCount, @Nullable Long prefDiscussionRetentionDuration, @Nullable Integer sharedSettingsVersion, @Nullable Long settingExistenceDuration, @Nullable Long settingVisibilityDuration, boolean settingReadOnce) {
        this.discussionId = discussionId;
        this.serializedColorJson = serializedColorJson;
        this.backgroundImageUrl = backgroundImageUrl;
        this.prefSendReadReceipt = prefSendReadReceipt;
        this.prefMuteNotifications = prefMuteNotifications;
        this.prefMuteNotificationsTimestamp = prefMuteNotificationsTimestamp;
        this.prefAutoOpenLimitedVisibilityInboundMessages = prefAutoOpenLimitedVisibilityInboundMessages;
        this.prefRetainWipedOutboundMessages = prefRetainWipedOutboundMessages;
        this.prefDiscussionRetentionCount = prefDiscussionRetentionCount;
        this.prefDiscussionRetentionDuration = prefDiscussionRetentionDuration;
        this.sharedSettingsVersion = sharedSettingsVersion;
        this.settingExistenceDuration = settingExistenceDuration;
        this.settingVisibilityDuration = settingVisibilityDuration;
        this.settingReadOnce = settingReadOnce;
    }


    @Ignore
    public DiscussionCustomization(long discussionId) {
        this.discussionId = discussionId;
        this.serializedColorJson = null;
        this.backgroundImageUrl = null;
        this.prefSendReadReceipt = null;
        this.prefMuteNotifications = false;
        this.prefMuteNotificationsTimestamp = null;
        this.prefAutoOpenLimitedVisibilityInboundMessages = null;
        this.prefRetainWipedOutboundMessages = null;
        this.prefDiscussionRetentionCount = null;
        this.prefDiscussionRetentionDuration = null;
        this.sharedSettingsVersion = null;
        this.settingExistenceDuration = null;
        this.settingVisibilityDuration = null;
        this.settingReadOnce = false;
    }


    public String buildBackgroundImagePath() {
        return AppSingleton.DISCUSSION_BACKGROUNDS_DIRECTORY + File.separator + discussionId + "-" + System.currentTimeMillis();
    }


    public boolean shouldMuteNotifications() {
        if (prefMuteNotificationsTimestamp == null || prefMuteNotificationsTimestamp > System.currentTimeMillis()) {
            return prefMuteNotifications;
        }
        return false;
    }

    @Nullable
    public ColorJson getColorJson() {
        if (serializedColorJson == null) {
            return null;
        }
        try {
            return AppSingleton.getJsonObjectMapper().readValue(serializedColorJson, ColorJson.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setColorJson(ColorJson colorJson) throws Exception {
        if (colorJson == null) {
            this.serializedColorJson = null;
        } else {
            this.serializedColorJson = AppSingleton.getJsonObjectMapper().writeValueAsString(colorJson);
        }
    }

    @Nullable
    public JsonSharedSettings getSharedSettingsJson() {
        if (sharedSettingsVersion == null) {
            return null;
        }
        JsonSharedSettings jsonSharedSettings = new JsonSharedSettings();
        jsonSharedSettings.setVersion(sharedSettingsVersion);
        jsonSharedSettings.setJsonExpiration(getExpirationJson());
        return jsonSharedSettings;
    }

    @Nullable
    public Message.JsonExpiration getExpirationJson() {
        if (sharedSettingsVersion == null) {
            return null;
        }
        if (!settingReadOnce
                && settingVisibilityDuration == null
                && settingExistenceDuration == null) {
            return null;
        }
        Message.JsonExpiration expiration = new Message.JsonExpiration();
        expiration.setReadOnce(settingReadOnce);
        expiration.setVisibilityDuration(settingVisibilityDuration);
        expiration.setExistenceDuration(settingExistenceDuration);
        return expiration;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColorJson {
        public int color;
        public float alpha;

        public ColorJson() {
        }

        public ColorJson(int color, float alpha) {
            this.color = color;
            this.alpha = alpha;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonSharedSettings {
        int version;
        byte[] groupUid;
        byte[] groupOwner;
        Message.JsonExpiration jsonExpiration;

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        @JsonProperty("guid")
        public byte[] getGroupUid() {
            return groupUid;
        }

        @JsonProperty("guid")
        public void setGroupUid(byte[] groupUid) {
            this.groupUid = groupUid;
        }

        @JsonProperty("go")
        public byte[] getGroupOwner() {
            return groupOwner;
        }

        @JsonProperty("go")
        public void setGroupOwner(byte[] groupOwner) {
            this.groupOwner = groupOwner;
        }

        @JsonProperty("exp")
        public Message.JsonExpiration getJsonExpiration() {
            return jsonExpiration;
        }

        @JsonProperty("exp")
        public void setJsonExpiration(Message.JsonExpiration jsonExpiration) {
            this.jsonExpiration = jsonExpiration;
        }

        @JsonIgnore
        public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
            if (bytesGroupOwnerAndUid.length < 32) {
                throw new Exception();
            }
            byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
            byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
            setGroupOwner(bytesGroupOwner);
            setGroupUid(bytesGroupUid);
        }
    }
}
