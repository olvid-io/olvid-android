/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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


import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.encoder.Encoded;

public class PrivateIdentity {
    private final Identity publicIdentity;
    private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
    private final EncryptionPrivateKey encryptionPrivateKey;
    private final MACKey macKey;

    public PrivateIdentity(Identity publicIdentity, ServerAuthenticationPrivateKey serverAuthenticationPrivateKey, EncryptionPrivateKey encryptionPrivateKey, MACKey macKey) {
        this.publicIdentity = publicIdentity;
        this.serverAuthenticationPrivateKey = serverAuthenticationPrivateKey;
        this.encryptionPrivateKey = encryptionPrivateKey;
        this.macKey = macKey;
    }

    public Identity getPublicIdentity() {
        return publicIdentity;
    }

    public ServerAuthenticationPrivateKey getServerAuthenticationPrivateKey() {
        return serverAuthenticationPrivateKey;
    }

    public EncryptionPrivateKey getEncryptionPrivateKey() {
        return encryptionPrivateKey;
    }

    public UID computeUniqueUid() {
        return publicIdentity.computeUniqueUid();
    }

    public ServerAuthenticationPublicKey getServerAuthenticationPublicKey() {
        return publicIdentity.getServerAuthenticationPublicKey();
    }

    public EncryptionPublicKey getEncryptionPublicKey() {
        return publicIdentity.getEncryptionPublicKey();
    }

    public MACKey getMacKey() {
        return macKey;
    }

    public byte[] serialize() {
        return Encoded.of(new Encoded[]{
                Encoded.of(publicIdentity.getBytes()),
                Encoded.of(serverAuthenticationPrivateKey),
                Encoded.of(encryptionPrivateKey),
                Encoded.of(macKey)
        }).getBytes();
    }

    public static PrivateIdentity deserialize(byte[] bytes) {
        try {
            Encoded[] encodedElements = new Encoded(bytes).decodeList();
            return new PrivateIdentity(encodedElements[0].decodeIdentity(),
                    (ServerAuthenticationPrivateKey) encodedElements[1].decodePrivateKey(),
                    (EncryptionPrivateKey) encodedElements[2].decodePrivateKey(),
                    (MACKey) encodedElements[3].decodeSymmetricKey());
        } catch (Exception e) {
            Logger.w("An error occurred while deserializing a PrivateIdentity.");
            return null;
        }
    }
}
