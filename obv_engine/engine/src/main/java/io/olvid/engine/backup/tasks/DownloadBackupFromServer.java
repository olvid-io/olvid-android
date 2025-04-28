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

package io.olvid.engine.backup.tasks;


import io.olvid.engine.datatypes.ServerMethodForS3;

class DownloadBackupFromServer extends ServerMethodForS3 {
    private final String backupDownloadUrl;
    private byte[] encryptedBackup;

    public DownloadBackupFromServer(String backupDownloadUrl) {
        this.backupDownloadUrl = backupDownloadUrl;
    }

    public byte[] getEncryptedBackup() {
        return encryptedBackup;
    }




    @Override
    protected String getUrl() {
        return backupDownloadUrl;
    }

    @Override
    protected byte[] getDataToSend() {
        return new byte[0];
    }

    @Override
    protected void handleReceivedData(byte[] receivedData) {
        this.encryptedBackup = receivedData;
    }

    @Override
    protected String getMethod() {
        return METHOD_GET;
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return false;
    }

    public boolean execute() {
        return super.execute(false) == OK;
    }
}
