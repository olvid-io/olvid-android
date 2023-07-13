/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.customClasses;


import android.content.Context;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.Objects;

import io.olvid.messenger.R;
import io.olvid.messenger.settings.SettingsActivity;

public class LocationIntegrationSelectorDialog {
    @NonNull private final AlertDialog.Builder builder;

    public LocationIntegrationSelectorDialog(@NonNull Context context, @NonNull OnIntegrationSelectedListener onIntegrationSelectedListener) {
        final String[] providers = context.getResources().getStringArray(R.array.location_integration_values);
        final String[] providerLabels = context.getResources().getStringArray(R.array.location_integration);

        int initiallySelected = -1;
        String integrationString = SettingsActivity.getLocationIntegration().getString();
        for (int i=0; i< providers.length; i++) {
            if (Objects.equals(integrationString, providers[i])) {
                initiallySelected = i;
                break;
            }
        }

        SelectableArrayAdapter<String> providerAdapter = new SelectableArrayAdapter<>(context, initiallySelected, providerLabels);

        builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                .setTitle(R.string.pref_location_integration_title)
                .setAdapter(providerAdapter, (dialog, which) -> {
                    if (which < 0 || which > providers.length) {
                        return;
                    }

                    switch (providers[which]) {
                        case SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_OSM: {
                            onIntegrationSelectedListener.onIntegrationSelected(SettingsActivity.LocationIntegrationEnum.OSM);
                            break;
                        }
                        case SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_MAPS: {
                            onIntegrationSelectedListener.onIntegrationSelected(SettingsActivity.LocationIntegrationEnum.MAPS);
                            break;
                        }
                        case SettingsActivity.PREF_VALUE_LOCATION_INTEGRATION_BASIC: {
                            onIntegrationSelectedListener.onIntegrationSelected(SettingsActivity.LocationIntegrationEnum.BASIC);
                            break;
                        }
                        default: {
                            // do nothing
                        }
                    }
                })
                .setNegativeButton(R.string.button_label_cancel, null);

        int fourDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(3*fourDp, 2*fourDp, 3*fourDp, fourDp);

        TextView disclaimerTextView = new TextView(context);
        disclaimerTextView.setBackgroundResource(R.drawable.background_info_message);
        disclaimerTextView.setPadding(2*fourDp, fourDp, 2*fourDp, fourDp);
        disclaimerTextView.setText(R.string.text_explanation_location_map_provider);
        disclaimerTextView.setTextColor(ContextCompat.getColor(context, R.color.primary700));
        linearLayout.addView(disclaimerTextView);

        TextView settingsTextView = new TextView(context);
        settingsTextView.setPadding(2*fourDp, 2*fourDp, 2*fourDp, 0);
        settingsTextView.setText(R.string.text_explanation_location_map_provider_settings);
        linearLayout.addView(settingsTextView);


        builder.setView(linearLayout);
    }

    public void show() {
        builder.create().show();
    }


    public interface OnIntegrationSelectedListener {
        void onIntegrationSelected(SettingsActivity.LocationIntegrationEnum itegration);
    }
}
