/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.discussion.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.olvid.engine.Logger
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Camera preview with the live page outline, and full resolution capture. */
@Stable
class ScannerCameraState {
    /** Stabilized outline, in normalized [0, 1] coordinates of the upright frame. */
    var outline by mutableStateOf<Quad?>(null)
        private set
    var frameAspect by mutableFloatStateOf(3f / 4f)
        private set
    var detectionMs by mutableLongStateOf(0L)
        private set
    /** How long the outline has been still, from 0 to 1 (ready for an automatic capture). */
    var stability by mutableFloatStateOf(0f)
        private set
    var torch by mutableStateOf(false)
        private set

    internal var camera: Camera? = null
        set(value) {
            field = value
            if (torch) value?.cameraControl?.enableTorch(true)
            focalRatio = value?.focalRatio()
        }
    /** Focal length of the bound camera, in picture diagonals (the 4:3 output covers the sensor). */
    var focalRatio: Double? = null
        private set
    private var stillReference: Quad? = null
    private var stillFrames = 0

    val analyzer = QuadAnalyzer()
    private val stabilizer = OutlineStabilizer()
    internal val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(ratio4by3())
        .build()

    internal fun onDetection(quad: Quad?, ms: Long, aspect: Float) {
        detectionMs = ms
        frameAspect = aspect
        val current = stabilizer.update(quad)
        outline = current
        val reference = stillReference
        if (current != null && quad != null && reference != null && current.alignedTo(reference).maxCornerDistance(reference) < STILL_DISTANCE) {
            stillFrames++
        } else {
            stillReference = current
            stillFrames = 0
        }
        stability = if (current == null) 0f else min(1f, stillFrames / STILL_FRAMES.toFloat())
    }

    fun toggleTorch() {
        torch = !torch
        camera?.cameraControl?.enableTorch(torch)
    }

    /**
     * Captures a full resolution picture. [onCaptured] runs on [executor] with the upright picture
     * and the outline displayed at the time of the capture.
     */
    fun takePicture(executor: Executor, onCaptured: (upright: Bitmap, outline: Quad?, focalRatio: Double?) -> Unit, onError: () -> Unit) {
        val outlineAtCapture = outline
        val aspectAtCapture = frameAspect
        val focal = focalRatio
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                runCatching {
                    val upright = image.toUprightBitmap()
                    // the normalized outline only maps onto the capture if both frames have the same aspect
                    val sameAspect = abs(upright.width / upright.height.toFloat() - aspectAtCapture) < MAX_ASPECT_DIFFERENCE
                    onCaptured(upright, outlineAtCapture.takeIf { sameAspect }, focal)
                }
                    .onFailure {
                        Logger.w("Document scanner: unable to process the capture: $it")
                        onError()
                    }
            }

            override fun onError(exception: ImageCaptureException) {
                Logger.w("Document scanner: capture failed: $exception")
                onError()
            }
        })
    }
}

@Composable
fun ScannerCameraPreview(state: ScannerCameraState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(state, lifecycleOwner) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().setResolutionSelector(ratio4by3()).build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ratio4by3())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor, state.analyzer.apply {
                        onResult = { quad, ms, aspect -> mainExecutor.execute { state.onDetection(quad, ms, aspect) } }
                    })
                }
            runCatching {
                provider.unbindAll()
                state.camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, state.imageCapture)
            }.onFailure { Logger.w("Document scanner: camera binding failed: $it") }
        }, mainExecutor)
        onDispose {
            state.camera = null
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    // corners follow the detection with a spring, the outline fades in and out
    val corners = remember { List(8) { Animatable(0f) } }
    var visible by remember { mutableStateOf(false) }
    val target = state.outline
    LaunchedEffect(target) {
        if (target == null) {
            visible = false
            return@LaunchedEffect
        }
        val values = target.points.flatMap { listOf(it.x, it.y) }
        if (!visible) {
            corners.forEachIndexed { i, corner -> corner.snapTo(values[i]) }
            visible = true
        } else {
            corners.forEachIndexed { i, corner -> launch { corner.animateTo(values[i], spring(stiffness = Spring.StiffnessMediumLow)) } }
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(OUTLINE_FADE_MS), label = "outline")

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(state.frameAspect)) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                if (alpha > 0f) {
                    val p = List(4) { ScanPoint(corners[2 * it].value * size.width, corners[2 * it + 1].value * size.height) }
                    drawOutline(Quad(p[0], p[1], p[2], p[3]), alpha)
                }
            }
        }
    }
}

class QuadAnalyzer : ImageAnalysis.Analyzer {
    internal var onResult: (quad: Quad?, ms: Long, aspect: Float) -> Unit = { _, _, _ -> }
    private val detector = QuadDetector()
    private var buffer = ByteArray(0)
    // last outline in buffer coordinates, kept through a few missed frames
    private var tracked: Quad? = null
    private var trackedMisses = 0
    @Volatile
    private var rawFrameRequest: ((luminance: ByteArray, width: Int, height: Int, rowStride: Int, rotation: Int) -> Unit)? = null

    /** Hands the next raw luminance frame to [onFrame], on the analysis thread (tuning only). */
    fun requestRawFrame(onFrame: (luminance: ByteArray, width: Int, height: Int, rowStride: Int, rotation: Int) -> Unit) {
        rawFrameRequest = onFrame
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = image.planes[0]
            val data = plane.buffer
            val size = data.remaining()
            if (buffer.size < size) buffer = ByteArray(size)
            data.get(buffer, 0, size)

            val rotation = image.imageInfo.rotationDegrees
            rawFrameRequest?.let {
                rawFrameRequest = null
                it(buffer, image.width, image.height, plane.rowStride, rotation)
            }

            val start = System.nanoTime()
            val quad = detector.detect(buffer, image.width, image.height, plane.rowStride, tracked)
            val ms = (System.nanoTime() - start) / 1_000_000
            if (quad != null) {
                tracked = quad
                trackedMisses = 0
            } else if (++trackedMisses > MAX_TRACKED_MISSES) {
                tracked = null
            }

            val (w, h) = image.width.toFloat() to image.height.toFloat()
            val (uprightW, uprightH) = if (rotation % 180 == 0) w to h else h to w
            val normalized = quad?.let { q ->
                Quad.fromUnordered(q.points.map { p ->
                    val r = when (rotation) {
                        90 -> ScanPoint(h - p.y, p.x)
                        180 -> ScanPoint(w - p.x, h - p.y)
                        270 -> ScanPoint(p.y, w - p.x)
                        else -> p
                    }
                    ScanPoint(r.x / uprightW, r.y / uprightH)
                })
            }
            onResult(normalized, ms, uprightW / uprightH)
        }
    }

    companion object {
        private const val MAX_TRACKED_MISSES = 5
    }
}

@OptIn(ExperimentalCamera2Interop::class)
private fun Camera.focalRatio(): Double? = runCatching {
    val characteristics = Camera2CameraInfo.from(cameraInfo)
    val focal = characteristics.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val sensor = characteristics.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    if (focal == null || sensor == null) null else focal / hypot(sensor.width.toDouble(), sensor.height.toDouble())
}.getOrNull()

private const val OUTLINE_FADE_MS = 180
private const val MAX_ASPECT_DIFFERENCE = 0.02f

// automatic capture once the outline moved less than STILL_DISTANCE (normalized) during STILL_FRAMES frames
private const val STILL_DISTANCE = 0.02f
private const val STILL_FRAMES = 15

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val rotation = imageInfo.rotationDegrees
    val raw = use { it.toBitmap() }
    return if (rotation == 0) raw else
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
}

private fun ratio4by3() = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .build()

internal val OutlineColor = Color(0xFF2682EB)

internal fun DrawScope.drawOutline(quad: Quad, alpha: Float = 1f) {
    val path = Path().apply {
        val points = quad.points
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, OutlineColor.copy(alpha = 0.33f * alpha))
    drawPath(path, OutlineColor.copy(alpha = alpha), style = Stroke(width = 3.dp.toPx()))
    quad.points.forEach { drawCircle(Color.White.copy(alpha = alpha), radius = 6.dp.toPx(), center = Offset(it.x, it.y)) }
}
