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
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = Reaction.TABLE_NAME,
        foreignKeys = @ForeignKey(entity = Message.class,
                parentColumns = "id",
                childColumns = Reaction.MESSAGE_ID,
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(Reaction.MESSAGE_ID),
                @Index(value={Reaction.MESSAGE_ID, Reaction.BYTES_IDENTITY}, unique = true),
        }
)
public class Reaction {
    public static final String TABLE_NAME = "reactions_table";

    public static final String MESSAGE_ID = "message_id";
    public static final String BYTES_IDENTITY = "bytes_identity"; // bytes identity null if reaction is mine
    public static final String EMOJI = "emoji";
    public static final String TIMESTAMP = "timestamp";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = MESSAGE_ID)
    public long messageId;

    // bytes identity null if reaction is mine
    @ColumnInfo(name = BYTES_IDENTITY)
    @Nullable
    public byte[] bytesIdentity;

    @ColumnInfo(name = EMOJI)
    @Nullable
    public String emoji;

    @ColumnInfo(name = TIMESTAMP)
    public long timestamp;

    // default constructor required by Room
    public Reaction(long messageId, @Nullable byte[] bytesIdentity, @Nullable String emoji, long timestamp) {
        this.messageId = messageId;
        this.bytesIdentity = bytesIdentity;
        this.emoji = emoji;
        this.timestamp = timestamp;
    }

    @NonNull
    @Override
    public String toString() {
        return "Reaction: " + emoji + " | messageid: " + messageId;
    }
}
