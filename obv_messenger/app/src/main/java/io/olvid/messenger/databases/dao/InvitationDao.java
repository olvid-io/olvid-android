/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;
import java.util.UUID;

import io.olvid.messenger.databases.entity.Invitation;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public interface InvitationDao {
    @Insert(onConflict = REPLACE)
    void insert(Invitation... invitations);

    @Delete
    void delete(Invitation... invitations);

    @Query("SELECT * FROM " + Invitation.TABLE_NAME + " WHERE " + Invitation.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ORDER BY " + Invitation.INVITATION_TIMESTAMP + " DESC")
    LiveData<List<Invitation>> getAll(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + Invitation.TABLE_NAME + " WHERE " + Invitation.DIALOG_UUID + " = :dialogUuid")
    Invitation getByDialogUuid(UUID dialogUuid);

}
