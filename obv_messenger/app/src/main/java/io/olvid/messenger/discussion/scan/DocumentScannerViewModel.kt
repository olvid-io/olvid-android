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

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.engine.Logger
import io.olvid.messenger.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A scanned page: the captured picture (saved at [SOURCE_MAX_SIDE], with a smaller [preview] kept in
 * memory) and the edits applied to it. [quad] is in picture coordinates.
 */
data class ScanPage(
    val id: Int,
    val file: File,
    val preview: Bitmap,
    val width: Int,
    val height: Int,
    val detected: Quad?,
    val quad: Quad,
    val rotation: Int = 0,
    val filter: ScanFilter = ScanFilter.ENHANCED,
    /** Focal length of the camera, in picture diagonals, when known (see [PageAspect]). */
    val focalRatio: Double? = null,
) {
    val fullImage: Quad
        get() = fullQuad(width, height)
}

internal fun fullQuad(width: Int, height: Int) = Quad(
    ScanPoint(0f, 0f), ScanPoint(width.toFloat(), 0f), ScanPoint(width.toFloat(), height.toFloat()), ScanPoint(0f, height.toFloat())
)

class DocumentScannerViewModel(application: Application) : AndroidViewModel(application) {
    val pages = mutableStateListOf<ScanPage>()
    /** Rendered pages (straightened, rotated, filtered), by page id. */
    val rendered = mutableStateMapOf<Int, Bitmap>()
    /** Small renderings of the page for each filter, for the filter picker. */
    val filterPreviews = mutableStateMapOf<Pair<Int, ScanFilter>, Bitmap>()
    var capturing by mutableStateOf(false)
    var exporting by mutableStateOf(false)
    /** Index of the page the next capture replaces, null to add a page. */
    var retakeIndex by mutableStateOf<Int?>(null)
    /** Index of the page just added or replaced, to show it; reset by the screen. */
    var addedPage by mutableStateOf<Int?>(null)
    var automatic by mutableStateOf(false)
    private val defaultFileName = "Scan_" + SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(Date())
    /** Name of the exported PDF, without the .pdf extension. */
    var fileName by mutableStateOf(defaultFileName)

    private val sessionDirectory = File(application.cacheDir, SCAN_DIRECTORY + "/session_" + System.currentTimeMillis())
    private var nextId = 0
    private val renderJobs = HashMap<Int, Job>()

    init {
        // PDFs of previous scans, already copied as attachments
        viewModelScope.launch(Dispatchers.IO) {
            File(application.cacheDir, SCAN_DIRECTORY).listFiles { file -> file.isFile && file.lastModified() < System.currentTimeMillis() - OLD_PDF_MS }
                ?.forEach { it.delete() }
        }
    }

    /** Called on a background thread with the upright capture. */
    fun addCapture(upright: Bitmap, liveOutline: Quad?, focalRatio: Double?) {
        val ratio = min(1f, SOURCE_MAX_SIDE / max(upright.width, upright.height).toFloat())
        val source = if (ratio < 1f) upright.scale((upright.width * ratio).roundToInt(), (upright.height * ratio).roundToInt()) else upright
        // keep the outline shown when capturing, so the crop is what the user saw
        val detected = liveOutline?.map { ScanPoint(it.x * source.width, it.y * source.height) }
            ?: PerspectiveCorrector.detect(source)

        sessionDirectory.mkdirs()
        val id = synchronized(this) { nextId++ }
        val file = File(sessionDirectory, "page_$id.jpg")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, SOURCE_QUALITY, it) }
        val previewRatio = min(1f, PREVIEW_MAX_SIDE / max(source.width, source.height).toFloat())
        val preview = if (previewRatio < 1f) source.scale((source.width * previewRatio).roundToInt(), (source.height * previewRatio).roundToInt()) else source

        val page = ScanPage(
            id = id,
            file = file,
            preview = preview,
            width = source.width,
            height = source.height,
            detected = detected,
            quad = detected ?: fullQuad(source.width, source.height),
            focalRatio = focalRatio,
        )
        viewModelScope.launch {
            val index = retakeIndex?.takeIf { it in pages.indices }
            retakeIndex = null
            if (index != null) {
                delete(pages[index])
                pages.add(index, page)
            } else {
                pages.add(page)
            }
            addedPage = index ?: pages.lastIndex
            render(page)
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        capturing = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching { decodeUpright(uri)?.let { addCapture(it, null, null) } }
                        .onFailure { Logger.w("Document scanner: unable to import $uri: $it") }
                }
            }
            capturing = false
        }
    }

    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = getApplication<App>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SOURCE_MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        return if (rotation == 0) bitmap else
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }

    fun requestFilterPreviews(page: ScanPage) {
        viewModelScope.launch(Dispatchers.Default) {
            val ratio = FILTER_PREVIEW_SIDE / max(page.preview.width, page.preview.height).toFloat()
            val small = page.preview.scale((page.preview.width * ratio).roundToInt(), (page.preview.height * ratio).roundToInt())
            ScanFilter.entries.forEach { filter ->
                val key = page.id to filter
                val bitmap = renderPage(small, page.copy(filter = filter))
                withContext(Dispatchers.Main) { filterPreviews[key] = bitmap }
            }
        }
    }

    fun update(page: ScanPage) {
        val index = pages.indexOfFirst { it.id == page.id }
        if (index < 0) return
        pages[index] = page
        render(page)
    }

    fun delete(page: ScanPage) {
        pages.removeAll { it.id == page.id }
        renderJobs.remove(page.id)?.cancel()
        rendered.remove(page.id)
        ScanFilter.entries.forEach { filterPreviews.remove(page.id to it) }
        page.file.delete()
    }

    private fun render(page: ScanPage) {
        renderJobs.remove(page.id)?.cancel()
        renderJobs[page.id] = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { renderPage(page.preview, page) }
            rendered[page.id] = bitmap
        }
    }

    /** Writes all pages to a PDF file and returns it, or null if something failed. */
    suspend fun exportPdf(): File? = withContext(Dispatchers.Default) {
        runCatching {
            val output = File(getApplication<App>().cacheDir, SCAN_DIRECTORY).apply { mkdirs() }
                .let { File(it, sanitizedFileName() + ".pdf") }
            val pdfPages = pages.toList().map { page ->
                val source = BitmapFactory.decodeFile(page.file.path)
                val bitmap = renderPage(source, page)
                val jpeg = ByteArrayOutputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, PDF_QUALITY, it)
                    it.toByteArray()
                }
                ScanPdfWriter.Page(jpeg, bitmap.width, bitmap.height)
            }
            output.outputStream().buffered().use { ScanPdfWriter.write(it, pdfPages) }
            output
        }.onFailure { Logger.w("Document scanner: PDF export failed: $it") }.getOrNull()
    }

    private fun sanitizedFileName(): String =
        fileName.trim()
            .replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .ifEmpty { defaultFileName }

    override fun onCleared() {
        sessionDirectory.deleteRecursively()
    }

    companion object {
        const val SCAN_DIRECTORY = "document_scan"
        // about 210 dpi on an A4 page, and a bounded memory use while exporting
        private const val SOURCE_MAX_SIDE = 2480
        private const val PREVIEW_MAX_SIDE = 1280
        private const val SOURCE_QUALITY = 92
        private const val PDF_QUALITY = 85
        private const val FILTER_PREVIEW_SIDE = 240
        private const val OLD_PDF_MS = 60 * 60 * 1000L
    }
}

/** Straightens, rotates and filters [page] from [source], the full picture or its preview. */
internal fun renderPage(source: Bitmap, page: ScanPage): Bitmap {
    val scale = source.width / page.width.toFloat()
    val corrected = PerspectiveCorrector.correct(source, page.quad.map { ScanPoint(it.x * scale, it.y * scale) }, maxSide = max(source.width, source.height), focalRatio = page.focalRatio)
    val rotated = if (page.rotation == 0) corrected else
        Bitmap.createBitmap(corrected, 0, 0, corrected.width, corrected.height, Matrix().apply { postRotate(page.rotation.toFloat()) }, true)
    if (page.filter == ScanFilter.ORIGINAL) return rotated
    val pixels = IntArray(rotated.width * rotated.height)
    rotated.getPixels(pixels, 0, rotated.width, 0, 0, rotated.width, rotated.height)
    val filtered = page.filter.apply(pixels, rotated.width, rotated.height)
    return Bitmap.createBitmap(filtered, rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
}
