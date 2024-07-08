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

package io.olvid.engine.datatypes;

import java.util.HashMap;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.datatypes.containers.PreKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.encoder.Encoded;

public class PreKeyBlobOnServer {
    public final PreKey preKey;
    public final String[] rawDeviceCapabilities;

    public PreKeyBlobOnServer(PreKey preKey, String[] rawDeviceCapabilities) {
        this.preKey = preKey;
        this.rawDeviceCapabilities = rawDeviceCapabilities;
    }

    public static PreKeyBlobOnServer verifySignatureAndDecode(Encoded encodedSignedPreKey, Identity preKeyOwnerIdentity, UID expectedDeviceUid, Long serverTimestamp) {
        try {
            Encoded verifiedEncodedPreKey = null; // will remain null if the signature is invalid
            try {
                Encoded[] encodeds = encodedSignedPreKey.decodeList();
                byte[] payload = encodeds[0].getBytes();
                byte[] signature = encodeds[1].decodeBytes();
                if (Signature.verify(Constants.SignatureContext.DEVICE_PRE_KEY, payload, preKeyOwnerIdentity, signature)) {
                    verifiedEncodedPreKey = encodeds[0];
                } else {
                    Logger.i("PreKey signature verification failed.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (verifiedEncodedPreKey != null) {
                try {
                    HashMap<DictionaryKey, Encoded> dict = verifiedEncodedPreKey.decodeDictionary();
                    Encoded encodedPreKey = dict.get(new DictionaryKey("prk"));
                    if (encodedPreKey == null) {
                        throw new Exception();
                    }
                    Encoded[] encodeds = encodedPreKey.decodeList();
                    UID deviceUid = encodeds[2].decodeUid();
                    if (!Objects.equals(expectedDeviceUid, deviceUid)) {
                        Logger.w("Device UID mismatch for a preKey received from server");
                        throw new Exception();
                    }
                    KeyId keyId = new KeyId(encodeds[0].decodeBytes());
                    byte[] compactEncryptionPublicKey = encodeds[1].decodeBytes();
                    EncryptionPublicKey encryptionPublicKey = EncryptionPublicKey.of(compactEncryptionPublicKey);
                    long expirationTimestamp = encodeds[3].decodeLong();

                    String[] capabilityStrings;
                    Encoded encodedCapabilities = dict.get(new DictionaryKey("cap"));
                    if (encodedCapabilities != null) {
                        capabilityStrings = encodedCapabilities.decodeStringArray();
                    } else {
                        capabilityStrings = null;
                    }

                    // check that the received preKey is not already expired
                    if (serverTimestamp != null && expirationTimestamp > serverTimestamp) {
                        return new PreKeyBlobOnServer(new PreKey(expectedDeviceUid, keyId, encryptionPublicKey, expirationTimestamp), capabilityStrings);
                    }
                } catch (Exception e) {
                    Logger.i("PreKey decoding failed.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
