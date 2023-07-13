/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

open class CodeSpan : MetricAffectingSpan() {
    override fun updateMeasureState(p: TextPaint) {
        apply(p)
    }

    override fun updateDrawState(ds: TextPaint) {
        apply(ds)
        ds.bgColor = ColorUtils.applyAlpha(ds.color, 25)
    }

    private fun apply(p: TextPaint) {
        applyCodeTextStyle(p)
    }

    private fun applyCodeTextStyle(paint: Paint) {
        paint.typeface = Typeface.MONOSPACE
    }
}