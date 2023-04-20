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

package io.olvid.messenger.discussion.location;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.mapbox.mapboxsdk.MapStrictMode;
import com.mapbox.mapboxsdk.maps.AttributionDialogManager;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import io.olvid.messenger.R;

// For MapLibre integration: creates a custom pop up when clicking on attribution button
public class MapLibreCustomAttributionDialogManager extends AttributionDialogManager {
    private final Context context;
    private final String[] attributionsNames = new String[]{
            "© OpenMapTiles",
            "© OpenStreetMap contributors",
            "© MapLibre",
            "© Olvid"
    };
    private final String[] attributionsUrls = new String[]{
            "https://www.openmaptiles.org/",
            "https://www.openstreetmap.org/copyright ",
            "https://maplibre.org/",
            "https://olvid.io"
    };

    public MapLibreCustomAttributionDialogManager(@NonNull Context context, @NonNull MapboxMap mapboxMap) {
        super(context, mapboxMap);
        this.context = context;
    }

    @Override
    protected void showAttributionDialog(@NonNull String[] attributionTitles) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_title_osm_attributions);
        builder.setAdapter(new ArrayAdapter<>(context, R.layout.maplibre_attribution_list_item, attributionsNames), this);
        builder.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(attributionsUrls[which]));
            context.startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            // explicitly handling if the device hasn't have a web browser installed. #8899
            Toast.makeText(context, R.string.maplibre_attributionErrorNoBrowser, Toast.LENGTH_LONG).show();
            MapStrictMode.strictModeViolation(exception);
        }
    }
}
