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


import java.util.Arrays;

public interface Commitment {
    CommitmentOutput commit(byte[] tag, byte[] value, PRNGService prng);
    byte[] open(byte[] tag, byte[] commitment, byte[] decommitment);

    class CommitmentOutput {
        public final byte[] commitment;
        public final byte[] decommitment;

        public CommitmentOutput(byte[] commitment, byte[] decommitment) {
            this.commitment = commitment;
            this.decommitment = decommitment;
        }
    }
}

class CommitmentWithSHA256 implements Commitment {
    static final int COMMITMENT_RANDOM_LENGTH = 32;


    @Override
    public CommitmentOutput commit(byte[] tag, byte[] value, PRNGService prng) {
        HashSHA256 h = new HashSHA256();
        byte[] e = prng.bytes(COMMITMENT_RANDOM_LENGTH);
        byte[] decommitment = new byte[value.length + COMMITMENT_RANDOM_LENGTH];
        System.arraycopy(value, 0, decommitment, 0, value.length);
        System.arraycopy(e, 0, decommitment, value.length, COMMITMENT_RANDOM_LENGTH);

        byte[] input = new byte[tag.length + value.length + COMMITMENT_RANDOM_LENGTH];
        System.arraycopy(tag, 0, input, 0, tag.length);
        System.arraycopy(decommitment, 0, input, tag.length, decommitment.length);

        byte[] commitment = h.digest(input);

        return new CommitmentOutput(commitment, decommitment);
    }

    @Override
    public byte[] open(byte[] tag, byte[] commitment, byte[] decommitment) {
        HashSHA256 h = new HashSHA256();
        byte[] input = new byte[tag.length + decommitment.length];
        System.arraycopy(tag, 0, input, 0, tag.length);
        System.arraycopy(decommitment, 0, input, tag.length, decommitment.length);

        byte[] commitment2 = h.digest(input);
        if (Arrays.equals(commitment, commitment2)) {
            return Arrays.copyOfRange(decommitment, 0, decommitment.length - CommitmentWithSHA256.COMMITMENT_RANDOM_LENGTH);
        } else {
            return null;
        }
    }
}