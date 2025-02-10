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

package io.olvid.engine.datatypes.containers;


import java.nio.charset.StandardCharsets;

import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.UID;

public class StringAndBoolean {
    public final String string;
    public final boolean bool;

    public StringAndBoolean(String string, boolean bool) {
        this.string = string;
        this.bool = bool;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StringAndBoolean)) {
            return false;
        }
        StringAndBoolean other = (StringAndBoolean) o;
        return string.equals(other.string) && bool == other.bool;
    }

    @Override
    public int hashCode() {
        return string.hashCode() * 31 + Boolean.hashCode(bool);
    }

    public static UID computeUniqueUid(String string, boolean bool) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] input = new byte[string.getBytes(StandardCharsets.UTF_8).length + 1];
        System.arraycopy(string.getBytes(StandardCharsets.UTF_8), 0, input, 0, input.length - 1);
        input[input.length - 1] = bool ? (byte) 0x01 : (byte) 0x00;
        return new UID(sha256.digest(input));
    }
}
