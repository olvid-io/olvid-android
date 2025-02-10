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

public abstract class EncryptionEciesPrivateKey extends EncryptionPrivateKey {
    public static final String SECRET_EXPONENT_KEY_NAME = "n";

    private final BigInteger a;

    public EncryptionEciesPrivateKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(algorithmImplementation, key);
        try {
            a = key.get(new DictionaryKey(SECRET_EXPONENT_KEY_NAME)).decodeBigUInt();
        } catch (DecodingException e) {
            throw new InvalidParameterException();
        }
    }

    public BigInteger getA() {
        return a;
    }
}
