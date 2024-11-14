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

package io.olvid.messenger.databases.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.StringUtils;

@Entity(tableName = Group2.TABLE_NAME,
        primaryKeys = {Group2.BYTES_OWNED_IDENTITY, Group2.BYTES_GROUP_IDENTIFIER},
        foreignKeys = {
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = Group2.BYTES_OWNED_IDENTITY,
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(Group2.BYTES_OWNED_IDENTITY),
        }
)
public class Group2 {
    public static final String TABLE_NAME = "group2_table";

    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_GROUP_IDENTIFIER = "bytes_group_identifier";
    public static final String KEYCLOAK_MANAGED = "keycloak_managed";

    public static final String NAME = "name";
    public static final String PHOTO_URL = "photo_url";
    public static final String GROUP_MEMBERS_NAMES = "group_members_names";
    public static final String UPDATE_IN_PROGRESS = "update_in_progress";
    public static final String NEW_PUBLISHED_DETAILS = "new_published_details";

    public static final String OWN_PERMISSION_ADMIN = "own_permission_admin";
    public static final String OWN_PERMISSION_REMOTE_DELETE_ANYTHING = "own_permission_remote_delete_anything";
    public static final String OWN_PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES = "own_permission_edit_or_remote_delete_own_messages";
    public static final String OWN_PERMISSION_CHANGE_SETTINGS = "own_permission_change_settings";
    public static final String OWN_PERMISSION_SEND_MESSAGE = "own_permission_send_message";

    public static final String CUSTOM_NAME = "custom_name";
    public static final String CUSTOM_PHOTO_URL = "custom_photo_url"; // set to "" to remove the default photo_url, null to use the default
    public static final String PERSONAL_NOTE = "personal_note";
    public static final String FULL_SEARCH_FIELD = "full_search_field";

    public static final int PUBLISHED_DETAILS_NOTHING_NEW = 0;
    public static final int PUBLISHED_DETAILS_NEW_UNSEEN = 1;
    public static final int PUBLISHED_DETAILS_NEW_SEEN = 2;

    public static final int UPDATE_NONE = 0;
    public static final int UPDATE_SYNCING = 1;
    public static final int UPDATE_CREATING = 2;


    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = BYTES_GROUP_IDENTIFIER)
    @NonNull
    public byte[] bytesGroupIdentifier;

    @ColumnInfo(name = KEYCLOAK_MANAGED)
    public boolean keycloakManaged;


    @ColumnInfo(name = NAME)
    @Nullable
    public String name;

    @ColumnInfo(name = PHOTO_URL)
    @Nullable
    public String photoUrl;

    @ColumnInfo(name = GROUP_MEMBERS_NAMES)
    @NonNull
    public String groupMembersNames;

    @ColumnInfo(name = UPDATE_IN_PROGRESS)
    public int updateInProgress;

    @ColumnInfo(name = NEW_PUBLISHED_DETAILS)
    public int newPublishedDetails;

    @ColumnInfo(name = OWN_PERMISSION_ADMIN)
    public boolean ownPermissionAdmin;

    @ColumnInfo(name = OWN_PERMISSION_REMOTE_DELETE_ANYTHING)
    public boolean ownPermissionRemoteDeleteAnything;

    @ColumnInfo(name = OWN_PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES)
    public boolean ownPermissionEditOrRemoteDeleteOwnMessages;

    @ColumnInfo(name = OWN_PERMISSION_CHANGE_SETTINGS)
    public boolean ownPermissionChangeSettings;

    @ColumnInfo(name = OWN_PERMISSION_SEND_MESSAGE)
    public boolean ownPermissionSendMessage;

    @ColumnInfo(name = CUSTOM_NAME)
    @Nullable
    public String customName;

    @ColumnInfo(name = CUSTOM_PHOTO_URL)
    @Nullable
    public String customPhotoUrl;

    @ColumnInfo(name = PERSONAL_NOTE)
    @Nullable
    public String personalNote;

    @ColumnInfo(name = FULL_SEARCH_FIELD)
    @NonNull
    public String fullSearchField;

    @NonNull
    public String getCustomName() {
        if ((customName == null && name == null)
                ||  Objects.equals("", customName)) {
            return groupMembersNames;
        } else if (customName == null) {
            return name;
        }
        return customName;
    }


    // return the group name/custom name, or the truncated members list
    @NonNull
    public String getTruncatedCustomName() {
        if (name == null && customName == null && groupMembersNames.length() > 80) {
            String separator = App.getContext().getString(R.string.text_contact_names_separator);
            int pos = groupMembersNames.indexOf(separator, 80);
            if (pos != -1) {
                return groupMembersNames.substring(0, pos) + "…";
            }
        }
        return getCustomName();
    }

    @Nullable
    public String getCustomPhotoUrl() {
        if (customPhotoUrl == null) {
            return photoUrl;
        } else if (customPhotoUrl.isEmpty()) {
            return null;
        }
        return customPhotoUrl;
    }


    // default constructor used by Room
    public Group2(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, boolean keycloakManaged, @Nullable String name, @Nullable String photoUrl, @NonNull String groupMembersNames, int updateInProgress, int newPublishedDetails, boolean ownPermissionAdmin, boolean ownPermissionRemoteDeleteAnything, boolean ownPermissionEditOrRemoteDeleteOwnMessages, boolean ownPermissionChangeSettings, boolean ownPermissionSendMessage, @Nullable String customName, @Nullable String customPhotoUrl, @Nullable String personalNote, @NonNull String fullSearchField) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        this.keycloakManaged = keycloakManaged;
        this.name = name;
        this.photoUrl = photoUrl;
        this.groupMembersNames = groupMembersNames;
        this.updateInProgress = updateInProgress;
        this.newPublishedDetails = newPublishedDetails;
        this.ownPermissionAdmin = ownPermissionAdmin;
        this.ownPermissionRemoteDeleteAnything = ownPermissionRemoteDeleteAnything;
        this.ownPermissionEditOrRemoteDeleteOwnMessages = ownPermissionEditOrRemoteDeleteOwnMessages;
        this.ownPermissionChangeSettings = ownPermissionChangeSettings;
        this.ownPermissionSendMessage = ownPermissionSendMessage;
        this.customName = customName;
        this.customPhotoUrl = customPhotoUrl;
        this.personalNote = personalNote;
        this.fullSearchField = fullSearchField;
    }

    @Ignore
    // constructor for new groups
    public Group2(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, boolean keycloakManaged, @Nullable String name, @Nullable String photoUrl, int newPublishedDetails, HashSet<GroupV2.Permission> ownPermissions) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        this.keycloakManaged = keycloakManaged;
        this.name = name;
        this.photoUrl = photoUrl;
        this.updateInProgress = UPDATE_NONE;
        this.groupMembersNames = "";
        this.newPublishedDetails = newPublishedDetails;
        this.ownPermissionAdmin = ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN);
        this.ownPermissionRemoteDeleteAnything = ownPermissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING);
        this.ownPermissionEditOrRemoteDeleteOwnMessages = ownPermissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);
        this.ownPermissionChangeSettings = ownPermissions.contains(GroupV2.Permission.CHANGE_SETTINGS);
        this.ownPermissionSendMessage = ownPermissions.contains(GroupV2.Permission.SEND_MESSAGE);
        this.customName = null;
        this.customPhotoUrl = null;
        this.personalNote = null;
        this.fullSearchField = "";
    }

    @NonNull
    public String computeFullSearch(@NonNull List<String> membersFullSearch) {
        String suffix = "";
        if (customName != null) {
            suffix += " " + customName;
        }
        if (personalNote != null) {
            suffix += " " + personalNote;
        }
        if (name != null) {
            suffix += " " + name;
        }
        return StringUtils.unAccent(String.join(" ", membersFullSearch) + suffix);
    }
}
