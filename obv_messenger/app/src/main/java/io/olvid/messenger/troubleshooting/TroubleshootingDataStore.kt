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

package io.olvid.messenger.troubleshooting

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import io.olvid.engine.Logger
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class TroubleshootingDataStore(private val context: Context) {
    companion object {
        private val Context.troubleshootingDataStore by preferencesDataStore("troubleshooting")
    }

    suspend fun load() = context.troubleshootingDataStore.data.catch { exception ->
        if (exception is IOException) {
            Logger.e("troubleshootingDatastore", exception)
        } else {
            Logger.e("unable to load troubleshootingDatastore")
        }
        emit(emptyPreferences())
    }.first()

    fun isMute(item: String) = context.troubleshootingDataStore.data.catch { exception ->
        if (exception is IOException) {
            Logger.e("troubleshootingDatastore", exception)
        } else {
            Logger.e("unable to read troubleshootingDatastore")
        }
        emit(emptyPreferences())
    }.map { preferences ->
        preferences[booleanPreferencesKey(item)] ?: false
    }

    suspend fun updateMute(item: String, value: Boolean) {
        try {
            context.troubleshootingDataStore.edit { preferences ->
                preferences[booleanPreferencesKey(item)] = value
            }
        } catch (exception: Exception) {
        }
    }
}