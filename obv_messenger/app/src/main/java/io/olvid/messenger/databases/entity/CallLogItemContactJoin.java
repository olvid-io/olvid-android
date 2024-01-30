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
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = CallLogItemContactJoin.TABLE_NAME,
        primaryKeys = {CallLogItemContactJoin.CALL_LOG_ITEM_ID, CallLogItemContactJoin.BYTES_OWNED_IDENTITY, CallLogItemContactJoin.BYTES_CONTACT_IDENTITY},
        foreignKeys = {
                @ForeignKey(entity = CallLogItem.class,
                        parentColumns = "id",
                        childColumns = CallLogItemContactJoin.CALL_LOG_ITEM_ID,
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Contact.class,
                        parentColumns = {Contact.BYTES_OWNED_IDENTITY, Contact.BYTES_CONTACT_IDENTITY},
                        childColumns = {CallLogItemContactJoin.BYTES_OWNED_IDENTITY, CallLogItemContactJoin.BYTES_CONTACT_IDENTITY},
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(value = CallLogItemContactJoin.CALL_LOG_ITEM_ID),
                @Index(value = {CallLogItemContactJoin.BYTES_OWNED_IDENTITY, CallLogItemContactJoin.BYTES_CONTACT_IDENTITY}),
        }

)
public class CallLogItemContactJoin {
    public static final String TABLE_NAME = "call_log_item_contact_join";

    public static final String CALL_LOG_ITEM_ID = "call_log_item_id";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";

    @ColumnInfo(name = CALL_LOG_ITEM_ID)
    public final long callLogItemId;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public final byte[] bytesOwnedIdentity;

    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @NonNull
    public final byte[] bytesContactIdentity;

    public CallLogItemContactJoin(long callLogItemId, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity) {
        this.callLogItemId = callLogItemId;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
    }
}
