/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.openid.jsons;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonGroupsResponse {
    List<String> signedGroupBlobs;
    List<String> signedGroupDeletions;
    List<String> signedGroupKicks;
    long currentTimestamp;

    @JsonProperty("signed_group_blobs")
    public List<String> getSignedGroupBlobs() {
        return signedGroupBlobs;
    }
    @JsonProperty("signed_group_blobs")
    public void setSignedGroupBlobs(List<String> signedGroupBlobs) {
        this.signedGroupBlobs = signedGroupBlobs;
    }

    @JsonProperty("signed_group_deletions")
    public List<String> getSignedGroupDeletions() {
        return signedGroupDeletions;
    }

    @JsonProperty("signed_group_deletions")
    public void setSignedGroupDeletions(List<String> signedGroupDeletions) {
        this.signedGroupDeletions = signedGroupDeletions;
    }

    @JsonProperty("signed_group_kicks")
    public List<String> getSignedGroupKicks() {
        return signedGroupKicks;
    }

    @JsonProperty("signed_group_kicks")
    public void setSignedGroupKicks(List<String> signedGroupKicks) {
        this.signedGroupKicks = signedGroupKicks;
    }

    @JsonProperty("current_timestamp")
    public long getCurrentTimestamp() {
        return currentTimestamp;
    }

    @JsonProperty("current_timestamp")
    public void setCurrentTimestamp(long currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
    }
}
