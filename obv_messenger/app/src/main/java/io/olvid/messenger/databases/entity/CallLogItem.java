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
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = CallLogItem.TABLE_NAME,
        foreignKeys = {
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = CallLogItem.BYTES_OWNED_IDENTITY,
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Group.class,
                        parentColumns = {Group.BYTES_GROUP_OWNER_AND_UID, Group.BYTES_OWNED_IDENTITY},
                        childColumns = {CallLogItem.BYTES_GROUP_OWNER_AND_UID, CallLogItem.BYTES_OWNED_IDENTITY},
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(value = {CallLogItem.BYTES_OWNED_IDENTITY}),
                @Index(value = {CallLogItem.TIMESTAMP}),
                @Index(value = {CallLogItem.BYTES_GROUP_OWNER_AND_UID, CallLogItem.BYTES_OWNED_IDENTITY}),
        }
)
public class CallLogItem {
        public static final String TABLE_NAME = "call_log_table";

        public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
        public static final String BYTES_GROUP_OWNER_AND_UID = "bytes_group_owner_and_uid";
        public static final String TIMESTAMP = "timestamp";
        public static final String CALL_TYPE = "call_type";
        public static final String CALL_STATUS = "call_status";
        public static final String DURATION = "duration";

        public static final int TYPE_OUTGOING = 1;
        public static final int TYPE_INCOMING = 2;

        public static final int STATUS_SUCCESSFUL = 1;
        public static final int STATUS_MISSED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_BUSY = 4;

        @PrimaryKey(autoGenerate = true)
        public long id;

        @ColumnInfo(name = BYTES_OWNED_IDENTITY)
        @NonNull
        public byte[] bytesOwnedIdentity;

        @ColumnInfo(name = BYTES_GROUP_OWNER_AND_UID)
        @Nullable
        public byte[] bytesGroupOwnerAndUid;

        @ColumnInfo(name = TIMESTAMP)
        public long timestamp;

        @ColumnInfo(name = CALL_TYPE)
        public int callType;

        @ColumnInfo(name = CALL_STATUS)
        public int callStatus;

        @ColumnInfo(name = DURATION)
        public int duration;


        // default constructor required by Room
        public CallLogItem(@NonNull byte[] bytesOwnedIdentity, @Nullable byte[] bytesGroupOwnerAndUid, long timestamp, int callType, int callStatus, int duration) {
                this.bytesOwnedIdentity = bytesOwnedIdentity;
                this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
                this.timestamp = timestamp;
                this.callType = callType;
                this.callStatus = callStatus;
                this.duration = duration;
        }


        @Ignore
        public CallLogItem(@NonNull byte[] bytesOwnedIdentity, @Nullable byte[] bytesGroupOwnerAndUid, int callType, int callStatus) {
                this.bytesOwnedIdentity = bytesOwnedIdentity;
                this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
                this.timestamp = System.currentTimeMillis();
                this.callType = callType;
                this.callStatus = callStatus;
                this.duration = 0;
        }

        @Ignore
        public CallLogItem(@NonNull byte[] bytesOwnedIdentity, int callType, int callStatus, long timestamp) {
                this.bytesOwnedIdentity = bytesOwnedIdentity;
                this.bytesGroupOwnerAndUid = null;
                this.timestamp = timestamp;
                this.callType = callType;
                this.callStatus = callStatus;
                this.duration = 0;
        }
}
