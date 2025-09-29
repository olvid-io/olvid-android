/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.settings

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceFragmentCompat
import io.olvid.messenger.R
import io.olvid.messenger.settings.SettingsActivity.Companion.betaFeaturesEnabled


class CallPreferenceFragment  : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences_call, rootKey)

        val screen = preferenceScreen

        val scaledTurnPreference: DropDownPreference? = screen.findPreference(SettingsActivity.PREF_KEY_SCALED_TURN_REGION)
        if (scaledTurnPreference != null) {
            if (betaFeaturesEnabled) {
                scaledTurnPreference.isVisible = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.almostWhite))
    }
}