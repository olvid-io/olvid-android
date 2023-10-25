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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.MultilineSummaryPreferenceCategory;
import io.olvid.messenger.customClasses.MuteNotificationDialog;
import io.olvid.messenger.customClasses.NoClickSwitchPreference;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.tasks.PropagatePinnedDiscussionsChangeTask;

public class DiscussionSettingsHeadersFragment extends PreferenceFragmentCompat implements DiscussionSettingsViewModel.SettingsChangedListener {
    FragmentActivity activity;
    private DiscussionSettingsDataStore discussionSettingsDataStore;
    private DiscussionSettingsViewModel discussionSettingsViewModel = null;

    MultilineSummaryPreferenceCategory lockedWarningPreference;
    NoClickSwitchPreference pinPreference;
    NoClickSwitchPreference muteNotificationsPreference;
    Preference sendReceiveHeaderPreference;
    Preference sharedEphemeralSettingsHeaderPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.discussion_preferences_headers, rootKey);
        activity = requireActivity();
        discussionSettingsViewModel = new ViewModelProvider(activity).get(DiscussionSettingsViewModel.class);
        discussionSettingsDataStore = discussionSettingsViewModel.getDiscussionSettingsDataStore();
        getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);

        discussionSettingsViewModel.getDiscussionLiveData().observe(this, (Discussion discussion) -> {
            if (pinPreference != null && discussion != null) {
                pinPreference.setChecked(discussion.pinned);
            }
        });

        PreferenceScreen screen = getPreferenceScreen();

        lockedWarningPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_LOCKED_EXPLANATION);

        pinPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_PIN);
        if (pinPreference != null) {
            pinPreference.setOnPreferenceClickListener(preference -> {
                Discussion discussion = discussionSettingsViewModel.getDiscussionLiveData().getValue();
                if (discussion != null) {
                    App.runThread(() -> {
                        AppDatabase.getInstance().discussionDao().updatePinned(discussion.id, !discussion.pinned);
                        new PropagatePinnedDiscussionsChangeTask(discussion.bytesOwnedIdentity).run();
                    });
                    return true;
                }
                return false;
            });
        }

        muteNotificationsPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS);
        if (muteNotificationsPreference != null) {
            muteNotificationsPreference.setOnPreferenceClickListener(preference -> {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                    discussionSettingsDataStore.putBoolean(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS, false);
                } else {
                    Context context = getContext();
                    Long discussionId = discussionSettingsViewModel.getDiscussionId();
                    if (discussionId != null && context != null) {
                        MuteNotificationDialog muteNotificationDialog = new MuteNotificationDialog(context, (Long muteExpirationTimestamp, boolean muteWholeProfile, boolean muteExceptMentioned) -> App.runThread(() -> {
                            DiscussionCustomization discussionCust = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
                            boolean insert = false;
                            if (discussionCust == null) {
                                discussionCust = new DiscussionCustomization(discussionId);
                                insert = true;
                            }
                            discussionCust.prefMuteNotifications = true;
                            discussionCust.prefMuteNotificationsTimestamp = muteExpirationTimestamp;
                            discussionCust.prefMuteNotificationsExceptMentioned = muteExceptMentioned;
                            if (insert) {
                                AppDatabase.getInstance().discussionCustomizationDao().insert(discussionCust);
                            } else {
                                AppDatabase.getInstance().discussionCustomizationDao().update(discussionCust);
                            }
                        }), MuteNotificationDialog.MuteType.DISCUSSION, discussionCustomization == null || discussionCustomization.prefMuteNotificationsExceptMentioned);

                        muteNotificationDialog.show();
                    }
                }
                return true;
            });
        }

        sendReceiveHeaderPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_SEND_RECEIVE);
        sharedEphemeralSettingsHeaderPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS);


        discussionSettingsViewModel.addSettingsChangedListener(this);
    }

    @Override
    public void onSettingsChanged(@Nullable DiscussionCustomization discussionCustomization) {
        if (muteNotificationsPreference != null) {
            boolean shouldMute = discussionCustomization != null && discussionCustomization.shouldMuteNotifications();
            muteNotificationsPreference.setChecked(shouldMute);
            if (!shouldMute) {
                muteNotificationsPreference.setSummary(getString(R.string.pref_discussion_mute_notifications_summary));
            } else if (discussionCustomization.prefMuteNotificationsTimestamp == null) {
                muteNotificationsPreference.setSummary(getString(R.string.pref_discussion_mute_notifications_on_summary));
            } else {
                CharSequence dateString = StringUtils.getNiceDateString(getContext(), discussionCustomization.prefMuteNotificationsTimestamp);
                muteNotificationsPreference.setSummary(getString(R.string.pref_discussion_mute_notifications_on_until_summary, dateString));
            }
        }
    }

    @Override
    public void onLockedOrGroupAdminChanged(boolean locked, boolean nonAdminGroup) {
        if (lockedWarningPreference != null) {
            lockedWarningPreference.setVisible(locked);
        }
        if (sendReceiveHeaderPreference != null) {
            sendReceiveHeaderPreference.setEnabled(!locked);
        }
        if (sharedEphemeralSettingsHeaderPreference != null) {
            sharedEphemeralSettingsHeaderPreference.setEnabled(!locked);
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
