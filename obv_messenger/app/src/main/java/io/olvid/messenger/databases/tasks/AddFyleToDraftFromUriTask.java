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

package io.olvid.messenger.databases.tasks;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.FyleProgressSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.JpegUtils;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.services.AvailableSpaceHelper;
import io.olvid.messenger.settings.SettingsActivity;

public class AddFyleToDraftFromUriTask implements Runnable {


    @Nullable
    private final Uri uri;
    @Nullable
    private File localFile; // always a real File, with absolute path

    private final long discussionId;
    @Nullable
    private final String mimeType;
    @Nullable
    private final String fileName;
    @Nullable
    private final byte[] webclientProvidedSha256;

    public AddFyleToDraftFromUriTask(@NonNull Uri uri, long discussionId) {
        this.uri = uri;
        this.localFile = null;
        this.discussionId = discussionId;
        this.mimeType = null;
        this.fileName = null;
        this.webclientProvidedSha256 = null;
    }

    public AddFyleToDraftFromUriTask(@NonNull Uri uri, @Nullable String fileName, @Nullable String mimeType, long discussionId) {
        this.uri = uri;
        this.localFile = null;
        this.discussionId = discussionId;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.webclientProvidedSha256 = null;
    }

    public AddFyleToDraftFromUriTask(@NonNull Uri uri, @NonNull File localFile, long discussionId) {
        this.uri = uri;
        this.localFile = localFile;
        this.discussionId = discussionId;
        this.mimeType = null;
        this.fileName = null;
        this.webclientProvidedSha256 = null;
    }

    public AddFyleToDraftFromUriTask(@NonNull File localFile, @NonNull String fileName, @NonNull String mimeType, long discussionId) {
        this.uri = null;
        this.localFile = localFile;
        this.discussionId = discussionId;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.webclientProvidedSha256 = null;
    }

    public AddFyleToDraftFromUriTask(@NonNull File localFile, long discussionId, @NonNull String mimeType, @NonNull String fileName, @NonNull byte[] webclientProvidedSha256) {
        this.uri = null;
        this.localFile = localFile;
        this.discussionId = discussionId;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.webclientProvidedSha256 = webclientProvidedSha256;
    }

    // used for location messages with preview, a new draft message is manually created to insert preview as an attachment
    public AddFyleToDraftFromUriTask(Message draftMessage, @NonNull Uri uri, @Nullable String fileName, @Nullable String mimeType, long discussionId) {
        this.draftMessage = draftMessage;
        this.uri = uri;
        this.localFile = null;
        this.discussionId = discussionId;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.webclientProvidedSha256 = null;
    }

    private Message draftMessage = null;

    @Override
    public void run() {
        final AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion == null) {
            return;
        }

        // always true, except for location messages (when draft message is manually created)
        if (draftMessage == null) {
            db.runInTransaction(() -> {
                draftMessage = db.messageDao().getDiscussionDraftMessageSync(discussionId);
                if (draftMessage == null) {
                    draftMessage = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
                    draftMessage.id = db.messageDao().insert(draftMessage);
                }
            });
        }

        if (draftMessage == null) {
            Logger.e("Error getting/creating draft for discussion with id " + discussionId);
            return;
        }

        ContentResolver contentResolver = App.getContext().getContentResolver();
        String outputFile = null;
        String fileName = null;
        long uriFileSize = -1;
        FyleMessageJoinWithStatus copyingFyleMessageJoinWithStatus = null;
        Fyle nullFyle = null;

        try {
            String mimeType = computeMimeType(contentResolver);

            if (this.fileName != null) {
                fileName = this.fileName;
                if (mimeType == null) {
                    mimeType = PreviewUtils.getNonNullMimeType(null, fileName);
                }
            } else if (uri != null) {
                String[] projection = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
                try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
                    if ((cursor != null) && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                        try {
                            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                            if (sizeIndex >= 0) {
                                uriFileSize = cursor.getLong(sizeIndex);
                                if (AvailableSpaceHelper.getAvailableSpace() != null && AvailableSpaceHelper.getAvailableSpace() < uriFileSize) {
                                    App.openAppDialogLowStorageSpace();
                                }
                            }
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                }
            }

            if (fileName == null) {
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (extension != null) {
                    fileName = new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date()) + "." + extension;
                } else {
                    fileName = new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date());
                }
            }

            // try to correct potentially "generic" mime types like image/*
            mimeType = PreviewUtils.getNonNullMimeType(mimeType, fileName);

            //////////////
            // cleanup JPEG EXIF data if asked
            //////////////
            boolean alteredContent = false;
            if ("image/jpeg".equals(mimeType) && SettingsActivity.getMetadataRemovalPreference()) {
                localFile = removeJpegMetadata(uri, localFile, contentResolver);
                alteredContent = true;
            }


            final Fyle.SizeAndSha256 sizeAndSha256;
            if (localFile == null) {

                // --> copy the file locally before computing its hash
                // there is a bug with some phone (Oppo Find X2 Lite) where the input stream given by openInputStream is not always the same
                File photoDir = new File(App.getContext().getCacheDir(), App.CAMERA_PICTURE_FOLDER);
                try {
                    //noinspection ResultOfMethodCallIgnored
                    photoDir.mkdirs();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                localFile = new File(photoDir, Logger.getUuidString(UUID.randomUUID()));


                nullFyle = new Fyle();
                nullFyle.id = db.fyleDao().insert(nullFyle);

                copyingFyleMessageJoinWithStatus = FyleMessageJoinWithStatus.createCopying(nullFyle.id,
                        draftMessage.id,
                        draftMessage.senderIdentifier,
                        localFile.getAbsolutePath(),
                        fileName,
                        mimeType,
                        uriFileSize);
                db.fyleMessageJoinWithStatusDao().insert(copyingFyleMessageJoinWithStatus);

                draftMessage.recomputeAttachmentCount(db);
                db.messageDao().updateAttachmentCount(draftMessage.id, draftMessage.totalAttachmentCount, draftMessage.imageCount, 0, draftMessage.imageResolutions);

                long lastUpdateTimestamp = 0;
                //noinspection ConstantConditions
                try (InputStream is = contentResolver.openInputStream(uri)) {
                    if (is == null) {
                        throw new Exception("Unable to read from provided Uri");
                    }
                    MessageDigest h = MessageDigest.getInstance("SHA-256");
                    long fileSize = 0;

                    try (FileOutputStream fos = new FileOutputStream(localFile)) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = is.read(buffer)) != -1) {
                            h.update(buffer, 0, c);
                            fileSize += c;
                            fos.write(buffer, 0, c);
                            if (uriFileSize != -1) {
                                long newTimestamp = System.currentTimeMillis();
                                if (newTimestamp - lastUpdateTimestamp > 100) {
                                    lastUpdateTimestamp = newTimestamp;
                                    FyleProgressSingleton.INSTANCE.updateProgress(copyingFyleMessageJoinWithStatus.fyleId, copyingFyleMessageJoinWithStatus.messageId, (float) fileSize / uriFileSize, null);
                                }
                            }
                        }
                    }
                    FyleProgressSingleton.INSTANCE.finishProgress(copyingFyleMessageJoinWithStatus.fyleId, copyingFyleMessageJoinWithStatus.messageId);
                    sizeAndSha256 = new Fyle.SizeAndSha256(fileSize, h.digest());
                }
            } else {
                sizeAndSha256 = Fyle.computeSHA256FromFile(localFile.getAbsolutePath());
            }

            if (sizeAndSha256 == null) {
                Logger.i("Unable to compute SHA256 of local file");
                throw new Exception();
            }

            if (!alteredContent && this.webclientProvidedSha256 != null && !Arrays.equals(this.webclientProvidedSha256, sizeAndSha256.sha256)) {
                Logger.i("AddFyleToDraftFromUriTask: given sha256 and computed sha256 don't match");
                throw new Exception();
            }



            {
                // the inputs are fine, the sha256 is correct --> we are going to attach the fyle!
                // update draft message and the discussion last message timestamp
                draftMessage.timestamp = System.currentTimeMillis();
                draftMessage.sortIndex = draftMessage.timestamp;
                db.messageDao().updateTimestampAndSortIndex(draftMessage.id, draftMessage.timestamp, draftMessage.sortIndex);
                if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                }
            }

            final byte[] sha256 = sizeAndSha256.sha256;
            final long fileSize = sizeAndSha256.fileSize;
            outputFile = Fyle.buildFylePath(sha256);
            String finalMimeType = mimeType;

            Fyle fyle = db.fyleDao().getBySha256(sha256);
            if (fyle != null) {
                if (fyle.isComplete()) {
                    try {
                        // cleanup unnecessary copy of the file
                        if (nullFyle != null) {
                            db.fyleDao().delete(nullFyle);
                        }
                        if (copyingFyleMessageJoinWithStatus != null) {
                            try {
                                //noinspection ResultOfMethodCallIgnored
                                new File(copyingFyleMessageJoinWithStatus.getAbsoluteFilePath()).delete();
                            } catch (Exception e) {
                                // do nothing, will be auto cleaned up
                            }
                        }

                        final Fyle finalFyle = fyle;
                        final String finalOutputFile = outputFile;
                        final String finalFileName = fileName;
                        Boolean alreadyAttached = db.runInTransaction(() -> {
                            if (db.fyleMessageJoinWithStatusDao().get(finalFyle.id, draftMessage.id) != null) {
                                // file already attached
                                return true;
                            }
                            // Fyle is already complete, we can simply "hard-link" it
                            FyleMessageJoinWithStatus fyleMessageJoinWithStatus = FyleMessageJoinWithStatus.createDraft(finalFyle.id,
                                    draftMessage.id,
                                    draftMessage.senderIdentifier,
                                    finalOutputFile,
                                    finalFileName,
                                    finalMimeType,
                                    fileSize
                            );
                            db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
                            draftMessage.recomputeAttachmentCount(db);
                            db.messageDao().updateAttachmentCount(draftMessage.id, draftMessage.totalAttachmentCount, draftMessage.imageCount, 0, draftMessage.imageResolutions);
                            return false;
                        });
                        if (alreadyAttached == null || alreadyAttached) {
                            App.toast(App.getContext().getString(R.string.toast_message_file_already_attached, finalFileName), Toast.LENGTH_SHORT, Gravity.BOTTOM);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    // Fyle is incomplete (still downloading), but we have the complete Fyle at hand!
                    try {
                        Fyle.acquireLock(sha256);
                        if (nullFyle != null) {
                            db.fyleDao().delete(nullFyle);
                        }

                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().get(fyle.id, draftMessage.id);
                        if (fyleMessageJoinWithStatus != null) {
                            // file already attached --> delete the previous version which was incomplete
                            db.fyleMessageJoinWithStatusDao().delete(fyleMessageJoinWithStatus);
                        }

                        fyleMessageJoinWithStatus = FyleMessageJoinWithStatus.createDraft(fyle.id,
                                draftMessage.id,
                                draftMessage.senderIdentifier,
                                outputFile,
                                fileName,
                                finalMimeType,
                                fileSize);
                        db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
                        draftMessage.recomputeAttachmentCount(db);
                        db.messageDao().updateAttachmentCount(draftMessage.id, draftMessage.totalAttachmentCount, draftMessage.imageCount, 0, draftMessage.imageResolutions);

                        // update the filePath and mark the Fyle as complete
                        fyle.moveToFyleDirectory(localFile.getPath());
                        db.fyleDao().update(fyle);

                        //noinspection ConstantConditions
                        fyleMessageJoinWithStatus.filePath = fyle.filePath;
                        db.fyleMessageJoinWithStatusDao().updateFilePath(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.filePath);

                        // check all downloading operations, mark them as complete and delete the associated inboxAttachment (this will cancel the download operation)
                        List<FyleMessageJoinWithStatus> fyleMessageJoinWithStatusList = db.fyleMessageJoinWithStatusDao().getForFyleId(fyle.id);
                        for (FyleMessageJoinWithStatus otherFyleMessageJoinWithStatus : fyleMessageJoinWithStatusList) {
                            switch (otherFyleMessageJoinWithStatus.status) {
                                case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                                case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                                    otherFyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                                    FyleProgressSingleton.INSTANCE.finishProgress(otherFyleMessageJoinWithStatus.fyleId, otherFyleMessageJoinWithStatus.messageId);
                                    //noinspection ConstantConditions
                                    otherFyleMessageJoinWithStatus.filePath = fyle.filePath;
                                    otherFyleMessageJoinWithStatus.size = fileSize;
                                    db.fyleMessageJoinWithStatusDao().update(otherFyleMessageJoinWithStatus);
                                    otherFyleMessageJoinWithStatus.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);
                                    if (otherFyleMessageJoinWithStatus.engineNumber != null) {
                                        AppSingleton.getEngine().markAttachmentForDeletion(otherFyleMessageJoinWithStatus.bytesOwnedIdentity, otherFyleMessageJoinWithStatus.engineMessageIdentifier, otherFyleMessageJoinWithStatus.engineNumber);
                                    }
                                    otherFyleMessageJoinWithStatus.computeTextContentForFullTextSearchOnOtherThread(db, fyle);
                                    break;
                            }
                        }

                        // re-post the message if it was put on hold
                        db.runInTransaction(() -> {
                            Message reDraftMessage = db.messageDao().get(draftMessage.id);
                            if (reDraftMessage != null && reDraftMessage.status == Message.STATUS_UNPROCESSED) {
                                reDraftMessage.recomputeAttachmentCount(db);
                                db.messageDao().updateAttachmentCount(reDraftMessage.id, reDraftMessage.totalAttachmentCount, reDraftMessage.imageCount, 0, reDraftMessage.imageResolutions);
                                reDraftMessage.post(false, null);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        Fyle.releaseLock(sha256);
                    }
                }
            } else {
                try {
                    Fyle.acquireLock(sha256);
                    if (nullFyle != null) {
                        fyle = nullFyle;
                        fyle.sha256 = sha256;

                        copyingFyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_DRAFT;
                        copyingFyleMessageJoinWithStatus.size = fileSize;
                        AppDatabase.getInstance().fyleMessageJoinWithStatusDao().update(copyingFyleMessageJoinWithStatus);
                    } else {
                        fyle = new Fyle(sha256);
                        fyle.id = db.fyleDao().insert(fyle);

                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = FyleMessageJoinWithStatus.createDraft(
                                fyle.id,
                                draftMessage.id,
                                draftMessage.senderIdentifier,
                                outputFile,
                                fileName,
                                mimeType,
                                fileSize);
                        db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
                    }
                    draftMessage.recomputeAttachmentCount(db);
                    db.messageDao().updateAttachmentCount(draftMessage.id, draftMessage.totalAttachmentCount, draftMessage.imageCount, 0, draftMessage.imageResolutions);

                    // update the filePath and mark the Fyle as complete
                    fyle.moveToFyleDirectory(localFile.getPath());
                    db.fyleDao().update(fyle);
                    db.fyleMessageJoinWithStatusDao().updateFilePath(draftMessage.id, fyle.id, fyle.filePath);


                    // re-post the message if it was put on hold
                    db.runInTransaction(() -> {
                        Message reDraftMessage = db.messageDao().get(draftMessage.id);
                        if (reDraftMessage != null && reDraftMessage.status == Message.STATUS_UNPROCESSED) {
                            reDraftMessage.recomputeAttachmentCount(db);
                            db.messageDao().updateAttachmentCount(reDraftMessage.id, reDraftMessage.totalAttachmentCount, reDraftMessage.imageCount, 0, reDraftMessage.imageResolutions);
                            reDraftMessage.post(false, null);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Fyle.releaseLock(sha256);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (outputFile != null) {
                File outputFileFile = new File(App.absolutePathFromRelative(outputFile));
                //noinspection ResultOfMethodCallIgnored
                outputFileFile.delete();
            }
            if (nullFyle != null) {
                db.fyleDao().delete(nullFyle);
            }
            if (fileName != null) {
                App.toast(App.getContext().getResources().getString(R.string.toast_message_failed_to_attach_filename, fileName), Toast.LENGTH_SHORT, Gravity.BOTTOM);
            } else {
                App.toast(R.string.toast_message_failed_to_attach, Toast.LENGTH_SHORT);
            }
        }
    }

    private String computeMimeType(ContentResolver contentResolver) throws Exception {
        if (this.mimeType != null) {
            return this.mimeType;
        } else if (uri != null) {
            return contentResolver.getType(uri);
        } else {
            Logger.e("AddFyleToDraftFromUriTask: both mimeType and uri are null");
            throw new Exception();
        }
    }

    private File removeJpegMetadata(@Nullable Uri uri, @Nullable File localFile, ContentResolver contentResolver) throws IOException {
        File photoDir = new File(App.getContext().getCacheDir(), App.CAMERA_PICTURE_FOLDER);
        File photoFile = new File(photoDir, Logger.getUuidString(UUID.randomUUID()));
        //noinspection ResultOfMethodCallIgnored
        photoDir.mkdirs();
        if (!photoFile.createNewFile()) {
            throw new IOException();
        }
        if (uri != null) {
            try (InputStream in = contentResolver.openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(photoFile)) {
                if (in == null) {
                    throw new IOException();
                }
                JpegUtils.copyJpegWithoutAttributes(in, fos);
            } catch (JpegUtils.ICCProfileFoundException e) {
                JpegUtils.recompress(contentResolver, uri, photoFile);
                return photoFile;
            }
            try (InputStream in = contentResolver.openInputStream(uri)) {
                if (in == null) {
                    throw new IOException();
                }
                ExifInterface exifInterface = new ExifInterface(in);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation != ExifInterface.ORIENTATION_NORMAL &&
                        orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                    exifInterface = new ExifInterface(photoFile);
                    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
                    exifInterface.saveAttributes();
                }
            } catch (Exception e) {
                Logger.w("Error copying orientation after EXIF removal");
            }
        } else if (localFile != null){
            try (InputStream in = new FileInputStream(localFile);
                 FileOutputStream fos = new FileOutputStream(photoFile)) {
                JpegUtils.copyJpegWithoutAttributes(in, fos);
            } catch (JpegUtils.ICCProfileFoundException e) {
                JpegUtils.recompress(localFile, photoFile);
                return photoFile;
            }
            try {
                ExifInterface exifInterface = new ExifInterface(localFile);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation != ExifInterface.ORIENTATION_NORMAL &&
                        orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                    exifInterface = new ExifInterface(photoFile);
                    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
                    exifInterface.saveAttributes();
                }
            } catch (Exception e) {
                Logger.w("Error copying orientation after EXIF removal");
            }
        } else {
            throw new IOException();
        }
        return photoFile;
    }
}