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


import java.math.BigInteger;
import java.util.HashMap;

import io.olvid.engine.crypto.EdwardCurve;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EdwardCurvePoint;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.encoder.EncodingException;

public class ServerAuthenticationECSdsaCurve25519KeyPair extends KeyPair {
    @Override
    public ServerAuthenticationECSdsaCurve25519PublicKey getPublicKey() {
        return (ServerAuthenticationECSdsaCurve25519PublicKey) publicKey;
    }

    @Override
    public ServerAuthenticationECSdsaCurve25519PrivateKey getPrivateKey() {
        return (ServerAuthenticationECSdsaCurve25519PrivateKey) privateKey;
    }

    public ServerAuthenticationECSdsaCurve25519KeyPair(ServerAuthenticationECSdsaCurve25519PublicKey publicKey, ServerAuthenticationECSdsaCurve25519PrivateKey privateKey) {
        super(publicKey, privateKey);
    }

    public static ServerAuthenticationECSdsaCurve25519KeyPair generate(PRNGService prng) {
        EdwardCurve curve25519 = Suite.getCurve(EdwardCurve.CURVE_25519);
        BigInteger a;
        do {
            a = prng.bigInt(curve25519.q);
        } while (a.equals(BigInteger.ONE) || a.equals(BigInteger.ZERO));
        EdwardCurvePoint A = curve25519.scalarMultiplicationWithX(a, curve25519.G);
        HashMap<DictionaryKey, Encoded> publicKeyDictionary = new HashMap<>();
        HashMap<DictionaryKey, Encoded> privateKeyDictionary = new HashMap<>();
        try {
            publicKeyDictionary.put(new DictionaryKey(ServerAuthenticationECSdsaPublicKey.PUBLIC_X_COORD_KEY_NAME), Encoded.of(A.getX(), curve25519.byteLength));
            publicKeyDictionary.put(new DictionaryKey(ServerAuthenticationECSdsaPublicKey.PUBLIC_Y_COORD_KEY_NAME), Encoded.of(A.getY(), curve25519.byteLength));
            privateKeyDictionary.put(new DictionaryKey(ServerAuthenticationECSdsaPrivateKey.SECRET_EXPONENT_KEY_NAME), Encoded.of(a, curve25519.byteLength));
        } catch (EncodingException e) {
            return null;
        }
        return new ServerAuthenticationECSdsaCurve25519KeyPair(new ServerAuthenticationECSdsaCurve25519PublicKey(publicKeyDictionary), new ServerAuthenticationECSdsaCurve25519PrivateKey(privateKeyDictionary));
    }
}
