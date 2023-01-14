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

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.DiscussionCustomization;


public class DiscussionSharedEphemeralSettingsPreferenceFragment extends PreferenceFragmentCompat implements DiscussionSettingsViewModel.SettingsChangedListener {
    private FragmentActivity activity;
    private DiscussionSettingsViewModel discussionSettingsViewModel = null;
    private DiscussionSettingsDataStore discussionSettingsDataStore;


    PreferenceCategory sharedSettingsCategory;
    SwitchPreference readOncePreference;
    ListPreference visibilityDurationPreference;
    ListPreference existenceDurationPreference;


    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.discussion_fragment_preferences_shared_ephemeral_settings, rootKey);
        activity = requireActivity();
        discussionSettingsViewModel = new ViewModelProvider(activity).get(DiscussionSettingsViewModel.class);
        discussionSettingsDataStore = discussionSettingsViewModel.getDiscussionSettingsDataStore();
        getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);

        PreferenceScreen screen = getPreferenceScreen();

        sharedSettingsCategory = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS);
        if (sharedSettingsCategory != null) {
            readOncePreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_READ_ONCE);
            visibilityDurationPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_VISIBILITY_DURATION);
            existenceDurationPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_EXISTENCE_DURATION);
        }

        discussionSettingsViewModel.addSettingsChangedListener(this);
    }

    @Override
    public void onSettingsChanged(DiscussionCustomization discussionCustomization) {
        if (readOncePreference != null) {
            readOncePreference.setChecked(discussionSettingsViewModel.getSettingsReadOnce());
        }
        if (visibilityDurationPreference != null) {
            if (discussionSettingsViewModel.getSettingsVisibilityDuration() == null) {
                visibilityDurationPreference.setValue("null");
                visibilityDurationPreference.setSummary(R.string.pref_discussion_visibility_duration_summary_null);
            } else {
                visibilityDurationPreference.setValue(Long.toString(discussionSettingsViewModel.getSettingsVisibilityDuration()));
                CharSequence entry = visibilityDurationPreference.getEntry();
                if (entry == null) {
                    List<CharSequence> values = new ArrayList<>(Arrays.asList(visibilityDurationPreference.getEntryValues()));
                    List<CharSequence> entries = new ArrayList<>(Arrays.asList(visibilityDurationPreference.getEntries()));
                    values.add(Long.toString(discussionSettingsViewModel.getSettingsVisibilityDuration()));
                    entry = StringUtils.getNiceDurationString(requireContext(), discussionSettingsViewModel.getSettingsVisibilityDuration());
                    entries.add(entry);

                    visibilityDurationPreference.setEntryValues(values.toArray(new CharSequence[0]));
                    visibilityDurationPreference.setEntries(entries.toArray(new CharSequence[0]));
                }
                visibilityDurationPreference.setSummary(getString(R.string.pref_discussion_visibility_duration_summary, entry));
            }
        }
        if (existenceDurationPreference != null) {
            if (discussionSettingsViewModel.getSettingsExistenceDuration() == null) {
                existenceDurationPreference.setValue("null");
                existenceDurationPreference.setSummary(R.string.pref_discussion_existence_duration_summary_null);
            } else {
                existenceDurationPreference.setValue(Long.toString(discussionSettingsViewModel.getSettingsExistenceDuration()));
                CharSequence entry = existenceDurationPreference.getEntry();
                if (entry == null) {
                    List<CharSequence> values = new ArrayList<>(Arrays.asList(existenceDurationPreference.getEntryValues()));
                    List<CharSequence> entries = new ArrayList<>(Arrays.asList(existenceDurationPreference.getEntries()));
                    values.add(Long.toString(discussionSettingsViewModel.getSettingsExistenceDuration()));
                    entry = StringUtils.getNiceDurationString(requireContext(), discussionSettingsViewModel.getSettingsExistenceDuration());
                    entries.add(entry);

                    existenceDurationPreference.setEntryValues(values.toArray(new CharSequence[0]));
                    existenceDurationPreference.setEntries(entries.toArray(new CharSequence[0]));
                }
                existenceDurationPreference.setSummary(getString(R.string.pref_discussion_existence_duration_summary, entry));
            }
        }
    }

    @Override
    public void onLockedOrGroupAdminChanged(boolean locked, boolean nonAdminGroup) {
        if (locked) {
            if (sharedSettingsCategory != null) {
                sharedSettingsCategory.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary);
            }
            if (readOncePreference != null) {
                readOncePreference.setEnabled(false);
            }
            if (visibilityDurationPreference != null) {
                visibilityDurationPreference.setEnabled(false);
            }
            if (existenceDurationPreference != null) {
                existenceDurationPreference.setEnabled(false);
            }
        } else if (nonAdminGroup) {
            // joined group
            if (sharedSettingsCategory != null) {
                sharedSettingsCategory.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary_only_owner);
            }
            if (readOncePreference != null) {
                readOncePreference.setEnabled(false);
            }
            if (visibilityDurationPreference != null) {
                visibilityDurationPreference.setEnabled(false);
            }
            if (existenceDurationPreference != null) {
                existenceDurationPreference.setEnabled(false);
            }
        } else {
            // own group or no group
            if (sharedSettingsCategory != null) {
                sharedSettingsCategory.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary);
            }
            if (readOncePreference != null) {
                readOncePreference.setEnabled(true);
            }
            if (visibilityDurationPreference != null) {
                visibilityDurationPreference.setEnabled(true);
            }
            if (existenceDurationPreference != null) {
                existenceDurationPreference.setEnabled(true);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (discussionSettingsViewModel != null) {
            discussionSettingsViewModel.removeSettingsChangedListener(this);
        }
    }
}
