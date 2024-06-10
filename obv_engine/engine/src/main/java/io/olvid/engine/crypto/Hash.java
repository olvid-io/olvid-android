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

package io.olvid.engine.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public interface Hash {
    String SHA256 = "sha-256";
    String SHA512 = "sha-512";

    int outputLength();
    byte[] digest(byte[] data);
}


class HashSHA256 implements Hash {
    private MessageDigest h;
    static final  int OUTPUT_LENGTH = 32;

    HashSHA256() {
        try {
            h = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ignored) { }
    }

    @Override
    public int outputLength() {
        return OUTPUT_LENGTH;
    }

    @Override
    public byte[] digest(byte[] data) {
        return h.digest(data);
    }
}

class HashSHA512 implements Hash {
    private MessageDigest h;
    static final  int OUTPUT_LENGTH = 64;

    HashSHA512() {
        try {
            h = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException ignored) { }
    }

    @Override
    public int outputLength() {
        return OUTPUT_LENGTH;
    }

    @Override
    public byte[] digest(byte[] data) {
        return h.digest(data);
    }
}