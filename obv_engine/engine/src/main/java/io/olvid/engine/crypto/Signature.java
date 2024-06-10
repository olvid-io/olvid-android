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


import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HashMap;

import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EdwardCurvePoint;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.GroupV2;
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

public abstract class Signature {
    public abstract byte[] sign(SignaturePrivateKey privateKey, byte[] message, PRNGService prng) throws InvalidKeyException;
    public abstract byte[] sign(SignaturePrivateKey privateKey, SignaturePublicKey publicKey, byte[] message, PRNGService prng) throws InvalidKeyException;
    public abstract boolean verify(SignaturePublicKey publicKey, byte[] message, byte[] signature) throws InvalidKeyException;

    public static boolean verify(Constants.SignatureContext signatureContext, Identity[] identities, Identity signerIdentity, byte[] signature) throws Exception {
        try {
            SignaturePublicKey signaturePublicKey = signerIdentity.getServerAuthenticationPublicKey().getSignaturePublicKey();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //noinspection ConstantConditions
            baos.write(Constants.getSignatureChallengePrefix(signatureContext));
            for (Identity identity: identities) {
                baos.write(identity.getBytes());
            }
            baos.write(Arrays.copyOfRange(signature, 0, Constants.SIGNATURE_PADDING_LENGTH));
            byte[] challenge = baos.toByteArray();
            baos.close();

            Signature signatureAlgo = Suite.getSignature(signaturePublicKey);
            return signatureAlgo.verify(signaturePublicKey, challenge, Arrays.copyOfRange(signature, Constants.SIGNATURE_PADDING_LENGTH, signature.length));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean verify(Constants.SignatureContext signatureContext, UID deviceUidA, UID deviceUidB, Identity identityA, Identity identityB, Identity signerIdentity, byte[] signature) throws Exception {
        try {
            SignaturePublicKey signaturePublicKey = signerIdentity.getServerAuthenticationPublicKey().getSignaturePublicKey();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //noinspection ConstantConditions
            baos.write(Constants.getSignatureChallengePrefix(signatureContext));
            baos.write(deviceUidA.getBytes());
            baos.write(deviceUidB.getBytes());
            baos.write(identityA.getBytes());
            baos.write(identityB.getBytes());
            baos.write(Arrays.copyOfRange(signature, 0, Constants.SIGNATURE_PADDING_LENGTH));
            byte[] challenge = baos.toByteArray();
            baos.close();

            Signature signatureAlgo = Suite.getSignature(signaturePublicKey);
            return signatureAlgo.verify(signaturePublicKey, challenge, Arrays.copyOfRange(signature, Constants.SIGNATURE_PADDING_LENGTH, signature.length));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean verify(Constants.SignatureContext signatureContext, byte[] block, Identity signerIdentity, byte[] signature) throws Exception {
        try {
            SignaturePublicKey signaturePublicKey = signerIdentity.getServerAuthenticationPublicKey().getSignaturePublicKey();

            byte[] prefix = Constants.getSignatureChallengePrefix(signatureContext);
            byte[] padding = Arrays.copyOfRange(signature, 0, Constants.SIGNATURE_PADDING_LENGTH);
            //noinspection ConstantConditions
            byte[] challenge = new byte[prefix.length + block.length + Constants.SIGNATURE_PADDING_LENGTH];
            System.arraycopy(prefix, 0, challenge, 0, prefix.length);
            System.arraycopy(block, 0, challenge, prefix.length, block.length);
            System.arraycopy(padding, 0, challenge, prefix.length + block.length, Constants.SIGNATURE_PADDING_LENGTH);

            Signature signatureAlgo = Suite.getSignature(signaturePublicKey);
            return signatureAlgo.verify(signaturePublicKey, challenge, Arrays.copyOfRange(signature, Constants.SIGNATURE_PADDING_LENGTH, signature.length));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean verify(Constants.SignatureContext signatureContext, GroupV2.Identifier groupIdentifier, byte[] nonce, Identity contactIdentity, Identity signerIdentity, byte[] signature) throws Exception {
        try {
            SignaturePublicKey signaturePublicKey = signerIdentity.getServerAuthenticationPublicKey().getSignaturePublicKey();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //noinspection ConstantConditions
            baos.write(Constants.getSignatureChallengePrefix(signatureContext));
            baos.write(groupIdentifier.getBytes());
            baos.write(nonce);
            if (contactIdentity != null) {
                baos.write(contactIdentity.getBytes());
            }
            baos.write(Arrays.copyOfRange(signature, 0, Constants.SIGNATURE_PADDING_LENGTH));
            byte[] challenge = baos.toByteArray();
            baos.close();

            Signature signatureAlgo = Suite.getSignature(signaturePublicKey);
            return signatureAlgo.verify(signaturePublicKey, challenge, Arrays.copyOfRange(signature, Constants.SIGNATURE_PADDING_LENGTH, signature.length));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static byte[] sign(Constants.SignatureContext signatureContext, SignaturePrivateKey signaturePrivateKey, PRNGService prng) {
        try {
            byte[] prefix = Constants.getSignatureChallengePrefix(signatureContext);
            byte[] padding = prng.bytes(Constants.SIGNATURE_PADDING_LENGTH);
            //noinspection ConstantConditions
            byte[] challenge = new byte[prefix.length + Constants.SIGNATURE_PADDING_LENGTH];
            System.arraycopy(prefix, 0, challenge, 0, prefix.length);
            System.arraycopy(padding, 0, challenge, prefix.length, Constants.SIGNATURE_PADDING_LENGTH);

            byte[] signatureBytes = Suite.getSignature(signaturePrivateKey).sign(signaturePrivateKey, challenge, prng);
            byte[] output = new byte[Constants.SIGNATURE_PADDING_LENGTH + signatureBytes.length];
            System.arraycopy(padding, 0, output, 0, Constants.SIGNATURE_PADDING_LENGTH);
            System.arraycopy(signatureBytes, 0, output, Constants.SIGNATURE_PADDING_LENGTH, signatureBytes.length);
            return output;
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] sign(Constants.SignatureContext signatureContext, byte[] data, SignaturePrivateKey signaturePrivateKey, PRNGService prng) {
        try {
            byte[] prefix = Constants.getSignatureChallengePrefix(signatureContext);
            byte[] padding = prng.bytes(Constants.SIGNATURE_PADDING_LENGTH);
            //noinspection ConstantConditions
            byte[] challenge = new byte[prefix.length + data.length + Constants.SIGNATURE_PADDING_LENGTH];
            System.arraycopy(prefix, 0, challenge, 0, prefix.length);
            System.arraycopy(data, 0, challenge, prefix.length, data.length);
            System.arraycopy(padding, 0, challenge, prefix.length + data.length, Constants.SIGNATURE_PADDING_LENGTH);

            byte[] signatureBytes = Suite.getSignature(signaturePrivateKey).sign(signaturePrivateKey, challenge, prng);
            byte[] output = new byte[Constants.SIGNATURE_PADDING_LENGTH + signatureBytes.length];
            System.arraycopy(padding, 0, output, 0, Constants.SIGNATURE_PADDING_LENGTH);
            System.arraycopy(signatureBytes, 0, output, Constants.SIGNATURE_PADDING_LENGTH, signatureBytes.length);
            return output;
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }
}


abstract class SignatureECSdsa extends Signature {
    private final EdwardCurve curve;

    SignatureECSdsa(EdwardCurve curve) {
        this.curve = curve;
    }

    public byte[] internalSign(SignatureECSdsaPrivateKey privateKey, byte[] message, PRNGService prng) {
        try {
            EdwardCurvePoint A = curve.scalarMultiplicationWithX(privateKey.getA(), curve.G);
            HashMap<DictionaryKey, Encoded> publicKeyDictionary = new HashMap<>();
            publicKeyDictionary.put(new DictionaryKey(SignatureECSdsaPublicKey.PUBLIC_X_COORD_KEY_NAME), Encoded.of(A.getX(), curve.byteLength));
            publicKeyDictionary.put(new DictionaryKey(SignatureECSdsaPublicKey.PUBLIC_Y_COORD_KEY_NAME), Encoded.of(A.getY(), curve.byteLength));

            SignatureECSdsaPublicKey publicKey = new SignatureECSdsaPublicKey(privateKey.getAlgorithmImplementation(), publicKeyDictionary){};
            return internalSign(privateKey, publicKey, message, prng);
        } catch (Exception ignored) {}
        return null;
    }

    public byte[] internalSign(SignatureECSdsaPrivateKey privateKey, SignatureECSdsaPublicKey publicKey, byte[] message, PRNGService prng) {
        try {
            int l = curve.byteLength;
            byte[] hashInput = new byte[message.length + 2*l];
            EdwardCurve.ScalarAndPoint aAndaG = curve.generateRandomScalarAndPoint(prng);
            System.arraycopy(Encoded.bytesFromBigUInt(aAndaG.getPoint().getY(), l), 0, hashInput, 0, l);
            System.arraycopy(Encoded.bytesFromBigUInt(publicKey.getAy(), l), 0, hashInput, l, l);
            System.arraycopy(message, 0, hashInput, 2*l, message.length);

            // TODO: switch to SHA512 once all clients support signature with SHA512 hash
//            byte[] hash = new HashSHA512().digest(hashInput);
            byte[] hash = new HashSHA256().digest(hashInput);
            BigInteger e = Encoded.bigUIntFromBytes(hash);
            BigInteger y = aAndaG.getScalar().subtract(privateKey.getA().multiply(e)).mod(curve.q);

            byte[] signature = new byte[hash.length + l];
            System.arraycopy(hash, 0, signature, 0, hash.length);
            System.arraycopy(Encoded.bytesFromBigUInt(y, l), 0, signature, hash.length, l);
            return signature;
        } catch (EncodingException ignored) {}
        return null;
    }

    public boolean internalVerify(SignatureECSdsaPublicKey publicKey, byte[] message, byte[] signature) {
        try {
            int l = curve.byteLength;
            // Our verification supports both hash with SHA256 (legacy) and SHA512
            boolean isSha512;
            if (signature.length == HashSHA256.OUTPUT_LENGTH + l) {
                isSha512 = false;
            } else if (signature.length == HashSHA512.OUTPUT_LENGTH + l) {
                isSha512 = true;
            } else {
                return false;
            }
            EdwardCurvePoint A = EdwardCurvePoint.noCheckFactory(publicKey.getAx(), publicKey.getAy(), curve);

            // check that the public key A is not a low order point
            if (A.isLowOrderPoint()) {
                return false;
            }

            byte[] hash = Arrays.copyOfRange(signature, 0, signature.length  - l);
            BigInteger e = Encoded.bigUIntFromBytes(hash);
            BigInteger y = Encoded.bigUIntFromBytes(Arrays.copyOfRange(signature, signature.length - l, signature.length));
            // check that the signature y is indeed smaller than q (to prevent undetected signature reuse)
            if (y.compareTo(curve.q) >= 0) {
                return false;
            }
            EdwardCurvePoint[] points = curve.mulAdd(y, curve.G, e, A);

            byte[] hashInput = new byte[message.length + 2*l];
            System.arraycopy(Encoded.bytesFromBigUInt(publicKey.getAy(), l), 0, hashInput, l, l);
            System.arraycopy(message, 0, hashInput, 2*l, message.length);

            for (EdwardCurvePoint point: points) {
                System.arraycopy(Encoded.bytesFromBigUInt(point.getY(), l), 0, hashInput, 0, l);
                byte[] recomputedHash = isSha512 ? new HashSHA512().digest(hashInput) : new HashSHA256().digest(hashInput);;
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
    public byte[] sign(SignaturePrivateKey privateKey, byte[] message, PRNGService prng) throws InvalidKeyException {
        if (! (privateKey instanceof SignatureECSdsaMDCPrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalSign((SignatureECSdsaMDCPrivateKey) privateKey, message, prng);
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
    public byte[] sign(SignaturePrivateKey privateKey, byte[] message, PRNGService prng) throws InvalidKeyException {
        if (! (privateKey instanceof SignatureECSdsaCurve25519PrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalSign((SignatureECSdsaPrivateKey) privateKey, message, prng);
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