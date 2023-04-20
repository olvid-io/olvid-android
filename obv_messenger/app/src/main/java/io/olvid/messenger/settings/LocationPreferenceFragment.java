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

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import io.olvid.messenger.R;

public class LocationPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_location, rootKey);

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        ListPreference mapIntegrationPreference = screen.findPreference(SettingsActivity.PREF_KEY_LOCATION_INTEGRATION);
        Preference osmLanguagePreference = screen.findPreference(SettingsActivity.PREF_KEY_LOCATION_OSM_LANGUAGE);
        if (mapIntegrationPreference != null && osmLanguagePreference != null) {
            mapIntegrationPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                if (newValue instanceof String) {
                    osmLanguagePreference.setVisible(SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_OSM.equals((String) newValue));
                }
                return true;
            });

            osmLanguagePreference.setVisible(SettingsActivity.getLocationIntegration() == SettingsActivity.LocationIntegrationEnum.OSM);
        }
    }
}
