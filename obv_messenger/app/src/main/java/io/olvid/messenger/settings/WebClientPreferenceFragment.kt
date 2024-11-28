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
package io.olvid.messenger.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceFragmentCompat
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.NoClickSwitchPreference
import io.olvid.messenger.fragments.dialog.LockScreenPINVerificationDialogFragment
import io.olvid.messenger.settings.SettingsActivity.Companion.useApplicationLockScreen

class WebClientPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences_webclient, rootKey)
        val screen = preferenceScreen ?: return

        run {
            val languageSetting =
                screen.findPreference<ListPreference>(SettingsActivity.PREF_KEY_LANGUAGE_WEBCLIENT)
            if (languageSetting != null) {
                languageSetting.summaryProvider =
                    SummaryProvider { preference: Preference ->
                        val listPreference =
                            preference as ListPreference
                        listPreference.entry
                    }
            }
        }

        run {
            val themeSetting =
                screen.findPreference<ListPreference>(SettingsActivity.PREF_KEY_THEME_WEBCLIENT)
            if (themeSetting != null) {
                themeSetting.summaryProvider =
                    SummaryProvider { preference: Preference ->
                        val listPreference =
                            preference as ListPreference
                        listPreference.entry
                    }
            }
        }

        run {
            val requireUnlock =
                screen.findPreference<NoClickSwitchPreference>(SettingsActivity.PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT)
            if (requireUnlock != null) {
                requireUnlock.isEnabled = useApplicationLockScreen()

                requireUnlock.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener { pref: Preference? ->
                        if (pref is NoClickSwitchPreference) {
                            val preference = pref
                            if (preference.isChecked) {
                                val lockScreenPINVerificationDialogFragment =
                                    LockScreenPINVerificationDialogFragment.newInstance()
                                lockScreenPINVerificationDialogFragment.setOnPINEnteredCallBack {
                                    if (preference.callChangeListener(false)) {
                                        preference.isChecked = false
                                    }
                                }
                                lockScreenPINVerificationDialogFragment.show(
                                    childFragmentManager,
                                    "dialog"
                                )
                            } else {
                                if (preference.callChangeListener(true)) {
                                    preference.isChecked = true
                                }
                            }
                        }
                        true
                    }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(resources.getColor(R.color.dialogBackground))
    }
}
