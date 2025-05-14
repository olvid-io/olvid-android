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

import static io.olvid.messenger.databases.dao.DiscussionDao.PREFIX_DISCUSSION_COLUMNS;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;

@Dao
public interface MessageDao {
    String PREFIX_MESSAGE_COLUMNS = "mess.id AS mess_id, " +
            "mess." + Message.SENDER_SEQUENCE_NUMBER + " AS mess_" + Message.SENDER_SEQUENCE_NUMBER + ", " +
            "mess." + Message.CONTENT_BODY + " AS mess_" + Message.CONTENT_BODY + ", " +
            "mess." + Message.JSON_REPLY + " AS mess_" + Message.JSON_REPLY + ", " +
            "mess." + Message.JSON_EXPIRATION + " AS mess_" + Message.JSON_EXPIRATION + ", " +
            "mess." + Message.JSON_RETURN_RECEIPT + " AS mess_" + Message.JSON_RETURN_RECEIPT + ", " +
            "mess." + Message.JSON_LOCATION + " AS mess_" + Message.JSON_LOCATION + ", " +
            "mess." + Message.LOCATION_TYPE + " AS mess_" + Message.LOCATION_TYPE + ", " +
            "mess." + Message.SORT_INDEX + " AS mess_" + Message.SORT_INDEX + ", " +
            "mess." + Message.TIMESTAMP + " AS mess_" + Message.TIMESTAMP + ", " +
            "mess." + Message.STATUS + " AS mess_" + Message.STATUS + ", " +
            "mess." + Message.WIPE_STATUS + " AS mess_" + Message.WIPE_STATUS + ", " +
            "mess." + Message.MESSAGE_TYPE + " AS mess_" + Message.MESSAGE_TYPE + ", " +
            "mess." + Message.DISCUSSION_ID + " AS mess_" + Message.DISCUSSION_ID + ", " +
            "mess." + Message.INBOUND_MESSAGE_ENGINE_IDENTIFIER + " AS mess_" + Message.INBOUND_MESSAGE_ENGINE_IDENTIFIER + ", " +
            "mess." + Message.SENDER_IDENTIFIER + " AS mess_" + Message.SENDER_IDENTIFIER + ", " +
            "mess." + Message.SENDER_THREAD_IDENTIFIER + " AS mess_" + Message.SENDER_THREAD_IDENTIFIER + ", " +
            "mess." + Message.TOTAL_ATTACHMENT_COUNT + " AS mess_" + Message.TOTAL_ATTACHMENT_COUNT + ", " +
            "mess." + Message.IMAGE_COUNT + " AS mess_" + Message.IMAGE_COUNT + ", " +
            "mess." + Message.WIPED_ATTACHMENT_COUNT + " AS mess_" + Message.WIPED_ATTACHMENT_COUNT + ", " +
            "mess." + Message.EDITED + " AS mess_" + Message.EDITED + ", " +
            "mess." + Message.FORWARDED + " AS mess_" + Message.FORWARDED + ", " +
            "mess." + Message.REACTIONS + " AS mess_" + Message.REACTIONS + ", " +
            "mess." + Message.IMAGE_RESOLUTIONS + " AS mess_" + Message.IMAGE_RESOLUTIONS + ", " +
            "mess." + Message.MISSED_MESSAGE_COUNT + " AS mess_" + Message.MISSED_MESSAGE_COUNT + ", " +
            "mess." + Message.EXPIRATION_START_TIMESTAMP + " AS mess_" + Message.EXPIRATION_START_TIMESTAMP + ", " +
            "mess." + Message.LIMITED_VISIBILITY + " AS mess_" + Message.LIMITED_VISIBILITY + ", " +
            "mess." + Message.LINK_PREVIEW_FYLE_ID + " AS mess_" + Message.LINK_PREVIEW_FYLE_ID + ", " +
            "mess." + Message.JSON_MENTIONS + " AS mess_" + Message.JSON_MENTIONS + ", " +
            "mess." + Message.MENTIONED + " AS mess_" + Message.MENTIONED + ", " +
            "mess." + Message.BOOKMARKED + " AS mess_" + Message.BOOKMARKED;


    @Insert
    long insert(@NonNull Message message);

    @Delete
    void delete(@NonNull Message... messages);

    @Update
    void update(@NonNull Message message);

    @Query("SELECT m.id FROM " + Message.TABLE_NAME + " AS m " +
            " JOIN " + Message.FTS_TABLE_NAME + " ON m.id = " + Message.FTS_TABLE_NAME + ".rowid" +
            " WHERE m." + Message.MESSAGE_TYPE + " <= " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND m." + Message.DISCUSSION_ID + " = :discussionId" +
            " AND " + Message.FTS_TABLE_NAME + " MATCH :query ORDER BY m." + Message.TIMESTAMP + " DESC")
    List<Long> discussionSearch(long discussionId, @NonNull String query);


    class DiscussionAndMessage {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        @Embedded
        public Message message;
    }

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.TIMESTAMP + " = :timestamp, " +
            Message.SORT_INDEX + " = :sortIndex " +
            " WHERE id = :messageId")
    void updateTimestampAndSortIndex(long messageId, long timestamp, double sortIndex);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.WIPE_STATUS + " = :wipeStatus " +
            " WHERE id = :messageId")
    void updateWipeStatus(long messageId, int wipeStatus);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.STATUS + " = :status " +
            " WHERE id = :messageId")
    void updateStatus(long messageId, int status);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.MESSAGE_TYPE + " = :messageType " +
            " WHERE id = :messageId")
    void updateMessageType(long messageId, int messageType);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.MISSED_MESSAGE_COUNT + " = :missedMessageCount " +
            " WHERE id = :messageId")
    void updateMissedMessageCount(long messageId, long missedMessageCount);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.TOTAL_ATTACHMENT_COUNT + " = :totalAttachmentCount, " +
            Message.IMAGE_COUNT + " = :imageCount, " +
            Message.WIPED_ATTACHMENT_COUNT + " = :wipedAttachmentCount, " +
            Message.IMAGE_RESOLUTIONS + " = :imageResolutions " +
            " WHERE id = :messageId")
    void updateAttachmentCount(long messageId, int totalAttachmentCount, int imageCount, int wipedAttachmentCount, @Nullable String imageResolutions);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.CONTENT_BODY + " = NULL, " +
            Message.JSON_REPLY + " = NULL, " +
            Message.JSON_LOCATION + " = NULL, " +
            Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_NONE + ", " +
            Message.EDITED + " = " + Message.EDITED_NONE + ", " +
            Message.FORWARDED + " = 0, " +
            Message.WIPE_STATUS + " = :wipeStatus, " +
            Message.REACTIONS + " = NULL, " +
            Message.IMAGE_RESOLUTIONS + " = NULL, " +
            Message.JSON_MENTIONS + " = NULL, " +
            Message.LIMITED_VISIBILITY + " = 0 " +
            " WHERE id = :messageId")
    void updateWipe(long messageId, int wipeStatus);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.CONTENT_BODY + " = :body, " +
            Message.EDITED + " = " + Message.EDITED_UNSEEN +
            " WHERE id = :messageId")
    void updateBody(long messageId, @Nullable String body);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.FORWARDED + " = :forwarded " +
            " WHERE id = :messageId")
    void updateForwarded(long messageId, boolean forwarded);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.CONTENT_BODY + " = :body, " +
            Message.JSON_LOCATION + " = :jsonLocation " +
            " WHERE id = :messageId")
    void updateLocation(long messageId, @Nullable String body, @Nullable String jsonLocation);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.LOCATION_TYPE + " = :locationType " +
            " WHERE id = :messageId")
    void updateLocationType(long messageId, int locationType);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.REACTIONS + " = :reactions " +
            " WHERE id = :messageId")
    void updateReactions(long messageId, @Nullable String reactions);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.JSON_MENTIONS + " = :mentions " +
            " WHERE id = :messageId")
    void updateMentions(long messageId, @Nullable String mentions);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.MENTIONED + " = :mentioned " +
            " WHERE id = :messageId")
    void updateMentioned(long messageId, boolean mentioned);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.BOOKMARKED + " = :bookmarked " +
            " WHERE id = :messageId")
    void updateBookmarked(boolean bookmarked, long... messageId);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.EXPIRATION_START_TIMESTAMP + " = :expirationStartTimestamp " +
            " WHERE id = :messageId")
    void updateExpirationStartTimestamp(long messageId, long expirationStartTimestamp);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.LINK_PREVIEW_FYLE_ID + " = :linkPreviewFyleId " +
            " WHERE id = :messageId")
    void updateLinkPreviewFyleId(long messageId, @Nullable Long linkPreviewFyleId);

//    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = :discussionId AND " + Message.STATUS + " != " + Message.STATUS_DRAFT + " ORDER BY " + Message.SORT_INDEX + " ASC")
//    LiveData<List<Message>> getDiscussionMessages(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = :discussionId AND " + Message.STATUS + " != " + Message.STATUS_DRAFT + " ORDER BY " + Message.SORT_INDEX + " DESC")
    PagingSource<Integer,Message> getDiscussionMessagesPaged(long discussionId);

//    @Query("SELECT * FROM " + Message.TABLE_NAME +
//            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
//            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
//            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_GROUP_MEMBER_JOINED +
//            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_GROUP_MEMBER_LEFT +
//            " ORDER BY " + Message.SORT_INDEX + " ASC")
//    LiveData<List<Message>> getDiscussionMessagesWithoutGroupMemberChanges(long discussionId);
    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_GROUP_MEMBER_JOINED +
            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_GROUP_MEMBER_LEFT +
            " ORDER BY " + Message.SORT_INDEX + " DESC")
    PagingSource<Integer,Message> getDiscussionMessagesWithoutGroupMemberChangesPaged(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = :discussionId AND " + Message.STATUS + " != " + Message.STATUS_DRAFT + " ORDER BY " + Message.SORT_INDEX + " DESC LIMIT :count")
    LiveData<List<Message>> getLastDiscussionMessages(long discussionId, int count);


    @Query("SELECT COUNT(*) FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT)
    int countMessagesInDiscussion(long discussionId);

    @Query("SELECT COUNT(*) FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNREAD +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNPROCESSED +
            " AND " + Message.STATUS + " != " + Message.STATUS_COMPUTING_PREVIEW +
            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)
    int countExpirableMessagesInDiscussion(long discussionId);

    @Query("SELECT COUNT(*) FROM " + Message.TABLE_NAME + " AS m " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS c " +
            " ON m." + Message.DISCUSSION_ID + " = c." + DiscussionCustomization.DISCUSSION_ID +
            " WHERE m." + Message.STATUS + " != " + Message.STATUS_UNREAD +
            " AND m." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND m." + Message.STATUS + " != " + Message.STATUS_UNPROCESSED +
            " AND m." + Message.STATUS + " != " + Message.STATUS_COMPUTING_PREVIEW +
            " AND m." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND c." + DiscussionCustomization.PREF_DISCUSSION_RETENTION_COUNT + " IS NULL " +
            " GROUP BY m." + Message.DISCUSSION_ID)
    List<Integer> countExpirableMessagesInDiscussionsWithNoCustomization();


    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE (" + Message.STATUS + " = " + Message.STATUS_UNPROCESSED +
            " OR " + Message.STATUS + " = " + Message.STATUS_COMPUTING_PREVIEW + ") " +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE)
    List<Message> getUnprocessedAndPreviewingMessages();

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.STATUS + " = " + Message.STATUS_PROCESSING +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE)
    List<Message> getProcessingMessages();

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.STATUS + " = " + Message.STATUS_DRAFT +
            " AND " + Message.DISCUSSION_ID + " = :discussionId " +
            " ORDER BY " + Message.TIMESTAMP + " DESC LIMIT 1")
    @Nullable Message getDiscussionDraftMessageSync(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.STATUS + " = " + Message.STATUS_DRAFT +
            " AND " + Message.DISCUSSION_ID + " = :discussionId " +
            " ORDER BY " + Message.TIMESTAMP + " DESC LIMIT 1")
    LiveData<Message> getDiscussionDraftMessage(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE id = :messageId")
    @Nullable Message get(long messageId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE id = :messageId")
    LiveData<Message> getLive(long messageId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber ")
    @Nullable Message getBySenderSequenceNumber(long senderSequenceNumber, @NonNull UUID senderThreadIdentifier, @NonNull byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " > :senderSequenceNumber " +
            " ORDER BY " + Message.SENDER_SEQUENCE_NUMBER + " ASC " +
            " LIMIT 1")
    @Nullable Message getFollowingBySenderSequenceNumber(long senderSequenceNumber, @NonNull UUID senderThreadIdentifier, @NonNull byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber ")
    LiveData<Message> getBySenderSequenceNumberAsync(long senderSequenceNumber, @NonNull UUID senderThreadIdentifier, @NonNull byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE id IN (:selectedMessageIds)")
    List<Message> getMany(@NonNull List<Long> selectedMessageIds);

    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", m.* FROM " + Message.TABLE_NAME + " AS m " +
            " JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = m." + Message.DISCUSSION_ID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " WHERE m." + Message.BOOKMARKED + " = 1 " +
            " ORDER BY m." + Message.SORT_INDEX + " ASC")
    LiveData<List<DiscussionAndMessage>> getAllBookmarkedLiveData(@NonNull byte[] bytesOwnedIdentity);


    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.STATUS + " = " + Message.STATUS_READ +
            " WHERE id IN(:messageIds) " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD)
    void markMessagesRead(@NonNull Long[] messageIds);


    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.EDITED + " = " + Message.EDITED_SEEN +
            " WHERE id IN(:messageIds) " +
            " AND " + Message.EDITED + " = " + Message.EDITED_UNSEEN)
    void markEditedMessagesSeen(@NonNull Long[] messageIds);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE id IN(:messageIds) " +
            " AND " + Message.WIPE_STATUS + " = " + Message.WIPE_STATUS_WIPE_ON_READ)
    List<Message> getWipeOnReadSubset(@NonNull Long[] messageIds);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.WIPE_STATUS + " = " + Message.WIPE_STATUS_WIPE_ON_READ)
    List<Message> getAllWipeOnRead();

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " > :senderSequenceNumber " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY " + Message.SENDER_SEQUENCE_NUMBER + " ASC " +
            " LIMIT 1")
    @Nullable Message getNextMessageBySequenceNumber(long senderSequenceNumber, @NonNull UUID senderThreadIdentifier, @NonNull byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " < :senderSequenceNumber " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY " + Message.SENDER_SEQUENCE_NUMBER + " DESC " +
            " LIMIT 1")
    @Nullable Message getPreviousMessageBySequenceNumber(long senderSequenceNumber, @NonNull UUID senderThreadIdentifier, @NonNull byte[] senderIdentifier, long discussionId);


    @Query("SELECT " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SORT_INDEX + " < :maxSortIndex " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY " + Message.SORT_INDEX + " DESC " +
            " LIMIT 1")
    @Nullable Double getPreviousSortIndex(double maxSortIndex, long discussionId);

    @Query("SELECT " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SORT_INDEX + " > :maxSortIndex " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY " + Message.SORT_INDEX + " ASC " +
            " LIMIT 1")
    @Nullable Double getNextSortIndex(double maxSortIndex, long discussionId);

    @Query("SELECT MAX(" + Message.SORT_INDEX + ") FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = :discussionId")
    @Nullable Double getDiscussionMaxSortIndex(long discussionId);

    @Query("SELECT count(*) FROM " + Message.TABLE_NAME + " AS mess " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE mess." + Message.INBOUND_MESSAGE_ENGINE_IDENTIFIER + " = :engineIdentifier " +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    int getCountForEngineIdentifier(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] engineIdentifier);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = :discussionId")
    List<Message> getAllDiscussionMessagesSync(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT)
    List<Message> getAllNonDraftDiscussionMessagesSync(long discussionId);



    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.STATUS + " = " + Message.STATUS_READ +
            " WHERE " + Message.STATUS + " = " + Message.STATUS_UNREAD +
            " AND " + Message.DISCUSSION_ID + " = :discussionId")
    void markAllDiscussionMessagesRead(long discussionId);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.STATUS + " = " + Message.STATUS_READ +
            " WHERE " + Message.STATUS + " = " + Message.STATUS_UNREAD +
            " AND " + Message.DISCUSSION_ID + " = :discussionId" +
            " AND " + Message.TIMESTAMP + " <= :timestamp ")
    void markDiscussionMessagesReadUpTo(long discussionId, long timestamp);

    @Query("SELECT EXISTS " +
            " ( SELECT 1 FROM " + Message.TABLE_NAME + " AS message " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS discussion " +
            " ON discussion.id = " + Message.DISCUSSION_ID +
            " WHERE discussion." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND discussion." + Discussion.ARCHIVED + " = 0 " +
            " AND message." + Message.STATUS + " = " + Message.STATUS_UNREAD +
            " " +
            " UNION " +
            " SELECT 1 FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.UNREAD + " = 1 " +
            " AND " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " " +
            " UNION " +
            " SELECT 1 FROM " + Invitation.TABLE_NAME + " AS inv " +
            " WHERE inv." + Invitation.CATEGORY_ID + " IN ( " +
            ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY +  ") " +
            " AND inv." + Invitation.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " )")
    LiveData<Boolean> hasUnreadMessagesOrDiscussionsOrInvitations(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT COUNT(*) as unread_count, id as message_id, min(" + Message.SORT_INDEX + ") as min_sort_index FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD)
    LiveData<UnreadCountAndFirstMessage> getUnreadCountAndFirstMessage(long discussionId);

    @Query("DELETE FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " = " + Message.STATUS_DRAFT)
    void deleteDiscussionDraftMessage(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_NEW_PUBLISHED_DETAILS)
    List<Message> getAllDiscussionNewPublishedDetailsMessages(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD)
    List<Message> getAllUnreadDiscussionMessagesSync(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.IMAGE_COUNT + " != 0")
    List<Message> getAllWithImages();

    @Query("SELECT message.* FROM " + Message.TABLE_NAME + " AS message " +
            " JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
            " ON message.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
            " WHERE FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " = '" + OpenGraph.MIME_TYPE + "'")
    List<Message> getAllWithLinkPreview();

    @Query("SELECT id FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.TIMESTAMP + " < :minTimestamp" +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNREAD +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNPROCESSED +
            " AND " + Message.STATUS + " != " + Message.STATUS_COMPUTING_PREVIEW +
            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)
    List<Long> getOldDiscussionMessages(long discussionId, long minTimestamp);

    @Query("SELECT count(*) FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.TIMESTAMP + " < :minTimestamp" +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNREAD +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNPROCESSED +
            " AND " + Message.STATUS + " != " + Message.STATUS_COMPUTING_PREVIEW +
            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)
    int countOldDiscussionMessages(long discussionId, long minTimestamp);

    @Query("SELECT count(*) FROM " + Message.TABLE_NAME + " AS m " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS c " +
            " ON m." + Message.DISCUSSION_ID + " = c." + DiscussionCustomization.DISCUSSION_ID +
            " WHERE m." + Message.TIMESTAMP + " < :minTimestamp" +
            " AND m." + Message.STATUS + " != " + Message.STATUS_UNREAD +
            " AND m." + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND m." + Message.STATUS + " != " + Message.STATUS_UNPROCESSED +
            " AND m." + Message.STATUS + " != " + Message.STATUS_COMPUTING_PREVIEW +
            " AND m." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " AND c." + DiscussionCustomization.PREF_DISCUSSION_RETENTION_DURATION + " IS NULL ")
    int countOldMessagesInDiscussionsWithNoCustomization(long minTimestamp);

    @Query("SELECT id FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNREAD +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " AND " + Message.STATUS + " != " + Message.STATUS_UNPROCESSED +
            " AND " + Message.STATUS + " != " + Message.STATUS_COMPUTING_PREVIEW +
            " AND " + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " ORDER BY " + Message.SORT_INDEX + " ASC LIMIT :number")
    List<Long> getExcessiveDiscussionMessages(long discussionId, int number);

    @Query("SELECT COUNT( distinct mess.id ) FROM " + Message.TABLE_NAME + " AS mess " +
    " INNER JOIN " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
    " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
    " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
    " ON FMjoin." + FyleMessageJoinWithStatus.FYLE_ID + " = fyle.id " +
    " WHERE fyle." + Fyle.FILE_PATH + " IS NULL " +
    " AND mess.id IN ( :selectedMessageIds )")
    int countMessagesWithIncompleteFyles(@NonNull List<Long> selectedMessageIds);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.JSON_LOCATION + " NOT NULL " +
            " AND " + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND " + Message.STATUS + " != " + Message.STATUS_SENT_FROM_ANOTHER_DEVICE)
    List<Message> getOutboundSharingLocationMessages();

    @Query("SELECT COUNT(*) FROM " + Message.TABLE_NAME +
            " WHERE " + Message.JSON_LOCATION + " NOT NULL " +
            " AND " + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND " + Message.STATUS + " != " + Message.STATUS_SENT_FROM_ANOTHER_DEVICE)
    int countOutboundSharingLocationMessages();

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.JSON_LOCATION + " NOT NULL " +
            " AND " + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND " + Message.DISCUSSION_ID + " = :discussionId " +
            " ORDER BY " + Message.SORT_INDEX + " ASC ")
    LiveData<List<Message>> getCurrentlySharingLocationMessagesInDiscussionLiveData(long discussionId);

    @Query("SELECT m.* FROM " + Message.TABLE_NAME + " AS m " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON m." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE m." + Message.JSON_LOCATION + " NOT NULL " +
            " AND m." + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY " + Message.TIMESTAMP + " ASC ")
    LiveData<List<Message>> getCurrentlySharingLocationMessagesForOwnedIdentityLiveData(@NonNull byte[] bytesOwnedIdentity);


    @Query("SELECT EXISTS " +
            "(SELECT 1 FROM " + Message.TABLE_NAME + " AS m " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON m." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE m." + Message.JSON_LOCATION + " NOT NULL " +
            " AND m." + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity)")
    LiveData<Boolean> hasLocationSharing(@NonNull byte[] bytesOwnedIdentity);


    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.JSON_LOCATION + " NOT NULL " +
            " AND " + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND (" + Message.MESSAGE_TYPE + " = " + Message.TYPE_INBOUND_MESSAGE +
            " OR (" + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND " + Message.STATUS + " != " + Message.STATUS_SENT_FROM_ANOTHER_DEVICE + ")) " +
            " AND " + Message.DISCUSSION_ID + " = :discussionId")
    List<Message> getCurrentlySharingInboundLocationMessagesInDiscussion(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.JSON_LOCATION + " NOT NULL " +
            " AND " + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND " + Message.STATUS + " != " + Message.STATUS_SENT_FROM_ANOTHER_DEVICE +
            " AND " + Message.DISCUSSION_ID + " = :discussionId " +
            " ORDER BY " + Message.SORT_INDEX + " ASC ")
    List<Message> getCurrentlySharingOutboundLocationMessagesInDiscussion(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.JSON_LOCATION + " NOT NULL " +
            " AND " + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE +
            " AND " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " ORDER BY " + Message.SORT_INDEX + " ASC ")
    List<Message> getCurrentLocationSharingMessagesOfIdentityInDiscussion(@NonNull byte[] senderIdentifier, long discussionId);

    @Query("SELECT id FROM " + Message.TABLE_NAME +
            " WHERE " + Message.LIMITED_VISIBILITY + " = 1")
    List<Long> getAllLimitedVisibilityMessageIds();

    @Query("SELECT max(" + Message.TIMESTAMP + ") FROM " + Message.TABLE_NAME +
            " WHERE " + Message.MESSAGE_TYPE + " IN (" + Message.TYPE_INBOUND_MESSAGE + "," + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE + ") " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD +
            " AND " + Message.DISCUSSION_ID + " = :discussionId ")
    @Nullable Long getServerTimestampOfLatestUnreadInboundMessageInDiscussion(long discussionId);

    @Query("SELECT COUNT(*) FROM " + Message.TABLE_NAME +
            " WHERE " + Message.WIPE_STATUS + " = " + Message.WIPE_STATUS_REMOTE_DELETED)
    int countRemoteDeletedMessages();

    @Query("DELETE FROM " + Message.TABLE_NAME +
            " WHERE " + Message.WIPE_STATUS + " = " + Message.WIPE_STATUS_REMOTE_DELETED)
    void deleteAllRemoteDeletedMessages();


    @Query("SELECT EXISTS " +
            " ( SELECT 1 FROM " + Message.TABLE_NAME + " AS message " +
            " WHERE message." + Message.STATUS + " = " + Message.STATUS_UNREAD +
            " " +
            " UNION " +
            " SELECT 1 FROM " + Invitation.TABLE_NAME + " AS inv " +
            " WHERE inv." + Invitation.CATEGORY_ID + " IN ( " +
            ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY +  ") " +
            " )")
    boolean unreadMessagesOrInvitationsExist();

    @Query("SELECT id, " + Message.DISCUSSION_ID + ", " + Message.MENTIONED + ", " + Message.TIMESTAMP + " FROM " + Message.TABLE_NAME +
            " WHERE (" + Message.MESSAGE_TYPE + " = " + Message.TYPE_INBOUND_MESSAGE +
            " OR " + Message.MESSAGE_TYPE + " = " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
            " OR " + Message.MESSAGE_TYPE + " = " + Message.TYPE_PHONE_CALL +
            " OR " + Message.MESSAGE_TYPE + " = " + Message.TYPE_NEW_PUBLISHED_DETAILS + " ) " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD)
    List<UnreadMessageStub> getAllUnreadMessageStubs();

    @Query("SELECT id, " + Message.DISCUSSION_ID + " FROM " + Message.TABLE_NAME +
            " WHERE " + Message.JSON_LOCATION + " NOT NULL " +
            " AND " + Message.LOCATION_TYPE + " = " + Message.LOCATION_TYPE_SHARE)
    List<LocationMessageStub> getAllLocationMessageStubs();

    class UnreadCountAndFirstMessage {
        @ColumnInfo(name = "unread_count")
        public int unreadCount;

        @ColumnInfo(name = "message_id")
        public long messageId;

        @ColumnInfo(name = "min_sort_index")
        public double minSortIndex;
    }

    class UnreadMessageStub {
        @ColumnInfo(name = "id")
        public long messageId;

        @ColumnInfo(name = Message.DISCUSSION_ID)
        public long discussionId;

        @ColumnInfo(name = Message.MENTIONED)
        public boolean mentioned;

        @ColumnInfo(name = Message.TIMESTAMP)
        public long timestamp;
    }

    class LocationMessageStub {
        @ColumnInfo(name = "id")
        public long messageId;

        @ColumnInfo(name = Message.DISCUSSION_ID)
        public long discussionId;
    }
}
