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

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;

public class IdentityAndUidAndNumber {
    public final Identity ownedIdentity;
    public final UID uid;
    public final int attachmentNumber;

    public IdentityAndUidAndNumber(Identity ownedIdentity, UID uid, int attachmentNumber) {
        this.ownedIdentity = ownedIdentity;
        this.uid = uid;
        this.attachmentNumber = attachmentNumber;
    }

    @Override
    public int hashCode() {
        return ownedIdentity.hashCode() ^ uid.hashCode() ^ attachmentNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IdentityAndUidAndNumber)) {
            return false;
        }
        IdentityAndUidAndNumber other = (IdentityAndUidAndNumber) obj;
        return ownedIdentity.equals(other.ownedIdentity) && uid.equals(other.uid) && attachmentNumber == other.attachmentNumber;
    }

    @Override
    public String toString() {
        return  ownedIdentity + " - " + uid + " - " + attachmentNumber;
    }
}
