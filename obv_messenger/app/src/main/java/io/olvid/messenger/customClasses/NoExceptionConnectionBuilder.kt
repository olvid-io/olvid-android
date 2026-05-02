/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

import android.net.Uri
import android.os.Build
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.services.MDMConfigurationSingleton.getUserAgentOverride
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

class NoExceptionConnectionBuilder : ConnectionBuilder {
    @Throws(IOException::class)
    override fun openConnection(uri: Uri): HttpURLConnection {
        try {
            val connection: HttpURLConnection = INSTANCE.openConnection(uri)
            if (connection is HttpsURLConnection && AppSingleton.getSslSocketFactory() != null) {
                connection.setSSLSocketFactory(AppSingleton.getSslSocketFactory())
            }
            val userAgentProperty = if (getUserAgentOverride() != null) getUserAgentOverride() else System.getProperty("http.agent")
            if (userAgentProperty != null) {
                connection.setRequestProperty("User-Agent", userAgentProperty)
            }
            return connection
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    companion object {
        private val INSTANCE: ConnectionBuilder = DefaultConnectionBuilder.INSTANCE

        fun Uri.downloadContent(maxDownloadSize: Long = 1_000_000L): DownloadResult {
            var connection: HttpURLConnection? = null
            try {
                connection = NoExceptionConnectionBuilder().openConnection(this)
                when(val responseCode = connection.getResponseCode()) {
                    HttpURLConnection.HTTP_OK -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            if (connection.contentLengthLong > maxDownloadSize)
                                return DownloadResult.PayloadTooLarge
                        } else {
                            if (connection.contentLength > maxDownloadSize)
                                return DownloadResult.PayloadTooLarge
                        }

                        connection.getInputStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(1024)
                            var totalRead = 0
                            var bytesRead: Int

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                totalRead += bytesRead
                                if (totalRead > maxDownloadSize) return DownloadResult.PayloadTooLarge
                                output.write(buffer, 0, bytesRead)
                            }
                            return DownloadResult.Success(output = output.toByteArray())
                        }
                    }

                    else -> return DownloadResult.HttpError(responseCode)
                }
            } catch (e: Exception) {
                Logger.x(e)
                return DownloadResult.ConnectionError
            } finally {
                connection?.disconnect()
            }
        }
    }

    sealed interface DownloadResult {
        data object ConnectionError : DownloadResult
        data object PayloadTooLarge : DownloadResult
        data class HttpError(val errorCode: Int) : DownloadResult
        @Suppress("ArrayInDataClass")
        data class Success(val output: ByteArray) : DownloadResult
    }
}
