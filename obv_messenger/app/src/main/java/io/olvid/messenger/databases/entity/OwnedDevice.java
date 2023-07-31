/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.databases.entity;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import java.util.Arrays;

import io.olvid.engine.Logger;
import io.olvid.messenger.R;

@Entity(
        tableName = OwnedDevice.TABLE_NAME,
        primaryKeys = {OwnedDevice.BYTES_OWNED_IDENTITY, OwnedDevice.BYTES_DEVICE_UID},
        foreignKeys = {
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = OwnedDevice.BYTES_OWNED_IDENTITY,
                        onDelete = ForeignKey.CASCADE)
        }
)
public class OwnedDevice {
    public static final String TABLE_NAME = "owned_device_table";

    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_DEVICE_UID = "bytes_device_uid";
    public static final String DISPLAY_NAME = "display_name";
    public static final String CURRENT_DEVICE = "current_device";
    public static final String CHANNEL_CONFIRMED = "channel_confirmed";
    public static final String LAST_REGISTRATION_TIMESTAMP = "last_registration_timestamp";
    public static final String EXPIRATION_TIMESTAMP = "expiration_timestamp";


    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = BYTES_DEVICE_UID)
    @NonNull
    public byte[] bytesDeviceUid;

    @ColumnInfo(name = DISPLAY_NAME)
    @Nullable
    public String displayName;

    @ColumnInfo(name = CURRENT_DEVICE)
    public boolean currentDevice;

    @ColumnInfo(name = CHANNEL_CONFIRMED)
    public boolean channelConfirmed;

    @ColumnInfo(name = LAST_REGISTRATION_TIMESTAMP)
    @Nullable
    public Long lastRegistrationTimestamp;

    @ColumnInfo(name = EXPIRATION_TIMESTAMP)
    @Nullable
    public Long expirationTimestamp;


    // Constructor required by Room
    public OwnedDevice(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesDeviceUid, @Nullable String displayName, boolean currentDevice, boolean channelConfirmed, @Nullable Long lastRegistrationTimestamp, @Nullable Long expirationTimestamp) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesDeviceUid = bytesDeviceUid;
        this.displayName = displayName;
        this.currentDevice = currentDevice;
        this.channelConfirmed = channelConfirmed;
        this.lastRegistrationTimestamp = lastRegistrationTimestamp;
        this.expirationTimestamp = expirationTimestamp;
    }

    @NonNull public String getDisplayNameOrDeviceHexName(Context context) {
        if (displayName != null) {
            return displayName;
        }
        return context.getString(R.string.text_device_xxxx, Logger.toHexString(Arrays.copyOfRange(bytesDeviceUid,0, 2)));
    }
}
