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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import java.util.HashSet;

import io.olvid.engine.datatypes.containers.GroupV2;

@Entity(
        tableName = Group2Member.TABLE_NAME,
        primaryKeys = {Group2Member.BYTES_OWNED_IDENTITY, Group2Member.BYTES_GROUP_IDENTIFIER, Group2Member.BYTES_CONTACT_IDENTITY},
        foreignKeys = {
                @ForeignKey(entity = Group2.class,
                        parentColumns = {Group2.BYTES_OWNED_IDENTITY, Group2.BYTES_GROUP_IDENTIFIER},
                        childColumns = {Group2Member.BYTES_OWNED_IDENTITY, Group2Member.BYTES_GROUP_IDENTIFIER},
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Contact.class,
                        parentColumns = {Contact.BYTES_OWNED_IDENTITY, Contact.BYTES_CONTACT_IDENTITY},
                        childColumns = {Group2Member.BYTES_OWNED_IDENTITY, Group2Member.BYTES_CONTACT_IDENTITY},
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(value = {Group2Member.BYTES_OWNED_IDENTITY}),
                @Index(value = {Group2Member.BYTES_OWNED_IDENTITY, Group2Member.BYTES_GROUP_IDENTIFIER}),
                @Index(value = {Group2Member.BYTES_OWNED_IDENTITY, Group2Member.BYTES_CONTACT_IDENTITY}),
        }
)
public class Group2Member {
    public static final String TABLE_NAME = "group2_member_table";

    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_GROUP_IDENTIFIER = "bytes_group_identifier";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";

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


    // default constructor used by Room
    public Group2Member(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity, boolean permissionAdmin, boolean permissionRemoteDeleteAnything, boolean permissionEditOrRemoteDeleteOwnMessages, boolean permissionChangeSettings, boolean permissionSendMessage) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        this.bytesContactIdentity = bytesContactIdentity;

        this.permissionAdmin = permissionAdmin;
        this.permissionRemoteDeleteAnything = permissionRemoteDeleteAnything;
        this.permissionEditOrRemoteDeleteOwnMessages = permissionEditOrRemoteDeleteOwnMessages;
        this.permissionChangeSettings = permissionChangeSettings;
        this.permissionSendMessage = permissionSendMessage;
    }

    @Ignore
    public Group2Member(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity, HashSet<GroupV2.Permission> permissions) throws Exception {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        this.bytesContactIdentity = bytesContactIdentity;

        this.permissionAdmin = permissions.contains(GroupV2.Permission.GROUP_ADMIN);
        this.permissionRemoteDeleteAnything = permissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING);
        this.permissionEditOrRemoteDeleteOwnMessages = permissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);
        this.permissionChangeSettings = permissions.contains(GroupV2.Permission.CHANGE_SETTINGS);
        this.permissionSendMessage = permissions.contains(GroupV2.Permission.SEND_MESSAGE);
    }

}
