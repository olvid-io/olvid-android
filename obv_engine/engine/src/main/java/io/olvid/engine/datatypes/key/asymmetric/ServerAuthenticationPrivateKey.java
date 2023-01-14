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

package io.olvid.engine.datatypes.key.asymmetric;


import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.key.CryptographicKey;
import io.olvid.engine.encoder.Encoded;

public abstract class ServerAuthenticationPrivateKey extends PrivateKey {
    public ServerAuthenticationPrivateKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        super(CryptographicKey.ALGO_CLASS_SERVER_AUTHENTICATION, algorithmImplementation, key);
    }

    public static ServerAuthenticationPrivateKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                return new ServerAuthenticationECSdsaMDCPrivateKey(key);
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                return new ServerAuthenticationECSdsaCurve25519PrivateKey(key);
        }
        return null;
    }

    public SignaturePrivateKey getSignaturePrivateKey() throws Exception {
        switch (algorithmImplementation) {
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                return ((ServerAuthenticationECSdsaPrivateKey) this).getSignaturePrivateKey();
            default:
                throw new Exception("This server authentication private key does not implement signature");
        }
    }

}
