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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ScanPdfWriterTest {

    private fun jpeg(width: Int, height: Int): ScanPdfWriter.Page {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xffffff else 0x203040)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        return ScanPdfWriter.Page(bytes, width, height)
    }

    private fun write(vararg pages: ScanPdfWriter.Page): ByteArray =
        ByteArrayOutputStream().also { ScanPdfWriter.write(it, pages.toList()) }.toByteArray()

    private fun ByteArray.text() = String(this, Charsets.ISO_8859_1)

    @Test
    fun `cross reference table points to each object`() {
        val pdf = write(jpeg(120, 170), jpeg(170, 120), jpeg(100, 100))
        val text = pdf.text()
        val startXref = text.substringAfterLast("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.startsWith("xref\n", startXref))

        val lines = text.substring(startXref).lines()
        val count = lines[1].split(" ")[1].toInt()
        assertEquals(1 + 2 + 3 * 3, count)
        for (id in 1 until count) {
            val offset = lines[2 + id].substring(0, 10).toInt()
            assertTrue("object $id not at $offset", text.startsWith("$id 0 obj", offset))
        }
        assertTrue(text.endsWith("%%EOF\n"))
    }

    @Test
    fun `one page per image, sized to the image aspect ratio`() {
        val text = write(jpeg(120, 170), jpeg(170, 120)).text()
        assertTrue(text.contains("/Count 2"))
        assertTrue(text.contains("/MediaBox [0 0 594 842]"))
        assertTrue(text.contains("/MediaBox [0 0 842 594]"))
    }

    @Test
    fun `images are embedded as is`() {
        val page = jpeg(64, 48)
        val pdf = write(page)
        val start = pdf.text().indexOf("/Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n")
        assertTrue(start > 0)
        val data = pdf.text().substringAfter("/Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n").toByteArray(Charsets.ISO_8859_1)
        assertTrue(data.copyOf(page.jpeg.size).contentEquals(page.jpeg))
    }
}
