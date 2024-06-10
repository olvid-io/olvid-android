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
import java.util.Arrays;

import io.olvid.engine.crypto.exceptions.DecryptionException;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.symmetric.AuthEncAES256ThenSHA256Key;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.key.symmetric.MACHmacSha256Key;
import io.olvid.engine.datatypes.key.symmetric.SymEncCTRAES256Key;
import io.olvid.engine.datatypes.key.symmetric.SymmetricKey;

public interface AuthEnc {
    String CTR_AES256_THEN_HMAC_SHA256 = "ctr-aes-256_then_hmac_sha-256";

    int keyByteLength();
    int ciphertextLengthFromPlaintextLength(int plaintextLength);
    int plaintextLengthFromCiphertextLength(int ciphertextLength);
    EncryptedBytes encrypt(AuthEncKey key, byte[] plaintext, PRNG prng) throws InvalidKeyException;
    byte[] decrypt(AuthEncKey key, EncryptedBytes ciphertext) throws DecryptionException, InvalidKeyException;
    KDF.Delegate getKDFDelegate();
    AuthEncKey generateKey(PRNG prng);
    AuthEncKey generateMessageKey(PRNG prng, byte[] message);
    boolean verifyMessageKey(AuthEncKey authEncKey, byte[] message);
}


class KDFDelegateForAuthEncAES256ThenSHA256 implements KDF.Delegate {
    @Override
    public int getKeyLength() {
        return MACHmacSha256Key.KEY_BYTE_LENGTH + SymEncCTRAES256Key.KEY_BYTE_LENGTH;
    }

    @Override
    public SymmetricKey[] processBytes(byte[] bytes) {
        return new SymmetricKey[]{
                AuthEncAES256ThenSHA256Key.of(
                        Arrays.copyOfRange(bytes, 0, MACHmacSha256Key.KEY_BYTE_LENGTH),
                        Arrays.copyOfRange(bytes, MACHmacSha256Key.KEY_BYTE_LENGTH, MACHmacSha256Key.KEY_BYTE_LENGTH + SymEncCTRAES256Key.KEY_BYTE_LENGTH)
                )
        };
    }
}


class AuthEncAES256ThenSHA256 implements AuthEnc {
    @Override
    public int keyByteLength() {
        return MACHmacSha256Key.KEY_BYTE_LENGTH + SymEncCtrAES256.KEY_BYTE_LENGTH;
    }

    @Override
    public int ciphertextLengthFromPlaintextLength(int plaintextLength) {
        return plaintextLength + SymEncCtrAES256.IV_BYTE_LENGTH + MACHmacSha256.OUTPUT_LENGTH;
    }

    @Override
    public int plaintextLengthFromCiphertextLength(int ciphertextLength) {
        return ciphertextLength - SymEncCtrAES256.IV_BYTE_LENGTH - MACHmacSha256.OUTPUT_LENGTH;
    }

    @Override
    public EncryptedBytes encrypt(AuthEncKey key, byte[] plaintext, PRNG prng) throws InvalidKeyException {
        if (! (key instanceof AuthEncAES256ThenSHA256Key)) {
            throw new InvalidKeyException();
        }
        MACHmacSha256Key macKey = ((AuthEncAES256ThenSHA256Key) key).getMacKey();
        SymEncCTRAES256Key encKey = ((AuthEncAES256ThenSHA256Key) key).getEncKey();
        MACHmacSha256 mac = new MACHmacSha256();
        SymEncCtrAES256 enc = new SymEncCtrAES256(encKey);

        byte[] ciphertext = new byte[ciphertextLengthFromPlaintextLength(plaintext.length)];
        byte[] iv = prng.bytes(SymEncCtrAES256.IV_BYTE_LENGTH);
        enc.encrypt(iv, plaintext, ciphertext);
        byte[] hash = mac.digest(macKey, ciphertext, enc.ciphertextLengthFromPlaintextLength(plaintext.length));
        System.arraycopy(hash, 0, ciphertext, enc.ciphertextLengthFromPlaintextLength(plaintext.length), hash.length);
        return new EncryptedBytes(ciphertext);
    }

    @Override
    public byte[] decrypt(AuthEncKey key, EncryptedBytes ciphertext) throws DecryptionException, InvalidKeyException {
        if (! (key instanceof AuthEncAES256ThenSHA256Key)) {
            throw new InvalidKeyException();
        }
        MACHmacSha256Key macKey = ((AuthEncAES256ThenSHA256Key) key).getMacKey();
        SymEncCTRAES256Key encKey = ((AuthEncAES256ThenSHA256Key) key).getEncKey();
        MACHmacSha256 mac = new MACHmacSha256();
        SymEncCtrAES256 enc = new SymEncCtrAES256(encKey);

        byte[] ciphertextBytes = ciphertext.getBytes();
        byte[] hash = Arrays.copyOfRange(ciphertextBytes, ciphertextBytes.length - mac.outputLength(), ciphertextBytes.length);
        byte[] encryptedBytes = Arrays.copyOfRange(ciphertextBytes, 0, ciphertextBytes.length - mac.outputLength());
        if (!mac.verify(macKey, encryptedBytes, hash)) {
            throw new DecryptionException();
        }
        return enc.decrypt(new EncryptedBytes(encryptedBytes));
    }

    @Override
    public KDF.Delegate getKDFDelegate() {
        return new KDFDelegateForAuthEncAES256ThenSHA256();
    }

    @Override
    public AuthEncKey generateKey(PRNG prng) {
        KDF kdf = Suite.getKDF(KDF.KDF_SHA256);
        Seed kdfSeed = new Seed(prng);
        try {
            return (AuthEncKey) kdf.gen(kdfSeed, getKDFDelegate())[0];
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public AuthEncKey generateMessageKey(PRNG prng, byte[] message) {
        KDF kdf = Suite.getKDF(KDF.KDF_SHA256);

        Seed encryptionKdfSeed = new Seed(prng);
        SymEncCTRAES256Key symEncCTRAES256Key = (SymEncCTRAES256Key) kdf.gen(encryptionKdfSeed, new KDFDelegateForSymEncCtrAES256())[0];

        byte[] concatenation = new byte[symEncCTRAES256Key.getKeyLength() + message.length];
        System.arraycopy(symEncCTRAES256Key.getKeyBytes(), 0, concatenation, 0, symEncCTRAES256Key.getKeyLength());
        System.arraycopy(message, 0, concatenation, symEncCTRAES256Key.getKeyLength(), message.length);
        MACHmacSha256Key hmacSha256Key = (MACHmacSha256Key) kdf.gen(new Seed(concatenation), new KDFDelegateForHmacSHA256())[0];

        return AuthEncAES256ThenSHA256Key.of(hmacSha256Key.getKeyBytes(), symEncCTRAES256Key.getKeyBytes());
    }

    @Override
    public boolean verifyMessageKey(AuthEncKey authEncKey, byte[] message) {
        KDF kdf = Suite.getKDF(KDF.KDF_SHA256);

        if (authEncKey instanceof AuthEncAES256ThenSHA256Key) {
            SymEncCTRAES256Key symEncCTRAES256Key = ((AuthEncAES256ThenSHA256Key) authEncKey).getEncKey();

            byte[] concatenation = new byte[symEncCTRAES256Key.getKeyLength() + message.length];
            System.arraycopy(symEncCTRAES256Key.getKeyBytes(), 0, concatenation, 0, symEncCTRAES256Key.getKeyLength());
            System.arraycopy(message, 0, concatenation, symEncCTRAES256Key.getKeyLength(), message.length);
            MACHmacSha256Key hmacSha256Key = (MACHmacSha256Key) kdf.gen(new Seed(concatenation), new KDFDelegateForHmacSHA256())[0];

            return Arrays.equals(hmacSha256Key.getKeyBytes(), ((AuthEncAES256ThenSHA256Key) authEncKey).getMacKey().getKeyBytes());
        } else {
            return false;
        }
    }
}