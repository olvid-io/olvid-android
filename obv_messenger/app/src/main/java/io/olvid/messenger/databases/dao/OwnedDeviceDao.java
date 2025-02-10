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
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.messenger.databases.entity.OwnedDevice;

@Dao
public interface OwnedDeviceDao {
    @Insert
    void insert(@NonNull OwnedDevice ownedDevice);

    @Delete
    void delete(@NonNull OwnedDevice ownedDevice);

    @Query("UPDATE " + OwnedDevice.TABLE_NAME +
            " SET " + OwnedDevice.DISPLAY_NAME + " = :displayName " +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + OwnedDevice.BYTES_DEVICE_UID + " = :bytesDeviceUid ")
    void updateDisplayName(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid, @Nullable String displayName);

    @Query("UPDATE " + OwnedDevice.TABLE_NAME +
            " SET " + OwnedDevice.CHANNEL_CONFIRMED + " = :channelConfirmed " +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + OwnedDevice.BYTES_DEVICE_UID + " = :bytesDeviceUid ")
    void updateChannelConfirmed(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid, boolean channelConfirmed);

    @Query("UPDATE " + OwnedDevice.TABLE_NAME +
            " SET " + OwnedDevice.HAS_PRE_KEY + " = :hasPreKey " +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + OwnedDevice.BYTES_DEVICE_UID + " = :bytesDeviceUid ")
    void updateHasPreKey(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid, boolean hasPreKey);

   @Query("UPDATE " + OwnedDevice.TABLE_NAME +
            " SET " + OwnedDevice.TRUSTED + " = :trusted " +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
           " AND " + OwnedDevice.BYTES_DEVICE_UID + " = :bytesDeviceUid ")
    void updateTrusted(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid, boolean trusted);

    @Query("UPDATE " + OwnedDevice.TABLE_NAME +
            " SET " + OwnedDevice.CURRENT_DEVICE + " = :currentDevice " +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + OwnedDevice.BYTES_DEVICE_UID + " = :bytesDeviceUid ")
    void updateCurrentDevice(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid, boolean currentDevice);

    @Query("UPDATE " + OwnedDevice.TABLE_NAME +
            " SET " + OwnedDevice.LAST_REGISTRATION_TIMESTAMP + " = :lastRegistrationTimestamp, " +
            OwnedDevice.EXPIRATION_TIMESTAMP + " = :expirationTimestamp " +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + OwnedDevice.BYTES_DEVICE_UID + " = :bytesDeviceUid ")
    void updateTimestamps(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid, @Nullable Long lastRegistrationTimestamp, @Nullable Long expirationTimestamp);


    @Query("SELECT * FROM " + OwnedDevice.TABLE_NAME +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + OwnedDevice.BYTES_DEVICE_UID + " = :bytesDeviceUid ")
    @Nullable OwnedDevice get(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid);

    @Query("SELECT * FROM " + OwnedDevice.TABLE_NAME +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " ORDER BY " + OwnedDevice.CURRENT_DEVICE + " DESC, " + OwnedDevice.DISPLAY_NAME + " ASC, " + OwnedDevice.BYTES_DEVICE_UID + " ASC ")
    LiveData<List<OwnedDevice>> getAllSorted(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + OwnedDevice.TABLE_NAME +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity ")
    List<OwnedDevice> getAllSync(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT EXISTS (SELECT 1 FROM " + OwnedDevice.TABLE_NAME +
            " WHERE " + OwnedDevice.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + OwnedDevice.CURRENT_DEVICE + " = 0 " +
            " AND (" + OwnedDevice.CHANNEL_CONFIRMED + " = 1 " +
            " OR " + OwnedDevice.HAS_PRE_KEY + " = 1))")
    boolean doesOwnedIdentityHaveAnotherDeviceWithChannel(@NonNull byte[] bytesOwnedIdentity);

}
