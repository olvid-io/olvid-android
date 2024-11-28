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

package io.olvid.messenger.discussion.location;


import android.location.Location;

import io.olvid.messenger.databases.entity.jsons.JsonLocation;

// class used to encapsulate maplibre or google maps LatLng classes
public class LatLngWrapper {
    private double latitude;
    private double longitude;

    public LatLngWrapper(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // google version
    public LatLngWrapper(com.google.android.gms.maps.model.LatLng latLng) {
        latitude = latLng.latitude;
        longitude = latLng.longitude;
    }

    // maplibre version
    public LatLngWrapper(org.maplibre.android.geometry.LatLng latLng) {
        latitude = latLng.getLatitude();
        longitude = latLng.getLongitude();
    }

    public LatLngWrapper(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
    }

    // JsonLocation version
    public LatLngWrapper(JsonLocation jsonLocation) {
        latitude = jsonLocation.getLatitude();
        longitude = jsonLocation.getLongitude();
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Location toLocation() {
        Location location = new Location("");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(0);
        return location;
    }

    public org.maplibre.android.geometry.LatLng toMapLibre() {
        return new org.maplibre.android.geometry.LatLng(latitude, longitude);
    }

    public com.google.android.gms.maps.model.LatLng toGoogleMaps() {
        return new com.google.android.gms.maps.model.LatLng(latitude, longitude);
    }
}
