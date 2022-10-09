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

package io.olvid.messenger.discussion;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.tasks.UpdateMessageBodyTask;
import io.olvid.messenger.settings.SettingsActivity;

public class Utils {
    private static final Calendar cal1 = Calendar.getInstance();
    private static final Calendar cal2 = Calendar.getInstance();

    static boolean notTheSameDay(long timestamp1, long timestamp2) {
        cal1.setTimeInMillis(timestamp1);
        cal2.setTimeInMillis(timestamp2);
        return cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR) || cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR);
    }

    static void launchModifyMessagePopup(FragmentActivity activity, Message message) {
        if (message != null
                && message.messageType == Message.TYPE_OUTBOUND_MESSAGE
                && message.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED
                && message.wipeStatus != Message.WIPE_STATUS_WIPED
                && !message.isLocationMessage()) {
            View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_edit_message, null);
            final TextInputEditText editMessageTextView = dialogView.findViewById(R.id.edit_message_text_view);
            if (SettingsActivity.useKeyboardIncognitoMode()) {
                editMessageTextView.setImeOptions(editMessageTextView.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            }
            editMessageTextView.setText(message.contentBody);

            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_edit_message)
                    .setView(dialogView)
                    .setPositiveButton(R.string.button_label_update, (DialogInterface dialog, int which) -> App.runThread(new UpdateMessageBodyTask(message.id, (editMessageTextView.getText() == null) ? "" : editMessageTextView.getText().toString().trim())))
                    .setNegativeButton(R.string.button_label_cancel, null);
            AlertDialog alertDialog = builder.create();
            alertDialog.setOnShowListener(dialog -> {
                Window window = ((AlertDialog) dialog).getWindow();
                if (window != null) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }

                final Button button = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                button.setEnabled(false);
                if (message.contentBody != null) {
                    try {
                        editMessageTextView.setSelection(message.contentBody.length());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> imm.showSoftInput(editMessageTextView, InputMethodManager.SHOW_IMPLICIT), 200);
                }

                editMessageTextView.requestFocus();

                editMessageTextView.addTextChangedListener(new TextChangeListener() {
                    final String oldBody = message.contentBody;

                    @Override
                    public void afterTextChanged(Editable s) {
                        String trimmed = s.toString().trim();
                        if (trimmed.length() == 0 || trimmed.equals(oldBody)) {
                            if (button.isEnabled()) {
                                button.setEnabled(false);
                            }
                        } else {
                            if (!button.isEnabled()) {
                                button.setEnabled(true);
                            }
                        }
                    }
                });
            });
            alertDialog.show();
        }
    }

    static void openForwardMessageDialog(FragmentActivity activity, @NonNull List<Long> selectedMessageIds, @Nullable Runnable openDialogCallback) {
        Runnable runnable = () -> {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            if (prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION, false)) {
                if (openDialogCallback != null) {
                    openDialogCallback.run();
                }
                ForwardMessagesDialogFragment forwardMessagesDialogFragment = ForwardMessagesDialogFragment.newInstance();
                forwardMessagesDialogFragment.show(activity.getSupportFragmentManager(), "ForwardMessagesDialogFragment");
            } else {
                View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_view_message_and_checkbox, null);
                TextView message = dialogView.findViewById(R.id.dialog_message);
                message.setText(R.string.dialog_message_forward_message_explanation);
                CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION, isChecked);
                    editor.apply();
                });

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                builder.setTitle(R.string.dialog_title_forward_message_explanation)
                        .setView(dialogView)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_proceed, (dialog, which) -> {
                            if (openDialogCallback != null) {
                                openDialogCallback.run();
                            }
                            ForwardMessagesDialogFragment forwardMessageDialogFragment = ForwardMessagesDialogFragment.newInstance();
                            forwardMessageDialogFragment.show(activity.getSupportFragmentManager(), "ForwardMessagesDialogFragment");
                        });
                builder.create().show();
            }
        };

        App.runThread(() -> {
            int count = 0;
            try {
                count = AppDatabase.getInstance().messageDao().countMessagesWithIncompleteFyles(selectedMessageIds);
            } catch (Exception ignored) { }

            int finalCount = count;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalCount == selectedMessageIds.size()) {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_incomplete_attachments)
                            .setMessage(R.string.dialog_message_incomplete_attachments)
                            .setNegativeButton(R.string.button_label_ok, null);
                    builder.create().show();
                } else if (finalCount > 0) {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_incomplete_attachments)
                            .setMessage(R.string.dialog_message_incomplete_attachments_partial)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .setPositiveButton(R.string.button_label_proceed, (dialog, which) -> runnable.run());
                    builder.create().show();
                } else {
                    runnable.run();
                }
            });
        });
    }
}
