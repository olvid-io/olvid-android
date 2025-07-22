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

import io.olvid.engine.Logger
import io.olvid.engine.engine.Engine
import io.olvid.engine.engine.types.ObvMessage
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.jsons.JsonPayload
import kotlinx.coroutines.Runnable


class HandleNewMessageTask(
    private val engine: Engine,
    private val obvMessage: ObvMessage,
    private val db: AppDatabase,
): Runnable {
    override fun run() {
        try {
            when (processObvMessage(db, engine, obvMessage)) {
                HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS -> engine.deleteMessageAndAttachments(
                    obvMessage.bytesToIdentity,
                    obvMessage.identifier
                )

                HandleMessageOutput.MARK_MESSAGE_FOR_DELETION -> engine.markMessageForDeletion(
                    obvMessage.bytesToIdentity,
                    obvMessage.identifier
                )

                HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION,
                HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_MESSAGE -> engine.markMessageAsOnHold(
                    obvMessage.bytesToIdentity,
                    obvMessage.identifier
                )

                HandleMessageOutput.DO_NOTHING -> Unit
            }
        } catch (e: Exception) {
            Logger.e("Exception while handling a new message")
            Logger.x(e)
        }
    }
}

class ProcessReadyToProcessOnHoldMessagesTask(
    private val engine: Engine,
    private val db: AppDatabase,
    private val bytesOwnedIdentity: ByteArray?
) : Runnable {
    override fun run() {
        (bytesOwnedIdentity?.let {
            db.onHoldInboxMessageDao().getAllReadyToProcessForOwnedIdentity(bytesOwnedIdentity)
        } ?: db.onHoldInboxMessageDao().getAllReadyToProcess())
            .also {
                if (it.isNotEmpty()) {
                    Logger.i("⏸️ Found ${it.size} on hold messages to process")
                }
            }
            .forEach { onHoldInboxMessage ->
                try {
                    engine.getOnHoldMessage(bytesOwnedIdentity, onHoldInboxMessage.messageEngineIdentifier)
                        ?.also { obvMessage ->
                            when (processObvMessage(db, engine, obvMessage)) {
                                HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS -> {
                                    engine.deleteMessageAndAttachments(
                                        obvMessage.bytesToIdentity,
                                        obvMessage.identifier
                                    )

                                    // message was successfully processed -> delete the onHoldInboxMessage
                                    db.onHoldInboxMessageDao().delete(onHoldInboxMessage)
                                }

                                HandleMessageOutput.MARK_MESSAGE_FOR_DELETION -> {
                                    engine.markMessageForDeletion(
                                        obvMessage.bytesToIdentity,
                                        obvMessage.identifier
                                    )

                                    // message was successfully processed -> delete the onHoldInboxMessage
                                    db.onHoldInboxMessageDao().delete(onHoldInboxMessage)
                                }

                                HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_DISCUSSION -> {
                                    if (onHoldInboxMessage.waitingForMessage) {
                                        Logger.i("⏸️ A message that was on hold for a message is now on hold for a discussion, this is rather unusual!")
                                        // we were waiting for a message, and now we are waiting for the discussion itself!
                                        // --> this should not happen often, but we delete our onHoldInboxMessage anyway as a new one was created
                                        db.onHoldInboxMessageDao().delete(onHoldInboxMessage)
                                    } else {
                                        // we were already waiting for discussion, so the onHoldInboxMessage during processObvMessage did nothing
                                        // --> we reset the readyToProcess
                                        onHoldInboxMessage.readyToProcess = false
                                        db.onHoldInboxMessageDao().update(onHoldInboxMessage)
                                    }
                                }

                                HandleMessageOutput.PUT_MESSAGE_ON_HOLD_FOR_MESSAGE -> {
                                    if (onHoldInboxMessage.waitingForMessage) {
                                        // we were already waiting for a message, so the onHoldInboxMessage during processObvMessage did nothing
                                        // --> we reset the readyToProcess
                                        onHoldInboxMessage.readyToProcess = false
                                        db.onHoldInboxMessageDao().update(onHoldInboxMessage)
                                    } else {
                                        Logger.i("⏸️ A message that was on hold for a discussion is now on hold for a message")
                                        // we were waiting for discussion before
                                        // --> delete our old onHoldInboxMessage as a new one was created
                                        db.onHoldInboxMessageDao().delete(onHoldInboxMessage)
                                    }
                                }

                                HandleMessageOutput.DO_NOTHING -> Unit // do nothing 😁
                            }
                        } ?: run {
                        // could not retrieve engine message --> delete the onHoldInboxMessage
                        db.onHoldInboxMessageDao().delete(onHoldInboxMessage)
                    }
                } catch (e: Exception) {
                    Logger.e("Exception while processing on hold messages")
                    Logger.x(e)
                }
            }
    }
}


private fun processObvMessage(
    db: AppDatabase,
    engine: Engine,
    obvMessage: ObvMessage,
): HandleMessageOutput {
    val count = db.messageDao().getCountForEngineIdentifier(obvMessage.bytesToIdentity, obvMessage.identifier)
    if (count > 0) {
        // content was already inserted in database
        return HandleMessageOutput.MARK_MESSAGE_FOR_DELETION
    }

    try {
        val messagePayload: JsonPayload
        try {
            messagePayload = AppSingleton.getJsonObjectMapper().readValue(obvMessage.messagePayload, JsonPayload::class.java)
        } catch (_: Exception) {
            Logger.e("Received a message that cannot be deserialized! Deleting it...")
            return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
        }

        // special handling of WebRTC messages
        messagePayload.jsonWebrtcMessage?.let { jsonWebrtcMessage ->
            App.handleWebrtcMessage(obvMessage.bytesToIdentity, obvMessage.bytesFromIdentity, obvMessage.bytesFromDeviceUid, jsonWebrtcMessage, obvMessage.downloadTimestamp + (System.currentTimeMillis() - obvMessage.localDownloadTimestamp).coerceAtLeast(0), obvMessage.serverTimestamp)
            return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
        }

        val messageSender = MessageSender.of(db, obvMessage.bytesToIdentity, obvMessage.bytesFromIdentity)
        if (messageSender == null) {
            // message sender can only be null if the message was sent by an unknown contact, encrypted with a pre-key.
            // The engine already takes care of putting such messages on hold, so this should only happen in edge cases
            // where the DBs are not in sync, or a contact is deleted immediately after being created.
            Logger.e("Received a message from an unknown contact!!!")
            return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS
        }

        messagePayload.jsonQuerySharedSettings?.let { jsonQuerySharedSettings ->
            return handleQuerySharedSettings(db, jsonQuerySharedSettings, messageSender)
        }

        messagePayload.jsonSharedSettings?.let { jsonSharedSettings ->
            return handleSharedSettings(db, jsonSharedSettings, messageSender, obvMessage)
        }

        messagePayload.jsonDeleteDiscussion?.let { jsonDeleteDiscussion ->
            return handleDeleteDiscussion(db, jsonDeleteDiscussion, messageSender, obvMessage)
        }

        messagePayload.jsonDeleteMessages?.let { jsonDeleteMessages ->
            return handleDeleteMessages(db, jsonDeleteMessages, messageSender, obvMessage)
        }

        messagePayload.jsonUpdateMessage?.let { jsonUpdateMessage ->
            return handleUpdateMessage(db, jsonUpdateMessage, messageSender, obvMessage)
        }

        messagePayload.jsonReaction?.let { jsonReaction ->
            return handleReaction(db, jsonReaction, messageSender, obvMessage)
        }

        messagePayload.jsonPollVote?.let { jsonPollVote ->
            return handlePollVote(db, jsonPollVote, messageSender, obvMessage)
        }

        messagePayload.jsonScreenCaptureDetection?.let { jsonScreenCaptureDetection ->
            return handleScreenCaptureDetection(db, jsonScreenCaptureDetection, messageSender, obvMessage)
        }

        messagePayload.jsonDiscussionRead?.let { jsonDiscussionRead ->
            return handleDiscussionRead(db, jsonDiscussionRead, messageSender, obvMessage)
        }

        messagePayload.jsonLimitedVisibilityMessageOpened?.let { jsonLimitedVisibilityMessageOpened ->
            return handleLimitedVisibilityMessageOpened(db, jsonLimitedVisibilityMessageOpened, messageSender, obvMessage)
        }

        messagePayload.jsonMessage?.takeIf {
            !it.isEmpty || obvMessage.attachments.isNotEmpty()
        }?.let { jsonMessage ->
            return handleNormalMessage(db, jsonMessage, messagePayload.jsonReturnReceipt, messageSender, obvMessage, engine)
        }

        // if none of the previous handlers was called, the message is empty (or only contains fields this app does not yet understand)
        // --> the inbox message can simply be deleted
        return HandleMessageOutput.DELETE_MESSAGE_AND_ATTACHMENTS

    } catch (e: Exception) {
        Logger.x(e)

        // in case of exception, do nothing, try again at the next app startup
        return HandleMessageOutput.DO_NOTHING
    }
}
