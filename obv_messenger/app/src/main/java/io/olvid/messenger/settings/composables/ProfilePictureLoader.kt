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

package io.olvid.messenger.settings.composables

import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfilePictureLabelAndKey(val identity: ByteArray, val photoLabel: ByteArray, val photoKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfilePictureLabelAndKey) return false

        if (!identity.contentEquals(other.identity)) return false
        if (!photoLabel.contentEquals(other.photoLabel)) return false
        if (!photoKey.contentEquals(other.photoKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identity.contentHashCode()
        result = 31 * result + photoLabel.contentHashCode()
        result = 31 * result + photoKey.contentHashCode()
        return result
    }
}

class ProfilePictureKeyer: Keyer<ProfilePictureLabelAndKey> {
    @OptIn(ExperimentalStdlibApi::class)
    override fun key(data: ProfilePictureLabelAndKey, options: Options): String {
        return data.identity.toHexString() + data.photoLabel.toHexString() + data.photoKey.toHexString()
    }
}

class ProfilePictureFetcher(
    private val profilePictureLabelAndKey: ProfilePictureLabelAndKey,
    private val options: Options,
): Fetcher {
    override suspend fun fetch(): FetchResult? {
        return withContext(Dispatchers.IO) {
            try {
                val jpegBytes: ByteArray? = AppSingleton.getEngine().downloadProfilePicture(profilePictureLabelAndKey.identity, profilePictureLabelAndKey.photoLabel, profilePictureLabelAndKey.photoKey)
                jpegBytes?.let {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(options.context.resources, jpegBytes.inputStream()),
                        isSampled = false,
                        dataSource = DataSource.NETWORK
                    )
                }
            } catch (e: Exception) {
                Logger.x(e)
            }
            return@withContext null
        }
    }

    class Factory: Fetcher.Factory<ProfilePictureLabelAndKey> {
        override fun create(profilePictureLabelAndKey: ProfilePictureLabelAndKey, options: Options, imageLoader: ImageLoader): Fetcher {
            return ProfilePictureFetcher(profilePictureLabelAndKey, options)
        }
    }
}
