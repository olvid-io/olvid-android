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

import android.net.Uri;

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
    void insert(FyleMessageJoinWithStatus fyleMessageJoinWithStatus);

    @Update
    void update(FyleMessageJoinWithStatus fyleMessageJoinWithStatus);

    @Delete
    void delete(FyleMessageJoinWithStatus fyleMessageJoinWithStatus);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.ENGINE_MESSAGE_IDENTIFIER + " = :engineMessageIdentifier, " +
            FyleMessageJoinWithStatus.ENGINE_NUMBER + " = :engineNumber " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateEngineIdentifier(long messageId, long fyleId, byte[] engineMessageIdentifier, int engineNumber);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.STATUS + " = :status " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateStatus(long messageId, long fyleId, int status);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.PROGRESS + " = :progress " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateProgress(long messageId, long fyleId, float progress);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.STATUS + " = :status, " +
            FyleMessageJoinWithStatus.PROGRESS + " = :progress, " +
            FyleMessageJoinWithStatus.SIZE + " = :size " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateStatusProgressSize(long messageId, long fyleId, int status, float progress, long size);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " = :imageResolution " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateImageResolution(long messageId, long fyleId, String imageResolution);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.WAS_OPENED + " = 1 " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateWasOpened(long messageId, long fyleId);

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
    void updateMiniPreview(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int number, byte[] miniPreview);

    @Query("UPDATE " + FyleMessageJoinWithStatus.TABLE_NAME +
            " SET " + FyleMessageJoinWithStatus.FILE_PATH + " = :filePath " +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId ")
    void updateFilePath(long messageId, long fyleId, String filePath);

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
    FyleAndStatus getFyleAndStatus(final long messageId, final long fyleId);

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
    FyleMessageJoinWithStatus getByEngineIdentifierAndNumber(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int number);

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " WHERE " + FyleMessageJoinWithStatus.STATUS + " = " + FyleMessageJoinWithStatus.STATUS_UPLOADING)
    List<FyleMessageJoinWithStatus> getUploading();

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " WHERE " + FyleMessageJoinWithStatus.STATUS + " IN (" + FyleMessageJoinWithStatus.STATUS_DOWNLOADING + "," + FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE + ")")
    List<FyleMessageJoinWithStatus> getDownloading();

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    List<FyleMessageJoinWithStatus> getForFyleId(long fyleId);


    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId AND " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId")
    FyleMessageJoinWithStatus get(long fyleId, long messageId);

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND " + FyleMessageJoinWithStatus.ENGINE_NUMBER + " = :attachmentNumber")
    FyleMessageJoinWithStatus getByIdAndAttachmentNumber(long messageId, Integer attachmentNumber);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE mess." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY mess." + Message.SORT_INDEX + " DESC, " +
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForDiscussion(Long discussionId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusForMessage(long messageId);


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
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentity(byte[] bytesOwnedIdentity);

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentityBySize(byte[] bytesOwnedIdentity);

    @Query(IMAGE_AND_VIDEO_FOR_OWNED_IDENTITY_QUERY +
            " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentityByName(byte[] bytesOwnedIdentity);


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
    LiveData<Long> getTotalUsage(byte[] bytesOwnedIdentity);

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
    LiveData<Long> getMimeUsage(byte[] bytesOwnedIdentity, String mimeStart);


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
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginSizeAsc(byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginSizeDesc(byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginDateAsc(byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginDateDesc(byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginNameAsc(byte[] bytesOwnedIdentity);
    @Query(MEDIA_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getMediaFyleAndOriginNameDesc(byte[] bytesOwnedIdentity);

    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginSizeAsc(byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginSizeDesc(byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginDateAsc(byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginDateDesc(byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginNameAsc(byte[] bytesOwnedIdentity);
    @Query(FILE_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getFileFyleAndOriginNameDesc(byte[] bytesOwnedIdentity);

    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginSizeAsc(byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginSizeDesc(byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginDateAsc(byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginDateDesc(byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginNameAsc(byte[] bytesOwnedIdentity);
    @Query(AUDIO_FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getAudioFyleAndOriginNameDesc(byte[] bytesOwnedIdentity);

    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " ASC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginSizeAsc(byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.SIZE + " DESC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginSizeDesc(byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " ASC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginDateAsc(byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY mess." + Message.TIMESTAMP + " DESC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginDateDesc(byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " ASC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginNameAsc(byte[] bytesOwnedIdentity);
    @Query(FYLE_AND_ORIGIN_QUERY + " ORDER BY FMjoin." + FyleMessageJoinWithStatus.FILE_NAME + " DESC ")
    LiveData<List<FyleAndOrigin>> getFyleAndOriginNameDesc(byte[] bytesOwnedIdentity);



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

        public Uri getContentUri() {
            if (fyle.sha256 == null) {
                return null;
            }
            byte[] randomizer = new byte[16];
            new SecureRandom().nextBytes(randomizer);
            return Uri.parse(BuildConfig.CONTENT_PROVIDER_URI_PREFIX + Logger.toHexString(fyle.sha256) + "/" + fyleMessageJoinWithStatus.messageId + "/" + Logger.toHexString(randomizer));
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
