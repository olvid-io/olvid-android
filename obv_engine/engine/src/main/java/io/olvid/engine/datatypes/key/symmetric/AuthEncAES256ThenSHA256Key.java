/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import java.util.Arrays;
import java.util.HashMap;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.Encoded;

public class AuthEncAES256ThenSHA256Key extends AuthEncKey {
    private final MACHmacSha256Key macKey;
    private final SymEncCTRAES256Key encKey;

    public AuthEncAES256ThenSHA256Key(HashMap<DictionaryKey, Encoded> key) {
        super(AuthEncKey.ALGO_IMPL_AES256_THEN_SHA256, key);
        macKey = new MACHmacSha256Key(key);
        encKey = new SymEncCTRAES256Key(key);
    }

    public static AuthEncAES256ThenSHA256Key of(byte[] macKey, byte[] encKey) {
        HashMap<DictionaryKey, Encoded> key = new HashMap<>();
        key.put(new DictionaryKey(MACKey.MACKEY_KEY_NAME), Encoded.of(macKey));
        key.put(new DictionaryKey(SymEncKey.SYMENC_KEY_NAME), Encoded.of(encKey));
        return new AuthEncAES256ThenSHA256Key(key);
    }

    public static AuthEncAES256ThenSHA256Key generate(PRNG prng) {
        byte[] bytes = prng.bytes(MACHmacSha256Key.KEY_BYTE_LENGTH + SymEncCTRAES256Key.KEY_BYTE_LENGTH);
        return AuthEncAES256ThenSHA256Key.of(Arrays.copyOfRange(bytes, 0, MACHmacSha256Key.KEY_BYTE_LENGTH), Arrays.copyOfRange(bytes, MACHmacSha256Key.KEY_BYTE_LENGTH, MACHmacSha256Key.KEY_BYTE_LENGTH + SymEncCTRAES256Key.KEY_BYTE_LENGTH));
    }

    public MACHmacSha256Key getMacKey() {
        return macKey;
    }

    public SymEncCTRAES256Key getEncKey() {
        return encKey;
    }


    @Override
    public String toString() {
        return Logger.toHexString(macKey.getKeyBytes()) + " - " + Logger.toHexString(encKey.getKeyBytes());
    }
}
