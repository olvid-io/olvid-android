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

package io.olvid.engine.engine.types.identities;


import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.lang.JoseException;

import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ObvKeycloakState {
    public final String keycloakServer;
    public final String clientId;
    public final String clientSecret;
    public final JsonWebKeySet jwks;
    public final JsonWebKey signatureKey;
    public final String serializedAuthState;
    public final long latestRevocationListTimestamp; // not included in the serialized version
    public final long latestGroupUpdateTimestamp; // not included in the serialized version

    public ObvKeycloakState(String keycloakServer, String clientId, String clientSecret, JsonWebKeySet jwks, JsonWebKey signatureKey, String serializedAuthState, long latestRevocationListTimestamp, long latestGroupUpdateTimestamp) {
        this.keycloakServer = keycloakServer;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.jwks = jwks;
        this.signatureKey = signatureKey;
        this.serializedAuthState = serializedAuthState;
        this.latestRevocationListTimestamp = latestRevocationListTimestamp;
        this.latestGroupUpdateTimestamp = latestGroupUpdateTimestamp;
    }

    public Encoded encode() {
        HashMap<DictionaryKey, Encoded> dict = new HashMap<>();
        if (keycloakServer != null) {
            dict.put(new DictionaryKey("ks"), Encoded.of(keycloakServer));
        }
        if (clientId != null) {
            dict.put(new DictionaryKey("ci"), Encoded.of(clientId));
        }
        if (clientSecret != null) {
            dict.put(new DictionaryKey("cs"), Encoded.of(clientSecret));
        }
        if (jwks != null) {
            dict.put(new DictionaryKey("jwks"), Encoded.of(jwks.toJson()));
        }
        if (signatureKey != null) {
            dict.put(new DictionaryKey("sk"), Encoded.of(signatureKey.toJson()));
        }
        if (serializedAuthState != null) {
            dict.put(new DictionaryKey("sas"), Encoded.of(serializedAuthState));
        }
        return Encoded.of(dict);
    }

    public static ObvKeycloakState of(Encoded encoded) throws DecodingException {
        final String keycloakServer;
        final String clientId;
        final String clientSecret;
        JsonWebKeySet jwks;
        JsonWebKey signatureKey;
        final String serializedAuthState;

        HashMap<DictionaryKey, Encoded> dict = encoded.decodeDictionary();
        DictionaryKey key = new DictionaryKey("ks");
        Encoded encodedValue = dict.get(key);
        if (encodedValue != null) {
            keycloakServer = encodedValue.decodeString();
        } else {
            keycloakServer = null;
        }
        key = new DictionaryKey("ci");
        encodedValue = dict.get(key);
        if (encodedValue != null) {
            clientId = encodedValue.decodeString();
        } else {
            clientId = null;
        }
        key = new DictionaryKey("cs");
        encodedValue = dict.get(key);
        if (encodedValue != null) {
            clientSecret = encodedValue.decodeString();
        } else {
            clientSecret = null;
        }
        key = new DictionaryKey("jwks");
        encodedValue = dict.get(key);
        if (encodedValue != null) {
            try {
                jwks = new JsonWebKeySet(encodedValue.decodeString());
            } catch (JoseException e) {
                jwks = null;
            }
        } else {
            jwks = null;
        }
        key = new DictionaryKey("sk");
        encodedValue = dict.get(key);
        if (encodedValue != null) {
            try {
                signatureKey = JsonWebKey.Factory.newJwk(encodedValue.decodeString());
            } catch (JoseException e) {
                signatureKey = null;
            }
        } else {
            signatureKey = null;
        }
        key = new DictionaryKey("sas");
        encodedValue = dict.get(key);
        if (encodedValue != null) {
            serializedAuthState = encodedValue.decodeString();
        } else {
            serializedAuthState = null;
        }

        return new ObvKeycloakState(keycloakServer, clientId, clientSecret, jwks, signatureKey, serializedAuthState, 0, 0);
    }
}
