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

import io.olvid.engine.datatypes.Identity;

public class IdentityAndLong {
    public final Identity identity;
    public final long lng;

    public IdentityAndLong(Identity identity, long lng) {
        this.identity = identity;
        this.lng = lng;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IdentityAndLong)) {
            return false;
        }
        IdentityAndLong other = (IdentityAndLong) o;
        return identity.equals(other.identity) && lng == other.lng;
    }

    @Override
    public int hashCode() {
        return identity.hashCode() * 31 + Long.hashCode(lng);
    }

    @Override
    public String toString() {
        return  identity + " - " + lng;
    }
}

