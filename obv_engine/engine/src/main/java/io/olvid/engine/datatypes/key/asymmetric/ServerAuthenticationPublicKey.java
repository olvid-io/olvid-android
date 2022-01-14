/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public abstract class ServerAuthenticationPublicKey extends PublicKey {
    public static final byte ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC = (byte) 0x00;
    public static final byte ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519 = (byte) 0x01;


    public ServerAuthenticationPublicKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        super(CryptographicKey.ALGO_CLASS_SERVER_AUTHENTICATION, algorithmImplementation, key);
    }

    public static ServerAuthenticationPublicKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                return new ServerAuthenticationECSdsaMDCPublicKey(key);
            case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                return new ServerAuthenticationECSdsaCurve25519PublicKey(key);
        }
        return null;
    }

     public static int getCompactKeyLength(byte algorithmImplementation) {
         switch (algorithmImplementation) {
             case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                 return ServerAuthenticationECSdsaMDCPublicKey.COMPACT_KEY_LENGTH;
             case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                 return ServerAuthenticationECSdsaCurve25519PublicKey.COMPACT_KEY_LENGTH;
         }
         return -1;
     }

     public int getCompactKeyLength() {
         return ServerAuthenticationPublicKey.getCompactKeyLength(algorithmImplementation);
     }

    public static ServerAuthenticationPublicKey of(byte[] compactKeyBytes) throws DecodingException {
        switch (compactKeyBytes[0]) {
            case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                return ServerAuthenticationECSdsaMDCPublicKey.of(compactKeyBytes);
            case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                return ServerAuthenticationECSdsaCurve25519PublicKey.of(compactKeyBytes);
        }
        throw new DecodingException();
    }

    public abstract byte[] getCompactKey();

    public SignaturePublicKey getSignaturePublicKey() throws Exception {
        switch (algorithmImplementation) {
            case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
            case ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                return ((ServerAuthenticationECSdsaPublicKey) this).getSignaturePublicKey();
            default:
                throw new Exception("This server authentication public key does not implement signature");
        }
    }
}
