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

import java.util.Objects;

import io.olvid.engine.datatypes.KeyId;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;

public class PreKey {
    public final UID deviceUid;
    public final KeyId keyId;
    public final EncryptionPublicKey encryptionPublicKey;
    public final long expirationTimestamp;

    public PreKey(UID deviceUid, KeyId keyId, EncryptionPublicKey encryptionPublicKey, long expirationTimestamp) {
        this.keyId = keyId;
        this.encryptionPublicKey = encryptionPublicKey;
        this.deviceUid = deviceUid;
        this.expirationTimestamp = expirationTimestamp;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreKey preKey = (PreKey) o;
        return expirationTimestamp == preKey.expirationTimestamp
                && Objects.equals(deviceUid, preKey.deviceUid)
                && Objects.equals(keyId, preKey.keyId)
                && Objects.equals(encryptionPublicKey, preKey.encryptionPublicKey);
    }
}
