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

import io.olvid.engine.crypto.EdwardCurve;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.key.CryptographicKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public abstract class EncryptionPublicKey extends PublicKey {
    public static final byte ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256 = (byte) 0x00;
    public static final byte ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256 = (byte) 0x01;

    public EncryptionPublicKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(CryptographicKey.ALGO_CLASS_PUBLIC_KEY_ENCRYPTION, algorithmImplementation, key);
    }

    public static EncryptionPublicKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return new EncryptionEciesMDCPublicKey(key);
            case ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return new EncryptionEciesCurve25519PublicKey(key);
        }
        return null;
    }

    public static int getCompactKeyLength(byte algorithmImplementation) {
        switch (algorithmImplementation) {
            case ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return EncryptionEciesMDCPublicKey.COMPACT_KEY_LENGTH;
            case ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return EncryptionEciesCurve25519PublicKey.COMPACT_KEY_LENGTH;
        }
        return -1;
    }

    public int getCompactKeyLength() {
        return EncryptionPublicKey.getCompactKeyLength(algorithmImplementation);
    }


    public static EncryptionPublicKey of(byte[] compactKeyBytes) throws DecodingException {
        switch (compactKeyBytes[0]) {
            case ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return EncryptionEciesMDCPublicKey.of(compactKeyBytes);
            case ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return EncryptionEciesCurve25519PublicKey.of(compactKeyBytes);
        }
        throw new DecodingException();
    }

    protected EdwardCurve getCurve() {
        switch (algorithmImplementation) {
            case ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256: {
                return Suite.getCurve(EdwardCurve.MDC);
            }
            case ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256: {
                return Suite.getCurve(EdwardCurve.CURVE_25519);
            }
        }
        return null;
    }

    public abstract byte[] getCompactKey();
}
