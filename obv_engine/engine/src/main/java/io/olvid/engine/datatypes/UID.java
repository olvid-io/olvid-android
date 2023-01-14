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

package io.olvid.engine.datatypes;


import java.util.Arrays;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNG;

public class UID implements Comparable<UID> {
    public static final int UID_LENGTH = 32;
    private final byte[] uid;


    public UID(byte[] uid) throws IllegalArgumentException {
        if (uid.length != UID_LENGTH) {
            throw new IllegalArgumentException();
        }
        this.uid = uid;
    }

    public UID(String uidHexString) {
        this(Logger.fromHexString(uidHexString));
    }

    public UID(PRNG prng) {
        this(prng.bytes(UID_LENGTH));
    }

    public static UID fromLong(long l) {
        byte[] bytes = new byte[UID_LENGTH];
        bytes[0] = (byte) (l & 0xff);
        bytes[1] = (byte) ((l>>8) & 0xff);
        bytes[2] = (byte) ((l>>16) & 0xff);
        bytes[3] = (byte) ((l>>24) & 0xff);
        bytes[4] = (byte) ((l>>32) & 0xff);
        bytes[5] = (byte) ((l>>40) & 0xff);
        bytes[6] = (byte) ((l>>48) & 0xff);
        bytes[7] = (byte) ((l>>56) & 0xff);
        return new UID(bytes);
    }

    public byte[] getBytes() {
        return uid;
    }

    @Override
    public String toString() {
        return Logger.toHexString(uid);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UID)) {
            return false;
        }
        return Arrays.equals(uid, ((UID)other).uid);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(uid);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(UID otherUid) {
        for (int i=0; i<UID_LENGTH; i++) {
            if (this.uid[i] != otherUid.uid[i]) {
                return (this.uid[i]&0xff) - (otherUid.uid[i]&0xff);
            }
        }
        return 0;
    }
}
