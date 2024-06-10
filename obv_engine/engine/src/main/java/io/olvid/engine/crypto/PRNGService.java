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

import java.math.BigInteger;
import java.security.SecureRandom;

import io.olvid.engine.datatypes.Seed;


public interface PRNGService extends PRNG {
    void reseed(Seed seed);
}

class PRNGServiceHmacSHA256 implements PRNGService {
    public static final int RESEED_FREQUENCY = 100;
    private final SecureRandom rand;
    PRNGHmacSHA256 prng;
    int reseedCounter;
    private static final PRNGServiceHmacSHA256 instance = new PRNGServiceHmacSHA256();

    private PRNGServiceHmacSHA256() {
        rand = new SecureRandom();
        byte[] seedBytes = new byte[Seed.MIN_SEED_LENGTH];
        rand.nextBytes(seedBytes);
        prng = new PRNGHmacSHA256(new Seed(seedBytes));
        reseedCounter = 1;
    }

    public static PRNGServiceHmacSHA256 getInstance() {
        return instance;
    }


    @Override
    public synchronized void reseed(Seed seed) {
        prng = new PRNGHmacSHA256(seed);
    }

    @Override
    public synchronized byte[] bytes(int n) {
        byte[] output = prng.bytes(n);
        if (reseedCounter == RESEED_FREQUENCY) {
            byte[] seedBytes = new byte[Seed.MIN_SEED_LENGTH];
            rand.nextBytes(seedBytes);
            prng.reseed(new Seed(seedBytes));
            reseedCounter = 1;
        } else {
            reseedCounter++;
        }
        return output;
    }

    @Override
    public synchronized BigInteger bigInt(BigInteger n) {
        return prng.bigInt(n);
    }
}
