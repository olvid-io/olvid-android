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
package io.olvid.messenger.owneddetails

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Matrix.ScaleToFit.CENTER
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnTouchListener
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class SelectDetailsPhotoActivity : LockableActivity(), OnTouchListener, OnClickListener,
    OnSeekBarChangeListener {
    private val viewModel: SelectDetailsPhotoViewModel by viewModels()

    private var photoImageView: ImageView? = null
    private var brightnessSeekBar: SeekBar? = null
    private var contrastSeekBar: SeekBar? = null
    private var saturationSeekBar: SeekBar? = null
    private var temperatureSeekBar: SeekBar? = null

    private val currentMatrix = Matrix()
    private var overlayRect = RectF()
    private var insetsRect = RectF()

    private val startPoint = PointF()
    private val bitmapMiddlePoint = PointF()
    private var initialDist = 0f
    private val initialBitmapZone = RectF()
    private val newBitmapZone = RectF()
    private var initialScale = 0f
    private var bitmapWidth = 1
    private var bitmapHeight = 1

    override fun attachBaseContext(baseContext: Context) {
        disableScaling = true
        super.attachBaseContext(baseContext)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO

        setContentView(R.layout.activity_select_details_photo)
        val window = window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
            WindowCompat.getInsetsController(
                getWindow(),
                getWindow().decorView
            ).isAppearanceLightNavigationBars =
                false
            WindowCompat.getInsetsController(
                getWindow(),
                getWindow().decorView
            ).isAppearanceLightStatusBars =
                false
        }
        val root = findViewById<ConstraintLayout>(R.id.root_constraint_layout)
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets: WindowInsetsCompat ->
                val insets =
                    windowInsets.getInsets(Type.systemBars() or Type.ime() or Type.displayCutout())
                insetsRect = RectF(
                    insets.left.toFloat(),
                    insets.top.toFloat(),
                    insets.right.toFloat(),
                    insets.bottom.toFloat()
                )
                root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }
        viewModel.photoBitmap.observe(
            this
        ) { photoBitmap: Bitmap? -> this.resetImage(photoBitmap) }

        photoImageView = findViewById(R.id.photo_image_view)
        photoImageView?.setOnTouchListener(this)

        val backButton = findViewById<ImageView>(R.id.button_back)
        backButton.setOnClickListener(this)
        val resetButton = findViewById<ImageView>(R.id.button_reset)
        resetButton.setOnClickListener(this)
        val okButton = findViewById<ImageView>(R.id.button_accept)
        okButton.setOnClickListener(this)
        val rotateButton = findViewById<ImageView>(R.id.button_rotate)
        rotateButton.setOnClickListener(this)
        brightnessSeekBar = findViewById(R.id.brightness_seekbar)
        brightnessSeekBar?.setOnSeekBarChangeListener(this)
        contrastSeekBar = findViewById(R.id.contrast_seekbar)
        contrastSeekBar?.setOnSeekBarChangeListener(this)
        temperatureSeekBar = findViewById(R.id.temperature_seekbar)
        temperatureSeekBar?.setOnSeekBarChangeListener(this)
        saturationSeekBar = findViewById(R.id.saturation_seekbar)
        saturationSeekBar?.setOnSeekBarChangeListener(this)
        val overlay = findViewById<ImageView>(R.id.overlay_image_view)
        overlay.addOnLayoutChangeListener { _: View?, left: Int, top: Int, right: Int, bottom: Int, _: Int, _: Int, _: Int, _: Int ->
            overlayRect = RectF(
                left - insetsRect.left,
                top - insetsRect.top,
                right - insetsRect.left,
                bottom - insetsRect.top
            )
            fitBitmapZoneToOverlay()
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.data == null) {
            onBackPressed()
            return
        }

        try {
            viewModel.setPhotoUri(this.contentResolver, intent.data!!)
        } catch (_: Exception) {
            onBackPressed()
        }
    }

    private fun resetImage(photoBitmap: Bitmap?) {
        if (photoBitmap != null) {
            bitmapWidth = photoBitmap.width
            bitmapHeight = photoBitmap.height
            photoImageView?.setImageBitmap(photoBitmap)
            fitBitmapZoneToOverlay()
        }
    }

    private fun fitBitmapZoneToOverlay() {
        val bitmapZone = viewModel.bitmapZone
        currentMatrix.setRectToRect(bitmapZone, overlayRect, CENTER)
        photoImageView?.imageMatrix = currentMatrix
    }

    private var mode = NONE

    private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val x = x1 - x0
        val y = y1 - y0
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun bitmapMiddle(
        point: PointF,
        initialBitmapZone: RectF,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ) {
        val x = (x0 + x1) / 2
        val y = (y0 + y1) / 2
        val alphaX = (x - overlayRect.left) / (overlayRect.right - overlayRect.left)
        val alphaY = (y - overlayRect.top) / (overlayRect.bottom - overlayRect.top)
        point[alphaX * (initialBitmapZone.right - initialBitmapZone.left) + initialBitmapZone.left] =
            alphaY * (initialBitmapZone.bottom - initialBitmapZone.top) + initialBitmapZone.top
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialBitmapZone.set(viewModel.bitmapZone)
                initialScale =
                    (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left)
                startPoint[event.x] = event.y
                mode = DRAG
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > 2) {
                    mode = NONE
                } else {
                    initialDist =
                        distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    if (initialDist > 10f) {
                        initialBitmapZone.set(viewModel.bitmapZone)
                        initialScale =
                            (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left)
                        bitmapMiddle(
                            bitmapMiddlePoint,
                            initialBitmapZone,
                            event.getX(0),
                            event.getY(0),
                            event.getX(1),
                            event.getY(1)
                        )
                        mode = ZOOM
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                mode = NONE
            }

            MotionEvent.ACTION_POINTER_UP -> {
                when (event.pointerCount) {
                    3 -> {
                        val x0: Float
                        val x1: Float
                        val y0: Float
                        val y1: Float
                        when (event.actionIndex) {
                            0 -> {
                                x0 = event.getX(1)
                                x1 = event.getX(2)
                                y0 = event.getY(1)
                                y1 = event.getY(2)
                            }

                            1 -> {
                                x0 = event.getX(0)
                                x1 = event.getX(2)
                                y0 = event.getY(0)
                                y1 = event.getY(2)
                            }

                            2 -> {
                                x0 = event.getX(0)
                                x1 = event.getX(1)
                                y0 = event.getY(0)
                                y1 = event.getY(1)
                            }

                            else -> {
                                x0 = event.getX(0)
                                x1 = event.getX(1)
                                y0 = event.getY(0)
                                y1 = event.getY(1)
                            }
                        }
                        initialDist = distance(x0, y0, x1, y1)
                        if (initialDist > 10f) {
                            initialBitmapZone.set(viewModel.bitmapZone)
                            initialScale =
                                (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left)
                            bitmapMiddle(bitmapMiddlePoint, initialBitmapZone, x0, y0, x1, y1)
                            mode = ZOOM
                        }
                    }

                    2 -> {
                        initialBitmapZone.set(viewModel.bitmapZone)
                        initialScale =
                            (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left)
                        if (event.actionIndex == 0) {
                            startPoint[event.getX(1)] = event.getY(1)
                        } else {
                            startPoint[event.getX(0)] = event.getY(0)
                        }
                        mode = DRAG
                    }

                    else -> {
                        mode = NONE
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    newBitmapZone[initialBitmapZone.left - (event.x - startPoint.x) / initialScale, initialBitmapZone.top - (event.y - startPoint.y) / initialScale, initialBitmapZone.right - (event.x - startPoint.x) / initialScale] =
                        initialBitmapZone.bottom - (event.y - startPoint.y) / initialScale
                    if (newBitmapZone.left < 0) {
                        newBitmapZone.right -= newBitmapZone.left
                        newBitmapZone.left = 0f
                    } else if (newBitmapZone.right > bitmapWidth) {
                        newBitmapZone.left -= newBitmapZone.right - bitmapWidth
                        newBitmapZone.right = bitmapWidth.toFloat()
                    }
                    if (newBitmapZone.top < 0) {
                        newBitmapZone.bottom -= newBitmapZone.top
                        newBitmapZone.top = 0f
                    } else if (newBitmapZone.bottom > bitmapHeight) {
                        newBitmapZone.top -= newBitmapZone.bottom - bitmapHeight
                        newBitmapZone.bottom = bitmapHeight.toFloat()
                    }
                    viewModel.bitmapZone = newBitmapZone
                    fitBitmapZoneToOverlay()
                } else if (mode == ZOOM) {
                    val newDist =
                        distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    if (newDist > 10f) {
                        val ratio = getRatio(newDist)

                        newBitmapZone[(initialBitmapZone.left - bitmapMiddlePoint.x) * ratio + bitmapMiddlePoint.x, (initialBitmapZone.top - bitmapMiddlePoint.y) * ratio + bitmapMiddlePoint.y, (initialBitmapZone.right - bitmapMiddlePoint.x) * ratio + bitmapMiddlePoint.x] =
                            (initialBitmapZone.bottom - bitmapMiddlePoint.y) * ratio + bitmapMiddlePoint.y
                        if (newBitmapZone.left < 0) {
                            newBitmapZone.right -= newBitmapZone.left
                            newBitmapZone.left = 0f
                        } else if (newBitmapZone.right > bitmapWidth) {
                            newBitmapZone.left -= newBitmapZone.right - bitmapWidth
                            newBitmapZone.right = bitmapWidth.toFloat()
                        }
                        if (newBitmapZone.top < 0) {
                            newBitmapZone.bottom -= newBitmapZone.top
                            newBitmapZone.top = 0f
                        } else if (newBitmapZone.bottom > bitmapHeight) {
                            newBitmapZone.top -= newBitmapZone.bottom - bitmapHeight
                            newBitmapZone.bottom = bitmapHeight.toFloat()
                        }
                        viewModel.bitmapZone = newBitmapZone
                        fitBitmapZoneToOverlay()
                    }

                    fitBitmapZoneToOverlay()
                }
            }
        }
        return true
    }

    private fun getRatio(newDist: Float): Float {
        var newScale = initialScale * newDist / initialDist
        val zoneWidth = (overlayRect.right - overlayRect.left) / newScale
        if (zoneWidth > bitmapWidth || zoneWidth > bitmapHeight) {
            newScale = if (bitmapWidth < bitmapHeight) {
                (overlayRect.right - overlayRect.left) / bitmapWidth
            } else {
                (overlayRect.right - overlayRect.left) / bitmapHeight
            }
        }
        return initialScale / newScale
    }


    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.button_back) {
            onBackPressed()
        } else if (id == R.id.button_rotate) {
            viewModel.rotate90()
        } else if (id == R.id.button_reset) {
            viewModel.resetBitmapZone()
            viewModel.setBrightness(0)
            viewModel.setContrast(0)
            viewModel.setSaturation(0)
            viewModel.setTemperature(0)
            brightnessSeekBar?.progress = 255
            contrastSeekBar?.progress = 255
            saturationSeekBar?.progress = 255
            temperatureSeekBar?.progress = 255
            photoImageView?.colorFilter = viewModel.colorMatrix
            fitBitmapZoneToOverlay()
        } else if (id == R.id.button_accept) { // extract the bitmap and return it
            val bitmap = viewModel.fullBitmap
            val scaled = viewModel.scaled
            if (bitmap != null) {
                val bitmapZone = Rect()
                val unscaledZone = viewModel.bitmapZone
                RectF(
                    unscaledZone.left * scaled,
                    unscaledZone.top * scaled,
                    unscaledZone.right * scaled,
                    unscaledZone.bottom * scaled
                ).roundOut(bitmapZone)

                var size = bitmapZone.right - bitmapZone.left
                if (size > 1080) {
                    size = 1080
                }
                val cropped = Bitmap.createBitmap(size, size, ARGB_8888)
                val canvas = Canvas(cropped)
                val paint = Paint()
                paint.setColorFilter(viewModel.colorMatrix)
                canvas.drawBitmap(bitmap, bitmapZone, Rect(0, 0, size, size), paint)

                val photoDir = File(cacheDir, App.CAMERA_PICTURE_FOLDER)
                val photoFile = File(
                    photoDir,
                    SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(
                        Date()
                    ) + "_cropped.jpg"
                )
                try {
                    FileOutputStream(photoFile).use { out ->
                        photoDir.mkdirs()
                        cropped.compress(JPEG, 75, out)
                        out.flush()
                        val returnIntent = Intent()
                        returnIntent.putExtra(
                            CROPPED_JPEG_RETURN_INTENT_EXTRA,
                            photoFile.absolutePath
                        )
                        setResult(RESULT_OK, returnIntent)
                        finish()
                    }
                } catch (e: Exception) {
                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT)
                    e.printStackTrace()
                }
            } else {
                App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT)
            }
        }
    }

    override fun onBackPressed() {
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        when (seekBar.id) {
            R.id.brightness_seekbar -> {
                viewModel.setBrightness(progress - 255)
            }

            R.id.contrast_seekbar -> {
                viewModel.setContrast(progress - 255)
            }

            R.id.temperature_seekbar -> {
                viewModel.setTemperature(progress - 255)
            }

            R.id.saturation_seekbar -> {
                viewModel.setSaturation(progress - 255)
            }
        }
        photoImageView?.colorFilter = viewModel.colorMatrix
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        // do nothing
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        // do nothing
    }

    companion object {
        const val CROPPED_JPEG_RETURN_INTENT_EXTRA: String = "cropped_jpeg"

        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
