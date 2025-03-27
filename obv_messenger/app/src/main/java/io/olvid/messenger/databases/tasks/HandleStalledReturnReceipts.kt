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
package io.olvid.messenger.databases.tasks

import io.olvid.engine.engine.Engine
import io.olvid.messenger.databases.AppDatabase


class HandleStalledReturnReceipts(
    private val engine: Engine,
    private val bytesOwnedIdentity: ByteArray,
    private val returnReceiptNonce: ByteArray,
    private val returnReceiptKey: ByteArray
) :
    Runnable {
    override fun run() {
        val db = AppDatabase.getInstance()
        db.messageReturnReceiptDao().getAllByNonce(returnReceiptNonce)
            .forEach { returnReceipt ->
                engine.decryptReturnReceipt(returnReceiptKey, returnReceipt.payload)
                    ?.process(
                        bytesOwnedIdentity,
                        returnReceiptNonce,
                        returnReceiptKey,
                        returnReceipt.timestamp
                    )
                    ?.also {
                        db.messageReturnReceiptDao().delete(returnReceipt)
                    }
            }
    }
}