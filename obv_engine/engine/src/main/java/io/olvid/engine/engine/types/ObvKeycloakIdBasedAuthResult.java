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

package io.olvid.engine.engine.types;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ObvKeycloakIdBasedAuthResult {
    public final Status status;
    public final String accessToken; // null in case of error
    public final String refreshToken; // null in case of error or if there is no possibility to refresh the accessToekn
    public final String clientId; // null in case of error or if refreshToken is null
    public final String clientSecret; // null in case of error or if refreshToken is null (or the openId Connect client has no clientSecret)

    public ObvKeycloakIdBasedAuthResult(Status status) {
        this(status, null, null, null, null);
    }

    public ObvKeycloakIdBasedAuthResult(Status status, String accessToken, String refreshToken, String clientId, String clientSecret) {
        this.status = status;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public enum Status {
        SUCCESS,
        NETWORK_ERROR,
        PERMANENT_ERROR,
        ERROR,
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GetSessionResponse {
        @JsonProperty("access_token")
        public String accessToken;
        @JsonProperty("refresh_token")
        public String refreshToken;
        @JsonProperty("client_id")
        public String clientId;
        @JsonProperty("client_secret")
        public String clientSecret;
    }
}
