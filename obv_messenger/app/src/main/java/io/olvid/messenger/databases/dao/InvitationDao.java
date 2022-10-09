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
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;
import java.util.UUID;

import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.databases.entity.Invitation;

@Dao
public interface InvitationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Invitation... invitations);

    @Delete
    void delete(Invitation... invitations);

    @Query("SELECT * FROM " + Invitation.TABLE_NAME)
    List<Invitation> getAll();

    @Query("SELECT * FROM " + Invitation.TABLE_NAME +
            " WHERE " + Invitation.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY " + Invitation.INVITATION_TIMESTAMP + " DESC")
    LiveData<List<Invitation>> getAllForOwnedIdentity(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + Invitation.TABLE_NAME +
            " WHERE " + Invitation.DIALOG_UUID + " = :dialogUuid")
    Invitation getByDialogUuid(UUID dialogUuid);

    @Query("SELECT * FROM " + Invitation.TABLE_NAME +
            " WHERE " + Invitation.CATEGORY_ID + " = " + ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY +
            " OR " + Invitation.CATEGORY_ID + " = " + ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY)
    List<Invitation> getAllGroupInvites();

    @Query("SELECT * FROM " + Invitation.TABLE_NAME +
            " WHERE " + Invitation.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + Invitation.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + Invitation.CATEGORY_ID + " in ( " + ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY + ", " + ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY + ") " +
            " LIMIT 1")
    LiveData<Invitation> getContactOneToOneInvitation(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
}
