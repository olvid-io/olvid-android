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

package io.olvid.messenger.owneddetails;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.olvid.messenger.customClasses.PreviewUtils;


public class SelectDetailsPhotoViewModel extends ViewModel {
    private Uri photoUri;
    private int scaled;
    private Bitmap fullBitmap;
    private final MutableLiveData<Bitmap> photoBitmap = new MutableLiveData<>();

    public static final int MAX_BITMAP_SIZE = 100 * 1024 * 1024;


    private RectF bitmapZone = new RectF();
    private int brightness = 0; // between -255 and 255
    private float contrast = 1; // between e^-2 and e^2
    private float saturation = 1; // between 0 and 4
    private int temperature = 0; // between -57 and 57
    private int rotation = 0; // between 0 and 3 (90° CW rotation steps)

    public LiveData<Bitmap> getPhotoBitmap() {
        return photoBitmap;
    }

    public void setPhotoUri(ContentResolver contentResolver, Uri photoUri) throws IOException {
        if (!photoUri.equals(this.photoUri)) {
            this.photoUri = photoUri;
            this.scaled = 1;
            this.rotation = 0;
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try {
                InputStream is = contentResolver.openInputStream(photoUri);
                if (is != null) {
                    ExifInterface exifInterface = new ExifInterface(is);
                    orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Bitmap bitmap = MediaStore.Images.Media.getBitmap(contentResolver, photoUri);
            if (bitmap == null) {
                return;
            }
            bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);
            fullBitmap = bitmap;

            if (bitmap.getByteCount() > MAX_BITMAP_SIZE) {
                scaled = (int) Math.sqrt((double) bitmap.getByteCount()/MAX_BITMAP_SIZE) + 1;
                bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth()/scaled, bitmap.getHeight()/scaled, false);
            }

            this.photoBitmap.postValue(bitmap);
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w > h) {
                bitmapZone = new RectF((w-h)/2f, 0, (w+h)/2f, h);
            } else {
                bitmapZone = new RectF(0, (h-w)/2f, w, (h+w)/2f);
            }
        }
    }

    public Bitmap getFullBitmap() {
        return fullBitmap;
    }

    public int getScaled() {
        return scaled;
    }

    public void resetBitmapZone() {
        Bitmap bitmap = photoBitmap.getValue();
        // reset any rotation
        if (bitmap != fullBitmap) {
            fullBitmap = PreviewUtils.rotateBitmap(fullBitmap, orientations[(4-rotation)&3]);
            bitmap = PreviewUtils.rotateBitmap(bitmap, orientations[(4 - rotation) & 3]);
        } else {
            fullBitmap = PreviewUtils.rotateBitmap(fullBitmap, orientations[(4-rotation)&3]);
            bitmap = fullBitmap;
        }
        rotation = 0;

        // recompute the fitted bitmapZone
        if (bitmap != null) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w > h) {
                bitmapZone = new RectF((w - h) / 2f, 0, (w + h) / 2f, h);
            } else {
                bitmapZone = new RectF(0, (h - w) / 2f, w, (h + w) / 2f);
            }
        }

        // update the bitmap (potentially rotated)
        photoBitmap.postValue(bitmap);
    }

    public RectF getBitmapZone() {
        return bitmapZone;
    }

    public void setBitmapZone(RectF bitmapZone) {
        this.bitmapZone.set(bitmapZone);
    }

    public void rotate90() {
        this.rotation = (this.rotation + 1) & 0x3;
        if (fullBitmap == null || photoBitmap.getValue() == null) {
            return;
        }

        Bitmap bitmap = photoBitmap.getValue();
        //noinspection SuspiciousNameCombination
        bitmapZone.set(bitmap.getHeight() - bitmapZone.bottom, bitmapZone.left, bitmap.getHeight() - bitmapZone.top, bitmapZone.right);
        if (!Objects.equals(photoBitmap.getValue(), fullBitmap)) {
            fullBitmap = PreviewUtils.rotateBitmap(fullBitmap, ExifInterface.ORIENTATION_ROTATE_90);
            bitmap = PreviewUtils.rotateBitmap(photoBitmap.getValue(), ExifInterface.ORIENTATION_ROTATE_90);
            photoBitmap.postValue(bitmap);
        } else {
            fullBitmap = PreviewUtils.rotateBitmap(fullBitmap, ExifInterface.ORIENTATION_ROTATE_90);
            photoBitmap.postValue(fullBitmap);
        }
    }



    public void setBrightness(int brightness) {
        this.brightness = brightness;
    }

    public void setContrast(int contrast) {
        this.contrast = (float) Math.exp(contrast/128d);
    }

    public void setSaturation(int saturation) {
        if (saturation < 0) {
            this.saturation = (saturation + 255f) / 255f;
        } else if (saturation < 128) {
            this.saturation = 1 + saturation / 128f;
        } else {
            this.saturation = 2 + (saturation - 128f) / 64f;
        }
    }

    public void setTemperature(int temperature) {
        this.temperature = (int) (temperature * 57f / 255f);
    }

    public ColorMatrixColorFilter getColorMatrix() {
        float translate = brightness + (-.5f * contrast + .5f) * 255f;
        ColorMatrix colorMatrix = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        });

        ColorMatrix saturationMatrix = new ColorMatrix();
        saturationMatrix.setSaturation(saturation);
        colorMatrix.postConcat(saturationMatrix);

        colorMatrix.postConcat(getTemperatureColorMatrix(temperature));

        return new ColorMatrixColorFilter(colorMatrix);
    }

    private static final int[] orientations = new int[]{ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_ROTATE_270};

    public static final float[][] TEMPERATURES = new float[][] {
            {1.0000f, 0.0337f, 0.0000f},
            {1.0000f, 0.0592f, 0.0000f},
            {1.0000f, 0.0846f, 0.0000f},
            {1.0000f, 0.1096f, 0.0000f},
            {1.0000f, 0.1341f, 0.0000f},
            {1.0000f, 0.1578f, 0.0000f},
            {1.0000f, 0.1806f, 0.0000f},
            {1.0000f, 0.2025f, 0.0000f},
            {1.0000f, 0.2235f, 0.0000f},
            {1.0000f, 0.2434f, 0.0000f},
            {1.0000f, 0.2647f, 0.0033f},
            {1.0000f, 0.2889f, 0.0120f},
            {1.0000f, 0.3126f, 0.0219f},
            {1.0000f, 0.3360f, 0.0331f},
            {1.0000f, 0.3589f, 0.0454f},
            {1.0000f, 0.3814f, 0.0588f},
            {1.0000f, 0.4034f, 0.0734f},
            {1.0000f, 0.4250f, 0.0889f},
            {1.0000f, 0.4461f, 0.1054f},
            {1.0000f, 0.4668f, 0.1229f},
            {1.0000f, 0.4870f, 0.1411f},
            {1.0000f, 0.5067f, 0.1602f},
            {1.0000f, 0.5259f, 0.1800f},
            {1.0000f, 0.5447f, 0.2005f},
            {1.0000f, 0.5630f, 0.2216f},
            {1.0000f, 0.5809f, 0.2433f},
            {1.0000f, 0.5983f, 0.2655f},
            {1.0000f, 0.6153f, 0.2881f},
            {1.0000f, 0.6318f, 0.3112f},
            {1.0000f, 0.6480f, 0.3346f},
            {1.0000f, 0.6636f, 0.3583f},
            {1.0000f, 0.6789f, 0.3823f},
            {1.0000f, 0.6938f, 0.4066f},
            {1.0000f, 0.7083f, 0.4310f},
            {1.0000f, 0.7223f, 0.4556f},
            {1.0000f, 0.7360f, 0.4803f},
            {1.0000f, 0.7494f, 0.5051f},
            {1.0000f, 0.7623f, 0.5299f},
            {1.0000f, 0.7750f, 0.5548f},
            {1.0000f, 0.7872f, 0.5797f},
            {1.0000f, 0.7992f, 0.6045f},
            {1.0000f, 0.8108f, 0.6293f},
            {1.0000f, 0.8221f, 0.6541f},
            {1.0000f, 0.8330f, 0.6787f},
            {1.0000f, 0.8437f, 0.7032f},
            {1.0000f, 0.8541f, 0.7277f},
            {1.0000f, 0.8642f, 0.7519f},
            {1.0000f, 0.8740f, 0.7760f},
            {1.0000f, 0.8836f, 0.8000f},
            {1.0000f, 0.8929f, 0.8238f},
            {1.0000f, 0.9019f, 0.8473f},
            {1.0000f, 0.9107f, 0.8707f},
            {1.0000f, 0.9193f, 0.8939f},
            {1.0000f, 0.9276f, 0.9168f},
            {1.0000f, 0.9357f, 0.9396f},
            {1.0000f, 0.9436f, 0.9621f},
            {1.0000f, 0.9513f, 0.9844f},
            {1.0000f, 1.0000f, 1.0000f},
            {0.9937f, 0.9526f, 1.0000f},
            {0.9726f, 0.9395f, 1.0000f},
            {0.9526f, 0.9270f, 1.0000f},
            {0.9337f, 0.9150f, 1.0000f},
            {0.9157f, 0.9035f, 1.0000f},
            {0.8986f, 0.8925f, 1.0000f},
            {0.8823f, 0.8819f, 1.0000f},
            {0.8668f, 0.8718f, 1.0000f},
            {0.8520f, 0.8621f, 1.0000f},
            {0.8379f, 0.8527f, 1.0000f},
            {0.8244f, 0.8437f, 1.0000f},
            {0.8115f, 0.8351f, 1.0000f},
            {0.7992f, 0.8268f, 1.0000f},
            {0.7874f, 0.8187f, 1.0000f},
            {0.7761f, 0.8110f, 1.0000f},
            {0.7652f, 0.8035f, 1.0000f},
            {0.7548f, 0.7963f, 1.0000f},
            {0.7449f, 0.7894f, 1.0000f},
            {0.7353f, 0.7827f, 1.0000f},
            {0.7260f, 0.7762f, 1.0000f},
            {0.7172f, 0.7699f, 1.0000f},
            {0.7086f, 0.7638f, 1.0000f},
            {0.7004f, 0.7579f, 1.0000f},
            {0.6925f, 0.7522f, 1.0000f},
            {0.6774f, 0.7414f, 1.0000f},
            {0.6635f, 0.7311f, 1.0000f},
            {0.6504f, 0.7215f, 1.0000f},
            {0.6382f, 0.7124f, 1.0000f},
            {0.6268f, 0.7039f, 1.0000f},
            {0.6161f, 0.6958f, 1.0000f},
            {0.6060f, 0.6881f, 1.0000f},
            {0.5965f, 0.6808f, 1.0000f},
            {0.5875f, 0.6739f, 1.0000f},
            {0.5791f, 0.6674f, 1.0000f},
            {0.5673f, 0.6581f, 1.0000f},
            {0.5564f, 0.6495f, 1.0000f},
            {0.5431f, 0.6389f, 1.0000f},
            {0.5340f, 0.6316f, 1.0000f},
            {0.5256f, 0.6247f, 1.0000f},
            {0.5152f, 0.6162f, 1.0000f},
            {0.5035f, 0.6065f, 1.0000f},
            {0.4930f, 0.5978f, 1.0000f},
            {0.4835f, 0.5898f, 1.0000f},
            {0.4749f, 0.5824f, 1.0000f},
            {0.4671f, 0.5757f, 1.0000f},
            {0.4599f, 0.5696f, 1.0000f},
            {0.4474f, 0.5586f, 1.0000f},
            {0.4367f, 0.5492f, 1.0000f},
            {0.4275f, 0.5410f, 1.0000f},
            {0.4196f, 0.5339f, 1.0000f},
            {0.4064f, 0.5219f, 1.0000f},
            {0.3961f, 0.5123f, 1.0000f},
            {0.3841f, 0.5012f, 1.0000f},
            {0.3751f, 0.4926f, 1.0000f},
            {0.3680f, 0.4858f, 1.0000f},
            {0.3624f, 0.4804f, 1.0000f},
            {0.3563f, 0.4745f, 1.0000f}};

    private static ColorMatrix getTemperatureColorMatrix(int temperature) {
        if (temperature < -57) {
            temperature = -57;
        } else if (temperature > 57) {
            temperature = 57;
        }
        float[] t = TEMPERATURES[temperature+57];
        float c = 3 / (t[0] + t[1] + t[2]);

        return new ColorMatrix(new float[]{
                c*t[0], 0, 0, 0, 0,
                0, c*t[1], 0, 0, 0,
                0, 0, c*t[2], 0, 0,
                0, 0, 0, 1, 0
        });
    }
}
