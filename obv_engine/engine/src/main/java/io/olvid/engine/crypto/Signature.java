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

package io.olvid.engine.crypto;


import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.util.Arrays;

import io.olvid.engine.datatypes.EdwardCurvePoint;
import io.olvid.engine.datatypes.key.asymmetric.SignatureECSdsaCurve25519PrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.SignatureECSdsaCurve25519PublicKey;
import io.olvid.engine.datatypes.key.asymmetric.SignatureECSdsaMDCPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.SignatureECSdsaMDCPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.SignatureECSdsaPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.SignatureECSdsaPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.SignaturePrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.SignaturePublicKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.encoder.EncodingException;

public interface Signature {
    byte[] sign(SignaturePrivateKey privateKey, SignaturePublicKey publicKey, byte[] message, PRNGService prng) throws InvalidKeyException;
    boolean verify(SignaturePublicKey publicKey, byte[] message, byte[] signature) throws InvalidKeyException;
}


abstract class SignatureECSdsa implements Signature {
    private final EdwardCurve curve;

    SignatureECSdsa(EdwardCurve curve) {
        this.curve = curve;
    }

    public byte[] internalSign(SignatureECSdsaPrivateKey privateKey, SignatureECSdsaPublicKey publicKey, byte[] message, PRNGService prng) {
        try {
            int l = curve.byteLength;
            byte[] hashInput = new byte[message.length + 2*l];
            EdwardCurve.ScalarAndPoint aAndaG = curve.generateRandomScalarAndPoint(prng);
            System.arraycopy(Encoded.bytesFromBigUInt(aAndaG.getPoint().getY(), l), 0, hashInput, 0, l);
            System.arraycopy(Encoded.bytesFromBigUInt(publicKey.getAy(), l), 0, hashInput, l, l);
            System.arraycopy(message, 0, hashInput, 2*l, message.length);

            byte[] hash = new HashSHA256().digest(hashInput);
            BigInteger e = Encoded.bigUIntFromBytes(hash);
            BigInteger y = aAndaG.getScalar().subtract(privateKey.getA().multiply(e)).mod(curve.q);

            byte[] signature = new byte[HashSHA256.OUTPUT_LENGTH + l];
            System.arraycopy(hash, 0, signature, 0, HashSHA256.OUTPUT_LENGTH);
            System.arraycopy(Encoded.bytesFromBigUInt(y, l), 0, signature, HashSHA256.OUTPUT_LENGTH, l);
            return signature;
        } catch (EncodingException ignored) {}
        return null;
    }

    public boolean internalVerify(SignatureECSdsaPublicKey publicKey, byte[] message, byte[] signature) {
        try {
            int l = curve.byteLength;
            if (signature.length != HashSHA256.OUTPUT_LENGTH + l) {
                return false;
            }
            EdwardCurvePoint A = EdwardCurvePoint.noCheckFactory(publicKey.getAx(), publicKey.getAy(), curve);

            byte[] hash = Arrays.copyOfRange(signature, 0, HashSHA256.OUTPUT_LENGTH);
            BigInteger e = Encoded.bigUIntFromBytes(hash);
            BigInteger y = Encoded.bigUIntFromBytes(Arrays.copyOfRange(signature, HashSHA256.OUTPUT_LENGTH, HashSHA256.OUTPUT_LENGTH + l));
            EdwardCurvePoint[] points = curve.mulAdd(y, curve.G, e, A);

            byte[] hashInput = new byte[message.length + 2*l];
            System.arraycopy(Encoded.bytesFromBigUInt(publicKey.getAy(), l), 0, hashInput, l, l);
            System.arraycopy(message, 0, hashInput, 2*l, message.length);


            for (EdwardCurvePoint point: points) {
                System.arraycopy(Encoded.bytesFromBigUInt(point.getY(), l), 0, hashInput, 0, l);
                byte[] recomputedHash = new HashSHA256().digest(hashInput);
                if (Arrays.equals(hash, recomputedHash)) {
                    return true;
                }
            }
        } catch (EncodingException ignored) {}
        return false;
    }
}

class SignatureECSdsaMDC extends SignatureECSdsa {
    SignatureECSdsaMDC() {
        super(MDC.getInstance());
    }

    @Override
    public byte[] sign(SignaturePrivateKey privateKey, SignaturePublicKey publicKey, byte[] message, PRNGService prng) throws InvalidKeyException {
        if (! (privateKey instanceof SignatureECSdsaMDCPrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalSign((SignatureECSdsaPrivateKey) privateKey, (SignatureECSdsaPublicKey) publicKey, message, prng);
    }

    @Override
    public boolean verify(SignaturePublicKey publicKey, byte[] message, byte[] signature) throws InvalidKeyException {
        if (! (publicKey instanceof SignatureECSdsaMDCPublicKey)) {
            throw new InvalidKeyException();
        }
        return internalVerify((SignatureECSdsaPublicKey) publicKey, message, signature);
    }
}

class SignatureECSdsaCurve25519 extends SignatureECSdsa {
    SignatureECSdsaCurve25519() {
        super(Curve25519.getInstance());
    }

    @Override
    public byte[] sign(SignaturePrivateKey privateKey, SignaturePublicKey publicKey, byte[] message, PRNGService prng) throws InvalidKeyException {
        if (! (privateKey instanceof SignatureECSdsaCurve25519PrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalSign((SignatureECSdsaPrivateKey) privateKey, (SignatureECSdsaPublicKey) publicKey, message, prng);
    }

    @Override
    public boolean verify(SignaturePublicKey publicKey, byte[] message, byte[] signature) throws InvalidKeyException {
        if (! (publicKey instanceof SignatureECSdsaCurve25519PublicKey)) {
            throw new InvalidKeyException();
        }
        return internalVerify((SignatureECSdsaPublicKey) publicKey, message, signature);
    }
}