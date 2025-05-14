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

package io.olvid.messenger.discussion.message.attachments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import io.olvid.messenger.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PdfBitmapConverter {
    var renderer: PdfRenderer? = null
    val lock = Object()

    suspend fun pdfToBitmaps(filePath: String, maxPageWidthPx: Float, maxPageHeightPx: Float): List<RenderablePdfPage> {
        return withContext(Dispatchers.IO) {
            renderer?.close()
            val fd = ParcelFileDescriptor.open(
                File(App.absolutePathFromRelative(filePath)),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            with(PdfRenderer(fd)) {
                renderer = this
                return@withContext (0 until pageCount).map { index ->
                    openPage(index).use { page ->
                        val pageRatio = page.width.toFloat() / page.height
                        val screenRatio = maxPageWidthPx / maxPageHeightPx
                        val width: Int
                        val height: Int
                        if (pageRatio > screenRatio) {
                            width = maxPageWidthPx.roundToInt()
                            height = (maxPageWidthPx / pageRatio).roundToInt()
                        } else {
                            height = maxPageHeightPx.roundToInt()
                            width = (maxPageHeightPx * pageRatio).roundToInt()
                        }

                        RenderablePdfPage(width, height, key = "${filePath}-${index}") {
                            val bitmap = createBitmap(width, height)

                            Canvas(bitmap).apply {
                                drawColor(Color.WHITE)
                                drawBitmap(bitmap, 0f, 0f, null)
                            }

                            // we synchronize here as old Android API throw an exception when opening a page if another one is already open
                            // on API35 this works fine without the lock
                            synchronized(lock) {
                                openPage(index).use { page ->
                                    page.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )
                                }
                            }
                            bitmap
                        }
                    }
                }
            }
        }
    }
}

data class RenderablePdfPage(val width: Int, val height: Int, val key: String, val render: suspend () -> Bitmap?)

class RenderablePdfPageFetcher(
    private val renderablePdfPage: RenderablePdfPage,
    private val options: Options,
): Fetcher {
    override suspend fun fetch(): FetchResult {
        return DrawableResult(
            drawable = renderablePdfPage.render()?.toDrawable(options.context.resources) ?: ShapeDrawable(),
            isSampled = true,
            dataSource = DataSource.MEMORY)
    }

    class Factory: Fetcher.Factory<RenderablePdfPage> {
        override fun create(data: RenderablePdfPage, options: Options, imageLoader: ImageLoader): Fetcher {
            return RenderablePdfPageFetcher(data, options)
        }
    }
}

class RenderablePdfPageKeyer: Keyer<RenderablePdfPage> {
    override fun key(data: RenderablePdfPage, options: Options): String {
        return data.key
    }
}