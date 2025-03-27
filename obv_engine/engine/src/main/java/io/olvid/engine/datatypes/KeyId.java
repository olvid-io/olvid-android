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

package io.olvid.engine.datatypes;


import java.util.Arrays;

import io.olvid.engine.Logger;

public class KeyId {
    public static final int KEYID_LENGTH = 32;
    private final byte[] keyId;


    public KeyId(byte[] keyId) throws IllegalArgumentException {
        if (keyId.length != KEYID_LENGTH) {
            throw new IllegalArgumentException();
        }
        this.keyId = keyId;
    }

    public byte[] getBytes() {
        return keyId;
    }

    @Override
    public String toString() {
        return Logger.toHexString(keyId);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof KeyId)) {
            return false;
        }
        return Arrays.equals(keyId, ((KeyId)other).keyId);
    }

}
