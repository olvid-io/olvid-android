/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.encoder.EncodingException;

public abstract class ServerAuthenticationECSdsaPublicKey extends ServerAuthenticationPublicKey {
    public static final String PUBLIC_X_COORD_KEY_NAME = "x";
    public static final String PUBLIC_Y_COORD_KEY_NAME = "y";

    private final SignatureECSdsaPublicKey signaturePublicKey;

    public ServerAuthenticationECSdsaPublicKey(byte algorithmImplementation, HashMap<DictionaryKey, Encoded> key, SignatureECSdsaPublicKey signaturePublicKey) {
        super(algorithmImplementation, key);
        this.signaturePublicKey = signaturePublicKey;
    }

    public SignatureECSdsaPublicKey getSignaturePublicKey() {
        return signaturePublicKey;
    }

    public byte[] getCompactKey() {
        byte[] compactKey = new byte[getCompactKeyLength()];
        compactKey[0] = algorithmImplementation;
        try {
            byte[] yBytes = Encoded.bytesFromBigUInt(key.get(new DictionaryKey(PUBLIC_Y_COORD_KEY_NAME)).decodeBigUInt(), compactKey.length - 1);
            System.arraycopy(yBytes, 0, compactKey, 1, yBytes.length);
        } catch (EncodingException | DecodingException ignored) {}
        return  compactKey;
    }

}
