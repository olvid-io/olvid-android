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
package io.olvid.messenger.customClasses

import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.text.Spannable
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.olvid.messenger.R
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum

class LocationIntegrationSelectorDialog(
    private val context: Context,
    hideSettingsBottomMessage: Boolean,
    private val onIntegrationSelectedListener: OnIntegrationSelectedListener
) {
    private val builder: AlertDialog.Builder

    init {
        val providers = context.resources.getStringArray(R.array.location_integration_values)
        val providerLabels = context.resources.getStringArray(R.array.location_integration)

        var initiallySelected = -1
        val integrationString = SettingsActivity.locationIntegration.string
        for (i in providers.indices) {
            if (integrationString == providers[i]) {
                initiallySelected = i
                break
            }
        }

        val providerAdapter = SelectableArrayAdapter(
            context, initiallySelected, providerLabels
        )

        builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
            .setTitle(R.string.pref_location_integration_title)
            .setAdapter(providerAdapter) { dialog: DialogInterface?, which: Int ->
                if (which < 0 || which > providers.size) {
                    return@setAdapter
                }
                when (providers[which]) {
                    SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_OSM -> {
                        onIntegrationSelectedListener.onIntegrationSelected(
                            LocationIntegrationEnum.OSM,
                            null
                        )
                    }

                    SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_MAPS -> {
                        onIntegrationSelectedListener.onIntegrationSelected(
                            LocationIntegrationEnum.MAPS,
                            null
                        )
                    }

                    SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_BASIC -> {
                        onIntegrationSelectedListener.onIntegrationSelected(
                            LocationIntegrationEnum.BASIC,
                            null
                        )
                    }

                    SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_CUSTOM_OSM -> {
                        openCustomInput()
                    }

                    else -> {}
                }
            }
            .setNegativeButton(R.string.button_label_cancel, null)

        val fourDp = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()

        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.setPadding(3 * fourDp, 2 * fourDp, 3 * fourDp, fourDp)

        val disclaimerTextView = TextView(context)
        disclaimerTextView.setBackgroundResource(R.drawable.background_info_message)
        disclaimerTextView.setPadding(2 * fourDp, fourDp, 2 * fourDp, fourDp)
        disclaimerTextView.setText(R.string.text_explanation_location_map_provider)
        disclaimerTextView.setTextColor(ContextCompat.getColor(context, R.color.primary700))
        linearLayout.addView(disclaimerTextView)

        if (!hideSettingsBottomMessage) {
            val settingsTextView = TextView(context)
            settingsTextView.setPadding(2 * fourDp, 2 * fourDp, 2 * fourDp, 0)
            settingsTextView.setText(R.string.text_explanation_location_map_provider_settings)
            linearLayout.addView(settingsTextView)
        }

        builder.setView(linearLayout)
    }

    fun show() {
        builder.create().show()
    }

    private fun openCustomInput() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view_message_and_input, null)

        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        messageTextView.text = context.getString(R.string.dialog_message_custom_osm_url).formatMarkdown(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.dialog_text_layout)
        textInputLayout.setHint(R.string.hint_custom_osm_url)

        val editText = dialogView.findViewById<TextInputEditText>(R.id.dialog_edittext)
        editText.setText(SettingsActivity.locationCustomOsmServerUrl)
        editText.inputType = InputType.TYPE_TEXT_VARIATION_URI

        val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_custom_osm_url)
            .setView(dialogView)
            .setPositiveButton(R.string.button_label_ok) { _,_ ->
                editText.text?.toString()?.let {
                    onIntegrationSelectedListener.onIntegrationSelected(
                        LocationIntegrationEnum.CUSTOM_OSM,
                        it
                    )
                }
            }
            .setNegativeButton(R.string.button_label_cancel, null)

        builder.create().show()
    }

    interface OnIntegrationSelectedListener {
        fun onIntegrationSelected(integration: LocationIntegrationEnum, customOsmServerUrl: String?)
    }
}
