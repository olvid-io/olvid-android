/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog
import io.olvid.messenger.customClasses.NoClickSwitchPreference
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.services.GpsDebugLogger
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum
import java.nio.charset.StandardCharsets


class LocationPreferenceFragment : PreferenceFragmentCompat() {
    private var activity: FragmentActivity? = null
    private var exportGpsLogsLauncher: ActivityResultLauncher<String?>? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences_location, rootKey)
        activity = requireActivity()

        val screen = preferenceScreen ?: return

        val mapIntegrationPreference = screen.findPreference<ListPreference>(SettingsActivity.PREF_KEY_LOCATION_INTEGRATION)
        if (mapIntegrationPreference != null) {
            mapIntegrationPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { _: Preference? ->
                LocationIntegrationSelectorDialog(mapIntegrationPreference.context, object : LocationIntegrationSelectorDialog.OnIntegrationSelectedListener {
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
            disableAddressPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                if (newValue is Boolean) {
                    customAddressServerPreference.isVisible = (!newValue)
                }
                true
            }

            customAddressServerPreference.isVisible = !SettingsActivity.locationDisableAddressLookup
            customAddressServerPreference.summary = SettingsActivity.locationCustomAddressServer

            customAddressServerPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { _: Preference? ->
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

        val gpsDebugPreference = screen.findPreference<SwitchPreference>(SettingsActivity.PREF_KEY_LOCATION_GPS_DEBUG)
        val gpsAlwaysOnPreference = screen.findPreference<SwitchPreference>(SettingsActivity.PREF_KEY_LOCATION_GPS_ALWAYS_ON)
        val exportGpsLogsPreference = screen.findPreference<Preference>(SettingsActivity.PREF_KEY_LOCATION_EXPORT_GPS_LOGS)
        if (gpsDebugPreference != null && gpsAlwaysOnPreference != null && exportGpsLogsPreference != null) {
            if (SettingsActivity.betaFeaturesEnabled) {
                gpsDebugPreference.isVisible = true
                gpsAlwaysOnPreference.isVisible = true
                exportGpsLogsPreference.isVisible = true

                gpsDebugPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    // if disabling debug options while GPS is always on, also stop the notification
                    if ((newValue as? Boolean) == false && SettingsActivity.gpsAlwaysOn) {
                        gpsAlwaysOnPreference.isChecked = false
                        SettingsActivity.gpsAlwaysOn = false
                        Handler(Looper.getMainLooper()).postDelayed({
                            UnifiedForegroundService.LocationSharingSubService.stopSharingForGpsAlwaysOn()
                        }, 500)
                    }
                    true
                }

                gpsAlwaysOnPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    // start/stop the GPS with 1 second delau to give time for the preference to be saved
                    Handler(Looper.getMainLooper()).postDelayed(
                    {
                        if ((newValue as? Boolean) == true) {
                            GpsDebugLogger.logGpsEvent("Enabling GPS always on")
                            UnifiedForegroundService.LocationSharingSubService.startSharingForGpsAlwaysOn()
                        } else {
                            GpsDebugLogger.logGpsEvent("Disabling GPS always on")
                            UnifiedForegroundService.LocationSharingSubService.stopSharingForGpsAlwaysOn()
                        }
                    }, 500)
                    true
                }

                if (exportGpsLogsLauncher == null) {
                    exportGpsLogsLauncher = registerForActivityResult<String?, Uri?>(CreateDocument("text/plain"), this::onExportGpsLogsFileSelected)
                }

                exportGpsLogsPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
                    exportGpsLogsLauncher?.launch("olvid_gps_debug_log.txt")
                    true
                }
            }
        }
    }

    private fun onExportGpsLogsFileSelected(uri: Uri?) {
        if (uri == null) return
        if (!StringUtils.validateUri(uri)) return

        App.runThread {
            activity?.getContentResolver()?.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(
                    GpsDebugLogger.getLogsContent().toByteArray(StandardCharsets.UTF_8)
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.almostWhite))
    }
}
