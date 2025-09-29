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
package io.olvid.messenger.openid.jsons

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class JsonGroupsResponse {
    @JsonProperty("signed_group_blobs")
    var signedGroupBlobs: MutableList<String?>? = null

    @JsonProperty("signed_group_deletions")
    var signedGroupDeletions: MutableList<String?>? = null

    @JsonProperty("signed_group_kicks")
    var signedGroupKicks: MutableList<String?>? = null

    @JsonProperty("current_timestamp")
    var currentTimestamp: Long = 0
}
