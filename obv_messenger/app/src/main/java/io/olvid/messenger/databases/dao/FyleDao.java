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

package io.olvid.messenger.databases.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;

@Dao
public interface FyleDao {
    @Insert
    long insert(@NonNull Fyle fyle);

    @Delete
    void delete(@NonNull Fyle fyle);

    @Update
    void update(@NonNull Fyle fyle);


    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = " +
            " ( SELECT id FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_DRAFT + " AND " + Message.DISCUSSION_ID + " = :discussionId ) " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' ")
    LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> getDiscussionDraftFyles(long discussionId);

    @Query("SELECT * FROM " + Fyle.TABLE_NAME + " WHERE id = :fyleId")
    @Nullable Fyle getById(long fyleId);


    @Query("SELECT * FROM " + Fyle.TABLE_NAME + " WHERE " + Fyle.SHA256 + " = :sha256")
    @Nullable Fyle getBySha256(@NonNull byte[] sha256);

    @Query("SELECT fyle.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " LEFT JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMJoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " IS NULL")
    List<Fyle> getStray();

    @Query("SELECT " + Fyle.SHA256 + " FROM " + Fyle.TABLE_NAME)
    List<byte[]> getAllSha256();
}
