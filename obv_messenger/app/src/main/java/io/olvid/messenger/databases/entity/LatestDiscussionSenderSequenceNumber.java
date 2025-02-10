/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import java.util.UUID;

@Entity(tableName = LatestDiscussionSenderSequenceNumber.TABLE_NAME,
        primaryKeys = {LatestDiscussionSenderSequenceNumber.DISCUSSION_ID, LatestDiscussionSenderSequenceNumber.SENDER_IDENTIFIER, LatestDiscussionSenderSequenceNumber.SENDER_THREAD_IDENTIFIER},
        foreignKeys = {
                @ForeignKey(entity =  Discussion.class,
                parentColumns = "id",
                childColumns = LatestDiscussionSenderSequenceNumber.DISCUSSION_ID,
                onDelete = ForeignKey.CASCADE),
        })
public class LatestDiscussionSenderSequenceNumber {
    public static final String TABLE_NAME = "latest_discussion_sender_sequence_number_table";

    public static final String DISCUSSION_ID = "discussion_id";
    public static final String SENDER_IDENTIFIER = "sender_identifier";
    public static final String SENDER_THREAD_IDENTIFIER = "sender_thread_identifier";
    public static final String LATEST_SEQUENCE_NUMBER = "latest_sequence_number";

    @ColumnInfo(name = DISCUSSION_ID)
    public long discussionId;

    @ColumnInfo(name = SENDER_IDENTIFIER)
    @NonNull
    public byte[] senderIdentifier;

    @ColumnInfo(name = SENDER_THREAD_IDENTIFIER)
    @NonNull
    public UUID senderThreadIdentifier;

    @ColumnInfo(name = LATEST_SEQUENCE_NUMBER)
    public long latestSequenceNumber;


    // default constructor required by Room
    public LatestDiscussionSenderSequenceNumber(long discussionId, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, long latestSequenceNumber) {
        this.discussionId = discussionId;
        this.senderIdentifier = senderIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.latestSequenceNumber = latestSequenceNumber;
    }
}
