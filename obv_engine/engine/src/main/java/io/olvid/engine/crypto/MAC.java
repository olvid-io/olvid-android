/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.symmetric.MACHmacSha256Key;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.datatypes.key.symmetric.SymmetricKey;

public interface MAC {
    String HMAC_SHA256 = "hmac_sha-256";

    int outputLength();
    byte[] digest(MACKey key, byte[] bytes) throws InvalidKeyException;
    byte[] digest(MACKey key, byte[] bytes, int inputLen) throws InvalidKeyException;
    boolean verify(MACKey key, byte[] bytes, byte[] mac) throws InvalidKeyException;

    MACKey generateKey(PRNG prng);
}

class MACHmacSha256 implements MAC {
    static final int OUTPUT_LENGTH = 32;

    @Override
    public int outputLength() {
        return OUTPUT_LENGTH;
    }

    @Override
    public byte[] digest(MACKey key, byte[] bytes) throws InvalidKeyException {
        try {
            Mac h = Mac.getInstance("HmacSHA256");
            h.init(new SecretKeySpec(key.getKeyBytes(), "HmacSHA256"));
            return h.doFinal(bytes);
        } catch (NoSuchAlgorithmException ignored) {}
        return null;
    }

    @Override
    public byte[] digest(MACKey key, byte[] bytes, int inputLen) throws InvalidKeyException {
        try {
            Mac h = Mac.getInstance("HmacSHA256");
            h.init(new SecretKeySpec(key.getKeyBytes(), "HmacSHA256"));
            h.update(bytes, 0, inputLen);
            return h.doFinal();
        } catch (NoSuchAlgorithmException ignored) {}
        return null;
    }


    @Override
    public boolean verify(MACKey key, byte[] bytes, byte[] mac) throws InvalidKeyException {
        byte[] newMac = digest(key, bytes);
        return Arrays.equals(mac, newMac);
    }

    @Override
    public MACKey generateKey(PRNG prng) {
        KDF kdf = Suite.getKDF(KDF.KDF_SHA256);
        Seed kdfSeed = new Seed(prng);
        try {
            return (MACKey) kdf.gen(kdfSeed, new KDFDelegateForHmacSHA256())[0];
        } catch (Exception e) {
            return null;
        }
    }
}

class KDFDelegateForHmacSHA256 implements KDF.Delegate {
    @Override
    public int getKeyLength() {
        return MACHmacSha256Key.KEY_BYTE_LENGTH;
    }

    @Override
    public SymmetricKey[] processBytes(byte[] bytes) {
        return new SymmetricKey[] {
                MACHmacSha256Key.of(bytes)
        };
    }
}
