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

package io.olvid.messenger.openid.jsons;


import org.jose4j.jwk.JsonWebKey;

import java.util.List;

import io.olvid.engine.engine.types.JsonKeycloakUserDetails;

public class KeycloakUserDetailsAndStuff {
    public final JsonKeycloakUserDetails userDetails;
    public final String signedUserDetails;
    public final JsonWebKey signatureKey;
    public final String server;
    public final String apiKey;
    public final List<String> pushTopics;
    public final String selfRevocationTestNonce;

    public KeycloakUserDetailsAndStuff(JsonKeycloakUserDetails userDetails, String signedUserDetails, JsonWebKey signatureKey, String server, String apiKey, List<String> pushTopics, String selfRevocationTestNonce) {
        this.userDetails = userDetails;
        this.signedUserDetails = signedUserDetails;
        this.signatureKey = signatureKey;
        this.server = server;
        this.apiKey = apiKey;
        this.pushTopics = pushTopics;
        this.selfRevocationTestNonce = selfRevocationTestNonce;
    }
}
