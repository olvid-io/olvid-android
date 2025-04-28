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

package io.olvid.messenger.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;

public class SendReceiveMessagesPreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_send_receive_messages, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        SwitchPreference sendReadReceiptPreference = screen.findPreference(SettingsActivity.PREF_KEY_READ_RECEIPT);
        if (sendReadReceiptPreference != null) {
            sendReadReceiptPreference.setOnPreferenceChangeListener((Preference preference, Object checked) -> {
                if (checked instanceof Boolean) {
                    try {
                        AppSingleton.getEngine().propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom.createSettingDefaultSendReadReceipts((Boolean) checked));
                        AppSingleton.getEngine().deviceBackupNeeded();
                    } catch (Exception e) {
                        Logger.w("Failed to propagate default send read receipt setting change to other devices");
                        e.printStackTrace();
                    }
                }
                return true;
            });
        }

        SwitchPreference unarchiveOnNotificationPreference = screen.findPreference(SettingsActivity.PREF_KEY_UNARCHIVE_DISCUSSION_ON_NOTIFICATION);
        if (unarchiveOnNotificationPreference != null) {
            unarchiveOnNotificationPreference.setOnPreferenceChangeListener((Preference preference, Object checked) -> {
                if (checked instanceof Boolean) {
                    try {
                        AppSingleton.getEngine().propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom.createSettingUnarchiveOnNotification((Boolean) checked));
                    } catch (Exception e) {
                        Logger.w("Failed to propagate unarchive on notification setting change to other devices");
                        e.printStackTrace();
                    }
                }
                return true;
            });
        }

        SwitchPreference retainRemoteDeletedPreference = screen.findPreference(SettingsActivity.PREF_KEY_RETAIN_REMOTE_DELETED_MESSAGES);
        if (retainRemoteDeletedPreference != null) {
            retainRemoteDeletedPreference.setOnPreferenceChangeListener((Preference preference, Object checked) -> {
                if (checked instanceof Boolean && !(Boolean) checked) {
                    App.runThread(() -> {
                        // count Message.WIPE_STATUS_REMOTE_DELETED messages
                        final int count = AppDatabase.getInstance().messageDao().countRemoteDeletedMessages();
                        if (count > 0) {
                            new Handler(Looper.getMainLooper()).post(() -> {

                                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_delete_remote_deleted_messages)
                                        .setMessage(getResources().getQuantityString(R.plurals.dialog_message_delete_remote_deleted_messages, count, count))
                                        .setPositiveButton(R.string.button_label_delete, (DialogInterface dialog, int which) -> App.runThread(() -> {
                                            AppDatabase.getInstance().messageDao().deleteAllRemoteDeletedMessages();
                                        }))
                                        .setNegativeButton(R.string.button_label_do_nothing, null);
                                builder.create().show();
                            });
                        }
                    });
                }
                return true;
            });
        }

        EditTextPreference defaultDiscussionRetentionCountPreference = screen.findPreference(SettingsActivity.PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT);
        if (defaultDiscussionRetentionCountPreference != null) {
            defaultDiscussionRetentionCountPreference.setSummaryProvider(preference -> {
                EditTextPreference editTextPreference = (EditTextPreference) preference;
                String value = editTextPreference.getText();
                if (value == null || value.length() == 0) {
                    return getString(R.string.pref_discussion_retention_count_summary_null);
                }
                try {
                    long count = Long.parseLong(value);
                    return getString(R.string.pref_discussion_retention_count_summary, count);
                } catch (Exception e) {
                    return getString(R.string.pref_discussion_retention_count_summary_null);
                }
            });
            defaultDiscussionRetentionCountPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setHint(R.string.pref_discussion_retention_count_hint);
                editText.selectAll();
            });
            defaultDiscussionRetentionCountPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                Long maxMessages = null;
                if (newValue != null && ((String) newValue).length() != 0) {
                    try {
                        maxMessages = Long.parseLong((String) newValue);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                if (maxMessages == null) {
                    return true;
                }
                long finalMaxMessages = maxMessages;
                App.runThread(() -> {
                    List<Integer> counts = AppDatabase.getInstance().messageDao().countExpirableMessagesInDiscussionsWithNoCustomization();
                    int toDelete = 0;
                    for (Integer count : counts) {
                        if (count != null && count > finalMaxMessages) {
                            toDelete += count - finalMaxMessages;
                        }
                    }
                    if (toDelete > 0) {
                        int finalToDelete = toDelete;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_confirm_retention_policy)
                                    .setMessage(getResources().getQuantityString(R.plurals.dialog_message_confirm_retention_policy, finalToDelete, finalToDelete))
                                    .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> defaultDiscussionRetentionCountPreference.setText((String) newValue))
                                    .setNegativeButton(R.string.button_label_cancel, null);
                            builder.create().show();
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> defaultDiscussionRetentionCountPreference.setText((String) newValue));
                    }
                });
                return false;
            });
        }

        ListPreference defaultDiscussionRetentionDurationPreference = screen.findPreference(SettingsActivity.PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION);
        if (defaultDiscussionRetentionDurationPreference != null) {
            defaultDiscussionRetentionDurationPreference.setSummaryProvider(preference -> {
                ListPreference listPreference = (ListPreference) preference;
                String value = listPreference.getValue();
                CharSequence entry = listPreference.getEntry();
                if (value == null || "null".equals(value)) {
                    return getString(R.string.pref_discussion_retention_duration_summary_null);
                }
                return getString(R.string.pref_discussion_retention_duration_summary, entry);
            });
            defaultDiscussionRetentionDurationPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                Long retentionDuration = null;
                if (newValue != null && !"null".equals(newValue)) {
                    try {
                        retentionDuration = Long.parseLong((String) newValue);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                if (retentionDuration == null) {
                    return true;
                }
                Long finalRetentionDuration = retentionDuration;
                App.runThread(() -> {
                    int toDelete = AppDatabase.getInstance().messageDao().countOldMessagesInDiscussionsWithNoCustomization(System.currentTimeMillis() - 1000L * finalRetentionDuration);
                    if (toDelete > 0) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_confirm_retention_policy)
                                    .setMessage(getResources().getQuantityString(R.plurals.dialog_message_confirm_retention_policy, toDelete, toDelete))
                                    .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> defaultDiscussionRetentionDurationPreference.setValue((String) newValue))
                                    .setNegativeButton(R.string.button_label_cancel, null);
                            builder.create().show();
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> defaultDiscussionRetentionDurationPreference.setValue((String) newValue));
                    }
                });
                return false;
            });
        }

        PreferenceCategory defaultEphemeralSettingsCategory = screen.findPreference(SettingsActivity.PREF_KEY_CATEGORY_DEFAULT_EPHEMERAL_SETTINGS);
        if (defaultEphemeralSettingsCategory != null) {
            ListPreference defaultVisibilityDurationPreference = screen.findPreference(SettingsActivity.PREF_KEY_DEFAULT_VISIBILITY_DURATION);
            if (defaultVisibilityDurationPreference != null) {
                defaultVisibilityDurationPreference.setSummaryProvider(preference -> {
                    ListPreference listPreference = (ListPreference) preference;
                    String value = listPreference.getValue();
                    CharSequence entry = listPreference.getEntry();
                    if (value == null || "null".equals(value)) {
                        return getString(R.string.pref_discussion_visibility_duration_summary_null);
                    }
                    return getString(R.string.pref_discussion_visibility_duration_summary, entry);
                });
            }

            ListPreference defaultExistenceDurationPreference = screen.findPreference(SettingsActivity.PREF_KEY_DEFAULT_EXISTENCE_DURATION);
            if (defaultExistenceDurationPreference != null) {
                defaultExistenceDurationPreference.setSummaryProvider(preference -> {
                    ListPreference listPreference = (ListPreference) preference;
                    String value = listPreference.getValue();
                    CharSequence entry = listPreference.getEntry();
                    if (value == null || "null".equals(value)) {
                        return getString(R.string.pref_discussion_existence_duration_summary_null);
                    }
                    return getString(R.string.pref_discussion_existence_duration_summary, entry);
                });
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.almostWhite));
    }
}
