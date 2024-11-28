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
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.PendingGroupMember;

@Dao
public interface PendingGroupMemberDao {
    @Insert
    void insert(@NonNull PendingGroupMember pendingGroupMember);

    @Delete
    void delete(@NonNull PendingGroupMember pendingGroupMember);

    @Update
    void update(@NonNull PendingGroupMember pendingGroupMember);

    @Query("SELECT * FROM " + PendingGroupMember.TABLE_NAME + " WHERE " + PendingGroupMember.BYTES_IDENTITY + " = :bytesIdentity AND " +
            PendingGroupMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " +
            PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    @Nullable PendingGroupMember get(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupOwnerAndUid, @NonNull byte[] bytesIdentity);

    @Query("SELECT * FROM " + PendingGroupMember.TABLE_NAME + " WHERE " +
            PendingGroupMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity AND " +
            PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    List<PendingGroupMember> getGroupPendingMembers(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupOwnerAndUid);

    @Query("SELECT " +
            " pending." + PendingGroupMember.BYTES_IDENTITY + " AS pending_" + PendingGroupMember.BYTES_IDENTITY + ", " +
            " pending." + PendingGroupMember.BYTES_OWNED_IDENTITY + " AS pending_" + PendingGroupMember.BYTES_OWNED_IDENTITY + ", " +
            " pending." + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " AS pending_" + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + ", " +
            " pending." + PendingGroupMember.DISPLAY_NAME + " AS pending_" + PendingGroupMember.DISPLAY_NAME + ", " +
            " pending." + PendingGroupMember.DECLINED + " AS pending_" + PendingGroupMember.DECLINED + ", " +
            " contact.* FROM " + PendingGroupMember.TABLE_NAME + " AS pending " +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS contact " +
            " ON contact." + Contact.BYTES_OWNED_IDENTITY + " = pending." + PendingGroupMember.BYTES_OWNED_IDENTITY +
            " AND contact." + Contact.BYTES_CONTACT_IDENTITY + " = pending." + PendingGroupMember.BYTES_IDENTITY +
            " WHERE pending." + PendingGroupMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND pending." + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    LiveData<List<PendingGroupMemberAndContact>> getGroupPendingMemberAndContactsLiveData(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupOwnerAndUid);

    @Query("SELECT " +
            " pending." + PendingGroupMember.BYTES_IDENTITY + " AS pending_" + PendingGroupMember.BYTES_IDENTITY + ", " +
            " pending." + PendingGroupMember.BYTES_OWNED_IDENTITY + " AS pending_" + PendingGroupMember.BYTES_OWNED_IDENTITY + ", " +
            " pending." + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " AS pending_" + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + ", " +
            " pending." + PendingGroupMember.DISPLAY_NAME + " AS pending_" + PendingGroupMember.DISPLAY_NAME + ", " +
            " pending." + PendingGroupMember.DECLINED + " AS pending_" + PendingGroupMember.DECLINED + ", " +
            " contact.* FROM " + PendingGroupMember.TABLE_NAME + " AS pending " +
            " LEFT JOIN " + Contact.TABLE_NAME + " AS contact " +
            " ON contact." + Contact.BYTES_OWNED_IDENTITY + " = pending." + PendingGroupMember.BYTES_OWNED_IDENTITY +
            " AND contact." + Contact.BYTES_CONTACT_IDENTITY + " = pending." + PendingGroupMember.BYTES_IDENTITY +
            " WHERE pending." + PendingGroupMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND pending." + PendingGroupMember.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid")
    List<PendingGroupMemberAndContact> getGroupPendingMemberAndContacts(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupOwnerAndUid);

    class PendingGroupMemberAndContact {
        @Embedded(prefix = "pending_")
        public PendingGroupMember pendingGroupMember;

        @Embedded
        public Contact contact;
    }

}
