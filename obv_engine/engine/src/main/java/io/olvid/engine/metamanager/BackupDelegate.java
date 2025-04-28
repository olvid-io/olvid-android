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

package io.olvid.engine.metamanager;

import io.olvid.engine.datatypes.UID;
import io.olvid.engine.engine.types.ObvBackupKeyInformation;
import io.olvid.engine.engine.types.identities.ObvIdentity;

public interface BackupDelegate {
    //    void generateNewBackupKey();
    //    int verifyBackupKey(String seedString);
    void stopLegacyBackups();
    void setAutoBackupEnabled(boolean enabled, boolean doNotInitiateBackupNow);
    void initiateBackup(boolean forExpert);

    void backupFailed(String tag, UID backupKeyUid, int version);
    void backupSuccess(String tag, UID backupKeyUid, int version, String backupContent);
    ObvBackupKeyInformation getBackupKeyInformation() throws Exception;
    void markBackupExported(UID backupKeyUid, int version);
    void markBackupUploaded(UID backupKeyUid, int version);
    void discardBackup(UID backupKeyUid, int version);
    int validateBackupSeed(String seedString, byte[] backupContent);
    ObvIdentity[] restoreOwnedIdentitiesFromBackup(String seedString, byte[] backupContent, String deviceDisplayName);
    void restoreContactsAndGroupsFromBackup(String seedString, byte[] backupContent, ObvIdentity[] restoredOwnedIdentities);
}
