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

package io.olvid.messenger.customClasses;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.LruCache;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.tasks.UpdateMessageImageResolutionsTask;

public class PreviewUtils {

    public static final int MAX_SIZE = 100_000; // constant to indicate that previews should be as large as possible
    public static final int MAX_BITMAP_SIZE = 100 * 1024 * 1024;

    // region Implement a local in-memory cache of miniature bitmaps
    private static final int MAX_THUMBNAILS_CACHE_SIZE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? 20_000_000 : 100_000_000;
    private static final LruCache<String, SizeAndBitmap> thumbnailCache = new LruCache<String, SizeAndBitmap>(MAX_THUMBNAILS_CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, SizeAndBitmap value) {
            return value.bitmap.getByteCount();
        }
    };


    @NonNull
    public static String getNonNullMimeType(@Nullable String mimeType, @Nullable String fileName) {
        if (mimeType == null || !mimeType.contains("/") || mimeType.endsWith("/*")) { // also try yo correct generic mime types
            if (fileName != null) {
                String extension = StringUtils2.Companion.getExtensionFromFilename(fileName);
                String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (type != null) {
                    return type;
                }
            }
            if (mimeType == null || !mimeType.contains("/")) { // don't return this when the mime type ends with "/*"
                return "application/octet-stream";
            }
        }
        return mimeType;
    }


    public static boolean mimeTypeIsSupportedImageOrVideo(@NonNull String mimeType) {
        if (mimeType.startsWith("image/")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return ImageDecoder.isMimeTypeSupported(mimeType);
            } else {
                switch (mimeType) {
                    case "image/jpeg":
                    case "image/bmp":
                    case "image/png":
                    case "image/gif":
                    case "image/webp":
                        return true;
                    default:
                        return false;
                }
            }
        }
        return mimeType.startsWith("video/");
    }

    public static boolean canGetPreview(Fyle fyle, FyleMessageJoinWithStatus fyleMessageJoinWithStatus) {
        String mimeType = fyleMessageJoinWithStatus.getNonNullMimeType();
        boolean notEmpty = fyleMessageJoinWithStatus.progress != 0 || (fyleMessageJoinWithStatus.status != FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE && fyleMessageJoinWithStatus.status != FyleMessageJoinWithStatus.STATUS_DOWNLOADING);

        if (mimeType.startsWith("image/")) {
            if (notEmpty) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return ImageDecoder.isMimeTypeSupported(mimeType);
                } else {
                    switch (mimeType) {
                        case "image/jpeg":
                        case "image/bmp":
                        case "image/png":
                        case "image/gif":
                        case "image/webp":
                            return true;
                        default:
                            return false;
                    }
                }
            } else {
                return false;
            }
        } else if (mimeType.startsWith("video/")) {
            return notEmpty;
        } else {
            return fyle.isComplete()
                    && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
                    && ("application/pdf".equals(mimeType));
        }
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        int degrees;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            default:
                return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap getBitmapPreview(Fyle fyle, FyleMessageJoinWithStatus fyleMessageJoinWithStatus, int previewPixelSize) {
        if (fyle.sha256 == null) {
            return null;
        }
        String cacheKey = Logger.toHexString(fyle.sha256) + "_" + fyleMessageJoinWithStatus.getNonNullMimeType();
        if (fyle.isComplete()) {
            SizeAndBitmap sizeAndBitmap = thumbnailCache.get(cacheKey);
            if (sizeAndBitmap != null && sizeAndBitmap.size >= previewPixelSize) {
                if (sizeAndBitmap.originalWidth != null && sizeAndBitmap.originalHeight != null) {
                    String imageResolution = sizeAndBitmap.originalWidth + "x" + sizeAndBitmap.originalHeight;
                    if (!imageResolution.equals(fyleMessageJoinWithStatus.imageResolution)) {
                        // we do not update the fyleMessageJoin resolution here, otherwise we won't notice the difference when computing the diffUtil from database
                        // fyleMessageJoinWithStatus.imageResolution = imageResolution;
                        AppDatabase.getInstance().fyleMessageJoinWithStatusDao().updateImageResolution(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, imageResolution);
                        App.runThread(new UpdateMessageImageResolutionsTask(fyleMessageJoinWithStatus.messageId));
                    }
                }
                return sizeAndBitmap.bitmap;
            }
        }
        String filePath = App.absolutePathFromRelative(fyle.filePath);
        if (filePath == null) {
            filePath = fyleMessageJoinWithStatus.getAbsoluteFilePath();
        }
        if (!new File(filePath).exists()) {
            if (fyleMessageJoinWithStatus.miniPreview != null) {
                try {
                    return BitmapFactory.decodeByteArray(fyleMessageJoinWithStatus.miniPreview, 0, fyleMessageJoinWithStatus.miniPreview.length);
                } catch (Exception e) {
                    // do nothing
                }
            }
            return null;
        }

        Bitmap bitmap = null;
        Integer originalWidth = null;
        Integer originalHeight = null;
        if (fyleMessageJoinWithStatus.getNonNullMimeType().matches("image/.+")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            int size = Math.min(options.outWidth, options.outHeight);
            if (size == -1) {
                Logger.d("Error loading bitmap bounds from image Fyle");
                if (fyleMessageJoinWithStatus.miniPreview != null) {
                    try {
                        return BitmapFactory.decodeByteArray(fyleMessageJoinWithStatus.miniPreview, 0, fyleMessageJoinWithStatus.miniPreview.length);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                return null;
            }

            options = new BitmapFactory.Options();
            if (previewPixelSize != MAX_SIZE) {
                options.inSampleSize = size / previewPixelSize;
            } else {
                options.inSampleSize = 1;
            }
            if (!fyle.isComplete() && fyleMessageJoinWithStatus.miniPreview != null) {
                // make the bitmap mutable, so we can mux the miniPreview with it
                options.inMutable = true;
            }

            try {
                bitmap = BitmapFactory.decodeFile(filePath, options);
                if (bitmap == null) {
                    if (fyleMessageJoinWithStatus.miniPreview != null) {
                        try {
                            return BitmapFactory.decodeByteArray(fyleMessageJoinWithStatus.miniPreview, 0, fyleMessageJoinWithStatus.miniPreview.length);
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    return null;
                }
            } catch (OutOfMemoryError e) {
                return null;
            }

            if (bitmap.getByteCount() > MAX_BITMAP_SIZE) {
                int scaled = (int) Math.sqrt((double) bitmap.getByteCount() / MAX_BITMAP_SIZE) + 1;
                bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / scaled, bitmap.getHeight() / scaled, true);
            }

            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try {
                ExifInterface exifInterface = new ExifInterface(filePath);
                orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                bitmap = rotateBitmap(bitmap, orientation);
            } catch (IOException e) {
                Logger.d("Error creating ExifInterface for file " + filePath);
            }


            if (!fyle.isComplete() && fyleMessageJoinWithStatus.miniPreview != null) {
                // fyle is incomplete --> complete the bitmap with our mini preview
                try {
                    Bitmap miniPreview = BitmapFactory.decodeByteArray(fyleMessageJoinWithStatus.miniPreview, 0, fyleMessageJoinWithStatus.miniPreview.length);
                    Canvas canvas = new Canvas(bitmap);
                    Paint paint = new Paint();
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
                    int wOrH = Math.min(bitmap.getWidth(), bitmap.getHeight());
                    RectF dst = new RectF((bitmap.getWidth()-wOrH) / 2f, (bitmap.getHeight()-wOrH) / 2f, (bitmap.getWidth()+wOrH) / 2f, (bitmap.getHeight()+wOrH) / 2f);
                    canvas.drawBitmap(miniPreview, null, dst, paint);
                } catch (Exception e) {
                    // do nothing
                }
            }

            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                //noinspection SuspiciousNameCombination
                originalWidth = options.outHeight;
                //noinspection SuspiciousNameCombination
                originalHeight = options.outWidth;
            } else {
                originalWidth = options.outWidth;
                originalHeight = options.outHeight;
            }
            String imageResolution = originalWidth + "x" + originalHeight;
            if (!imageResolution.equals(fyleMessageJoinWithStatus.imageResolution)) {
                // we do not update the fyleMessageJoin resolution here, otherwise we won't notice the difference when computing the diffUtil from database
                // fyleMessageJoinWithStatus.imageResolution = imageResolution;
                AppDatabase.getInstance().fyleMessageJoinWithStatusDao().updateImageResolution(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, imageResolution);
                App.runThread(new UpdateMessageImageResolutionsTask(fyleMessageJoinWithStatus.messageId));
            }
        } else if (fyleMessageJoinWithStatus.getNonNullMimeType().matches("video/.+")) {
            bitmap = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
            if (fyle.isComplete()) {
                String imageResolution = null;
                if (bitmap == null) {
                    imageResolution = "";
                } else {
                    try {
                        // Do not use "close with resource" as this only works on API 29 or higher...
                        //noinspection resource
                        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                        mediaMetadataRetriever.setDataSource(filePath);
                        String widthString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                        String heightString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                        if (widthString != null && heightString != null) {
                            originalWidth = Integer.parseInt(widthString);
                            originalHeight = Integer.parseInt(heightString);

                            imageResolution = "v" + originalWidth + "x" + originalHeight;
                        }
                    } catch (Exception e) {
                        // do nothing, this will be tried again when computing next preview
                    }
                }
                if (!Objects.equals(fyleMessageJoinWithStatus.imageResolution, imageResolution)) {
                    // we do not update the fyleMessageJoin resolution here, otherwise we won't notice the difference when computing the diffUtil from database
                    // fyleMessageJoinWithStatus.imageResolution = imageResolution;
                    AppDatabase.getInstance().fyleMessageJoinWithStatusDao().updateImageResolution(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, imageResolution);
                    App.runThread(new UpdateMessageImageResolutionsTask(fyleMessageJoinWithStatus.messageId));
                }
            }
        } else if (fyle.isComplete() && fyleMessageJoinWithStatus.getNonNullMimeType().equals("application/pdf")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                try {
                    if (previewPixelSize == MAX_SIZE) {
                        previewPixelSize = 256;
                    }
                    ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(filePath), ParcelFileDescriptor.MODE_READ_ONLY);
                    try (PdfRenderer renderer = new PdfRenderer(fd)) {
                        try (PdfRenderer.Page page = renderer.openPage(0)) {
                            float ratio = (float) page.getWidth() / page.getHeight();
                            if (ratio > 1) {
                                bitmap = Bitmap.createBitmap(previewPixelSize, (int) (previewPixelSize / ratio), Bitmap.Config.ARGB_8888);
                            } else {
                                bitmap = Bitmap.createBitmap((int) (ratio * previewPixelSize), previewPixelSize, Bitmap.Config.ARGB_8888);
                            }
                            Canvas canvas = new Canvas(bitmap);
                            canvas.drawColor(Color.WHITE);
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (fyle.isComplete() && bitmap != null) {
            synchronized (thumbnailCache) {
                SizeAndBitmap oldCachedBitmap = thumbnailCache.get(cacheKey);
                if (oldCachedBitmap == null || oldCachedBitmap.size < previewPixelSize) {
                    thumbnailCache.put(cacheKey, new SizeAndBitmap(previewPixelSize, bitmap, originalWidth, originalHeight));
                }
            }
        }
        if (bitmap == null && fyleMessageJoinWithStatus.miniPreview != null) {
            try {
                bitmap = BitmapFactory.decodeByteArray(fyleMessageJoinWithStatus.miniPreview, 0, fyleMessageJoinWithStatus.miniPreview.length);
            } catch (Exception e) {
                // do nothing
            }
        }
        return bitmap;
    }


    public static String getResolutionString(Fyle fyle, FyleMessageJoinWithStatus fyleMessageJoinWithStatus) {
        if (fyle.isComplete()) {
            String mimeType = getNonNullMimeType(fyleMessageJoinWithStatus.mimeType, fyleMessageJoinWithStatus.fileName);
            if (mimeType.startsWith("video/")) {
                try {
                    // Do not use "close with resource" as this only works on API 29 or higher...
                    //noinspection resource
                    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                    mediaMetadataRetriever.setDataSource(fyle.filePath);
                    String widthString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    String heightString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    if (widthString != null && heightString != null) {
                        return "v" + Integer.parseInt(widthString) + "x" + Integer.parseInt(heightString);
                    } else {
                        return "";
                    }
                } catch (Exception e) {
                    // do nothing
                }
            } else if (mimeType.startsWith("image/")) {
                String absolutePath = App.absolutePathFromRelative(fyle.filePath);
                if (absolutePath != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // drawable (possibly animated)
                        try {
                            String resolution = PreviewUtilsWithDrawables.getDrawableResolution(absolutePath);
                            if (resolution != null) {
                                return resolution;
                            }
                        } catch (Exception e) {
                            // on API28 emulator, decoding sometimes fails --> do nothing and fallback to bitmap
                        }
                    }
                    // bitmap fallback (for old Android versions or in case of Exception)

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(absolutePath, options);
                    if (options.outWidth != -1 && options.outHeight != -1) {
                        int orientation = ExifInterface.ORIENTATION_NORMAL;
                        try {
                            ExifInterface exifInterface = new ExifInterface(absolutePath);
                            orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        } catch (IOException e) {
                            // do nothing
                        }

                        if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                            return options.outHeight + "x" + options.outWidth;
                        } else {
                            return options.outWidth + "x" + options.outHeight;
                        }
                    }
                }
            }
        }
        return null;
    }




    public static void purgeCache() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PreviewUtilsWithDrawables.purgeCache();
        }
        thumbnailCache.evictAll();
    }

    private static class SizeAndBitmap {
        final int size;
        @NonNull final Bitmap bitmap;
        @Nullable final Integer originalWidth;
        @Nullable final Integer originalHeight;

        public SizeAndBitmap(int size, @NonNull Bitmap bitmap, @Nullable Integer originalWidth, @Nullable Integer originalHeight) {
            this.size = size;
            this.bitmap = bitmap;
            this.originalHeight = originalHeight;
            this.originalWidth = originalWidth;
        }
    }

    public static class DrawablePreviewException extends Exception {
        public DrawablePreviewException(Throwable cause) {
            super(cause);
        }
    }

    public static class ImageResolution {
        public enum KIND {
            IMAGE,
            ANIMATED,
            VIDEO,
        }

        public final KIND kind;
        public final int width;
        public final int height;

        public ImageResolution(String resolutionString) throws Exception {
            if (resolutionString == null) {
                throw new Exception();
            }
            if (resolutionString.startsWith("a")) {
                kind = KIND.ANIMATED;
                resolutionString = resolutionString.substring(1);
            } else if (resolutionString.startsWith("v")) {
                kind = KIND.VIDEO;
                resolutionString = resolutionString.substring(1);
            } else {
                kind = KIND.IMAGE;
            }

            String[] parts = resolutionString.split("x");
            if (parts.length != 2) {
                throw new Exception();
            }

            width = Integer.parseInt(parts[0]);
            height = Integer.parseInt(parts[1]);
        }

        public static ImageResolution[] parseMultiple(String resolutionsString) throws Exception {
            if (resolutionsString == null) {
                return new ImageResolution[0];
            }
            String[] parts = resolutionsString.split(";");
            ImageResolution[] imageResolutions = new ImageResolution[parts.length];
            for (int i=0; i<parts.length; i++) {
                imageResolutions[i] = new ImageResolution(parts[i]);
            }

            return imageResolutions;
        }

        @SuppressWarnings("SuspiciousNameCombination")
        public int getPreferredHeight(int displayWidth, boolean wide) {
            if (kind == KIND.ANIMATED) {
                if (width > height) {
                    return (displayWidth * height) / width;
                } else {
                    return displayWidth;
                }
            } else {
                if (wide) {
                    return displayWidth / 2;
                }
                return displayWidth;
            }
        }
    }
}
