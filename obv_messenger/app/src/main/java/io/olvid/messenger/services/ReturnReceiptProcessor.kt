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

package io.olvid.messenger.services

import io.olvid.engine.Logger
import io.olvid.engine.engine.Engine
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.tasks.HandleReceiveReturnReceipt
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue


object ReturnReceiptProcessor {
    val db: AppDatabase = AppDatabase.getInstance()
    val engine: Engine = AppSingleton.getEngine()
    val queue: BlockingQueue<EngineReturnReceipt> = ArrayBlockingQueue(5_000)
    val processingThread = Thread {
        while(true) {
            val engineReturnReceipt: EngineReturnReceipt
            try {
                engineReturnReceipt = queue.take()
            } catch (e: InterruptedException) {
                Logger.x(e)
                continue
            }

            HandleReceiveReturnReceipt(
                db,
                engine,
                engineReturnReceipt.bytesOwnedIdentity,
                engineReturnReceipt.serverUid,
                engineReturnReceipt.returnReceiptNonce,
                engineReturnReceipt.encryptedPayload,
                engineReturnReceipt.timestamp
            ).run()
        }
    }

    init {
        processingThread.start()
    }

    fun processReturnReceipt(
        bytesOwnedIdentity: ByteArray,
        serverUid: ByteArray,
        returnReceiptNonce: ByteArray,
        encryptedPayload: ByteArray,
        timestamp: Long
    ) {
        try {
            queue.put(EngineReturnReceipt(
                bytesOwnedIdentity = bytesOwnedIdentity,
                serverUid = serverUid,
                returnReceiptNonce = returnReceiptNonce,
                encryptedPayload = encryptedPayload,
                timestamp = timestamp
            ))
        } catch (e: InterruptedException) {
            Logger.x(e)
        }
    }

    @Suppress("ArrayInDataClass")
    data class EngineReturnReceipt(
        val bytesOwnedIdentity: ByteArray,
        val serverUid: ByteArray,
        val returnReceiptNonce: ByteArray,
        val encryptedPayload: ByteArray,
        val timestamp: Long,
    )
}