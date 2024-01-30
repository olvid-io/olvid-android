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

package io.olvid.engine.datatypes.containers;

import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class GroupInformation {
    public final Identity groupOwnerIdentity;
    public final UID groupUid;
    public final String serializedGroupDetailsWithVersionAndPhoto;

    public GroupInformation(Identity groupOwnerIdentity, UID groupUid, String serializedGroupDetailsWithVersionAndPhoto) {
        this.groupOwnerIdentity = groupOwnerIdentity;
        this.groupUid = groupUid;
        this.serializedGroupDetailsWithVersionAndPhoto = serializedGroupDetailsWithVersionAndPhoto;
    }

    public Encoded encode() {
        return Encoded.of(new Encoded[]{
                Encoded.of(groupOwnerIdentity),
                Encoded.of(groupUid),
                Encoded.of(serializedGroupDetailsWithVersionAndPhoto),
        });
    }

    public static GroupInformation of(Encoded encoded) throws DecodingException {
        Encoded[] encodeds = encoded.decodeList();
        if (encodeds.length != 3) {
            throw new DecodingException();
        }
        return new GroupInformation(
                encodeds[0].decodeIdentity(),
                encodeds[1].decodeUid(),
                encodeds[2].decodeString()
        );
    }

    public static GroupInformation generate(Identity groupOwner, String serializedGroupDetailsWithVersionAndPhoto, PRNG prng) {
        UID groupUid = new UID(prng);
        return new GroupInformation(groupOwner, groupUid, serializedGroupDetailsWithVersionAndPhoto);
    }

    public UID computeProtocolUid() {
        return computeProtocolUid(groupOwnerIdentity.getBytes(), groupUid.getBytes());
    }

    public static UID computeProtocolUid(byte[] bytesGroupOwnerIdentity, byte[] bytesGroupUid) {
        Seed prngSeed = new Seed(new Seed(bytesGroupOwnerIdentity), new Seed(bytesGroupUid));
        PRNG seededPRNG = Suite.getDefaultPRNG(0, prngSeed);
        return new UID(seededPRNG);
    }

    public byte[] getGroupOwnerAndUid() {
        byte[] groupOwnerAndUid = new byte[groupOwnerIdentity.getBytes().length + UID.UID_LENGTH];
        System.arraycopy(groupOwnerIdentity.getBytes(), 0, groupOwnerAndUid, 0, groupOwnerIdentity.getBytes().length);
        System.arraycopy(groupUid.getBytes(), 0, groupOwnerAndUid, groupOwnerIdentity.getBytes().length, UID.UID_LENGTH);
        return groupOwnerAndUid;
    }
}
