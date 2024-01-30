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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.ImageViewPreference;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.fragments.dialog.LedColorPickerDialogFragment;
import io.olvid.messenger.settings.SettingsActivity;


public class DiscussionNotificationsPreferenceFragment extends PreferenceFragmentCompat implements DiscussionSettingsViewModel.SettingsChangedListener {
    public static final int MESSAGE_RINGTONE_REQUEST_CODE = 23;
    public static final int CALL_RINGTONE_REQUEST_CODE = 25;


    private FragmentActivity activity;
    private DiscussionSettingsViewModel discussionSettingsViewModel = null;
    private DiscussionSettingsDataStore discussionSettingsDataStore;

    SwitchPreference useCustomMessageNotificationPreference;
    Preference messageRingtonePreference;
    ListPreference messageVibrationPatternPreference;
    ImageViewPreference messageLedColorPreference;
    SwitchPreference useCustomCallNotificationPreference;
    Preference callRingtonePreference;
    ListPreference callVibrationPatternPreference;
    SwitchPreference callUseFlashPreference;


    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.discussion_fragment_preferences_notifications, rootKey);
        activity = requireActivity();
        discussionSettingsViewModel = new ViewModelProvider(activity).get(DiscussionSettingsViewModel.class);
        discussionSettingsDataStore = discussionSettingsViewModel.getDiscussionSettingsDataStore();
        getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);

        PreferenceScreen screen = getPreferenceScreen();

        useCustomMessageNotificationPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_CUSTOM_NOTIFICATION);

        messageRingtonePreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_RINGTONE);
        if (messageRingtonePreference != null) {
            messageRingtonePreference.setOnPreferenceClickListener(preference -> {
                Uri messageRingtone = Uri.parse(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_RINGTONE, null));

                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, messageRingtone);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
                App.startActivityForResult(this, intent, MESSAGE_RINGTONE_REQUEST_CODE);
                return true;
            });
        }

        messageVibrationPatternPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_VIBRATION_PATTERN);
        if (messageVibrationPatternPreference != null) {
            messageVibrationPatternPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    long[] pattern = SettingsActivity.intToVibrationPattern(Integer.parseInt((String) newValue));
                    if (pattern != null) {
                        v.vibrate(pattern, -1);
                    }
                }
                return true;
            });
        }

        messageLedColorPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_LED_COLOR);
        if (messageLedColorPreference != null) {
            messageLedColorPreference.setOnPreferenceClickListener((Preference preference) -> {
                LedColorPickerDialogFragment ledColorPickerDialogFragment = LedColorPickerDialogFragment.newInstance();
                ledColorPickerDialogFragment.setInitialColor(SettingsActivity.getMessageLedColor());
                ledColorPickerDialogFragment.setOnLedColorSelectedListener((String color) -> {
                    discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_LED_COLOR, color);
                    updateMessageLedColorImage();
                });
                ledColorPickerDialogFragment.show(getChildFragmentManager(), "dialog");
                return true;
            });
        }

        useCustomCallNotificationPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_CUSTOM_NOTIFICATION);

        callRingtonePreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_RINGTONE);
        if (callRingtonePreference != null) {
            callRingtonePreference.setOnPreferenceClickListener(preference -> {
                Uri callRingtone = Uri.parse(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_RINGTONE, null));

                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, callRingtone);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI);
                App.startActivityForResult(this, intent, CALL_RINGTONE_REQUEST_CODE);
                return true;
            });
        }

        callVibrationPatternPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_VIBRATION_PATTERN);
        if (callVibrationPatternPreference != null) {
            callVibrationPatternPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    long[] pattern = SettingsActivity.intToVibrationPattern(Integer.parseInt((String) newValue));
                    if (pattern != null) {
                        v.vibrate(pattern, -1);
                    }
                }
                return true;
            });
        }

        callUseFlashPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_USE_FLASH);
        if (callUseFlashPreference != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                callUseFlashPreference.setVisible(false);
            }
        }

        discussionSettingsViewModel.addSettingsChangedListener(this);
    }

    private void updateMessageRingtoneSummary() {
        if (messageRingtonePreference != null){
            Uri ringtoneUri = Uri.parse(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_RINGTONE, null));
            if (ringtoneUri.equals(Uri.EMPTY)) {
                messageRingtonePreference.setSummary(getString(R.string.pref_text_silent));
            } else {
                Ringtone ringtone = RingtoneManager.getRingtone(activity, ringtoneUri);
                if (ringtone != null) {
                    messageRingtonePreference.setSummary(ringtone.getTitle(activity));
                } else {
                    messageRingtonePreference.setSummary(null);
                }
            }
        }
    }

    private void updateMessageLedColorImage() {
        if (messageLedColorPreference != null) {
            String colorString = discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_LED_COLOR, null);
            if (colorString == null) {
                messageLedColorPreference.setColor((Integer) null);
            } else {
                int color = Integer.parseInt(colorString.substring(1), 16);
                messageLedColorPreference.setColor(color);
            }
        }
    }

    private void updateCallRingtoneSummary() {
        if (callRingtonePreference != null){
            Uri ringtoneUri = Uri.parse(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_RINGTONE, null));
            if (ringtoneUri.equals(Uri.EMPTY)) {
                callRingtonePreference.setSummary(getString(R.string.pref_text_silent));
            } else {
                Ringtone ringtone = RingtoneManager.getRingtone(activity, ringtoneUri);
                if (ringtone != null) {
                    try {
                        callRingtonePreference.setSummary(ringtone.getTitle(activity));
                    } catch (Exception e) {
                        callRingtonePreference.setSummary(null);
                    }
                } else {
                    callRingtonePreference.setSummary(null);
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case MESSAGE_RINGTONE_REQUEST_CODE: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (StringUtils.validateUri(uri)) {
                        //noinspection ConstantConditions
                        discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_RINGTONE, uri.toString());
                    } else {
                        discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_RINGTONE, Uri.EMPTY.toString());
                    }
                    updateMessageRingtoneSummary();
                }
                break;
            }
            case CALL_RINGTONE_REQUEST_CODE: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (StringUtils.validateUri(uri)) {
                        //noinspection ConstantConditions
                        discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_RINGTONE, uri.toString());
                    } else {
                        discussionSettingsDataStore.putString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_RINGTONE, Uri.EMPTY.toString());
                    }
                    updateCallRingtoneSummary();
                }
                break;
            }
        }
    }

    @Override
    public void onSettingsChanged(@Nullable DiscussionCustomization discussionCustomization) {
        if (useCustomMessageNotificationPreference != null) {
            useCustomMessageNotificationPreference.setChecked(discussionSettingsDataStore.getBoolean(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_CUSTOM_NOTIFICATION, false));
        }
        if (messageVibrationPatternPreference != null) {
            messageVibrationPatternPreference.setValue(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_VIBRATION_PATTERN, null));
        }
        updateMessageRingtoneSummary();
        updateMessageLedColorImage();

        if (useCustomCallNotificationPreference != null) {
            useCustomCallNotificationPreference.setChecked(discussionSettingsDataStore.getBoolean(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_CUSTOM_NOTIFICATION, false));
        }
        if (callVibrationPatternPreference != null) {
            callVibrationPatternPreference.setValue(discussionSettingsDataStore.getString(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_VIBRATION_PATTERN, null));
        }
        updateCallRingtoneSummary();
        if (callUseFlashPreference != null) {
            callUseFlashPreference.setChecked(discussionSettingsDataStore.getBoolean(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_USE_FLASH, false));
        }
    }

    @Override
    public void onLockedOrGroupAdminChanged(boolean locked, boolean nonAdminGroup) {
        // nothing to do here...
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (discussionSettingsViewModel != null) {
            discussionSettingsViewModel.removeSettingsChangedListener(this);
        }
    }
}
