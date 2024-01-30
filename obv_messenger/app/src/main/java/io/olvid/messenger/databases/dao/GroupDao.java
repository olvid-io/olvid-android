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
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.PendingGroupMember;

@Dao
public interface GroupDao {
    String PREFIX_GROUP_COLUMNS = "groop." + Group.BYTES_GROUP_OWNER_AND_UID + " AS group_" + Group.BYTES_GROUP_OWNER_AND_UID + ", " +
            "groop." + Group.BYTES_OWNED_IDENTITY + " AS group_" + Group.BYTES_OWNED_IDENTITY + ", " +
            "groop." + Group.CUSTOM_NAME + " AS group_" + Group.CUSTOM_NAME + ", " +
            "groop." + Group.NAME + " AS group_" + Group.NAME + ", " +
            "groop." + Group.NEW_PUBLISHED_DETAILS + " AS group_" + Group.NEW_PUBLISHED_DETAILS + ", " +
            "groop." + Group.BYTES_GROUP_OWNER_IDENTITY + " AS group_" + Group.BYTES_GROUP_OWNER_IDENTITY + ", " +
            "groop." + Group.PHOTO_URL + " AS group_" + Group.PHOTO_URL + ", " +
            "groop." + Group.GROUP_MEMBERS_NAMES + " AS group_" + Group.GROUP_MEMBERS_NAMES + ", " +
            "groop." + Group.CUSTOM_PHOTO_URL + " AS group_" + Group.CUSTOM_PHOTO_URL + ", " +
            "groop." + Group.PERSONAL_NOTE + " AS group_" + Group.PERSONAL_NOTE;

    @Insert
    void insert(Group group);

    @Delete
    void delete(Group group);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.NEW_PUBLISHED_DETAILS + " = :newPublishedDetails " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    void updatePublishedDetailsStatus(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, int newPublishedDetails);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.NAME + " = :name, " +
            Group.PHOTO_URL + " = :photoUrl " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    void updateNameAndPhoto(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String name, String photoUrl);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.CUSTOM_NAME + " = :customName " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    void updateCustomName(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String customName);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.CUSTOM_PHOTO_URL + " = :customPhotoUrl " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    void updateCustomPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String customPhotoUrl);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.PERSONAL_NOTE + " = :personalNote " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    void updatePersonalNote(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String personalNote);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.PHOTO_URL + " = :photoUrl " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    void updatePhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String photoUrl);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.GROUP_MEMBERS_NAMES + " = :groupMembersNames " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    void updateGroupMembersNames(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String groupMembersNames);

    @Query("SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    Group get(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);

    @Query("SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    LiveData<Group> getLiveData(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);

    @Query("SELECT " + PREFIX_GROUP_COLUMNS + ", " + Group2Dao.GROUP2_NULL_COLUMNS + " FROM " + Group.TABLE_NAME + " AS groop " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    LiveData<Group2Dao.GroupOrGroup2> getGroupOrGroup2LiveData(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);


    @Query("SELECT * FROM " + Group.TABLE_NAME)
    List<Group> getAll();

    @Query("SELECT g.* FROM " + Group.TABLE_NAME + " AS g " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS cgj " +
            " ON g." + Group.BYTES_OWNED_IDENTITY + " = cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " AND g." + Group.BYTES_GROUP_OWNER_AND_UID + " = cgj." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID +
            " WHERE cgj." + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ")
    List<Group> getAllForContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    
    @Query("SELECT * FROM " + Group.TABLE_NAME +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NULL")
    List<Group> getAllOwned(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + Group.TABLE_NAME +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NOT NULL")
    List<Group> getAllJoined(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + Group.TABLE_NAME +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ")
    List<Group> getAllForOwnedIdentity(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + Group.TABLE_NAME +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY (" + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NULL) DESC, " + Group.NAME + " COLLATE NOCASE ASC")
    LiveData<List<Group>> getAllOwnedThenJoined(byte[] bytesOwnedIdentity);

    @Query("SELECT " + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " FROM " + PendingGroupMember.TABLE_NAME +
            " WHERE " + PendingGroupMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + PendingGroupMember.BYTES_IDENTITY + " = :bytesContactIdentity " +
            " AND " + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID +
            " IN (SELECT " + Group.BYTES_GROUP_OWNER_AND_UID + " FROM " + Group.TABLE_NAME +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NOT NULL)")
    List<byte[]> getBytesGroupOwnerAndUidOfJoinedGroupWithPendingMember(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT " + Group.CUSTOM_PHOTO_URL + " FROM " + Group.TABLE_NAME +
            " WHERE " + Group.CUSTOM_PHOTO_URL + " IS NOT NULL")
    List<String> getAllCustomPhotoUrls();


    @Nullable
    @Query("SELECT COALESCE(c." + Contact.CUSTOM_DISPLAY_NAME + " , c." + Contact.DISPLAY_NAME + ") " +
            " FROM " + Contact.TABLE_NAME + " AS c " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS cgj " +
            " ON c." + Contact.BYTES_OWNED_IDENTITY + " = cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " AND c." + Contact.BYTES_CONTACT_IDENTITY + " = cgj." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " WHERE cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND cgj." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid " +
            " ORDER BY c." + Contact.SORT_DISPLAY_NAME + " ASC ")
    String[] getGroupMembersNames(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);

    @Nullable
    @Query("SELECT COALESCE(c." + Contact.CUSTOM_DISPLAY_NAME + " , c." + Contact.FIRST_NAME + " , c." + Contact.DISPLAY_NAME + ") " +
            " FROM " + Contact.TABLE_NAME + " AS c " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS cgj " +
            " ON c." + Contact.BYTES_OWNED_IDENTITY + " = cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " AND c." + Contact.BYTES_CONTACT_IDENTITY + " = cgj." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " WHERE cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND cgj." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid " +
            " ORDER BY c." + Contact.SORT_DISPLAY_NAME + " ASC ")
    String[] getGroupMembersFirstNames(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);
}
