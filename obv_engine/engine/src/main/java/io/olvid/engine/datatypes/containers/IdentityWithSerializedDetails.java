/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class IdentityWithSerializedDetails implements Comparable<IdentityWithSerializedDetails> {
    public final Identity identity;
    public final String serializedDetails;

    public IdentityWithSerializedDetails(Identity identity, String serializedDetails) {
        this.identity = identity;
        this.serializedDetails = serializedDetails;
    }

    @Override
    public int hashCode() {
        return identity.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof IdentityWithSerializedDetails)) {
            return false;
        }
        return identity.equals(((IdentityWithSerializedDetails) other).identity);
    }

    @Override
    public int compareTo(IdentityWithSerializedDetails other) {
        return identity.compareTo(other.identity);
    }

    public Encoded encode() {
        return Encoded.of(new Encoded[]{
           Encoded.of(identity),
           Encoded.of(serializedDetails),
        });
    }

    public static IdentityWithSerializedDetails of(Encoded encoded) throws DecodingException {
        Encoded[] encodeds = encoded.decodeList();
        if (encodeds.length != 2) {
            throw new DecodingException();
        }
        return new IdentityWithSerializedDetails(encodeds[0].decodeIdentity(), encodeds[1].decodeString());
    }
}
