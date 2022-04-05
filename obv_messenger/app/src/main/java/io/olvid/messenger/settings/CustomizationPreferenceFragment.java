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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.emoji2.bundled.BundledEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.DropDownPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import io.olvid.messenger.App;
import io.olvid.messenger.R;

public class CustomizationPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_customization, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        {
            final SwitchPreference darkModeSwitchPreference = screen.findPreference(SettingsActivity.PREF_KEY_DARK_MODE);
            final ListPreference darkModeListPreference = screen.findPreference(SettingsActivity.PREF_KEY_DARK_MODE_API29);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (darkModeSwitchPreference != null) {
                    screen.removePreference(darkModeSwitchPreference);
                }
                if (darkModeListPreference != null) {
                    darkModeListPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                        if (newValue instanceof String) {
                            switch ((String) newValue) {
                                case "Dark":
                                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                                    break;
                                case "Light":
                                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                                    break;
                                case "Auto":
                                default:
                                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            }
                        }
                        return true;
                    });
                }
            } else {
                if (darkModeListPreference != null) {
                    screen.removePreference(darkModeListPreference);
                }
                if (darkModeSwitchPreference != null) {
                    darkModeSwitchPreference.setOnPreferenceChangeListener((Preference preference, Object checked) -> {
                        if (checked instanceof Boolean) {
                            if ((Boolean) checked) {
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                            } else {
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                            }
                        }
                        return true;
                    });
                }
            }
        }

        {
            final DropDownPreference fontScalePreference = screen.findPreference(SettingsActivity.PREF_KEY_FONT_SCALE);
            if (fontScalePreference != null) {
                fontScalePreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    Intent scaleChangedIntent = new Intent(SettingsActivity.ACTIVITY_RECREATE_REQUIRED_ACTION);
                    scaleChangedIntent.setPackage(App.getContext().getPackageName());
                    // we delay sending this intent so we are sure the setting is updated when activities are recreated
                    new Handler(Looper.getMainLooper()).postDelayed(() -> LocalBroadcastManager.getInstance(App.getContext()).sendBroadcast(scaleChangedIntent), 200);
                    return true;
                });
            }
        }

        {
            final SwitchPreference useSystemEmojisPreference = screen.findPreference(SettingsActivity.PREF_KEY_USE_SYSTEM_EMOJIS);
            if (useSystemEmojisPreference != null) {
                useSystemEmojisPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    if (newValue instanceof Boolean) {
                        EmojiCompat.Config emojiConfig = new BundledEmojiCompatConfig(App.getContext());
                        if (!(Boolean) newValue) {
                            emojiConfig.setReplaceAll(true);
                        }
                        EmojiCompat.reset(emojiConfig);
                    }
                    return true;
                });
            }
        }
    }
}
