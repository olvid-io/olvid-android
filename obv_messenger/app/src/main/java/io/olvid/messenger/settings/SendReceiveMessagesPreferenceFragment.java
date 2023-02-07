/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.List;

import io.olvid.messenger.App;
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

        SwitchPreference linkPreviewPreferenceOutbound = screen.findPreference(SettingsActivity.PREF_KEY_LINK_PREVIEW_OUTBOUND);
        if (linkPreviewPreferenceOutbound != null) {
            if (!SettingsActivity.getBetaFeaturesEnabled()) {
                screen.removePreference(linkPreviewPreferenceOutbound);
            }
        }

        SwitchPreference linkPreviewPreferenceInbound = screen.findPreference(SettingsActivity.PREF_KEY_LINK_PREVIEW_INBOUND);
        if (linkPreviewPreferenceInbound != null) {
            if (!SettingsActivity.getBetaFeaturesEnabled()) {
                screen.removePreference(linkPreviewPreferenceInbound);
            }
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
}
