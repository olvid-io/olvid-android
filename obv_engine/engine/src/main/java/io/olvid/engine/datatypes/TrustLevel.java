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

package io.olvid.engine.datatypes;

public class TrustLevel implements Comparable<TrustLevel> {
    public final int major;
    public final int minor;

    public TrustLevel(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public static TrustLevel of(String majorDotMinor) {
        int major = Integer.parseInt(majorDotMinor.substring(0,1));
        int minor = Integer.parseInt(majorDotMinor.substring(2,3));
        return new TrustLevel(major, minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }

    public static TrustLevel createDirect() {
        return new TrustLevel(4,0);
    }

    public static TrustLevel createServer() {
        return new TrustLevel(3,0);
    }

    public static TrustLevel createIndirect(int indirectTrustLevelMajor) {
        return new TrustLevel(2, indirectTrustLevelMajor);
    }

    public static TrustLevel createServerGroupV2() {
        return new TrustLevel(1, 0);
    }

    @Override
    public int compareTo(TrustLevel other) {
        if (major < other.major) {
            return -1;
        }
        if (major > other.major) {
            return 1;
        }
        return Integer.compare(minor, other.minor);
    }
}
