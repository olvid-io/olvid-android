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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import java.util.HashSet;

import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask;
import io.olvid.messenger.settings.SettingsActivity;

@Entity(
        tableName = Group2PendingMember.TABLE_NAME,
        primaryKeys = {Group2PendingMember.BYTES_OWNED_IDENTITY, Group2PendingMember.BYTES_GROUP_IDENTIFIER, Group2PendingMember.BYTES_CONTACT_IDENTITY},
        foreignKeys = {
                @ForeignKey(entity = Group2.class,
                        parentColumns = {Group2.BYTES_OWNED_IDENTITY, Group2.BYTES_GROUP_IDENTIFIER},
                        childColumns = {Group2PendingMember.BYTES_OWNED_IDENTITY, Group2PendingMember.BYTES_GROUP_IDENTIFIER},
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(Group2PendingMember.BYTES_OWNED_IDENTITY),
                @Index({Group2PendingMember.BYTES_OWNED_IDENTITY, Group2PendingMember.BYTES_GROUP_IDENTIFIER}),
        }
)
public class Group2PendingMember {
    public static final String TABLE_NAME = "group2_pending_member_table";

    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_GROUP_IDENTIFIER = "bytes_group_identifier";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";

    public static final String DISPLAY_NAME = "display_name";
    public static final String SORT_DISPLAY_NAME = "sort_display_name";
    public static final String FULL_SEARCH_DISPLAY_NAME = "full_search_display_name";
    public static final String IDENTITY_DETAILS = "identity_details";

    public static final String PERMISSION_ADMIN = "permission_admin";
    public static final String PERMISSION_REMOTE_DELETE_ANYTHING = "permission_remote_delete_anything";
    public static final String PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES = "permission_edit_or_remote_delete_own_messages";
    public static final String PERMISSION_CHANGE_SETTINGS = "permission_change_settings";
    public static final String PERMISSION_SEND_MESSAGE = "permission_send_message";

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = BYTES_GROUP_IDENTIFIER)
    @NonNull
    public byte[] bytesGroupIdentifier;

    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @NonNull
    public byte[] bytesContactIdentity;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @ColumnInfo(name = DISPLAY_NAME)
    @NonNull
    public String displayName;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @ColumnInfo(name = SORT_DISPLAY_NAME)
    @NonNull
    public byte[] sortDisplayName;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @ColumnInfo(name = FULL_SEARCH_DISPLAY_NAME)
    @NonNull
    public String fullSearchDisplayName;

    @ColumnInfo(name = IDENTITY_DETAILS)
    @Nullable
    public String identityDetails;

    @ColumnInfo(name = PERMISSION_ADMIN)
    public boolean permissionAdmin;

    @ColumnInfo(name = PERMISSION_REMOTE_DELETE_ANYTHING)
    public boolean permissionRemoteDeleteAnything;

    @ColumnInfo(name = PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES)
    public boolean permissionEditOrRemoteDeleteOwnMessages;

    @ColumnInfo(name = PERMISSION_CHANGE_SETTINGS)
    public boolean permissionChangeSettings;

    @ColumnInfo(name = PERMISSION_SEND_MESSAGE)
    public boolean permissionSendMessage;


    // Constructor required by Room
    public Group2PendingMember(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity, @NonNull String displayName, @NonNull byte[] sortDisplayName, @NonNull String fullSearchDisplayName, @Nullable String identityDetails, boolean permissionAdmin, boolean permissionRemoteDeleteAnything, boolean permissionEditOrRemoteDeleteOwnMessages, boolean permissionChangeSettings, boolean permissionSendMessage) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        this.bytesContactIdentity = bytesContactIdentity;
        this.displayName = displayName;
        this.sortDisplayName = sortDisplayName;
        this.fullSearchDisplayName = fullSearchDisplayName;
        this.identityDetails = identityDetails;
        this.permissionAdmin = permissionAdmin;
        this.permissionRemoteDeleteAnything = permissionRemoteDeleteAnything;
        this.permissionEditOrRemoteDeleteOwnMessages = permissionEditOrRemoteDeleteOwnMessages;
        this.permissionChangeSettings = permissionChangeSettings;
        this.permissionSendMessage = permissionSendMessage;
    }

    @Ignore
    public Group2PendingMember(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity, @NonNull String serializedIdentityDetails, HashSet<GroupV2.Permission> permissions) throws Exception {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        this.bytesContactIdentity = bytesContactIdentity;
        this.setIdentityDetailsAndDisplayName(serializedIdentityDetails);

        this.permissionAdmin = permissions.contains(GroupV2.Permission.GROUP_ADMIN);
        this.permissionRemoteDeleteAnything = permissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING);
        this.permissionEditOrRemoteDeleteOwnMessages = permissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);
        this.permissionChangeSettings = permissions.contains(GroupV2.Permission.CHANGE_SETTINGS);
        this.permissionSendMessage = permissions.contains(GroupV2.Permission.SEND_MESSAGE);
    }

    public void setIdentityDetailsAndDisplayName(@NonNull String serializedIdentityDetails) throws Exception {
        JsonIdentityDetails jsonIdentityDetails = null;
        try {
            jsonIdentityDetails = AppSingleton.getJsonObjectMapper().readValue(serializedIdentityDetails, JsonIdentityDetails.class);
        } catch (Exception ignored) { }
        if (jsonIdentityDetails == null) {
            this.displayName = "";
            this.sortDisplayName = new byte[0];
            this.fullSearchDisplayName = "";
            this.identityDetails = null;
        } else {
            this.displayName = jsonIdentityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
            this.sortDisplayName = ContactDisplayNameFormatChangedTask.computeSortDisplayName(jsonIdentityDetails, null, SettingsActivity.getSortContactsByLastName());
            this.fullSearchDisplayName = StringUtils.unAccent(jsonIdentityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FOR_SEARCH, false));
            this.identityDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonIdentityDetails);
        }
    }
}
