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

package io.olvid.messenger.databases.tasks;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.FileOutputStream;
import java.io.InputStream;

import io.olvid.messenger.App;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;

public class SetDiscussionBackgroundImageTask implements Runnable {
    private final Uri uri;
    private final long discussionId;

    public SetDiscussionBackgroundImageTask(Uri uri, long discussionId) {
        this.uri = uri;
        this.discussionId = discussionId;
    }

    @Override
    public void run() {
        final AppDatabase db = AppDatabase.getInstance();
        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
        if (discussionCustomization == null) {
            discussionCustomization = new DiscussionCustomization(discussionId);
            db.discussionCustomizationDao().insert(discussionCustomization);
        }

        ContentResolver contentResolver = App.getContext().getContentResolver();

        String relativeOutputFile = discussionCustomization.buildBackgroundImagePath();
        String outputFile = App.absolutePathFromRelative(relativeOutputFile);

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        // first get the orientation
        try (InputStream is = contentResolver.openInputStream(uri)) {
            if (is != null) {
                ExifInterface exifInterface = new ExifInterface(is);
                orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {}

        // copy the file
        try (InputStream is = contentResolver.openInputStream(uri)) {
            if (is == null) {
                throw new Exception("Unable to read from provided Uri");
            }

            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap.getHeight() > bitmap.getWidth()) {
                if (bitmap.getHeight() > 2160) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth()*2160/bitmap.getHeight(), 2160, true);
                }
            } else {
                if (bitmap.getWidth() > 2160) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, 2160, bitmap.getHeight()*2160/bitmap.getWidth(), true);
                }
            }

            bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
                fos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        discussionCustomization.backgroundImageUrl = relativeOutputFile;
        db.discussionCustomizationDao().update(discussionCustomization);
    }
}
