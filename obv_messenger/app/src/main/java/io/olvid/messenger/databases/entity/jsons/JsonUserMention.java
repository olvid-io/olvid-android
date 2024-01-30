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

package io.olvid.messenger.databases.entity.jsons;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonUserMention {
    byte[] userIdentifier;
    int rangeStart;
    int rangeEnd;

    public JsonUserMention() {
    }

    @JsonIgnore
    public int getLength() {
        return getRangeEnd() - getRangeStart();
    }

    public JsonUserMention(byte[] userIdentifier, int rangeStart, int rangeEnd) {
        this.userIdentifier = userIdentifier;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    @JsonProperty("uid")
    public byte[] getUserIdentifier() {
        return userIdentifier;
    }

    @JsonProperty("uid")
    public void setUserIdentifier(byte[] uid) {
        this.userIdentifier = uid;
    }

    @JsonProperty("rs")
    public int getRangeStart() {
        return rangeStart;
    }

    @JsonProperty("rs")
    public void setRangeStart(int index) {
        this.rangeStart = index;
    }

    @JsonProperty("re")
    public int getRangeEnd() {
        return rangeEnd;
    }

    @JsonProperty("re")
    public void setRangeEnd(int index) {
        this.rangeEnd = index;
    }

    @JsonIgnore
    @Override
    public int hashCode() {
        return (rangeStart * 31 + rangeEnd) * 31 + Arrays.hashCode(userIdentifier);
    }

    @JsonIgnore
    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof JsonUserMention)) {
            return false;
        }
        JsonUserMention other = (JsonUserMention) obj;
        return (rangeStart == other.rangeStart) && (rangeEnd == other.rangeEnd) && Arrays.equals(userIdentifier, other.userIdentifier);
    }
}
