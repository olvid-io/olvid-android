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
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

@Entity(
        tableName = ContactGroupJoin.TABLE_NAME,
        primaryKeys = {ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID, ContactGroupJoin.BYTES_OWNED_IDENTITY, ContactGroupJoin.BYTES_CONTACT_IDENTITY},
        foreignKeys = {
                @ForeignKey(entity = Group.class,
                        parentColumns = {Group.BYTES_GROUP_OWNER_AND_UID, Group.BYTES_OWNED_IDENTITY},
                        childColumns = {ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID, ContactGroupJoin.BYTES_OWNED_IDENTITY},
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Contact.class,
                        parentColumns = {Contact.BYTES_CONTACT_IDENTITY, Contact.BYTES_OWNED_IDENTITY},
                        childColumns = {ContactGroupJoin.BYTES_CONTACT_IDENTITY, ContactGroupJoin.BYTES_OWNED_IDENTITY},
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(value = {ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID, ContactGroupJoin.BYTES_OWNED_IDENTITY}),
                @Index(value = {ContactGroupJoin.BYTES_CONTACT_IDENTITY, ContactGroupJoin.BYTES_OWNED_IDENTITY})
        }
)


public class ContactGroupJoin {
    public static final String TABLE_NAME = "contact_group_join";

    public static final String BYTES_GROUP_OWNER_AND_UID = "bytes_group_owner_and_uid";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";
    public static final String TIMESTAMP = "timestamp";


    @ColumnInfo(name = BYTES_GROUP_OWNER_AND_UID)
    @NonNull
    public final byte[] bytesGroupUid;

    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @NonNull
    public final byte[] bytesContactIdentity;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public final byte[] bytesOwnedIdentity;

    @ColumnInfo(name = TIMESTAMP)
    public final long timestamp;

    // default constructor used by Room
    public ContactGroupJoin(@NonNull byte[] bytesGroupUid, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, long timestamp) {
        this.bytesGroupUid = bytesGroupUid;
        this.bytesContactIdentity = bytesContactIdentity;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.timestamp = timestamp;
    }

    @Ignore
    public ContactGroupJoin(@NonNull byte[] bytesGroupUid, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity) {
        this.bytesGroupUid = bytesGroupUid;
        this.bytesContactIdentity = bytesContactIdentity;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.timestamp = System.currentTimeMillis();
    }
}
