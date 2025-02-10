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
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog.OnIntegrationSelectedListener
import io.olvid.messenger.customClasses.NoClickSwitchPreference
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum

class LocationPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences_location, rootKey)

        val screen = preferenceScreen ?: return

        val mapIntegrationPreference = screen.findPreference<ListPreference>(SettingsActivity.PREF_KEY_LOCATION_INTEGRATION)
        if (mapIntegrationPreference != null) {
            mapIntegrationPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
                LocationIntegrationSelectorDialog(mapIntegrationPreference.context, true, object : OnIntegrationSelectedListener {
                    override fun onIntegrationSelected(integration: LocationIntegrationEnum, customOsmServerUrl: String?) {
                        SettingsActivity.setLocationIntegration(integration.string, customOsmServerUrl)
                        mapIntegrationPreference.value = integration.string
                    }
                }).show()
                true
            }
        }


        val disableAddressPreference = screen.findPreference<SwitchPreference>(SettingsActivity.PREF_KEY_LOCATION_DISABLE_ADDRESS_LOOKUP)
        val customAddressServerPreference = screen.findPreference<NoClickSwitchPreference>(SettingsActivity.PREF_KEY_LOCATION_USE_CUSTOM_ADDRESS_SERVER)
        if (disableAddressPreference != null && customAddressServerPreference != null) {
            disableAddressPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                if (newValue is Boolean) {
                    customAddressServerPreference.isVisible = (!newValue)
                }
                true
            }

            customAddressServerPreference.isVisible = !SettingsActivity.locationDisableAddressLookup
            customAddressServerPreference.summary = SettingsActivity.locationCustomAddressServer

            customAddressServerPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
                if (SettingsActivity.locationCustomAddressServer == null) {
                    context?.let { context ->
                        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view_message_and_input, null)

                        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
                        messageTextView.text = context.getString(R.string.dialog_message_custom_address_server_url).formatMarkdown()

                        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.dialog_text_layout)
                        textInputLayout.setHint(R.string.hint_custom_address_server_url)

                        val editText = dialogView.findViewById<TextInputEditText>(R.id.dialog_edittext)
                        editText.setText(SettingsActivity.locationCustomAddressServerEvenIfDisabled)
                        editText.inputType = InputType.TYPE_TEXT_VARIATION_URI

                        val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_custom_address_server_url)
                            .setView(dialogView)
                            .setPositiveButton(R.string.button_label_ok) { _, _ ->
                                editText.text?.toString()?.let {
                                    SettingsActivity.locationCustomAddressServer = it
                                    customAddressServerPreference.isChecked = true
                                    customAddressServerPreference.summary = it
                                }
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)

                        builder.create().show()
                    }
                } else {
                    customAddressServerPreference.isChecked = false
                    customAddressServerPreference.summary = null
                }
                true
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(resources.getColor(R.color.dialogBackground))
    }
}
