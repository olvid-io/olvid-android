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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

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

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.ENGINE_NUMBER + " = :attachmentNumber")
    @Nullable FyleMessageJoinWithStatus getByIdAndAttachmentNumber(long messageId, int attachmentNumber);


    String IMAGE_AND_VIDEO_FOR_DISCUSSION_QUERY = "SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT;

    @Query(IMAGE_AND_VIDEO_FOR_DISCUSSION_QUERY +
            " ORDER BY mess." + Message.SORT_INDEX + " ASC, " +
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForDiscussion(long discussionId);

    @Query(IMAGE_AND_VIDEO_FOR_DISCUSSION_QUERY +
            " ORDER BY mess." + Message.SORT_INDEX + " DESC, " +
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForDiscussionDescending(long discussionId);

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


    String IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY = "SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE;

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY mess." + Message.TIMESTAMP + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentity(@NonNull byte[] bytesOwnedIdentity);

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY mess." + Message.TIMESTAMP + " ASC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentityAscending(@NonNull byte[] bytesOwnedIdentity);

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentityBySize(@NonNull byte[] bytesOwnedIdentity);

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentityBySizeAscending(@NonNull byte[] bytesOwnedIdentity);

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentityByName(@NonNull byte[] bytesOwnedIdentity);

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentityByNameAscending(@NonNull byte[] bytesOwnedIdentity);


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

    @Query("SELECT SUM(size) FROM (SELECT MAX(FMjoin." + FyleMessageJoinWithStatus.SIZE + ") AS size, fyle. " + Fyle.SHA256 +
            " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL " + // only count complete attachments
            " GROUP BY fyle." + Fyle.SHA256 + ")")
    LiveData<Long> getTotalUsage(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT SUM(size) FROM (SELECT MAX(FMjoin." + FyleMessageJoinWithStatus.SIZE + ") AS size, fyle. " + Fyle.SHA256 +
            " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL " + // only count complete attachments
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE :mimeStart " +
            " GROUP BY fyle." + Fyle.SHA256 + ")")
    LiveData<Long> getMimeUsage(@NonNull byte[] bytesOwnedIdentity, @NonNull String mimeStart);


    String FYLE_AND_ORIGIN_QUERY = "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " + MessageDao.PREFIX_MESSAGE_COLUMNS + ", fyle.*, FMjoin.* " +
            " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL ";

    String MEDIA_FYLE_AND_ORIGIN_QUERY = FYLE_AND_ORIGIN_QUERY +
            " AND (FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'video/%' " +
            " OR FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'image/%')";
    String AUDIO_FYLE_AND_ORIGIN_QUERY = FYLE_AND_ORIGIN_QUERY +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " LIKE 'audio/%' ";
    String FILE_FYLE_AND_ORIGIN_QUERY = FYLE_AND_ORIGIN_QUERY +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'audio/%' " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'video/%' " +
            " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " NOT LIKE 'image/%' ";

    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginSizeAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginSizeDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginDateAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginDateDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginNameAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginNameDesc(@NonNull byte[] bytesOwnedIdentity);

    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginSizeAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginSizeDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginDateAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginDateDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginNameAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginNameDesc(@NonNull byte[] bytesOwnedIdentity);

    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginSizeAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginSizeDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginDateAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginDateDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginNameAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginNameDesc(@NonNull byte[] bytesOwnedIdentity);

    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginSizeAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginSizeDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginDateAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginDateDesc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginNameAsc(@NonNull byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginNameDesc(@NonNull byte[] bytesOwnedIdentity);


    class FyleAndStatusTimestamped {
        @Embedded
        public FyleAndStatus fyleAndStatus;
        public long timestamp;

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof FyleAndStatusTimestamped)) {
                return false;
            }
            FyleAndStatusTimestamped other = (FyleAndStatusTimestamped) obj;
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
            if (!(obj instanceof FyleAndStatus)) {
                return false;
            }
            FyleAndStatus other = (FyleAndStatus) obj;
            return (other.fyle.id == fyle.id) && (other.fyleMessageJoinWithStatus.messageId == fyleMessageJoinWithStatus.messageId);
        }

        @Override
        public int hashCode() {
            return (int) (fyle.id + 31*fyleMessageJoinWithStatus.messageId);
        }
    }
}
