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

package io.olvid.engine.metamanager;


import java.sql.SQLException;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;

public interface EncryptionForIdentityDelegate {
    EncryptedBytes wrap(AuthEncKey messageKey, Identity toIdentity, PRNGService prng);
    AuthEncKey unwrap(Session session, EncryptedBytes wrappedKey, Identity toIdentity) throws SQLException;
    byte[] decrypt(Session session, EncryptedBytes ciphertext, Identity toIdentity) throws SQLException;
}
