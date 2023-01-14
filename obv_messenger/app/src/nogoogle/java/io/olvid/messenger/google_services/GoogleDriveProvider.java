/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.google_services;

import io.olvid.messenger.services.BackupCloudProviderService;

public class GoogleDriveProvider {
    public static void uploadBackup(String account, byte[] backupContent, BackupCloudProviderService.OnBackupsUploadCallback onBackupsUploadCallback) {
    }


    public static void listBackups(String account, BackupCloudProviderService.OnBackupsListCallback onBackupsListCallback) {
    }

    public static void downloadBackup(String account, String fileId, BackupCloudProviderService.OnBackupDownloadCallback onBackupDownloadCallback) {
    }

    public static void deleteBackup(String account, String fileId, BackupCloudProviderService.OnBackupDeleteCallback onBackupDeleteCallback) {
    }
}
