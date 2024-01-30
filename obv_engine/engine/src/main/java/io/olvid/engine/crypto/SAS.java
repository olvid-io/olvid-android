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
import java.nio.charset.StandardCharsets;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;

public abstract class SAS {
    public static byte[] compute(Seed seedAlice, Seed seedBob, int numberOfDigits) {
        Seed seed = new Seed(seedAlice, seedBob);
        PRNG prng = Suite.getPRNG(PRNG.PRNG_HMAC_SHA256, seed);
        BigInteger max = BigInteger.valueOf(10).pow(numberOfDigits);
        BigInteger sas = prng.bigInt(max).add(max); // We add max to the sas to be able to get the 0 padding by simply removing the first character of the String
        return sas.toString(10).substring(1).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] computeDouble(Seed seedAlice, Seed seedBob, Identity identityBob, int numberOfDigits) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] bytesIdentity = identityBob.getBytes();
        byte[] toHash = new byte[bytesIdentity.length + seedAlice.length];
        System.arraycopy(bytesIdentity, 0, toHash, 0, bytesIdentity.length);
        System.arraycopy(seedAlice.getBytes(), 0, toHash, bytesIdentity.length, seedAlice.length);
        byte[] hash = sha256.digest(toHash);

        byte[] xor = new byte[Math.min(hash.length, seedBob.length)];
        for (int i=0; i<xor.length; i++) {
            xor[i] = (byte) (seedBob.getBytes()[i] ^ hash[i]);
        }

        Seed seed = new Seed(xor);
        PRNG prng = Suite.getPRNG(PRNG.PRNG_HMAC_SHA256, seed);
        BigInteger max = BigInteger.valueOf(10).pow(2*numberOfDigits);
        BigInteger sas = prng.bigInt(max).add(max); // We add max to the sas to be able to get the 0 padding by simply removing the first character of the String
        return sas.toString(10).substring(1).getBytes(StandardCharsets.UTF_8);
    }

    public static String computeSimple(Seed seedAlice, Seed seedBob, byte[] rawPublicKeyBob, int numberOfDigits) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] toHash = new byte[rawPublicKeyBob.length + seedAlice.length];
        System.arraycopy(rawPublicKeyBob, 0, toHash, 0, rawPublicKeyBob.length);
        System.arraycopy(seedAlice.getBytes(), 0, toHash, rawPublicKeyBob.length, seedAlice.length);
        byte[] hash = sha256.digest(toHash);

        byte[] xor = new byte[Math.min(hash.length, seedBob.length)];
        for (int i=0; i<xor.length; i++) {
            xor[i] = (byte) (seedBob.getBytes()[i] ^ hash[i]);
        }

        Seed seed = new Seed(xor);
        PRNG prng = Suite.getPRNG(PRNG.PRNG_HMAC_SHA256, seed);
        BigInteger max = BigInteger.valueOf(10).pow(numberOfDigits);
        BigInteger sas = prng.bigInt(max).add(max); // We add max to the sas to be able to get the 0 padding by simply removing the first character of the String
        return sas.toString(10).substring(1);
    }
}
