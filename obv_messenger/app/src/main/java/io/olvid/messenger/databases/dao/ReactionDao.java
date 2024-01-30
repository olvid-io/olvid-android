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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.Reaction;

@Dao
public interface ReactionDao {
    @Insert
    long insert(Reaction reaction);

    @Update
    void update(Reaction reaction);

    @Delete
    void delete(Reaction reaction);

    @Query("SELECT * FROM " + Reaction.TABLE_NAME +
            " WHERE " + Reaction.MESSAGE_ID + " = :messageId ")
    List<Reaction> getAllForMessage(long messageId);

    @Query("SELECT * FROM " + Reaction.TABLE_NAME +
            " WHERE " + Reaction.MESSAGE_ID + " = :messageId " +
            " AND " + Reaction.EMOJI + " NOT NULL " +
            " ORDER BY CASE WHEN " + Reaction.BYTES_IDENTITY + " IS NULL THEN 0 ELSE 1 END ASC, " +
            Reaction.TIMESTAMP + " DESC")
    LiveData<List<Reaction>> getAllNonNullForMessageSortedByTimestampLiveData(long messageId);

    @Query("SELECT * FROM " + Reaction.TABLE_NAME +
            " WHERE " + Reaction.MESSAGE_ID + " = :messageId " +
            " AND " + Reaction.BYTES_IDENTITY + " IS NULL")
    Reaction getMyReactionForMessage(long messageId);

    @Query("DELETE FROM " + Reaction.TABLE_NAME +
            " WHERE " + Reaction.MESSAGE_ID + " = :messageId ")
    void deleteAllForMessage(long messageId);
}
