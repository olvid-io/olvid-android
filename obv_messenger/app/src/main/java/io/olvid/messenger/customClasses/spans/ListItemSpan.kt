package io.olvid.messenger.customClasses.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style.FILL
import android.graphics.Paint.Style.STROKE
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import kotlin.math.min
import kotlin.math.roundToInt

open class ListItemSpan(private val level: Int) :
    LeadingMarginSpan {
    private val paint = Paint()
    private val circle = RectF()
    private val rectangle = Rect()
    private val margin = 16.toPx()

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
        if ((text as Spanned).getSpanStart(this) == start) {  // apply bullet at text start only and ignore text wrap
            paint.set(p)
            val save = c.save()
            try {
                val width: Int = (level + 1) * margin

                val textLineHeight = (paint.descent() - paint.ascent()).roundToInt()
                val side = min(width, textLineHeight) / 3
                val marginLeft = (width - side) / 2

                val l: Int = if (dir > 0) {
                    x + marginLeft
                } else {
                    x - width + marginLeft
                }
                val r: Int = l + side
                val t = baseline + ((paint.descent() + paint.ascent()) / 2f).roundToInt() - side / 2
                val b = t + side

                if (level == 0
                    || level == 1
                ) {
                    circle[l.toFloat(), t.toFloat(), r.toFloat()] = b.toFloat()
                    val style = if (level == 0) FILL else STROKE
                    paint.style = style
                    c.drawOval(circle, paint)
                } else {
                    rectangle[l, t, r] = b
                    paint.style = FILL
                    c.drawRect(rectangle, paint)

                }

            } finally {
                c.restoreToCount(save)
            }
        }
    }
}