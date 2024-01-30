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

package io.olvid.messenger.databases.entity;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = Fyle.TABLE_NAME,
        indices = {
                @Index(value = {Fyle.SHA256}, unique = true),
        }
)

public class Fyle {
    public static final String TABLE_NAME = "fyle_table";

    public static final String FILE_PATH = "permanent_file_path";
    public static final String SHA256 = "sha256";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = FILE_PATH)
    @Nullable
    public String filePath; // relative to getNoBackupFilesDir()

    @ColumnInfo(name = SHA256)
    @Nullable
    public byte[] sha256;

    // default constructor required by Room, and used for complete files (when sending)
    public Fyle(@Nullable String filePath, @Nullable byte[] sha256) {
        this.filePath = filePath;
        this.sha256 = sha256;
    }

    // used when creating an "empty" Fyle for receiving an attachment
    @Ignore
    public Fyle(@NonNull byte[] sha256) {
        this.filePath = null;
        this.sha256 = sha256;
    }

    @Ignore
    public Fyle() {
        this.filePath = null;
        this.sha256 = null;
    }

    public void delete() {
        AppDatabase.getInstance().fyleDao().delete(this);
        if (filePath != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(App.absolutePathFromRelative(filePath)).delete();
        }
    }

    // oldPath should be an absolute path
    public void moveToFyleDirectory(String oldPath) throws Exception {
        String newPath = buildFylePath(sha256);
        File oldFile = new File(oldPath);
        File newFile = new File(App.absolutePathFromRelative(newPath));
        if (!oldFile.renameTo(newFile)) {
            // rename failed --> maybe on 2 different partitions
            // fallback to a copy.
            // if it fails, let the exception bubble up
            try (FileInputStream fileInputStream = new FileInputStream(oldFile); FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                byte[] buffer = new byte[262_144];
                int c;
                while ((c = fileInputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, c);
                }
            }

            //noinspection ResultOfMethodCallIgnored
            oldFile.delete();
        }
        filePath = newPath;
    }

    // if this method was to be changed, also update the PeriodicTasksScheduler.FolderCleanerWorker doWork method
    @NonNull
    public static String buildFylePath(byte[] sha256) {
        return AppSingleton.FYLE_DIRECTORY + File.separator + Logger.toHexString(sha256);
    }

    private static SizeAndSha256 computeSHA256FromInputStream(@NonNull InputStream is) {
        try {
            MessageDigest h = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[262_144];
            long fileSize = 0;
            int c;
            while ((c = is.read(buffer)) != -1) {
                h.update(buffer, 0, c);
                fileSize += c;
            }
            return new SizeAndSha256(fileSize, h.digest());
        } catch (Exception e) {
            return null;
        }

    }

    public static SizeAndSha256 computeSHA256FromFile(String file) {
        try (InputStream is = new FileInputStream(file)) {
            return computeSHA256FromInputStream(is);
        } catch (Exception e) {
            return null;
        }
    }

    public static class SizeAndSha256 {
        public final long fileSize;
        public final byte[] sha256;

        public SizeAndSha256(long fileSize, byte[] sha256) {
            this.fileSize = fileSize;
            this.sha256 = sha256;
        }
    }

    public boolean isComplete() {
        return filePath != null;
    }

    // region lock on Fyle
    private static final HashMap<String, ReentrantLock> fyleLocks = new HashMap<>();
    private static final Object hashMapLock = new Object();

    public static void acquireLock(@NonNull byte[] sha256) {
        String sha256String = Logger.toHexString(sha256);
        if (!fyleLocks.containsKey(sha256String)) {
            synchronized (hashMapLock) {
                if (!fyleLocks.containsKey(sha256String)) {
                    fyleLocks.put(sha256String, new ReentrantLock());
                }
            }
        }
        //noinspection ConstantConditions
        fyleLocks.get(sha256String).lock();
    }

    public static void releaseLock(@NonNull byte[] sha256) {
        String sha256String = Logger.toHexString(sha256);
        if (!fyleLocks.containsKey(sha256String)) {
            Logger.e("Trying to release a lock that does not exist!");
            return;
        }
        //noinspection ConstantConditions
        fyleLocks.get(sha256String).unlock();
    }
    // endregion

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonMetadata {
        String type;
        String fileName;
        byte[] sha256;

        public JsonMetadata(String type, String fileName, byte[] sha256) {
            this.type = type;
            this.fileName = fileName;
            this.sha256 = sha256;
        }

        public JsonMetadata() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @JsonProperty("file_name")
        public String getFileName() {
            return fileName;
        }

        @JsonProperty("file_name")
        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public byte[] getSha256() {
            return sha256;
        }

        public void setSha256(byte[] sha256) {
            this.sha256 = sha256;
        }
    }
}
