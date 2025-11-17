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

package io.olvid.engine.datatypes;


import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.MAC;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.metamanager.IdentityDelegate;

public class PrivateIdentity {
    private final Identity publicIdentity;
    private final ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
    private final EncryptionPrivateKey encryptionPrivateKey;
    private final MACKey macKey;

    private static final byte[] COMPUTE_SAS_DETERMINISTIC_SEED_MAC_PAYLOAD = new byte[]{0x55};
    private static final byte[] COMPUTE_TRANSFER_SAS_DETERMINISTIC_SEED_MAC_PAYLOAD = new byte[]{0x56};
    private static final byte[] ENCRYPT_RETURN_RECEIPT_DETERMINISTIC_SEED_MAC_PAYLOAD = new byte[]{0x57};

    private static final byte[] BACKUP_SEED_FOR_LEGACY_IDENTITY_MAC_PAYLOAD = new byte[]{(byte) 0xcc};
    private static final byte[] BACKUP_SEED_FOR_LEGACY_IDENTITY_HASH_PADDING = "backupKey".getBytes(StandardCharsets.UTF_8);

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

    public static PrivateIdentity of(byte[] bytes) {
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


    public Seed getDeterministicSeedForOwnedIdentity(byte[] diversificationTag, IdentityDelegate.DeterministicSeedContext context) throws Exception {
        MAC mac = Suite.getMAC(macKey);
        byte[] digest;
        switch (context) {
            case COMPUTE_SAS -> digest = mac.digest(macKey, COMPUTE_SAS_DETERMINISTIC_SEED_MAC_PAYLOAD);
            case COMPUTE_TRANSFER_SAS -> digest = mac.digest(macKey, COMPUTE_TRANSFER_SAS_DETERMINISTIC_SEED_MAC_PAYLOAD);
            case ENCRYPT_RETURN_RECEIPT -> digest = mac.digest(macKey, ENCRYPT_RETURN_RECEIPT_DETERMINISTIC_SEED_MAC_PAYLOAD);
            default -> throw new Exception("Unknown deterministic seed context");
        }

        byte[] hashInput = new byte[digest.length + diversificationTag.length];
        System.arraycopy(digest, 0, hashInput, 0, digest.length);
        System.arraycopy(diversificationTag, 0, hashInput, digest.length, diversificationTag.length);
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] hash = sha256.digest(hashInput);
        return new Seed(hash);
    }

    public BackupSeed getDeterministicBackupSeedForLegacyIdentity() throws Exception {
        MAC mac = Suite.getMAC(macKey);
        byte[] digest = mac.digest(macKey, BACKUP_SEED_FOR_LEGACY_IDENTITY_MAC_PAYLOAD);
        byte[] hashInput = new byte[digest.length + BACKUP_SEED_FOR_LEGACY_IDENTITY_HASH_PADDING.length];
        System.arraycopy(digest, 0, hashInput, 0, digest.length);
        System.arraycopy(BACKUP_SEED_FOR_LEGACY_IDENTITY_HASH_PADDING, 0, hashInput, digest.length, BACKUP_SEED_FOR_LEGACY_IDENTITY_HASH_PADDING.length);
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] hash = sha256.digest(hashInput);
        return new BackupSeed(Arrays.copyOfRange(hash, 0, BackupSeed.BACKUP_SEED_LENGTH));
    }
}
