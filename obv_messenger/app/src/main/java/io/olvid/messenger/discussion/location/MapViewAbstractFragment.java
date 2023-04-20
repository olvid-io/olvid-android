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

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;

import com.mapbox.geojson.constants.GeoJsonConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class MapViewAbstractFragment extends Fragment {
    // any early interaction with the map must be in the callback runnable (else map is not ready and it won't work)
    abstract void setOnMapReadyCallback(@Nullable Runnable callback);

    // interact with user position
    @RequiresPermission(anyOf = {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"})
    abstract boolean setEnableCurrentLocation(boolean enable); // check that location permission are ok and location is enabled
    @RequiresPermission(anyOf = {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"})
    abstract void centerOnCurrentLocation(boolean animate);

    // interact with map
    @NonNull abstract MutableLiveData<LatLngWrapper> getCurrentCameraCenterLiveData(); // updated every time camera stop somewhere (null during movements)
    abstract double getCameraZoom();
    abstract void launchMapSnapshot(@NonNull Consumer<Bitmap> onSnapshotReadyCallback);

    // customize map
    abstract void setGestureEnabled(boolean enabled);
    abstract void setOnMapClickListener(Runnable clickListener);
    abstract void setOnMapLongClickListener(Runnable clickListener);

    // markers api (use first call in onMapReadyCallback)
    abstract void addMarker(long id, Bitmap icon, @NonNull LatLngWrapper latLngWrapper, @Nullable Float precision);
    abstract void updateMarker(long id, @NonNull LatLngWrapper latLngWrapper, @Nullable Float precision); // use message.id as a unique id
    abstract void removeMarker(long id); // use message.id as a unique id

    abstract void centerOnMarkers(boolean animate, boolean includeMyLocation); // center on all markers currently shown
    abstract void centerOnMarker(long id, @SuppressWarnings("SameParameterValue") boolean animate); // center on a specific marker use message.id as unique id


    protected final Pair<LatLngWrapper, LatLngWrapper> computeBounds(List<LatLngWrapper> positions) {
        if (positions == null || positions.size() == 0) {
            return null;
        } else if (positions.size() == 1) {
            return new Pair<>(positions.get(0), null);
        } else {
            double latNorth = GeoJsonConstants.MIN_LATITUDE;
            double latSouth = GeoJsonConstants.MAX_LATITUDE;
            ArrayList<Double> longitudes = new ArrayList<>(positions.size());
            for (LatLngWrapper position : positions) {
                latNorth = Math.max(latNorth, position.getLatitude());
                latSouth = Math.min(latSouth, position.getLatitude());
                longitudes.add(position.getLongitude());
            }
            // now sort the longitudes to get the offset of the largest gap
            Collections.sort(longitudes);
            int largestGapIndex = 0;
            double largestGapValue = 0;
            for (int i = 0; i < longitudes.size(); i++) {
                double gap = longitudes.get((i + 1) % longitudes.size()) - longitudes.get(i);
                if (gap < 0) {
                    gap += 360;
                }
                if (gap > largestGapValue) {
                    largestGapIndex = i;
                    largestGapValue = gap;
                }
            }

            double west = longitudes.get((largestGapIndex + 1) % longitudes.size());
            double east = longitudes.get(largestGapIndex);
            if (east < west) {
                east += 360;
            }

            if ((360 - largestGapValue) < 0.005 && (latNorth - latSouth) < 0.05) {
                return new Pair<>(
                        new LatLngWrapper((latSouth + latNorth) / 2,
                                (west + east) / 2),
                        null);
            } else {
                return new Pair<>(new LatLngWrapper(latSouth, west), new LatLngWrapper(latNorth, east));
            }
        }
    }
}
