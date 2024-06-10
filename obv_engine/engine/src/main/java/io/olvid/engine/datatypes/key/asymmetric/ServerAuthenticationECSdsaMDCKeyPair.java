/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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


import java.math.BigInteger;
import java.util.HashMap;

import io.olvid.engine.crypto.EdwardCurve;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EdwardCurvePoint;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.encoder.EncodingException;

public class ServerAuthenticationECSdsaMDCKeyPair extends KeyPair {
    @Override
    public ServerAuthenticationECSdsaMDCPublicKey getPublicKey() {
        return (ServerAuthenticationECSdsaMDCPublicKey) publicKey;
    }

    @Override
    public ServerAuthenticationECSdsaMDCPrivateKey getPrivateKey() {
        return (ServerAuthenticationECSdsaMDCPrivateKey) privateKey;
    }

    public ServerAuthenticationECSdsaMDCKeyPair(ServerAuthenticationECSdsaMDCPublicKey publicKey, ServerAuthenticationECSdsaMDCPrivateKey privateKey) {
        super(publicKey, privateKey);
    }

    public static ServerAuthenticationECSdsaMDCKeyPair generate(PRNGService prng) {
        EdwardCurve mdc = Suite.getCurve(EdwardCurve.MDC);
        BigInteger a;
        EdwardCurvePoint A;
        // check we do not generate a low order public key
        do {
            do {
                a = prng.bigInt(mdc.q);
            } while (a.equals(BigInteger.ZERO) || a.equals(BigInteger.ONE));
            A = mdc.scalarMultiplicationWithX(a, mdc.G);
        } while (A.isLowOrderPoint());
        HashMap<DictionaryKey, Encoded> publicKeyDictionary = new HashMap<>();
        HashMap<DictionaryKey, Encoded> privateKeyDictionary = new HashMap<>();
        try {
            publicKeyDictionary.put(new DictionaryKey(ServerAuthenticationECSdsaPublicKey.PUBLIC_X_COORD_KEY_NAME), Encoded.of(A.getX(), mdc.byteLength));
            publicKeyDictionary.put(new DictionaryKey(ServerAuthenticationECSdsaPublicKey.PUBLIC_Y_COORD_KEY_NAME), Encoded.of(A.getY(), mdc.byteLength));
            privateKeyDictionary.put(new DictionaryKey(ServerAuthenticationECSdsaPrivateKey.SECRET_EXPONENT_KEY_NAME), Encoded.of(a, mdc.byteLength));
        } catch (EncodingException e) {
            return null;
        }
        return new ServerAuthenticationECSdsaMDCKeyPair(new ServerAuthenticationECSdsaMDCPublicKey(publicKeyDictionary), new ServerAuthenticationECSdsaMDCPrivateKey(privateKeyDictionary));
    }

    public static boolean areKeysMatching(ServerAuthenticationECSdsaMDCPublicKey publicKey, ServerAuthenticationECSdsaMDCPrivateKey privateKey) {
        EdwardCurve mdc = Suite.getCurve(EdwardCurve.MDC);
        EdwardCurvePoint A = mdc.scalarMultiplicationWithX(privateKey.getSignaturePrivateKey().getA(), mdc.G);
        return A.getY().equals(publicKey.getSignaturePublicKey().getAy());
    }
}
