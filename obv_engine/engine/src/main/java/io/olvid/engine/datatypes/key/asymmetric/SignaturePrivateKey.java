/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

public abstract class SignaturePrivateKey extends PrivateKey {
    public SignaturePrivateKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        super(CryptographicKey.ALGO_CLASS_SIGNATURE, algorithmImplementation, key);
    }

    public static SignaturePrivateKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case SignaturePublicKey.ALGO_IMPL_EC_SDSA_MDC:
                return new SignatureECSdsaMDCPrivateKey(key);
            case SignaturePublicKey.ALGO_IMPL_EC_SDSA_CURVE25519:
                return new SignatureECSdsaCurve25519PrivateKey(key);
        }
        return null;
    }
}
