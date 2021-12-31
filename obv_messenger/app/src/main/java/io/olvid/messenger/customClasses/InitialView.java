/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.core.content.res.ResourcesCompat;

import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.exifinterface.media.ExifInterface;

import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;

import io.olvid.messenger.App;
import io.olvid.messenger.R;

public class InitialView extends View {
    private byte[] bytes;
    private String initial;
    private String photoUrl; // absolute path
    private boolean keycloakCertified = false;
    private boolean locked = false;
    private boolean inactive = false;

    private Paint backgroundPaint;
    private Paint insidePaint;
    private Bitmap overlayBitmap;

    private int width;
    private int height;
    private int size = 0;
    private float top;
    private float left;
    private Bitmap bitmap;
    private float insideX;
    private float insideY;


    public InitialView(Context context) {
        super(context);
    }

    public InitialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) {
            setInitial(new byte[]{0,1,35}, "A");
        }
    }

    public InitialView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public InitialView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setGroup(byte[] groupId) {
        this.bitmap = null;
        this.photoUrl = null;
        this.bytes = groupId;
        this.initial = null;
        init();
    }

    public void setInitial(byte[] identityBytes, String initial) {
        this.bitmap = null;
        this.photoUrl = null;
        this.bytes = identityBytes;
        this.initial = initial;
        init();
    }

    public void setPhotoUrl(byte[] bytes, String relativePathPhotoUrl) {
        this.bitmap = null;
        this.photoUrl = App.absolutePathFromRelative(relativePathPhotoUrl);
        this.bytes = bytes;
        this.initial = null;
        init();
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setAbsolutePhotoUrl(byte[] bytes, String absolutePhotoUrl) {
        this.bitmap = null;
        this.photoUrl = absolutePhotoUrl;
        this.bytes = bytes;
        this.initial = null;
        init();
    }

    public void setKeycloakCertified(boolean keycloakCertified) {
        if (this.keycloakCertified != keycloakCertified) {
            this.bitmap = null;
            this.keycloakCertified = keycloakCertified;
            init();
        }
    }

    public void setLocked(boolean locked) {
        if (this.locked != locked) {
            this.locked = locked;
            init();
        }
    }

    public void setInactive(boolean inactive) {
        if (this.inactive != inactive) {
            this.inactive = inactive;
            init();
        }
    }


    private void init() {
        if (bytes == null || size == 0) {
            return;
        }

        invalidate();

        top = (height - size) * .5f;
        left = (width - size) * .5f;

        if (photoUrl != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photoUrl, options);
            int size = Math.min(options.outWidth, options.outHeight);
            if (size != -1) {
                int subSampling = size / this.size;
                options = new BitmapFactory.Options();
                options.inSampleSize = subSampling;
                Bitmap squareBitmap = BitmapFactory.decodeFile(photoUrl, options);
                if (squareBitmap != null) {
                    try {
                        ExifInterface exifInterface = new ExifInterface(photoUrl);
                        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        squareBitmap = PreviewUtils.rotateBitmap(squareBitmap, orientation);
                    } catch (IOException e) {
                        // exif error, do nothing
                    }
                    RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), squareBitmap);
                    roundedDrawable.setCornerRadius(this.size / 2f);
                    bitmap = Bitmap.createBitmap(this.size, this.size, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    roundedDrawable.setBounds(0, 0, this.size, this.size);
                    if (locked || inactive) {
                        ColorMatrix colorMatrix = new ColorMatrix();
                        colorMatrix.setSaturation(.5f);
                        roundedDrawable.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                    }
                    roundedDrawable.draw(canvas);

                    if (locked) {
                        int lockSize = (int) (.3f*this.size);
                        Bitmap keycloakBitmap = Bitmap.createBitmap(lockSize, lockSize, Bitmap.Config.ARGB_8888);
                        Canvas keycloakCanvas = new Canvas(keycloakBitmap);
                        Drawable lockDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_lock_circled, null);
                        if (lockDrawable != null) {
                            lockDrawable.setBounds(0, 0, lockSize, lockSize);
                            lockDrawable.draw(keycloakCanvas);
                        }
                        canvas.drawBitmap(keycloakBitmap, this.size-lockSize,  0, null);
                    } else if (inactive) {
                        int blockedSize = (int) (.8f*this.size);
                        Bitmap blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, Bitmap.Config.ARGB_8888);
                        Canvas blockedCanvas = new Canvas(blockedBitmap);
                        Drawable blockedDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_block_outlined, null);
                        if (blockedDrawable != null) {
                            blockedDrawable.setBounds(0, 0, blockedSize, blockedSize);
                            blockedDrawable.draw(blockedCanvas);
                        }
                        canvas.drawBitmap(blockedBitmap, (this.size-blockedSize)/2f,  (this.size-blockedSize)/2f, null);
                    } else if (keycloakCertified) {
                        int keycloakSize = (int) (.3f*this.size);
                        Bitmap keycloakBitmap = Bitmap.createBitmap(keycloakSize, keycloakSize, Bitmap.Config.ARGB_8888);
                        Canvas keycloakCanvas = new Canvas(keycloakBitmap);
                        Drawable keycloakDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_keycloak_certified, null);
                        if (keycloakDrawable != null) {
                            keycloakDrawable.setBounds(0, 0, keycloakSize, keycloakSize);
                            keycloakDrawable.draw(keycloakCanvas);
                        }
                        canvas.drawBitmap(keycloakBitmap, this.size-keycloakSize,  0, null);
                    }

                    return;
                }
            }
        }

        int lightColor = getLightColor(bytes);
        int darkColor = getDarkColor(bytes);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(lightColor);

        insidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        insidePaint.setStyle(Paint.Style.FILL);
        insidePaint.setColor(darkColor);
        insidePaint.setTextSize(size * .6f);
        insidePaint.setTextAlign(Paint.Align.CENTER);

        insidePaint.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));

        if (locked) {
            int bitmapSize = (int) (size * .45f);
            overlayBitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(overlayBitmap);
            Drawable overlayDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_lock, null);
            if (overlayDrawable != null) {
                overlayDrawable.setColorFilter(new PorterDuffColorFilter(darkColor, PorterDuff.Mode.SRC_IN));
                overlayDrawable.setBounds(0, 0, bitmapSize, bitmapSize);
                overlayDrawable.draw(bitmapCanvas);
            }

            insideX = (size - bitmapSize) * .5f;
            insideY = (size - bitmapSize) * .5f;
        } else {
            if (initial == null) {
                int bitmapSize = (int) (size * .75f);
                overlayBitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(overlayBitmap);
                Drawable groupDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_group, null);
                if (groupDrawable != null) {
                    groupDrawable.setColorFilter(new PorterDuffColorFilter(darkColor, PorterDuff.Mode.SRC_IN));
                    groupDrawable.setBounds(0, 0, bitmapSize, bitmapSize);
                    groupDrawable.draw(bitmapCanvas);
                }

                insideX = (size - bitmapSize) * .5f;
                insideY = (size - bitmapSize) * .5f;
            } else {
                overlayBitmap = null;
                insideX = size * .5f;
                insideY = size * .5f - (insidePaint.descent() + insidePaint.ascent()) / 2f;
            }
        }
    }

    private int getLightColor(byte[] bytes) {
        int hue = hueFromBytes(bytes);
        if (bytes.length == 0){
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.5f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.9f});
            }
        } else {
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0.6f, 0.7f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0.8f, 0.9f});
            }
        }
    }

    private int getDarkColor(byte[] bytes) {
        int hue = hueFromBytes(bytes);
        if (bytes.length == 0){
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.4f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.7f});
            }
        } else {
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0.5f, 0.5f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0.7f, 0.7f});
            }
        }
    }

    public static int getTextColor(Context context, byte[] bytes, Integer hue) {
        int computedHue = (hue != null) ? hue : hueFromBytes(bytes);
        if (bytes.length == 0) {
            if ((context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{computedHue, 0, 0.4f});
            } else {
                return ColorUtils.HSLToColor(new float[]{computedHue, 0, 0.4f});
            }
        } else {
            if ((context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{computedHue, 0.5f, 0.4f});
            } else {
                return ColorUtils.HSLToColor(new float[]{computedHue, 0.7f, 0.4f});
            }
        }
    }

    public static int hueFromBytes(byte[] bytes) {
        if (bytes == null) {
            return 0;
        }
        int result = 1;
        for (byte element : bytes) {
            result = 31 * result + element;
        }
        return ((result & 0xff)*360)/256;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        setSize(w, h);
    }

    public void setSize(int w, int h) {
        bitmap = null;
        width = w;
        height = h;
        size = Math.min(width, height);
        init();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawOnCanvas(canvas);
    }

    public void drawOnCanvas(Canvas canvas) {
        try {
            if (bitmap == null) {
                if (backgroundPaint == null) {
                    return;
                }
                Bitmap localBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(localBitmap);

                bitmapCanvas.drawOval(new RectF(0, 0, size, size), backgroundPaint);

                if (overlayBitmap != null) {
                    bitmapCanvas.drawBitmap(overlayBitmap, insideX, insideY, insidePaint);
                } else if (initial != null) {
                    bitmapCanvas.drawText(initial, insideX, insideY, insidePaint);
                }

                if (inactive) {
                    int blockedSize = (int) (.8f*this.size);
                    Bitmap blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, Bitmap.Config.ARGB_8888);
                    Canvas blockedCanvas = new Canvas(blockedBitmap);
                    Drawable blockedDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_block_outlined, null);
                    if (blockedDrawable != null) {
                        blockedDrawable.setBounds(0, 0, blockedSize, blockedSize);
                        blockedDrawable.draw(blockedCanvas);
                    }
                    bitmapCanvas.drawBitmap(blockedBitmap, (this.size-blockedSize)/2f,  (this.size-blockedSize)/2f, null);
                } else if (keycloakCertified) {
                    int keycloakSize = (int) (.3f * size);
                    Bitmap keycloakBitmap = Bitmap.createBitmap(keycloakSize, keycloakSize, Bitmap.Config.ARGB_8888);
                    Canvas keycloakCanvas = new Canvas(keycloakBitmap);
                    Drawable keycloakDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_keycloak_certified, null);
                    if (keycloakDrawable != null) {
                        keycloakDrawable.setBounds(0, 0, keycloakSize, keycloakSize);
                        keycloakDrawable.draw(keycloakCanvas);
                    }
                    bitmapCanvas.drawBitmap(keycloakBitmap, size - keycloakSize, 0, null);
                }

                bitmap = localBitmap;
            }

            canvas.drawBitmap(bitmap, left, top, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Bitmap getAdaptiveBitmap() {
        int size = getContext().getResources().getDimensionPixelSize(R.dimen.shortcut_icon_size);
        int innerSize = getContext().getResources().getDimensionPixelSize(R.dimen.inner_shortcut_icon_size);
        int padding = (size-innerSize)/2;
        setSize(innerSize, innerSize);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (backgroundPaint != null) {
            canvas.drawPaint(backgroundPaint);
        } else {
            Paint blackBackgroundPaint = new Paint();
            blackBackgroundPaint.setColor(Color.BLACK);
            canvas.drawPaint(blackBackgroundPaint);
        }

        if (photoUrl != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photoUrl, options);
            int bitmapSize = Math.min(options.outWidth, options.outHeight);
            if (bitmapSize != -1) {
                int subSampling = bitmapSize / innerSize;
                options = new BitmapFactory.Options();
                options.inSampleSize = subSampling;
                Bitmap squareBitmap = BitmapFactory.decodeFile(photoUrl, options);
                if (squareBitmap != null) {
                    try {
                        ExifInterface exifInterface = new ExifInterface(photoUrl);
                        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        squareBitmap = PreviewUtils.rotateBitmap(squareBitmap, orientation);
                    } catch (IOException e) {
                        // do nothing --> no rotation
                    }
                    if (locked || inactive) {
                        ColorMatrix colorMatrix = new ColorMatrix();
                        colorMatrix.setSaturation(.5f);
                        Paint matrixPaint = new Paint();
                        matrixPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                        canvas.drawBitmap(squareBitmap, null, new Rect(padding, padding, innerSize + padding, innerSize + padding), matrixPaint);
                    } else {
                        canvas.drawBitmap(squareBitmap, null, new Rect(padding, padding, innerSize + padding, innerSize + padding), null);
                    }
                }
            }
        } else {
            if (overlayBitmap != null) {
                canvas.drawBitmap(overlayBitmap, padding + insideX, padding + insideY, insidePaint);
            } else {
                canvas.drawText(initial, padding + insideX, padding + insideY, insidePaint);
            }

            if (inactive) {
                int blockedSize = (int) (.8f*this.size);
                Bitmap blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, Bitmap.Config.ARGB_8888);
                Canvas blockedCanvas = new Canvas(blockedBitmap);
                Drawable blockedDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_block_outlined, null);
                if (blockedDrawable != null) {
                    blockedDrawable.setBounds(0, 0, blockedSize, blockedSize);
                    blockedDrawable.draw(blockedCanvas);
                }
                canvas.drawBitmap(blockedBitmap, (this.size-blockedSize)/2f,  (this.size-blockedSize)/2f, null);
            }
        }
        return bitmap;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
