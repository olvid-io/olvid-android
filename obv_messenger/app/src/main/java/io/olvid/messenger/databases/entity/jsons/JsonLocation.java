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

package io.olvid.messenger.databases.entity.jsons;

import android.content.Context;
import android.location.Location;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import io.olvid.messenger.R;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonLocation {
    @JsonIgnore
    public static final int TYPE_SEND = 1;
    @JsonIgnore
    public static final int TYPE_SHARING = 2;
    @JsonIgnore
    public static final int TYPE_END_SHARING = 3;

    // -- message metadata --
    public int type;
    public long timestamp; // location timestamp
    // -- sharing message fields --
    public Long count; // null if not sharing
    public Long sharingInterval; // null if not sharing (else in ms)
    public Long sharingExpiration; // can be null if endless sharing (else in ms)
    // -- location --
    public double latitude;
    public double longitude;
    // -- optional metadata --
    public Double altitude; // meters (default value null)
    public Float precision; // meters (default value null)
    public String address; // (default value empty string or null)

    public JsonLocation() {
    }

    public JsonLocation(int type, @Nullable Long sharingExpiration, @Nullable Long sharingInterval, @Nullable Long count, double latitude, double longitude, Double altitude, Float precision, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.precision = precision;
        this.timestamp = timestamp;
        this.type = type;
        this.count = count;
        this.sharingExpiration = sharingExpiration;
        this.sharingInterval = sharingInterval;
    }

    public static JsonLocation startSharingLocationMessage(@Nullable Long sharingExpiration, @NotNull Long interval, @NotNull Location location) {
        return new JsonLocation(
                TYPE_SHARING,
                sharingExpiration,
                interval,
                1L,
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : null,
                location.hasAccuracy() ? location.getAccuracy() : null,
                location.getTime()
        );
    }

    public static JsonLocation updateSharingLocationMessage(@NotNull JsonLocation originalJsonLocation, @NotNull Location location) {
        return new JsonLocation(
                TYPE_SHARING,
                originalJsonLocation.getSharingExpiration(),
                originalJsonLocation.getSharingInterval(),
                originalJsonLocation.getCount() + 1,
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : null,
                location.hasAccuracy() ? location.getAccuracy() : null,
                location.getTime()
        );
    }

    public static JsonLocation endOfSharingLocationMessage(@NotNull Long count) {
        JsonLocation endOfSharingJsonLocation = new JsonLocation();
        endOfSharingJsonLocation.type = TYPE_END_SHARING;
        endOfSharingJsonLocation.count = count;
        return endOfSharingJsonLocation;
    }

    public static JsonLocation sendLocationMessage(@NotNull Location location) {
        return new JsonLocation(
                TYPE_SEND,
                null,
                null,
                null,
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : null,
                location.hasAccuracy() ? location.getAccuracy() : null,
                location.getTime()
        );
    }

    // ----- sharing message metadata -----
    @JsonProperty("c")
    public Long getCount() {
        return count;
    }

    @JsonProperty("c")
    public void setCount(Long count) {
        this.count = count;
    }

    @JsonProperty("se")
    public Long getSharingExpiration() {
        return sharingExpiration;
    }

    @JsonProperty("se")
    public void setSharingExpiration(Long sharingExpiration) {
        this.sharingExpiration = sharingExpiration;
    }

    @JsonProperty("i")
    public Long getSharingInterval() {
        return sharingInterval;
    }

    @JsonProperty("i")
    public void setSharingInterval(Long sharingInterval) {
        this.sharingInterval = sharingInterval;
    }

    // -- message metadata --
    @JsonProperty("t")
    public int getType() {
        return type;
    }

    @JsonProperty("t")
    public void setType(int type) {
        this.type = type;
    }

    @JsonProperty("ts")
    public long getTimestamp() {
        return timestamp;
    }

    @JsonProperty("ts")
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // ----- location -----
    @JsonProperty("long")
    public double getLongitude() {
        return longitude;
    }

    @JsonProperty("long")
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @JsonProperty("lat")
    public double getLatitude() {
        return latitude;
    }

    @JsonProperty("lat")
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    // ----- optional metadata -----
    @JsonProperty("alt")
    public Double getAltitude() {
        return altitude;
    }

    @JsonProperty("alt")
    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    @JsonProperty("prec")
    public Float getPrecision() {
        return precision;
    }

    @JsonProperty("prec")
    public void setPrecision(Float accuracy) {
        this.precision = accuracy;
    }

    @JsonProperty("add")
    public String getAddress() {
        return address;
    }

    @JsonProperty("add")
    public void setAddress(String address) {
        this.address = address;
    }


    ////////////
    // formatters

    private final static DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
    private final static DecimalFormat truncated5 = new DecimalFormat("#0.00000", decimalSymbols);
    private final static DecimalFormat truncated1 = new DecimalFormat("#0.0", decimalSymbols);
    private final static DecimalFormat truncated0 = new DecimalFormat("#0", decimalSymbols);

    @JsonIgnore
    public String getLocationMessageBody() {
        StringBuilder stringBuilder = new StringBuilder();
        if (this.address != null && !this.address.isEmpty()) {
            stringBuilder.append(address);
            stringBuilder.append("\n\n");
        }
        stringBuilder.append("https://maps.google.com/?q=");
        stringBuilder.append(this.getTruncatedLatitudeString());
        stringBuilder.append("+");
        stringBuilder.append(this.getTruncatedLongitudeString());
        return stringBuilder.toString();
    }

    // create an android Location object from jsonLocation content
    @JsonIgnore
    public Location getAsLocation() {
        Location location = new Location("");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAltitude(altitude != null ? altitude : 0);
        return location;
    }

    @JsonIgnore
    public String getTruncatedLatitudeString() {
        return truncated5.format(latitude);
    }

    @JsonIgnore
    public String getTruncatedLongitudeString() {
        return truncated5.format(longitude);
    }

    @JsonIgnore
    public String getTruncatedPrecisionString(Context context) {
        if (precision == null) {
            return "-";
        }
        return context.getString(R.string.xx_meters, truncated1.format(precision));
    }

    @JsonIgnore
    public String getTruncatedAltitudeString(Context context) {
        if (altitude == null) {
            return "-";
        }
        return context.getString(R.string.xx_meters, truncated0.format(altitude));
    }
}
