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
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import io.olvid.messenger.BuildConfig
import kotlin.collections.contains
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.getOrDefault
import kotlin.runCatching
import kotlin.text.lowercase

object UrlUtils {
    // A set of known tracking parameters to remove.
    private val TRACKING_PARAMS = setOf(
        // Google Analytics
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        // Facebook Click Identifier
        "fbclid",
        // Google Click Identifier
        "gclid", "dclid",
        // microsoft
        "msclkid",
        // HubSpot
        "_hsenc", "_hsmi",
        // Marketo
        "mkt_tok",
        // Others
        "mc_cid", "mc_eid", "sessionid"
    )

    /**
     * Parses, cleans, and reconstructs a URL to remove common tracking parameters.
     *
     * @param urlString The input URL to clean.
     * @return A cleaned URL string, or the original URL if it's invalid or doesn't need cleaning.
     */
    fun cleanUrl(urlString: String): String {
        return runCatching {
            val uri = urlString.toUri()

            if (uri.scheme?.lowercase() !in listOf("http", "https")) {
                return urlString
            }
            if (uri.queryParameterNames.isEmpty()) {
                return urlString
            }

            val cleanedUriBuilder = uri.buildUpon().clearQuery()

            uri.queryParameterNames.filter { key -> key.lowercase() !in TRACKING_PARAMS }
                .forEach { key ->
                    uri.getQueryParameters(key).forEach { value ->
                        cleanedUriBuilder.appendQueryParameter(key, value)
                    }
                }
            cleanedUriBuilder.build().toString()
        }.getOrDefault(urlString)
    }
}

fun Uri.clean() = UrlUtils.cleanUrl(toString())
fun String.cleanUrl() = UrlUtils.cleanUrl(this)

fun Context.openStoreUrlOrFallback() {
    runCatching {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "market://details?id=$packageName".toUri()
            )
        )
    }.onFailure {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    if (BuildConfig.USE_GOOGLE_LIBS)
                        "https://play.google.com/store/apps/details?id=$packageName".toUri()
                    else
                        "https://olvid.io/download/".toUri()
                )
            )
        }
    }
}
