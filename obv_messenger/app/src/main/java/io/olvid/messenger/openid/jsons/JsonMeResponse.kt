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
class JsonMeResponse {
    var signature: String? = null
    var server: String? = null

    @JsonProperty("revocation-allowed")
    var revocationAllowed: Boolean? = null

    @JsonProperty("transfer-restricted")
    var transferRestricted: Boolean? = null

    @JsonProperty("api-key")
    var apiKey: String? = null

    @JsonProperty("nonce")
    var selfRevocationTestNonce: String? = null

    @JsonProperty("push-topics")
    var pushTopics: MutableList<String>? = null

    @JsonProperty("signed-revocations")
    var signedRevocations: MutableList<String>? = null

    @JsonProperty("current-timestamp")
    var currentTimestamp: Long = 0

    @JsonProperty("min-build-versions")
    var minimumBuildVersions: MutableMap<String, Int>? = null
}
