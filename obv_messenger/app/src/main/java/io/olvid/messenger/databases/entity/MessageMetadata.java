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


import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = MessageMetadata.TABLE_NAME,
        foreignKeys = @ForeignKey(entity = Message.class,
                parentColumns = "id",
                childColumns = MessageMetadata.MESSAGE_ID,
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(MessageMetadata.MESSAGE_ID),
                @Index(value = {MessageMetadata.MESSAGE_ID, MessageMetadata.KIND}),
                @Index(MessageMetadata.TIMESTAMP),
        }
)
public class MessageMetadata {
    public static final String TABLE_NAME = "message_metadata_table";

    public static final String MESSAGE_ID = "message_id";
    public static final String KIND = "kind";
    public static final String TIMESTAMP = "timestamp";
    public static final String BYTES_REMOTE_IDENTITY = "bytes_remote_identity";

    public static final int KIND_DELIVERED = 0;
    public static final int KIND_READ = 1;
    public static final int KIND_WIPED = 2; // use for limited visibility outbound messages, before they are fully deleted
    public static final int KIND_EDITED = 3;
    public static final int KIND_REMOTE_DELETED = 4;
    public static final int KIND_UNDELIVERED = 5; // when we set the outbound status to undelivered
    public static final int KIND_LOCATION_SHARING_LATEST_UPDATE = 6; // used to avoid multiple edited metadata when sharing location
    public static final int KIND_LOCATION_SHARING_END = 7;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = MESSAGE_ID)
    public long messageId;

    @ColumnInfo(name = KIND)
    public int kind;

    @ColumnInfo(name = TIMESTAMP)
    public long timestamp;

    @ColumnInfo(name = BYTES_REMOTE_IDENTITY)
    @Nullable
    public byte[] bytesRemoteIdentity;

    // default constructor required by Room
    public MessageMetadata(long messageId, int kind, long timestamp, @Nullable byte[] bytesRemoteIdentity) {
        this.messageId = messageId;
        this.kind = kind;
        this.timestamp = timestamp;
        this.bytesRemoteIdentity = bytesRemoteIdentity;
    }

    @Ignore
    public MessageMetadata(long messageId, int kind, long timestamp) {
        this.messageId = messageId;
        this.kind = kind;
        this.timestamp = timestamp;
        this.bytesRemoteIdentity = null;
    }
}
