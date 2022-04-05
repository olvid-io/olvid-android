/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RoomWarnings;
import androidx.room.Transaction;

import java.util.List;

import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;

@Dao
public abstract class DiscussionDao {
    @Insert
    public abstract long insert(Discussion discussion);

    @Delete
    public abstract void delete(Discussion discussion);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.KEYCLOAK_MANAGED + " = :keycloakManaged " +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    public abstract void updateKeycloakManaged(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, boolean keycloakManaged);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.ACTIVE + " = :active " +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    public abstract void updateActive(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, boolean active);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.TRUST_LEVEL + " = :trustLevel " +
            " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Discussion.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    public abstract void updateTrustLevel(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, int trustLevel);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.LAST_MESSAGE_TIMESTAMP + " = :lastMessageTimestamp " +
            " WHERE id = :discussionId ")
    public abstract void updateLastMessageTimestamp(long discussionId, long lastMessageTimestamp);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + " = :lastOutboundMessageSequenceNumber " +
            " WHERE id = :discussionId ")
    public abstract void updateLastOutboundMessageSequenceNumber(long discussionId, long lastOutboundMessageSequenceNumber);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.TITLE + " = :title, " +
            Discussion.PHOTO_URL + " = :photoUrl " +
            " WHERE id = :discussionId ")
    public abstract void updateTitleAndPhotoUrl(long discussionId, String title, String photoUrl);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.BYTES_CONTACT_IDENTITY + " = NULL, " +
            Discussion.BYTES_GROUP_OWNER_AND_UID + " = NULL, " +
            Discussion.KEYCLOAK_MANAGED + " = 0 " +
            " WHERE id = :discussionId ")
    public abstract void updateAsLocked(long discussionId);

    @Query("UPDATE " + Discussion.TABLE_NAME +
            " SET " + Discussion.UNREAD + " = :unread " +
            " WHERE id = :discussionId ")
    public abstract void updateDiscussionUnreadStatus(long discussionId, boolean unread);


    static final String PREFIX_DISCUSSION_COLUMNS = "disc.id AS disc_id, " +
            " disc." + Discussion.TITLE + " AS disc_" + Discussion.TITLE + ", " +
            " disc." + Discussion.BYTES_OWNED_IDENTITY + " AS disc_" + Discussion.BYTES_OWNED_IDENTITY + ", " +
            " disc." + Discussion.SENDER_THREAD_IDENTIFIER + " AS disc_" + Discussion.SENDER_THREAD_IDENTIFIER + ", " +
            " disc." + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + " AS disc_" + Discussion.LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER + ", " +
            " disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " AS disc_" + Discussion.LAST_MESSAGE_TIMESTAMP + ", " +
            " disc." + Discussion.BYTES_CONTACT_IDENTITY + " AS disc_" + Discussion.BYTES_CONTACT_IDENTITY + ", " +
            " disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " AS disc_" + Discussion.BYTES_GROUP_OWNER_AND_UID + ", " +
            " disc." + Discussion.PHOTO_URL + " AS disc_" + Discussion.PHOTO_URL + ", " +
            " disc." + Discussion.KEYCLOAK_MANAGED + " AS disc_" + Discussion.KEYCLOAK_MANAGED + ", " +
            " disc." + Discussion.UNREAD + " AS disc_" + Discussion.UNREAD + ", " +
            " disc." + Discussion.ACTIVE + " AS disc_" + Discussion.ACTIVE + ", " +
            " disc." + Discussion.TRUST_LEVEL + " AS disc_" + Discussion.TRUST_LEVEL;

    private static final String PREFIX_DISCUSSION_CUSTOMIZATION_COLUMNS =
            "cust." + DiscussionCustomization.DISCUSSION_ID + " AS cust_" + DiscussionCustomization.DISCUSSION_ID + ", " +
                    "cust." + DiscussionCustomization.SERIALIZED_COLOR_JSON + " AS cust_" + DiscussionCustomization.SERIALIZED_COLOR_JSON + ", " +
                    "cust." + DiscussionCustomization.BACKGROUND_IMAGE_URL + " AS cust_" + DiscussionCustomization.BACKGROUND_IMAGE_URL + ", " +
                    "cust." + DiscussionCustomization.PREF_SEND_READ_RECEIPT + " AS cust_" + DiscussionCustomization.PREF_SEND_READ_RECEIPT + ", " +
                    "cust." + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS + " AS cust_" + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS + ", " +
                    "cust." + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " AS cust_" + DiscussionCustomization.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + ", " +
                    "cust." + DiscussionCustomization.PREF_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_MESSAGES + " AS cust_" + DiscussionCustomization.PREF_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_MESSAGES + ", " +
                    "cust." + DiscussionCustomization.PREF_RETAIN_WIPED_OUTBOUND_MESSAGES + " AS cust_" + DiscussionCustomization.PREF_RETAIN_WIPED_OUTBOUND_MESSAGES + ", " +
                    "cust." + DiscussionCustomization.PREF_DISCUSSION_RETENTION_COUNT + " AS cust_" + DiscussionCustomization.PREF_DISCUSSION_RETENTION_COUNT + ", " +
                    "cust." + DiscussionCustomization.PREF_DISCUSSION_RETENTION_DURATION + " AS cust_" + DiscussionCustomization.PREF_DISCUSSION_RETENTION_DURATION + ", " +
                    "cust." + DiscussionCustomization.SHARED_SETTINGS_VERSION + " AS cust_" + DiscussionCustomization.SHARED_SETTINGS_VERSION + ", " +
                    "cust." + DiscussionCustomization.SETTING_EXISTENCE_DURATION + " AS cust_" + DiscussionCustomization.SETTING_EXISTENCE_DURATION + ", " +
                    "cust." + DiscussionCustomization.SETTING_VISIBILITY_DURATION + " AS cust_" + DiscussionCustomization.SETTING_VISIBILITY_DURATION + ", " +
                    "cust." + DiscussionCustomization.SETTING_READ_ONCE + " AS cust_" + DiscussionCustomization.SETTING_READ_ONCE;

    @Transaction
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " message.*, unread.count AS unread_count, " +
            PREFIX_DISCUSSION_CUSTOMIZATION_COLUMNS +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN ( SELECT id, " + Message.SENDER_SEQUENCE_NUMBER + ", " +
            Message.JSON_REPLY + ", " + Message.JSON_EXPIRATION + ", " + Message.JSON_RETURN_RECEIPT + ", " +
            Message.CONTENT_BODY + ", " + Message.TIMESTAMP + ", " +
            Message.STATUS + ", " + Message.WIPE_STATUS + ", " + Message.MESSAGE_TYPE + ", " +
            Message.DISCUSSION_ID + ", " + Message.ENGINE_MESSAGE_IDENTIFIER + ", " +
            Message.SENDER_IDENTIFIER + ", " + Message.SENDER_THREAD_IDENTIFIER + ", " +
            Message.TOTAL_ATTACHMENT_COUNT + ", " + Message.IMAGE_COUNT + ", " +
            Message.WIPED_ATTACHMENT_COUNT + ", " + Message.EDITED + ", " + Message.FORWARDED + ", " +
            Message.REACTIONS + ", " + Message.IMAGE_RESOLUTIONS + ", " + Message.MISSED_MESSAGE_COUNT + ", " +
            " MAX(" + Message.SORT_INDEX + ") AS " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME + " GROUP BY " + Message.DISCUSSION_ID + " ) AS message " +
            " ON message." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN ( SELECT COUNT(*) AS count, " + Message.DISCUSSION_ID + " FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_UNREAD + " GROUP BY " + Message.DISCUSSION_ID + " ) AS unread " +
            " ON unread." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS cust " +
            " ON cust." + DiscussionCustomization.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " != 0 " +
            " ORDER BY disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " DESC"
    )
    public abstract LiveData<List<DiscussionAndLastMessage>> getNonDeletedDiscussionAndLastMessages(byte[] bytesOwnedIdentity);

    @Transaction
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " message.*, unread.count AS unread_count, " +
            PREFIX_DISCUSSION_CUSTOMIZATION_COLUMNS +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN ( SELECT id, " + Message.SENDER_SEQUENCE_NUMBER + ", " +
            Message.JSON_REPLY + ", " + Message.JSON_EXPIRATION + ", " + Message.JSON_RETURN_RECEIPT + ", " +
            Message.CONTENT_BODY + ", " + Message.TIMESTAMP + ", " +
            Message.STATUS + ", " + Message.WIPE_STATUS + ", " + Message.MESSAGE_TYPE + ", " +
            Message.DISCUSSION_ID + ", " + Message.ENGINE_MESSAGE_IDENTIFIER + ", " +
            Message.SENDER_IDENTIFIER + ", " + Message.SENDER_THREAD_IDENTIFIER + ", " +
            Message.TOTAL_ATTACHMENT_COUNT + ", " + Message.IMAGE_COUNT + ", " +
            Message.WIPED_ATTACHMENT_COUNT + ", " + Message.EDITED + ", " + Message.FORWARDED + ", " +
            Message.REACTIONS + ", " + Message.IMAGE_RESOLUTIONS + ", " + Message.MISSED_MESSAGE_COUNT + ", " +
            " MAX(" + Message.SORT_INDEX + ") AS " + Message.SORT_INDEX + " FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " != " + Message.STATUS_DRAFT + " GROUP BY " + Message.DISCUSSION_ID + " ) AS message " +
            " ON message." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN ( SELECT COUNT(*) AS count, " + Message.DISCUSSION_ID + " FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " = " + Message.STATUS_UNREAD + " GROUP BY " + Message.DISCUSSION_ID + " ) AS unread " +
            " ON unread." + Message.DISCUSSION_ID + " = disc.id " +
            " LEFT JOIN " + DiscussionCustomization.TABLE_NAME + " AS cust " +
            " ON cust." + DiscussionCustomization.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY disc." + Discussion.LAST_MESSAGE_TIMESTAMP + " DESC"
    )
    public abstract LiveData<List<DiscussionAndLastMessage>> getAllDiscussionsAndLastMessages(byte[] bytesOwnedIdentity);


    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " WHERE id = :discussionId")
    public abstract Discussion getById(long discussionId);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " WHERE id = :discussionId")
    public abstract LiveData<Discussion> getByIdAsync(long discussionId);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " WHERE " + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " + Discussion.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    public abstract Discussion getByContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Discussion.TABLE_NAME + " WHERE " + Discussion.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid AND " + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes")
    public abstract Discussion getByGroupOwnerAndUid(byte[] ownedIdentityBytes, byte[] bytesGroupOwnerAndUid);

    @Query("SELECT " + Discussion.BYTES_OWNED_IDENTITY + " FROM " + Discussion.TABLE_NAME + "  WHERE id = :discussionId")
    public abstract byte[] getBytesOwnedIdentityForDiscussionId(long discussionId);

    @Query("SELECT discussion.* FROM " + Discussion.TABLE_NAME + " AS discussion " +
            " INNER JOIN ( SELECT " + Message.DISCUSSION_ID + ", MAX(" + Message.TIMESTAMP + ") AS maxTimestamp FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " != " + Message.STATUS_DRAFT + " AND " +  Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE + " GROUP BY " + Message.DISCUSSION_ID + ") AS message " +
            " ON discussion.id = message." + Message.DISCUSSION_ID +
            " INNER JOIN " + OwnedIdentity.TABLE_NAME + " AS oi " +
            " ON discussion." + Discussion.BYTES_OWNED_IDENTITY + " = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +
            " WHERE (discussion." + Discussion.BYTES_GROUP_OWNER_AND_UID + " NOT NULL OR " +
            " discussion." + Discussion.BYTES_CONTACT_IDENTITY + " NOT NULL) " +
            " AND oi." + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " GROUP BY discussion.id " +
            " ORDER BY maxTimestamp DESC")
    public abstract LiveData<List<Discussion>> getLatestDiscussionsInWhichYouWrote();

    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            "group_concat(CASE WHEN group_contact." + Contact.CUSTOM_DISPLAY_NAME + " IS NULL THEN group_contact." + Contact.DISPLAY_NAME + " ELSE group_contact." + Contact.CUSTOM_DISPLAY_NAME + " END, :joiner) AS groupContactDisplayNames FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + ContactGroupJoin.TABLE_NAME + " AS group_join " +
            " ON disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = group_join." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS group_contact " +
            " ON group_contact." + Contact.BYTES_CONTACT_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND group_contact." + Contact.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " WHERE disc.id = :discussionId ")
    public abstract LiveData<DiscussionAndContactDisplayNames> getWithContactNames(long discussionId, String joiner);


    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH) // the column is_group is used for sorting only
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            "group_concat(CASE WHEN group_contact." + Contact.CUSTOM_DISPLAY_NAME + " IS NULL THEN group_contact." + Contact.DISPLAY_NAME + " ELSE group_contact." + Contact.CUSTOM_DISPLAY_NAME + " END, :joiner) AS groupContactDisplayNames, CASE WHEN disc.bytes_contact_identity IS NULL THEN 1 ELSE 0 END AS is_group  FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + ContactGroupJoin.TABLE_NAME + " AS group_join " +
            " ON disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = group_join." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS group_contact " +
            " ON group_contact." + Contact.BYTES_CONTACT_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND group_contact." + Contact.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " GROUP BY disc.id" +
            " ORDER BY is_group, disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract LiveData<List<DiscussionAndContactDisplayNames>> getAllWithContactNames(byte[] ownedIdentityBytes, String joiner);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH) // the column is_group is used for sorting only
    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            "group_concat(CASE WHEN group_contact." + Contact.CUSTOM_DISPLAY_NAME + " IS NULL THEN group_contact." + Contact.DISPLAY_NAME + " ELSE group_contact." + Contact.CUSTOM_DISPLAY_NAME + " END, :joiner) AS groupContactDisplayNames, CASE WHEN disc.bytes_contact_identity IS NULL THEN 1 ELSE 0 END AS is_group  FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + ContactGroupJoin.TABLE_NAME + " AS group_join " +
            " ON disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = group_join." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS group_contact " +
            " ON group_contact." + Contact.BYTES_CONTACT_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND group_contact." + Contact.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND (disc." + Discussion.BYTES_CONTACT_IDENTITY + " IS NOT NULL " +
            " OR disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " IS NOT NULL) " +
            " GROUP BY disc.id" +
            " ORDER BY is_group, disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract LiveData<List<DiscussionAndContactDisplayNames>> getAllNotLockedWithContactNames(byte[] ownedIdentityBytes, String joiner);

    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            "group_concat(CASE WHEN group_contact." + Contact.CUSTOM_DISPLAY_NAME + " IS NULL THEN group_contact." + Contact.DISPLAY_NAME + " ELSE group_contact." + Contact.CUSTOM_DISPLAY_NAME + " END, :joiner) AS groupContactDisplayNames FROM " + Discussion.TABLE_NAME + " AS disc " +
            " LEFT JOIN " + ContactGroupJoin.TABLE_NAME + " AS group_join " +
            " ON disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = group_join." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS group_contact " +
            " ON group_contact." + Contact.BYTES_CONTACT_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND group_contact." + Contact.BYTES_OWNED_IDENTITY + " = group_join." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " LEFT JOIN ( SELECT " + Message.DISCUSSION_ID + ", MAX(" + Message.TIMESTAMP + ") AS maxTimestamp FROM " + Message.TABLE_NAME + " WHERE " + Message.STATUS + " != " + Message.STATUS_DRAFT + " AND " + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE + " GROUP BY " + Message.DISCUSSION_ID + ") AS message " +
            " ON disc.id = message." + Message.DISCUSSION_ID +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND (disc." + Discussion.BYTES_CONTACT_IDENTITY + " IS NOT NULL " +
            " OR disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " IS NOT NULL) " +
            " GROUP BY disc.id " +
            " ORDER BY message.maxTimestamp DESC")
    public abstract LiveData<List<DiscussionAndContactDisplayNames>> getAllActiveWithContactNamesOrderedByActivity(byte[] ownedIdentityBytes, String joiner);

    @Query("SELECT " + PREFIX_DISCUSSION_COLUMNS + ", " +
            " group_concat(CASE WHEN contact." + Contact.CUSTOM_DISPLAY_NAME + " IS NULL THEN contact." + Contact.DISPLAY_NAME + " ELSE contact." + Contact.CUSTOM_DISPLAY_NAME + " END, :joiner) AS groupContactDisplayNames " +
            " FROM " + Discussion.TABLE_NAME + " AS disc " +
            " INNER JOIN ( SELECT * FROM " + ContactGroupJoin.TABLE_NAME + " WHERE " + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity AND " + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ) AS groop_one " +
            " ON disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = groop_one." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = groop_one." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS groop " +
            " ON disc." + Discussion.BYTES_GROUP_OWNER_AND_UID + " = groop." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = groop." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " INNER JOIN " + Contact.TABLE_NAME + " AS contact " +
            " ON contact." + Contact.BYTES_CONTACT_IDENTITY + " = groop." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND contact." + Contact.BYTES_OWNED_IDENTITY + " = groop." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " GROUP BY disc.id " +
            " ORDER BY disc." + Discussion.TITLE + " COLLATE NOCASE ASC")
    public abstract LiveData<List<DiscussionAndContactDisplayNames>> getContactActiveGroupDiscussionsWithContactNames(byte[] bytesContactIdentity, byte[] bytesOwnedIdentity, String joiner);


    @Query("DELETE FROM " + Discussion.TABLE_NAME +
            " WHERE " + Discussion.BYTES_CONTACT_IDENTITY + " IS NULL " +
            " AND " + Discussion.BYTES_GROUP_OWNER_AND_UID + " IS NULL " +
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
            " AND " + Discussion.BYTES_CONTACT_IDENTITY + " IS NULL " +
            " AND " + Discussion.BYTES_GROUP_OWNER_AND_UID + " IS NULL")
    public abstract List<String> getAllLockedDiscussionPhotoUrls();

    public static class DiscussionAndLastMessage {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        @Embedded
        public Message message;

        @Embedded(prefix = "cust_")
        public DiscussionCustomization discussionCustomization;

        @ColumnInfo(name = "unread_count")
        public int unreadCount;
    }

    public static class DiscussionAndContactDisplayNames {
        @Embedded(prefix = "disc_")
        public Discussion discussion;

        public String groupContactDisplayNames;
    }

    public static class DiscussionAndCustomization {
        @Embedded
        public Discussion discussion;

        @Embedded
        public DiscussionCustomization discussionCustomization;
    }
}
