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


import java.security.InvalidParameterException;
import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.key.CryptographicKey;
import io.olvid.engine.encoder.Encoded;

public abstract class EncryptionPrivateKey extends PrivateKey {
    public EncryptionPrivateKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(CryptographicKey.ALGO_CLASS_PUBLIC_KEY_ENCRYPTION, algorithmImplementation, key);
    }

    public static EncryptionPrivateKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return new EncryptionEciesMDCPrivateKey(key);
            case EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return new EncryptionEciesCurve25519PrivateKey(key);
        }
        return null;
    }
}
