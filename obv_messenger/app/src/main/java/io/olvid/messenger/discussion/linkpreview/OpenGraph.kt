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
import java.util.Locale

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
                    siteName = dictionary[DictionaryKey("site")]?.decodeString()
                    dictionary[DictionaryKey("image")]?.decodeBytes()?.let {
                        bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                } catch (e: DecodingException) {
                    title = null
                    description = null
                    bitmap = null
                    siteName = null
                }
            }
        }

        val DOMAINS_WITH_LONG_DESCRIPTION = listOf(
            ".x.com",
            ".twitter.com",
            ".fxtwitter.com",
            ".vxtwitter.com",
            ".mastodon.social",
        )
    }

    // tests if the OpenGraph object actually contains anything interesting to display
    fun isEmpty(): Boolean {
        return title.isNullOrEmpty() && description.isNullOrEmpty() && bitmap == null
    }

    fun hasLargeImageToDisplay() : Boolean {
        return (bitmap?.width ?: 0) >= 400
    }


    fun shouldShowCompleteDescription() : Boolean {
        return url?.let {
            try {
                Uri.parse(it).host?.let { host ->
                    val dottedHost = ".$host".lowercase(locale = Locale.ENGLISH)
                    Companion.DOMAINS_WITH_LONG_DESCRIPTION.any { dottedHost.endsWith(it) }
                }
            } catch (e : Exception) {
                null
            }
        } ?: false
    }

    fun fileName(): String {
        return originalUrl ?: "link-preview"
    }

    fun getSafeUri(): Uri? {
        return StringUtils2.getLink(url)?.second?.let {
            Uri.parse(it).takeIf { uri -> uri.scheme != null }
        }
    }

    fun buildDescription() : String {
        return description.takeIf { it.isNullOrEmpty().not() } ?: siteName.takeIf { it.isNullOrEmpty().not() } ?: url ?: ""
    }

    fun encode(): Encoded {
        val map = HashMap<DictionaryKey, Encoded>()
        title?.let {
            map[DictionaryKey("title")] = Encoded.of(it)
        }
        description?.let {
            map[DictionaryKey("desc")] = Encoded.of(it)
        }
        siteName?.let {
            map[DictionaryKey("site")] = Encoded.of(it)
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