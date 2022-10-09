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

package io.olvid.messenger.settings;

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
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.ImageViewPreference;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.fragments.dialog.LedColorPickerDialogFragment;
import io.olvid.messenger.notifications.AndroidNotificationManager;

public class NotificationPreferenceFragment extends PreferenceFragmentCompat {
    FragmentActivity activity;

    public static final int MESSAGE_RINGTONE_REQUEST_CODE = 23;
    public static final int CALL_RINGTONE_REQUEST_CODE = 25;

    Preference messageRingtonePreference;
    Preference messageVibrationPatternPreference;
    ImageViewPreference messageLedColorPreference;
    Preference callRingtonePreference;
    Preference callVibrationPatternPreference;
    SwitchPreference callUseFlashPreference;
    boolean messageSettingsChanged = false;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_notification, rootKey);

        PreferenceScreen screen = getPreferenceScreen();
        activity = requireActivity();

        messageRingtonePreference = screen.findPreference(SettingsActivity.PREF_KEY_MESSAGE_RINGTONE);
        if (messageRingtonePreference != null) {
            messageRingtonePreference.setOnPreferenceClickListener(preference -> {
                Uri messageRingtone = SettingsActivity.getMessageRingtone();

                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, messageRingtone);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
                App.startActivityForResult(this, intent, MESSAGE_RINGTONE_REQUEST_CODE);
                return true;
            });
            updateMessageRingtoneSummary();
        }

        messageVibrationPatternPreference = screen.findPreference(SettingsActivity.PREF_KEY_MESSAGE_VIBRATION_PATTERN);
        if (messageVibrationPatternPreference != null) {
            messageVibrationPatternPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                messageSettingsChanged = true;
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

        messageLedColorPreference = screen.findPreference(SettingsActivity.PREF_KEY_MESSAGE_LED_COLOR);
        if (messageLedColorPreference != null) {
            messageLedColorPreference.setOnPreferenceClickListener((Preference preference) -> {
                LedColorPickerDialogFragment ledColorPickerDialogFragment = LedColorPickerDialogFragment.newInstance();
                ledColorPickerDialogFragment.setInitialColor(SettingsActivity.getMessageLedColor());
                ledColorPickerDialogFragment.setOnLedColorSelectedListener((String color) -> {
                    SettingsActivity.setMessageLedColor(color);
                    messageSettingsChanged = true;
                    updateMessageLedColorImage();
                });
                ledColorPickerDialogFragment.show(getChildFragmentManager(), "dialog");
                return true;
            });
            updateMessageLedColorImage();
        }

        callRingtonePreference = screen.findPreference(SettingsActivity.PREF_KEY_CALL_RINGTONE);
        if (callRingtonePreference != null) {
            callRingtonePreference.setOnPreferenceClickListener(preference -> {
                Uri callRingtone = SettingsActivity.getCallRingtone();

                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, callRingtone);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI);
                App.startActivityForResult(this, intent, CALL_RINGTONE_REQUEST_CODE);
                return true;
            });
            updateCallRingtoneSummary();
        }

        callVibrationPatternPreference = screen.findPreference(SettingsActivity.PREF_KEY_CALL_VIBRATION_PATTERN);
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

        callUseFlashPreference = screen.findPreference(SettingsActivity.PREF_KEY_CALL_USE_FLASH);
        if (callUseFlashPreference != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                callUseFlashPreference.setVisible(false);
            }
        }


    }

    private void updateMessageRingtoneSummary() {
        if (messageRingtonePreference != null){
            Uri ringtoneUri = SettingsActivity.getMessageRingtone();
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
            String colorString = SettingsActivity.getMessageLedColor();
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
            Uri ringtoneUri = SettingsActivity.getCallRingtone();
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
                    if (Objects.equals(uri, Settings.System.DEFAULT_NOTIFICATION_URI)) {
                        SettingsActivity.setMessageRingtone(null);
                    } else if (StringUtils.validateUri(uri)) {
                        SettingsActivity.setMessageRingtone(uri.toString());
                    } else {
                        SettingsActivity.setMessageRingtone(Uri.EMPTY.toString());
                    }
                    updateMessageRingtoneSummary();
                    messageSettingsChanged = true;
                }
                break;
            }
            case CALL_RINGTONE_REQUEST_CODE: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (Objects.equals(uri, Settings.System.DEFAULT_NOTIFICATION_URI)) {
                        SettingsActivity.setCallRingtone(null);
                    } else if (StringUtils.validateUri(uri)) {
                        SettingsActivity.setCallRingtone(uri.toString());
                    } else {
                        SettingsActivity.setCallRingtone(Uri.EMPTY.toString());
                    }
                    updateCallRingtoneSummary();
                }
                break;
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (messageSettingsChanged) {
            AndroidNotificationManager.updateMessageChannel(true);
        }
    }
}
