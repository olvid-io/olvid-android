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

package io.olvid.messenger.databases.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.UUID;

import io.olvid.messenger.databases.entity.LatestDiscussionSenderSequenceNumber;

@Dao
public interface LatestDiscussionSenderSequenceNumberDao {
    @Insert
    void insert(LatestDiscussionSenderSequenceNumber latestDiscussionSenderSequenceNumber);

    @Query("UPDATE " + LatestDiscussionSenderSequenceNumber.TABLE_NAME +
            " SET " + LatestDiscussionSenderSequenceNumber.LATEST_SEQUENCE_NUMBER + " = :latestSequenceNumber " +
            " WHERE " + LatestDiscussionSenderSequenceNumber.DISCUSSION_ID + " = :discussionId " +
            " AND " + LatestDiscussionSenderSequenceNumber.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + LatestDiscussionSenderSequenceNumber.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " )
    void updateLatestSequenceNumber(long discussionId, byte[] senderIdentifier, UUID senderThreadIdentifier, long latestSequenceNumber);

    @Query("SELECT * FROM " + LatestDiscussionSenderSequenceNumber.TABLE_NAME +
            " WHERE " + LatestDiscussionSenderSequenceNumber.DISCUSSION_ID + " = :discussionId " +
            " AND " + LatestDiscussionSenderSequenceNumber.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + LatestDiscussionSenderSequenceNumber.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " )
    LatestDiscussionSenderSequenceNumber get(long discussionId, byte[] senderIdentifier, UUID senderThreadIdentifier);

    @Query("DELETE FROM " + LatestDiscussionSenderSequenceNumber.TABLE_NAME +
            " WHERE " + LatestDiscussionSenderSequenceNumber.DISCUSSION_ID + " = :discussionId")
    void deleteForDiscussion(long discussionId);
}
