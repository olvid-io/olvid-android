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

package io.olvid.engine.datatypes.key.asymmetric;


import java.math.BigInteger;
import java.security.InvalidParameterException;
import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public abstract class SignatureECSdsaPublicKey extends SignaturePublicKey {
    public static final String PUBLIC_X_COORD_KEY_NAME = ServerAuthenticationECSdsaPublicKey.PUBLIC_X_COORD_KEY_NAME;
    public static final String PUBLIC_Y_COORD_KEY_NAME = ServerAuthenticationECSdsaPublicKey.PUBLIC_Y_COORD_KEY_NAME;

    private final BigInteger Ax;
    private final BigInteger Ay;

    public SignatureECSdsaPublicKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(algorithmImplementation, key);
        try {
            Ay = key.get(new DictionaryKey(PUBLIC_Y_COORD_KEY_NAME)).decodeBigUInt();
            DictionaryKey xKey = new DictionaryKey(PUBLIC_X_COORD_KEY_NAME);
            if (key.containsKey(xKey)) {
                Ax = key.get(xKey).decodeBigUInt();
            } else {
                Ax = null;
            }
        } catch (DecodingException e) {
            throw new InvalidParameterException();
        }
    }

    public BigInteger getAx() {
        return Ax;
    }

    public BigInteger getAy() {
        return Ay;
    }


}
