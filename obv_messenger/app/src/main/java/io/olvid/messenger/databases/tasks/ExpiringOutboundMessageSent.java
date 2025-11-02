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

package io.olvid.messenger.databases.tasks;


import java.util.Collections;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.services.MessageExpirationService;
import io.olvid.messenger.settings.SettingsActivity;

public class ExpiringOutboundMessageSent implements Runnable {
    private final Message message;

    public ExpiringOutboundMessageSent(Message message) {
        this.message = message;
    }

    @Override
    public void run() {
        if (message == null || message.jsonExpiration == null || message.expirationStartTimestamp != 0) {
            return;
        }
        JsonExpiration jsonExpiration;
        try {
            jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(message.jsonExpiration, JsonExpiration.class);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        AppDatabase db = AppDatabase.getInstance();
        boolean sendExpirationIntent = false;

        //////////
        // what is here does not apply to messages that are already remote deleted or wiped
        //////////
        if (message.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED) {

            // we have an ephemeral message --> check the discussion settings and the currently opened discussion to see how to handle this
            Discussion discussion = db.discussionDao().getById(message.discussionId);
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(message.discussionId);

            boolean retain;
            if (discussionCustomization != null && discussionCustomization.prefRetainWipedOutboundMessages != null) {
                retain = discussionCustomization.prefRetainWipedOutboundMessages;
            } else {
                retain = SettingsActivity.getDefaultRetainWipedOutboundMessages();
            }


            boolean alreadyWiped = message.wipeStatus == Message.WIPE_STATUS_WIPED;
            // readOnce
            if (jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce()) {
                // read once message --> if discussion is currently opened, mark as wipe_on_read, else wipe it directly
                if (AndroidNotificationManager.getCurrentShowingDiscussionId() != null && AndroidNotificationManager.getCurrentShowingDiscussionId() == discussion.id) {
                    // discussion is visible, only mark for wipe
                    db.messageDao().updateWipeStatus(message.id, Message.WIPE_STATUS_WIPE_ON_READ);
                } else {
                    alreadyWiped = true;
                    // discussion not showing --> wipe it or delete it depending on setting
                    if (retain) {
                        db.runInTransaction(() -> {
                            Message reMessage = db.messageDao().get(message.id);
                            if (reMessage != null) {
                                reMessage.wipe(db);
                                reMessage.deleteAttachments(db);
                            }
                        });
                    } else {
                        new DeleteMessagesTask(Collections.singletonList(message.id), SecureDeleteEverywhereDialogBuilder.DeletionChoice.LOCAL).run();
                    }
                }
            }


            // visibilityDuration
            if (jsonExpiration.getVisibilityDuration() != null && !alreadyWiped) {
                sendExpirationIntent = true;
                MessageExpiration messageExpiration = new MessageExpiration(0, message.id, System.currentTimeMillis() + jsonExpiration.getVisibilityDuration() * 1000L, retain);
                db.messageExpirationDao().insert(messageExpiration);
            }
        }

        // existenceDuration
        if (jsonExpiration.getExistenceDuration() != null) {
            sendExpirationIntent = true;
            MessageExpiration messageExpiration = new MessageExpiration(0, message.id, System.currentTimeMillis() + jsonExpiration.getExistenceDuration()*1000L, false);
            db.messageExpirationDao().insert(messageExpiration);
        }

        if (sendExpirationIntent) {
            message.expirationStartTimestamp = System.currentTimeMillis();
            db.messageDao().updateExpirationStartTimestamp(message.id, message.expirationStartTimestamp);
            App.runThread(MessageExpirationService::scheduleNextExpiration);
        }
    }
}
