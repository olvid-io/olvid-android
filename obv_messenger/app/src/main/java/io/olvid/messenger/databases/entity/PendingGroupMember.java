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

package io.olvid.messenger.databases.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = PendingGroupMember.TABLE_NAME,
        primaryKeys = {PendingGroupMember.BYTES_IDENTITY, PendingGroupMember.BYTES_OWNED_IDENTITY, PendingGroupMember.BYTES_GROUP_OWNER_AND_UID},
        foreignKeys = {
                @ForeignKey(entity = Group.class,
                        parentColumns = {Group.BYTES_GROUP_OWNER_AND_UID, Group.BYTES_OWNED_IDENTITY},
                        childColumns = {PendingGroupMember.BYTES_GROUP_OWNER_AND_UID, PendingGroupMember.BYTES_OWNED_IDENTITY},
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index({PendingGroupMember.BYTES_GROUP_OWNER_AND_UID, PendingGroupMember.BYTES_OWNED_IDENTITY}),
        }
)
public class PendingGroupMember {
    public static final String TABLE_NAME = "pending_group_member_table";

    public static final String BYTES_IDENTITY = "bytes_identity";
    public static final String BYTES_GROUP_OWNER_AND_UID = "bytes_group_owner_and_uid";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String DISPLAY_NAME = "display_name";
    public static final String DECLINED = "declined";

    @ColumnInfo(name = BYTES_IDENTITY)
    @NonNull
    public byte[] bytesIdentity;

    @ColumnInfo(name = DISPLAY_NAME)
    @NonNull
    public String displayName;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = BYTES_GROUP_OWNER_AND_UID)
    @NonNull
    public byte[] bytesGroupOwnerAndUid;

    @ColumnInfo(name = DECLINED)
    public boolean declined;

    // Default constructor used by Room
    public PendingGroupMember(@NonNull byte[] bytesIdentity, @NonNull String displayName, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupOwnerAndUid, boolean declined) {
        this.bytesIdentity = bytesIdentity;
        this.displayName = displayName;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
        this.declined = declined;
    }
}
