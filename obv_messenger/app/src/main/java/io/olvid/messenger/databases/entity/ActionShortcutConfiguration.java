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

package io.olvid.messenger.databases.entity;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.olvid.messenger.AppSingleton;

@Entity(
        tableName = ActionShortcutConfiguration.TABLE_NAME,
        foreignKeys = {
                @ForeignKey(entity = Discussion.class,
                        parentColumns = "id",
                        childColumns = ActionShortcutConfiguration.DISCUSSION_ID,
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(value = {ActionShortcutConfiguration.DISCUSSION_ID}),
        }
)
public class ActionShortcutConfiguration {
    public static final String TABLE_NAME = "action_shortcut_configuration_table";

    public static final String APP_WIDGET_ID = "app_widget_id";
    public static final String DISCUSSION_ID = "discussion_id";
    public static final String SERIALIZED_CONFIGURATION = "serialized_configuration";

    @PrimaryKey
    @ColumnInfo(name = APP_WIDGET_ID)
    public int appWidgetId;

    @ColumnInfo(name = DISCUSSION_ID)
    public long discussionId;

    @ColumnInfo(name = SERIALIZED_CONFIGURATION)
    @NonNull
    public String serializedConfiguration;

    // default constructor required for Room
    public ActionShortcutConfiguration(int appWidgetId, long discussionId, @NonNull String serializedConfiguration) {
        this.appWidgetId = appWidgetId;
        this.discussionId = discussionId;
        this.serializedConfiguration = serializedConfiguration;
    }

    @Ignore
    public ActionShortcutConfiguration(int appWidgetId, long discussionId, @NonNull JsonConfiguration jsonConfiguration) throws JsonProcessingException {
        this.appWidgetId = appWidgetId;
        this.discussionId = discussionId;
        this.serializedConfiguration = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonConfiguration);
    }

    @Nullable
    public JsonConfiguration getJsonConfiguration() {
        try {
            return AppSingleton.getJsonObjectMapper().readValue(serializedConfiguration, JsonConfiguration.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setJsonConfiguration(@NonNull JsonConfiguration jsonConfiguration) throws JsonProcessingException {
        this.serializedConfiguration = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonConfiguration);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonConfiguration {
        public static final String ICON_SEND = "send";
        public static final String ICON_OK = "ok";
        public static final String ICON_WARN = "warn";
        public static final String ICON_ERROR = "error";
        public static final String ICON_BURN = "burn";
        public static final String ICON_GRASS = "grass";
        public static final String ICON_HAND = "hand";
        public static final String ICON_HEART = "heart";
        public static final String ICON_HEXES = "hexes";
        public static final String ICON_QUESTION = "question";
        public static final String ICON_STAR = "star";
        public static final String ICON_THUMB = "thumb";

        public static final String[] ICONS = new String[] {
                ICON_SEND, ICON_OK, ICON_WARN, ICON_ERROR,
                ICON_BURN, ICON_GRASS, ICON_HAND, ICON_HEART,
                ICON_QUESTION, ICON_STAR, ICON_HEXES, ICON_THUMB
        };

        public String widgetLabel;
        public String widgetIcon;
        @ColorInt public Integer widgetIconTint;
        public boolean widgetShowBadge;

        public String messageToSend;

        public boolean confirmBeforeSend;
        public boolean vibrateOnSend;
    }
}
