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
package io.olvid.messenger.designsystem.avatar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import io.olvid.messenger.settings.SettingsActivity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generative "pattern" avatar: a circle in one shade of an identity color family with 3 seeded
 * rotated rectangles in the other shades, replacing the colored-circle-with-initial when a contact
 * has no photo.
 *
 * Everything (family, shade roles, rectangle sizes / rotations / positions) derives
 * deterministically from the bytes identity, so a given contact renders the same avatar
 * everywhere, forever. Light and dark theme share the exact same geometry and only swap the
 * shade values. The generation algorithm is documented in
 * docs/avatar-pattern-spec.md so other Olvid clients can replicate it.
 */

/** The 12 identity color families as `{Light, Base, Dark}` shade triads, per app theme. */
private class ShadeTriad(light: Long, base: Long, dark: Long) {
    val shades = listOf(Color(light), Color(base), Color(dark))
}

private class IdentityFamily(val lightTheme: ShadeTriad, val darkTheme: ShadeTriad)

// Ordered by hue around the color wheel so that `hueFromBytes` buckets map each contact to the
// family closest to its current HSL color (bucket i covers hues [i*30°, (i+1)*30°)).
private val identityFamilies = listOf(
    // Terracotta (~23°)
    IdentityFamily(
        ShadeTriad(0xFFF4D0B2, 0xFFD29E7E, 0xFFB68060),
        ShadeTriad(0xFFD29E7E, 0xFFB68060, 0xFF976548),
    ),
    // Sand (~37°)
    IdentityFamily(
        ShadeTriad(0xFFF0DEBC, 0xFFDBC9AD, 0xFFC9B18F),
        ShadeTriad(0xFFDBC9AD, 0xFFC9B18F, 0xFF9F8866),
    ),
    // Gold (~39°)
    IdentityFamily(
        ShadeTriad(0xFFF8E7C1, 0xFFE4C998, 0xFFE8BF76),
        ShadeTriad(0xFFE2CDA0, 0xFFCBB385, 0xFFA98F64),
    ),
    // Sage (~79°)
    IdentityFamily(
        ShadeTriad(0xFFE3EACB, 0xFFB8C49D, 0xFFA4B189),
        ShadeTriad(0xFFB8C49D, 0xFFA4B189, 0xFF7B8865),
    ),
    // Eucalyptus (~155°)
    IdentityFamily(
        ShadeTriad(0xFFE7F4F0, 0xFFC8E1D9, 0xFFB3D3C9),
        ShadeTriad(0xFFA3C1B1, 0xFF90AFA0, 0xFF6B8C7B),
    ),
    // Juniper (~168°)
    IdentityFamily(
        ShadeTriad(0xFFC8ECE1, 0xFFA9D7C9, 0xFF96B9AE),
        ShadeTriad(0xFF82A09D, 0xFF587B77, 0xFF4E6562),
    ),
    // Lavender (~217°)
    IdentityFamily(
        ShadeTriad(0xFFD4E2F7, 0xFFB9CDEE, 0xFF869BC8),
        ShadeTriad(0xFFA1B3DA, 0xFF869BC8, 0xFF6476A7),
    ),
    // Slate (~228°)
    IdentityFamily(
        ShadeTriad(0xFFE7EAF3, 0xFF999EB1, 0xFF798197),
        ShadeTriad(0xFF999EB1, 0xFF798197, 0xFF636A7D),
    ),
    // Periwinkle (~248°)
    IdentityFamily(
        ShadeTriad(0xFFE7E0F8, 0xFFACA7CF, 0xFF908BB7),
        ShadeTriad(0xFFACA7CF, 0xFF908BB7, 0xFF78759D),
    ),
    // Mauve (~316°)
    IdentityFamily(
        ShadeTriad(0xFFFFEDF8, 0xFFB99CB1, 0xFFA27A96),
        ShadeTriad(0xFFB99CB1, 0xFFA27A96, 0xFF7E5A73),
    ),
    // Heather (~318°)
    IdentityFamily(
        ShadeTriad(0xFFD7B8CF, 0xFFD1ACC6, 0xFFB99CB1),
        ShadeTriad(0xFFD1ACC6, 0xFFB99CB1, 0xFFB78CA7),
    ),
    // Rose (~337°)
    IdentityFamily(
        ShadeTriad(0xFFF5D8E9, 0xFFC38DA2, 0xFFA5627B),
        ShadeTriad(0xFFC38DA2, 0xFFA5627B, 0xFF8E5168),
    ),
)

/**
 * splitmix64 — deterministic and trivially portable to the other Olvid clients, unlike
 * java.util.Random. Do not change the constants or the consumption order in [avatarPattern]:
 * both are part of the cross-client spec, and any change re-rolls every user's avatar.
 */
private class SplitMix64(private var state: Long) {
    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1) with 24 bits of precision — enough for on-screen geometry. */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24)

    fun nextFloat(min: Float, max: Float): Float = min + (max - min) * nextFloat()

    fun nextInt(bound: Int): Int = ((nextLong() ushr 33) % bound).toInt()
}

/** 64-bit variant of the rolling hash used by [hueFromBytes]. */
private fun seedFromBytes(bytes: ByteArray): Long {
    var result = 1L
    for (element in bytes) {
        result = 31L * result + element
    }
    return result
}

/**
 * One rectangle of the pattern. Geometry is normalized to the circle diameter: [centerX]/[centerY]
 * are offsets from the circle center, [width]/[height] fractions of the diameter.
 */
data class PatternRect(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val color: Color,
)

data class AvatarPatternSpec(
    val background: Color,
    val rects: List<PatternRect>,
    /** Shade with enough contrast against [background] for a glyph (group icon) drawn on top. */
    val glyphColor: Color,
)

// Per-slot geometry ranges, in diameter units.
// Slot 0 is the small accent rectangle, slot 1 the large one, slot 2 the medium one.
private val slotWidthRanges = listOf(0.25f to 1.00f, 0.37f to 0.82f, 0.42f to 0.70f)
private val slotHeightRanges = listOf(0.40f to 1.05f, 0.84f to 1.25f, 0.74f to 1.14f)
private val slotDistanceRanges = listOf(0.20f to 0.45f, 0.45f to 0.60f, 0.45f to 0.65f)
private const val ANGLE_JITTER = 40f

/**
 * Deterministically expands a bytes identity into its avatar pattern. Same bytes ⇒ same spec;
 * [darkMode] only swaps shade values, never geometry.
 */
fun avatarPattern(bytes: ByteArray, darkMode: Boolean): AvatarPatternSpec {
    val family = identityFamilies[hueFromBytes(bytes) * 12 / 360]
    val triad = (if (darkMode) family.darkTheme else family.lightTheme).shades
    val rng = SplitMix64(seedFromBytes(bytes))

    // Background shade: weighted towards Light (5/8 Light, 2/8 Base, 1/8 Dark).
    val backgroundIndex = when (rng.nextInt(8)) {
        in 0..4 -> 0
        5, 6 -> 1
        else -> 2
    }
    val rectShades = triad.filterIndexed { index, _ -> index != backgroundIndex }
    // Every example uses exactly 2 rectangle colors: one rect gets one shade, the other two share.
    val soloShade = rectShades[rng.nextInt(2)]
    val pairedShade = rectShades.first { it != soloShade }
    val soloSlot = rng.nextInt(3)

    val baseAngle = rng.nextFloat(0f, 360f)
    val rects = List(3) { slot ->
        val angle = baseAngle + slot * 120f + rng.nextFloat(-ANGLE_JITTER, ANGLE_JITTER)
        val distance = rng.nextFloat(slotDistanceRanges[slot].first, slotDistanceRanges[slot].second)
        val radians = Math.toRadians(angle.toDouble())
        PatternRect(
            centerX = (cos(radians) * distance).toFloat(),
            centerY = (sin(radians) * distance).toFloat(),
            width = rng.nextFloat(slotWidthRanges[slot].first, slotWidthRanges[slot].second),
            height = rng.nextFloat(slotHeightRanges[slot].first, slotHeightRanges[slot].second),
            rotationDegrees = rng.nextFloat(0f, 360f),
            color = if (slot == soloSlot) soloShade else pairedShade,
        )
    }
    return AvatarPatternSpec(
        background = triad[backgroundIndex],
        rects = rects,
        glyphColor = if (backgroundIndex == 2) triad[0] else triad[2],
    )
}

/** Draws the pattern clipped to the avatar circle, filling this DrawScope. */
fun DrawScope.drawAvatarPattern(spec: AvatarPatternSpec) {
    val diameter = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)
    clipPath(
        Path().apply {
            addOval(Rect(center - Offset(diameter / 2f, diameter / 2f), Size(diameter, diameter)))
        }
    ) {
        drawCircle(spec.background, radius = diameter / 2f, center = center)
        drawPatternRects(spec, diameter, center)
    }
}

/**
 * Full-bleed variant for adaptive launcher icons: the launcher masks the shape itself, so the
 * pattern paints the whole square (background included) without a circle clip.
 */
internal fun DrawScope.drawAvatarPatternFullBleed(spec: AvatarPatternSpec) {
    drawRect(spec.background, topLeft = Offset.Zero, size = size)
    drawPatternRects(spec, size.minDimension, Offset(size.width / 2f, size.height / 2f))
}

private fun DrawScope.drawPatternRects(spec: AvatarPatternSpec, diameter: Float, center: Offset) {
    for (rect in spec.rects) {
        val rectCenter = center + Offset(rect.centerX * diameter, rect.centerY * diameter)
        val rectSize = Size(rect.width * diameter, rect.height * diameter)
        rotate(rect.rotationDegrees, pivot = rectCenter) {
            drawRect(
                rect.color,
                topLeft = rectCenter - Offset(rectSize.width / 2f, rectSize.height / 2f),
                size = rectSize,
            )
        }
    }
}

// Snapshot-state mirror of the preference: reads from Compose draw blocks subscribe to it, so
// toggling the setting invalidates already-composed avatars (plain reads still work off-screen).
private val usePatternAvatarsState by lazy { mutableStateOf(SettingsActivity.usePatternAvatars()) }

val usePatternAvatars: Boolean
    get() = usePatternAvatarsState.value

/** Call after the preference changes (see CustomizationPreferenceFragment). */
fun refreshUsePatternAvatars() {
    usePatternAvatarsState.value = SettingsActivity.usePatternAvatars()
}
