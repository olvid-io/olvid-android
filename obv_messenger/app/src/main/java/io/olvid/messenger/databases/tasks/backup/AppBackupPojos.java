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

package io.olvid.messenger.databases.tasks.backup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

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
    public boolean mute_notifications;
    public Long mute_notification_timestamp;
    public boolean show_neutral_notification_when_hidden;
    public List<ContactPojo_0> contacts;
    public List<GroupPojo_0> groups;

    @JsonIgnore
    boolean isEmpty() {
        return (contacts == null || contacts.isEmpty()) &&
                (groups == null || groups.isEmpty());
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @JsonIgnore
    boolean isEmpty() {
        return serialized_color_json == null &&
                send_read_receipt == null &&
                !mute_notifications &&
                auto_open_limited_visibility == null &&
                retain_wiped_outbound == null &&
                retention_count == null &&
                retention_duration == null &&
                (shared_settings_version == null || (settings_existence_duration == null && settings_visibility_duration == null && !settings_read_once));
    }
}