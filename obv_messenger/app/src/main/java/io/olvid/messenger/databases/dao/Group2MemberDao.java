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
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;

@Dao
public interface Group2MemberDao {
    @Insert
    void insert(@NonNull Group2Member groupMember);

    @Delete
    void delete(@NonNull Group2Member groupMember);

    @Update
    void update(@NonNull Group2Member groupMember);

    @Query("UPDATE " + Group2Member.TABLE_NAME +
            " SET " + Group2Member.PENDING_CREATION_TIMESTAMP + " = NULL " +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity ")
    void clearPendingCreationTimestamp(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity ")
    @Nullable Group2Member get(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT EXISTS (SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity )")
    boolean isGroupMember(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity);


    @Query("SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier")
    List<Group2Member> getGroupMembers(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT c.* FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier")
    List<Contact> getGroupMemberContactsSync(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT c.* FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier")
    LiveData<List<Contact>> getGroupMemberContacts(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM ( " +
            " SELECT c.*, gm." + Group2Member.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " c." + Contact.SORT_DISPLAY_NAME + " AS sortDisplayName, " +
            " c." + Contact.IDENTITY_DETAILS + " AS identityDetails, " +
            " gm." + Group2Member.PERMISSION_ADMIN + " AS permissionAdmin, " +
            " gm." + Group2Member.PERMISSION_SEND_MESSAGE + " AS permissionSendMessage, " +
            " gm." + Group2Member.PERMISSION_REMOTE_DELETE_ANYTHING + " AS permissionRemoteDeleteAnything, " +
            " gm." + Group2Member.PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES + " AS permissionEditOrRemoteDeleteOwnMessages, " +
            " gm." + Group2Member.PERMISSION_CHANGE_SETTINGS + " AS permissionChangeSettings, " +
            " 0 as pending " +
            " FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " UNION " +
            " SELECT c.*, pm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " COALESCE(c." + Contact.SORT_DISPLAY_NAME + ", pm." + Group2PendingMember.SORT_DISPLAY_NAME + " ) AS sortDisplayName, " +
            " COALESCE(c." + Contact.IDENTITY_DETAILS + ", pm." + Group2PendingMember.IDENTITY_DETAILS + " ) AS identityDetails, " +
            " pm." + Group2PendingMember.PERMISSION_ADMIN + " AS permissionAdmin, " +
            " pm." + Group2PendingMember.PERMISSION_SEND_MESSAGE + " AS permissionSendMessage, " +
            " pm." + Group2Member.PERMISSION_REMOTE_DELETE_ANYTHING + " AS permissionRemoteDeleteAnything, " +
            " pm." + Group2Member.PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES + " AS permissionEditOrRemoteDeleteOwnMessages, " +
            " pm." + Group2Member.PERMISSION_CHANGE_SETTINGS + " AS permissionChangeSettings, " +
            " 1 as pending " +
            " FROM " + Group2PendingMember.TABLE_NAME + " AS pm " +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON pm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND pm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE pm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND pm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " ) ORDER BY sortDisplayName ASC")
    LiveData<List<Group2MemberOrPending>> getGroupMembersAndPending(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM ( " +
            " SELECT c.*, gm." + Group2Member.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " c." + Contact.SORT_DISPLAY_NAME + " AS sortDisplayName, " +
            " c." + Contact.IDENTITY_DETAILS + " AS identityDetails, " +
            " c." + Contact.FULL_SEARCH_DISPLAY_NAME + " AS fullSearchDisplayName " +
            " FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " UNION " +
            " SELECT c.*, pm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " COALESCE(c." + Contact.SORT_DISPLAY_NAME + ", pm." + Group2PendingMember.SORT_DISPLAY_NAME + " ) AS sortDisplayName, " +
            " COALESCE(c." + Contact.IDENTITY_DETAILS + ", pm." + Group2PendingMember.IDENTITY_DETAILS + " ) AS identityDetails, " +
            " COALESCE(c." + Contact.FULL_SEARCH_DISPLAY_NAME + ", pm." + Group2PendingMember.FULL_SEARCH_DISPLAY_NAME + " ) AS fullSearchDisplayName " +
            " FROM " + Group2PendingMember.TABLE_NAME + " AS pm " +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON pm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND pm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE pm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND pm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " ) ORDER BY sortDisplayName ASC")
    LiveData<List<Group2MemberOrPendingForMention>> getGroupMembersAndPendingForMention(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM ( " +
            " SELECT c.*, gm." + Group2Member.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " c." + Contact.SORT_DISPLAY_NAME + " AS sortDisplayName, " +
            " c." + Contact.IDENTITY_DETAILS + " AS identityDetails, " +
            " gm." + Group2Member.PERMISSION_ADMIN + " AS permissionAdmin, " +
            " gm." + Group2Member.PERMISSION_SEND_MESSAGE + " AS permissionSendMessage, " +
            " gm." + Group2Member.PERMISSION_REMOTE_DELETE_ANYTHING + " AS permissionRemoteDeleteAnything, " +
            " gm." + Group2Member.PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES + " AS permissionEditOrRemoteDeleteOwnMessages, " +
            " gm." + Group2Member.PERMISSION_CHANGE_SETTINGS + " AS permissionChangeSettings, " +
            " 0 as pending " +
            " FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " UNION " +
            " SELECT c.*, pm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " COALESCE(c." + Contact.SORT_DISPLAY_NAME + ", pm." + Group2PendingMember.SORT_DISPLAY_NAME + " ) AS sortDisplayName, " +
            " COALESCE(c." + Contact.IDENTITY_DETAILS + ", pm." + Group2PendingMember.IDENTITY_DETAILS + " ) AS identityDetails, " +
            " pm." + Group2PendingMember.PERMISSION_ADMIN + " AS permissionAdmin, " +
            " pm." + Group2PendingMember.PERMISSION_SEND_MESSAGE + " AS permissionSendMessage, " +
            " pm." + Group2Member.PERMISSION_REMOTE_DELETE_ANYTHING + " AS permissionRemoteDeleteAnything, " +
            " pm." + Group2Member.PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES + " AS permissionEditOrRemoteDeleteOwnMessages, " +
            " pm." + Group2Member.PERMISSION_CHANGE_SETTINGS + " AS permissionChangeSettings, " +
            " 1 as pending " +
            " FROM " + Group2PendingMember.TABLE_NAME + " AS pm " +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON pm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND pm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE pm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND pm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " ) ORDER BY sortDisplayName ASC")
    List<Group2MemberOrPending> getGroupMembersAndPendingSync(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT disc.id FROM " + Discussion.TABLE_NAME + " AS disc " +
            " INNER JOIN " + Group2.TABLE_NAME + " AS g " +
            " ON disc." + Discussion.BYTES_OWNED_IDENTITY + " = g." + Group2.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP_V2 +
            " AND disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = g." + Group2.BYTES_GROUP_IDENTIFIER +
            " INNER JOIN " + Group2Member.TABLE_NAME + " AS gm " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = g." + Group2.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = g." + Group2.BYTES_GROUP_IDENTIFIER +
            " WHERE g." + Group2.OWN_PERMISSION_CHANGE_SETTINGS + " = 1 " +
            " AND gm." + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND gm." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    List<Long> getGroupV2DiscussionIdsWithSettingsPermissionWithContact(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);


    @Query("SELECT * FROM (SELECT c.* FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND (c." + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR c." + Contact.PRE_KEY_COUNT + " > 0) "  +
            " UNION SELECT c.* FROM " + Contact.TABLE_NAME + " AS c " +
            " WHERE c." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND c." + Contact.BYTES_CONTACT_IDENTITY + " IN ( :bytesContactIdentities )" +
            " AND (c." + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR c." + Contact.PRE_KEY_COUNT + " > 0)) " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getGroupMemberContactsAndMore(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull List<byte[]> bytesContactIdentities);

    @Query("SELECT COUNT(*) FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    int countContactGroups(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT EXISTS (SELECT 1 FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier )")
    boolean groupHasMembers(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + Group2Member.PENDING_CREATION_TIMESTAMP + " IS NOT NULL")
    List<Group2Member> getAllForContactWithPendingCreationTimestamp(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.PENDING_CREATION_TIMESTAMP + " IS NOT NULL")
    List<Group2Member> getAllWithPendingCreationTimestamp();

    // members and pending members of all groups (or one group), ranked for the avatar cluster:
    // with-photo first, then admins, then by name; pending members usually have no contact row
    // so they naturally rank last
    String GROUP_MEMBER_AVATAR_INFO_HAS_PHOTO = " (CASE WHEN c." + Contact.CUSTOM_PHOTO_URL + " IS NULL THEN c." + Contact.PHOTO_URL + " WHEN c." + Contact.CUSTOM_PHOTO_URL + " = '' THEN NULL ELSE c." + Contact.CUSTOM_PHOTO_URL + " END) IS NOT NULL AS hasPhoto ";
    String GROUP_MEMBER_AVATAR_INFO_MEMBER_SELECT = "SELECT gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " AS bytesGroupIdentifier, " +
            " gm." + Group2Member.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " gm." + Group2Member.PERMISSION_ADMIN + " AS permissionAdmin, " +
            " c." + Contact.SORT_DISPLAY_NAME + " AS sortName, " +
            GROUP_MEMBER_AVATAR_INFO_HAS_PHOTO +
            " FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ";
    String GROUP_MEMBER_AVATAR_INFO_PENDING_SELECT = "SELECT pm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " AS bytesGroupIdentifier, " +
            " pm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " AS bytesContactIdentity, " +
            " pm." + Group2PendingMember.PERMISSION_ADMIN + " AS permissionAdmin, " +
            " COALESCE(c." + Contact.SORT_DISPLAY_NAME + ", pm." + Group2PendingMember.SORT_DISPLAY_NAME + ") AS sortName, " +
            GROUP_MEMBER_AVATAR_INFO_HAS_PHOTO +
            " FROM " + Group2PendingMember.TABLE_NAME + " AS pm " +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON pm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND pm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE pm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ";
    String GROUP_MEMBER_AVATAR_INFO_ORDER = " ORDER BY hasPhoto DESC, permissionAdmin DESC, sortName ASC";

    @Query("SELECT * FROM ( " + GROUP_MEMBER_AVATAR_INFO_MEMBER_SELECT +
            " UNION ALL " + GROUP_MEMBER_AVATAR_INFO_PENDING_SELECT +
            " )" + GROUP_MEMBER_AVATAR_INFO_ORDER)
    List<GroupMemberAvatarInfo> getAllGroupMembersAvatarInfoSync(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM ( " + GROUP_MEMBER_AVATAR_INFO_MEMBER_SELECT +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " UNION ALL " + GROUP_MEMBER_AVATAR_INFO_PENDING_SELECT +
            " AND pm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " )" + GROUP_MEMBER_AVATAR_INFO_ORDER)
    List<GroupMemberAvatarInfo> getGroupMembersAvatarInfoSync(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    class GroupMemberAvatarInfo {
        public byte[] bytesGroupIdentifier;
        public byte[] bytesContactIdentity;
        public boolean permissionAdmin;
        public byte[] sortName;
        public boolean hasPhoto;
    }


    class Group2MemberOrPending {
        @Embedded
        public Contact contact;

        public byte[] bytesContactIdentity;
        public byte[] sortDisplayName;
        public String identityDetails;
        public boolean permissionAdmin;
        public boolean permissionSendMessage;
        public boolean permissionRemoteDeleteAnything;
        public boolean permissionEditOrRemoteDeleteOwnMessages;
        public boolean permissionChangeSettings;
        public boolean pending;
    }

    class Group2MemberOrPendingForMention {
        @Embedded
        public Contact contact;

        public byte[] bytesContactIdentity;
        public byte[] sortDisplayName;
        public String identityDetails;
        public String fullSearchDisplayName;
    }
}
