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

import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.engine.types.ObvDeviceBackupForRestore;
import io.olvid.engine.engine.types.ObvProfileBackupsForRestore;

public interface BackupV2Delegate {
    String generateDeviceBackupSeed(String server) throws Exception; // throws an exception if an active device backup seed already exists
    String getCurrentDeviceBackupSeed() throws Exception;
    void deleteDeviceBackupSeed(BackupSeed backupSeed) throws Exception;
    boolean backupDeviceAndProfilesNow();
    ObvDeviceBackupForRestore fetchDeviceBackup(String server, BackupSeed backupSeed);
    ObvProfileBackupsForRestore fetchProfileBackups(String server, BackupSeed backupSeed);
    boolean deleteProfileBackupSnapshot(String server, BackupSeed backupSeed, UID backupThreadId, long version);
}
