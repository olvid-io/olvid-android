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
import android.util.Log
import android.util.LruCache
import io.olvid.engine.encoder.Encoded
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.decodeSampledBitmapFromBytes
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.toHostHeader
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import java.util.Arrays
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private val cache = LruCache<String, OpenGraph>(50)
private const val MAX_IMAGE_SIZE = 5*1024*1024

class LinkPreviewRepository {
    private val client = OkHttpClient.Builder()
        .cache(null)
        .addInterceptor(UserAgentInterceptor("WhatsApp/2"))
        .apply {
            if (!SettingsActivity.getNoNotifyCertificateChangeForPreviews()) {
                AppSingleton.getSslSocketFactory()?.let { sslSocketFactory ->
                    try {
                        val trustManagerFactory = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm()
                        )
                        trustManagerFactory.init(null as KeyStore?)
                        val trustManagers = trustManagerFactory.trustManagers
                        check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
                            ("Unexpected default trust managers:"
                                    + Arrays.toString(trustManagers))
                        }
                        val trustManager = trustManagers[0] as X509TrustManager
                        this.sslSocketFactory(sslSocketFactory, trustManager)
                    } catch (e: java.lang.Exception) {
                        Log.e("LinkPreviewRepository", "Error initializing okHttpClient trustManager")
                    }
                }
            }

            System.getProperty("http.agent")?.let { systemUserAgent ->
                this.proxyAuthenticator { route, response ->
                    val request = Authenticator.JAVA_NET_AUTHENTICATOR.authenticate(route, response)
                    request?.newBuilder()?.header("User-Agent", systemUserAgent)?.build()
                        ?: if (route == null) {
                            null
                        } else Request.Builder()
                            .url(route.address.url)
                            .method("CONNECT", null)
                            .header("Host", route.address.url.toHostHeader(true))
                            .header("Proxy-Connection", "Keep-Alive")
                            .header("User-Agent", systemUserAgent)
                            .build()
                }
            }
        }.build()

    suspend fun fetchOpenGraph(url: String, imageWidth: Int, imageHeight: Int): OpenGraph {
        return withContext(Dispatchers.IO) {
            cache.get(url) ?: try {
                val openGraph = OpenGraphParser().parse(url, client)
                openGraph?.image?.let {
                    openGraph.bitmap = fetchImage(it, imageWidth, imageHeight)
                }
                openGraph.also { cache.put(url, it) } ?: OpenGraph()
            } catch (ex: Exception) {
                OpenGraph()
            }
        }
    }
    private fun fetchImage(uri: String, imageWidth: Int, imageHeight: Int): Bitmap? {
        return try {
            val response = client.newCall(Request.Builder().url(uri).build()).execute()
            if (response.isSuccessful) {
                response.body?.let {
                    if (it.contentLength() > MAX_IMAGE_SIZE) return null
                    decodeSampledBitmapFromBytes(it.byteStream().readBytes(), imageWidth, imageHeight)
                }
            } else {
                null
            }
        } catch (ex: Exception) {
            null
        }
    }

    suspend fun decodeOpenGraph(fyle: Fyle): OpenGraph? {
        return withContext(Dispatchers.IO) {
            cache.get(fyle.filePath) ?: try {
                FileInputStream(App.absolutePathFromRelative(fyle.filePath)).use { fis ->
                    ByteArrayOutputStream().use { byteArrayOutputStream ->
                        val buffer = ByteArray(262144)
                        var c: Int
                        while (fis.read(buffer).also { c = it } != -1) {
                            byteArrayOutputStream.write(buffer, 0, c)
                        }
                        OpenGraph.of(Encoded(byteArrayOutputStream.toByteArray())).also { cache.put(fyle.filePath, it) }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }
}