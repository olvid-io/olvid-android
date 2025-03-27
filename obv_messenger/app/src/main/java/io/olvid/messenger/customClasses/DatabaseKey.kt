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

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.MasterKey.Builder
import androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import java.security.SecureRandom

class DatabaseKey {

    companion object {
        const val ENGINE_DATABASE_SECRET = "engine_database_secret"
        const val APP_DATABASE_SECRET =  "app_database_secret"
        @OptIn(ExperimentalStdlibApi::class)
        private fun generateHexKey() = ByteArray(32).apply { SecureRandom().nextBytes(this) }.toHexString()

        @JvmStatic
        fun get(keystoreKey: String): String? {
            var dbKey : String? = BuildConfig.HARDCODED_DATABASE_SECRET
            if (dbKey == null) {
                try {
                    val masterKey = Builder(App.getContext())
                        .setKeyScheme(AES256_GCM)
                        .build()
                    val sharedPreferences = EncryptedSharedPreferences.create(
                        App.getContext(),
                        App.getContext().getString(R.string.preference_filename_database),
                        masterKey,
                        AES256_SIV,
                        PrefValueEncryptionScheme.AES256_GCM
                    )
                    dbKey = sharedPreferences.getString(keystoreKey, null)
                    if (dbKey == null) {
                        dbKey = "x'${generateHexKey()}'"
                        sharedPreferences.edit().putString(keystoreKey, dbKey).apply()
                    }
                } catch (e: Exception) {
                   Logger.w("Unable to generate db secret key")
                }
            }
            return dbKey
        }
    }
}