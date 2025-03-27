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

package io.olvid.messenger.discussion.linkpreview

import io.olvid.engine.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets

class OpenGraphParser {

    fun parse(url: String, client: OkHttpClient): OpenGraph? {
        val openGraphResult = OpenGraph(url = url)

        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()

            var html = ""
            if (response.isSuccessful) {
                response.body?.let { body ->
                    if (body.contentType()?.subtype != "html") return null
                    html = String(
                        body.byteStream().readBytes(),
                        body.contentType()?.charset(StandardCharsets.UTF_8)
                            ?: StandardCharsets.UTF_8
                    )
                }
            } else {
                // typically, for 404 errors
                return null
            }

            val doc = Jsoup.parse(html, url)

            val ogTags = doc.select(DOC_SELECT_OGTAGS)

            ogTags.forEach { tag ->

                when (tag.attr(PROPERTY)) {
                    OG_IMAGE -> {
                        openGraphResult.image = tag.attr(OPEN_GRAPH_KEY)
                    }
                    OG_DESCRIPTION -> {
                        openGraphResult.description = tag.attr(OPEN_GRAPH_KEY)
                    }
                    OG_TITLE -> {
                        openGraphResult.title = tag.attr(OPEN_GRAPH_KEY)
                    }
                    OG_TYPE -> {
                        openGraphResult.type = tag.attr(OPEN_GRAPH_KEY)
                    }
                }
            }

            if (openGraphResult.title.isNullOrEmpty()) {
                openGraphResult.title = doc.title()
            }
            if (openGraphResult.description.isNullOrEmpty()) {
                val docSelection = doc.select(DOC_SELECT_DESCRIPTION)
                openGraphResult.description = docSelection.firstOrNull()?.attr("content") ?: ""
            }
            if (openGraphResult.image.isNullOrEmpty() || openGraphResult.image == "null") {
                openGraphResult.image =
                    doc.head().select(DOC_SELECT_FAVICON).firstOrNull()?.attr("abs:href")
                        ?: doc.head()
                            .select(DOC_SELECT_ITEMPROP).firstOrNull()?.attr("content")
            }
        } catch (e: Exception) {
            Logger.w("Opengraph parser error: " + e.message)
            return null
        }

        return openGraphResult
    }

    companion object {
        private const val DOC_SELECT_OGTAGS = "meta[property^=og:]"
        private const val DOC_SELECT_DESCRIPTION = "meta[name=description]"
        private const val DOC_SELECT_FAVICON = "link[rel~=icon][href~=.*\\.(ico|png)]"
        private const val DOC_SELECT_ITEMPROP = "meta[itemprop=image]"
        private const val OPEN_GRAPH_KEY = "content"
        private const val PROPERTY = "property"
        private const val OG_IMAGE = "og:image"
        private const val OG_DESCRIPTION = "og:description"
        private const val OG_TITLE = "og:title"
//        private const val OG_SITE_NAME = "og:site_name"
        private const val OG_TYPE = "og:type"
    }
}