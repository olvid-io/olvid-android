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

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = MessageExpiration.TABLE_NAME,
        foreignKeys = @ForeignKey(entity = Message.class,
                parentColumns = "id",
                childColumns = MessageExpiration.MESSAGE_ID,
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(MessageExpiration.MESSAGE_ID),
                @Index(MessageExpiration.EXPIRATION_TIMESTAMP),
        }
)
public class MessageExpiration {
    public static final String TABLE_NAME = "message_expiration_table";

    public static final String MESSAGE_ID = "message_id";
    public static final String EXPIRATION_TIMESTAMP = "expiration_timestamp";
    public static final String WIPE_ONLY = "wipe_only";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = MESSAGE_ID)
    public long messageId;

    @ColumnInfo(name = EXPIRATION_TIMESTAMP)
    public long expirationTimestamp;

    @ColumnInfo(name = WIPE_ONLY)
    public boolean wipeOnly;

    // default constructor required by Room
    public MessageExpiration(long messageId, long expirationTimestamp, boolean wipeOnly) {
        this.messageId = messageId;
        this.expirationTimestamp = expirationTimestamp;
        this.wipeOnly = wipeOnly;
    }
}
