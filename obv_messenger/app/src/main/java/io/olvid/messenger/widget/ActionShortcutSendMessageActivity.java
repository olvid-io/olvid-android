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

package io.olvid.messenger.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;

public class ActionShortcutSendMessageActivity extends AppCompatActivity {
    public static final String APP_WIDGET_ID_INTENT_EXTRA = "app_widget_id";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int appWidgetId = getIntent().getIntExtra(APP_WIDGET_ID_INTENT_EXTRA, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        } else {
            App.runThread(() -> {
                ActionShortcutConfiguration actionShortcutConfiguration = AppDatabase.getInstance().actionShortcutConfigurationDao().getByAppWidgetId(appWidgetId);
                if (actionShortcutConfiguration != null) {
                    ActionShortcutConfiguration.JsonConfiguration configuration = actionShortcutConfiguration.getJsonConfiguration();
                    if (configuration != null) {
                        Discussion discussion = AppDatabase.getInstance().discussionDao().getById(actionShortcutConfiguration.discussionId);
                        if (discussion != null && discussion.active && !discussion.isLocked()) {
                            if (configuration.confirmBeforeSend) {
                                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_send_predefined_message)
                                        .setMessage("")
                                        .setPositiveButton(R.string.button_label_ok, null)
                                        .setNegativeButton(R.string.button_label_cancel, null)
                                        .setOnDismissListener((DialogInterface dialogInterface) -> finish());
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    AlertDialog dialog = builder.create();
                                    dialog.setOnShowListener((DialogInterface dialogInterface) -> {
                                        Button okButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                                        okButton.setOnClickListener((v) -> {
                                            dialog.setOnDismissListener(null);
                                            dialog.dismiss();
                                            App.runThread(() -> sendMessage(discussion, configuration));
                                        });
                                    });
                                    dialog.show();
                                });
                            } else {
                                sendMessage(discussion, configuration);
                            }
                            return;
                        }
                    }
                }
                finish();
            });
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    Long messageId = null;

    private void sendMessage(Discussion discussion, ActionShortcutConfiguration.JsonConfiguration configuration) {
        AppDatabase db = AppDatabase.getInstance();

        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
        final Message.JsonExpiration jsonExpiration;
        if (discussionCustomization != null) {
            jsonExpiration = discussionCustomization.getExpirationJson();
        } else {
            jsonExpiration = null;
        }

        db.runInTransaction(() -> {
            discussion.lastOutboundMessageSequenceNumber++;
            db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
            discussion.updateLastMessageTimestamp(System.currentTimeMillis());
            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);

            Message.JsonMessage jsonMessage = new Message.JsonMessage(configuration.messageToSend);
            if (jsonExpiration != null) {
                jsonMessage.setJsonExpiration(jsonExpiration);
            }
            Message message = new Message(
                    db,
                    discussion.lastOutboundMessageSequenceNumber,
                    jsonMessage,
                    null,
                    System.currentTimeMillis(),
                    Message.STATUS_UNPROCESSED,
                    Message.TYPE_OUTBOUND_MESSAGE,
                    discussion.id,
                    null,
                    discussion.bytesOwnedIdentity,
                    discussion.senderThreadIdentifier,
                    0,
                    0
            );
            message.id = db.messageDao().insert(message);
            // save the messageId for the vibrate observer
            messageId = message.id;

            message.post(false, false);
        });
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(50);
        }

        // Re-enable this code once a foreground service for message sending is implemented
        // for now, vibration in background is not possible, so we disable this option
//        if (configuration.vibrateOnSend && messageId != null) {
//            if (vibrator != null) {
//                // vibrate when the message status goes to send
//                LiveData<Message> messageLiveData = db.messageDao().getLive(messageId);
//                Observer<Message> messageObserver = new Observer<Message>() {
//                    @Override
//                    public void onChanged(Message message) {
//                        if (message.status == Message.STATUS_SENT || message.status == Message.STATUS_DELIVERED || message.status == Message.STATUS_DELIVERED_AND_READ) {
//                            long[] pattern = new long[]{0, 50, 100, 50};
//                            vibrator.vibrate(pattern, -1);
//                            messageLiveData.removeObserver(this);
//                        }
//                    }
//                };
//                Handler handler = new Handler(Looper.getMainLooper());
//                handler.post(() -> messageLiveData.observeForever(messageObserver));
//                handler.postDelayed(() -> messageLiveData.removeObserver(messageObserver), 20_000);
//            }
//        }
        finish();
    }
}
