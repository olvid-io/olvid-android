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

import java.util.Arrays;

import io.olvid.engine.crypto.MAC;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesCurve25519KeyPair;
import io.olvid.engine.datatypes.key.symmetric.MACKey;

// A Seed used for backup, typically represented as 8x4 characters
public class BackupSeed {
    public static final int BACKUP_SEED_LENGTH = 20; // the seed is 20-bytes, that is 8x4 characters (MAJ & number without I, O, S, Z)

    private static final char[] seedArray = "0123456789ABCDEFGHJKLMNPQRTUVWXY".toCharArray();
    private static final byte[] seedInvArray = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            0,  1,  2,  3,  4,  5,  6,  7,  8,  9, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15, 16, 17,  1, 18, 19, 20, 21, 22,  0,
            23, 24, 25,  5, 26, 27, 28, 29, 30, 31,  2, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15, 16, 17,  1, 18, 19, 20, 21, 22,  0,
            23, 24, 25,  5, 26, 27, 28, 29, 30, 31,  2, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };

    private final byte[] backupSeedBytes;

    public byte[] getBackupSeedBytes() {
        return backupSeedBytes;
    }

    private BackupSeed(byte[] backupSeedBytes) throws Exception {
        if (backupSeedBytes.length != BACKUP_SEED_LENGTH) {
            throw new Exception("Bad backupSeedBytes length");
        }
        this.backupSeedBytes = backupSeedBytes;
    }


    public BackupSeed(String seedString) throws SeedTooLongException, SeedTooShortException {
        backupSeedBytes = new byte[BACKUP_SEED_LENGTH];
        int written = 0;
        for (char letter: seedString.toCharArray()) {
            byte val = seedInvArray[letter];
            if (val == -1) {
                continue;
            }
            if (written > (8*BACKUP_SEED_LENGTH - 5)) {
                throw new SeedTooLongException();
            }
            int byteOffset = written & 7;
            if (byteOffset < 4) {
                backupSeedBytes[written>>3] |= val << (3-byteOffset);
            } else {
                backupSeedBytes[written>>3] |= val >> (byteOffset-3);
                backupSeedBytes[(written>>3) + 1] |= val << (11-byteOffset);
            }
            written += 5;
        }
        if (written != (8*BACKUP_SEED_LENGTH)) {
            throw new SeedTooShortException();
        }
    }

    public static BackupSeed generate(PRNG prng) {
        try {
            return new BackupSeed(prng.bytes(BACKUP_SEED_LENGTH));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String toString() {
        char[] chars = new char[39];
        int read = 0;
        for (int i=0; i<39; i++) {
            if (i%5 == 4) {
                chars[i] = ' ';
            } else {
                int byteOffset = read & 7;
                int charVal;
                if (byteOffset < 4) {
                    charVal = (backupSeedBytes[read>>3] >> (3-byteOffset)) & 31;
                } else {
                    charVal = (backupSeedBytes[read>>3] << (byteOffset-3)) & 31;
                    charVal |= (backupSeedBytes[(read>>3) + 1] & 0xff) >> (11 - byteOffset);
                }
                chars[i] = seedArray[charVal];
                read += 5;
            }
        }
        return new String(chars);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BackupSeed)) {
            return false;
        }
        return Arrays.equals(backupSeedBytes, ((BackupSeed) o).backupSeedBytes);
    }

    public DerivedKeys deriveKeys() {
        byte[] fullSeedBytes = new byte[32];
        System.arraycopy(backupSeedBytes, 0, fullSeedBytes, 0, BACKUP_SEED_LENGTH);
        PRNG prng = Suite.getPRNG(PRNG.PRNG_HMAC_SHA256, new Seed(fullSeedBytes));
        UID backupKeyUid = new UID(prng);
        EncryptionEciesCurve25519KeyPair encryptionKeyPair = EncryptionEciesCurve25519KeyPair.generate(prng);
        MAC mac = Suite.getMAC(MAC.HMAC_SHA256);
        if (mac == null) {
            return null;
        }
        MACKey macKey = mac.generateKey(prng);
        return new DerivedKeys(backupKeyUid, encryptionKeyPair, macKey);
    }

    public static class DerivedKeys {
        public final UID backupKeyUid; // should never be used during backup: a bug fixed in Sept. 2023 may have changed the original value of this in DB
        public final EncryptionEciesCurve25519KeyPair encryptionKeyPair;
        public final MACKey macKey;

        public DerivedKeys(UID backupKeyUid, EncryptionEciesCurve25519KeyPair encryptionKeyPair, MACKey macKey) {
            this.backupKeyUid = backupKeyUid;
            this.encryptionKeyPair = encryptionKeyPair;
            this.macKey = macKey;
        }
    }

    public static class SeedTooShortException extends Exception {}

    public static class SeedTooLongException extends Exception {}
}
