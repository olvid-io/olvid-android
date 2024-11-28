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

package io.olvid.messenger.discussion.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import io.olvid.messenger.R;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.settings.SettingsActivity;


public class DiscussionSendReceiveMessagesPreferenceFragment extends PreferenceFragmentCompat implements DiscussionSettingsViewModel.SettingsChangedListener {
    private FragmentActivity activity;
    private DiscussionSettingsViewModel discussionSettingsViewModel = null;
    private DiscussionSettingsDataStore discussionSettingsDataStore;

    ListPreference readReceiptPreference;
    ListPreference autoOpenLimitedVisibilityPreference;
    ListPreference retainWipedOutboundMessagesPreference;


    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.discussion_fragment_preferences_send_receive_messages, rootKey);
        activity = requireActivity();
        discussionSettingsViewModel = new ViewModelProvider(activity).get(DiscussionSettingsViewModel.class);
        discussionSettingsDataStore = discussionSettingsViewModel.getDiscussionSettingsDataStore();
        getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);

        PreferenceScreen screen = getPreferenceScreen();

        readReceiptPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_READ_RECEIPT);
        if (readReceiptPreference != null) {
            CharSequence[] readReceiptPreferenceEntries = readReceiptPreference.getEntries();
            if (SettingsActivity.getDefaultSendReadReceipt()) {
                readReceiptPreferenceEntries[0] = getString(R.string.pref_text_app_default_true);
            } else {
                readReceiptPreferenceEntries[0] = getString(R.string.pref_text_app_default_false);
            }
        }

        autoOpenLimitedVisibilityPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND);
        if (autoOpenLimitedVisibilityPreference != null) {
            CharSequence[] autoOpenLimitedVisibilityPreferenceEntries = autoOpenLimitedVisibilityPreference.getEntries();
            if (SettingsActivity.getDefaultAutoOpenLimitedVisibilityInboundMessages()) {
                autoOpenLimitedVisibilityPreferenceEntries[0] = getString(R.string.pref_text_app_default_true);
            } else {
                autoOpenLimitedVisibilityPreferenceEntries[0] = getString(R.string.pref_text_app_default_false);
            }
        }

        retainWipedOutboundMessagesPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES);
        if (retainWipedOutboundMessagesPreference != null) {
            CharSequence[] retainWipedOutboundMessagesPreferenceEntries = retainWipedOutboundMessagesPreference.getEntries();
            if (SettingsActivity.getDefaultRetainWipedOutboundMessages()) {
                retainWipedOutboundMessagesPreferenceEntries[0] = getString(R.string.pref_text_app_default_true);
            } else {
                retainWipedOutboundMessagesPreferenceEntries[0] = getString(R.string.pref_text_app_default_false);
            }
        }

        discussionSettingsViewModel.addSettingsChangedListener(this);
    }

    @Override
    public void onSettingsChanged(@Nullable DiscussionCustomization discussionCustomization) {
        if (readReceiptPreference != null) {
            readReceiptPreference.setValue(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_READ_RECEIPT, "null"));
        }
        if (autoOpenLimitedVisibilityPreference != null) {
            autoOpenLimitedVisibilityPreference.setValue(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND, "null"));
        }
        if (retainWipedOutboundMessagesPreference != null) {
            retainWipedOutboundMessagesPreference.setValue(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES, "null"));
        }
    }

    @Override
    public void onLockedOrGroupAdminChanged(boolean locked, boolean nonAdminGroup) {
        if (readReceiptPreference != null) {
            readReceiptPreference.setEnabled(!locked);
        }
        if (autoOpenLimitedVisibilityPreference != null) {
            autoOpenLimitedVisibilityPreference.setEnabled(!locked);
        }
        if (retainWipedOutboundMessagesPreference != null) {
            retainWipedOutboundMessagesPreference.setEnabled(!locked);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (discussionSettingsViewModel != null) {
            discussionSettingsViewModel.removeSettingsChangedListener(this);
        }
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(getResources().getColor(R.color.dialogBackground));
    }
}
