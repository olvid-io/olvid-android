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
package io.olvid.messenger.owneddetails

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore.Images.Media
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.messenger.customClasses.PreviewUtils
import java.io.IOException
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SelectDetailsPhotoViewModel : ViewModel() {
    private var photoUri: Uri? = null
    var scaled: Int = 0
        private set
    var fullBitmap: Bitmap? = null
        private set
    val photoBitmap = MutableLiveData<Bitmap?>()

    var bitmapZone = RectF()
    private var brightness = 0 // between -255 and 255
    private var contrast = 1f // between e^-2 and e^2
    private var saturation = 1f // between 0 and 4
    private var temperature = 0 // between -57 and 57
    private var rotation = 0 // between 0 and 3 (90° CW rotation steps)

    fun getPhotoBitmap(): LiveData<Bitmap?> {
        return photoBitmap
    }

    @Throws(IOException::class)
    fun setPhotoUri(contentResolver: ContentResolver, photoUri: Uri) {
        if (photoUri != this.photoUri) {
            this.photoUri = photoUri
            this.scaled = 1
            this.rotation = 0
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                val `is` = contentResolver.openInputStream(photoUri)
                if (`is` != null) {
                    val exifInterface = ExifInterface(`is`)
                    orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var bitmap: Bitmap =
                Media.getBitmap(contentResolver, photoUri) ?: return
            bitmap = PreviewUtils.rotateBitmap(bitmap, orientation)
            fullBitmap = bitmap

            if (bitmap.byteCount > MAX_BITMAP_SIZE) {
                scaled = sqrt(bitmap.byteCount.toDouble() / MAX_BITMAP_SIZE).roundToInt() + 1
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    bitmap.width / scaled,
                    bitmap.height / scaled,
                    false
                )
            }

            photoBitmap.postValue(bitmap)
            val w = bitmap.width
            val h = bitmap.height
            bitmapZone = if (w > h) {
                RectF((w - h) / 2f, 0f, (w + h) / 2f, h.toFloat())
            } else {
                RectF(0f, (h - w) / 2f, w.toFloat(), (h + w) / 2f)
            }
        }
    }

    fun resetBitmapZone() {
        var bitmap = photoBitmap.value
        // reset any rotation
        if (bitmap != fullBitmap) {
            fullBitmap = PreviewUtils.rotateBitmap(
                fullBitmap,
                orientations[(4 - rotation) and 3]
            )
            bitmap = PreviewUtils.rotateBitmap(
                bitmap,
                orientations[(4 - rotation) and 3]
            )
        } else {
            fullBitmap = PreviewUtils.rotateBitmap(
                fullBitmap,
                orientations[(4 - rotation) and 3]
            )
            bitmap = fullBitmap
        }
        rotation = 0

        // recompute the fitted bitmapZone
        if (bitmap != null) {
            val w = bitmap.width
            val h = bitmap.height
            bitmapZone = if (w > h) {
                RectF((w - h) / 2f, 0f, (w + h) / 2f, h.toFloat())
            } else {
                RectF(0f, (h - w) / 2f, w.toFloat(), (h + w) / 2f)
            }
        }

        // update the bitmap (potentially rotated)
        photoBitmap.postValue(bitmap)
    }

    fun rotate90() {
        this.rotation = (this.rotation + 1) and 0x3
        if (fullBitmap == null || photoBitmap.value == null) {
            return
        }

        photoBitmap.value?.let { bitmap ->
            bitmapZone[bitmap.height - bitmapZone.bottom, bitmapZone.left, bitmap.height - bitmapZone.top] =
                bitmapZone.right
            if (photoBitmap.value != fullBitmap) {
                fullBitmap =
                    PreviewUtils.rotateBitmap(fullBitmap, ExifInterface.ORIENTATION_ROTATE_90)
                photoBitmap.postValue(
                    PreviewUtils.rotateBitmap(
                        bitmap,
                        ExifInterface.ORIENTATION_ROTATE_90
                    )
                )
            } else {
                fullBitmap =
                    PreviewUtils.rotateBitmap(fullBitmap, ExifInterface.ORIENTATION_ROTATE_90)
                photoBitmap.postValue(fullBitmap)
            }
        }
    }


    fun setBrightness(brightness: Int) {
        this.brightness = brightness
    }

    fun setContrast(contrast: Int) {
        this.contrast = exp(contrast / 128.0).toFloat()
    }

    fun setSaturation(saturation: Int) {
        if (saturation < 0) {
            this.saturation = (saturation + 255f) / 255f
        } else if (saturation < 128) {
            this.saturation = 1 + saturation / 128f
        } else {
            this.saturation = 2 + (saturation - 128f) / 64f
        }
    }

    fun setTemperature(temperature: Int) {
        this.temperature = (temperature * 57f / 255f).toInt()
    }

    val colorMatrix: ColorMatrixColorFilter
        get() {
            val translate = brightness + (-.5f * contrast + .5f) * 255f
            val colorMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )

            val saturationMatrix = ColorMatrix()
            saturationMatrix.setSaturation(saturation)
            colorMatrix.postConcat(saturationMatrix)

            colorMatrix.postConcat(
                getTemperatureColorMatrix(
                    temperature
                )
            )

            return ColorMatrixColorFilter(colorMatrix)
        }

    companion object {
        const val MAX_BITMAP_SIZE: Int = 100 * 1024 * 1024


        private val orientations = intArrayOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_ROTATE_270
        )

        private val TEMPERATURES: Array<FloatArray> = arrayOf(
            floatArrayOf(1.0000f, 0.0337f, 0.0000f),
            floatArrayOf(1.0000f, 0.0592f, 0.0000f),
            floatArrayOf(1.0000f, 0.0846f, 0.0000f),
            floatArrayOf(1.0000f, 0.1096f, 0.0000f),
            floatArrayOf(1.0000f, 0.1341f, 0.0000f),
            floatArrayOf(1.0000f, 0.1578f, 0.0000f),
            floatArrayOf(1.0000f, 0.1806f, 0.0000f),
            floatArrayOf(1.0000f, 0.2025f, 0.0000f),
            floatArrayOf(1.0000f, 0.2235f, 0.0000f),
            floatArrayOf(1.0000f, 0.2434f, 0.0000f),
            floatArrayOf(1.0000f, 0.2647f, 0.0033f),
            floatArrayOf(1.0000f, 0.2889f, 0.0120f),
            floatArrayOf(1.0000f, 0.3126f, 0.0219f),
            floatArrayOf(1.0000f, 0.3360f, 0.0331f),
            floatArrayOf(1.0000f, 0.3589f, 0.0454f),
            floatArrayOf(1.0000f, 0.3814f, 0.0588f),
            floatArrayOf(1.0000f, 0.4034f, 0.0734f),
            floatArrayOf(1.0000f, 0.4250f, 0.0889f),
            floatArrayOf(1.0000f, 0.4461f, 0.1054f),
            floatArrayOf(1.0000f, 0.4668f, 0.1229f),
            floatArrayOf(1.0000f, 0.4870f, 0.1411f),
            floatArrayOf(1.0000f, 0.5067f, 0.1602f),
            floatArrayOf(1.0000f, 0.5259f, 0.1800f),
            floatArrayOf(1.0000f, 0.5447f, 0.2005f),
            floatArrayOf(1.0000f, 0.5630f, 0.2216f),
            floatArrayOf(1.0000f, 0.5809f, 0.2433f),
            floatArrayOf(1.0000f, 0.5983f, 0.2655f),
            floatArrayOf(1.0000f, 0.6153f, 0.2881f),
            floatArrayOf(1.0000f, 0.6318f, 0.3112f),
            floatArrayOf(1.0000f, 0.6480f, 0.3346f),
            floatArrayOf(1.0000f, 0.6636f, 0.3583f),
            floatArrayOf(1.0000f, 0.6789f, 0.3823f),
            floatArrayOf(1.0000f, 0.6938f, 0.4066f),
            floatArrayOf(1.0000f, 0.7083f, 0.4310f),
            floatArrayOf(1.0000f, 0.7223f, 0.4556f),
            floatArrayOf(1.0000f, 0.7360f, 0.4803f),
            floatArrayOf(1.0000f, 0.7494f, 0.5051f),
            floatArrayOf(1.0000f, 0.7623f, 0.5299f),
            floatArrayOf(1.0000f, 0.7750f, 0.5548f),
            floatArrayOf(1.0000f, 0.7872f, 0.5797f),
            floatArrayOf(1.0000f, 0.7992f, 0.6045f),
            floatArrayOf(1.0000f, 0.8108f, 0.6293f),
            floatArrayOf(1.0000f, 0.8221f, 0.6541f),
            floatArrayOf(1.0000f, 0.8330f, 0.6787f),
            floatArrayOf(1.0000f, 0.8437f, 0.7032f),
            floatArrayOf(1.0000f, 0.8541f, 0.7277f),
            floatArrayOf(1.0000f, 0.8642f, 0.7519f),
            floatArrayOf(1.0000f, 0.8740f, 0.7760f),
            floatArrayOf(1.0000f, 0.8836f, 0.8000f),
            floatArrayOf(1.0000f, 0.8929f, 0.8238f),
            floatArrayOf(1.0000f, 0.9019f, 0.8473f),
            floatArrayOf(1.0000f, 0.9107f, 0.8707f),
            floatArrayOf(1.0000f, 0.9193f, 0.8939f),
            floatArrayOf(1.0000f, 0.9276f, 0.9168f),
            floatArrayOf(1.0000f, 0.9357f, 0.9396f),
            floatArrayOf(1.0000f, 0.9436f, 0.9621f),
            floatArrayOf(1.0000f, 0.9513f, 0.9844f),
            floatArrayOf(1.0000f, 1.0000f, 1.0000f),
            floatArrayOf(0.9937f, 0.9526f, 1.0000f),
            floatArrayOf(0.9726f, 0.9395f, 1.0000f),
            floatArrayOf(0.9526f, 0.9270f, 1.0000f),
            floatArrayOf(0.9337f, 0.9150f, 1.0000f),
            floatArrayOf(0.9157f, 0.9035f, 1.0000f),
            floatArrayOf(0.8986f, 0.8925f, 1.0000f),
            floatArrayOf(0.8823f, 0.8819f, 1.0000f),
            floatArrayOf(0.8668f, 0.8718f, 1.0000f),
            floatArrayOf(0.8520f, 0.8621f, 1.0000f),
            floatArrayOf(0.8379f, 0.8527f, 1.0000f),
            floatArrayOf(0.8244f, 0.8437f, 1.0000f),
            floatArrayOf(0.8115f, 0.8351f, 1.0000f),
            floatArrayOf(0.7992f, 0.8268f, 1.0000f),
            floatArrayOf(0.7874f, 0.8187f, 1.0000f),
            floatArrayOf(0.7761f, 0.8110f, 1.0000f),
            floatArrayOf(0.7652f, 0.8035f, 1.0000f),
            floatArrayOf(0.7548f, 0.7963f, 1.0000f),
            floatArrayOf(0.7449f, 0.7894f, 1.0000f),
            floatArrayOf(0.7353f, 0.7827f, 1.0000f),
            floatArrayOf(0.7260f, 0.7762f, 1.0000f),
            floatArrayOf(0.7172f, 0.7699f, 1.0000f),
            floatArrayOf(0.7086f, 0.7638f, 1.0000f),
            floatArrayOf(0.7004f, 0.7579f, 1.0000f),
            floatArrayOf(0.6925f, 0.7522f, 1.0000f),
            floatArrayOf(0.6774f, 0.7414f, 1.0000f),
            floatArrayOf(0.6635f, 0.7311f, 1.0000f),
            floatArrayOf(0.6504f, 0.7215f, 1.0000f),
            floatArrayOf(0.6382f, 0.7124f, 1.0000f),
            floatArrayOf(0.6268f, 0.7039f, 1.0000f),
            floatArrayOf(0.6161f, 0.6958f, 1.0000f),
            floatArrayOf(0.6060f, 0.6881f, 1.0000f),
            floatArrayOf(0.5965f, 0.6808f, 1.0000f),
            floatArrayOf(0.5875f, 0.6739f, 1.0000f),
            floatArrayOf(0.5791f, 0.6674f, 1.0000f),
            floatArrayOf(0.5673f, 0.6581f, 1.0000f),
            floatArrayOf(0.5564f, 0.6495f, 1.0000f),
            floatArrayOf(0.5431f, 0.6389f, 1.0000f),
            floatArrayOf(0.5340f, 0.6316f, 1.0000f),
            floatArrayOf(0.5256f, 0.6247f, 1.0000f),
            floatArrayOf(0.5152f, 0.6162f, 1.0000f),
            floatArrayOf(0.5035f, 0.6065f, 1.0000f),
            floatArrayOf(0.4930f, 0.5978f, 1.0000f),
            floatArrayOf(0.4835f, 0.5898f, 1.0000f),
            floatArrayOf(0.4749f, 0.5824f, 1.0000f),
            floatArrayOf(0.4671f, 0.5757f, 1.0000f),
            floatArrayOf(0.4599f, 0.5696f, 1.0000f),
            floatArrayOf(0.4474f, 0.5586f, 1.0000f),
            floatArrayOf(0.4367f, 0.5492f, 1.0000f),
            floatArrayOf(0.4275f, 0.5410f, 1.0000f),
            floatArrayOf(0.4196f, 0.5339f, 1.0000f),
            floatArrayOf(0.4064f, 0.5219f, 1.0000f),
            floatArrayOf(0.3961f, 0.5123f, 1.0000f),
            floatArrayOf(0.3841f, 0.5012f, 1.0000f),
            floatArrayOf(0.3751f, 0.4926f, 1.0000f),
            floatArrayOf(0.3680f, 0.4858f, 1.0000f),
            floatArrayOf(0.3624f, 0.4804f, 1.0000f),
            floatArrayOf(0.3563f, 0.4745f, 1.0000f)
        )

        private fun getTemperatureColorMatrix(temperature: Int): ColorMatrix {
            var temperature = temperature
            if (temperature < -57) {
                temperature = -57
            } else if (temperature > 57) {
                temperature = 57
            }
            val t = TEMPERATURES[temperature + 57]
            val c = 3 / (t[0] + t[1] + t[2])

            return ColorMatrix(
                floatArrayOf(
                    c * t[0], 0f, 0f, 0f, 0f,
                    0f, c * t[1], 0f, 0f, 0f,
                    0f, 0f, c * t[2], 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
    }
}
