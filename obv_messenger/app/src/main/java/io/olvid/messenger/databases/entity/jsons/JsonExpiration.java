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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonExpiration {
    public Long existenceDuration; // in seconds
    public Long visibilityDuration; // in seconds
    public Boolean readOnce;

    @JsonProperty("ex")
    public Long getExistenceDuration() {
        return existenceDuration;
    }

    @JsonProperty("ex")
    public void setExistenceDuration(Long existenceDuration) {
        this.existenceDuration = existenceDuration;
    }

    @JsonProperty("vis")
    public Long getVisibilityDuration() {
        return visibilityDuration;
    }

    @JsonProperty("vis")
    public void setVisibilityDuration(Long visibilityDuration) {
        this.visibilityDuration = visibilityDuration;
    }

    @JsonProperty("ro")
    public Boolean getReadOnce() {
        return readOnce;
    }

    @JsonProperty("ro")
    public void setReadOnce(Boolean readOnce) {
        this.readOnce = readOnce;
    }

    @JsonIgnore
    @NonNull
    public JsonExpiration computeGcd(@Nullable JsonExpiration jsonExpiration) {
        if (jsonExpiration != null) {
            readOnce = (readOnce != null && readOnce) || (jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce());
            if (jsonExpiration.getVisibilityDuration() != null) {
                if (visibilityDuration == null) {
                    visibilityDuration = jsonExpiration.getVisibilityDuration();
                } else {
                    visibilityDuration = Math.min(visibilityDuration, jsonExpiration.getVisibilityDuration());
                }
            }
            if (jsonExpiration.getExistenceDuration() != null) {
                if (existenceDuration == null) {
                    existenceDuration = jsonExpiration.getExistenceDuration();
                } else {
                    existenceDuration = Math.min(existenceDuration, jsonExpiration.getExistenceDuration());
                }
            }
        }
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof JsonExpiration)) {
            return false;
        }
        JsonExpiration other = (JsonExpiration) obj;
        if ((readOnce != null && readOnce) ^ (other.readOnce != null && other.readOnce)) {
            return false;
        }
        if (!Objects.equals(visibilityDuration, other.visibilityDuration)) {
            return false;
        }
        return Objects.equals(existenceDuration, other.existenceDuration);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean likeNull() {
        return (readOnce == null || !readOnce) && (visibilityDuration == null) && (existenceDuration == null);
    }
}
