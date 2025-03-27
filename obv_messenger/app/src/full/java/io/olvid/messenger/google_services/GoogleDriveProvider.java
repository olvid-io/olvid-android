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

package io.olvid.messenger.google_services;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.services.BackupCloudProviderService;
import io.olvid.messenger.settings.SettingsActivity;

public class GoogleDriveProvider {
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String MIME_TYPE = "application/octet-stream";
    private static final String BACKUP_FOLDER_NAME = "olvidbackup";

    public static void uploadBackup(@NonNull String accountEmail, @NonNull byte[] backupContent, @NonNull BackupCloudProviderService.OnBackupsUploadCallback onBackupsUploadCallback) {
        App.runThread(() -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(App.getContext());
            if (account == null || !Objects.equals(account.getEmail(), accountEmail)) {
                onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED);
                return;
            }

            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(App.getContext(), Collections.singleton(DriveScopes.DRIVE_APPDATA));
                credential.setSelectedAccount(account.getAccount());

                Drive googleDriveService = new Drive.Builder(new NetHttpTransport(), new GsonFactory(), credential)
                        .setApplicationName(App.getContext().getString(R.string.app_name))
                        .build();

                String folderId;
                List<File> folderList = googleDriveService.files().list()
                        .setQ("mimeType = '" + FOLDER_MIME_TYPE + "' and name = '" + BACKUP_FOLDER_NAME + "'")
                        .setSpaces("appDataFolder")
                        .execute().getFiles();
                if (folderList.size() == 0) {
                    // we need to create the folder
                    File folderMetadata = new File()
                            .setName(BACKUP_FOLDER_NAME)
                            .setMimeType(FOLDER_MIME_TYPE)
                            .setParents(Collections.singletonList("appDataFolder"));

                    File folder = googleDriveService.files().create(folderMetadata)
                            .setFields("id")
                            .execute();
                    folderId = folder.getId();
                } else {
                    folderId = folderList.get(0).getId();
                    if (folderList.size() > 1) {
                        // there were more than one backup folders  --> delete all other backup folders (the delete is recursive)
                        for (int i = 1; i < folderList.size(); i++) {
                            googleDriveService.files().delete(folderList.get(i).getId()).execute();
                        }
                    }
                }

                String deviceUniqueId = SettingsActivity.getAutomaticBackupDeviceUniqueId();
                String fileName = deviceUniqueId + "_" + BackupCloudProviderService.BACKUP_FILE_NAME_MODEL_PART;

                List<File> fileList = googleDriveService.files().list()
                        .setQ("name = '" + fileName + "' and '" + folderId + "' in parents")
                        .setSpaces("appDataFolder")
                        .execute().getFiles();

                File uploadedFile;
                if (fileList.size() == 0) {
                    File fileMetadata = new File()
                            .setName(fileName)
                            .setParents(Collections.singletonList(folderId));

                    uploadedFile = googleDriveService.files().create(fileMetadata, new ByteArrayContent(MIME_TYPE, backupContent))
                            .setFields("id")
                            .execute();
                } else {
                    String fileId = fileList.get(0).getId();
                    // pick the first file, and update its content with the new content
                    File fileMetadata = new File()
                            .setName(fileName);

                    uploadedFile = googleDriveService.files()
                            .update(fileId, fileMetadata, new ByteArrayContent(MIME_TYPE, backupContent))
                            .execute();

                    if (fileList.size() > 1) {
                        // there were more than one backups for this device --> delete all other backups
                        for (int i = 1; i < fileList.size(); i++) {
                            googleDriveService.files().delete(fileList.get(i).getId()).execute();
                        }
                    }
                }

                if (uploadedFile.getId() != null) {
                    Logger.d("Successfully uploaded a backup to Google Drive");
                    // upload successful
                    onBackupsUploadCallback.onUploadSuccess();
                    return;
                }
            } catch (UserRecoverableAuthIOException e) {
                onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED);
                return;
            } catch (IOException e) {
                onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_NETWORK_ERROR);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
            onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_UNKNOWN);
        });
    }

    public static void listBackups(@NonNull String accountEmail, @NonNull BackupCloudProviderService.OnBackupsListCallback onBackupsListCallback) {
        App.runThread(() -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(App.getContext());
            if (account == null || !Objects.equals(account.getEmail(), accountEmail)) {
                onBackupsListCallback.onListFailure(BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED);
                return;
            }
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(App.getContext(), Collections.singleton(DriveScopes.DRIVE_APPDATA));
                credential.setSelectedAccount(account.getAccount());

                Drive googleDriveService = new Drive.Builder(new NetHttpTransport(), new GsonFactory(), credential)
                        .setApplicationName(App.getContext().getString(R.string.app_name))
                        .build();

                List<File> folderList = googleDriveService.files().list()
                        .setQ("mimeType = '" + FOLDER_MIME_TYPE + "' and name = '" + BACKUP_FOLDER_NAME + "'")
                        .setSpaces("appDataFolder")
                        .execute().getFiles();
                if (folderList.size() == 0) {
                    onBackupsListCallback.onListSuccess(new ArrayList<>());
                    return;
                }
                String folderId = folderList.get(0).getId();


                List<File> fileList = googleDriveService.files().list()
                        .setQ("'" + folderId + "' in parents")
                        .setFields("files(id, name, modifiedTime)")
                        .setSpaces("appDataFolder")
                        .execute().getFiles();

                List<BackupCloudProviderService.BackupItem> backupsList = new ArrayList<>();
                for (File file : fileList) {
                    String[] parts = file.getName().split("[|_]", 2);
                    if (parts.length == 2) {
                        backupsList.add(new BackupCloudProviderService.BackupItem(parts[1], file.getId(), file.getModifiedTime().getValue()));
                    }
                }
                Collections.sort(backupsList, Collections.reverseOrder());
                onBackupsListCallback.onListSuccess(backupsList);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
            onBackupsListCallback.onListFailure(BackupCloudProviderService.ERROR_UNKNOWN);
        });
    }

    public static void downloadBackup(@NonNull String accountEmail, @NonNull String fileId, @NonNull BackupCloudProviderService.OnBackupDownloadCallback onBackupDownloadCallback) {
        App.runThread(() -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(App.getContext());
            if (account == null || !Objects.equals(account.getEmail(), accountEmail)) {
                onBackupDownloadCallback.onDownloadFailure(BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED);
                return;
            }
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(App.getContext(), Collections.singleton(DriveScopes.DRIVE_APPDATA));
                credential.setSelectedAccount(account.getAccount());

                Drive googleDriveService = new Drive.Builder(new NetHttpTransport(), new GsonFactory(), credential)
                        .setApplicationName(App.getContext().getString(R.string.app_name))
                        .build();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                googleDriveService.files().get(fileId).executeMediaAndDownloadTo(baos);
                onBackupDownloadCallback.onDownloadSuccess(baos.toByteArray());
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
            onBackupDownloadCallback.onDownloadFailure(BackupCloudProviderService.ERROR_UNKNOWN);
        });
    }

    public static void deleteBackup(@NonNull String accountEmail, @NonNull String fileId, @NonNull BackupCloudProviderService.OnBackupDeleteCallback onBackupDeleteCallback) {
        App.runThread(() -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(App.getContext());
            if (account == null || !Objects.equals(account.getEmail(), accountEmail)) {
                onBackupDeleteCallback.onDeleteFailure(BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED);
                return;
            }
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(App.getContext(), Collections.singleton(DriveScopes.DRIVE_APPDATA));
                credential.setSelectedAccount(account.getAccount());

                Drive googleDriveService = new Drive.Builder(new NetHttpTransport(), new GsonFactory(), credential)
                        .setApplicationName(App.getContext().getString(R.string.app_name))
                        .build();

                googleDriveService.files().delete(fileId).execute();
                onBackupDeleteCallback.onDeleteSuccess();
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
            onBackupDeleteCallback.onDeleteFailure(BackupCloudProviderService.ERROR_UNKNOWN);
        });
    }
}
