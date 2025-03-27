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

import java.util.HashMap;

import io.olvid.engine.crypto.EdwardCurve;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.key.CryptographicKey;
import io.olvid.engine.encoder.Encoded;

public abstract class SignaturePublicKey extends PublicKey {
    public static final byte ALGO_IMPL_EC_SDSA_MDC = (byte) 0x00;
    public static final byte ALGO_IMPL_EC_SDSA_CURVE25519 = (byte) 0x01;

    public SignaturePublicKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        super(CryptographicKey.ALGO_CLASS_SIGNATURE, algorithmImplementation, key);
    }

    public static SignaturePublicKey of(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        switch (algorithmImplementation) {
            case ALGO_IMPL_EC_SDSA_MDC:
                return new SignatureECSdsaMDCPublicKey(key);
            case ALGO_IMPL_EC_SDSA_CURVE25519:
                return new SignatureECSdsaCurve25519PublicKey(key);
        }
        return null;
    }

    protected EdwardCurve getCurve() {
        switch (algorithmImplementation) {
            case ALGO_IMPL_EC_SDSA_MDC: {
                return Suite.getCurve(EdwardCurve.MDC);
            }
            case ALGO_IMPL_EC_SDSA_CURVE25519: {
                return Suite.getCurve(EdwardCurve.CURVE_25519);
            }
        }
        return null;
    }
}
