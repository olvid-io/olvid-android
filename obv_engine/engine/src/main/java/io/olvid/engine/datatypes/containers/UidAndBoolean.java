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

import io.olvid.engine.datatypes.UID;

public class UidAndBoolean {
    public final UID uid;
    public final boolean bool;

    public UidAndBoolean(UID uid, boolean bool) {
        this.uid = uid;
        this.bool = bool;
    }

    @Override
    public int hashCode() {
        return uid.hashCode() * 31 + Boolean.hashCode(bool);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UidAndBoolean)) {
            return false;
        }
        UidAndBoolean other = (UidAndBoolean) obj;
        return uid.equals(other.uid) && bool == other.bool;
    }

    @Override
    public String toString() {
        return  uid + " - " + bool;
    }
}
