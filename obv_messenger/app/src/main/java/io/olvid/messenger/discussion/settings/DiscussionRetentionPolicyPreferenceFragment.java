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

package io.olvid.messenger.discussion.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.settings.SettingsActivity;


public class DiscussionRetentionPolicyPreferenceFragment extends PreferenceFragmentCompat implements DiscussionSettingsViewModel.SettingsChangedListener {
    private FragmentActivity activity;
    private DiscussionSettingsViewModel discussionSettingsViewModel = null;
    private DiscussionSettingsDataStore discussionSettingsDataStore;

    PreferenceCategory retentionPolicyCategory;
    EditTextPreference discussionRetentionCountPreference;
    ListPreference discussionRetentionDurationPreference;


    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.discussion_fragment_preferences_retention_policy, rootKey);
        activity = requireActivity();
        discussionSettingsViewModel = new ViewModelProvider(activity).get(DiscussionSettingsViewModel.class);
        discussionSettingsDataStore = discussionSettingsViewModel.getDiscussionSettingsDataStore();
        getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);

        PreferenceScreen screen = getPreferenceScreen();

        retentionPolicyCategory = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_RETENTION_POLICY);
        if (retentionPolicyCategory != null) {
            discussionRetentionCountPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_COUNT);
            if (discussionRetentionCountPreference != null) {
                final Long defaultRetentionCount = SettingsActivity.getDefaultDiscussionRetentionCount();
                String defaultRetentionString;
                if (defaultRetentionCount == null) {
                    defaultRetentionString = getString(R.string.pref_discussion_retention_count_summary_null);
                    discussionRetentionCountPreference.setDialogMessage(R.string.pref_discussion_retention_count_dialog_message_default_null);
                } else {
                    defaultRetentionString = getString(R.string.pref_discussion_retention_count_summary, defaultRetentionCount);
                    discussionRetentionCountPreference.setDialogMessage(getString(R.string.pref_discussion_retention_count_dialog_message_default_number, defaultRetentionCount));
                }

                discussionRetentionCountPreference.setSummaryProvider(preference -> {
                    EditTextPreference editTextPreference = (EditTextPreference) preference;
                    String value = editTextPreference.getText();
                    if (value == null || value.length() == 0) {
                        return getString(R.string.pref_text_app_default_string, defaultRetentionString);
                    }
                    try {
                        long count = Long.parseLong(value);
                        if (count == 0) {
                            return getString(R.string.pref_discussion_retention_count_summary_null);
                        } else {
                            return getString(R.string.pref_discussion_retention_count_summary, count);
                        }
                    } catch (Exception e) {
                        return getString(R.string.pref_discussion_retention_count_summary_null);
                    }
                });
                discussionRetentionCountPreference.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setHint(R.string.pref_discussion_retention_count_hint);
                    editText.selectAll();
                });
                discussionRetentionCountPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    Long maxMessages = null;
                    if (newValue == null || ((String) newValue).length() == 0) {
                        maxMessages = defaultRetentionCount;
                    } else {
                        try {
                            maxMessages = Long.parseLong((String) newValue);
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    if (maxMessages == null || maxMessages == 0) {
                        return true;
                    }
                    long finalMaxMessages = maxMessages;
                    App.runThread(() -> {
                        int count = AppDatabase.getInstance().messageDao().countExpirableMessagesInDiscussion(discussionSettingsViewModel.getDiscussionId());
                        int toDelete = (int) ((long) count - finalMaxMessages);
                        if (toDelete > 0) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_confirm_retention_policy)
                                        .setMessage(getResources().getQuantityString(R.plurals.dialog_message_confirm_retention_policy, toDelete, toDelete))
                                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_COUNT, (String) newValue))
                                        .setNegativeButton(R.string.button_label_cancel, null);
                                builder.create().show();
                            });
                        } else {
                            discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_COUNT, (String) newValue);
                        }
                    });
                    return false;
                });
            }

            discussionRetentionDurationPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_DURATION);
            if (discussionRetentionDurationPreference != null) {
                Long defaultRetentionDuration = SettingsActivity.getDefaultDiscussionRetentionDuration();
                String defaultRetentionString;
                String shortDefaultRetentionString;
                if (defaultRetentionDuration == null) {
                    defaultRetentionString = getString(R.string.pref_discussion_retention_duration_summary_null);
                    shortDefaultRetentionString = defaultRetentionString;
                } else {
                    int index = discussionRetentionDurationPreference.findIndexOfValue(Long.toString(defaultRetentionDuration));
                    if (index == -1) {
                        defaultRetentionString = getString(R.string.pref_discussion_retention_duration_summary_null);
                        shortDefaultRetentionString = defaultRetentionString;
                    } else {
                        defaultRetentionString = getString(R.string.pref_discussion_retention_duration_summary, discussionRetentionDurationPreference.getEntries()[index]);
                        shortDefaultRetentionString = discussionRetentionDurationPreference.getEntries()[index].toString();
                    }
                }

                CharSequence[] entries = discussionRetentionDurationPreference.getEntries();
                entries[0] = getString(R.string.pref_text_app_default_string, shortDefaultRetentionString);

                discussionRetentionDurationPreference.setSummaryProvider((Preference preference) -> {
                    ListPreference listPreference = (ListPreference) preference;
                    String value = listPreference.getValue();
                    CharSequence entry = listPreference.getEntry();
                    if (value == null || "null".equals(value)) {
                        return getString(R.string.pref_text_app_default_string, defaultRetentionString);
                    } else if ("0".equals(value)) {
                        return getString(R.string.pref_discussion_retention_duration_summary_null);
                    }
                    return getString(R.string.pref_discussion_retention_duration_summary, entry);
                });
                discussionRetentionDurationPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    Long retentionDuration = null;
                    if (newValue == null || "null".equals(newValue)) {
                        retentionDuration = defaultRetentionDuration;
                    } else {
                        try {
                            retentionDuration = Long.parseLong((String) newValue);
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    if (retentionDuration == null || retentionDuration == 0) {
                        return true;
                    }
                    Long finalRetentionDuration = retentionDuration;
                    App.runThread(() -> {
                        int toDelete = AppDatabase.getInstance().messageDao().countOldDiscussionMessages(discussionSettingsViewModel.getDiscussionId(), System.currentTimeMillis() - 1000L * finalRetentionDuration);
                        if (toDelete > 0) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_confirm_retention_policy)
                                        .setMessage(getResources().getQuantityString(R.plurals.dialog_message_confirm_retention_policy, toDelete, toDelete))
                                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_DURATION, (String) newValue))
                                        .setNegativeButton(R.string.button_label_cancel, null);
                                builder.create().show();
                            });
                        } else {
                            discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_DURATION, (String) newValue);
                        }
                    });
                    return false;
                });

            }

        }

        discussionSettingsViewModel.addSettingsChangedListener(this);
    }

    @Override
    public void onSettingsChanged(DiscussionCustomization discussionCustomization) {
        if (discussionRetentionCountPreference != null) {
            discussionRetentionCountPreference.setText(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_COUNT, ""));
        }

        if (discussionRetentionDurationPreference != null) {
            discussionRetentionDurationPreference.setValue(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_DURATION, "null"));
        }
    }

    @Override
    public void onLockedOrGroupAdminChanged(boolean locked, boolean nonAdminGroup) {
        // nothing to do here.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (discussionSettingsViewModel != null) {
            discussionSettingsViewModel.removeSettingsChangedListener(this);
        }
    }
}
