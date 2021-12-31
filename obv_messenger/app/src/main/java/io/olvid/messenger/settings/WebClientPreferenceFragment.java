/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.NoClickSwitchPreference;
import io.olvid.messenger.fragments.dialog.LockScreenPINVerificationDialogFragment;

public class WebClientPreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_webclient, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        {
            ListPreference languageSetting = screen.findPreference(SettingsActivity.PREF_KEY_LANGUAGE_WEBCLIENT);
            if (languageSetting != null) {
                languageSetting.setSummaryProvider(preference -> {
                    ListPreference listPreference = (ListPreference) preference;
                    return listPreference.getEntry();
                });
            }
        }

        {
            ListPreference themeSetting = screen.findPreference(SettingsActivity.PREF_KEY_THEME_WEBCLIENT);
            if (themeSetting != null) {
                themeSetting.setSummaryProvider(preference -> {
                    ListPreference listPreference = (ListPreference) preference;
                    return listPreference.getEntry();
                });
            }
        }

        {
            NoClickSwitchPreference requireUnlock = screen.findPreference(SettingsActivity.PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT);
            if (requireUnlock != null) {
                requireUnlock.setEnabled(SettingsActivity.useApplicationLockScreen());

                requireUnlock.setOnPreferenceClickListener((Preference pref) -> {
                    if (pref instanceof NoClickSwitchPreference) {
                        NoClickSwitchPreference preference = (NoClickSwitchPreference) pref;
                        if (preference.isChecked()) {
                            LockScreenPINVerificationDialogFragment lockScreenPINVerificationDialogFragment = LockScreenPINVerificationDialogFragment.newInstance();
                            lockScreenPINVerificationDialogFragment.setOnPINEnteredCallBack(() -> {
                                if (preference.callChangeListener(false)) {
                                    preference.setChecked(false);
                                }
                            });
                            lockScreenPINVerificationDialogFragment.show(getChildFragmentManager(), "dialog");
                        } else {
                            if (preference.callChangeListener(true)) {
                                preference.setChecked(true);
                            }
                        }
                    }
                    return true;
                });
            }
        }
    }
}
