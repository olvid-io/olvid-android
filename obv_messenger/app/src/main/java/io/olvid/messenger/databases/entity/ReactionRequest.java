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
import androidx.room.Index;

import java.util.UUID;

@Entity(
        tableName = ReactionRequest.TABLE_NAME,
        primaryKeys = {
                ReactionRequest.DISCUSSION_ID,
                ReactionRequest.SENDER_IDENTIFIER,
                ReactionRequest.SENDER_THREAD_IDENTIFIER,
                ReactionRequest.SENDER_SEQUENCE_NUMBER,
                ReactionRequest.REACTER,
        },
        foreignKeys = {
                @ForeignKey(entity = Discussion.class,
                        parentColumns = "id",
                        childColumns = ReactionRequest.DISCUSSION_ID,
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(ReactionRequest.DISCUSSION_ID),
                @Index(ReactionRequest.SERVER_TIMESTAMP),
                @Index({ReactionRequest.DISCUSSION_ID,
                        ReactionRequest.SENDER_IDENTIFIER,
                        ReactionRequest.SENDER_THREAD_IDENTIFIER,
                        ReactionRequest.SENDER_SEQUENCE_NUMBER}),
        }
)
public class ReactionRequest {
    public static final String TABLE_NAME = "reaction_request_table";

    public static final String DISCUSSION_ID = "discussion_id";
    public static final String SENDER_IDENTIFIER = "sender_identifier";
    public static final String SENDER_THREAD_IDENTIFIER = "sender_thread_identifier";
    public static final String SENDER_SEQUENCE_NUMBER = "sender_sequence_number";
    public static final String REACTER = "reacter";
    public static final String SERVER_TIMESTAMP = "server_timestamp";
    public static final String REACTION = "reaction";

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

    @ColumnInfo(name = REACTER)
    @NonNull
    public byte[] reacter;

    @ColumnInfo(name = SERVER_TIMESTAMP)
    public long serverTimestamp;

    @ColumnInfo(name = REACTION)
    @NonNull
    public String reaction;

    // default constructor required by Room
    public ReactionRequest(long discussionId, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, long senderSequenceNumber, @NonNull byte[] reacter, long serverTimestamp, @NonNull String reaction) {
        this.discussionId = discussionId;
        this.senderIdentifier = senderIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.senderSequenceNumber = senderSequenceNumber;
        this.reacter = reacter;
        this.serverTimestamp = serverTimestamp;
        this.reaction = reaction;
    }
}
