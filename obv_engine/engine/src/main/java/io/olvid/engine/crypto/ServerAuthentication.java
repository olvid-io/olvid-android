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

package io.olvid.engine.crypto;


import java.security.InvalidKeyException;

import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaCurve25519PrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaCurve25519PublicKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaMDCPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaMDCPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;

public interface ServerAuthentication {
    byte[] solveChallenge(byte[] challenge, ServerAuthenticationPrivateKey privateKey, ServerAuthenticationPublicKey publicKey, PRNGService prng) throws InvalidKeyException;
}

abstract class ServerAuthenticationECSdsa implements ServerAuthentication {
    private final SignatureECSdsa signatureECSdsa;

    ServerAuthenticationECSdsa(SignatureECSdsa signatureECSdsa) {
        this.signatureECSdsa = signatureECSdsa;
    }

    byte[] internalSolveChallenge(byte[] challenge, ServerAuthenticationECSdsaPrivateKey privateKey, ServerAuthenticationECSdsaPublicKey publicKey, PRNGService prng) throws InvalidKeyException {
        byte[] padding = prng.bytes(Constants.SIGNATURE_PADDING_LENGTH);
        byte[] formattedChallenge = new byte[Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length + challenge.length + Constants.SIGNATURE_PADDING_LENGTH];
        System.arraycopy(Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX, 0, formattedChallenge, 0, Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length);
        System.arraycopy(challenge, 0, formattedChallenge, Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length, challenge.length);
        System.arraycopy(padding, 0, formattedChallenge, Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length + challenge.length, Constants.SIGNATURE_PADDING_LENGTH);
        byte[] signature = signatureECSdsa.sign(privateKey.getSignaturePrivateKey(), publicKey.getSignaturePublicKey(), formattedChallenge, prng);
        byte[] response = new byte[Constants.SIGNATURE_PADDING_LENGTH + signature.length];
        System.arraycopy(padding, 0, response, 0, Constants.SIGNATURE_PADDING_LENGTH);
        System.arraycopy(signature, 0, response, Constants.SIGNATURE_PADDING_LENGTH, signature.length);
        return response;
    }
}


class ServerAuthenticationECSdsaMDC extends ServerAuthenticationECSdsa {

    ServerAuthenticationECSdsaMDC() {
        super(new SignatureECSdsaMDC());
    }

    @Override
    public byte[] solveChallenge(byte[] challenge, ServerAuthenticationPrivateKey privateKey, ServerAuthenticationPublicKey publicKey, PRNGService prng) throws InvalidKeyException {
        if (!(publicKey instanceof ServerAuthenticationECSdsaMDCPublicKey) || !(privateKey instanceof ServerAuthenticationECSdsaMDCPrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalSolveChallenge(challenge, (ServerAuthenticationECSdsaPrivateKey) privateKey, (ServerAuthenticationECSdsaPublicKey) publicKey, prng);
    }
}



class ServerAuthenticationECSdsaCurve25519 extends ServerAuthenticationECSdsa {

    ServerAuthenticationECSdsaCurve25519() {
        super(new SignatureECSdsaCurve25519());
    }

    @Override
    public byte[] solveChallenge(byte[] challenge, ServerAuthenticationPrivateKey privateKey, ServerAuthenticationPublicKey publicKey, PRNGService prng) throws InvalidKeyException {
        if (!(publicKey instanceof ServerAuthenticationECSdsaCurve25519PublicKey) || !(privateKey instanceof ServerAuthenticationECSdsaCurve25519PrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalSolveChallenge(challenge, (ServerAuthenticationECSdsaPrivateKey) privateKey, (ServerAuthenticationECSdsaPublicKey) publicKey, prng);
    }
}