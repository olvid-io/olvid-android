/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.discussion.compose.VoiceMessageRecorder;
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
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' " +
            " ORDER BY FMjoin.rowid ASC")
    LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> getDiscussionDraftFyles(long discussionId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = " +
            " ( SELECT id FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_DRAFT + " AND " + Message.DISCUSSION_ID + " = :discussionId ) " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' " +
            " AND (" +
            "FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'audio/%' " +
            " OR FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " NOT LIKE '%" + VoiceMessageRecorder.AUDIO_FILE_NAME_SUFFIX + "%'" +
            ") " +
            " ORDER BY FMjoin.rowid ASC")
    LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> getDiscussionDraftFylesWithoutVoiceRecordings(long discussionId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = " +
            " ( SELECT id FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_DRAFT + " AND " + Message.DISCUSSION_ID + " = :discussionId ) " +
            " AND FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " LIKE '%" + VoiceMessageRecorder.AUDIO_FILE_NAME_SUFFIX + "%' " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'audio/%' " +
            " LIMIT 1")
    @Nullable LiveData<FyleMessageJoinWithStatusDao.FyleAndStatus> getDiscussionDraftVoiceRecording(long discussionId);

    @Query("SELECT * FROM " + Fyle.TABLE_NAME + " WHERE id = :fyleId")
    @Nullable Fyle getById(long fyleId);


    @Query("SELECT * FROM " + Fyle.TABLE_NAME + " WHERE " + Fyle.SHA256 + " = :sha256")
    @Nullable Fyle getBySha256(@NonNull byte[] sha256);

    @Query("SELECT fyle.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " LEFT JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMJoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " IS NULL")
    @NonNull List<Fyle> getStray();

    @Query("SELECT " + Fyle.SHA256 + " FROM " + Fyle.TABLE_NAME)
    @NonNull List<byte[]> getAllSha256();

    @Query("SELECT DISTINCT fyle.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = mess.id " +
            " WHERE fyle." + Fyle.SHA256 + " IS NOT NULL " +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL " +
            " AND mess." + Message.DISCUSSION_ID + " IN (:discussionIds) " +
            " AND mess." + Message.MESSAGE_TYPE + " IN ( " + Message.TYPE_OUTBOUND_MESSAGE + "," + Message.TYPE_INBOUND_MESSAGE + ") " +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT
    )
    @NonNull List<Fyle> getAllTransferableForDiscussionIds(@NonNull List<Long> discussionIds);

    @Query("SELECT DISTINCT fyle.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = mess.id " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE fyle." + Fyle.SHA256 + " IS NOT NULL " +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL " +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mess." + Message.MESSAGE_TYPE + " IN ( " + Message.TYPE_OUTBOUND_MESSAGE + "," + Message.TYPE_INBOUND_MESSAGE + ") " +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT
    )
    @NonNull List<Fyle> getAllTransferableForOwnedIdentity(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT " + Fyle.SHA256 + " FROM " + Fyle.TABLE_NAME +
            " WHERE " + Fyle.FILE_PATH + " IS NOT NULL " +
            " AND " + Fyle.SHA256 + " IN (:sha256s) "
    )
    @NonNull List<byte[]> filterKnownAndComplete(@NonNull List<byte[]> sha256s);

    @Query("SELECT " + Fyle.SHA256 + " FROM " + Fyle.TABLE_NAME +
            " WHERE " + Fyle.FILE_PATH + " IS NULL " +
            " AND " + Fyle.SHA256 + " IN (:sha256s) "
    )
    @NonNull List<byte[]> filterKnownAndIncomplete(@NonNull List<byte[]> sha256s);
}
