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
package io.olvid.messenger.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.toRect
import androidx.core.graphics.toRectF
import io.olvid.messenger.databases.entity.TextBlock
import io.olvid.messenger.gallery.GalleryImageView.Mode.DRAG
import io.olvid.messenger.gallery.GalleryImageView.Mode.NONE
import io.olvid.messenger.gallery.GalleryImageView.Mode.ZOOM
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt


class GalleryImageView : AppCompatImageView {
    enum class Mode {
        NONE,
        DRAG,
        ZOOM,
    }

    // drag and zoom control
    private var mode = NONE
    private val dragStartPoint = PointF()
    private val zoomBitmapMiddle = PointF()
    private var zoomInitialDist = 0f
    private val initialBitmapCenterPoint = PointF()
    private var initialBitmapScale = 0f
    private var flingAnimation: FlingAnimation? = null

    // TextBlocks
    private val paint: Paint by lazy {
        Paint().apply {
            color = Color.YELLOW
            alpha = 50
            isAntiAlias = true
        }
    }
    private val rectF: RectF by lazy { RectF() }
    private var textBlocks: List<TextBlock>? = null

    // image scale and offset
    private val matrix = Matrix()
    private var autoFit = true
    private val bitmapCenterPoint = PointF()
    private var bitmapScale =
        0f // with a bitmapScale of 3, 3 screen pixels are used to display 1 bitmap pixel
    private var bitmapWidth = 0
    private var bitmapHeight = 0

    private var draggable = false
    private var parentViewPagerUserInputController: ParentViewPagerUserInputController? = null
    var singleTapUpCallback: ((List<TextBlock>?) -> Unit)? = {}
    var postedSingleTapRunnable: Runnable? = null


    private val interpolator: Interpolator = AccelerateDecelerateInterpolator()

    // click / double click detection
    private var clicking = false
    private var doubleClicking = false
    private var doubleClickFirstTimestamp: Long = 0
    private val singleTapHandler = Handler(Looper.getMainLooper())

    private val gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (draggable) {
                flingAnimation?.stopped = true
                flingAnimation = FlingAnimation(velocityX, velocityY)
                postOnAnimation(flingAnimation)
                return true
            }
            return false
        }
    })

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        recomputeMatrix(forceAutoFit = false, maintainAutoFit = true, animate = false)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null) {
            bitmapWidth = drawable.intrinsicWidth
            bitmapHeight = drawable.intrinsicHeight
        } else {
            bitmapWidth = -1
            bitmapHeight = -1
        }
        recomputeMatrix(forceAutoFit = true, maintainAutoFit = false, animate = false)
    }

    fun setParentViewPagerUserInputController(parentViewPagerUserInputController: ParentViewPagerUserInputController?) {
        this.parentViewPagerUserInputController = parentViewPagerUserInputController
    }

    fun setTextBlocks(textBlocks: List<TextBlock>?) {
        this.textBlocks = textBlocks
    }


    private fun recomputeMatrix(forceAutoFit: Boolean, maintainAutoFit: Boolean, animate: Boolean) {
        if (bitmapWidth == -1 || bitmapHeight == -1) {
            draggable = false
            return
        }
        if (forceAutoFit || (maintainAutoFit && autoFit)) {
            autoFit = true
            bitmapCenterPoint[bitmapWidth / 2f] = bitmapHeight / 2f
            bitmapScale = min(
                (width.toFloat() / bitmapWidth).toDouble(),
                (height.toFloat() / bitmapHeight).toDouble()
            ).toFloat()
            draggable = false
        } else {
            /////////
            // MIN SCALE:
            // - for large images: fit screen
            // - for small ones: 1px for 1px
            val minScale = min(
                1.0,
                min(
                    (width.toFloat() / bitmapWidth).toDouble(),
                    (height.toFloat() / bitmapHeight).toDouble()
                )
            ).toFloat()
            if (bitmapScale < minScale) {
                bitmapScale = minScale
            }

            ////////
            // MIN BOUNDS:
            // - screen viewport never goes outside image
            // - for small images/on the small axis, center the viewport
            val minBitmapCenterX = min(
                (bitmapWidth / 2f).toDouble(),
                (width / 2f / bitmapScale).toDouble()
            ).toFloat()
            val minBitmapCenterY = min(
                (bitmapHeight / 2f).toDouble(),
                (height / 2f / bitmapScale).toDouble()
            ).toFloat()

            bitmapCenterPoint[max(
                min(
                    bitmapCenterPoint.x.toDouble(),
                    (bitmapWidth - minBitmapCenterX).toDouble()
                ), minBitmapCenterX.toDouble()
            ).toFloat()] = max(
                min(
                    bitmapCenterPoint.y.toDouble(),
                    (bitmapHeight - minBitmapCenterY).toDouble()
                ), minBitmapCenterY.toDouble()
            ).toFloat()

            draggable =
                (bitmapScale * bitmapWidth > width + 1) || (bitmapScale * bitmapHeight > height + 1)
        }

        matrix.reset()
        matrix.postTranslate(-bitmapCenterPoint.x, -bitmapCenterPoint.y)
        matrix.postScale(bitmapScale, bitmapScale)
        matrix.postTranslate(width / 2f, height / 2f)

        if (animate) {
            val oldMatrixValues = FloatArray(9)
            val newMatrixValues = FloatArray(9)
            val halfValues = FloatArray(9)
            halfValues[8] = 1f
            imageMatrix.getValues(oldMatrixValues)
            matrix.getValues(newMatrixValues)
            val startTime = System.currentTimeMillis()
            val duration: Long = 200
            postOnAnimation(object : Runnable {
                override fun run() {
                    val progress = (System.currentTimeMillis() - startTime).toFloat() / duration
                    if (progress > 1) {
                        matrix.setValues(newMatrixValues)
                    } else {
                        val ratio = interpolator.getInterpolation(progress)
                        halfValues[0] =
                            oldMatrixValues[0] + ratio * (newMatrixValues[0] - oldMatrixValues[0])
                        halfValues[2] =
                            oldMatrixValues[2] + ratio * (newMatrixValues[2] - oldMatrixValues[2])
                        halfValues[4] =
                            oldMatrixValues[4] + ratio * (newMatrixValues[4] - oldMatrixValues[4])
                        halfValues[5] =
                            oldMatrixValues[5] + ratio * (newMatrixValues[5] - oldMatrixValues[5])
                        matrix.setValues(halfValues)
                        postOnAnimation(this)
                    }
                    imageMatrix = matrix
                }
            })
        } else {
            imageMatrix = matrix
        }
    }

    private fun setMode(mode: Mode) {
        this.mode = mode
        parentViewPagerUserInputController?.apply {
            when (mode) {
                NONE -> setParentViewPagerUserInputEnabled(true)
                DRAG -> setParentViewPagerUserInputEnabled(!draggable)
                ZOOM -> {
                    autoFit = false
                    setParentViewPagerUserInputEnabled(false)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        textBlocks?.forEach {
            it.boundingBox?.let { boundingBox ->
                rectF[boundingBox.left.toFloat(), boundingBox.top.toFloat(), boundingBox.right.toFloat()] =
                    boundingBox.bottom.toFloat()
                matrix.mapRect(rectF)
                canvas.drawRect(rectF, paint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> {
                setMode(NONE)
            }

            MotionEvent.ACTION_UP -> {
                if (clicking
                    && event.eventTime - event.downTime < CLICK_DURATION && distance(
                        dragStartPoint.x,
                        dragStartPoint.y,
                        event.x,
                        event.y
                    ) < 10f
                ) {
                    // click detected
                    if (doubleClicking) {
                        // double click detected
                        if (autoFit) {
                            autoFit = false
                            bitmapCenterPoint[bitmapWidth / 2f + (event.x - width / 2f) / bitmapScale] =
                                bitmapHeight / 2f + (event.y - height / 2f) / bitmapScale
                            bitmapScale = 1f
                            recomputeMatrix(
                                forceAutoFit = false,
                                maintainAutoFit = false,
                                animate = true
                            )
                        } else {
                            recomputeMatrix(
                                forceAutoFit = true,
                                maintainAutoFit = false,
                                animate = true
                            )
                        }
                    } else {
                        val point = floatArrayOf(dragStartPoint.x, dragStartPoint.y)
                        val inverse = Matrix()
                        matrix.invert(inverse)
                        inverse.mapPoints(point)
                        textBlocks?.filter {
                            it.boundingBox?.contains(
                                point[0].roundToInt(),
                                point[1].roundToInt()
                            ) == true
                        }?.takeIf {
                            it.isNotEmpty()
                        }?.apply {
                            singleTapUpCallback?.let {
                                val selectedTextBlocks = emptyList<TextBlock>().toMutableList()
                                forEach {
                                    selectedTextBlocks.add(it.copy(boundingBox = it.boundingBox?.let {
                                        val dest = RectF()
                                        matrix.mapRect(dest, it.toRectF())
                                        dest.toRect()
                                    }))
                                }
                                // when clicking a block, no need to delay to detect double clicks
                                singleTapHandler.post { it.invoke(selectedTextBlocks) }
                            }
                        } ?: run {
                            // simple click --> start timer
                            singleTapUpCallback?.let {
                                postedSingleTapRunnable = Runnable {
                                    postedSingleTapRunnable = null
                                    it.invoke(null)
                                }
                                singleTapHandler.postDelayed(
                                    postedSingleTapRunnable!!,
                                    DOUBLE_CLICK_DURATION + event.downTime - event.eventTime
                                )
                            }
                        }
                        doubleClickFirstTimestamp = event.downTime
                    }
                }
                setMode(NONE)
            }

            MotionEvent.ACTION_DOWN -> {

                flingAnimation?.stopped = true
                flingAnimation = null

                clicking = true
                doubleClicking = event.eventTime - doubleClickFirstTimestamp < DOUBLE_CLICK_DURATION
                        && distance(dragStartPoint.x, dragStartPoint.y, event.x, event.y) < 100f
                if (doubleClicking) {
                    postedSingleTapRunnable?.let {
                        singleTapHandler.removeCallbacks(it)
                    }
                }

                initialBitmapCenterPoint[bitmapCenterPoint.x] = bitmapCenterPoint.y
                dragStartPoint[event.x] = event.y
                setMode(DRAG)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                clicking = false
                if (event.pointerCount > 2) {
                    setMode(NONE)
                } else if (event.pointerCount == 2) {
                    zoomInitialDist =
                        distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1))

                    if (zoomInitialDist > 10f) {
                        initialBitmapScale = bitmapScale
                        initialBitmapCenterPoint[bitmapCenterPoint.x] = bitmapCenterPoint.y
                        computeBitmapMiddle(
                            zoomBitmapMiddle,
                            event.getX(0),
                            event.getY(0),
                            event.getX(1),
                            event.getY(1)
                        )
                        setMode(ZOOM)
                    }
                }
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
                        zoomInitialDist = distance(x0, y0, x1, y1)
                        if (zoomInitialDist > 10f) {
                            initialBitmapScale = bitmapScale
                            initialBitmapCenterPoint[bitmapCenterPoint.x] = bitmapCenterPoint.y
                            computeBitmapMiddle(zoomBitmapMiddle, x0, y0, x1, y1)
                            setMode(ZOOM)
                        }
                    }

                    2 -> {
                        initialBitmapCenterPoint[bitmapCenterPoint.x] = bitmapCenterPoint.y

                        if (event.actionIndex == 0) {
                            dragStartPoint[event.getX(1)] = event.getY(1)
                        } else {
                            dragStartPoint[event.getX(0)] = event.getY(0)
                        }
                        setMode(DRAG)
                    }

                    else -> {
                        setMode(NONE)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    if (distance(event.x, event.y, dragStartPoint.x, dragStartPoint.y) > 10f) {
                        clicking = false
                    }
                    bitmapCenterPoint[initialBitmapCenterPoint.x - (event.x - dragStartPoint.x) / bitmapScale] =
                        initialBitmapCenterPoint.y - (event.y - dragStartPoint.y) / bitmapScale
                    recomputeMatrix(forceAutoFit = false, maintainAutoFit = false, animate = false)
                } else if (mode == ZOOM) {
                    val zoomNewDist =
                        distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    if (zoomNewDist > 10f) {
                        bitmapScale = initialBitmapScale * zoomNewDist / zoomInitialDist
                        bitmapCenterPoint[zoomBitmapMiddle.x + (initialBitmapCenterPoint.x - zoomBitmapMiddle.x) * initialBitmapScale / bitmapScale] =
                            zoomBitmapMiddle.y + (initialBitmapCenterPoint.y - zoomBitmapMiddle.y) * initialBitmapScale / bitmapScale
                        recomputeMatrix(
                            forceAutoFit = false,
                            maintainAutoFit = false,
                            animate = false
                        )
                    }
                }
            }
        }
        return true
    }

    private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val x = x1 - x0
        val y = y1 - y0
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun computeBitmapMiddle(
        outputPoint: PointF,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ) {
        val x = (x0 + x1) / 2
        val y = (y0 + y1) / 2
        val offsetX = x - width / 2f
        val offsetY = y - height / 2f
        outputPoint[bitmapCenterPoint.x + offsetX / bitmapScale] =
            bitmapCenterPoint.y + offsetY / bitmapScale
    }

    private inner class FlingAnimation(var velocityX: Float, var velocityY: Float) : Runnable {
        val inertia: Double = .1
        val stopThreshold: Int = 200

        var stopped: Boolean = false
        var previousTimestamp: Long

        init {
            this.previousTimestamp = System.currentTimeMillis()
        }

        override fun run() {
            if (!stopped) {
                val newTimestamp = System.currentTimeMillis()
                val elapsed = (newTimestamp - previousTimestamp) / 1000.0


                bitmapCenterPoint[(bitmapCenterPoint.x - elapsed * velocityX / bitmapScale).toFloat()] =
                    (bitmapCenterPoint.y - elapsed * velocityY / bitmapScale).toFloat()
                recomputeMatrix(forceAutoFit = false, maintainAutoFit = false, animate = false)

                val ratio = inertia.pow(elapsed)
                velocityX *= ratio.toFloat()
                velocityY *= ratio.toFloat()
                if (velocityX * velocityX + velocityY * velocityY > stopThreshold) {
                    previousTimestamp = newTimestamp
                    postOnAnimation(this)
                }
            }
        }
    }

    interface ParentViewPagerUserInputController {
        fun setParentViewPagerUserInputEnabled(enabled: Boolean)
    }

    companion object {
        private const val DOUBLE_CLICK_DURATION = 500
        private const val CLICK_DURATION = 200
    }
}
