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

package io.olvid.engine.datatypes.key.symmetric;

import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.key.CryptographicKey;
import io.olvid.engine.encoder.Encoded;

public abstract class AuthEncKey extends SymmetricKey {
    public static final byte ALGO_IMPL_AES256_THEN_SHA256 = (byte) 0x00;

    public AuthEncKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        super(CryptographicKey.ALGO_CLASS_AUTHENTICATED_SYMMETRIC_ENCRYPTION, algorithmImplementation, key);
    }

    public static AuthEncKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case ALGO_IMPL_AES256_THEN_SHA256:
                return new AuthEncAES256ThenSHA256Key(key);
        }
        return null;
    }
}
