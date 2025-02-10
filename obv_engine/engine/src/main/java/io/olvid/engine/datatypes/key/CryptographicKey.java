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

package io.olvid.engine.datatypes.key;


import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.Encoded;

public abstract class CryptographicKey {
    public static final byte ALGO_CLASS_SYMMETRIC_ENCRYPTION = (byte) 0x00;
    public static final byte ALGO_CLASS_MAC = (byte) 0x01;
    public static final byte ALGO_CLASS_AUTHENTICATED_SYMMETRIC_ENCRYPTION = (byte) 0x02;

    public static final byte ALGO_CLASS_SIGNATURE = (byte) 0x11;
    public static final byte ALGO_CLASS_PUBLIC_KEY_ENCRYPTION = (byte) 0x12;
    public static final byte ALGO_CLASS_SERVER_AUTHENTICATION = (byte) 0x14;


    protected final byte algorithmClass;
    protected final byte algorithmImplementation;
    protected final HashMap<DictionaryKey, Encoded> key;

    protected CryptographicKey(byte algorithmClass, byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key) {
        this.algorithmClass = algorithmClass;
        this.algorithmImplementation = algorithmImplementation;
        this.key = key;
    }

    public boolean equals(Object o) {
        if (!(o instanceof CryptographicKey)) {
            return false;
        }
        CryptographicKey other = (CryptographicKey) o;
        if ((other.getAlgorithmClass() != algorithmClass) || (other.getAlgorithmImplementation() != algorithmImplementation)) {
            return false;
        }
        return key.equals(other.getKey());
    }

    @Override
    public int hashCode() {
        return key.hashCode() + 31 * algorithmClass + 631 * algorithmImplementation;
    }

    public byte getAlgorithmClass() {
        return algorithmClass;
    }

    public byte getAlgorithmImplementation() {
        return algorithmImplementation;
    }

    public HashMap<DictionaryKey, Encoded> getKey() {
        return key;
    }
}