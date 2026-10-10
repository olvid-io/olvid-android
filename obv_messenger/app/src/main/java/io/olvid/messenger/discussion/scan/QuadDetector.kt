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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class ScanPoint(val x: Float, val y: Float)

data class Quad(
    val topLeft: ScanPoint,
    val topRight: ScanPoint,
    val bottomRight: ScanPoint,
    val bottomLeft: ScanPoint,
) {
    val points: List<ScanPoint>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun map(transform: (ScanPoint) -> ScanPoint) =
        Quad(transform(topLeft), transform(topRight), transform(bottomRight), transform(bottomLeft))

    // same outline with its corners relabeled to best match [reference]
    fun alignedTo(reference: Quad): Quad {
        val p = points
        val shift = (0 until 4).minBy { shift -> shifted(p, shift).maxCornerDistance(reference) }
        return shifted(p, shift)
    }

    fun maxCornerDistance(other: Quad): Float =
        points.zip(other.points).maxOf { (a, b) -> hypot(a.x - b.x, a.y - b.y) }

    private fun shifted(p: List<ScanPoint>, shift: Int) = Quad(p[shift % 4], p[(shift + 1) % 4], p[(shift + 2) % 4], p[(shift + 3) % 4])

    companion object {
        // Orders 4 points of a convex quadrilateral clockwise (y axis pointing down), starting top-left.
        fun fromUnordered(points: List<ScanPoint>): Quad {
            val cx = points.sumOf { it.x.toDouble() } / 4
            val cy = points.sumOf { it.y.toDouble() } / 4
            val sorted = points.sortedBy { atan2(it.y - cy, it.x - cx) }
            val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
            val o = List(4) { sorted[(start + it) % 4] }
            return Quad(o[0], o[1], o[2], o[3])
        }
    }
}

/**
 * Finds the outline of a document page in a luminance image, without any ML model:
 * downscale → blur → Canny edges → Hough lines → best quadrilateral, scored by how much of
 * its perimeter lies on detected edges.
 */
class QuadDetector(private val workingSize: Int = 320) {

    private class Line(val theta: Double, val rho: Double, val votes: Int)

    /**
     * @param luminance 8-bit luminance values (for instance the Y plane of a YUV camera frame)
     * @param previous the outline detected in the previous frame, favored to keep the outline stable
     * @return the page corners in [luminance] coordinates, or null if no page was found
     */
    fun detect(luminance: ByteArray, width: Int, height: Int, rowStride: Int = width, previous: Quad? = null): Quad? {
        val scale = max(1, ceil(max(width, height) / workingSize.toFloat()).toInt())
        val w = width / scale
        val h = height / scale
        if (w < MIN_SIZE || h < MIN_SIZE) return null

        val gray = downscale(luminance, rowStride, w, h, scale)
        val blurred = blur(gray, w, h)
        val edges = canny(blurred, w, h) ?: return null
        val lines = houghLines(edges, w, h)
        val tracked = previous?.map { ScanPoint(it.x / scale - 0.5f, it.y / scale - 0.5f) }
        val quad = bestQuad(lines, edges, blurred, w, h, tracked) ?: return null
        return quad.map { ScanPoint((it.x + 0.5f) * scale, (it.y + 0.5f) * scale) }
    }

    private fun downscale(src: ByteArray, rowStride: Int, w: Int, h: Int, scale: Int): FloatArray {
        val out = FloatArray(w * h)
        val norm = 1f / (scale * scale)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                for (dy in 0 until scale) {
                    val row = (y * scale + dy) * rowStride + x * scale
                    for (dx in 0 until scale) {
                        sum += src[row + dx].toInt() and 0xff
                    }
                }
                out[y * w + x] = sum * norm
            }
        }
        return out
    }

    // separable 5-tap binomial kernel
    private fun blur(src: FloatArray, w: Int, h: Int): FloatArray {
        val k = floatArrayOf(1f / 16, 4f / 16, 6f / 16, 4f / 16, 1f / 16)
        val tmp = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                for (i in -2..2) s += k[i + 2] * src[y * w + (x + i).coerceIn(0, w - 1)]
                tmp[y * w + x] = s
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                for (i in -2..2) s += k[i + 2] * tmp[(y + i).coerceIn(0, h - 1) * w + x]
                out[y * w + x] = s
            }
        }
        return out
    }

    private class Edges(val mask: BooleanArray, val angle: FloatArray)

    private fun canny(src: FloatArray, w: Int, h: Int): Edges? {
        val mag = FloatArray(w * h)
        val angle = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = (src[i - w + 1] + 2 * src[i + 1] + src[i + w + 1]) - (src[i - w - 1] + 2 * src[i - 1] + src[i + w - 1])
                val gy = (src[i + w - 1] + 2 * src[i + w] + src[i + w + 1]) - (src[i - w - 1] + 2 * src[i - w] + src[i - w + 1])
                mag[i] = hypot(gx, gy)
                angle[i] = atan2(gy, gx)
            }
        }

        // non-maximum suppression along the gradient direction
        val thin = FloatArray(w * h)
        var survivors = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val m = mag[i]
                if (m < MIN_GRADIENT) continue
                var a = angle[i]
                if (a < 0) a += PI.toFloat()
                val (n1, n2) = when {
                    a < PI / 8 || a >= 7 * PI / 8 -> mag[i - 1] to mag[i + 1]
                    a < 3 * PI / 8 -> mag[i - w - 1] to mag[i + w + 1]
                    a < 5 * PI / 8 -> mag[i - w] to mag[i + w]
                    else -> mag[i - w + 1] to mag[i + w - 1]
                }
                if (m >= n1 && m >= n2) {
                    thin[i] = m
                    survivors++
                }
            }
        }
        if (survivors < MIN_SIZE) return null

        // thresholds relative to the noise level: a percentile of the edges would be driven by
        // high-contrast content (text) and miss faint page borders
        val noise = mag.copyOf().also { it.sort() }[mag.size / 2]
        val high = max(noise * NOISE_FACTOR, MIN_GRADIENT * 2)
        val low = high * LOW_RATIO

        // hysteresis
        val mask = BooleanArray(w * h)
        val stack = IntArray(w * h)
        for (seed in thin.indices) {
            if (thin[seed] < high || mask[seed]) continue
            var top = 0
            stack[top++] = seed
            mask[seed] = true
            while (top > 0) {
                val i = stack[--top]
                val x = i % w
                val y = i / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val n = ny * w + nx
                        if (!mask[n] && thin[n] >= low) {
                            mask[n] = true
                            stack[top++] = n
                        }
                    }
                }
            }
        }
        return Edges(mask, angle)
    }

    private fun houghLines(edges: Edges, w: Int, h: Int): List<Line> {
        val diag = ceil(hypot(w.toDouble(), h.toDouble())).toInt()
        val rhoBins = 2 * diag + 1
        val cosT = DoubleArray(THETA_BINS) { cos(it * PI / THETA_BINS) }
        val sinT = DoubleArray(THETA_BINS) { sin(it * PI / THETA_BINS) }
        val acc = IntArray(THETA_BINS * rhoBins)

        for (i in edges.mask.indices) {
            if (!edges.mask[i]) continue
            val x = i % w
            val y = i / w
            // the gradient is normal to the edge: only vote for nearby orientations
            var a = edges.angle[i].toDouble()
            if (a < 0) a += PI
            val center = (a / PI * THETA_BINS).roundToInt()
            for (d in -THETA_WINDOW..THETA_WINDOW) {
                val t = Math.floorMod(center + d, THETA_BINS)
                val rho = (x * cosT[t] + y * sinT[t]).roundToInt() + diag
                acc[t * rhoBins + rho]++
            }
        }

        val minVotes = (min(w, h) * MIN_LINE_FRACTION).toInt()
        val peaks = ArrayList<Line>()
        for (t in 0 until THETA_BINS) {
            for (r in 0 until rhoBins) {
                val v = acc[t * rhoBins + r]
                if (v < minVotes) continue
                var isMax = true
                loop@ for (dt in -PEAK_THETA..PEAK_THETA) {
                    val nt = Math.floorMod(t + dt, THETA_BINS)
                    // lines at theta close to 0 and close to PI are the same with opposite rho
                    val wrapped = t + dt < 0 || t + dt >= THETA_BINS
                    val base = if (wrapped) rhoBins - 1 - r else r
                    for (dr in -PEAK_RHO..PEAK_RHO) {
                        val nr = base + dr
                        if (nr !in 0 until rhoBins || (dt == 0 && dr == 0)) continue
                        val nv = acc[nt * rhoBins + nr]
                        if (nv > v || (nv == v && (nt * rhoBins + nr) < (t * rhoBins + r))) {
                            isMax = false
                            break@loop
                        }
                    }
                }
                if (isMax) peaks.add(Line(t * PI / THETA_BINS, (r - diag).toDouble(), v))
            }
        }
        return peaks.sortedByDescending { it.votes }.take(MAX_LINES)
    }

    private fun angleBetween(a: Line, b: Line): Double {
        val d = abs(a.theta - b.theta)
        return min(d, PI - d)
    }

    private fun intersect(a: Line, b: Line): ScanPoint? {
        val det = cos(a.theta) * sin(b.theta) - sin(a.theta) * cos(b.theta)
        if (abs(det) < 1e-6) return null
        val x = (a.rho * sin(b.theta) - b.rho * sin(a.theta)) / det
        val y = (b.rho * cos(a.theta) - a.rho * cos(b.theta)) / det
        return ScanPoint(x.toFloat(), y.toFloat())
    }

    // distance between two lines, measured from the point of [a] closest to the image center
    private fun separation(a: Line, b: Line, cx: Double, cy: Double): Double {
        val d = cx * cos(a.theta) + cy * sin(a.theta) - a.rho
        val fx = cx - d * cos(a.theta)
        val fy = cy - d * sin(a.theta)
        return abs(fx * cos(b.theta) + fy * sin(b.theta) - b.rho)
    }

    private fun bestQuad(lines: List<Line>, edges: Edges, gray: FloatArray, w: Int, h: Int, tracked: Quad?): Quad? {
        val minSeparation = min(w, h) * MIN_OPPOSITE_SEPARATION
        val pairs = ArrayList<Pair<Line, Line>>()
        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                if (angleBetween(lines[i], lines[j]) < MAX_OPPOSITE_ANGLE
                    && separation(lines[i], lines[j], w / 2.0, h / 2.0) >= minSeparation
                ) {
                    pairs.add(lines[i] to lines[j])
                }
            }
        }

        val marginX = w * CORNER_MARGIN
        val marginY = h * CORNER_MARGIN
        val minArea = w * h * MIN_AREA_FRACTION
        val trackRadius = hypot(w.toFloat(), h.toFloat()) * TRACK_RADIUS
        var best: List<Line>? = null
        var bestScore = 0.0

        for (i in pairs.indices) {
            val (a, b) = pairs[i]
            for (j in i + 1 until pairs.size) {
                val (c, d) = pairs[j]
                if (a === c || a === d || b === c || b === d) continue
                if (angleBetween(a, c) < MIN_ADJACENT_ANGLE) continue
                val corners = listOf(intersect(a, c), intersect(c, b), intersect(b, d), intersect(d, a))
                if (corners.any { it == null || it.x < -marginX || it.y < -marginY || it.x > w + marginX || it.y > h + marginY }) continue
                @Suppress("UNCHECKED_CAST")
                val polygon = corners as List<ScanPoint>
                if (!isConvex(polygon) || area(polygon) < minArea) continue
                var score = borderScore(polygon, edges, gray, w, h) ?: continue
                score *= aspectFactor(polygon)
                val quad = Quad.fromUnordered(polygon)
                if (tracked != null && quad.alignedTo(tracked).maxCornerDistance(tracked) < trackRadius) score *= TRACK_BONUS
                if (score > bestScore) {
                    bestScore = score
                    best = listOf(a, c, b, d)
                }
            }
        }
        val sides = best?.map { refine(it, edges, w, h) } ?: return null
        val corners = List(4) { intersect(sides[it], sides[(it + 1) % 4]) ?: return null }
        return Quad.fromUnordered(corners)
    }

    // least squares fit of the edge pixels close to [line] whose gradient is normal to it: the Hough
    // grid alone is too coarse for precise (and stable) corners
    private fun refine(line: Line, edges: Edges, w: Int, h: Int): Line {
        val c = cos(line.theta)
        val s = sin(line.theta)
        var n = 0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (i in edges.mask.indices) {
            if (!edges.mask[i]) continue
            val x = (i % w).toDouble()
            val y = (i / w).toDouble()
            if (abs(x * c + y * s - line.rho) > REFINE_DISTANCE) continue
            if (abs(cos(edges.angle[i]) * s - sin(edges.angle[i]) * c) > MAX_EDGE_MISALIGNMENT) continue
            n++
            sx += x
            sy += y
            sxx += x * x
            syy += y * y
            sxy += x * y
        }
        if (n < MIN_SIZE) return line
        val mx = sx / n
        val my = sy / n
        val cxx = sxx / n - mx * mx
        val cyy = syy / n - my * my
        val cxy = sxy / n - mx * my
        // the normal is the eigenvector of the smallest eigenvalue of the covariance
        val theta = 0.5 * atan2(2 * cxy, cxx - cyy) + PI / 2
        val rho = mx * cos(theta) + my * sin(theta)
        return Line(theta, rho, line.votes)
    }

    // light preference for the A4 aspect ratio, estimated from the mean opposite side lengths: in
    // perspective the apparent ratio differs from the real one, so it only breaks near ties
    private fun aspectFactor(p: List<ScanPoint>): Double {
        val sides = List(4) { hypot(p[(it + 1) % 4].x - p[it].x, p[(it + 1) % 4].y - p[it].y).toDouble() }
        val first = (sides[0] + sides[2]) / 2
        val second = (sides[1] + sides[3]) / 2
        val deviation = ln(max(first, second) / min(first, second)) - ln(A4_RATIO)
        return ASPECT_FLOOR + (1 - ASPECT_FLOOR) * exp(-deviation * deviation / (2 * ASPECT_SIGMA * ASPECT_SIGMA))
    }

    /**
     * Covered minus uncovered border length, or null if the polygon does not look like a page border:
     * each side must be supported by edges running along it, and the contrast across the sides must
     * have the same polarity (page brighter or darker than its surroundings) on at least 3 sides. A
     * line inside the page (picture border, text row) has page on both sides or the opposite polarity.
     */
    private fun borderScore(p: List<ScanPoint>, edges: Edges, gray: FloatArray, w: Int, h: Int): Double? {
        val cx = p.sumOf { it.x.toDouble() } / 4
        val cy = p.sumOf { it.y.toDouble() } / 4
        var covered = 0
        var considered = 0
        var brighterSides = 0
        var darkerSides = 0
        for (i in 0 until 4) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val length = hypot(b.x - a.x, b.y - a.y)
            val dx = (b.x - a.x) / length
            val dy = (b.y - a.y) / length
            // outward normal
            var nx = -dy
            var ny = dx
            if (nx * ((a.x + b.x) / 2 - cx) + ny * ((a.y + b.y) / 2 - cy) < 0) {
                nx = -nx
                ny = -ny
            }

            val steps = max(1, length.toInt())
            var sideCovered = 0
            var visible = 0
            for (s in 0..steps) {
                val x = a.x + dx * s
                val y = a.y + dy * s
                // parts of the side outside the frame are neither covered nor uncovered
                if (x < 0 || y < 0 || x > w - 1 || y > h - 1) continue
                visible++
                for (k in -EDGE_SEARCH..EDGE_SEARCH) {
                    val qx = (x + nx * k).roundToInt()
                    val qy = (y + ny * k).roundToInt()
                    if (qx !in 0 until w || qy !in 0 until h) continue
                    val q = qy * w + qx
                    // the gradient of a border pixel is normal to the side
                    if (edges.mask[q] && abs(cos(edges.angle[q]) * ny - sin(edges.angle[q]) * nx) < MAX_EDGE_MISALIGNMENT) {
                        sideCovered++
                        break
                    }
                }
            }
            if (visible < (steps + 1) * MIN_SIDE_VISIBLE || sideCovered < visible * MIN_SIDE_COVERAGE) return null

            // per-sample votes, so that a few dark pixels (text close to the border) cannot flip a side
            var brighterInside = 0
            var darkerInside = 0
            var samples = 0
            for (s in 1 until CONTRAST_SAMPLES) {
                val t = 0.1f + 0.8f * s / CONTRAST_SAMPLES
                val x = a.x + (b.x - a.x) * t
                val y = a.y + (b.y - a.y) * t
                val ix = (x - nx * CONTRAST_OFFSET).roundToInt()
                val iy = (y - ny * CONTRAST_OFFSET).roundToInt()
                val ox = (x + nx * CONTRAST_OFFSET).roundToInt()
                val oy = (y + ny * CONTRAST_OFFSET).roundToInt()
                if (ix !in 0 until w || iy !in 0 until h || ox !in 0 until w || oy !in 0 until h) continue
                val contrast = gray[iy * w + ix] - gray[oy * w + ox]
                if (contrast > MIN_SIDE_CONTRAST) brighterInside++ else if (contrast < -MIN_SIDE_CONTRAST) darkerInside++
                samples++
            }
            // sides along the frame border cannot be checked, and ambiguous sides are neutral
            if (samples >= CONTRAST_SAMPLES / 2) {
                if (brighterInside >= samples * POLARITY_MAJORITY) brighterSides++
                else if (darkerInside >= samples * POLARITY_MAJORITY) darkerSides++
            }

            covered += sideCovered
            considered += visible
        }
        // one side may disagree (sheets stacked under the page are as bright as the page), not two
        // (the sides of a picture spanning the page width)
        val dissenting = min(brighterSides, darkerSides)
        if (dissenting > 1) return null
        val score = (covered - UNCOVERED_PENALTY * (considered - covered)) * (if (dissenting == 1) DISSENT_PENALTY else 1.0)
        return if (score > 0) score else null
    }

    private fun isConvex(p: List<ScanPoint>): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val c = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    private fun area(p: List<ScanPoint>): Float {
        var s = 0f
        for (i in 0 until 4) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    companion object {
        private const val MIN_SIZE = 32
        private const val MIN_GRADIENT = 8f
        private const val NOISE_FACTOR = 5f
        private const val LOW_RATIO = 0.5f
        private const val THETA_BINS = 180
        private const val THETA_WINDOW = 6
        private const val PEAK_THETA = 4
        private const val PEAK_RHO = 6
        private const val MIN_LINE_FRACTION = 0.12
        private const val MAX_LINES = 30
        private val MAX_OPPOSITE_ANGLE = Math.toRadians(35.0)
        private val MIN_ADJACENT_ANGLE = Math.toRadians(40.0)
        private const val CORNER_MARGIN = 0.25f
        private const val MIN_AREA_FRACTION = 0.12f
        private const val MIN_SIDE_COVERAGE = 0.35
        private const val EDGE_SEARCH = 2
        private const val REFINE_DISTANCE = 2.5
        private const val MIN_SIDE_VISIBLE = 0.25
        private const val UNCOVERED_PENALTY = 1.0
        private const val MIN_OPPOSITE_SEPARATION = 0.25
        private val MAX_EDGE_MISALIGNMENT = sin(Math.toRadians(25.0))
        private const val CONTRAST_SAMPLES = 16
        private const val CONTRAST_OFFSET = 2f
        private const val MIN_SIDE_CONTRAST = 4f
        private const val POLARITY_MAJORITY = 0.65
        private const val DISSENT_PENALTY = 0.8
        private val A4_RATIO = sqrt(2.0)
        private const val ASPECT_SIGMA = 0.3
        private const val ASPECT_FLOOR = 0.85
        private const val TRACK_RADIUS = 0.05f
        private const val TRACK_BONUS = 1.3
    }
}
