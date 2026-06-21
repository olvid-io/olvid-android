/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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
class JsonMagicResponse {
    @JsonProperty("access_token")
    var accessToken: String? = null
    @JsonProperty("refresh_token")
    var refreshToken: String? = null

    // if a refreshToken is provided, the clientId should be here too (except on Keycloak Olvid plugin versions before 4.6.2)
    @JsonProperty("client_id")
    var clientId: String? = null
    @JsonProperty("client_secret")
    var clientSecret: String? = null
}