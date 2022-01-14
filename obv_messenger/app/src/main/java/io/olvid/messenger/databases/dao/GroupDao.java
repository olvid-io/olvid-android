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

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;

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
            "groop." + Group.CUSTOM_PHOTO_URL + " AS group_" + Group.CUSTOM_PHOTO_URL + ", " +
            "groop." + Group.PERSONAL_NOTE + " AS group_" + Group.PERSONAL_NOTE;

    @Insert
    void insert(Group group);

    @Delete
    void delete(Group group);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.NEW_PUBLISHED_DETAILS + " = :newPublishedDetails " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnedAndUid")
    void updatePublishedDetailsStatus(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnedAndUid, int newPublishedDetails);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.NAME + " = :name, " +
            Group.PHOTO_URL + " = :photoUrl " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnedAndUid")
    void updateNameAndPhoto(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnedAndUid, String name, String photoUrl);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.CUSTOM_NAME + " = :customName " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnedAndUid")
    void updateCustomName(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnedAndUid, String customName);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.CUSTOM_PHOTO_URL + " = :customPhotoUrl " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnedAndUid")
    void updateCustomPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnedAndUid, String customPhotoUrl);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.PERSONAL_NOTE + " = :personalNote " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnedAndUid")
    void updatePersonalNote(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnedAndUid, String personalNote);

    @Query("UPDATE " + Group.TABLE_NAME +
            " SET " + Group.PHOTO_URL + " = :photoUrl " +
            " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnedAndUid")
    void updatePhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnedAndUid, String photoUrl);


    @Query("SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    Group get(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);

    @Query("SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " + Group.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    LiveData<Group> getLiveData(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);

    @Query("SELECT * FROM " + Group.TABLE_NAME)
    List<Group> getAllSync();

    @Query("SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NULL")
    List<Group> getAllOwned(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + Group.TABLE_NAME + " WHERE " + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NOT NULL")
    List<Group> getAllJoined(byte[] bytesOwnedIdentity);

    @Query("SELECT groop.*, GROUP_CONCAT(CASE WHEN contact." + Contact.CUSTOM_DISPLAY_NAME + " IS NULL THEN contact." + Contact.DISPLAY_NAME  + " ELSE contact." + Contact.CUSTOM_DISPLAY_NAME  + " END, :joiner) AS contactDisplayNames FROM " + Group.TABLE_NAME + " AS groop " +
            " LEFT JOIN " + ContactGroupJoin.TABLE_NAME + " AS cgjoin " +
            " ON cgjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = groop." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND cgjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = groop." + Group.BYTES_OWNED_IDENTITY +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS contact " +
            " ON cgjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = contact." + Contact.BYTES_CONTACT_IDENTITY +
            " AND cgjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = contact." + Contact.BYTES_OWNED_IDENTITY +
            " WHERE groop." + Group.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " GROUP BY groop." + Group.BYTES_GROUP_OWNER_AND_UID +
            " ORDER BY (groop." + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NULL) DESC, groop." + Group.NAME + " COLLATE NOCASE ASC")
    LiveData<List<GroupAndContactDisplayNames>> getAllOwnedThenJoined(byte[] bytesOwnedIdentity, String joiner);

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

    class GroupAndContactDisplayNames {
        @Embedded
        public Group group;

        public String contactDisplayNames;
    }
}
