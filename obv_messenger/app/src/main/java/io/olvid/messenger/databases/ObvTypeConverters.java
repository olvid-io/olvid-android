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

package io.olvid.messenger.databases;

import androidx.room.TypeConverter;

import java.util.UUID;

import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.AppSingleton;

public class ObvTypeConverters {

    // region UUID converters
    @TypeConverter
    public static UUID fromString(String uuidString) {
        if (uuidString == null) {
            return null;
        }
        return UUID.fromString(uuidString);
    }

    @TypeConverter
    public static String uuidToString(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return uuid.toString();
    }
    // endregion



    // region ObvDialog converters
    @TypeConverter
    public static byte[] obvDialogToBytes(ObvDialog obvDialog) {
        if (obvDialog == null) {
            return null;
        }
        return obvDialog.encode(AppSingleton.getJsonObjectMapper()).getBytes();
    }

    @TypeConverter
    public static ObvDialog obvDialogFromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return ObvDialog.of(new Encoded(bytes), AppSingleton.getJsonObjectMapper());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    // endregion

}
