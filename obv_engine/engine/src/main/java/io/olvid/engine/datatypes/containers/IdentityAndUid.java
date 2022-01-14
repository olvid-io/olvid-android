/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;

public class IdentityAndUid {
    public final Identity ownedIdentity;
    public final UID uid;

    public IdentityAndUid(Identity ownedIdentity, UID uid) {
        this.ownedIdentity = ownedIdentity;
        this.uid = uid;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IdentityAndUid)) {
            return false;
        }
        IdentityAndUid other = (IdentityAndUid) o;
        return ownedIdentity.equals(other.ownedIdentity) && uid.equals(other.uid);
    }

    @Override
    public int hashCode() {
        return ownedIdentity.hashCode() ^ uid.hashCode();
    }

    @Override
    public String toString() {
        return  ownedIdentity + " - " + uid;
    }

    public static UID computeUniqueUid(Identity ownedIdentity, UID uid) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] input = new byte[ownedIdentity.getBytes().length + UID.UID_LENGTH];
        System.arraycopy(ownedIdentity.getBytes(), 0, input, 0, ownedIdentity.getBytes().length);
        System.arraycopy(uid.getBytes(), 0, input, ownedIdentity.getBytes().length, UID.UID_LENGTH);
        return new UID(sha256.digest(input));
    }
}

