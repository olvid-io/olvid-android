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

package io.olvid.messenger.services;



import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;
import com.thegrizzlylabs.sardineandroid.impl.SardineException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.google_services.GoogleDriveProvider;
import io.olvid.messenger.settings.SettingsActivity;

public class BackupCloudProviderService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloudProviderConfiguration {
        public static final String PROVIDER_GOOGLE_DRIVE = "google";
        public static final String PROVIDER_WEBDAV = "webdav";

        public String provider;
        public String account;
        public String serverUrl;
        public String password;

        @SuppressWarnings("unused")
        public CloudProviderConfiguration() { }

        @JsonIgnore
        private CloudProviderConfiguration(String provider, String account, String serverUrl, String password) {
            this.provider = provider;
            this.account = account;
            this.serverUrl = serverUrl;
            this.password = password;
        }

        @JsonIgnore
        public static CloudProviderConfiguration buildGoogleDrive(String email) {
            return new CloudProviderConfiguration(PROVIDER_GOOGLE_DRIVE, email, null, null);
        }

        @JsonIgnore
        public static CloudProviderConfiguration buildWebDAV(String serverUrl, String login, String password) {
            return new CloudProviderConfiguration(PROVIDER_WEBDAV, login, serverUrl, password);
        }

        @JsonIgnore
        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof CloudProviderConfiguration)) {
                return false;
            }
            CloudProviderConfiguration other = (CloudProviderConfiguration) obj;
            if (!Objects.equals(provider, other.provider)) {
                return false;
            }
            switch (provider) {
                case PROVIDER_GOOGLE_DRIVE:
                    return Objects.equals(account, other.account);
                case PROVIDER_WEBDAV:
                    return Objects.equals(serverUrl, other.serverUrl) && Objects.equals(account, other.account) && Objects.equals(password, other.password);
                default:
                    return false;
            }
        }
    }


    public static class BackupItem implements Comparable<BackupItem> {
        public final String deviceName;
        public final String fileId;
        public final long timestamp;

        public BackupItem(String deviceName, String fileId, long timestamp) {
            this.deviceName = deviceName;
            this.fileId = fileId;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(@NonNull BackupItem other) {
            if (timestamp < other.timestamp) {
                return -1;
            } else if (timestamp > other.timestamp) {
                return 1;
            }
            return 0;
        }
    }


    public static final String BACKUP_FILE_NAME_MODEL_PART =  Build.BRAND + " " + Build.MODEL;

    public static final int ERROR_UNKNOWN = 1;
    public static final int ERROR_SIGN_IN_REQUIRED = 2;
    public static final int ERROR_BAD_CONFIGURATION = 3;
    // errors during webdav configuration validation
    public static final int ERROR_NETWORK_ERROR = 4;
    public static final int ERROR_AUTHENTICATION_ERROR = 5;
    public static final int ERROR_READ_ONLY = 6;

    public static final int ERROR_TEN_RETRIES_FAILED = 10;


    public static void uploadBackup(@NonNull CloudProviderConfiguration configuration, @NonNull byte[] backupContent, @NonNull OnBackupsUploadCallback onBackupsUploadCallback) {
        switch (configuration.provider) {
            case CloudProviderConfiguration.PROVIDER_WEBDAV:
                WebdavProvider.uploadBackup(configuration.serverUrl, configuration.account, configuration.password, backupContent, onBackupsUploadCallback);
                break;
            case CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                if (BuildConfig.USE_GOOGLE_LIBS) {
                    GoogleDriveProvider.uploadBackup(configuration.account, backupContent, onBackupsUploadCallback);
                } else {
                    onBackupsUploadCallback.onUploadFailure(ERROR_BAD_CONFIGURATION);
                }
                break;
            default:
                onBackupsUploadCallback.onUploadFailure(ERROR_BAD_CONFIGURATION);
                break;
        }
    }

    public interface OnBackupsUploadCallback {
        void onUploadSuccess();
        void onUploadFailure(int error);
    }


    public static void listBackups(@NonNull CloudProviderConfiguration configuration, @NonNull OnBackupsListCallback onBackupsListCallback) {
        switch (configuration.provider) {
            case CloudProviderConfiguration.PROVIDER_WEBDAV:
                WebdavProvider.listBackups(configuration.serverUrl, configuration.account, configuration.password, onBackupsListCallback);
                break;
            case CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                if (BuildConfig.USE_GOOGLE_LIBS) {
                    GoogleDriveProvider.listBackups(configuration.account, onBackupsListCallback);
                } else {
                    onBackupsListCallback.onListFailure(ERROR_BAD_CONFIGURATION);
                }
                break;
            default:
                onBackupsListCallback.onListFailure(ERROR_BAD_CONFIGURATION);
                break;
        }
    }

    public interface OnBackupsListCallback {
        void onListSuccess(List<BackupCloudProviderService.BackupItem> backupTimestampAndNames);
        void onListFailure(int error);
    }



    public static void downloadBackup(@NonNull CloudProviderConfiguration configuration, @NonNull BackupItem backupItem, @NonNull OnBackupDownloadCallback onBackupDownloadCallback) {
        switch (configuration.provider) {
            case CloudProviderConfiguration.PROVIDER_WEBDAV:
                WebdavProvider.downloadBackup(configuration.serverUrl, configuration.account, configuration.password, backupItem.fileId, onBackupDownloadCallback);
                break;
            case CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                if (BuildConfig.USE_GOOGLE_LIBS) {
                    GoogleDriveProvider.downloadBackup(configuration.account, backupItem.fileId, onBackupDownloadCallback);
                } else {
                    onBackupDownloadCallback.onDownloadFailure(ERROR_BAD_CONFIGURATION);
                }
                break;
            default:
                onBackupDownloadCallback.onDownloadFailure(ERROR_BAD_CONFIGURATION);
                break;
        }
    }

    public interface OnBackupDownloadCallback {
        void onDownloadSuccess(byte[] backupContent);
        void onDownloadFailure(int error);
    }



    public static void deleteBackup(@NonNull CloudProviderConfiguration configuration, @NonNull BackupItem backupItem, @NonNull OnBackupDeleteCallback onBackupDeleteCallback) {
        switch (configuration.provider) {
            case CloudProviderConfiguration.PROVIDER_WEBDAV:
                WebdavProvider.deleteBackup(configuration.serverUrl, configuration.account, configuration.password, backupItem.fileId, onBackupDeleteCallback);
                break;
            case CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                if (BuildConfig.USE_GOOGLE_LIBS) {
                    GoogleDriveProvider.deleteBackup(configuration.account, backupItem.fileId, onBackupDeleteCallback);
                } else {
                    onBackupDeleteCallback.onDeleteFailure(ERROR_BAD_CONFIGURATION);
                }
                break;
            default:
                onBackupDeleteCallback.onDeleteFailure(ERROR_BAD_CONFIGURATION);
                break;
        }
    }

    public interface OnBackupDeleteCallback {
        void onDeleteSuccess();
        void onDeleteFailure(int error);
    }

    public static void validateConfiguration(@NonNull CloudProviderConfiguration configuration, boolean validateWriteAccess, @NonNull OnValidateCallback onValidateCallback) {
        switch (configuration.provider) {
            case CloudProviderConfiguration.PROVIDER_WEBDAV:
                WebdavProvider.validateConfiguration(configuration.serverUrl, configuration.account, configuration.password, validateWriteAccess, onValidateCallback);
                break;
            case CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                if (BuildConfig.USE_GOOGLE_LIBS) {
                    onValidateCallback.onValidateSuccess();
                } else {
                    onValidateCallback.onValidateFailure(ERROR_BAD_CONFIGURATION);
                }
                break;
            default:
                onValidateCallback.onValidateFailure(ERROR_BAD_CONFIGURATION);
                break;
        }
    }

    public interface OnValidateCallback {
        void onValidateSuccess();
        void onValidateFailure(int error);
    }

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final long BASE_RESCHEDULING_TIME = 10_000L;
    private static CloudProviderConfiguration rescheduledConfiguration = null;
    private static byte[] rescheduledBackupContent = null;
    private static OnBackupsUploadCallback rescheduledBackupUploadCallback = null;
    private static int rescheduledFailedUploadCount = 0;

    public static void rescheduleBackupUpload(@NonNull CloudProviderConfiguration configuration, @NonNull byte[] backupContent, @NonNull OnBackupsUploadCallback onBackupsUploadCallback) {
        // only reset the fail counter if the backup is the same
        if (!Objects.equals(configuration, rescheduledConfiguration) || !Arrays.equals(backupContent, rescheduledBackupContent)) {
            rescheduledConfiguration = configuration;
            rescheduledBackupContent = backupContent;
            rescheduledFailedUploadCount = 0;
        }
        rescheduledBackupUploadCallback = onBackupsUploadCallback;

        long delay = (long) ((BASE_RESCHEDULING_TIME << rescheduledFailedUploadCount) * (1 + new Random().nextFloat()));
        scheduler.schedule(() -> retryBackupUpload(true), delay, TimeUnit.MILLISECONDS);
    }

    private static void retryBackupUpload(boolean incrementFailedAttempts) {
        if (rescheduledConfiguration != null && rescheduledBackupContent != null && rescheduledBackupUploadCallback != null) {
            // if the auto backup configuration changed, abort
            BackupCloudProviderService.CloudProviderConfiguration configuration = SettingsActivity.getAutomaticBackupConfiguration();
            if (!Objects.equals(configuration, rescheduledConfiguration)) {
                rescheduledConfiguration = null;
                rescheduledBackupContent = null;
                rescheduledBackupUploadCallback = null;
                return;
            }

            uploadBackup(rescheduledConfiguration, rescheduledBackupContent, new OnBackupsUploadCallback() {
                final OnBackupsUploadCallback wrappedCallback = rescheduledBackupUploadCallback;

                @Override
                public void onUploadSuccess() {
                    rescheduledConfiguration = null;
                    rescheduledBackupContent = null;
                    rescheduledBackupUploadCallback = null;
                    rescheduledFailedUploadCount = 0;

                    wrappedCallback.onUploadSuccess();
                }

                @Override
                public void onUploadFailure(int error) {
                    // check that the callback did not change during the upload try
                    if (incrementFailedAttempts && wrappedCallback == rescheduledBackupUploadCallback) {
                        rescheduledFailedUploadCount++;

                        if (rescheduledFailedUploadCount > 10) {
                            wrappedCallback.onUploadFailure(ERROR_TEN_RETRIES_FAILED);
                            return;
                        }
                    }

                    wrappedCallback.onUploadFailure(error);
                }
            });
        }
    }

    public static void networkAvailable() {
        // we do not increment the number of failed attempts when retrying on network available
        // --> this way, we are sure to have 10 "timed" attempts
        retryBackupUpload(false);
    }
}


class WebdavProvider {
    private static final String FILE_NAME_FOR_WRITE_ACCESS_TEST = ".olvidbackup_validation_file";
    private static final String BACKUP_ALT_NAME_SUFFIX = " (backup)";
    private static final String BACKUP_FILE_EXTENSION = ".olvidbackup";


    static void validateConfiguration(@NonNull String serverUrl, String username, String password, boolean validateWriteAccess, @NonNull BackupCloudProviderService.OnValidateCallback onValidateCallback) {
        App.runThread(() -> {
            boolean readSuccess = false;
            try {
                Sardine sardine = new OkHttpSardine(AppSingleton.getSslSocketFactory());

                if (username != null && username.length() > 0 && password != null && password.length() > 0) {
                    sardine.setCredentials(username, password);
                }

                List<DavResource> resourceList = sardine.list(serverUrl, 0);

                if (resourceList.size() != 1 || !resourceList.get(0).isDirectory()) {
                    onValidateCallback.onValidateFailure(BackupCloudProviderService.ERROR_BAD_CONFIGURATION);
                    return;
                }

                readSuccess = true;

                if (validateWriteAccess) {
                    String url = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + FILE_NAME_FOR_WRITE_ACCESS_TEST;
                    sardine.put(url, "test".getBytes(StandardCharsets.UTF_8));
                    sardine.delete(url);
                }
                onValidateCallback.onValidateSuccess();
            } catch (SardineException e) {
                e.printStackTrace();
                switch (e.getStatusCode()) {
                    case 401:
                    case 403:
                    case 404:
                        if (readSuccess) {
                            onValidateCallback.onValidateFailure(BackupCloudProviderService.ERROR_READ_ONLY);
                        } else {
                            onValidateCallback.onValidateFailure(BackupCloudProviderService.ERROR_AUTHENTICATION_ERROR);
                        }
                        break;
                    default:
                        onValidateCallback.onValidateFailure(BackupCloudProviderService.ERROR_BAD_CONFIGURATION);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                onValidateCallback.onValidateFailure(BackupCloudProviderService.ERROR_NETWORK_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                onValidateCallback.onValidateFailure(BackupCloudProviderService.ERROR_UNKNOWN);
            }
        });
    }

    static void uploadBackup(String serverUrl, String username, String password, byte[] backupContent, BackupCloudProviderService.OnBackupsUploadCallback onBackupsUploadCallback) {
        App.runThread(() -> {
            try {
                Sardine sardine = new OkHttpSardine(AppSingleton.getSslSocketFactory());

                if (username != null && username.length() > 0 && password != null && password.length() > 0) {
                    sardine.setCredentials(username, password);
                }

                String deviceUniqueId = SettingsActivity.getAutomaticBackupDeviceUniqueId();
                String fileName = deviceUniqueId + "_" + BackupCloudProviderService.BACKUP_FILE_NAME_MODEL_PART + BACKUP_FILE_EXTENSION;
                //noinspection DuplicateExpressions
                String url = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());

                if (sardine.exists(url)) {
                    // a file already exists with the name, do a "rotation" instead of brutally overwriting it
                    String fileNameAlt = fileName.substring(0, fileName.length() - BACKUP_FILE_EXTENSION.length()) + BACKUP_ALT_NAME_SUFFIX + BACKUP_FILE_EXTENSION;
                    //noinspection DuplicateExpressions
                    String urlAlt = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + URLEncoder.encode(fileNameAlt, StandardCharsets.UTF_8.name());
                    sardine.move(url, urlAlt, true);
                }

                sardine.put(url, backupContent);

                onBackupsUploadCallback.onUploadSuccess();
            } catch (SardineException e) {
                switch (e.getStatusCode()) {
                    case 401:
                    case 403:
                    case 404:
                        onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_AUTHENTICATION_ERROR);
                        break;
                    default:
                        onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_UNKNOWN);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_NETWORK_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                onBackupsUploadCallback.onUploadFailure(BackupCloudProviderService.ERROR_UNKNOWN);
            }
        });
    }


    static void listBackups(String serverUrl, String username, String password, BackupCloudProviderService.OnBackupsListCallback onBackupsListCallback) {
        App.runThread(() -> {
            try {
                Sardine sardine = new OkHttpSardine(AppSingleton.getSslSocketFactory());

                if (username != null && username.length() > 0 && password != null && password.length() > 0) {
                    sardine.setCredentials(username, password);
                }

                List<BackupCloudProviderService.BackupItem> backupItems = new ArrayList<>();

                List<DavResource> resourceList = sardine.list(serverUrl, 1);
                for (DavResource davResource : resourceList) {
                    if (davResource.getStatusCode() != 200 || davResource.isDirectory()) {
                        continue;
                    }

                    String name = davResource.getName();
                    if (!name.endsWith(BACKUP_FILE_EXTENSION)) {
                        continue;
                    }
                    long timestamp = davResource.getModified().getTime();
                    String[] parts = URLDecoder.decode(name.substring(0, name.length() - BACKUP_FILE_EXTENSION.length()), StandardCharsets.UTF_8.name()).split("[|_]", 2);
                    if (parts.length == 2) {
                        backupItems.add(new BackupCloudProviderService.BackupItem(parts[1], name, timestamp));
                    }
                }

                Collections.sort(backupItems, Collections.reverseOrder());
                onBackupsListCallback.onListSuccess(backupItems);
            } catch (SardineException e) {
                switch (e.getStatusCode()) {
                    case 401:
                    case 403:
                    case 404:
                        onBackupsListCallback.onListFailure(BackupCloudProviderService.ERROR_AUTHENTICATION_ERROR);
                        break;
                    default:
                        onBackupsListCallback.onListFailure(BackupCloudProviderService.ERROR_BAD_CONFIGURATION);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                onBackupsListCallback.onListFailure(BackupCloudProviderService.ERROR_NETWORK_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                onBackupsListCallback.onListFailure(BackupCloudProviderService.ERROR_UNKNOWN);
            }
        });
    }

    static void downloadBackup(String serverUrl, String username, String password, String fileId, BackupCloudProviderService.OnBackupDownloadCallback onBackupDownloadCallback) {
        App.runThread(() -> {
            try {
                Sardine sardine = new OkHttpSardine(AppSingleton.getSslSocketFactory());

                if (username != null && username.length() > 0 && password != null && password.length() > 0) {
                    sardine.setCredentials(username, password);
                }

                String url = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + URLEncoder.encode(fileId, StandardCharsets.UTF_8.name());

                try (InputStream is = sardine.get(url)) {
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int c;
                        while ((c = is.read(buffer)) > 0) {
                            baos.write(buffer, 0, c);
                        }

                        onBackupDownloadCallback.onDownloadSuccess(baos.toByteArray());
                    }
                }
            } catch (SardineException e) {
                switch (e.getStatusCode()) {
                    case 401:
                    case 403:
                    case 404:
                        onBackupDownloadCallback.onDownloadFailure(BackupCloudProviderService.ERROR_AUTHENTICATION_ERROR);
                        break;
                    default:
                        onBackupDownloadCallback.onDownloadFailure(BackupCloudProviderService.ERROR_UNKNOWN);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                onBackupDownloadCallback.onDownloadFailure(BackupCloudProviderService.ERROR_NETWORK_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                onBackupDownloadCallback.onDownloadFailure(BackupCloudProviderService.ERROR_UNKNOWN);
            }
        });
    }

    static void deleteBackup(String serverUrl, String username, String password, String fileId, BackupCloudProviderService.OnBackupDeleteCallback onBackupDeleteCallback) {
        App.runThread(() -> {
            try {
                Sardine sardine = new OkHttpSardine(AppSingleton.getSslSocketFactory());

                if (username != null && username.length() > 0 && password != null && password.length() > 0) {
                    sardine.setCredentials(username, password);
                }

                String url = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + URLEncoder.encode(fileId, StandardCharsets.UTF_8.name());
                sardine.delete(url);

                onBackupDeleteCallback.onDeleteSuccess();
            } catch (SardineException e) {
                switch (e.getStatusCode()) {
                    case 401:
                    case 403:
                    case 404:
                        onBackupDeleteCallback.onDeleteFailure(BackupCloudProviderService.ERROR_AUTHENTICATION_ERROR);
                        break;
                    default:
                        onBackupDeleteCallback.onDeleteFailure(BackupCloudProviderService.ERROR_UNKNOWN);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                onBackupDeleteCallback.onDeleteFailure(BackupCloudProviderService.ERROR_NETWORK_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                onBackupDeleteCallback.onDeleteFailure(BackupCloudProviderService.ERROR_UNKNOWN);
            }
        });
    }
}