/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import androidx.room.Ignore;
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

    @Query("SELECT " + FyleMessageJoinWithStatus.MESSAGE_ID + " FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    List<Long> getMessageIdsForFyleSync(final long fyleId);

    @Query("SELECT COUNT(*) FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    Long countMessageForFyle(final long fyleId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    List<FyleAndStatus> getFylesAndStatusForMessageSync(final long messageId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId" +
            " AND FMjoin." + FyleMessageJoinWithStatus.FYLE_ID + " = :fyleId")
    FyleAndStatus getFyleAndStatus(final long messageId, final long fyleId);

    @Query("SELECT * FROM " + FyleMessageJoinWithStatus.TABLE_NAME +
            " WHERE " + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId " +
            " ORDER BY " + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
    List<FyleMessageJoinWithStatus> getStatusesForMessage(final long messageId);

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID + " = :messageId ORDER BY FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " ASC")
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

    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND ( FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " != ''" +
            " OR FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL) " +
            " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND mess." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY mess." + Message.TIMESTAMP + " DESC, mess.id DESC, " + // also sort by message id so that two messages with the same timestamp are not mixed
            " FMjoin." + FyleMessageJoinWithStatus.ENGINE_NUMBER + " DESC")
    LiveData<List<FyleAndStatus>> getImageAndVideoFylesAndStatusesForOwnedIdentity(byte[] bytesOwnedIdentity);



    @Query("SELECT fyle.*, FMjoin.* FROM " + Fyle.TABLE_NAME + " AS fyle " +
            " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.IMAGE_RESOLUTION + " IS NULL " +
            " AND fyle." + Fyle.FILE_PATH + " IS NOT NULL")
    List<FyleAndStatus> getCompleteFyleAndStatusWithoutResolution();


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
