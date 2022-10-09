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

package io.olvid.messenger.main;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.UUID;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class InvitationListViewModel extends ViewModel {
    private final LiveData<List<Invitation>> invitations;
    private @Nullable String lastSas;
    private UUID lastSasDialogUUID;

    public InvitationListViewModel() {
        invitations = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().invitationDao().getAllForOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
        });
    }

    public LiveData<List<Invitation>> getInvitations() {
        return invitations;
    }

    public void setLastSas(@Nullable String lastSas, UUID uuid) {
        this.lastSas = lastSas;
        this.lastSasDialogUUID = uuid;
    }

    @Nullable
    public String getLastSas() {
        return lastSas;
    }

    public UUID getLastSasDialogUUID() {
        return lastSasDialogUUID;
    }
}
