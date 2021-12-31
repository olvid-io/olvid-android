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

package io.olvid.engine.datatypes;


import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;

public class Seed {
    public static final int MIN_SEED_LENGTH = 32;
    private final byte[] seed;
    public final int length;

    public Seed(byte[] seed) throws IllegalArgumentException {
        if (seed.length < MIN_SEED_LENGTH) {
            throw new IllegalArgumentException();
        }
        this.seed = seed;
        this.length = seed.length;
    }

    public Seed(Seed... seedsToConcatenate) {
        int len = 0;
        for (Seed seedToConcatenate: seedsToConcatenate) {
            len += seedToConcatenate.length;
        }
        this.seed = new byte[len];
        this.length = len;
        len = 0;
        for (Seed seedToConcatenate: seedsToConcatenate) {
            System.arraycopy(seedToConcatenate.seed, 0, this.seed, len, seedToConcatenate.length);
            len += seedToConcatenate.length;
        }
    }

    public Seed(PRNG prng) {
        this(prng.bytes(MIN_SEED_LENGTH));
    }

    public byte[] getBytes() {
        return seed;
    }

    public static Seed of(AuthEncKey... authEncKeys) throws Exception {
        if (authEncKeys.length == 0) {
            throw new Exception();
        }
        Seed zeroSeed = new Seed(new byte[MIN_SEED_LENGTH]);
        PRNG prng = Suite.getPRNG(PRNG.PRNG_HMAC_SHA256, zeroSeed);

        int ciphertextsLength = 0;
        EncryptedBytes[] ciphertexts = new EncryptedBytes[authEncKeys.length];
        for (int i=0; i<authEncKeys.length; i++) {
            AuthEnc authEnc = Suite.getAuthEnc(authEncKeys[i]);
            ciphertexts[i] = authEnc.encrypt(authEncKeys[i], new byte[MIN_SEED_LENGTH], prng);
            ciphertextsLength += ciphertexts[i].length;
        }
        byte[] hashInput = new byte[ciphertextsLength];
        ciphertextsLength = 0;
        for (int i=0; i<authEncKeys.length; i++) {
            System.arraycopy(ciphertexts[i].bytes, 0, hashInput, ciphertextsLength, ciphertexts[i].length);
            ciphertextsLength += ciphertexts[i].length;
        }
        Hash hash = Suite.getHash(Hash.SHA256);
        return new Seed(hash.digest(hashInput));
    }


    @Override
    public String toString() {
        return Logger.toHexString(seed);
    }
}
