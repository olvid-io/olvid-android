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

package io.olvid.messenger.databases.tasks;


import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.services.MessageExpirationService;
import io.olvid.messenger.settings.SettingsActivity;

public class InboundEphemeralMessageClicked implements Runnable {
    private final long messageId;

    public InboundEphemeralMessageClicked(long messageId) {
        this.messageId = messageId;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Message message = db.messageDao().get(messageId);
        if (message == null || message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
            return;
        }

        Message.JsonExpiration jsonExpiration;
        try {
            jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(message.jsonExpiration, Message.JsonExpiration.class);
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

        boolean expirationCreated = db.runInTransaction(() -> {
            message.messageType = Message.TYPE_INBOUND_MESSAGE;

            if (jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce()) {
                message.wipeStatus = Message.WIPE_STATUS_WIPE_ON_READ;
            }

            if (message.status == Message.STATUS_READ) {
                DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(message.discussionId);
                boolean sendReadReceipt;
                if (discussionCustomization != null && discussionCustomization.prefSendReadReceipt != null) {
                    sendReadReceipt = discussionCustomization.prefSendReadReceipt;
                } else {
                    sendReadReceipt = SettingsActivity.getDefaultSendReadReceipt();
                }
                if (sendReadReceipt) {
                    Discussion discussion = db.discussionDao().getById(message.discussionId);
                    App.runThread(() -> message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_READ));
                }
                App.runThread(new CreateReadMessageMetadata(message.id));
            }
            db.messageDao().update(message);

            if (jsonExpiration.getVisibilityDuration() != null) {
                long expirationTimestamp = System.currentTimeMillis() + jsonExpiration.getVisibilityDuration() * 1_000L;
                MessageExpiration messageExpiration = new MessageExpiration(message.id, expirationTimestamp, true);
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
