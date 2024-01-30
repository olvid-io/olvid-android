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


import java.util.Arrays;
import java.util.HashMap;

import io.olvid.engine.crypto.EdwardCurve;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.encoder.EncodingException;

public class ServerAuthenticationECSdsaMDCPublicKey extends ServerAuthenticationECSdsaPublicKey {
    public ServerAuthenticationECSdsaMDCPublicKey(HashMap<DictionaryKey, Encoded> key) {
        super(ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC, key, new SignatureECSdsaMDCPublicKey(key));
    }

    public static final int COMPACT_KEY_LENGTH = 1 + Suite.getCurve(EdwardCurve.MDC).byteLength;

    public static ServerAuthenticationECSdsaMDCPublicKey of(byte[] compactKeyBytes) throws DecodingException {
        if ((compactKeyBytes[0] != ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC) || (compactKeyBytes.length != COMPACT_KEY_LENGTH)){
            throw new DecodingException();
        }
        HashMap<DictionaryKey, Encoded> key = new HashMap<>();
        try {
            key.put(new DictionaryKey(SignatureECSdsaPublicKey.PUBLIC_Y_COORD_KEY_NAME), Encoded.of(Encoded.bigUIntFromBytes(Arrays.copyOfRange(compactKeyBytes, 1, compactKeyBytes.length)), COMPACT_KEY_LENGTH - 1));
        } catch (EncodingException e) {
            throw new DecodingException();
        }
        return new ServerAuthenticationECSdsaMDCPublicKey(key);
    }
}
