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
package io.olvid.messenger.customClasses

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum

class LocationIntegrationSelectorDialog(
    context: Context,
    private val onIntegrationSelectedListener: OnIntegrationSelectedListener
) {
    private val builder: AlertDialog.Builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
    private var dialog: AlertDialog? = null

    init {
        val dialogView = ComposeView(context).apply {
            setContent {
                LocationIntegrationPicker(
                    initiallySelectedProvider = SettingsActivity.locationIntegration,
                    initialCustomOsmProvider = SettingsActivity.locationCustomOsmServerUrl,
                    onChooseProvider = { provider, customOsmProvider ->
                        onIntegrationSelectedListener.onIntegrationSelected(provider, customOsmProvider)
                        dialog?.dismiss()
                    },
                    onDismiss = {
                        dialog?.dismiss()
                    }
                )
            }
        }
        builder.setView(dialogView)
    }

    fun show() {
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    interface OnIntegrationSelectedListener {
        fun onIntegrationSelected(integration: LocationIntegrationEnum, customOsmServerUrl: String?)
    }
}

@Composable
private fun LocationIntegrationPicker(
    initiallySelectedProvider: LocationIntegrationEnum = LocationIntegrationEnum.NONE,
    initialCustomOsmProvider: String? = null,
    onChooseProvider: (provider: LocationIntegrationEnum, customOsmUrl: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCustomInputDialog by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(initiallySelectedProvider) }
    var customOsmProvider by remember { mutableStateOf(initialCustomOsmProvider) }


    BaseDialogContent(
        title = stringResource(R.string.dialog_title_choose_map_provider),
        content = {
            Text(
                text = stringResource(R.string.dialog_message_choose_map_provider).formatMarkdownToAnnotatedString(),
                style = OlvidTypography.body1,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        selectedProvider = LocationIntegrationEnum.OSM
                    }
                    .heightIn(min = 48.dp)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedProvider == LocationIntegrationEnum.OSM,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorResource(R.color.olvid_gradient_light),
                        unselectedColor = colorResource(R.color.almostBlack),
                    )
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.location_integration_open_street_map),
                    style = OlvidTypography.body1
                )
            }

            if (BuildConfig.USE_GOOGLE_LIBS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = ripple(),
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            selectedProvider = LocationIntegrationEnum.MAPS
                        }
                        .heightIn(min = 48.dp)
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedProvider == LocationIntegrationEnum.MAPS,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colorResource(R.color.olvid_gradient_light),
                            unselectedColor = colorResource(R.color.almostBlack),
                        )
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.location_integration_google_maps),
                        style = OlvidTypography.body1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        selectedProvider = LocationIntegrationEnum.BASIC
                    }
                    .heightIn(min = 48.dp)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedProvider == LocationIntegrationEnum.BASIC,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorResource(R.color.olvid_gradient_light),
                        unselectedColor = colorResource(R.color.almostBlack),
                    )
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.location_integration_basic),
                    style = OlvidTypography.body1
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showCustomInputDialog = true
                    }
                    .heightIn(min = 48.dp)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedProvider == LocationIntegrationEnum.CUSTOM_OSM,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorResource(R.color.olvid_gradient_light),
                        unselectedColor = colorResource(R.color.almostBlack),
                    )
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.location_integration_custom_osm),
                        style = OlvidTypography.body1
                    )
                    customOsmProvider?.let {
                        Text(
                            text = it,
                            style = OlvidTypography.subtitle1,
                            color = colorResource(R.color.greyTint),
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        actions = {
            Spacer(Modifier.weight(1f, true))
            OlvidTextButton(
                text = stringResource(R.string.button_label_cancel),
                onClick = onDismiss
            )
            Spacer(Modifier.width(8.dp))
            OlvidActionButton(
                text = stringResource(R.string.button_label_ok),
                enabled = selectedProvider != LocationIntegrationEnum.NONE &&
                        (selectedProvider != initiallySelectedProvider ||
                                (selectedProvider == LocationIntegrationEnum.CUSTOM_OSM && initialCustomOsmProvider != customOsmProvider)
                        ),
                onClick = {
                    onChooseProvider.invoke(selectedProvider, customOsmProvider)
                },
            )
        }
    )

    if (showCustomInputDialog) {
        DialogSecure(
            onDismissRequest = {
                @Suppress("AssignedValueIsNeverRead")
                showCustomInputDialog = false
            }
        ) {
            var url by remember { mutableStateOf(customOsmProvider ?: "") }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            BaseDialogContent(
                title = stringResource(R.string.dialog_title_custom_osm_url),
                content = {
                    Text(
                        text = stringResource(R.string.dialog_message_custom_osm_url).formatMarkdownToAnnotatedString(),
                        style = OlvidTypography.body2,
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        value = url,
                        shape = RoundedCornerShape(12.dp),
                        colors = olvidDefaultTextFieldColors(),
                        onValueChange = { url = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        label = {
                            Text(
                                text = stringResource(R.string.hint_custom_osm_url),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    )
                },
                actions = {
                    Spacer(Modifier.weight(1f, true))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel)
                    ) {
                        @Suppress("AssignedValueIsNeverRead")
                        showCustomInputDialog = false
                    }
                    Spacer(Modifier.width(8.dp))
                    OlvidActionButton(
                        text = stringResource(R.string.button_label_ok),
                        enabled = url.isBlank().not()
                    ) {
                        // save configuration
                        customOsmProvider = url
                        selectedProvider = LocationIntegrationEnum.CUSTOM_OSM
                        @Suppress("AssignedValueIsNeverRead")
                        showCustomInputDialog = false
                    }
                }
            )
        }
    }
}


@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun LocationIntegrationPickerPreview() {
    LocationIntegrationPicker(
        initialCustomOsmProvider = "https://map.olvid.io/custom_style.json",
        initiallySelectedProvider = LocationIntegrationEnum.MAPS,
        onChooseProvider = { _, _ -> },
        onDismiss = {},
    )
}