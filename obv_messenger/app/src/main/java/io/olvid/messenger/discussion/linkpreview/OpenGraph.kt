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

package io.olvid.messenger.discussion.linkpreview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.olvid.engine.datatypes.DictionaryKey
import io.olvid.engine.encoder.DecodingException
import io.olvid.engine.encoder.Encoded
import io.olvid.messenger.customClasses.StringUtils2
import java.io.ByteArrayOutputStream

data class OpenGraph(
    var title: String? = null,
    var description: String? = null,
    var originalUrl: String? = null,
    var url: String? = null,
    var image: String? = null,
    var siteName: String? = null,
    var type: String? = null,
    var bitmap: Bitmap? = null
) {
    companion object {
        const val MIME_TYPE = "olvid/link-preview"

        fun of(encoded: Encoded): OpenGraph {
            return OpenGraph().apply {
                try {
                    val dictionary = encoded.decodeDictionary()
                    title = dictionary[DictionaryKey("title")]?.decodeString()
                    description = dictionary[DictionaryKey("desc")]?.decodeString()
                    url = dictionary[DictionaryKey("url")]?.decodeString()
                    dictionary[DictionaryKey("image")]?.decodeBytes()?.let {
                        bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                } catch (e: DecodingException) {
                    title = null
                    description = null
                    bitmap = null
                }
            }
        }
    }

    // tests if the OpenGraph object actually contains anything interesting to display
    fun isEmpty(): Boolean {
        return title.isNullOrEmpty() && description.isNullOrEmpty() && bitmap == null
    }

    fun fileName(): String {
        return originalUrl ?: "link-preview"
    }

    fun getSafeUri() : Uri? {
        return StringUtils2.getLink(url)?.second?.let { Uri.parse(it).takeIf { uri -> uri.scheme != null } }
    }

    fun encode(): Encoded {
        val map = HashMap<DictionaryKey, Encoded>()
        title?.let {
            map[DictionaryKey("title")] = Encoded.of(it)
        }
        description?.let {
            map[DictionaryKey("desc")] = Encoded.of(it)
        }
        url?.let {
            map[DictionaryKey("url")] = Encoded.of(it)
        }
        bitmap?.let {
            map[DictionaryKey("image")] = Encoded.of(
                ByteArrayOutputStream().apply {
                    it.compress(
                        if (it.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        75,
                        this
                    )
                }.toByteArray()
            )
        }
        return Encoded.of(map)
    }
}