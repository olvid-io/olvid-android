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

package io.olvid.messenger.services;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.ReactionRequest;
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest;
import io.olvid.messenger.databases.tasks.ApplyDiscussionRetentionPoliciesTask;
import io.olvid.messenger.databases.tasks.UpdateMessageImageResolutionsTask;
import io.olvid.messenger.settings.SettingsActivity;

public class PeriodicTasksScheduler {
    private static final String PERIODIC_TASKS_DEBUG_LOG_FILE_NAME = "periodic_tasks_executions.log";
    public static final long PERIODIC_TASKS_DEBUG_LOG_MAX_SIZE = 1_000_000L;

    public static final String DOWNLOAD_MESSAGES_WORK_NAME = "download_messages";
    public static final int DOWNLOAD_MESSAGES_INTERVAL_IN_SECONDS = 900; // this is the minimum allowed by Android

    public static final String VACUUM_DB_WORK_NAME = "vacuum_db";
    public static final int VACUUM_DB_INTERVAL_IN_HOURS = 11;

    public static final String FOLDER_CLEANER_WORK_NAME = "folder_cleaner";
    public static final int FOLDER_CLEANER_INTERVAL_IN_HOURS = 17;

    public static final String RETENTION_POLICY_WORK_NAME = "retention_policy";
    public static final int RETENTION_POLICY_INTERVAL_IN_HOURS = 1;

    public static final String REMOTE_DELETE_AND_EDIT_CLEANUP_WORK_NAME = "remote_delete";
    public static final int REMOTE_DELETE_AND_EDIT_CLEANUP_INTERVAL_IN_HOURS = 19;

    public static final String IMAGE_AND_VIDEO_RESOLUTION_WORK_NAME = "image_resolution";
    public static final int IMAGE_AND_VIDEO_RESOLUTION_INTERVAL_IN_HOURS = 2;

    public static void schedulePeriodicTasks(Context context) {
        try {
            WorkManager workManager = WorkManager.getInstance(context);

            PeriodicWorkRequest downloadMessagesWorkRequest =
                    new PeriodicWorkRequest.Builder(GetMessagesWorker.class, DOWNLOAD_MESSAGES_INTERVAL_IN_SECONDS, TimeUnit.SECONDS)
                            .setInitialDelay(DOWNLOAD_MESSAGES_INTERVAL_IN_SECONDS, TimeUnit.SECONDS)
                            .setConstraints(GetMessagesWorker.getConstraints())
                            .build();
            workManager.enqueueUniquePeriodicWork(DOWNLOAD_MESSAGES_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, downloadMessagesWorkRequest);


            PeriodicWorkRequest vacuumDbWorkRequest =
                    new PeriodicWorkRequest.Builder(VacuumDbWorker.class, VACUUM_DB_INTERVAL_IN_HOURS, TimeUnit.HOURS)
                            .setConstraints(VacuumDbWorker.getConstraints())
                            .build();
            workManager.enqueueUniquePeriodicWork(VACUUM_DB_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, vacuumDbWorkRequest);


            PeriodicWorkRequest folderCleanerWorkRequest =
                    new PeriodicWorkRequest.Builder(FolderCleanerWorker.class, FOLDER_CLEANER_INTERVAL_IN_HOURS, TimeUnit.HOURS)
                            .setConstraints(FolderCleanerWorker.getConstraints())
                            .build();
            workManager.enqueueUniquePeriodicWork(FOLDER_CLEANER_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, folderCleanerWorkRequest);


            PeriodicWorkRequest retentionPolicyWorkRequest =
                    new PeriodicWorkRequest.Builder(RetentionPolicyWorker.class, RETENTION_POLICY_INTERVAL_IN_HOURS, TimeUnit.HOURS)
                            .setConstraints(RetentionPolicyWorker.getConstraints())
                            .build();
            workManager.enqueueUniquePeriodicWork(RETENTION_POLICY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, retentionPolicyWorkRequest);


            PeriodicWorkRequest remoteDeleteAndEditCleanerWorkRequest =
                    new PeriodicWorkRequest.Builder(RemoteDeleteAndEditCleanerWorker.class, REMOTE_DELETE_AND_EDIT_CLEANUP_INTERVAL_IN_HOURS, TimeUnit.HOURS)
                            .setConstraints(RemoteDeleteAndEditCleanerWorker.getConstraints())
                            .build();
            workManager.enqueueUniquePeriodicWork(REMOTE_DELETE_AND_EDIT_CLEANUP_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, remoteDeleteAndEditCleanerWorkRequest);

            PeriodicWorkRequest imageAndVideoResolutionWorkRequest =
                    new PeriodicWorkRequest.Builder(ImageAndVideoResolutionWorker.class, IMAGE_AND_VIDEO_RESOLUTION_INTERVAL_IN_HOURS, TimeUnit.HOURS)
                            .setConstraints(ImageAndVideoResolutionWorker.getConstraints())
                            .build();
            workManager.enqueueUniquePeriodicWork(IMAGE_AND_VIDEO_RESOLUTION_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, imageAndVideoResolutionWorkRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void resetAllPeriodicTasksFollowingAnUpdate(Context context) {
        // after an app update, we cancel everything and reschedule, in case some settings were changed
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.cancelUniqueWork(DOWNLOAD_MESSAGES_WORK_NAME);
            workManager.cancelUniqueWork(VACUUM_DB_WORK_NAME);
            workManager.cancelUniqueWork(FOLDER_CLEANER_WORK_NAME);
            workManager.cancelUniqueWork(RETENTION_POLICY_WORK_NAME);
            workManager.cancelUniqueWork(REMOTE_DELETE_AND_EDIT_CLEANUP_WORK_NAME);
            workManager.cancelUniqueWork(IMAGE_AND_VIDEO_RESOLUTION_WORK_NAME);

            schedulePeriodicTasks(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void logPeriodicTaskRun(Context applicationContext, Class<?> clazz) {
        if (!BuildConfig.DEBUG && !SettingsActivity.useDebugLogLevel()) {
            return;
        }
        File logFile = new File(applicationContext.getNoBackupFilesDir(), PERIODIC_TASKS_DEBUG_LOG_FILE_NAME);
        boolean append = true;
        if (logFile.exists() && logFile.length() > PERIODIC_TASKS_DEBUG_LOG_MAX_SIZE) {
            append = false;
        }
        try (FileWriter fileWriter = new FileWriter(logFile, append)) {
            String logLine = Calendar.getInstance().getTime().toString() + " - executing task " + clazz.getSimpleName() + "\n";
            fileWriter.append(logLine);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class GetMessagesWorker extends Worker {
        public GetMessagesWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        private static Constraints getConstraints() {
            return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        }


        @NonNull
        @Override
        public Result doWork() {
            logPeriodicTaskRun(getApplicationContext(), getClass());
            if (System.currentTimeMillis() - App.appStartTimestamp > 5_000) {
                // only poll if app was started more than 5 seconds ago (avoids double polling when this task starts the app)
                for (OwnedIdentity ownedIdentity : AppDatabase.getInstance().ownedIdentityDao().getAll()) {
                    AppSingleton.getEngine().downloadMessages(ownedIdentity.bytesOwnedIdentity);
                }
            }
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                // nothing to do
            }
            return Result.success();
        }
    }

    public static class VacuumDbWorker extends Worker {
        public VacuumDbWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        private static Constraints getConstraints() {
            Constraints.Builder builder = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setRequiresDeviceIdle(true);
            }
            return builder.build();
        }


        @NonNull
        @Override
        public Result doWork() {
            try {
                logPeriodicTaskRun(getApplicationContext(), getClass());
                AppDatabase.getInstance().rawDao().executeRawQuery(new SimpleSQLiteQuery("VACUUM"));
                AppSingleton.getEngine().vacuumDatabase();
            } catch (Exception e) {
                e.printStackTrace();
                return Result.failure();
            }
            return Result.success();
        }
    }

    public static class FolderCleanerWorker extends Worker {
        public FolderCleanerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        private static Constraints getConstraints() {
            Constraints.Builder builder = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setRequiresDeviceIdle(true);
            }
            return builder.build();
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                logPeriodicTaskRun(getApplicationContext(), getClass());
                AppDatabase db = AppDatabase.getInstance();
                {
                    // Check for files in the Fyle dir not in the database
                    Pattern sha256Pattern = Pattern.compile("^[0-9A-F]{64}$");
                    List<byte[]> fyleSha256InDb = db.fyleDao().getAllSha256();
                    Set<String> hexSha256InDb = new HashSet<>(fyleSha256InDb.size());
                    for (byte[] sha256 : fyleSha256InDb) {
                        hexSha256InDb.add(Logger.toHexString(sha256));
                    }

                    File fylesDir = new File(App.absolutePathFromRelative(AppSingleton.FYLE_DIRECTORY));
                    if (fylesDir.isDirectory()) {
                        File[] list = fylesDir.listFiles();
                        if (list != null) {
                            for (File fyleFile : list) {
                                String name = fyleFile.getName();
                                if (!hexSha256InDb.contains(fyleFile.getName())) {
                                    Matcher m = sha256Pattern.matcher(name);
                                    if (!m.find()) {
                                        // file does not have the right Fyle name format --> delete it
                                        Logger.i("Deleting stray file in the fyles folder: " + name);
                                        //noinspection ResultOfMethodCallIgnored
                                        fyleFile.delete();
                                    } else {
                                        // Found file not in Fyle db
                                        byte[] sha256 = Logger.fromHexString(name);
                                        try {
                                            Fyle.acquireLock(sha256);
                                            // check again the Fyle is not in the database, with proper locking
                                            Fyle fyle = db.fyleDao().getBySha256(sha256);
                                            if (fyle == null) {
                                                Logger.i("Deleting stray file in the fyles folder: " + name);
                                                //noinspection ResultOfMethodCallIgnored
                                                fyleFile.delete();
                                            }
                                        } finally {
                                            Fyle.releaseLock(sha256);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                {
                    // check for stray discussion background images
                    List<String> discussionBackgroundFilePaths = db.discussionCustomizationDao().getAllBackgroundImageFilePaths();
                    Set<String> discussionBackgroundFileNames = new HashSet<>(discussionBackgroundFilePaths.size());
                    for (String path : discussionBackgroundFilePaths) {
                        discussionBackgroundFileNames.add(path.substring(path.lastIndexOf(File.separatorChar) + 1));
                    }

                    File backgroundsDir = new File(App.absolutePathFromRelative(AppSingleton.DISCUSSION_BACKGROUNDS_DIRECTORY));
                    if (backgroundsDir.isDirectory()) {
                        File[] list = backgroundsDir.listFiles();
                        if (list != null) {
                            for (File backgroundFile : list) {
                                if (!discussionBackgroundFileNames.contains(backgroundFile.getName())) {
                                    // Found file not in discussion backgrounds db
                                    Logger.i("Deleting stray file in the discussion backgrounds folder: " + backgroundFile.getName());
                                    //noinspection ResultOfMethodCallIgnored
                                    backgroundFile.delete();
                                }
                            }
                        }
                    }
                }


                {
                    // check for stray customPhotoUrl for Contact, Group, or locked Discussion
                    Set<String> customPhotoUrlFileNames = new HashSet<>();
                    customPhotoUrlFileNames.addAll(db.contactDao().getAllCustomPhotoUrls());
                    customPhotoUrlFileNames.addAll(db.groupDao().getAllCustomPhotoUrls());
                    customPhotoUrlFileNames.addAll(db.discussionDao().getAllLockedDiscussionPhotoUrls());

                    File customPhotosDir = new File(App.absolutePathFromRelative(AppSingleton.CUSTOM_PHOTOS_DIRECTORY));
                    if (customPhotosDir.isDirectory()) {
                        File[] list = customPhotosDir.listFiles();
                        if (list != null) {
                            for (File customPhotosFile : list) {
                                if (!customPhotoUrlFileNames.contains(AppSingleton.CUSTOM_PHOTOS_DIRECTORY + File.separator + customPhotosFile.getName())) {
                                    // Found file not in discussion backgrounds db
                                    Logger.i("Deleting stray file in the custom photos folder: " + customPhotosFile.getName());
                                    //noinspection ResultOfMethodCallIgnored
                                    customPhotosFile.delete();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return Result.failure();
            }
            return Result.success();
        }
    }

    public static class RetentionPolicyWorker extends Worker {
        public RetentionPolicyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        private static Constraints getConstraints() {
            Constraints.Builder builder = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setRequiresDeviceIdle(true);
            }
            return builder.build();
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                logPeriodicTaskRun(getApplicationContext(), getClass());
                new ApplyDiscussionRetentionPoliciesTask(null).run();
            } catch (Exception e) {
                e.printStackTrace();
                return Result.failure();
            }
            return Result.success();
        }
    }

    public static class RemoteDeleteAndEditCleanerWorker extends Worker {
        public RemoteDeleteAndEditCleanerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        private static Constraints getConstraints() {
            Constraints.Builder builder = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setRequiresDeviceIdle(true);
            }
            return builder.build();
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                logPeriodicTaskRun(getApplicationContext(), getClass());
                // delete all requests that have a server timestamp more than TTL in the past
                AppDatabase.getInstance().remoteDeleteAndEditRequestDao().deleteOlderThan(System.currentTimeMillis() - RemoteDeleteAndEditRequest.TTL);
                AppDatabase.getInstance().reactionRequestDao().deleteOlderThan(System.currentTimeMillis() - ReactionRequest.TTL);
            } catch (Exception e) {
                e.printStackTrace();
                return Result.failure();
            }
            return Result.success();
        }

    }

    public static class ImageAndVideoResolutionWorker extends Worker {
        public ImageAndVideoResolutionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        private static Constraints getConstraints() {
            Constraints.Builder builder = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true);
            return builder.build();
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                logPeriodicTaskRun(getApplicationContext(), getClass());

                AppDatabase db = AppDatabase.getInstance();
                for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus: db.fyleMessageJoinWithStatusDao().getCompleteFyleAndStatusWithoutResolution()) {
                    if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(fyleAndStatus.fyleMessageJoinWithStatus.mimeType, fyleAndStatus.fyleMessageJoinWithStatus.fileName))) {
                        String imageResolution = PreviewUtils.getResolutionString(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus);
                        if (imageResolution != null) {
                            fyleAndStatus.fyleMessageJoinWithStatus.imageResolution = imageResolution;
                            db.fyleMessageJoinWithStatusDao().updateImageResolution(fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyleMessageJoinWithStatus.fyleId, fyleAndStatus.fyleMessageJoinWithStatus.imageResolution);
                            new UpdateMessageImageResolutionsTask(fyleAndStatus.fyleMessageJoinWithStatus.messageId).run();
                        }
                    } else {
                        fyleAndStatus.fyleMessageJoinWithStatus.imageResolution = "";
                        db.fyleMessageJoinWithStatusDao().updateImageResolution(fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyleMessageJoinWithStatus.fyleId, fyleAndStatus.fyleMessageJoinWithStatus.imageResolution);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return Result.failure();
            }
            return Result.success();
        }

    }

}
