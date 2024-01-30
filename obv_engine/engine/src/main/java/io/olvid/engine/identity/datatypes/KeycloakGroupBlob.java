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

package io.olvid.engine.identity.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;

import io.olvid.engine.engine.types.JsonGroupDetails;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakGroupBlob {
    public byte[] bytesGroupUid; // 32-bytes UID
    public JsonGroupDetails groupDetails;
    public byte[] photoUid;
    public byte[] encodedPhotoKey;
    public String pushTopic;
    public HashSet<KeycloakGroupMemberAndPermissions> groupMembersAndPermissions;
    public String serializedSharedSettings;
    public long timestamp;

    public KeycloakGroupBlob() {
    }

    @JsonProperty("guid")
    public byte[] getBytesGroupUid() {
        return bytesGroupUid;
    }

    @JsonProperty("guid")
    public void setBytesGroupUid(byte[] bytesGroupUid) {
        this.bytesGroupUid = bytesGroupUid;
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
    public byte[] getPhotoUid() {
        return photoUid;
    }

    @JsonProperty("photo_label")
    public void setPhotoUid(byte[] photoUid) {
        this.photoUid = photoUid;
    }

    @JsonProperty("photo_key")
    public byte[] getEncodedPhotoKey() {
        return encodedPhotoKey;
    }

    @JsonProperty("photo_key")
    public void setEncodedPhotoKey(byte[] encodedPhotoKey) {
        this.encodedPhotoKey = encodedPhotoKey;
    }

    @JsonProperty("pt")
    public String getPushTopic() {
        return pushTopic;
    }

    @JsonProperty("pt")
    public void setPushTopic(String pushTopic) {
        this.pushTopic = pushTopic;
    }

    @JsonProperty("gm_perms")
    public HashSet<KeycloakGroupMemberAndPermissions> getGroupMembersAndPermissions() {
        return groupMembersAndPermissions;
    }

    @JsonProperty("gm_perms")
    public void setGroupMembersAndPermissions(HashSet<KeycloakGroupMemberAndPermissions> groupMembersAndPermissions) {
        this.groupMembersAndPermissions = groupMembersAndPermissions;
    }

    @JsonProperty("sss")
    public String getSerializedSharedSettings() {
        return serializedSharedSettings;
    }

    @JsonProperty("sss")
    public void setSerializedSharedSettings(String serializedSharedSettings) {
        this.serializedSharedSettings = serializedSharedSettings;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
