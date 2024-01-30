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
import android.text.Layout
import android.text.style.LeadingMarginSpan
import kotlin.math.max
import kotlin.math.min

open class BlockQuoteSpan : LeadingMarginSpan {
    private val blockQuoteWidth: Int = 6.toPx()
    private val blockQuoteMargin: Int = 24.toPx()
    private val rect = Rect()
    private val paint = Paint()
    override fun getLeadingMargin(first: Boolean): Int {
        return blockQuoteMargin
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
        paint.set(p)
        applyBlockQuoteStyle(paint)
        val left: Int
        val right: Int
        run {
            val l = x + dir * blockQuoteWidth
            val r = l + dir * blockQuoteWidth
            left = min(l, r)
            right = max(l, r)
        }
        rect[left, top, right] = bottom
        c.drawRect(rect, paint)
    }

    private fun applyBlockQuoteStyle(paint: Paint) {
        val BLOCK_QUOTE_DEF_COLOR_ALPHA = 25
        val color = ColorUtils.applyAlpha(paint.color, BLOCK_QUOTE_DEF_COLOR_ALPHA)
        paint.style = FILL
        paint.color = color
    }
}