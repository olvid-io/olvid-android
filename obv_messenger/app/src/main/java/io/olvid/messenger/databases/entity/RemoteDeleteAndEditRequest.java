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

import java.util.UUID;

@Entity(
        tableName = RemoteDeleteAndEditRequest.TABLE_NAME,
        primaryKeys = {
                RemoteDeleteAndEditRequest.DISCUSSION_ID,
                RemoteDeleteAndEditRequest.SENDER_IDENTIFIER,
                RemoteDeleteAndEditRequest.SENDER_THREAD_IDENTIFIER,
                RemoteDeleteAndEditRequest.SENDER_SEQUENCE_NUMBER,
        },
        foreignKeys = {
                @ForeignKey(entity = Discussion.class,
                        parentColumns = "id",
                        childColumns = RemoteDeleteAndEditRequest.DISCUSSION_ID,
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(RemoteDeleteAndEditRequest.DISCUSSION_ID),
                @Index(RemoteDeleteAndEditRequest.SERVER_TIMESTAMP),
        }
)
public class RemoteDeleteAndEditRequest {
    public static final String TABLE_NAME = "remote_delete_and_edit_request_table";

    public static final String DISCUSSION_ID = "discussion_id";
    public static final String SENDER_IDENTIFIER = "sender_identifier";
    public static final String SENDER_THREAD_IDENTIFIER = "sender_thread_identifier";
    public static final String SENDER_SEQUENCE_NUMBER = "sender_sequence_number";
    public static final String SERVER_TIMESTAMP = "server_timestamp";
    public static final String REQUEST_TYPE = "request_type";
    public static final String BODY = "body";
    public static final String REMOTE_DELETER = "remote_deleter";

    public static final int TYPE_DELETE = 0;
    public static final int TYPE_EDIT = 1;

    public static final long TTL = 30 * 86_400_000L; // keep messages in the table for 30 days. After that, we drop the request.

    @ColumnInfo(name = DISCUSSION_ID)
    public long discussionId;

    @ColumnInfo(name = SENDER_IDENTIFIER)
    @NonNull
    public byte[] senderIdentifier;

    @ColumnInfo(name = SENDER_THREAD_IDENTIFIER)
    @NonNull
    public UUID senderThreadIdentifier;

    @ColumnInfo(name = SENDER_SEQUENCE_NUMBER)
    public long senderSequenceNumber;

    @ColumnInfo(name = SERVER_TIMESTAMP)
    public long serverTimestamp;

    @ColumnInfo(name = REQUEST_TYPE)
    public int requestType;

    @ColumnInfo(name = BODY)
    @Nullable
    public String body;

    @ColumnInfo(name = REMOTE_DELETER)
    @Nullable
    public byte[] remoteDeleter;

    // default constructor required by Room
    public RemoteDeleteAndEditRequest(long discussionId, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, long senderSequenceNumber, long serverTimestamp, int requestType, @Nullable String body, @Nullable byte[] remoteDeleter) {
        this.discussionId = discussionId;
        this.senderIdentifier = senderIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.senderSequenceNumber = senderSequenceNumber;
        this.serverTimestamp = serverTimestamp;
        this.requestType = requestType;
        this.body = body;
        this.remoteDeleter = remoteDeleter;
    }
}
