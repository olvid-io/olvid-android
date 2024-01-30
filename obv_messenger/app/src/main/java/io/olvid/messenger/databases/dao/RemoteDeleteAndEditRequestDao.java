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

package io.olvid.messenger.databases.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.UUID;

import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest;

@Dao
public interface RemoteDeleteAndEditRequestDao {
    @Insert
    long insert(RemoteDeleteAndEditRequest remoteDeleteAndEditRequest);

    @Delete
    void delete(RemoteDeleteAndEditRequest remoteDeleteAndEditRequest);

    @Query("SELECT * FROM " + RemoteDeleteAndEditRequest.TABLE_NAME +
            " WHERE " + RemoteDeleteAndEditRequest.DISCUSSION_ID + " = :discussionId " +
            " AND " + RemoteDeleteAndEditRequest.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + RemoteDeleteAndEditRequest.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + RemoteDeleteAndEditRequest.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber " +
            " LIMIT 1 ")
    RemoteDeleteAndEditRequest getBySenderSequenceNumber(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId);

    @Query("DELETE FROM " + RemoteDeleteAndEditRequest.TABLE_NAME + " WHERE " + RemoteDeleteAndEditRequest.SERVER_TIMESTAMP + " < :timestamp ")
    void deleteOlderThan(long timestamp);
}
