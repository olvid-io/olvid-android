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
import io.olvid.engine.datatypes.UID;

public class UserData {
    public final Identity ownedIdentity;
    public final UID label;
    public final long nextRefreshTimestamp;
    public final byte[] bytesGroupOwnerAndUidOrIdentifier;
    public final Type type;

    public enum Type {
        OWNED_IDENTITY,
        GROUP,
        GROUP_V2,
    }

    public UserData(Identity ownedIdentity, UID label, long nextRefreshTimestamp, Type type, byte[] bytesGroupOwnerAndUidOrIdentifier) {
        this.ownedIdentity = ownedIdentity;
        this.label = label;
        this.nextRefreshTimestamp = nextRefreshTimestamp;
        this.type = type;
        this.bytesGroupOwnerAndUidOrIdentifier = bytesGroupOwnerAndUidOrIdentifier;
    }
}
