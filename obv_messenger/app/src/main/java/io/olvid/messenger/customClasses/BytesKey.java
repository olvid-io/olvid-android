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

package io.olvid.messenger.customClasses;


import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

public class BytesKey implements Comparable<BytesKey>, Parcelable {
    public final byte[] bytes;

    public BytesKey(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BytesKey)) return false;
        return Arrays.equals(bytes, ((BytesKey)other).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }


    @Override
    public int compareTo(BytesKey other) {
        if (bytes.length != other.bytes.length) {
            return bytes.length - other.bytes.length;
        }
        for (int i=0; i<bytes.length; i++) {
            if (bytes[i] != other.bytes[i]) {
                return (bytes[i] & 0xff) - (other.bytes[i] & 0xff);
            }
        }
        return 0;
    }

    // region Parcelable

    public static final Parcelable.Creator<BytesKey> CREATOR
            = new Parcelable.Creator<BytesKey>() {
        public BytesKey createFromParcel(Parcel in) {
            return new BytesKey(in.createByteArray());
        }

        public BytesKey[] newArray(int size) {
            return new BytesKey[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(bytes);
    }

    // endregion
}
