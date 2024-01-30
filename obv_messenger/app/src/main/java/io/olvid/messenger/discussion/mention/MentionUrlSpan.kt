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

package io.olvid.messenger.discussion.mention

import android.text.TextPaint
import android.text.style.URLSpan
import android.view.View

class MentionUrlSpan(val userIdentifier: ByteArray?, val length: Int, val color: Int?, val onClick: (() -> Unit)?) : URLSpan(null) {

    override fun onClick(widget: View) {
        onClick?.invoke()
    }

    override fun updateDrawState(drawState: TextPaint) {
        if (userIdentifier != null) {
            color?.let { drawState.linkColor = it }
            super.updateDrawState(drawState)
            drawState.isUnderlineText = true
        }
    }
}