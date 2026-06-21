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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.RoomWarnings;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;

@Dao
public interface FyleMessageJoinWithStatusDao {
    @Insert
    void insert(@NonNull FyleMessageJoinWithStatus fyleMessageJoinWithStatus);

    @Update
    void update(@NonNull FyleMessageJoinWithStatus fyleMessageJoinWithStatus);

    @Delete
    void delete(@NonNull FyleMessageJoinWithStatus fyleMessageJoinWithStatus);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.ENGINE_MESSAGE_IDENTIFIER + " = :engineMessageIdentifier, " +
            FyleMessageJoinWithStatus.ENGINE_NUMBER + " = :engineNumber " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateEngineIdentifier(long messageId, long fyleId, @Nullable byte[] engineMessageIdentifier, int engineNumber);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " = :imageResolution " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateImageResolution(long messageId, long fyleId, @Nullable String imageResolution);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.WAS_OPENED + " = 1 " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateWasOpened(long messageId, long fyleId);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.TEXT_CONTENT + " = :content, " +
            FyleMessageJoinWithStatus.TEXT_EXTRACTED + " = 1 " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateTextContent(long messageId, long fyleId, @Nullable String content);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.RECEPTION_STATUS + " = :receptionStatus " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateReceptionStatus(long messageId, long fyleId, int receptionStatus);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.MINI_PREVIEW + " = :miniPreview " +
            " WHERE " + FyleMessageJoinWithStatus.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity" +
            " AND " + FyleMessageJoinWithStatus.ENGINE_MESSAGE_IDENTIFIER + " = :messageIdentifier " +
            " AND " + FyleMessageJoinWithStatus.ENGINE_NUMBER + " = :number")
    void updateMiniPreview(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] messageIdentifier, int number, @Nullable byte[] miniPreview);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.MINI_PREVIEW + " = :miniPreview " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateMiniPreview(long messageId, long fyleId, @Nullable byte[] miniPreview);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.FILE_PATH + " = :filePath " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateFilePath(long messageId, long fyleId, @Nullable String filePath);

    @Query("SELECT " + FyleMessageJoinWithStatus.MESSAGE_ID + " FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    List<Long> getMessageIdsForFyleSync(final long fyleId);

    @Query("SELECT COUNT(*) FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    Long countMessageForFyle(final long fyleId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    List<FyleAndStatus> getFylesAndStatusForMessageSync(final long messageId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' " +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    List<FyleAndStatus> getFylesAndStatusForMessageSyncWithoutLinkPreview(final long messageId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId" +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' " +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    List<FyleAndStatus> getCompleteFylesAndStatusForMessageSyncWithoutLinkPreview(final long messageId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId" +
            " AND FMjoin." + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    @Nullable FyleAndStatus getFyleAndStatus(final long messageId, final long fyleId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId" +
            " AND FMjoin." + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    LiveData<FyleAndStatus> getFyleAndStatusObservable(final long messageId, final long fyleId);

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " ORDER BY " + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    List<FyleMessageJoinWithStatus> getStatusesForMessage(final long messageId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' " +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    LiveData<List<FyleAndStatus>> getFylesAndStatusForMessage(final long messageId);

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity" +
            " AND " + FyleMessageJoinWithStatus.ENGINE_MESSAGE_IDENTIFIER + " = :messageIdentifier " +
            " AND " + FyleMessageJoinWithStatus.ENGINE_NUMBER + " = :number")
    @Nullable FyleMessageJoinWithStatus getByEngineIdentifierAndNumber(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] messageIdentifier, int number);

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " WHERE " + FyleMessageJoinWithStatus.STATUS + " = " + FyleMessageJoinWithStatus.STATUS_UPLOADING)
    List<FyleMessageJoinWithStatus> getUploading();

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " WHERE " + FyleMessageJoinWithStatus.STATUS + " IN (" + FyleMessageJoinWithStatus.STATUS_DOWNLOADING + "," + FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE + ")")
    List<FyleMessageJoinWithStatus> getDownloading();

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    List<FyleMessageJoinWithStatus> getForFyleId(long fyleId);


    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId " +
            " AND " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId")
    @Nullable FyleMessageJoinWithStatus get(long fyleId, long messageId);

    @Query("SELECT FMjoin.* FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND fyle." + Fyle.SHA256 + " = :sha256 " +
            " LIMIT 1 ") // there should never be more than one match, but let's add this anyway
    @Nullable FyleMessageJoinWithStatus getByMessageIdAndSha256(long messageId, byte[] sha256);

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.ENGINE_NUMBER + " = :attachmentNumber")
    @Nullable FyleMessageJoinWithStatus getByIdAndAttachmentNumber(long messageId, int attachmentNumber);



    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusForMessage(long messageId);

    @Query("SELECT fyle.*, FMjoin.*, " + "mess." + Message.TIMESTAMP + " AS " + Message.TIMESTAMP + " FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != '' " +
            " OR FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " = 'image/svg+xml' " +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND (fyle." + Fyle.FILE_PATH + " IS NOT NULL" +
            " OR FMjoin." + FyleMessageJoinWithStatus.MINI_PREVIEW + " IS NOT NULL) " +
            " ORDER BY mess." + Message.SORT_INDEX + " DESC, " +
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatusTimestamped>> getGalleryMediasForDiscussion(long discussionId);

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT fyle.*, FMjoin.*, " + "mess." + Message.TIMESTAMP + " AS " + Message.TIMESTAMP + ", MIN(FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + ") AS first_engine_number FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != '' " +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL" +
            " GROUP BY mess." + Message.SORT_INDEX +
            " ORDER BY mess." + Message.SORT_INDEX + " DESC")
    LiveData<List<FyleAndStatusTimestamped>> getGalleryMediasForCalendar(long discussionId);

    @Query("SELECT fyle.*, FMjoin.*, " + "mess." + Message.TIMESTAMP + " AS " + Message.TIMESTAMP + " FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " = '" + OpenGraph.MIME_TYPE + "' " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL" +
            " ORDER BY mess." + Message.SORT_INDEX + " DESC, " +
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatusTimestamped>> getGalleryLinksForDiscussion(long discussionId);

    @Query("SELECT fyle.*, FMjoin.*, " + "mess." + Message.TIMESTAMP + " AS " + Message.TIMESTAMP + " FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'audio/%' " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'video/%' " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'image/%' " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL" +
            " ORDER BY mess." + Message.SORT_INDEX + " DESC, " +
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatusTimestamped>> getGalleryDocumentsForDiscussion(long discussionId);

    @Query("SELECT fyle.*, FMjoin.*, " + "mess." + Message.TIMESTAMP + " AS " + Message.TIMESTAMP + " FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'audio/%' " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL" +
            " ORDER BY mess." + Message.SORT_INDEX + " DESC, " +
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatusTimestamped>> getGalleryAudiosForDiscussion(long discussionId);


    // ---- Raw base queries for FyleAndStatus gallery (positional ? params) ----
    String IMAGE_AND_VIDEO_FOR_DISCUSSION_RAW_BASE = "SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = ?" +
            " AND (FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL)" +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT;
    String SORT_COLUMN_SORT_INDEX = "mess." + Message.SORT_INDEX;
    String SORT_COLUMN_ENGINE_NUMBER = "FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER;

    @RawQuery(observedEntities = {FyleMessageJoinWithStatus.class, Fyle.class, Message.class, Discussion.class})
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesRaw(SupportSQLiteQuery query);


    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.TEXT_EXTRACTED + " = 0" +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " = '" + OpenGraph.MIME_TYPE + "' " +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL")
    List<FyleAndStatus> getCompleteFyleAndStatusForTextExtraction();

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.TEXT_EXTRACTED + " = 0 " +
            " WHERE " + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'image/%'")
    void clearTextExtractedFromImages();

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL " +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL")
    List<FyleAndStatus> getCompleteFyleAndStatusWithoutResolution();

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
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
    LiveData<List<FyleAndStatus>> getAllTransferableForOwnedIdentity(@NonNull byte[] bytesOwnedIdentity);


    String FYLE_AND_ORIGIN_QUERY = "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " + MessageDao.PREFIX_MESSAGE_COLUMNS + ", fyle.*, FMjoin.* " +
            " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE;

    String GROUP_BY_SHA256 = " GROUP BY fyle." + Fyle.SHA256;
    String SENT_BY_ME_CONDITION = " AND mess." + Message.MESSAGE_TYPE + " = 1";
    String LARGE_FILE_CONDITION = " AND FMjoin." + FyleMessageJoinWithStatus.SIZE + " > :minSize";

    // ---- Type filter conditions (no params, used with @RawQuery) ----
    String MEDIA_TYPE_CONDITION =
            " AND (FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " = 'image/svg+xml'" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL)" +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'audio/%'";
    String AUDIO_TYPE_CONDITION =
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'audio/%'";
    String FILE_TYPE_CONDITION =
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'audio/%'" +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'video/%'" +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'image/%'" +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "'";

    // ---- Sort column expressions (used with @RawQuery) ----
    String SORT_COLUMN_SIZE = " FMjoin." + FyleMessageJoinWithStatus.SIZE;
    String SORT_COLUMN_DATE = " mess." + Message.TIMESTAMP;
    String SORT_COLUMN_NAME = " FMjoin." + FyleMessageJoinWithStatus.FILE_NAME;

    // ---- Raw base queries (positional ? params, for @RawQuery) ----
    String FYLE_AND_ORIGIN_RAW_BASE = "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " + MessageDao.PREFIX_MESSAGE_COLUMNS + ", fyle.*, FMjoin.* " +
            " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = ?" +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE;
    String FYLE_AND_ORIGIN_FOR_DISCUSSION_RAW_BASE = "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " + MessageDao.PREFIX_MESSAGE_COLUMNS + ", fyle.*, FMjoin.* " +
            " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE disc.id = ?" +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE;
    String LARGE_FILE_RAW_CONDITION = " AND FMjoin." + FyleMessageJoinWithStatus.SIZE + " > ? ";

    // ---- "All files" bucket ----

    @Query(FYLE_AND_ORIGIN_QUERY + GROUP_BY_SHA256 + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC LIMIT 15")
    LiveData<List<FyleAndOrigin>> getAllFilesFylePreview(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT COUNT(DISTINCT fyle." + Fyle.SHA256 + ") FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)
    LiveData<Integer> getAllFilesFyleCount(@NonNull byte[] bytesOwnedIdentity);

    @RawQuery(observedEntities = {FyleMessageJoinWithStatus.class, Fyle.class, Message.class, Discussion.class})
    LiveData<List<FyleAndOrigin>> getFyleAndOriginRaw(SupportSQLiteQuery query);

    // ---- Discussions with storage usage ----
    String DISCUSSIONS_WITH_USAGE_QUERY = "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " +
            " SUM(deduped.maxSize) AS totalSize " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " INNER JOIN (" +
            "  SELECT mess2." + Message.DISCUSSION_ID + " AS dedupDiscId, MAX(FMjoin2." + FyleMessageJoinWithStatus.SIZE + ") AS maxSize " +
            "  FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin2 " +
            "  INNER JOIN " + Fyle.TABLE_NAME + " AS fyle2 ON fyle2.id = FMjoin2." + FyleMessageJoinWithStatus.FYLE_ID +
            "  INNER JOIN " + Message.TABLE_NAME + " AS mess2 ON mess2.id = FMjoin2." + FyleMessageJoinWithStatus.MESSAGE_ID +
            "  WHERE fyle2." + Fyle.FILE_PATH + " IS NOT NULL " + // only consider complete fyles
            "  GROUP BY mess2." + Message.DISCUSSION_ID + ", fyle2." + Fyle.SHA256 +
            " ) AS deduped ON deduped.dedupDiscId = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " GROUP BY disc.id ";

    @Query(DISCUSSIONS_WITH_USAGE_QUERY + " ORDER BY totalSize DESC")
    LiveData<List<DiscussionAndUsage>> getDiscussionsWithUsageSizeDesc(@NonNull byte[] bytesOwnedIdentity);

    @Query(DISCUSSIONS_WITH_USAGE_QUERY + " ORDER BY disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " DESC")
    LiveData<List<DiscussionAndUsage>> getDiscussionsWithUsageDateDesc(@NonNull byte[] bytesOwnedIdentity);

    // ---- Total storage size for a single identity ----
    @Query("SELECT COALESCE(SUM(deduped.maxSize), 0) FROM (" +
            "  SELECT MAX(FMjoin." + FyleMessageJoinWithStatus.SIZE + ") AS maxSize" +
            "  FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin" +
            "  INNER JOIN " + Fyle.TABLE_NAME + " AS fyle ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            "  INNER JOIN " + Message.TABLE_NAME + " AS mess ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            "  INNER JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = mess." + Message.DISCUSSION_ID +
            "  WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity" +
            "  AND fyle." + Fyle.FILE_PATH + " IS NOT NULL " + // only consider complete fyles
            "  GROUP BY fyle." + Fyle.SHA256 +
            ") AS deduped")
    LiveData<Long> getTotalStorageSizeForIdentity(@NonNull byte[] bytesOwnedIdentity);

    // ---- "Sent by me" bucket ----
    @Query(FYLE_AND_ORIGIN_QUERY + SENT_BY_ME_CONDITION + GROUP_BY_SHA256 + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC LIMIT 15")
    LiveData<List<FyleAndOrigin>> getSentByMeFylePreview(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT COUNT(DISTINCT fyle." + Fyle.SHA256 + ") FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.MESSAGE_TYPE + " = 1")
    LiveData<Integer> getSentByMeFyleCount(@NonNull byte[] bytesOwnedIdentity);

    // ---- "Large files" bucket ----

    @Query(FYLE_AND_ORIGIN_QUERY + LARGE_FILE_CONDITION + GROUP_BY_SHA256 + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC LIMIT 15")
    LiveData<List<FyleAndOrigin>> getLargeFileFylePreview(@NonNull byte[] bytesOwnedIdentity, long minSize);

    @Query("SELECT COUNT(DISTINCT fyle." + Fyle.SHA256 + ") FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND FMjoin." + FyleMessageJoinWithStatus.SIZE + " > :minSize")
    LiveData<Integer> getLargeFileFyleCount(@NonNull byte[] bytesOwnedIdentity, long minSize);

    @Query("SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " + MessageDao.PREFIX_MESSAGE_COLUMNS
            + ", fyle.*, FMjoin.* " +
            " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'audio/%' " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL " +
            " ORDER BY mess." + Message.SORT_INDEX + " ASC")
    List<FyleAndOrigin> getAudioFyleAndOriginForDiscussionDateAscSync(long discussionId);

    class FyleAndStatusTimestamped {
        @Embedded
        public FyleAndStatus fyleAndStatus;
        public long timestamp;

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof FyleAndStatusTimestamped other)) {
                return false;
            }
            return (other.timestamp == timestamp) && (other.fyleAndStatus.equals(fyleAndStatus)) && Objects.equals(other.fyleAndStatus.fyle.filePath, fyleAndStatus.fyle.filePath);
        }
    }

    class FyleAndOrigin {
        @Embedded
        public FyleAndStatus fyleAndStatus;

        @Embedded(prefix = "mess_")
        public Message message;

        @Embedded(prefix = "disc_")
        public Discussion discussion;
    }

    class DiscussionAndUsage {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        @androidx.room.ColumnInfo(name = "totalSize")
        public long totalSize;
    }

    class FyleAndStatus {
        @Embedded
        public Fyle fyle;

        @Embedded
        public FyleMessageJoinWithStatus fyleMessageJoinWithStatus;

        public FyleAndStatus() {
        }

        public byte[] getMetadata() throws Exception {
            Fyle.JsonMetadata jsonMetadata = new Fyle.JsonMetadata(fyleMessageJoinWithStatus.getNonNullMimeType(), fyleMessageJoinWithStatus.fileName, fyle.sha256);
            return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonMetadata);
        }

        public Uri getContentUriForExternalSharing() {
            if (fyle.sha256 == null) {
                return null;
            }
            byte[] randomizer = new byte[16];
            new SecureRandom().nextBytes(randomizer);
            return Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".PROVIDER/" + Logger.toHexString(fyle.sha256) + "/" + fyleMessageJoinWithStatus.messageId + "/" + Logger.toHexString(randomizer));
        }

        public Uri getDeterministicContentUriForGallery() {
            if (fyle.sha256 == null) {
                return null;
            }
            return Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".PROVIDER/" + Logger.toHexString(fyle.sha256) + "/" + fyleMessageJoinWithStatus.messageId + "/00000000000000000000000000000000");
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof FyleAndStatus other)) {
                return false;
            }
            return (other.fyle.id == fyle.id) && (other.fyleMessageJoinWithStatus.messageId == fyleMessageJoinWithStatus.messageId);
        }

        @Override
        public int hashCode() {
            return (int) (fyle.id + 31*fyleMessageJoinWithStatus.messageId);
        }
    }
}
