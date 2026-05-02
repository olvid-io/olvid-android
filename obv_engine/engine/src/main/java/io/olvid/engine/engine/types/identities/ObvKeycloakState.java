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

package io.olvid.engine.engine.types.identities;


import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.lang.JoseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ObvKeycloakState {
    public final String keycloakServer; // non-null
    public final List<ObvKeycloakAuthType> supportedAuthenticationMethods;
    public final JsonWebKeySet jwks; // non-null --> only set to null when sending to app and deserialization failed
    public final JsonWebKey signatureKey; // non-null --> only set to null when sending to app and deserialization failed
    public final String serializedAuthState; // device dependant --> do not share with other devices
    public final boolean transferRestricted;
    public final String ownApiKey; // not included in the serialized version
    public final long latestRevocationListTimestamp; // not included in the serialized version
    public final long latestGroupUpdateTimestamp; // not included in the serialized version

    public ObvKeycloakState(String keycloakServer, List<ObvKeycloakAuthType> supportedAuthenticationMethods, JsonWebKeySet jwks, JsonWebKey signatureKey, String serializedAuthState, boolean transferRestricted, String ownApiKey, long latestRevocationListTimestamp, long latestGroupUpdateTimestamp) {
        this.keycloakServer = keycloakServer;
        this.supportedAuthenticationMethods = supportedAuthenticationMethods;
        this.jwks = jwks;
        this.signatureKey = signatureKey;
        this.serializedAuthState = serializedAuthState;
        this.transferRestricted = transferRestricted;
        this.ownApiKey = ownApiKey;
        this.latestRevocationListTimestamp = latestRevocationListTimestamp;
        this.latestGroupUpdateTimestamp = latestGroupUpdateTimestamp;
    }

    public Encoded encode() {
        HashMap<DictionaryKey, Encoded> dict = new HashMap<>();
        if (keycloakServer != null) {
            dict.put(new DictionaryKey("ks"), Encoded.of(keycloakServer));
        }
        for (ObvKeycloakAuthType authType : supportedAuthenticationMethods) {
            if (authType instanceof ObvKeycloakAuthType.IdBased idBased) {
                dict.put(new DictionaryKey("ida"), Encoded.of(true));
            } else if (authType instanceof ObvKeycloakAuthType.OpenIdConnect oidc) {
                if (oidc.clientId != null) {
                    dict.put(new DictionaryKey("ci"), Encoded.of(oidc.clientId));
                }
                if (oidc.clientSecret != null) {
                    dict.put(new DictionaryKey("cs"), Encoded.of(oidc.clientSecret));
                }
            }
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
        if (transferRestricted) {
            dict.put(new DictionaryKey("tr"), Encoded.of(transferRestricted));
        }
        return Encoded.of(dict);
    }

    public static ObvKeycloakState of(Encoded encoded) throws DecodingException {
        final String keycloakServer;
        final List<ObvKeycloakAuthType> supportedAuthenticationMethods = new ArrayList<>();
        JsonWebKeySet jwks;
        JsonWebKey signatureKey;
        final String serializedAuthState;
        boolean transferRestricted;

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
            String clientId = encodedValue.decodeString();

            key = new DictionaryKey("cs");
            encodedValue = dict.get(key);
            String clientSecret = (encodedValue != null) ? encodedValue.decodeString() : null;

            supportedAuthenticationMethods.add(new ObvKeycloakAuthType.OpenIdConnect(clientId, clientSecret));
        }
        key = new DictionaryKey("ida");
        encodedValue = dict.get(key);
        if (encodedValue != null && encodedValue.decodeBoolean()) {
            supportedAuthenticationMethods.add(new ObvKeycloakAuthType.IdBased());
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
        key = new DictionaryKey("tr");
        encodedValue = dict.get(key);
        if (encodedValue != null) {
             transferRestricted = encodedValue.decodeBoolean();
        } else {
            transferRestricted = false;
        }
        return new ObvKeycloakState(keycloakServer, supportedAuthenticationMethods, jwks, signatureKey, serializedAuthState, transferRestricted, null, 0, 0);
    }

}
