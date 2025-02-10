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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import io.olvid.messenger.databases.entity.MessageReturnReceipt

@Dao
interface MessageReturnReceiptDao {
    @Insert
    fun insert(messageReturnReceipt: MessageReturnReceipt): Long

    @Delete
    fun delete(messageReturnReceipt: MessageReturnReceipt)

    @Query("SELECT * FROM " + MessageReturnReceipt.TABLE_NAME + " WHERE " + MessageReturnReceipt.RETURN_RECEIPT_NONCE + " = :nonce ")
    fun getAllByNonce(nonce: ByteArray): List<MessageReturnReceipt>

    @Query("DELETE FROM " + MessageReturnReceipt.TABLE_NAME + " WHERE " + MessageReturnReceipt.RETURN_RECEIPT_TIMESTAMP + " < :timestamp ")
    fun deleteOlderThan(timestamp: Long)
}
