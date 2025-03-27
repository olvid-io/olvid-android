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
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.Group2PendingMember;

@Dao
public interface Group2PendingMemberDao {
    @Insert
    void insert(@NonNull Group2PendingMember pendingMember);

    @Delete
    void delete(@NonNull Group2PendingMember pendingMember);

    @Update
    void update(@NonNull Group2PendingMember pendingMember);

    @Query("UPDATE " + Group2PendingMember.TABLE_NAME +
            " SET " + Group2PendingMember.FIRST_NAME + " = :firstName " +
            " WHERE " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Group2PendingMember.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    void updateFirstName(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity, @Nullable String firstName);

    @Query("SELECT * FROM " + Group2PendingMember.TABLE_NAME +
            " WHERE " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier " +
            " AND " + Group2PendingMember.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity ")
    @Nullable Group2PendingMember get(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier, @NonNull byte[] bytesContactIdentity);

    @Query("SELECT * FROM " + Group2PendingMember.TABLE_NAME)
    List<Group2PendingMember> getAll();

    @Query("SELECT * FROM " + Group2PendingMember.TABLE_NAME +
            " WHERE " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2PendingMember.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier")
    List<Group2PendingMember> getGroupPendingMembers(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier);

    @Query("SELECT * FROM " + Group2PendingMember.TABLE_NAME +
            " WHERE " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    List<Group2PendingMember> getAll(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT COUNT(*) FROM " + Group2PendingMember.TABLE_NAME +
            " WHERE " + Group2PendingMember.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Group2PendingMember.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity")
    int countContactGroups(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity);
}
