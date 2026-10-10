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

/**
 * Turns per-frame detections into a stable outline: small moves are smoothed, a clearly different
 * outline must be seen on [switchFrames] consecutive frames before it replaces the displayed one,
 * and the outline is only dropped after [maxMisses] frames without detection.
 *
 * Distances are in the unit of the quads given to [update] (typically normalized coordinates).
 */
class OutlineStabilizer(
    private val jumpDistance: Float = 0.06f,
    private val switchFrames: Int = 3,
    private val maxMisses: Int = 5,
    private val smoothing: Float = 0.5f,
) {
    var outline: Quad? = null
        private set
    private var candidate: Quad? = null
    private var candidateFrames = 0
    private var misses = 0

    fun update(detected: Quad?): Quad? {
        if (detected == null) {
            if (++misses > maxMisses) {
                outline = null
                candidate = null
            }
            return outline
        }
        misses = 0
        val current = outline ?: return detected.also { outline = it }
        val aligned = detected.alignedTo(current)
        if (aligned.maxCornerDistance(current) < jumpDistance) {
            outline = interpolate(current, aligned)
            candidate = null
            return outline
        }
        val previousCandidate = candidate
        if (previousCandidate != null && aligned.alignedTo(previousCandidate).maxCornerDistance(previousCandidate) < jumpDistance) {
            candidateFrames++
        } else {
            candidateFrames = 1
        }
        candidate = aligned
        if (candidateFrames >= switchFrames) {
            outline = aligned
            candidate = null
        }
        return outline
    }

    private fun interpolate(from: Quad, to: Quad): Quad {
        val f = from.points
        val t = to.points
        val p = List(4) { ScanPoint(f[it].x + (t[it].x - f[it].x) * smoothing, f[it].y + (t[it].y - f[it].y) * smoothing) }
        return Quad(p[0], p[1], p[2], p[3])
    }
}
