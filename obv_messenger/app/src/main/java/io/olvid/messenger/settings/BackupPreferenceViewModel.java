/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.settings;

import androidx.lifecycle.ViewModel;

import io.olvid.engine.engine.types.ObvBackupKeyInformation;

public class BackupPreferenceViewModel extends ViewModel {
    private ObvBackupKeyInformation backupKeyInformation;
    private byte[] exportBackupContent;
    private int exportBackupVersion;
    private byte[] exportBackupKeyUid;
    private boolean googleApisInstalled;

    public byte[] getExportBackupContent() {
        return exportBackupContent;
    }

    public void setExportBackupContent(byte[] backupContent) {
        this.exportBackupContent = backupContent;
    }

    public int getExportBackupVersion() {
        return exportBackupVersion;
    }

    public void setExportBackupVersion(int exportBackupVersion) {
        this.exportBackupVersion = exportBackupVersion;
    }

    public byte[] getExportBackupKeyUid() {
        return exportBackupKeyUid;
    }

    public void setExportBackupKeyUid(byte[] exportBackupKeyUid) {
        this.exportBackupKeyUid = exportBackupKeyUid;
    }

    public ObvBackupKeyInformation getBackupKeyInformation() {
        return backupKeyInformation;
    }

    public void setBackupKeyInformation(ObvBackupKeyInformation backupKeyInformation) {
        this.backupKeyInformation = backupKeyInformation;
    }

    public boolean isGoogleApisInstalled() {
        return googleApisInstalled;
    }

    public void setGoogleApisInstalled(boolean googleApisInstalled) {
        this.googleApisInstalled = googleApisInstalled;
    }
}
