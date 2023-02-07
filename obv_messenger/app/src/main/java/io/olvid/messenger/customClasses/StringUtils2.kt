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

package io.olvid.messenger.customClasses

import android.text.SpannableString
import android.text.style.URLSpan
import androidx.core.text.util.LinkifyCompat
import androidx.core.util.PatternsCompat

class StringUtils2 {
    companion object {
        fun stringFirstLinkCheck(body: String?, link: String): Boolean {
            return getLink(body)?.first == link
        }

        fun getLink(s: String?): Pair<String,String?>? {
            s?.let {_s ->
                val source = SpannableString(_s)
                val urlSpan = source
                    .apply { LinkifyCompat.addLinks(this, PatternsCompat.WEB_URL, "https://", { s: CharSequence?, start: Int, end: Int ->
                        s?.let {
                            (start == 0) || (it[start-1] != '@')
                        } ?: false
                    }, null) }
                    .getSpans(0, _s.length, URLSpan::class.java).firstOrNull()
                return urlSpan?.let {
                    Pair(_s.subSequence(source.getSpanStart(it), source.getSpanEnd(it)).toString(), it.url)
                }
            }
            return null
        }

        fun getExtensionFromFilename(fileName: String): String? {
            val pos = fileName.lastIndexOf('.')
            if (pos != -1) {
                return fileName.substring(pos + 1)
            }
            return null
        }
    }
}