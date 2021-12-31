/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.engine.engine.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonGroupDetailsWithVersionAndPhoto {
    public static final String DUMMY_GROUP_DETAILS = "{\"details\":{\"name\":\"dummy\"}, \"version\": 0}";

    int version;
    JsonGroupDetails groupDetails;
    byte[] photoServerLabel;
    byte[] photoServerKey;
    String photoUrl; // this field will never be serialized

    public JsonGroupDetailsWithVersionAndPhoto() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @JsonProperty("details")
    public JsonGroupDetails getGroupDetails() {
        return groupDetails;
    }

    @JsonProperty("details")
    public void setGroupDetails(JsonGroupDetails groupDetails) {
        this.groupDetails = groupDetails;
    }

    @JsonProperty("photo_label")
    public byte[] getPhotoServerLabel() {
        return photoServerLabel;
    }

    @JsonProperty("photo_label")
    public void setPhotoServerLabel(byte[] photoServerLabel) {
        this.photoServerLabel = photoServerLabel;
    }

    @JsonProperty("photo_key")
    public byte[] getPhotoServerKey() {
        return photoServerKey;
    }

    @JsonProperty("photo_key")
    public void setPhotoServerKey(byte[] photoServerKey) {
        this.photoServerKey = photoServerKey;
    }

    @JsonIgnore // this field will never be serialized
    public String getPhotoUrl() {
        return photoUrl;
    }

    @JsonIgnore
    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
