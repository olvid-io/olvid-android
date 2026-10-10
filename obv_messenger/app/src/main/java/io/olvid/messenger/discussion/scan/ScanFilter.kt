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

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Page filters on ARGB pixels, after Leptonica (the image library of Tesseract):
 * - the paper brightness map is estimated from paper pixels only (pixBackgroundNorm): tiles without
 *   enough of them (pictures, dense text) are filled from their neighbors, then the map is smoothed;
 * - enhanced divides by that map with one gain for the three channels (hues are kept), then white
 *   balances on the paper color, stretches the contrast and sharpens;
 * - black & white is a Sauvola adaptive threshold of the normalized luminance, pictures being kept in
 *   enhanced grayscale (mixed mode, as ScanTailor's).
 */
enum class ScanFilter {
    ORIGINAL,
    ENHANCED,
    GRAYSCALE,
    BLACK_AND_WHITE;

    fun apply(pixels: IntArray, width: Int, height: Int): IntArray = when (this) {
        ORIGINAL -> pixels.copyOf()
        ENHANCED -> enhance(pixels, width, height)
        GRAYSCALE -> enhance(pixels, width, height).let { out -> IntArray(out.size) { gray(luminance(out[it])) } }
        BLACK_AND_WHITE -> blackAndWhite(pixels, width, height)
    }

    companion object {
        // paper map tiles, as a fraction of the longest side
        private const val TILE_FRACTION = 1 / 40f
        // a tile is flat (paper, possibly with text) if this fraction of its pixels is close to its bright level
        private const val MIN_PAPER_FRACTION = 0.5f
        private const val FLAT_TOLERANCE = 0.88f
        // largest level drop between neighboring paper tiles
        private const val GROWTH_RATIO = 0.9f
        // normalized paper value
        private const val PAPER = 245f
        // a darker paper level is not paper (no page, or a dark picture): the gain is capped at 2.5
        private const val MIN_PAPER_LEVEL = PAPER / 2.5f
        private const val BLACK_POINT_PERCENTILE = 0.01
        private const val MAX_BLACK_POINT = 90
        private const val SATURATION = 1.15f
        private const val SHARPEN = 0.6f
        private const val SAUVOLA_K = 0.1f
        private const val SAUVOLA_R = 128f
        private const val SAUVOLA_WINDOW_FRACTION = 1 / 50f
        // wider than text strokes, whose inside would otherwise be thresholded against itself
        private const val MIN_SAUVOLA_RADIUS = 8
        // smaller groups of non-paper tiles are text (bold titles, dense paragraphs), not pictures
        private const val MIN_PICTURE_TILES = 6

        private fun luminance(c: Int) = ((c shr 16 and 0xff) * 77 + (c shr 8 and 0xff) * 150 + (c and 0xff) * 29) shr 8

        private fun gray(v: Int) = (0xff shl 24) or (v shl 16) or (v shl 8) or v

        private fun rgb(r: Float, g: Float, b: Float) =
            (0xff shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)

        /** Per-pixel paper luminance, and which tiles of [tile] pixels are paper. */
        internal class PaperMap(val levels: FloatArray, val tile: Int, val tilesX: Int, val tilesY: Int, val paperTiles: BooleanArray)

        /** Paper luminance, interpolated from a tile map estimated on paper tiles only. */
        internal fun paperMap(lum: IntArray, w: Int, h: Int): PaperMap {
            val tile = max(8, (max(w, h) * TILE_FRACTION).roundToInt())
            val tilesX = (w + tile - 1) / tile
            val tilesY = (h + tile - 1) / tile

            // per tile: bright level (90th percentile) and flatness (fraction of pixels close to it)
            val levels = FloatArray(tilesX * tilesY)
            val flat = BooleanArray(tilesX * tilesY)
            val histogram = IntArray(256)
            for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
                histogram.fill(0)
                var total = 0
                for (y in ty * tile until min(h, (ty + 1) * tile)) for (x in tx * tile until min(w, (tx + 1) * tile)) {
                    histogram[lum[y * w + x]]++
                    total++
                }
                var remaining = (total * 0.1).toInt()
                var level = 255
                while (level > 0 && remaining > histogram[level]) remaining -= histogram[level--]
                var close = 0
                for (v in (level * FLAT_TOLERANCE).toInt()..255) close += histogram[v]
                levels[ty * tilesX + tx] = level.toFloat()
                flat[ty * tilesX + tx] = close >= total * MIN_PAPER_FRACTION
            }

            // paper tiles: grown from the brightest flat tiles through flat neighbors of similar level, so
            // that a gradual shade is followed while the jump into a picture stops the growth
            val map = FloatArray(tilesX * tilesY) { Float.NaN }
            val seedLevel = levels.filterIndexed { i, _ -> flat[i] }.sorted().let { if (it.isEmpty()) 0f else it[(it.size * 0.9).toInt().coerceAtMost(it.size - 1)] }
            val queue = ArrayDeque<Int>()
            for (i in levels.indices) {
                if (flat[i] && levels[i] >= seedLevel * 0.97f) {
                    map[i] = levels[i]
                    queue.add(i)
                }
            }
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                val tx = i % tilesX
                val ty = i / tilesX
                for ((dx, dy) in NEIGHBORS) {
                    val nx = tx + dx
                    val ny = ty + dy
                    if (nx !in 0 until tilesX || ny !in 0 until tilesY) continue
                    val j = ny * tilesX + nx
                    if (map[j].isNaN() && flat[j] && levels[j] >= levels[i] * GROWTH_RATIO) {
                        map[j] = levels[j]
                        queue.add(j)
                    }
                }
            }
            val bright = levels.maxOrNull() ?: 255f
            val paperTiles = BooleanArray(map.size) { !map[it].isNaN() }

            // fill tiles without paper from their neighbors, layer by layer
            if (map.all { it.isNaN() }) map.fill(bright)
            while (map.any { it.isNaN() }) {
                val previous = map.copyOf()
                for (i in map.indices) {
                    if (!previous[i].isNaN()) continue
                    val tx = i % tilesX
                    val ty = i / tilesX
                    var sum = 0f
                    var n = 0
                    for ((dx, dy) in NEIGHBORS) {
                        val nx = tx + dx
                        val ny = ty + dy
                        if (nx !in 0 until tilesX || ny !in 0 until tilesY) continue
                        val v = previous[ny * tilesX + nx]
                        if (!v.isNaN()) {
                            sum += v
                            n++
                        }
                    }
                    if (n > 0) map[i] = sum / n
                }
            }

            // smoothing: two 3x3 box passes
            var smoothed = map
            repeat(2) {
                val source = smoothed
                smoothed = FloatArray(source.size) { i ->
                    val tx = i % tilesX
                    val ty = i / tilesX
                    var sum = 0f
                    var n = 0
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = tx + dx
                        val ny = ty + dy
                        if (nx in 0 until tilesX && ny in 0 until tilesY) {
                            sum += source[ny * tilesX + nx]
                            n++
                        }
                    }
                    sum / n
                }
            }

            // bilinear interpolation between tile centers
            val out = FloatArray(w * h)
            for (y in 0 until h) {
                val fy = ((y + 0.5f) / tile - 0.5f).coerceIn(0f, (tilesY - 1).toFloat())
                val y0 = fy.toInt()
                val y1 = min(y0 + 1, tilesY - 1)
                val wy = fy - y0
                for (x in 0 until w) {
                    val fx = ((x + 0.5f) / tile - 0.5f).coerceIn(0f, (tilesX - 1).toFloat())
                    val x0 = fx.toInt()
                    val x1 = min(x0 + 1, tilesX - 1)
                    val wx = fx - x0
                    val top = smoothed[y0 * tilesX + x0] * (1 - wx) + smoothed[y0 * tilesX + x1] * wx
                    val bottom = smoothed[y1 * tilesX + x0] * (1 - wx) + smoothed[y1 * tilesX + x1] * wx
                    out[y * w + x] = max(MIN_PAPER_LEVEL, top * (1 - wy) + bottom * wy)
                }
            }
            return PaperMap(out, tile, tilesX, tilesY, paperTiles)
        }

        /**
         * Mixed black & white (as ScanTailor's mixed mode): Sauvola for the text, enhanced grayscale
         * inside the pictures, found as large groups of tiles without paper.
         */
        private fun blackAndWhite(pixels: IntArray, w: Int, h: Int): IntArray {
            val lum = IntArray(pixels.size) { luminance(pixels[it]) }
            val map = paperMap(lum, w, h)
            val normalized = FloatArray(lum.size) { min(255f, lum[it] * PAPER / map.levels[it]) }
            val out = sauvola(normalized, w, h)
            val pictures = pictureRectangles(map, normalized, w, h)
            if (pictures.isEmpty()) return out
            val enhanced = enhance(pixels, w, h, map)
            pictures.forEach { (x0, y0, x1, y1) ->
                for (y in y0 until y1) for (x in x0 until x1) {
                    out[y * w + x] = gray(luminance(enhanced[y * w + x]))
                }
            }
            return out
        }

        /** Bounding rectangles (x0, y0, x1, y1) of the groups of non-paper tiles, tightened to their content. */
        private fun pictureRectangles(map: PaperMap, normalized: FloatArray, w: Int, h: Int): List<IntArray> {
            val seen = BooleanArray(map.paperTiles.size)
            val rectangles = ArrayList<IntArray>()
            for (start in map.paperTiles.indices) {
                if (map.paperTiles[start] || seen[start]) continue
                var minX = Int.MAX_VALUE
                var minY = Int.MAX_VALUE
                var maxX = -1
                var maxY = -1
                var count = 0
                val queue = ArrayDeque<Int>().apply { add(start) }
                seen[start] = true
                while (queue.isNotEmpty()) {
                    val i = queue.removeFirst()
                    val tx = i % map.tilesX
                    val ty = i / map.tilesX
                    count++
                    minX = min(minX, tx)
                    maxX = max(maxX, tx)
                    minY = min(minY, ty)
                    maxY = max(maxY, ty)
                    for ((dx, dy) in NEIGHBORS) {
                        val nx = tx + dx
                        val ny = ty + dy
                        if (nx !in 0 until map.tilesX || ny !in 0 until map.tilesY) continue
                        val j = ny * map.tilesX + nx
                        if (!map.paperTiles[j] && !seen[j]) {
                            seen[j] = true
                            queue.add(j)
                        }
                    }
                }
                if (count < MIN_PICTURE_TILES) continue
                // one tile of margin, then tightened while the border line is paper
                var x0 = max(0, (minX - 1) * map.tile)
                var y0 = max(0, (minY - 1) * map.tile)
                var x1 = min(w, (maxX + 2) * map.tile)
                var y1 = min(h, (maxY + 2) * map.tile)
                fun paperColumn(x: Int) = (y0 until y1).count { normalized[it * w + x] >= PAPER * 0.9f } >= (y1 - y0) * 0.97
                fun paperRow(y: Int) = (x0 until x1).count { normalized[y * w + it] >= PAPER * 0.9f } >= (x1 - x0) * 0.97
                while (x1 - x0 > 1 && paperColumn(x0)) x0++
                while (x1 - x0 > 1 && paperColumn(x1 - 1)) x1--
                while (y1 - y0 > 1 && paperRow(y0)) y0++
                while (y1 - y0 > 1 && paperRow(y1 - 1)) y1--
                rectangles.add(intArrayOf(x0, y0, x1, y1))
            }
            return rectangles
        }

        private fun enhance(pixels: IntArray, w: Int, h: Int, map: PaperMap? = null): IntArray {
            val lum = IntArray(pixels.size) { luminance(pixels[it]) }
            val paper = (map ?: paperMap(lum, w, h)).levels

            // one gain per pixel for the three channels, so that hues are kept
            val r = FloatArray(pixels.size)
            val g = FloatArray(pixels.size)
            val b = FloatArray(pixels.size)
            var paperR = 0.0
            var paperG = 0.0
            var paperB = 0.0
            var paperCount = 0
            for (i in pixels.indices) {
                val gain = PAPER / paper[i]
                val c = pixels[i]
                r[i] = (c shr 16 and 0xff) * gain
                g[i] = (c shr 8 and 0xff) * gain
                b[i] = (c and 0xff) * gain
                if (lum[i] >= paper[i] * 0.9f) {
                    paperR += r[i]
                    paperG += g[i]
                    paperB += b[i]
                    paperCount++
                }
            }

            // white balance on the paper color, within reasonable bounds
            val (wr, wg, wb) = if (paperCount > 0) {
                Triple(
                    (PAPER * paperCount / paperR).toFloat().coerceIn(0.8f, 1.25f),
                    (PAPER * paperCount / paperG).toFloat().coerceIn(0.8f, 1.25f),
                    (PAPER * paperCount / paperB).toFloat().coerceIn(0.8f, 1.25f),
                )
            } else Triple(1f, 1f, 1f)

            // black point from the darkest pixels, stretch to [0, 255]
            val histogram = IntArray(256)
            for (i in pixels.indices) histogram[(0.3f * r[i] * wr + 0.59f * g[i] * wg + 0.11f * b[i] * wb).roundToInt().coerceIn(0, 255)]++
            var remaining = (pixels.size * BLACK_POINT_PERCENTILE).toInt()
            var black = 0
            while (black < MAX_BLACK_POINT && remaining > histogram[black]) remaining -= histogram[black++]
            val stretch = 255f / (PAPER - black)

            val out = IntArray(pixels.size)
            for (i in pixels.indices) {
                var cr = (r[i] * wr - black) * stretch
                var cg = (g[i] * wg - black) * stretch
                var cb = (b[i] * wb - black) * stretch
                val l = 0.3f * cr + 0.59f * cg + 0.11f * cb
                cr = l + (cr - l) * SATURATION
                cg = l + (cg - l) * SATURATION
                cb = l + (cb - l) * SATURATION
                out[i] = rgb(cr, cg, cb)
            }
            return sharpen(out, w, h)
        }

        // unsharp mask with a 3x3 binomial blur
        private fun sharpen(pixels: IntArray, w: Int, h: Int): IntArray {
            val out = pixels.copyOf()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                val result = FloatArray(3)
                for (channel in 0 until 3) {
                    val shift = 16 - 8 * channel
                    fun v(j: Int) = (pixels[j] shr shift and 0xff).toFloat()
                    val blur = (4 * v(i) + 2 * (v(i - 1) + v(i + 1) + v(i - w) + v(i + w)) +
                            v(i - w - 1) + v(i - w + 1) + v(i + w - 1) + v(i + w + 1)) / 16f
                    result[channel] = v(i) + SHARPEN * (v(i) - blur)
                }
                out[i] = rgb(result[0], result[1], result[2])
            }
            return out
        }

        /** Sauvola: threshold = mean × (1 + k × (std / R − 1)) over a window, with integral images. */
        private fun sauvola(lum: FloatArray, w: Int, h: Int): IntArray {
            val radius = max(MIN_SAUVOLA_RADIUS, (max(w, h) * SAUVOLA_WINDOW_FRACTION).roundToInt() / 2)
            val sums = DoubleArray((w + 1) * (h + 1))
            val squares = DoubleArray((w + 1) * (h + 1))
            for (y in 0 until h) {
                var rowSum = 0.0
                var rowSquares = 0.0
                for (x in 0 until w) {
                    val v = lum[y * w + x].toDouble()
                    rowSum += v
                    rowSquares += v * v
                    sums[(y + 1) * (w + 1) + x + 1] = sums[y * (w + 1) + x + 1] + rowSum
                    squares[(y + 1) * (w + 1) + x + 1] = squares[y * (w + 1) + x + 1] + rowSquares
                }
            }
            return IntArray(w * h) { i ->
                val x = i % w
                val y = i / w
                val x0 = max(0, x - radius)
                val y0 = max(0, y - radius)
                val x1 = min(w, x + radius + 1)
                val y1 = min(h, y + radius + 1)
                val n = ((x1 - x0) * (y1 - y0)).toDouble()
                fun area(table: DoubleArray) = table[y1 * (w + 1) + x1] - table[y0 * (w + 1) + x1] - table[y1 * (w + 1) + x0] + table[y0 * (w + 1) + x0]
                val mean = area(sums) / n
                val std = sqrt(max(0.0, area(squares) / n - mean * mean))
                val threshold = mean * (1 + SAUVOLA_K * (std / SAUVOLA_R - 1))
                if (lum[i] > threshold) gray(255) else gray(0)
            }
        }

        private val NEIGHBORS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
