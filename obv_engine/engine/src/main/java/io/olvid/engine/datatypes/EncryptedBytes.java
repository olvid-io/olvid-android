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

/**
 * A simple class to encapsulate a byte[] containing encrypted data.
 */

public class EncryptedBytes {
    final byte[] bytes;
    public final int length;

    public EncryptedBytes(byte[] bytes) {
        this.bytes = bytes;
        length = bytes.length;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EncryptedBytes)) {
            return false;
        }
        return Arrays.equals(bytes, ((EncryptedBytes)other).getBytes());
    }
}
