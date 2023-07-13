package io.olvid.messenger.customClasses.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

open class OrderedListItemSpan(private val level : Int = 0,
    private val number: String
) : LeadingMarginSpan {
    private val paint = Paint()
    private var margin = 0

    companion object {
        fun measure(textView: TextView, text: CharSequence) {
            (text as? Spanned)?.getSpans(
                0,
                text.length,
                OrderedListItemSpan::class.java
            )?.apply {
                val paint = textView.paint
                forEach {
                    it.margin = paint.measureText(it.number).roundToInt()
                }
            }
        }
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return max(margin, 12.toPx())
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
        if ((text as Spanned).getSpanStart(this) == start) {  // apply item at text start only and ignore text wrap
            paint.set(p)
            val numberWidth = paint.measureText(number).roundToInt()
            var width: Int = (level + 1) * 12.toPx()
            if (numberWidth > width) {
                width = numberWidth
                margin = numberWidth
            } else {
                margin = 0
            }
            val left: Int = if (dir > 0) {
                x + width * dir - numberWidth
            } else {
                x + width * dir + (width - numberWidth)
            }
            c.drawText(number, left.toFloat(), baseline.toFloat(), paint)
        }
    }
}