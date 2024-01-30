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
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;

@Dao
public interface Group2Dao {
    String PREFIX_GROUP2_COLUMNS = "grpp." + Group2.BYTES_OWNED_IDENTITY + " AS group2_" + Group2.BYTES_OWNED_IDENTITY + ", " +
            "grpp." + Group2.BYTES_GROUP_IDENTIFIER + " AS group2_" + Group2.BYTES_GROUP_IDENTIFIER + ", " +
            "grpp." + Group2.KEYCLOAK_MANAGED + " AS group2_" + Group2.KEYCLOAK_MANAGED + ", " +
            "grpp." + Group2.NAME + " AS group2_" + Group2.NAME + ", " +
            "grpp." + Group2.PHOTO_URL + " AS group2_" + Group2.PHOTO_URL + ", " +
            "grpp." + Group2.GROUP_MEMBERS_NAMES + " AS group2_" + Group2.GROUP_MEMBERS_NAMES + ", " +
            "grpp." + Group2.UPDATE_IN_PROGRESS + " AS group2_" + Group2.UPDATE_IN_PROGRESS + ", " +
            "grpp." + Group2.NEW_PUBLISHED_DETAILS + " AS group2_" + Group2.NEW_PUBLISHED_DETAILS + ", " +
            "grpp." + Group2.OWN_PERMISSION_ADMIN + " AS group2_" + Group2.OWN_PERMISSION_ADMIN + ", " +
            "grpp." + Group2.OWN_PERMISSION_REMOTE_DELETE_ANYTHING + " AS group2_" + Group2.OWN_PERMISSION_REMOTE_DELETE_ANYTHING + ", " +
            "grpp." + Group2.OWN_PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES + " AS group2_" + Group2.OWN_PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES + ", " +
            "grpp." + Group2.OWN_PERMISSION_CHANGE_SETTINGS + " AS group2_" + Group2.OWN_PERMISSION_CHANGE_SETTINGS + ", " +
            "grpp." + Group2.OWN_PERMISSION_SEND_MESSAGE + " AS group2_" + Group2.OWN_PERMISSION_SEND_MESSAGE + ", " +
            "grpp." + Group2.CUSTOM_NAME + " AS group2_" + Group2.CUSTOM_NAME + ", " +
            "grpp." + Group2.CUSTOM_PHOTO_URL + " AS group2_" + Group2.CUSTOM_PHOTO_URL + ", " +
            "grpp." + Group2.PERSONAL_NOTE + " AS group2_" + Group2.PERSONAL_NOTE;

    String GROUP2_NULL_COLUMNS = " NULL AS group2_" + Group2.BYTES_OWNED_IDENTITY + ", " +
            " NULL AS group2_" + Group2.BYTES_GROUP_IDENTIFIER + ", " +
            " NULL AS group2_" + Group2.KEYCLOAK_MANAGED + ", " +
            " NULL AS group2_" + Group2.NAME + ", " +
            " NULL AS group2_" + Group2.PHOTO_URL + ", " +
            " NULL AS group2_" + Group2.GROUP_MEMBERS_NAMES + ", " +
            " NULL AS group2_" + Group2.UPDATE_IN_PROGRESS + ", " +
            " NULL AS group2_" + Group2.NEW_PUBLISHED_DETAILS + ", " +
            " NULL AS group2_" + Group2.OWN_PERMISSION_ADMIN + ", " +
            " NULL AS group2_" + Group2.OWN_PERMISSION_REMOTE_DELETE_ANYTHING + ", " +
            " NULL AS group2_" + Group2.OWN_PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES + ", " +
            " NULL AS group2_" + Group2.OWN_PERMISSION_CHANGE_SETTINGS + ", " +
            " NULL AS group2_" + Group2.OWN_PERMISSION_SEND_MESSAGE + ", " +
            " NULL AS group2_" + Group2.CUSTOM_NAME + ", " +
            " NULL AS group2_" + Group2.CUSTOM_PHOTO_URL + ", " +
            " NULL AS group2_" + Group2.PERSONAL_NOTE;

    String GROUP_NULL_COLUMNS = " NULL AS group_" + Group.BYTES_GROUP_OWNER_AND_UID + ", " +
            " NULL AS group_" + Group.BYTES_OWNED_IDENTITY + ", " +
            " NULL AS group_" + Group.CUSTOM_NAME + ", " +
            " NULL AS group_" + Group.NAME + ", " +
            " NULL AS group_" + Group.NEW_PUBLISHED_DETAILS + ", " +
            " NULL AS group_" + Group.BYTES_GROUP_OWNER_IDENTITY + ", " +
            " NULL AS group_" + Group.PHOTO_URL + ", " +
            " NULL AS group_" + Group.GROUP_MEMBERS_NAMES + ", " +
            " NULL AS group_" + Group.CUSTOM_PHOTO_URL + ", " +
            " NULL AS group_" + Group.PERSONAL_NOTE;

    @Insert
    void insert(Group2 group);

    @Delete
    void delete(Group2 group);

    @Update
    void update(Group2 group);

    @Query("UPDATE " + Group2.TABLE_NAME + " SET " +
            Group2.PHOTO_URL + " = :photoUrl " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    void updatePhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, String photoUrl);

    @Query("UPDATE " + Group2.TABLE_NAME + " SET " +
            Group2.CUSTOM_NAME + " = :customName " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    void updateCustomName(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, String customName);

    @Query("UPDATE " + Group2.TABLE_NAME + " SET " +
            Group2.CUSTOM_PHOTO_URL + " = :customPhotoUrl " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    void updateCustomPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, String customPhotoUrl);

    @Query("UPDATE " + Group2.TABLE_NAME + " SET " +
            Group2.PERSONAL_NOTE + " = :personalNote " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    void updatePersonalNote(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, String personalNote);

    @Query("UPDATE " + Group2.TABLE_NAME + " SET " +
            Group2.UPDATE_IN_PROGRESS + " = :updating " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    void updateUpdateInProgress(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, int updating);

    @Query("UPDATE " + Group2.TABLE_NAME + " SET " +
            Group2.NEW_PUBLISHED_DETAILS + " = :newPublishedDetails " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    void updateNewPublishedDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, int newPublishedDetails);

    @Query("UPDATE " + Group2.TABLE_NAME + " SET " +
            Group2.GROUP_MEMBERS_NAMES + " = :groupMembersNames " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    void updateGroupMembersNames(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, String groupMembersNames);

    @Query("SELECT * FROM " + Group2.TABLE_NAME +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    Group2 get(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM " + Group2.TABLE_NAME +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    LiveData<Group2> getLiveData(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

    @Query("SELECT " + PREFIX_GROUP2_COLUMNS + ", " + GROUP_NULL_COLUMNS + " FROM " + Group2.TABLE_NAME + " AS grpp " +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier ")
    LiveData<GroupOrGroup2> getGroupOrGroup2LiveData(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM " + Group2.TABLE_NAME)
    List<Group2> getAll();

    @Query("SELECT * FROM " + Group2.TABLE_NAME +
            " WHERE " + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    List<Group2> getAllForOwnedIdentity(byte[] bytesOwnedIdentity);


    @Query("SELECT g.* FROM " + Group2.TABLE_NAME + " AS g " +
            " INNER JOIN " + Group2Member.TABLE_NAME + " AS gm " +
            " ON g." + Group2.BYTES_OWNED_IDENTITY + " = gm." + Group2Member.BYTES_OWNED_IDENTITY +
            " AND g." + Group2.BYTES_GROUP_IDENTIFIER + " = gm." + Group2Member.BYTES_GROUP_IDENTIFIER +
            " WHERE gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " UNION " +
            " SELECT g.* FROM " + Group2.TABLE_NAME + " AS g " +
            " INNER JOIN " + Group2PendingMember.TABLE_NAME + " AS gpm " +
            " ON g." + Group2.BYTES_OWNED_IDENTITY + " = gpm." + Group2PendingMember.BYTES_OWNED_IDENTITY +
            " AND g." + Group2.BYTES_GROUP_IDENTIFIER + " = gpm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER +
            " WHERE gpm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND gpm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ")
    List<Group2> getAllForContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Nullable
    @Query("SELECT mname FROM ( " +
            " SELECT COALESCE(c." + Contact.CUSTOM_DISPLAY_NAME + ", c." + Contact.DISPLAY_NAME + ") AS mname, c." + Contact.SORT_DISPLAY_NAME + " AS ord " +
            " FROM " + Contact.TABLE_NAME + " AS c " +
            " INNER JOIN " + Group2Member.TABLE_NAME + " AS gm " +
            " ON c." + Contact.BYTES_OWNED_IDENTITY + " = gm." + Group2Member.BYTES_OWNED_IDENTITY +
            " AND c." + Contact.BYTES_CONTACT_IDENTITY + " = gm." + Group2Member.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +

            " UNION " +

            " SELECT " + Group2PendingMember.DISPLAY_NAME + " AS mname, " + Group2PendingMember.SORT_DISPLAY_NAME + " AS ord FROM " + Group2PendingMember.TABLE_NAME +
            " WHERE " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " ) ORDER BY ord ASC ")
    String[] getGroupMembersNames(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);

    @Nullable
    @Query("SELECT mname FROM ( " +
            " SELECT COALESCE(c." + Contact.CUSTOM_DISPLAY_NAME + ", c." + Contact.FIRST_NAME + ", c." + Contact.DISPLAY_NAME + ") AS mname, c." + Contact.SORT_DISPLAY_NAME + " AS ord " +
            " FROM " + Contact.TABLE_NAME + " AS c " +
            " INNER JOIN " + Group2Member.TABLE_NAME + " AS gm " +
            " ON c." + Contact.BYTES_OWNED_IDENTITY + " = gm." + Group2Member.BYTES_OWNED_IDENTITY +
            " AND c." + Contact.BYTES_CONTACT_IDENTITY + " = gm." + Group2Member.BYTES_CONTACT_IDENTITY +
            " WHERE gm." + Group2Member.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +

            " UNION " +

            " SELECT COALESCE(" + Group2PendingMember.FIRST_NAME + ", " + Group2PendingMember.DISPLAY_NAME + ") AS mname, " + Group2PendingMember.SORT_DISPLAY_NAME + " AS ord FROM " + Group2PendingMember.TABLE_NAME +
            " WHERE " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " ) ORDER BY ord ASC ")
    String[] getGroupMembersFirstNames(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);


    @Query("SELECT * FROM (" +
            " SELECT " + GroupDao.PREFIX_GROUP_COLUMNS + ", " + GROUP2_NULL_COLUMNS + " FROM " + Group.TABLE_NAME + " AS groop " +
            " WHERE groop." + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " UNION " +
            " SELECT " + GROUP_NULL_COLUMNS + ", " + PREFIX_GROUP2_COLUMNS + " FROM " + Group2.TABLE_NAME + " AS grpp " +
            " WHERE grpp." + Group2.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            ") ORDER BY COALESCE(" +
            " group_" + Group.CUSTOM_NAME + ", " +
            " group_" + Group.NAME + ", " +
            " CASE WHEN group2_" + Group2.CUSTOM_NAME + " = '' THEN NULL ELSE group2_" + Group2.CUSTOM_NAME + " END, " +
            " group2_" + Group2.NAME + ", " +
            " group2_" + Group2.GROUP_MEMBERS_NAMES +
            " ) COLLATE NOCASE ASC ")
    LiveData<List<GroupOrGroup2>> getAllGroupOrGroup2(byte[] bytesOwnedIdentity);

    @Query("SELECT c.* FROM " + Contact.TABLE_NAME + " AS c " +
            " LEFT JOIN " + Group2Member.TABLE_NAME + " AS gm " +
            " ON gm." + Group2Member.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gm." + Group2Member.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " AND gm." + Group2Member.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " LEFT JOIN " + Group2PendingMember.TABLE_NAME + " AS gpm " +
            " ON gpm." + Group2PendingMember.BYTES_OWNED_IDENTITY + " = c." + Contact.BYTES_OWNED_IDENTITY +
            " AND gpm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " = c." + Contact.BYTES_CONTACT_IDENTITY +
            " AND gpm." + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " WHERE c." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND c." + Contact.ACTIVE + " = 1 " +
            " AND c." + Contact.CAPABILITY_GROUPS_V2 + " = 1 " +
            " AND c." + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " AND ( c." + Contact.BYTES_CONTACT_IDENTITY + " IN (:bytesRemovedMemberIdentities) " +
            " OR ( gm." + Group2Member.BYTES_CONTACT_IDENTITY + " IS NULL " +
            " AND gpm." + Group2PendingMember.BYTES_CONTACT_IDENTITY + " IS NULL ) ) " +
            " AND c." + Contact.BYTES_CONTACT_IDENTITY + " NOT IN (:bytesAddedMemberIdentities) " +
            " ORDER BY c." + Contact.SORT_DISPLAY_NAME + " ASC ")
    LiveData<List<Contact>> getAllValidContactsNotInGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, List<byte[]> bytesAddedMemberIdentities, List<byte[]> bytesRemovedMemberIdentities);

    @Query("SELECT " + Group2.CUSTOM_PHOTO_URL + " FROM " + Group2.TABLE_NAME +
            " WHERE " + Group2.CUSTOM_PHOTO_URL + " IS NOT NULL")
    List<String> getAllCustomPhotoUrls();


    class GroupOrGroup2 {
        @Embedded(prefix = "group_")
        public Group group;

        @Embedded(prefix = "group2_")
        public Group2 group2;
    }
}
