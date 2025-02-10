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

import java.security.InvalidKeyException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.key.symmetric.SymEncCTRAES256Key;
import io.olvid.engine.datatypes.key.symmetric.SymmetricKey;

interface SymEnc {
    String CTR_AES256 = "ctr-aes-256";

    int keyByteLength();
    int ivByteLength();
    int ciphertextLengthFromPlaintextLength(int plaintextLength);
    int plaintextLengthFromCiphertextLength(int ciphertextLength);
    void encrypt(byte[] iv, byte[] plaintext, byte[] ciphertext) throws InvalidKeyException;
    byte[] decrypt(EncryptedBytes ciphertext);
}

class KDFDelegateForSymEncCtrAES256 implements KDF.Delegate {
    @Override
    public int getKeyLength() {
        return SymEncCTRAES256Key.KEY_BYTE_LENGTH;
    }

    @Override
    public SymmetricKey[] processBytes(byte[] bytes) {
        return new SymmetricKey[]{
                SymEncCTRAES256Key.of(bytes)
        };
    }
}

class SymEncCtrAES256 implements SymEnc {
    private Cipher aes;
    private SymEncCTRAES256Key key;
    static final int KEY_BYTE_LENGTH = 32;
    static final int IV_BYTE_LENGTH = 8;
    static final int AES_BLOCK_BYTE_LENGTH = 16;

    private static final int ENCRYPT_BUFFER_SIZE = 262_144;

    SymEncCtrAES256(SymEncCTRAES256Key key) throws InvalidKeyException {
        try {
            aes = Cipher.getInstance("AES/CTR/NoPadding");
            this.key = key;
        } catch (Exception ignored) {}
    }

    @Override
    public int keyByteLength() {
        return KEY_BYTE_LENGTH;
    }

    @Override
    public int ivByteLength() {
        return IV_BYTE_LENGTH;
    }

    @Override
    public int ciphertextLengthFromPlaintextLength(int plaintextLength) {
        return plaintextLength + IV_BYTE_LENGTH;
    }

    @Override
    public int plaintextLengthFromCiphertextLength(int ciphertextLength) {
        return ciphertextLength - IV_BYTE_LENGTH;
    }

    @Override
    public void encrypt(byte[] iv, byte[] plaintext, byte[] ciphertext) throws InvalidKeyException {
        if (iv.length != IV_BYTE_LENGTH) {
            throw new InvalidKeyException();
        }
        System.arraycopy(iv, 0, ciphertext, 0, IV_BYTE_LENGTH);

        byte[] fullIV = new byte[AES_BLOCK_BYTE_LENGTH];
        System.arraycopy(iv, 0, fullIV, 0, IV_BYTE_LENGTH);

        try {
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getKeyBytes(), "AES"), new IvParameterSpec(fullIV));
            int outOffset = IV_BYTE_LENGTH;
            for (int offsetIn = 0; offsetIn < plaintext.length; offsetIn += ENCRYPT_BUFFER_SIZE) {
                int len = Math.min(plaintext.length - offsetIn, ENCRYPT_BUFFER_SIZE);
                outOffset += aes.update(plaintext, offsetIn, len, ciphertext, outOffset);
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    @Override
    public byte[] decrypt(EncryptedBytes ciphertext) {
        byte[] ciphertextBytes = ciphertext.getBytes();
        byte[] iv = Arrays.copyOfRange(ciphertextBytes, 0, IV_BYTE_LENGTH);
        byte[] ciphertextEnd = Arrays.copyOfRange(ciphertextBytes, IV_BYTE_LENGTH, ciphertextBytes.length);
        byte[] plaintext = null;

        byte[] fullIV = new byte[AES_BLOCK_BYTE_LENGTH];
        System.arraycopy(iv, 0, fullIV, 0, IV_BYTE_LENGTH);

        try {
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getKeyBytes(), "AES"), new IvParameterSpec(fullIV));
            plaintext = aes.doFinal(ciphertextEnd);
        } catch (Exception e) {
            Logger.x(e);
        }
        return plaintext;
    }
}