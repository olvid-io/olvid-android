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

package io.olvid.engine.crypto;


import java.util.Arrays;
import java.util.HashSet;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ProofOfWorkEngine {
    private static final int N = 256; // the number of columns of the matrix
    private static final int R = 128; // the number of lines of the matrix, must be a multiple of 64
    private static final int W = 4; // the weight of the target syndrome - to be on GV, binom(N,W) = 2**R. Must be 4.

    private static class Column {
        final long[] val = new long[R/64];
        final int[] indexes;

        Column (int[] indexes, byte[] bytes) {
            this.indexes = indexes;
            for (int i=0; i<bytes.length; i++) {
                val[i/8] ^= ((long)(bytes[i]&0xff)) << ((i&7)*8);
            }
        }
        Column (int[] indexes, byte[] bytes, int offset) {
            this.indexes = indexes;
            for (int i=0; i<R/8; i++) {
                val[i/8] ^= ((long)(bytes[offset+i]&0xff)) << ((i&7)*8);
            }
        }

        Column (int[] indexes, long[] val) {
            this.indexes = indexes;
            System.arraycopy(val, 0, this.val, 0, R/64);
        }

        public Column xor(Column other) {
            long[] xorVal = new long[val.length];
            for (int i=0; i<val.length; i++) {
                xorVal[i] = val[i] ^ other.val[i];
            }
            int[] xoredIndexes = new int[indexes.length + other.indexes.length];
            System.arraycopy(indexes, 0, xoredIndexes, 0, indexes.length);
            System.arraycopy(other.indexes, 0, xoredIndexes, indexes.length, other.indexes.length);
            return new Column(xoredIndexes, xorVal);
        }

        public boolean equals(Object o) {
            return (o instanceof Column) && Arrays.equals(val, ((Column) o).val);
        }

        public int hashCode() {
            return Arrays.hashCode(val);
        }
    }

    private static Column[] generateMatrix(Seed seed) {
        Column[] H = new Column[N];
        PRNG prng = Suite.getPRNG(PRNG.PRNG_HMAC_SHA256, seed);
        byte[] bytes = prng.bytes(N*R/8);
        for (int i=0; i<N; i++) {
            H[i] = new Column(new int[] {i}, bytes, i*R/8);
        }
        return H;
    }

    public static Encoded solveChallenge(Encoded challenge) throws DecodingException{
        Encoded[] list = challenge.decodeList();
        if (list.length != 2) {
            throw new DecodingException();
        }
        Seed seed = list[0].decodeSeed();
        byte[] Sbytes = list[1].decodeBytes();
        if (Sbytes.length != R/8) {
            throw new DecodingException();
        }
        Column S = new Column(new int[]{}, Sbytes);
        Column[] H = generateMatrix(seed);

        HashSet<Column> setHalf = new HashSet<>();
        HashSet<Column> setHalfS = new HashSet<>();

        for (int i=1; i<N; i++) {
            for (int j=0; j<i; j++) {
                Column xor = H[i].xor(H[j]);
                setHalf.add(xor);
                setHalfS.add(xor.xor(S));
            }
        }

        setHalf.retainAll(setHalfS);

        for (Column col: setHalf) {
            HashSet<Column> single = new HashSet<>();
            single.add(col);
            setHalfS.retainAll(single);
            for (Column col2: setHalfS) {
                int[] indexes = col.xor(col2).indexes;
                Arrays.sort(indexes);
                Encoded[] encodedIndexes = new Encoded[indexes.length];
                for (int i=0; i<indexes.length; i++) {
                    encodedIndexes[i] = Encoded.of(indexes[i]);
                }
                return Encoded.of(encodedIndexes);
            }
            break;
        }
        Logger.w("No solution was found for this challenge...");
        return null;
    }

}
