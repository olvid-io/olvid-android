/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
package io.olvid.messenger.customClasses.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style.FILL
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan

open class CodeBlockSpan : MetricAffectingSpan(), LeadingMarginSpan {
    private val rect = Rect()
    private val paint = Paint()
    private val margin = 6.toPx()
    override fun updateMeasureState(p: TextPaint) {
        apply(p)
    }

    override fun updateDrawState(ds: TextPaint) {
        apply(ds)
    }

    private fun apply(p: TextPaint) {
        applyCodeTextStyle(p)
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return margin
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout
    ) {
        paint.style = FILL
        paint.color = ColorUtils.applyAlpha(paint.color, 25)
        val left: Int
        val right: Int
        if (dir > 0) {
            left = x
            right = c.width
        } else {
            left = x - c.width
            right = x
        }
        rect[left, top, right] = bottom
        c.drawRect(rect, paint)
    }

    private fun applyCodeTextStyle(paint: Paint) {
        paint.typeface = Typeface.MONOSPACE
    }
}