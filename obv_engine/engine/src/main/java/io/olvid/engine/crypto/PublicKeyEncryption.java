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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.util.Arrays;

import io.olvid.engine.crypto.exceptions.DecryptionException;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.containers.CiphertextAndKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesCurve25519PrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesCurve25519PublicKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesMDCPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesMDCPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.encoder.EncodingException;


public interface PublicKeyEncryption {
    EncryptedBytes encrypt(EncryptionPublicKey publicKey, byte[] plaintext, PRNGService prng) throws InvalidKeyException;
    byte[] decrypt(EncryptionPrivateKey privateKey, EncryptedBytes ciphertext) throws InvalidKeyException, DecryptionException;
    CiphertextAndKey kemEncrypt(EncryptionPublicKey publicKey, PRNGService prng) throws InvalidKeyException;
    AuthEncKey kemDecrypt(EncryptionPrivateKey privateKey, EncryptedBytes ciphertext) throws InvalidKeyException, DecryptionException;
}

abstract class PublicKeyEncryptionEcies implements PublicKeyEncryption {
    private final KemEcies256Kem512 kem;
    private final AuthEncAES256ThenSHA256 dem;

    protected PublicKeyEncryptionEcies(KemEcies256Kem512 kem) {
        this.kem = kem;
        this.dem = new AuthEncAES256ThenSHA256();
    }

    public EncryptedBytes encrypt(EncryptionPublicKey publicKey, byte[] plaintext, PRNGService prng) throws InvalidKeyException {
        byte[] ciphertextBytes = new byte[kem.ciphertextLength() + dem.ciphertextLengthFromPlaintextLength(plaintext.length)];

        CiphertextAndKey ciphertextAndKey = kem.encrypt(publicKey, prng);
        System.arraycopy(ciphertextAndKey.getCiphertext().getBytes(), 0, ciphertextBytes, 0, kem.ciphertextLength());

        EncryptedBytes demCiphertext = dem.encrypt(ciphertextAndKey.getKey(), plaintext, prng);
        System.arraycopy(demCiphertext.getBytes(), 0, ciphertextBytes, kem.ciphertextLength(), dem.ciphertextLengthFromPlaintextLength(plaintext.length));

        return new EncryptedBytes(ciphertextBytes);
    }

    public byte[] decrypt(EncryptionPrivateKey privateKey, EncryptedBytes ciphertext) throws InvalidKeyException, DecryptionException {

        byte[] ciphertextBytes = ciphertext.getBytes();
        byte[] kemCiphertext = Arrays.copyOfRange(ciphertext.getBytes(), 0, KemEcies256Kem512.CIPHERTEXT_LENGTH);
        AuthEncKey key = kem.decrypt(privateKey, kemCiphertext);

        EncryptedBytes demCiphertext = new EncryptedBytes(Arrays.copyOfRange(ciphertext.getBytes(), KemEcies256Kem512.CIPHERTEXT_LENGTH, ciphertextBytes.length));
        return dem.decrypt(key, demCiphertext);
    }

    public CiphertextAndKey kemEncrypt(EncryptionPublicKey publicKey, PRNGService prng) throws InvalidKeyException {
        return kem.encrypt(publicKey, prng);
    }

    public AuthEncKey kemDecrypt(EncryptionPrivateKey privateKey, EncryptedBytes ciphertext) throws InvalidKeyException, DecryptionException {
        if (ciphertext.length != KemEcies256Kem512.CIPHERTEXT_LENGTH) {
            throw new DecryptionException("Bad kem ciphertext length");
        }
        return kem.decrypt(privateKey, ciphertext.getBytes());
    }
}

class PublicKeyEncryptionEciesMDC extends PublicKeyEncryptionEcies {
    protected PublicKeyEncryptionEciesMDC() {
        super(new KemEcies256Kem512MDC());
    }
}

class PublicKeyEncryptionEciesCurve25519 extends PublicKeyEncryptionEcies {
    protected PublicKeyEncryptionEciesCurve25519() {
        super(new KemEcies256Kem512Curve25519());
    }
}


interface KEM {
    CiphertextAndKey encrypt(EncryptionPublicKey publicKey, PRNGService prng) throws InvalidKeyException;
    AuthEncKey decrypt(EncryptionPrivateKey privateKey, byte[] ciphertext) throws InvalidKeyException;
    int ciphertextLength();

}

abstract class KemEcies256Kem512 implements KEM {
    private final EdwardCurve curve;
    public static final int CIPHERTEXT_LENGTH = 32;

    protected KemEcies256Kem512(EdwardCurve curve) {
        this.curve = curve;
    }

    CiphertextAndKey internalEncrypt(EncryptionPublicKey publicKey, PRNGService prng) {
        BigInteger Ay = ((EncryptionEciesPublicKey) publicKey).getAy();
        int l = curve.byteLength;
        BigInteger r;
        do {
            r = prng.bigInt(curve.q);
        } while (r.equals(BigInteger.ZERO));
        BigInteger Gy = curve.G.getY();
        BigInteger By = curve.scalarMultiplication(r, Gy);
        BigInteger Dy = curve.scalarMultiplication(r, Ay);
        try {
            byte[] ciphertext = Encoded.bytesFromBigUInt(By, l);
            byte[] seedBytes = new byte[2 * l];
            System.arraycopy(ciphertext, 0, seedBytes, 0, l);
            System.arraycopy(Encoded.bytesFromBigUInt(Dy, l), 0, seedBytes, l, l);
            AuthEncKey key = (AuthEncKey) new KDFSha256().gen(new Seed(seedBytes), new KDFDelegateForAuthEncAES256ThenSHA256())[0];
            return new CiphertextAndKey(key, new EncryptedBytes(ciphertext));
        } catch (EncodingException e) {
            return null;
        }
    }

    AuthEncKey internalDecrypt(EncryptionPrivateKey privateKey, byte[] c) {
        BigInteger a = ((EncryptionEciesPrivateKey) privateKey).getA();
        int l = curve.byteLength;
        if (c.length != l) {
            return null;
        }
        BigInteger By = Encoded.bigUIntFromBytes(c);
        By = curve.scalarMultiplication(curve.nu, By);
        if (By.equals(BigInteger.ONE)) {
            return null;
        }
        a = a.multiply(curve.nu.modInverse(curve.q)).mod(curve.q);
        BigInteger Dy = curve.scalarMultiplication(a, By);
        try {
            byte[] seedBytes = new byte[2 * l];
            System.arraycopy(c, 0, seedBytes, 0, l);
            System.arraycopy(Encoded.bytesFromBigUInt(Dy, l), 0, seedBytes, l, l);
            return (AuthEncKey) new KDFSha256().gen(new Seed(seedBytes), new KDFDelegateForAuthEncAES256ThenSHA256())[0];
        } catch (EncodingException e) {
            return null;
        }
    }

    @Override
    public int ciphertextLength() {
        return CIPHERTEXT_LENGTH;
    }
}

class KemEcies256Kem512MDC extends KemEcies256Kem512 {
    KemEcies256Kem512MDC() {
        super(MDC.getInstance());
    }

    @Override
    public CiphertextAndKey encrypt(EncryptionPublicKey publicKey, PRNGService prng) throws InvalidKeyException {
        if (! (publicKey instanceof EncryptionEciesMDCPublicKey)) {
            throw new InvalidKeyException();
        }
        return internalEncrypt(publicKey, prng);
    }

    @Override
    public AuthEncKey decrypt(EncryptionPrivateKey privateKey, byte[] ciphertext) throws InvalidKeyException {
        if (! (privateKey instanceof EncryptionEciesMDCPrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalDecrypt(privateKey, ciphertext);
    }
}

class KemEcies256Kem512Curve25519 extends KemEcies256Kem512 {
    KemEcies256Kem512Curve25519() {
        super(Curve25519.getInstance());
    }

    @Override
    public CiphertextAndKey encrypt(EncryptionPublicKey publicKey, PRNGService prng) throws InvalidKeyException {
        if (! (publicKey instanceof EncryptionEciesCurve25519PublicKey)) {
            throw new InvalidKeyException();
        }
        return internalEncrypt(publicKey, prng);
    }

    @Override
    public AuthEncKey decrypt(EncryptionPrivateKey privateKey, byte[] ciphertext) throws InvalidKeyException {
        if (! (privateKey instanceof EncryptionEciesCurve25519PrivateKey)) {
            throw new InvalidKeyException();
        }
        return internalDecrypt(privateKey, ciphertext);
    }
}