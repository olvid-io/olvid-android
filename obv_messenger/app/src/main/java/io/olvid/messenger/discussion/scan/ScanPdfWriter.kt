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

import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Minimal PDF writer with one JPEG image per page, embedded as is (DCTDecode). Android's
 * PdfDocument re-encodes bitmaps losslessly, which makes scanned pages several times larger.
 */
object ScanPdfWriter {
    class Page(val jpeg: ByteArray, val width: Int, val height: Int)

    // A4 long side, in points
    private const val LONG_SIDE = 842.0

    fun write(out: OutputStream, pages: List<Page>) {
        require(pages.isNotEmpty())
        val writer = CountingStream(out)
        val offsets = ArrayList<Long>()
        fun startObject() {
            offsets.add(writer.count)
            writer.ascii("${offsets.size} 0 obj\n")
        }

        writer.ascii("%PDF-1.4\n%âãÏÓ\n")
        // objects: 1 catalog, 2 pages, then 3 per page (page, content, image)
        startObject()
        writer.ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        startObject()
        val kids = pages.indices.joinToString(" ") { "${3 + 3 * it} 0 R" }
        writer.ascii("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")

        pages.forEachIndexed { index, page ->
            val pageId = 3 + 3 * index
            val scale = LONG_SIDE / max(page.width, page.height)
            val w = (page.width * scale).roundToInt()
            val h = (page.height * scale).roundToInt()

            startObject()
            writer.ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] /Contents ${pageId + 1} 0 R /Resources << /XObject << /Im$index ${pageId + 2} 0 R >> >> >>\nendobj\n")

            val content = "q $w 0 0 $h 0 0 cm /Im$index Do Q".toByteArray(Charsets.US_ASCII)
            startObject()
            writer.ascii("<< /Length ${content.size} >>\nstream\n")
            writer.write(content)
            writer.ascii("\nendstream\nendobj\n")

            startObject()
            writer.ascii("<< /Type /XObject /Subtype /Image /Width ${page.width} /Height ${page.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n")
            writer.write(page.jpeg)
            writer.ascii("\nendstream\nendobj\n")
        }

        val xref = writer.count
        writer.ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { writer.ascii("%010d 00000 n \n".format(it)) }
        writer.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        writer.flush()
    }

    private class CountingStream(private val out: OutputStream) {
        var count = 0L
            private set

        fun write(bytes: ByteArray) {
            out.write(bytes)
            count += bytes.size
        }

        fun ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))

        fun flush() = out.flush()
    }
}
