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
package io.olvid.messenger.databases

import android.graphics.Rect
import androidx.room.TypeConverter
import io.olvid.engine.Logger
import io.olvid.engine.encoder.Encoded
import io.olvid.engine.engine.types.ObvDialog
import io.olvid.messenger.AppSingleton
import java.util.UUID


class Converters {
    // region Rect (for text blocks bounding boxes)
    @TypeConverter
    fun fromRect(value: Rect): String {
        return value.flattenToString()
    }

    @TypeConverter
    fun stringToRect(value: String?): Rect? {
        return Rect.unflattenFromString(value)
    }
    // endregion

    // region UUID converters
    @TypeConverter
    fun fromUuid(uuid: UUID?): String? {
        return uuid?.let { Logger.getUuidString(it) }
    }

    @TypeConverter
    fun stringToUuid(uuidString: String?): UUID? {
        return uuidString?.let { UUID.fromString(it) }
    }
    // endregion

    // region ObvDialog converters
    @TypeConverter
    fun obvDialogToBytes(obvDialog: ObvDialog?): ByteArray? {
        return obvDialog?.encode(AppSingleton.getJsonObjectMapper())?.bytes
    }

    @TypeConverter
    fun obvDialogFromBytes(bytes: ByteArray?): ObvDialog? {
        if (bytes == null) {
            return null
        }
        try {
            return ObvDialog.of(Encoded(bytes), AppSingleton.getJsonObjectMapper())
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    // endregion
}
