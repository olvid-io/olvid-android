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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;

public class CiphertextAndKey {
    private final AuthEncKey key;
    private final EncryptedBytes ciphertext;

    public CiphertextAndKey(AuthEncKey key, EncryptedBytes ciphertext) {
        this.key = key;
        this.ciphertext = ciphertext;
    }

    public AuthEncKey getKey() {
        return key;
    }

    public EncryptedBytes getCiphertext() {
        return ciphertext;
    }
}
