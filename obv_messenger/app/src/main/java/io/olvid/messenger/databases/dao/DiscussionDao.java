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
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RoomWarnings;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;

@Dao
public abstract class DiscussionDao {
    @Insert
    public abstract long insert(@NonNull Discussion discussion);

    @Delete
    public abstract void delete(@NonNull Discussion discussion);

    @Update
    public abstract void updateAll(@NonNull Discussion discussion);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.KEYCLOAK_MANAGED + " = :keycloakManaged " +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_CONTACT +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesContactIdentity")
    public abstract void updateKeycloakManaged(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean keycloakManaged);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.ACTIVE + " = :active " +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity "+
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_CONTACT +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesContactIdentity")
    public abstract void updateActive(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean active);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.TRUST_LEVEL + " = :trustLevel " +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_CONTACT +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesContactIdentity")
    public abstract void updateTrustLevel(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, int trustLevel);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.LAST_MESSAGE_TIMESTAMP + " = :lastMessageTimestamp " +
            " WHERE id = :discussionId ")
    public abstract void updateLastMessageTimestamp(long discussionId, long lastMessageTimestamp);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.LAST_REMOTE_DELETE_TIMESTAMP + " = :lastRemoteDeleteTimestamp " +
            " WHERE id = :discussionId ")
    public abstract void updateLastRemoteDeleteTimestamp(long discussionId, long lastRemoteDeleteTimestamp);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.STATUS + " = :status " +
            " WHERE id = :discussionId ")
    public abstract void updateStatus(long discussionId, int status);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.PINNED + " = :pinned " +
            " WHERE id = :discussionId ")
    public abstract void updatePinned(long discussionId, int pinned);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.ARCHIVED + " = :archived " +
            " WHERE id = :discussionId ")
    public abstract void updateArchived(boolean archived, long... discussionId);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + " = :lastOutboundMessageSequenceNumber " +
            " WHERE id = :discussionId ")
    public abstract void updateLastOutboundMessageSequenceNumber(long discussionId, long lastOutboundMessageSequenceNumber);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.TITLE + " = :title, " +
            Discussion.PHOTO_URL + " = :photoUrl " +
            " WHERE id = :discussionId ")
    public abstract void updateTitleAndPhotoUrl(long discussionId, @Nullable String title, @Nullable String photoUrl);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.STATUS + " = " + Discussion.STATUS_LOCKED + ", " +
            Discussion.KEYCLOAK_MANAGED + " = 0, " +
            Discussion.ACTIVE + " = 1, " +
            Discussion.TRUST_LEVEL + " = NULL " +
            " WHERE id = :discussionId ")
    public abstract void updateAsLocked(long discussionId);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.UNREAD + " = :unread " +
            " WHERE id = :discussionId ")
    public abstract void updateDiscussionUnreadStatus(long discussionId, boolean unread);


    static final String PREFIX_DISCUSSION_COLUMNS = "disc.id AS disc_id, " +
            " disc." + Discussion.TITLE + " AS disc_" + Discussion.TITLE + ", " +
            " disc." + Discussion.BYTES_OWNED_IDENTITY + " AS disc_" + Discussion.BYTES_OWNED_IDENTITY + ", " +
            " disc." + Discussion.DISCUSSION_TYPE + " AS disc_" + Discussion.DISCUSSION_TYPE + ", " +
            " disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " AS disc_" + Discussion.BYTES_DISCUSSION_IDENTIFIER + ", " +
            " disc." + Discussion.SENDER_THREAD_IDENTIFIER + " AS disc_" + Discussion.SENDER_THREAD_IDENTIFIER + ", " +
            " disc." + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + " AS disc_" + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + ", " +
            " disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " AS disc_" + Discussion.LAST_MESSAGE_TIMESTAMP + ", " +
            " disc." + Discussion.LAST_REMOTE_DELETE_TIMESTAMP + " AS disc_" + Discussion.LAST_REMOTE_DELETE_TIMESTAMP + ", " +
            " disc." + Discussion.PHOTO_URL + " AS disc_" + Discussion.PHOTO_URL + ", " +
            " disc." + Discussion.KEYCLOAK_MANAGED + " AS disc_" + Discussion.KEYCLOAK_MANAGED + ", " +
            " disc." + Discussion.PINNED + " AS disc_" + Discussion.PINNED + ", " +
            " disc." + Discussion.ARCHIVED + " AS disc_" + Discussion.ARCHIVED + ", " +
            " disc." + Discussion.UNREAD + " AS disc_" + Discussion.UNREAD + ", " +
            " disc." + Discussion.ACTIVE + " AS disc_" + Discussion.ACTIVE + ", " +
            " disc." + Discussion.TRUST_LEVEL + " AS disc_" + Discussion.TRUST_LEVEL + ", " +
            " disc." + Discussion.STATUS + " AS disc_" + Discussion.STATUS;

    private static final String PREFIX_DISCUSSION_CUSTOMIZATION_COLUMNS =
            "cust." + DiscussionCustomization.DISCUSSION_ID + " AS cust_" + DiscussionCustomization.DISCUSSION_ID + ", " +
                    "cust." + DiscussionCustomization.SERIALIZED_COLOR_JSON + " AS cust_" + DiscussionCustomization.SERIALIZED_COLOR_JSON + ", " +
                    "cust." + DiscussionCustomization.BACKGROUND_IMAGE_URL + " AS cust_" + DiscussionCustomization.BACKGROUND_IMAGE_URL + ", " +
                    "cust." + DiscussionCustomization.PREF_SEND_READ_RECEIPT + " AS cust_" + DiscussionCustomization.PREF_SEND_READ_RECEIPT + ", " +
                    "cust." + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS + " AS cust_" + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS + ", " +
                    "cust." + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS_EXCEPT_MENTIONED + " AS cust_" + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS_EXCEPT_MENTIONED + ", " +
                    "cust." + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " AS cust_" + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + ", " +
                    "cust." + DiscussionCustomization.PREF_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_MESSAGES + " AS cust_" + DiscussionCustomization.PREF_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_MESSAGES + ", " +
                    "cust." + DiscussionCustomization.PREF_RETAIN_WIPED_OUTBOUND_MESSAGES + " AS cust_" + DiscussionCustomization.PREF_RETAIN_WIPED_OUTBOUND_MESSAGES + ", " +
                    "cust." + DiscussionCustomization.PREF_DISCUSSION_RETENTION_COUNT + " AS cust_" + DiscussionCustomization.PREF_DISCUSSION_RETENTION_COUNT + ", " +
                    "cust." + DiscussionCustomization.PREF_DISCUSSION_RETENTION_DURATION + " AS cust_" + DiscussionCustomization.PREF_DISCUSSION_RETENTION_DURATION + ", " +
                    "cust." + DiscussionCustomization.PREF_USE_CUSTOM_MESSAGE_NOTIFICATION + " AS cust_" + DiscussionCustomization.PREF_USE_CUSTOM_MESSAGE_NOTIFICATION + ", " +
                    "cust." + DiscussionCustomization.PREF_MESSAGE_NOTIFICATION_RINGTONE + " AS cust_" + DiscussionCustomization.PREF_MESSAGE_NOTIFICATION_RINGTONE + ", " +
                    "cust." + DiscussionCustomization.PREF_MESSAGE_NOTIFICATION_VIBRATION_PATTERN + " AS cust_" + DiscussionCustomization.PREF_MESSAGE_NOTIFICATION_VIBRATION_PATTERN + ", " +
                    "cust." + DiscussionCustomization.PREF_MESSAGE_NOTIFICATION_LED_COLOR + " AS cust_" + DiscussionCustomization.PREF_MESSAGE_NOTIFICATION_LED_COLOR + ", " +
                    "cust." + DiscussionCustomization.PREF_USE_CUSTOM_CALL_NOTIFICATION + " AS cust_" + DiscussionCustomization.PREF_USE_CUSTOM_CALL_NOTIFICATION + ", " +
                    "cust." + DiscussionCustomization.PREF_CALL_NOTIFICATION_RINGTONE + " AS cust_" + DiscussionCustomization.PREF_CALL_NOTIFICATION_RINGTONE + ", " +
                    "cust." + DiscussionCustomization.PREF_CALL_NOTIFICATION_VIBRATION_PATTERN + " AS cust_" + DiscussionCustomization.PREF_CALL_NOTIFICATION_VIBRATION_PATTERN + ", " +
                    "cust." + DiscussionCustomization.PREF_CALL_NOTIFICATION_USE_FLASH + " AS cust_" + DiscussionCustomization.PREF_CALL_NOTIFICATION_USE_FLASH + ", " +
                    "cust." + DiscussionCustomization.SHARED_SETTINGS_VERSION + " AS cust_" + DiscussionCustomization.SHARED_SETTINGS_VERSION + ", " +
                    "cust." + DiscussionCustomization.SETTING_EXISTENCE_DURATION + " AS cust_" + DiscussionCustomization.SETTING_EXISTENCE_DURATION + ", " +
                    "cust." + DiscussionCustomization.SETTING_VISIBILITY_DURATION + " AS cust_" + DiscussionCustomization.SETTING_VISIBILITY_DURATION + ", " +
                    "cust." + DiscussionCustomization.SETTING_READ_ONCE + " AS cust_" + DiscussionCustomization.SETTING_READ_ONCE;

    static final String PINNED_ORDER = "disc." + Discussion.PINNED + " = 0 ASC, disc." + Discussion.PINNED + " ASC";

    @Transaction
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " message.*, " +
            PREFIX_DISCUSSION_CUSTOMIZATION_COLUMNS +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN ( SELECT id, " + Message.SENDER_SEQUENCE_NUMBER + ", " +
            Message.JSON_REPLY + ", " + Message.JSON_EXPIRATION + ", " + Message.JSON_RETURN_RECEIPT + ", " +
            Message.JSON_LOCATION + ", " + Message.LOCATION_TYPE + ", " +
            Message.CONTENT_BODY + ", " + Message.TIMESTAMP + ", " +
            Message.STATUS + ", " + Message.WIPE_STATUS + ", " + Message.MESSAGE_TYPE + ", " +
            Message.DISCUSSION_ID + ", " + Message.INBOUND_MESSAGE_ENGINE_IDENTIFIER + ", " +
            Message.SENDER_IDENTIFIER + ", " + Message.SENDER_THREAD_IDENTIFIER + ", " +
            Message.TOTAL_ATTACHMENT_COUNT + ", " + Message.IMAGE_COUNT + ", " +
            Message.WIPED_ATTACHMENT_COUNT + ", " + Message.EDITED + ", " + Message.FORWARDED + ", " +
            Message.REACTIONS + ", " + Message.IMAGE_RESOLUTIONS + ", " + Message.MISSED_MESSAGE_COUNT + ", " +
            Message.EXPIRATION_START_TIMESTAMP + ", " + Message.LIMITED_VISIBILITY + ", " + Message.LINK_PREVIEW_FYLE_ID + ", " + Message.JSON_MENTIONS + ", " + Message.MENTIONED + ", " + Message.BOOKMARKED + ", " +
            " MAX(" + Message.SORT_INDEX + ") AS " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME + " GROUP BY " + Message.DISCUSSION_ID + " ) AS message " +
            " ON message." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS cust " +
            " ON cust." + DiscussionCustomization.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND disc." + Discussion.ARCHIVED + " = :archived " +
            " AND disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " != 0 " +
            " ORDER BY " + PINNED_ORDER + ", disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " DESC" )
    public abstract LiveData<List<SimpleDiscussionAndLastMessage>> getNonDeletedDiscussionAndLastMessages(@NonNull byte[] bytesOwnedIdentity, boolean archived);

    @Transaction
    @Query("SELECT " +
            "disc.id AS disc_id, " +
            " disc." + Discussion.TITLE + " AS disc_" + Discussion.TITLE + ", " +
            " disc." + Discussion.BYTES_OWNED_IDENTITY + " AS disc_" + Discussion.BYTES_OWNED_IDENTITY + ", " +
            " disc." + Discussion.DISCUSSION_TYPE + " AS disc_" + Discussion.DISCUSSION_TYPE + ", " +
            " disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " AS disc_" + Discussion.BYTES_DISCUSSION_IDENTIFIER + ", " +
            " disc." + Discussion.SENDER_THREAD_IDENTIFIER + " AS disc_" + Discussion.SENDER_THREAD_IDENTIFIER + ", " +
            " disc." + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + " AS disc_" + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + ", " +
            " (disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " + 1000000000000 * " + Discussion.PINNED + ") AS disc_" + Discussion.LAST_MESSAGE_TIMESTAMP + ", " +
            " disc." + Discussion.LAST_REMOTE_DELETE_TIMESTAMP + " AS disc_" + Discussion.LAST_REMOTE_DELETE_TIMESTAMP + ", " +
            " disc." + Discussion.PHOTO_URL + " AS disc_" + Discussion.PHOTO_URL + ", " +
            " disc." + Discussion.KEYCLOAK_MANAGED + " AS disc_" + Discussion.KEYCLOAK_MANAGED + ", " +
            " disc." + Discussion.PINNED + " AS disc_" + Discussion.PINNED + ", " +
            " disc." + Discussion.ARCHIVED + " AS disc_" + Discussion.ARCHIVED + ", " +
            " disc." + Discussion.UNREAD + " AS disc_" + Discussion.UNREAD + ", " +
            " disc." + Discussion.ACTIVE + " AS disc_" + Discussion.ACTIVE + ", " +
            " disc." + Discussion.TRUST_LEVEL + " AS disc_" + Discussion.TRUST_LEVEL + ", " +
            " disc." + Discussion.STATUS + " AS disc_" + Discussion.STATUS + ", " +
            " message.*, unread.count AS unread_count, (unreadMention.count != 0) AS unread_mention, 0 AS locations_shared, " +
            PREFIX_DISCUSSION_CUSTOMIZATION_COLUMNS +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN ( SELECT id, " + Message.SENDER_SEQUENCE_NUMBER + ", " +
            Message.JSON_REPLY + ", " + Message.JSON_EXPIRATION + ", " + Message.JSON_RETURN_RECEIPT + ", " +
            Message.JSON_LOCATION + ", " + Message.LOCATION_TYPE + ", " +
            Message.CONTENT_BODY + ", " + Message.TIMESTAMP + ", " +
            Message.STATUS + ", " + Message.WIPE_STATUS + ", " + Message.MESSAGE_TYPE + ", " +
            Message.DISCUSSION_ID + ", " + Message.INBOUND_MESSAGE_ENGINE_IDENTIFIER + ", " +
            Message.SENDER_IDENTIFIER + ", " + Message.SENDER_THREAD_IDENTIFIER + ", " +
            Message.TOTAL_ATTACHMENT_COUNT + ", " + Message.IMAGE_COUNT + ", " +
            Message.WIPED_ATTACHMENT_COUNT + ", " + Message.EDITED + ", " + Message.FORWARDED + ", " +
            Message.REACTIONS + ", " + Message.IMAGE_RESOLUTIONS + ", " + Message.MISSED_MESSAGE_COUNT + ", " +
            Message.EXPIRATION_START_TIMESTAMP + ", " + Message.LIMITED_VISIBILITY + ", " + Message.LINK_PREVIEW_FYLE_ID + ", " + Message.JSON_MENTIONS + ", " + Message.MENTIONED + ", " + Message.BOOKMARKED + ", " +
            " MAX(" + Message.SORT_INDEX + ") AS " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " != " + Message.STATUS_DRAFT + " GROUP BY " + Message.DISCUSSION_ID + " ) AS message " +
            " ON message." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN ( SELECT COUNT(*) AS count, " + Message.DISCUSSION_ID + " FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_UNREAD + " GROUP BY " + Message.DISCUSSION_ID + " ) AS unread " +
            " ON unread." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN ( SELECT COUNT(*) AS count, " + Message.DISCUSSION_ID + " FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_UNREAD + " AND " + Message.MENTIONED + " = 1" + " GROUP BY " + Message.DISCUSSION_ID + " ) AS unreadMention " +
            " ON unreadMention." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS cust " +
            " ON cust." + DiscussionCustomization.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND disc." + Discussion.STATUS + " != " + Discussion.STATUS_PRE_DISCUSSION +
            " ORDER BY " + PINNED_ORDER + ", disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " DESC" )
    public abstract LiveData<List<DiscussionAndLastMessage>> getAllDiscussionsAndLastMessagesForWebClient(@NonNull byte[] bytesOwnedIdentity);


    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " WHERE id = :discussionId")
    @Nullable public abstract Discussion getById(long discussionId);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " WHERE id = :discussionId")
    public abstract LiveData<Discussion> getByIdAsync(long discussionId);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_CONTACT +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesContactIdentity " +
            " AND " + Discussion.STATUS + " = " + Discussion.STATUS_NORMAL)
    @Nullable public abstract Discussion getByContact(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_CONTACT +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesContactIdentity " +
            " AND " + Discussion.STATUS + " = " + Discussion.STATUS_NORMAL)
    public abstract LiveData<Discussion> getByContactLiveData(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_CONTACT +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesContactIdentity ")
    @Nullable public abstract Discussion getByContactWithAnyStatus(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE "  + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes" +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesGroupOwnerAndUid " +
            " AND " + Discussion.STATUS + " IN (" + Discussion.STATUS_NORMAL + "," + Discussion.STATUS_READ_ONLY + ")")
    @Nullable public abstract Discussion getByGroupOwnerAndUid(@NonNull byte[] ownedIdentityBytes, @NonNull byte[] bytesGroupOwnerAndUid);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE "  + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes" +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesGroupOwnerAndUid ")
    @Nullable public abstract Discussion getByGroupOwnerAndUidWithAnyStatus(@NonNull byte[] ownedIdentityBytes, @NonNull byte[] bytesGroupOwnerAndUid);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE "  + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes" +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Discussion.STATUS + " IN (" + Discussion.STATUS_NORMAL + "," + Discussion.STATUS_READ_ONLY + ")")
    @Nullable public abstract Discussion getByGroupIdentifier(@NonNull byte[] ownedIdentityBytes, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE "  + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes" +
            " AND " + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesGroupIdentifier ")
    @Nullable public abstract Discussion getByGroupIdentifierWithAnyStatus(@NonNull byte[] ownedIdentityBytes, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE "  + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes" +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesGroupOwnerAndUidOrIdentifier " +
            " AND " + Discussion.STATUS + " IN (" + Discussion.STATUS_NORMAL + "," + Discussion.STATUS_READ_ONLY + ")")
    @Nullable public abstract Discussion getByGroupOwnerAndUidOrIdentifier(@NonNull byte[] ownedIdentityBytes, @NonNull byte[] bytesGroupOwnerAndUidOrIdentifier);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE "  + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes" +
            " AND " + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesGroupOwnerAndUidOrIdentifier " +
            " AND " + Discussion.STATUS + " IN (" + Discussion.STATUS_NORMAL + "," + Discussion.STATUS_READ_ONLY + ")")
    public abstract LiveData<Discussion> getByGroupOwnerAndUidOrIdentifierLiveData(@NonNull byte[] ownedIdentityBytes, @NonNull byte[] bytesGroupOwnerAndUidOrIdentifier);


    @Query("SELECT " + Discussion.BYTES_OWNED_IDENTITY + " FROM " + Discussion.TABLE_NAME + "  WHERE id = :discussionId")
    @Nullable public abstract byte[] getBytesOwnedIdentityForDiscussionId(long discussionId);

    @Query("SELECT discussion.* FROM " + Discussion.TABLE_NAME + " AS discussion " +
            " INNER JOIN ( SELECT " + Message.DISCUSSION_ID + ", MAX(" + Message.TIMESTAMP + ") AS maxTimestamp FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " != " + Message.STATUS_DRAFT + " AND " +  Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE + " GROUP BY " + Message.DISCUSSION_ID + ") AS message " +
            " ON discussion.id = message." + Message.DISCUSSION_ID +
            " INNER JOIN " + OwnedIdentity.TABLE_NAME + " AS oi " +
            " ON discussion." + Discussion.BYTES_OWNED_IDENTITY + " = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +
            " WHERE discussion." + Discussion.STATUS + " = " + Discussion.STATUS_NORMAL +
            " AND oi." + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " GROUP BY discussion.id " +
            " ORDER BY maxTimestamp DESC")
    public abstract LiveData<List<Discussion>> getLatestDiscussionsInWhichYouWrote();

    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " COALESCE(grp." + Group.GROUP_MEMBERS_NAMES + ", grpp." + Group2.GROUP_MEMBERS_NAMES + ") AS groupMemberNames, " +
            " COALESCE(grp." + Group.FULL_SEARCH_FIELD + ", grpp." + Group2.FULL_SEARCH_FIELD + ") AS patterMatchingField " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + Group.TABLE_NAME + " AS grp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grp." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grp." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " LEFT JOIN " + Group2.TABLE_NAME + " AS grpp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grpp." + Group2.BYTES_GROUP_IDENTIFIER +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grpp." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " WHERE disc.id = :discussionId ")
    public abstract LiveData<DiscussionAndGroupMembersNames> getWithGroupMembersNames(long discussionId);


    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", CASE disc." + Discussion.DISCUSSION_TYPE +
            " WHEN " + Discussion.TYPE_GROUP_V2 + " THEN ( SELECT count(*) FROM " +
            " (SELECT 1 FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = disc." + Discussion.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER +
            " UNION ALL " +
            " SELECT 1 FROM " + Group2PendingMember.TABLE_NAME + " AS gpm " +
            " WHERE gpm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = disc." + Discussion.BYTES_OWNED_IDENTITY +
            " AND gpm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER +
            " )) " +
            " WHEN " + Discussion.TYPE_GROUP + " THEN " +
            " ( SELECT count(*) FROM " + ContactGroupJoin.TABLE_NAME + " AS cgj " +
            " WHERE cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = disc." + Discussion.BYTES_OWNED_IDENTITY +
            " AND cgj." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER +
            " ) " +
            " ELSE -1 END AS count, " + // return -1 for one to one discussions
            " CASE disc." + Discussion.DISCUSSION_TYPE +
            " WHEN " + Discussion.TYPE_GROUP_V2 + " THEN ( " +
            " SELECT " + Group2.UPDATE_IN_PROGRESS + " FROM " + Group2.TABLE_NAME + " AS g " +
            " WHERE g." + Group2.BYTES_OWNED_IDENTITY + " = disc." + Discussion.BYTES_OWNED_IDENTITY +
            " AND g." + Group2.BYTES_GROUP_IDENTIFIER + " = disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER +
            " ) " +
            " ELSE 0 END AS updating " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " WHERE disc.id = :discussionId ")
    public abstract LiveData<DiscussionAndGroupMembersCount> getWithGroupMembersCount(long discussionId);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH) // the columns is_group and status are used for sorting only
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " COALESCE(grp." + Group.GROUP_MEMBERS_NAMES + ", grpp." + Group2.GROUP_MEMBERS_NAMES + ") AS groupMemberNames, " +
            " COALESCE(grp." + Group.FULL_SEARCH_FIELD + ", grpp." + Group2.FULL_SEARCH_FIELD + ") AS patterMatchingField, " +
            " CASE WHEN disc." + Discussion.DISCUSSION_TYPE + " != " + Discussion.TYPE_CONTACT + " THEN 1 ELSE 0 END AS is_group, " +
            " CASE disc." + Discussion.STATUS +
              " WHEN " + Discussion.STATUS_NORMAL + " THEN 0 " +
              " WHEN " + Discussion.STATUS_READ_ONLY + " THEN 0 " +
              " WHEN " + Discussion.STATUS_PRE_DISCUSSION + " THEN 1 " +
              " ELSE 2 " +
            " END AS status " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + Group.TABLE_NAME + " AS grp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grp." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grp." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " LEFT JOIN " + Group2.TABLE_NAME + " AS grpp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grpp." + Group2.BYTES_GROUP_IDENTIFIER +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grpp." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " ORDER BY status, is_group, disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract LiveData<List<DiscussionAndGroupMembersNames>> getAllWithGroupMembersNames(@NonNull byte[] ownedIdentityBytes);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH) // the column status is used for sorting only
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " COALESCE(grp." + Group.GROUP_MEMBERS_NAMES + ", grpp." + Group2.GROUP_MEMBERS_NAMES + ") AS groupMemberNames, " +
            " COALESCE(grp." + Group.FULL_SEARCH_FIELD + ", grpp." + Group2.FULL_SEARCH_FIELD + ") AS patterMatchingField, " +
            " CASE disc." + Discussion.STATUS +
            " WHEN " + Discussion.STATUS_NORMAL + " THEN 0 " +
            " WHEN " + Discussion.STATUS_READ_ONLY + " THEN 0 " +
            " ELSE 1 " +
            " END AS status " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + Group.TABLE_NAME + " AS grp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grp." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grp." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " LEFT JOIN " + Group2.TABLE_NAME + " AS grpp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grpp." + Group2.BYTES_GROUP_IDENTIFIER +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grpp." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " ORDER BY status DESC, disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract List<DiscussionAndGroupMembersNames> getAllForGlobalSearch(@NonNull byte[] ownedIdentityBytes);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH) // the columns is_group and status are used for sorting only
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " COALESCE(grp." + Group.GROUP_MEMBERS_NAMES + ", grpp." + Group2.GROUP_MEMBERS_NAMES + ") AS groupMemberNames, " +
            " COALESCE(grp." + Group.FULL_SEARCH_FIELD + ", grpp." + Group2.FULL_SEARCH_FIELD + ") AS patterMatchingField, " +
            " CASE WHEN disc." + Discussion.DISCUSSION_TYPE + " != " + Discussion.TYPE_CONTACT + " THEN 1 ELSE 0 END AS is_group, " +
            " CASE disc." + Discussion.STATUS +
              " WHEN " + Discussion.STATUS_NORMAL + " THEN 0 " +
              " WHEN " + Discussion.STATUS_READ_ONLY + " THEN 0 " +
              " WHEN " + Discussion.STATUS_PRE_DISCUSSION + " THEN 1 " +
              " ELSE 2 " +
            " END AS status " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + Group.TABLE_NAME + " AS grp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grp." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grp." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " LEFT JOIN " + Group2.TABLE_NAME + " AS grpp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grpp." + Group2.BYTES_GROUP_IDENTIFIER +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grpp." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " ORDER BY " + PINNED_ORDER + ", status, is_group, disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract LiveData<List<DiscussionAndGroupMembersNames>> getAllPinnedFirstWithGroupMembersNames(@NonNull byte[] ownedIdentityBytes);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH) // the column is_group is used for sorting only
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " COALESCE(grp." + Group.GROUP_MEMBERS_NAMES + ", grpp." + Group2.GROUP_MEMBERS_NAMES + ") AS groupMemberNames, " +
            " COALESCE(grp." + Group.FULL_SEARCH_FIELD + ", grpp." + Group2.FULL_SEARCH_FIELD + ") AS patterMatchingField, " +
            " CASE WHEN disc." + Discussion.DISCUSSION_TYPE + " != " + Discussion.TYPE_CONTACT + " THEN 1 ELSE 0 END AS is_group " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + Group.TABLE_NAME + " AS grp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grp." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grp." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " LEFT JOIN " + Group2.TABLE_NAME + " AS grpp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grpp." + Group2.BYTES_GROUP_IDENTIFIER +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grpp." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND disc." + Discussion.STATUS + " = " + Discussion.STATUS_NORMAL +
            " ORDER BY " + PINNED_ORDER + ", is_group, disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract LiveData<List<DiscussionAndGroupMembersNames>> getAllWritableWithGroupMembersNames(@NonNull byte[] ownedIdentityBytes);

    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " COALESCE(grp." + Group.GROUP_MEMBERS_NAMES + ", grpp." + Group2.GROUP_MEMBERS_NAMES + ") AS groupMemberNames, " +
            " COALESCE(grp." + Group.FULL_SEARCH_FIELD + ", grpp." + Group2.FULL_SEARCH_FIELD + ") AS patterMatchingField " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + Group.TABLE_NAME + " AS grp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grp." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grp." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " LEFT JOIN " + Group2.TABLE_NAME + " AS grpp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grpp." + Group2.BYTES_GROUP_IDENTIFIER +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grpp." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND disc." + Discussion.STATUS + " = " + Discussion.STATUS_NORMAL +
            " ORDER BY " + PINNED_ORDER + ", disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " DESC")
    public abstract LiveData<List<DiscussionAndGroupMembersNames>> getAllWritableWithGroupMembersNamesOrderedByActivity(@NonNull byte[] ownedIdentityBytes);

    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " COALESCE(grp." + Group.GROUP_MEMBERS_NAMES + ", grpp." + Group2.GROUP_MEMBERS_NAMES + ") AS groupMemberNames, " +
            " COALESCE(grp." + Group.FULL_SEARCH_FIELD + ", grpp." + Group2.FULL_SEARCH_FIELD + ") AS patterMatchingField " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " INNER JOIN ( SELECT " + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " AS gid, " + Discussion.TYPE_GROUP + " AS dt FROM " + ContactGroupJoin.TABLE_NAME +
            " WHERE " + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " UNION " +
            " SELECT " + Group2Member.BYTES_GROUP_IDENTIFIER + " AS gid, " + Discussion.TYPE_GROUP_V2 + " AS dt FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " UNION " +
            " SELECT " + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " AS gid, " + Discussion.TYPE_GROUP_V2 + " AS dt FROM " + Group2PendingMember.TABLE_NAME +
            " WHERE " + Group2PendingMember.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ) AS jn" +
            " ON jn.gid = disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER +
            " AND jn.dt = disc." + Discussion.DISCUSSION_TYPE +
            " LEFT JOIN " + Group.TABLE_NAME + " AS grp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grp." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grp." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " LEFT JOIN " + Group2.TABLE_NAME + " AS grpp " +
            " ON disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = grpp." + Group2.BYTES_GROUP_IDENTIFIER +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = grpp." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " WHERE disc." + Discussion.STATUS + " IN (" + Discussion.STATUS_NORMAL + "," + Discussion.STATUS_READ_ONLY + ")" +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract LiveData<List<DiscussionAndGroupMembersNames>> getContactNotLockedGroupDiscussionsWithGroupMembersNames(@NonNull byte[] bytesContactIdentity, @NonNull byte[] bytesOwnedIdentity);


    @Query("DELETE FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.STATUS + " = " + Discussion.STATUS_LOCKED +
            " AND NOT EXISTS (SELECT 1 FROM " + Message.TABLE_NAME + " WHERE " + Message.DISCUSSION_ID + " = " + Discussion.TABLE_NAME + ".id)")
    public abstract void deleteEmptyLockedDiscussions();

    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS cust " +
            " ON disc.id = cust." + DiscussionCustomization.DISCUSSION_ID)
    public abstract List<DiscussionAndCustomization> getAllDiscussionAndCustomizations();

    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS cust " +
            " ON disc.id = cust." + DiscussionCustomization.DISCUSSION_ID +
            " WHERE disc.id = :discussionId")
    public abstract List<DiscussionAndCustomization> getDiscussionAndCustomization(long discussionId);

    @Query("SELECT " + Discussion.PHOTO_URL + " FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.PHOTO_URL + " IS NOT NULL " +
            " AND " + Discussion.STATUS + " = " + Discussion.STATUS_LOCKED)
    public abstract List<String> getAllLockedDiscussionPhotoUrls();

    @Query("SELECT id FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ")
    public abstract List<Long> getAllDiscussionIdsForOwnedIdentity(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.STATUS + " = " + Discussion.STATUS_PRE_DISCUSSION)
    public abstract List<Discussion> getAllPreDiscussions();

    @Query("SELECT * FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.PINNED + " != 0 " +
            " AND " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY " + Discussion.PINNED)
    public abstract List<Discussion> getAllPinned(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT MAX(" + Discussion.PINNED + ") FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ")
    public abstract int getMaxPinnedIndex(@NonNull byte[] bytesOwnedIdentity);

    public static class DiscussionAndLastMessage {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        @Embedded
        @Nullable
        public Message message;

        @Embedded(prefix = "cust_")
        @Nullable
        public DiscussionCustomization discussionCustomization;

        @ColumnInfo(name = "unread_count")
        public int unreadCount;

        @ColumnInfo(name = "unread_mention")
        public boolean unreadMention;

        @ColumnInfo(name = "locations_shared")
        public boolean locationsShared;
    }

    public static class SimpleDiscussionAndLastMessage {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        @Embedded
        @Nullable
        public Message message;

        @Embedded(prefix = "cust_")
        @Nullable
        public DiscussionCustomization discussionCustomization;
    }

    public static class DiscussionAndGroupMembersNames {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        @Nullable
        public String groupMemberNames;

        @Nullable
        public String patterMatchingField;
    }

    public static class DiscussionAndGroupMembersCount {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        public int count;
        public int updating;
    }

    public static class DiscussionAndCustomization {
        @Embedded
        public Discussion discussion;

        @Embedded
        public DiscussionCustomization discussionCustomization;
    }
}
