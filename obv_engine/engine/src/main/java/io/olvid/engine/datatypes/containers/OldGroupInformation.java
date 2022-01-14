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
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class OldGroupInformation {
    public final Identity groupOwner;
    public final UID uid;
    public final String groupName;
    public final byte[] groupId;

    public OldGroupInformation(Identity groupOwner, UID uid, String groupName) {
        this.groupOwner = groupOwner;
        this.uid = uid;
        this.groupName = groupName;
        this.groupId = new byte[groupOwner.getBytes().length + UID.UID_LENGTH];
        System.arraycopy(groupOwner.getBytes(), 0, groupId, 0, groupOwner.getBytes().length);
        System.arraycopy(uid.getBytes(), 0, groupId, groupOwner.getBytes().length, UID.UID_LENGTH);
    }


    public Encoded encode() {
        return Encoded.of(new Encoded[]{
                Encoded.of(groupOwner),
                Encoded.of(uid),
                Encoded.of(groupName),
        });
    }

    public static OldGroupInformation of(Encoded encoded) throws DecodingException {
        Encoded[] encodeds = encoded.decodeList();
        if (encodeds.length != 3) {
            throw new DecodingException();
        }
        return new OldGroupInformation(
          encodeds[0].decodeIdentity(),
          encodeds[1].decodeUid(),
          encodeds[2].decodeString()
        );
    }
}
