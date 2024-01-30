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
import java.util.Arrays;
import java.util.HashMap;

import io.olvid.engine.crypto.EdwardCurve;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.encoder.EncodingException;

public class EncryptionEciesMDCPublicKey extends EncryptionEciesPublicKey {
    public EncryptionEciesMDCPublicKey(HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256, key);
    }

    public static final int COMPACT_KEY_LENGTH = 1 + Suite.getCurve(EdwardCurve.MDC).byteLength;

    public static EncryptionEciesMDCPublicKey of(byte[] compactKeyBytes) throws DecodingException {
        if ((compactKeyBytes[0] != EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256) || (compactKeyBytes.length != COMPACT_KEY_LENGTH)){
            throw new DecodingException();
        }
        HashMap<DictionaryKey, Encoded> key = new HashMap<>();
        try {
            key.put(new DictionaryKey(PUBLIC_Y_COORD_KEY_NAME), Encoded.of(Encoded.bigUIntFromBytes(Arrays.copyOfRange(compactKeyBytes, 1, compactKeyBytes.length)), COMPACT_KEY_LENGTH - 1));
        } catch (EncodingException e) {
            throw new DecodingException();
        }
        return new EncryptionEciesMDCPublicKey(key);
    }
}
