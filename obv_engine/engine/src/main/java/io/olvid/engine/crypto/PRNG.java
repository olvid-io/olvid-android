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
import java.security.InvalidParameterException;
import java.util.Arrays;

import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.symmetric.MACHmacSha256Key;

public interface PRNG {
    String PRNG_HMAC_SHA256 = "prng_hmac_sha-256";

    byte[] bytes(int l);
    BigInteger bigInt(BigInteger n);
}

class PRNGHmacSHA256 implements PRNG {
    byte[] state_k = new byte[MACHmacSha256Key.KEY_BYTE_LENGTH];
    byte[] state_v = new byte[MACHmacSha256.OUTPUT_LENGTH];

    public PRNGHmacSHA256(Seed seed) throws InvalidParameterException {
        Arrays.fill(state_k, (byte) 0);
        Arrays.fill(state_v, (byte) 1);
        update(seed.getBytes());
    }

    private void update(byte[] data) {
        try {
            byte[] in = new byte[state_v.length + 1 + data.length];
            System.arraycopy(state_v, 0, in, 0, state_v.length);
            in[state_v.length] = 0;
            System.arraycopy(data, 0, in, state_v.length + 1, data.length);
            state_k = new MACHmacSha256().digest(MACHmacSha256Key.of(state_k), in);
            state_v = new MACHmacSha256().digest(MACHmacSha256Key.of(state_k), state_v);
            if (data.length > 0) {
                System.arraycopy(state_v, 0, in, 0, state_v.length);
                in[state_v.length] = 1;
                System.arraycopy(data, 0, in, state_v.length + 1, data.length);
                state_k = new MACHmacSha256().digest(MACHmacSha256Key.of(state_k), in);
                state_v = new MACHmacSha256().digest(MACHmacSha256Key.of(state_k), state_v);
            }
        } catch (Exception ignored) {}
    }

    void reseed(Seed seed) {
        update(seed.getBytes());
    }

    @Override
    public byte[] bytes(int l) {
        byte[] output = new byte[l];
        for (int i=0; i<1+(l-1)/MACHmacSha256.OUTPUT_LENGTH; i++) {
            try {
                state_v = new MACHmacSha256().digest(MACHmacSha256Key.of(state_k), state_v);
                System.arraycopy(state_v, 0, output, i*MACHmacSha256.OUTPUT_LENGTH, Math.min(MACHmacSha256.OUTPUT_LENGTH, l-i*MACHmacSha256.OUTPUT_LENGTH));
            } catch (Exception ignored) {}
        }
        update(new byte[0]);
        return output;
    }

    @Override
    public BigInteger bigInt(BigInteger n) {
        BigInteger n_minus_one = n.subtract(BigInteger.ONE);
        int l = n_minus_one.bitLength();
        int ell = 1+(l-1)/8;
        int mask = (1<<(l-8*(ell-1))) - 1;
        while (true) {
            byte[] rand = bytes(ell);
            rand[0] = (byte) (rand[0]&mask);
            BigInteger r = new BigInteger(1, rand);
            if (r.compareTo(n) < 0) {
                return r;
            }
        }
    }
}