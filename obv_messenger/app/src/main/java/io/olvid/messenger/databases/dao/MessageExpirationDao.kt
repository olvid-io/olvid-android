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
package io.olvid.messenger.databases.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import io.olvid.messenger.databases.entity.MessageExpiration

@Dao
interface MessageExpirationDao {
    @Insert
    fun insert(messageExpiration: MessageExpiration): Long

    @Delete
    fun delete(messageExpiration: MessageExpiration)

    @Query("SELECT * FROM " + MessageExpiration.TABLE_NAME + " WHERE " + MessageExpiration.EXPIRATION_TIMESTAMP + " <= :currentTimeMillis")
    fun getAllExpired(currentTimeMillis: Long): List<MessageExpiration>

    @Query("SELECT MIN(" + MessageExpiration.EXPIRATION_TIMESTAMP + ") FROM " + MessageExpiration.TABLE_NAME)
    fun getNextExpiration() : Long?

    @Query("SELECT * FROM " + MessageExpiration.TABLE_NAME + " WHERE " + MessageExpiration.MESSAGE_ID + " = :messageId ORDER BY " + MessageExpiration.EXPIRATION_TIMESTAMP + " ASC LIMIT 1")
    fun get(messageId: Long): MessageExpiration?

    @Query("SELECT * FROM " + MessageExpiration.TABLE_NAME + " WHERE " + MessageExpiration.MESSAGE_ID + " = :messageId ORDER BY " + MessageExpiration.EXPIRATION_TIMESTAMP + " ASC LIMIT 1")
    fun getLive(messageId: Long): LiveData<MessageExpiration?>

    @Query("DELETE FROM " + MessageExpiration.TABLE_NAME + " WHERE " + MessageExpiration.MESSAGE_ID + " = :messageId AND " + MessageExpiration.WIPE_ONLY + " = 1")
    fun deleteWipeExpiration(messageId: Long)
}
