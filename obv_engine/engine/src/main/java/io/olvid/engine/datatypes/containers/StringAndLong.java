/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.engine.datatypes.containers;


public class StringAndLong {
    public final String string;
    public final long lng;

    public StringAndLong(String string, long lng) {
        this.string = string;
        this.lng = lng;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StringAndLong)) {
            return false;
        }
        StringAndLong other = (StringAndLong) o;
        return string.equals(other.string) && lng == other.lng;
    }

    @Override
    public int hashCode() {
        return string.hashCode() * 31 + Long.hashCode(lng);
    }
}
