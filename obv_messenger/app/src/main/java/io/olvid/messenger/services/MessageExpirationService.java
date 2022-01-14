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

package io.olvid.messenger.services;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.engine.Logger;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.settings.SettingsActivity;

public class MessageExpirationService extends BroadcastReceiver {
    public static final String EXPIRE_MESSAGES_ACTION = "expire_messages";
    private static Long scheduledAlarmTimestamp = null;

    private static Timer expireTimer = null;
    private static TimerTask expireTimerTask = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (EXPIRE_MESSAGES_ACTION.equals(intent.getAction())) {
            App.runThread(() -> {
                wipeExpiredMessages();
                scheduleNextExpiration();
            });
        }
    }

    private static void wipeExpiredMessages() {
        AppDatabase db = AppDatabase.getInstance();
        List<MessageExpiration> messageExpirations = db.messageExpirationDao().getAllExpired(System.currentTimeMillis());
        Logger.d("Messages to delete " + messageExpirations.size());
        for (MessageExpiration messageExpiration : messageExpirations) {
            db.runInTransaction(() -> {
                Message message = db.messageDao().get(messageExpiration.messageId);
                if (message == null) {
                    return;
                }
                if (message.isInbound() || !messageExpiration.wipeOnly) {
                    message.delete(db);
                } else {
                    // only wipe message content, but still delete all attachments
                    db.messageExpirationDao().delete(messageExpiration);
                    message.wipe(db);
                    message.deleteAttachments(db);
                }
                AndroidNotificationManager.expireMessageNotification(message.discussionId, message.id);
            });
        }

        boolean appWideRetain = SettingsActivity.getDefaultRetainWipedOutboundMessages();

        // also check for wipe after read messages which we could have missed
        Long discussionId = AndroidNotificationManager.getCurrentShowingDiscussionId();
        for (Message message: db.messageDao().getAllWipeOnRead()) {
            if (discussionId == null || message.discussionId != discussionId) {
                if (message.isInbound()) {
                    db.runInTransaction(() -> message.delete(db));
                } else {
                    boolean retain;
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(message.discussionId);
                    if (discussionCustomization == null || discussionCustomization.prefRetainWipedOutboundMessages == null) {
                        retain = appWideRetain;
                    } else {
                        retain = discussionCustomization.prefRetainWipedOutboundMessages;
                    }
                    if (retain) {
                        db.runInTransaction(() -> {
                            message.wipe(db);
                            message.deleteAttachments(db);
                        });
                    } else {
                        db.runInTransaction(() -> message.delete(db));
                    }
                }
                AndroidNotificationManager.expireMessageNotification(message.discussionId, message.id);
            }
        }
    }

    public static void scheduleNextExpiration() {
        try {
            AppDatabase db = AppDatabase.getInstance();
            Long nextExpirationTimestamp = db.messageExpirationDao().getNextExpiration();
            if (Objects.equals(scheduledAlarmTimestamp, nextExpirationTimestamp)) {
                // we already scheduled an alarm with the same timestamp, no need to cancel/re-schedule
                return;
            }
            scheduledAlarmTimestamp = nextExpirationTimestamp;

            if (scheduledAlarmTimestamp != null) {
                if (expireTimer == null) {
                    expireTimer = new Timer("MessageExpirationServiceTimer");
                }
                if (expireTimerTask != null) {
                    expireTimerTask.cancel();
                }
                expireTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        wipeExpiredMessages();
                        scheduleNextExpiration();
                    }
                };
                long delay = scheduledAlarmTimestamp - System.currentTimeMillis() + 10;  // we add a 10ms delay to ensure the message expire timestamp is passed
                if (delay < 0) {
                    delay = 0;
                }
                expireTimer.schedule(expireTimerTask, delay);
            }

            Intent expireIntent = new Intent(EXPIRE_MESSAGES_ACTION, null, App.getContext(), MessageExpirationService.class);
            PendingIntent pendingExpireIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingExpireIntent = PendingIntent.getBroadcast(App.getContext(), 0, expireIntent, PendingIntent.FLAG_MUTABLE);
            } else {
                pendingExpireIntent = PendingIntent.getBroadcast(App.getContext(), 0, expireIntent, 0);
            }

            AlarmManager alarmManager = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingExpireIntent);
                if (scheduledAlarmTimestamp != null) {
                    Logger.d("Scheduling wipe at " + scheduledAlarmTimestamp);
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduledAlarmTimestamp, pendingExpireIntent);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
