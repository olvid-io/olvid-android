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

import androidx.lifecycle.LiveData;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;

@Dao
public interface MessageDao {
    @Insert
    long insert(Message message);

    @Delete
    void delete(Message... messages);

    @Update
    void update(Message message);

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
    void updateAttachmentCount(long messageId, int totalAttachmentCount, int imageCount, int wipedAttachmentCount, String imageResolutions);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.WIPE_STATUS + " = :wipeStatus, " +
            Message.EDITED + " = " + Message.EDITED_NONE + ", " +
            Message.CONTENT_BODY + " = NULL, " +
            Message.REACTIONS + " = NULL, " +
            Message.JSON_REPLY + " = NULL " +
            " WHERE id = :messageId")
    void updateWipe(long messageId, int wipeStatus);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.CONTENT_BODY + " = :body, " +
            Message.EDITED + " = " + Message.EDITED_UNSEEN +
            " WHERE id = :messageId")
    void updateBody(long messageId, String body);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.REACTIONS + " = :reactions " +
            " WHERE id = :messageId")
    void updateReactions(long messageId, String reactions);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = :discussionId AND " + Message.STATUS + " != " + Message.STATUS_DRAFT + " ORDER BY " + Message.SORT_INDEX + " ASC")
    LiveData<List<Message>> getDiscussionMessages(long discussionId);

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

    @Query("SELECT message.* FROM " + Message.TABLE_NAME + " AS message " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS discussion " +
            " ON message." + Message.DISCUSSION_ID + " = discussion.id" +
            " WHERE (" + Message.STATUS + " = " + Message.STATUS_UNPROCESSED +
            " OR " + Message.STATUS + " = " + Message.STATUS_COMPUTING_PREVIEW + ") " +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND discussion." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND discussion." + Discussion.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    List<Message> getUnprocessedOrPreviewingMessagesForContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT message.* FROM " + Message.TABLE_NAME + " AS message " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS discussion " +
            " ON message." + Message.DISCUSSION_ID + " = discussion.id" +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS cgjoin " +
            " ON discussion." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = cgjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " AND discussion." + Discussion.BYTES_OWNED_IDENTITY  + " = cgjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " WHERE (" + Message.STATUS + " = " + Message.STATUS_UNPROCESSED +
            " OR " + Message.STATUS + " = " + Message.STATUS_COMPUTING_PREVIEW + ") " +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND cgjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND cgjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    List<Message> getUnprocessedOrPreviewingGroupMessagesForContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT message.* FROM " + Message.TABLE_NAME + " AS message " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS discussion " +
            " ON message." + Message.DISCUSSION_ID + " = discussion.id" +
            " WHERE (" + Message.STATUS + " = " + Message.STATUS_UNPROCESSED +
            " OR " + Message.STATUS + " = " + Message.STATUS_COMPUTING_PREVIEW + ") " +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND discussion." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND discussion." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    List<Message> getUnprocessedAndPreviewingGroupMessages(byte[] bytesGroupOwnerAndUid, byte[] bytesOwnedIdentity);


    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_PROCESSING + " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE)
    List<Message> getProcessingMessages();

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.STATUS + " = " + Message.STATUS_DRAFT +
            " AND " + Message.DISCUSSION_ID + " = :discussionId " +
            " ORDER BY " + Message.TIMESTAMP + " DESC LIMIT 1")
    Message getDiscussionDraftMessageSync(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.STATUS + " = " + Message.STATUS_DRAFT +
            " AND " + Message.DISCUSSION_ID + " = :discussionId " +
            " ORDER BY " + Message.TIMESTAMP + " DESC LIMIT 1")
    LiveData<Message> getDiscussionDraftMessage(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE id = :messageId")
    Message get(long messageId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE id = :messageId")
    LiveData<Message> getLive(long messageId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber ")
    Message getBySenderSequenceNumber(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " > :senderSequenceNumber " +
            " ORDER BY " + Message.SENDER_SEQUENCE_NUMBER + " ASC " +
            " LIMIT 1")
    Message getFollowingBySenderSequenceNumber(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber ")
    LiveData<Message> getBySenderSequenceNumberAsync(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME + " WHERE id IN (:selectedMessageIds)")
    List<Message> getMany(List<Long> selectedMessageIds);

    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.STATUS + " = " + Message.STATUS_READ +
            " WHERE id IN(:messageIds) " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD)
    void markMessagesRead(Long[] messageIds);


    @Query("UPDATE " + Message.TABLE_NAME +
            " SET " + Message.EDITED + " = " + Message.EDITED_SEEN +
            " WHERE id IN(:messageIds) " +
            " AND " + Message.EDITED + " = " + Message.EDITED_UNSEEN)
    void markEditedMessagesSeen(Long[] messageIds);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE id IN(:messageIds) " +
            " AND " + Message.WIPE_STATUS + " = " + Message.WIPE_STATUS_WIPE_ON_READ)
    List<Message> getWipeOnReadSubset(Long[] messageIds);

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
    Message getNextMessageBySequenceNumber(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
            " AND " + Message.SENDER_IDENTIFIER + " = :senderIdentifier " +
            " AND " + Message.SENDER_SEQUENCE_NUMBER + " < :senderSequenceNumber " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY " + Message.SENDER_SEQUENCE_NUMBER + " DESC " +
            " LIMIT 1")
    Message getPreviousMessageBySequenceNumber(long senderSequenceNumber, UUID senderThreadIdentifier, byte[] senderIdentifier, long discussionId);


    @Query("SELECT " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :disucssionId " +
            " AND " + Message.SORT_INDEX + " < :maxSortIndex " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY " + Message.SORT_INDEX + " DESC " +
            " LIMIT 1")
    Double getPreviousSortIndex(double maxSortIndex, long disucssionId);

    @Query("SELECT " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :disucssionId " +
            " AND " + Message.SORT_INDEX + " > :maxSortIndex " +
            " AND " + Message.STATUS + " != " + Message.STATUS_DRAFT +
            " ORDER BY " + Message.SORT_INDEX + " ASC " +
            " LIMIT 1")
    Double getNextSortIndex(double maxSortIndex, long disucssionId);

    @Query("SELECT MAX(" + Message.SORT_INDEX + ") FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = :discussionId")
    double getDiscussionMaxSortIndex(long discussionId);

    @Query("SELECT count(*) FROM " + Message.TABLE_NAME + " AS mess " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE mess." + Message.ENGINE_MESSAGE_IDENTIFIER + " = :engineIdentifier " +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    int getCountForEngineIdentifier(byte[] bytesOwnedIdentity, byte[] engineIdentifier);

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

    @Query("SELECT COUNT(*) > 0 FROM " + Message.TABLE_NAME + " AS message " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS discussion " +
            " ON discussion.id = " + Message.DISCUSSION_ID +
            " WHERE discussion." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND message." + Message.STATUS + " = " + Message.STATUS_UNREAD)
    LiveData<Boolean> hasUnreadMessages(byte[] bytesOwnedIdentity);

    @Query("SELECT COUNT(*) as unread_count, id as message_id, min(" + Message.SORT_INDEX + ") as min_sort_index FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD)
    LiveData<UnreadCountAndFirstMessage> getUnreadCountAndFirstMessage(long discussionId);

    @Query("DELETE FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " = " + Message.STATUS_DRAFT)
    void deleteDiscussionDraftMessage(long discussionId);

    @Query("DELETE FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_NEW_PUBLISHED_DETAILS)
    void deleteAllDiscussionNewPublishedDetailsMessages(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.DISCUSSION_ID + " = :discussionId " +
            " AND " + Message.STATUS + " = " + Message.STATUS_UNREAD)
    List<Message> getAllUnreadDiscussionMessagesSync(long discussionId);

    @Query("SELECT * FROM " + Message.TABLE_NAME +
            " WHERE " + Message.IMAGE_COUNT + " != 0")
    List<Message> getAllWithImages();

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


    class UnreadCountAndFirstMessage {
        @ColumnInfo(name = "unread_count")
        public int unreadCount;

        @ColumnInfo(name = "message_id")
        public long messageId;

        @ColumnInfo(name = "min_sort_index")
        public double minSortIndex;
    }
}
