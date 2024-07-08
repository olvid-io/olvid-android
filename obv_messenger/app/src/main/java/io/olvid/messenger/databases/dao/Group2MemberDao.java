/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
    void insert(Group2Member groupMember);

    @Delete
    void delete(Group2Member groupMember);

    @Update
    void update(Group2Member groupMember);

    @Query("SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity ")
    Group2Member get(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, byte[] bytesContactIdentity);

    @Query("SELECT EXISTS (SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity )")
    boolean isGroupMember(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, byte[] bytesContactIdentity);


    @Query("SELECT * FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier")
    List<Group2Member> getGroupMembers(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

    @Query("SELECT c.* FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier")
    List<Contact> getGroupMemberContactsSync(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

    @Query("SELECT c.* FROM " + Group2Member.TABLE_NAME + " AS gm " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS c " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier")
    LiveData<List<Contact>> getGroupMemberContacts(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

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
    LiveData<List<Group2MemberOrPending>> getGroupMembersAndPending(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

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
    LiveData<List<Group2MemberOrPendingForMention>> getGroupMembersAndPendingForMention(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

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
    List<Group2MemberOrPending> getGroupMembersAndPendingSync(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

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
    List<Long> getGroupV2DiscussionIdsWithSettingsPermissionWithContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);


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
    LiveData<List<Contact>> getGroupMemberContactsAndMore(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, List<byte[]> bytesContactIdentities);

    @Query("SELECT COUNT(*) FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    int countContactGroups(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT EXISTS (SELECT 1 FROM " + Group2Member.TABLE_NAME +
            " WHERE " + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier )")
    boolean groupHasMembers(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);


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
