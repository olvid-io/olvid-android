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

package io.olvid.messenger.webclient;

import java.security.InvalidKeyException;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.crypto.exceptions.DecryptionException;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.key.symmetric.AuthEncAES256ThenSHA256Key;

public class Cryptography {
    private final AuthEncAES256ThenSHA256Key authEncKey;

    public Cryptography(AuthEncAES256ThenSHA256Key authEncKey) {
        this.authEncKey = authEncKey;
    }

    public byte[] encrypt(byte[] message) {
        EncryptedBytes encryptedBytes;

        try {
            encryptedBytes = Suite.getAuthEnc(authEncKey).encrypt(authEncKey, message, Suite.getDefaultPRNGService(0));
        } catch (InvalidKeyException e) {
            Logger.e("Unable to encrypt message", e);
            return (null);
        }
        return (encryptedBytes.getBytes());
    }

    public byte[] decrypt(byte[] encryptedMessage) {
        EncryptedBytes encryptedBytes;
        byte[] decryptedBytes;

        encryptedBytes = new EncryptedBytes(encryptedMessage);
        try {
            decryptedBytes = Suite.getAuthEnc(this.authEncKey).decrypt(this.authEncKey, encryptedBytes);
        } catch (InvalidKeyException | DecryptionException e) {
            Logger.e("Unable to decrypt message", e);
            return null;
        }
        return (decryptedBytes);
    }
}
