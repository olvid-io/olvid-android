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

package io.olvid.engine.datatypes.key.asymmetric;


import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.encoder.Encoded;

public class ServerAuthenticationECSdsaCurve25519PrivateKey extends ServerAuthenticationECSdsaPrivateKey {
    public ServerAuthenticationECSdsaCurve25519PrivateKey(HashMap<DictionaryKey, Encoded> key) {
        super(ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519, key, new SignatureECSdsaCurve25519PrivateKey(key));
    }
}
