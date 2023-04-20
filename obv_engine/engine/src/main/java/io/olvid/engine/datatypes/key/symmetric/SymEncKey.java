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

package io.olvid.engine.datatypes.key.symmetric;

import java.security.InvalidParameterException;
import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.key.CryptographicKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public abstract class SymEncKey extends SymmetricKey {
    public static final byte ALGO_IMPL_CTR_AES256 = (byte) 0x00;

    public static final String SYMENC_KEY_NAME = "enckey";

    private final byte[] keyBytes;

    public SymEncKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(CryptographicKey.ALGO_CLASS_SYMMETRIC_ENCRYPTION, algorithmImplementation, key);
        try {
            keyBytes = key.get(new DictionaryKey(SYMENC_KEY_NAME)).decodeBytes();
        } catch (DecodingException e) {
            throw new InvalidParameterException();
        }
    }

    public byte[] getKeyBytes() {
        return keyBytes;
    }

    public int getKeyLength() {
        return keyBytes.length;
    }

    public static SymEncKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case ALGO_IMPL_CTR_AES256:
                return new SymEncCTRAES256Key(key);
        }
        return null;
    }
}
