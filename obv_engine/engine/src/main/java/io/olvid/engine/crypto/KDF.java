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

package io.olvid.engine.crypto;


import java.security.InvalidParameterException;

import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.symmetric.SymmetricKey;

public interface KDF {
    // WARNING: all KDF implementations must rely on a PRNG behaving as a random oracle. This is required for the security proof of ECIES.
    String KDF_SHA256 = "kdf_sha-256";

    SymmetricKey[] gen(Seed seed, KDF.Delegate delegate) throws InvalidParameterException;

    interface Delegate {
        int getKeyLength();
        SymmetricKey[] processBytes(byte[] bytes);
    }
}

class KDFSha256 implements KDF {
    @Override
    public SymmetricKey[] gen(Seed seed, KDF.Delegate delegate) throws InvalidParameterException {
        PRNGHmacSHA256 prng = new PRNGHmacSHA256(seed);
        byte[] bytes = prng.bytes(delegate.getKeyLength());
        return delegate.processBytes(bytes);
    }
}
