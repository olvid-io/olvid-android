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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.PendingGroupMember;

@Dao
public interface ContactDao {
    @Insert
    void insert(@NonNull Contact contact);

    @Delete
    void delete(@NonNull Contact contact);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.IDENTITY_DETAILS + " = :identityDetails, " +
            Contact.DISPLAY_NAME + " = :displayName, " +
            Contact.FIRST_NAME + " = :firstName, " +
            Contact.CUSTOM_DISPLAY_NAME + " = :customDisplayName, " +
            Contact.SORT_DISPLAY_NAME + " = :sortDisplayName, " +
            Contact.FULL_SEARCH_DISPLAY_NAME + " = :fullSearchDisplayName " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateAllDisplayNames(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, @Nullable String identityDetails, @NonNull String displayName, @Nullable String firstName, @Nullable String customDisplayName, @NonNull byte[] sortDisplayName, @NonNull String fullSearchDisplayName);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.FIRST_NAME + " = :firstName " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateFirstName(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, @Nullable String firstName);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.KEYCLOAK_MANAGED + " = :keycloakManaged " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateKeycloakManaged(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean keycloakManaged);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.ACTIVE + " = :active " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateActive(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean active);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.TRUST_LEVEL + " = :trustLevel " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateTrustLevel(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, int trustLevel);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.ONE_TO_ONE + " = :oneToOne " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateOneToOne(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean oneToOne);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.RECENTLY_ONLINE + " = :recentlyOnline " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateRecentlyOnline(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean recentlyOnline);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.NEW_PUBLISHED_DETAILS + " = :newPublishedDetails " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updatePublishedDetailsStatus(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, int newPublishedDetails);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.PHOTO_URL + " = :photoUrl " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updatePhotoUrl(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, @Nullable String photoUrl);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.CUSTOM_NAME_HUE + " = :customNameHue " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateCustomNameHue(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, @Nullable Integer customNameHue);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.PERSONAL_NOTE + " = :personalNote, " +
            Contact.FULL_SEARCH_DISPLAY_NAME + " = :fullSearchDisplayName " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updatePersonalNote(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, @Nullable String personalNote, @NonNull String fullSearchDisplayName);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.CUSTOM_PHOTO_URL + " = :customPhotoUrl " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateCustomPhotoUrl(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, @Nullable String customPhotoUrl);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.DEVICE_COUNT + " = :deviceCount, " +
            Contact.ESTABLISHED_CHANNEL_COUNT + " = :establishedChannelCount, " +
            Contact.PRE_KEY_COUNT + " = :preKeyCount " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateCounts(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, int deviceCount, int establishedChannelCount, int preKeyCount);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.CAPABILITY_WEBRTC_CONTINUOUS_ICE + " = :capable " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateCapabilityWebrtcContinuousIce(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean capable);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.CAPABILITY_GROUPS_V2 + " = :capable " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateCapabilityGroupsV2(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean capable);

    @Query("UPDATE " + Contact.TABLE_NAME +
            " SET " + Contact.CAPABILITY_ONE_TO_ONE_CONTACTS + " = :capable " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateCapabilityOneToOneContacts(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity, boolean capable);





    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND " + Contact.ONE_TO_ONE + " = 1 " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllOneToOneForOwnedIdentity(@NonNull byte[] ownedIdentityBytes);

    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND " + Contact.ONE_TO_ONE + " = 0 " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllNotOneToOneForOwnedIdentity(@NonNull byte[] ownedIdentityBytes);

    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    List<Contact> getAllForOwnedIdentitySync(@NonNull byte[] ownedIdentityBytes);

    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND " + Contact.ACTIVE + " = 1 " +
            " AND (" + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR " + Contact.PRE_KEY_COUNT + " > 0) " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllForOwnedIdentityWithChannel(@NonNull byte[] ownedIdentityBytes);

    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " AND " + Contact.ACTIVE + " = 1 " +
            " AND " + Contact.CAPABILITY_GROUPS_V2 + " = 1 " +
            " AND (" + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR " + Contact.PRE_KEY_COUNT + " > 0) " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllForOwnedIdentityWithChannelAndGroupV2Capability(@NonNull byte[] ownedIdentityBytes);

    @Query("SELECT * FROM " + Contact.TABLE_NAME + " " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " != :bytesExcludedContactIdentity " +
            " AND " + Contact.ACTIVE + " = 1 " +
            " AND " + Contact.ONE_TO_ONE + " = 1 " +
            " AND (" + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR " + Contact.PRE_KEY_COUNT + " > 0) " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllOneToOneForOwnedIdentityWithChannelExcludingOne(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesExcludedContactIdentity);

    @Query("SELECT * FROM " + Contact.TABLE_NAME + " " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Contact.BYTES_CONTACT_IDENTITY + " NOT IN (:excludedContacts) " +
            " AND " + Contact.ACTIVE + " = 1 " +
            " AND (" + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR " + Contact.PRE_KEY_COUNT + " > 0) " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllForOwnedIdentityWithChannelExcludingSome(@NonNull byte[] bytesOwnedIdentity, @NonNull List<byte[]> excludedContacts);

    @Query("SELECT * FROM " + Contact.TABLE_NAME)
    List<Contact> getAllSync();

    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.ACTIVE + " = 1 " +
            " AND (" + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR " + Contact.PRE_KEY_COUNT + " > 0) ")
    List<Contact> getAllWithChannel();


    @Query("SELECT * FROM " + Contact.TABLE_NAME + " WHERE " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity AND " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    @Nullable Contact get(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Contact.TABLE_NAME + " WHERE " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity AND " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    LiveData<Contact> getAsync(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAsList(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Contact.TABLE_NAME +
            " WHERE " + Contact.BYTES_CONTACT_IDENTITY + " IN ( :bytesContactIdentities )" +
            " AND " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity" +
            " AND (" + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR " + Contact.PRE_KEY_COUNT + " > 0) ")
    LiveData<List<Contact>> getWithChannelAsList(@NonNull byte[] bytesOwnedIdentity, @NonNull List<byte[]> bytesContactIdentities);

    @Query( "SELECT * FROM ( SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " WHERE " + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND (" + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " OR " + Contact.PRE_KEY_COUNT + " > 0) " +
            " EXCEPT SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS membersjoin " +
            " ON membersjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = contact." + Contact.BYTES_CONTACT_IDENTITY +
            " AND membersjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = contact." + Contact.BYTES_OWNED_IDENTITY +
            " WHERE membersjoin." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND membersjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid " +
            " EXCEPT SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + PendingGroupMember.TABLE_NAME + " AS pendingmember " +
            " ON pendingmember." + PendingGroupMember.BYTES_IDENTITY + " = contact." + Contact.BYTES_CONTACT_IDENTITY +
            " AND pendingmember." + PendingGroupMember.BYTES_OWNED_IDENTITY + " = contact." + Contact.BYTES_OWNED_IDENTITY +
            " WHERE pendingmember." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND pendingmember." + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid )" +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllForOwnedIdentityWithChannelExcludingGroup(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupOwnerAndUid);

    @Query( "SELECT * FROM ( SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS membersjoin " +
            " ON membersjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = contact." + Contact.BYTES_CONTACT_IDENTITY +
            " AND membersjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = contact." + Contact.BYTES_OWNED_IDENTITY +
            " WHERE membersjoin." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND membersjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid " +
            " UNION SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + PendingGroupMember.TABLE_NAME + " AS pendingmember " +
            " ON pendingmember." + PendingGroupMember.BYTES_IDENTITY + " = contact." + Contact.BYTES_CONTACT_IDENTITY +
            " AND pendingmember." + PendingGroupMember.BYTES_OWNED_IDENTITY + " = contact." + Contact.BYTES_OWNED_IDENTITY +
            " WHERE pendingmember." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND pendingmember." + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid )" +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getAllInGroupOrPending(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupOwnerAndUid);

    @Query("SELECT " + Contact.CUSTOM_PHOTO_URL + " FROM " + Contact.TABLE_NAME +
     " WHERE " + Contact.CUSTOM_PHOTO_URL + " IS NOT NULL")
    List<String> getAllCustomPhotoUrls();

    @Query("SELECT COUNT(*) FROM " + Contact.TABLE_NAME)
    long countAll();
}
