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

package io.olvid.engine.datatypes.key.symmetric;

import java.security.InvalidParameterException;
import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.Encoded;

public class SymEncCTRAES256Key extends SymEncKey {
    public static final int KEY_BYTE_LENGTH = 32;

    public SymEncCTRAES256Key(HashMap<DictionaryKey, Encoded> key) throws InvalidParameterException {
        super(SymEncKey.ALGO_IMPL_CTR_AES256, key);
        if (getKeyLength() != KEY_BYTE_LENGTH) {
            throw new InvalidParameterException();
        }
    }

    public static SymEncCTRAES256Key of(byte[] encKey) {
        HashMap<DictionaryKey, Encoded> key = new HashMap<>();
        key.put(new DictionaryKey(SYMENC_KEY_NAME), Encoded.of(encKey));
        return new SymEncCTRAES256Key(key);
    }
}
