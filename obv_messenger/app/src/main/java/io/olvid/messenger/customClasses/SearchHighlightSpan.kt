/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.customClasses

import android.content.Context
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import androidx.core.content.ContextCompat
import io.olvid.messenger.R


class SearchHighlightSpan(
    context: Context
) : CharacterStyle(), UpdateAppearance {
    private val backgroundColor : Int = ContextCompat.getColor(context, R.color.searchHighlightColor)
    private val foregroundColor : Int = ContextCompat.getColor(context, R.color.black)

    override fun updateDrawState(tp: TextPaint?) {
        tp?.color = foregroundColor
        tp?.bgColor = backgroundColor
    }

    override fun toString(): String {
        return "SearchHighlightSpan"
    }


}