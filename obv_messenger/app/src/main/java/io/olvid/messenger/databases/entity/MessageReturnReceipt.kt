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
package io.olvid.messenger.databases.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = MessageReturnReceipt.TABLE_NAME,
    indices = [Index(
        MessageReturnReceipt.RETURN_RECEIPT_NONCE
    )]
)
data class MessageReturnReceipt(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = RETURN_RECEIPT_NONCE) val nonce: ByteArray,
    @ColumnInfo(name = RETURN_RECEIPT_PAYLOAD) var payload: ByteArray,
    @ColumnInfo(name = RETURN_RECEIPT_TIMESTAMP) val timestamp: Long,
) {
    companion object {
        const val TTL = 15 * 86_400_000L // keep return receipts in the table for 15 days.
        const val TABLE_NAME: String = "message_return_receipt_table"
        const val RETURN_RECEIPT_NONCE: String = "nonce"
        const val RETURN_RECEIPT_PAYLOAD: String = "payload"
        const val RETURN_RECEIPT_TIMESTAMP: String = "timestamp"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageReturnReceipt) return false

        if (!nonce.contentEquals(other.nonce)) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
