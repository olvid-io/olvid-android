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

package io.olvid.messenger.databases.tasks.new_message

import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact


data class MessageSender(
    val type: Type,
    val contact: Contact?,
    val bytesOwnedIdentity: ByteArray
) {
    enum class Type {
        CONTACT,
        OWNED_IDENTITY,
    }

    val senderIdentity: ByteArray
        get() {
            return when (type) {
                Type.CONTACT -> contact!!.bytesContactIdentity
                Type.OWNED_IDENTITY -> bytesOwnedIdentity
            }
        }

    companion object {
        fun of(
            db: AppDatabase,
            bytesOwnedIdentity: ByteArray,
            bytesContactIdentity: ByteArray
        ): MessageSender? {
            return if (bytesOwnedIdentity.contentEquals(bytesContactIdentity)) {
                MessageSender(Type.OWNED_IDENTITY, null, bytesOwnedIdentity)
            } else {
                val contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity)
                if (contact != null) {
                    MessageSender(Type.CONTACT, contact, bytesOwnedIdentity)
                } else {
                    null
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageSender) return false

        if (type != other.type) return false
        if (contact != other.contact) return false
        if (!bytesOwnedIdentity.contentEquals(other.bytesOwnedIdentity)) return false
        if (!senderIdentity.contentEquals(other.senderIdentity)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (contact?.hashCode() ?: 0)
        result = 31 * result + bytesOwnedIdentity.contentHashCode()
        result = 31 * result + senderIdentity.contentHashCode()
        return result
    }


}