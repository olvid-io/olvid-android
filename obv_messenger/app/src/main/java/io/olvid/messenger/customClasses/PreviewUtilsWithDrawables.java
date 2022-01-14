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

package io.olvid.messenger.customClasses;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.tasks.UpdateMessageImageResolutionsTask;

public class PreviewUtilsWithDrawables {
    private static final int MAX_DRAWABLE_CACHE_COUNT = 20;
    private static final LruCache<String, SizeAndDrawable> thumbnailDrawableCache = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? new LruCache<>(MAX_DRAWABLE_CACHE_COUNT) : null;

    @RequiresApi(api = Build.VERSION_CODES.P)
    public static Drawable getDrawablePreview(Fyle fyle, FyleMessageJoinWithStatus fyleMessageJoinWithStatus, int previewPixelSize) throws PreviewUtils.DrawablePreviewException {
        String cacheKey = Logger.toHexString(fyle.sha256) + "_" + fyleMessageJoinWithStatus.getNonNullMimeType();
        if (fyle.isComplete()) {
            SizeAndDrawable sizeAndDrawable = thumbnailDrawableCache.get(cacheKey);
            if (sizeAndDrawable != null && sizeAndDrawable.size >= previewPixelSize) {
                if (sizeAndDrawable.originalWidth != null && sizeAndDrawable.originalHeight != null) {
                    String imageResolution = sizeAndDrawable.originalWidth + "x" + sizeAndDrawable.originalHeight;
                    if (sizeAndDrawable.drawable instanceof AnimatedImageDrawable){
                        imageResolution = "a" + imageResolution;
                    }
                    if (!imageResolution.equals(fyleMessageJoinWithStatus.imageResolution)) {
                        // we do not update the fyleMessageJoin resolution here, otherwise we won't notice the difference when computing the diffUtil from database
                        // fyleMessageJoinWithStatus.imageResolution = imageResolution;
                        AppDatabase.getInstance().fyleMessageJoinWithStatusDao().updateImageResolution(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, imageResolution);
                        App.runThread(new UpdateMessageImageResolutionsTask(fyleMessageJoinWithStatus.messageId));
                    }
                }
                return sizeAndDrawable.drawable;
            }
        }
        String filePath = App.absolutePathFromRelative(fyle.filePath);
        if (filePath == null) {
            filePath = fyleMessageJoinWithStatus.getAbsoluteFilePath();
        }
        if (!new File(filePath).exists()) {
            if (fyleMessageJoinWithStatus.miniPreview != null) {
                try {
                    ImageDecoder.Source src = ImageDecoder.createSource(ByteBuffer.wrap(fyleMessageJoinWithStatus.miniPreview));
                    return ImageDecoder.decodeDrawable(src);
                } catch (Exception e) {
                    // do nothing
                }
            }
            return null;
        }

        ImageDecoder.Source src = ImageDecoder.createSource(new File(filePath));
        Drawable drawable;
        Integer originalWidth;
        Integer originalHeight;
        boolean partial;
        try {
            PreviewUtilsScaleDownListener listener = new PreviewUtilsScaleDownListener(previewPixelSize);
            drawable = ImageDecoder.decodeDrawable(src, listener);
            originalWidth = listener.width;
            originalHeight = listener.height;
            partial = listener.partial;
            if (listener.width != null && listener.height != null && fyle.isComplete()) {
                String imageResolution = listener.width + "x" + listener.height;
                if (drawable instanceof AnimatedImageDrawable){
                    imageResolution = "a" + imageResolution;
                }
                if (!Objects.equals(fyleMessageJoinWithStatus.imageResolution, imageResolution)) {
                    // we do not update the fyleMessageJoin resolution here, otherwise we won't notice the difference when computing the diffUtil from database
                    // fyleMessageJoinWithStatus.imageResolution = imageResolution;
                    AppDatabase.getInstance().fyleMessageJoinWithStatusDao().updateImageResolution(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, imageResolution);
                    App.runThread(new UpdateMessageImageResolutionsTask(fyleMessageJoinWithStatus.messageId));
                }
            }
            if (partial && (drawable instanceof BitmapDrawable) && fyleMessageJoinWithStatus.miniPreview != null) {
                try {
                    Bitmap miniPreview = BitmapFactory.decodeByteArray(fyleMessageJoinWithStatus.miniPreview, 0, fyleMessageJoinWithStatus.miniPreview.length);
                    drawable = new BitmapDrawable(App.getContext().getResources(), ((BitmapDrawable) drawable).getBitmap().copy(Bitmap.Config.ARGB_8888, true));
                    Canvas canvas = new Canvas(((BitmapDrawable) drawable).getBitmap());
                    Paint paint = new Paint();
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
                    int wOrH = Math.min(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    RectF dst = new RectF((drawable.getIntrinsicWidth()-wOrH) / 2f, (drawable.getIntrinsicHeight()-wOrH) / 2f, (drawable.getIntrinsicWidth()+wOrH) / 2f, (drawable.getIntrinsicHeight()+wOrH) / 2f);
                    canvas.drawBitmap(miniPreview, null, dst, paint);
                } catch (Exception e) {
                    e.printStackTrace();
                    // do nothing
                }
            }
        } catch (Exception e) {
            // on API28 emulator, decoding sometimes fails --> throw exception so that we can fallback to old method
            throw new PreviewUtils.DrawablePreviewException(e);
        }

        if (fyle.isComplete() && !partial) {
            synchronized (thumbnailDrawableCache) {
                SizeAndDrawable oldCachedDrawable = thumbnailDrawableCache.get(cacheKey);
                if (oldCachedDrawable == null || oldCachedDrawable.size < previewPixelSize) {
                    thumbnailDrawableCache.put(cacheKey, new SizeAndDrawable(previewPixelSize, drawable, originalWidth, originalHeight));
                }
            }
        }
        return drawable;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    static class PreviewUtilsScaleDownListener implements ImageDecoder.OnHeaderDecodedListener, ImageDecoder.OnPartialImageListener {
        public Integer width;
        public Integer height;
        public boolean partial;

        private final int previewPixelSize;

        public PreviewUtilsScaleDownListener(int previewPixelSize) {
            this.previewPixelSize = previewPixelSize;
        }

        @Override
        public void onHeaderDecoded(@NonNull ImageDecoder decoder, @NonNull ImageDecoder.ImageInfo info, @NonNull ImageDecoder.Source source) {
            width = info.getSize().getWidth();
            height = info.getSize().getHeight();
            partial = false;
            if (previewPixelSize != PreviewUtils.MAX_SIZE) {
                int size = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (size > previewPixelSize) {
                    int subSampling = size / previewPixelSize;
                    decoder.setTargetSampleSize(subSampling);
                }
            } else if (4 * width * height > PreviewUtils.MAX_BITMAP_SIZE) {
                int scaled = (int) Math.sqrt((double) (4 * width * height) / PreviewUtils.MAX_BITMAP_SIZE) + 1;
                decoder.setTargetSize(width / scaled, height / scaled);
            }
            decoder.setOnPartialImageListener(this);
        }

        @Override
        public boolean onPartialImage(@NonNull ImageDecoder.DecodeException e) {
            partial = true;
            return true;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    static String getDrawableResolution(String filePath) throws Exception {
        ImageDecoder.Source src = ImageDecoder.createSource(new File(filePath));
        PreviewUtilsWithDrawables.PreviewUtilsScaleDownListener listener = new PreviewUtilsWithDrawables.PreviewUtilsScaleDownListener(10);
        Drawable drawable = ImageDecoder.decodeDrawable(src, listener);
        if (listener.width != null && listener.height != null) {
            String imageResolution = listener.width + "x" + listener.height;
            if (drawable instanceof AnimatedImageDrawable) {
                imageResolution = "a" + imageResolution;
            }
            return imageResolution;
        }
        return null;
    }

    public static void purgeCache() {
        if (thumbnailDrawableCache != null) {
            thumbnailDrawableCache.evictAll();
        }
    }

    static class SizeAndDrawable {
        final int size;
        @NonNull final Drawable drawable;
        @Nullable
        final Integer originalWidth;
        @Nullable final Integer originalHeight;

        public SizeAndDrawable(int size, @NonNull Drawable drawable, @Nullable Integer originalWidth, @Nullable Integer originalHeight) {
            this.size = size;
            this.drawable = drawable;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
        }
    }
}

