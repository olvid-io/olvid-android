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
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import io.olvid.messenger.databases.entity.ReactionRequest;

@Dao
public interface ReactionRequestDao {
    @Insert
    long insert(ReactionRequest reactionRequest);

    @Delete
    void delete(ReactionRequest... reactionRequest);

    @Update
    void update(ReactionRequest reactionRequest);

    @Query("SELECT * FROM " + ReactionRequest.TABLE_NAME +
            " WHERE " + ReactionRequest.DISCUSSION_ID + " = :discussionId " +
            " AND " + ReactionRequest.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + ReactionRequest.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + ReactionRequest.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber " +
            " AND " + ReactionRequest.REACTER + " = :reacter " +
            " LIMIT 1 ")
    ReactionRequest getBySenderSequenceNumberAndReacter(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId, byte[] reacter);

    @Query("SELECT * FROM " + ReactionRequest.TABLE_NAME +
            " WHERE " + ReactionRequest.DISCUSSION_ID + " = :discussionId " +
            " AND " + ReactionRequest.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + ReactionRequest.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + ReactionRequest.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber ")
    List<ReactionRequest> getAllBySenderSequenceNumber(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId);

    @Query("DELETE FROM " + ReactionRequest.TABLE_NAME + " WHERE " + ReactionRequest.SERVER_TIMESTAMP + " < :timestamp ")
    void deleteOlderThan(long timestamp);
}
