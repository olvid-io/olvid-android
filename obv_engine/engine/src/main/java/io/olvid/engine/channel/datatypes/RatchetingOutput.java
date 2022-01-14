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

package io.olvid.engine.channel.datatypes;


import io.olvid.engine.datatypes.KeyId;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;

public class RatchetingOutput {
    private final Seed ratchetedSeed;
    private final KeyId keyId;
    private final AuthEncKey authEncKey;

    public RatchetingOutput(Seed ratchetedSeed, KeyId keyId, AuthEncKey authEncKey) {
        this.ratchetedSeed = ratchetedSeed;
        this.keyId = keyId;
        this.authEncKey = authEncKey;
    }

    public Seed getRatchetedSeed() {
        return ratchetedSeed;
    }

    public KeyId getKeyId() {
        return keyId;
    }

    public AuthEncKey getAuthEncKey() {
        return authEncKey;
    }


}
