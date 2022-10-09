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

package io.olvid.engine.datatypes.key.asymmetric;


import io.olvid.engine.datatypes.key.CryptographicKey;

public class KeyPair {
    protected final PublicKey publicKey;
    protected final PrivateKey privateKey;

    public KeyPair(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public static boolean areKeysMatching(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        if ((publicKey.getAlgorithmClass() != privateKey.getAlgorithmClass()) || (publicKey.getAlgorithmImplementation() != privateKey.getAlgorithmImplementation())) {
            return false;
        }

        switch (publicKey.getAlgorithmClass()) {
            case CryptographicKey.ALGO_CLASS_SERVER_AUTHENTICATION: {
                switch (publicKey.getAlgorithmImplementation()) {
                    case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                        return ServerAuthenticationECSdsaMDCKeyPair.areKeysMatching((ServerAuthenticationECSdsaMDCPublicKey) publicKey, (ServerAuthenticationECSdsaMDCPrivateKey) privateKey);
                    case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                        return ServerAuthenticationECSdsaCurve25519KeyPair.areKeysMatching((ServerAuthenticationECSdsaCurve25519PublicKey) publicKey, (ServerAuthenticationECSdsaCurve25519PrivateKey) privateKey);
                }
                break;
            }
            case CryptographicKey.ALGO_CLASS_SIGNATURE: {
                break;
            }
        }
        throw new Exception("Keys match check not implemented");
    }

}
