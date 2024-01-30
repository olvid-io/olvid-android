/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.databases.tasks;


import androidx.annotation.NonNull;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.services.MessageExpirationService;
import io.olvid.messenger.settings.SettingsActivity;

public class InboundEphemeralMessageClicked implements Runnable {
    @NonNull
    private final byte[] bytesOwnedIdentity;
    private final long messageId;
    private Message message;
    private final long alreadyElapsedDelay;
    private final boolean clickedOnAnotherDevice;

    public InboundEphemeralMessageClicked(@NonNull byte[] bytesOwnedIdentity, long messageId) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.messageId = messageId;
        this.message = null;
        this.alreadyElapsedDelay = 0;
        this.clickedOnAnotherDevice = false;
    }

    public InboundEphemeralMessageClicked(@NonNull byte[] bytesOwnedIdentity, @NonNull Message message, long alreadyElapsedDelay) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.messageId = message.id;
        this.message = message;
        this.alreadyElapsedDelay = alreadyElapsedDelay;
        this.clickedOnAnotherDevice = true;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        if (message == null) {
            message = db.messageDao().get(messageId);
        }
        if (message == null || message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
            return;
        }

        JsonExpiration jsonExpiration;
        try {
            jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(message.jsonExpiration, JsonExpiration.class);
            if (jsonExpiration.getVisibilityDuration() == null && (jsonExpiration.getReadOnce() == null || !jsonExpiration.getReadOnce())) {
                // this should never happen
                Logger.e("Found ephemeral message with null delay and no read once");
                return;
            }
        } catch (Exception e) {
            Logger.e("Unable to parse jsonExpiration in ephemeral inbound message.");
            e.printStackTrace();
            return;
        }

        if (!clickedOnAnotherDevice && AppDatabase.getInstance().ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(bytesOwnedIdentity)) {
            Discussion discussion = db.discussionDao().getById(message.discussionId);
            if (discussion != null) {
                Message.postLimitedVisibilityMessageOpenedMessage(discussion, message);
            }
        }

        boolean expirationCreated = db.runInTransaction(() -> {
            if (!clickedOnAnotherDevice) {
                // reveal message only for clicked device
                 message.messageType = Message.TYPE_INBOUND_MESSAGE;
            }

            if (jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce()) {
                if (clickedOnAnotherDevice) {
                    message.delete(db);
                    return false;
                } else {
                    message.wipeStatus = Message.WIPE_STATUS_WIPE_ON_READ;
                }
            }

            if (message.status == Message.STATUS_READ) {
                DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(message.discussionId);
                boolean sendReadReceipt;
                if (discussionCustomization != null && discussionCustomization.prefSendReadReceipt != null) {
                    sendReadReceipt = discussionCustomization.prefSendReadReceipt;
                } else {
                    sendReadReceipt = SettingsActivity.getDefaultSendReadReceipt();
                }
                App.runThread(() -> {
                    if (sendReadReceipt) {
                        Discussion discussion = db.discussionDao().getById(message.discussionId);
                        message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_READ);
                    }
                    new CreateReadMessageMetadata(message.id).run();
                });
            }
            db.messageDao().update(message);

            if (jsonExpiration.getVisibilityDuration() != null) {
                long expirationTimestamp = System.currentTimeMillis() + jsonExpiration.getVisibilityDuration() * 1_000L - alreadyElapsedDelay;
                MessageExpiration messageExpiration = new MessageExpiration(message.id, expirationTimestamp, true);
                if (clickedOnAnotherDevice) {
                    // start expiration timer on other devices inbound ephemeral message
                    db.messageDao().updateExpirationStartTimestamp(message.id, expirationTimestamp);
                }
                db.messageExpirationDao().insert(messageExpiration);
                return true;
            } else {
                return false;
            }
        });

        if (expirationCreated) {
            App.runThread(MessageExpirationService::scheduleNextExpiration);
        }
    }
}
