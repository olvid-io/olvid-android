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

package io.olvid.messenger.troubleshooting

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.services.AvailableSpaceHelper
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

const val ALARM_CHECK_STATE = "alarm"
const val BACKGROUND_CHECK_STATE = "background"
const val BATTERY_CHECK_STATE = "battery"
const val STORAGE_CHECK_STATE = "storage"
const val SOCKET_CHECK_STATE = "socket"
const val FULL_SCREEN_CHECK_STATE = "full_screen"
const val BACKUP_CHECK_STATE = "backup"
const val LOCATION_CHECK_STATE = "location"

const val MUTE_KEY_PREFIX = "mute_"

@Stable
data class CheckState<K>(val name: String, val troubleshootingDataStore: TroubleshootingDataStore, val statusIsOk: (K) -> Boolean = { (it is Boolean) && it }, val getStatus: () -> K) {
    var status by mutableStateOf(getStatus())
    var valid by mutableStateOf(statusIsOk(getStatus()))

    fun refreshStatus() {
        status = getStatus()
        valid = statusIsOk(getStatus())
    }

    val isMute = troubleshootingDataStore.isMute("$MUTE_KEY_PREFIX$name")
    suspend fun updateMute(mute: Boolean) {
        troubleshootingDataStore.updateMute("$MUTE_KEY_PREFIX$name", mute)
    }
}

@Composable
internal fun LifecycleCheckerEffect(
    checks: List<CheckState<out Any>>,
    lifecycleEvent: Lifecycle.Event = Lifecycle.Event.ON_RESUME
) {
    val checkerObserver = remember(checks) {
        LifecycleEventObserver { _, event ->
            if (event == lifecycleEvent) {
                for (check in checks) {
                    check.refreshStatus()
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, checkerObserver) {
        lifecycle.addObserver(checkerObserver)
        onDispose { lifecycle.removeObserver(checkerObserver) }
    }
}


fun ComponentActivity.getBatteryOptimizationsState() =
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(
            packageName
        ) == true
    } else {
        true
    }

fun ComponentActivity.getAlarmState() =
    if (VERSION.SDK_INT >= VERSION_CODES.S) {
        (getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true
    } else {
        true
    }

fun ComponentActivity.getBackgroundState() =
    if (VERSION.SDK_INT >= VERSION_CODES.P) {
        (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isBackgroundRestricted == false
    } else {
        true
    }

fun getStorageState() =
    (AvailableSpaceHelper.getAvailableSpace()
        ?: Long.MAX_VALUE) > AvailableSpaceHelper.AVAILABLE_SPACE_WARNING_THRESHOLD

fun Context.getPermanentSocketState() =
    if (!BuildConfig.USE_FIREBASE_LIB || !GoogleServicesUtils.googleServicesAvailable(this)) {
        SettingsActivity.usePermanentWebSocket()
    } else {
        true
    }

fun Context.getLocationState() : Boolean {
    return (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false
}

 fun Context.getFullScreenIntentState() : Boolean {
    if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return  getSystemService(
            NotificationManager::class.java
        ).canUseFullScreenIntent()
    }
    return true
}

fun getBackupState(): Int {
    val info: String? = try {
        AppSingleton.getEngine().deviceBackupSeed
    } catch (_: Exception) {
        // this will be retried the next time MainActivity is started
        Logger.e("Unable to retrieve device backup seed")
        return -1
    }
    if (info == null) {
        // no device seed
        try {
            // still using legacy backups
            if (AppSingleton.getEngine().backupKeyInformation != null) {
                return 2
            }
        } catch (_: Exception) {
            Logger.e("Unable to retrieve legacy backup info")
        }
        return 1
    }
    return 0
}

data class BackupStateInfo(val title: String, val description: String, val critical: Boolean)

fun Context.getBackupStateInfo(): BackupStateInfo? {
    var title: Int? = null
    var description: Int = -1
    var critical = false
    try {
        val info: String? = try {
            AppSingleton.getEngine().deviceBackupSeed
        } catch (e: Exception) {
            // this will be retried the next time MainActivity is started
            Logger.x(e)
            return null
        }
        if (info == null) {
            // no device seed
            critical = SettingsActivity.backupsV2Status == SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_NOT_CONFIGURED
            title = R.string.snackbar_message_setup_backup
            description = R.string.dialog_message_setup_backup_explanation
            try {
                // still using legacy backups
                if (AppSingleton.getEngine().backupKeyInformation != null) {
                    title = R.string.troubleshooting_backup_legacy_title
                    description = R.string.enable_backups_message_with_legacy
                }
            } catch (e: Exception) {
                Logger.x(e)
            }
        }
    } catch (e: Exception) {
        Logger.x(e)
    }

    return title?.let {
        BackupStateInfo(getString(title), getString(description), critical)
    }
}

fun ComponentActivity.shouldShowTroubleshootingTip(): Boolean {
    val troubleshootingDataStore = TroubleshootingDataStore(this)
    return listOf(
        CheckState(BATTERY_CHECK_STATE, troubleshootingDataStore) { getBatteryOptimizationsState() },
        CheckState(ALARM_CHECK_STATE, troubleshootingDataStore) { getAlarmState() },
        CheckState(BACKGROUND_CHECK_STATE, troubleshootingDataStore) { getBackgroundState() },
        CheckState(STORAGE_CHECK_STATE, troubleshootingDataStore) { getStorageState() },
        CheckState(SOCKET_CHECK_STATE, troubleshootingDataStore) { getPermanentSocketState() },
        CheckState(FULL_SCREEN_CHECK_STATE, troubleshootingDataStore) { getFullScreenIntentState() },
    ).any { checkState -> checkState.valid.not() && runBlocking { checkState.isMute.first() }.not() }
    .or(
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
     )
}