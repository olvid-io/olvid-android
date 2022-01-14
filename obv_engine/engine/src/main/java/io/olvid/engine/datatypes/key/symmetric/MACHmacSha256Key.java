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

package io.olvid.engine.datatypes.key.symmetric;

import java.security.InvalidParameterException;
import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.Encoded;

public class MACHmacSha256Key extends MACKey {
    public static final int KEY_BYTE_LENGTH = 32;

    public MACHmacSha256Key(HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(MACKey.ALGO_IMPL_HMAC_SHA256, key);
        if (getKeyLength() < KEY_BYTE_LENGTH) {
            throw new InvalidParameterException();
        }
    }

    public static MACHmacSha256Key of(byte[] macKey) {
        HashMap<DictionaryKey, Encoded> key = new HashMap<>();
        key.put(new DictionaryKey(MACKEY_KEY_NAME), Encoded.of(macKey));
        return new MACHmacSha256Key(key);
    }
}
