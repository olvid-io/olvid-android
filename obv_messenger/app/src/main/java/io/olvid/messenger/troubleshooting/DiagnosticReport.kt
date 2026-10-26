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

package io.olvid.messenger.troubleshooting

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.services.MDMConfigurationSingleton
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val REPORTED_PUSH_EVENTS = 50

// Plain text report meant to be sent to support: no identity, contact or message content
fun Context.buildDiagnosticReport(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US)
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    return buildString {
        appendLine("Olvid diagnostic report, ${dateFormat.format(Date())}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.BRAND} ${Build.MODEL}, Android ${VERSION.RELEASE} (API ${VERSION.SDK_INT})")
        appendLine()

        appendLine("[Push]")
        appendLine("firebase lib: ${BuildConfig.USE_FIREBASE_LIB}, google services: ${GoogleServicesUtils.googleServicesAvailable(this@buildDiagnosticReport)}, push disabled: ${SettingsActivity.disablePushNotifications()}")
        appendLine("permanent foreground service: setting ${SettingsActivity.usePermanentForegroundService()}, running ${UnifiedForegroundService.isInForeground()}")
        appendLine("permanent websocket: ${SettingsActivity.usePermanentWebSocket()}, websocket state: ${AppSingleton.getWebsocketConnectivityStateLiveData().value}")
        appendLine()

        val engineThreads = EngineThreads.check()
        appendLine("[Engine threads] ${engineThreads.count { it.busyFrames.isEmpty() }}/${engineThreads.size} idle")
        engineThreads.filter { it.busyFrames.isNotEmpty() }.forEach { (name, state, busyFrames) ->
            appendLine("$name: $state")
            busyFrames.forEach { appendLine("    at $it") }
        }
        appendLine()

        appendLine("[Notifications]")
        appendLine("enabled: ${NotificationManagerCompat.from(this@buildDiagnosticReport).areNotificationsEnabled()}")
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            appendLine("message channel importance: ${notificationManager?.getNotificationChannel(AndroidNotificationManager.getMessageNotificationChannelId())?.importance}")
        }
        appendLine("interruption filter: ${notificationManager?.currentInterruptionFilter}")
        appendLine()

        appendLine("[Battery]")
        appendLine("ignoring battery optimizations: ${getBatteryOptimizationsState()}, power save: ${powerManager?.isPowerSaveMode}")
        appendLine("background restricted: ${!getBackgroundState()}")
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            appendLine("standby bucket: ${(getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager)?.appStandbyBucket}")
        }
        appendLine()

        val events = PushDiagnostics.getEvents()
        appendLine("[Pushes] ${events.size} recorded, latest first")
        events.asReversed().take(REPORTED_PUSH_EVENTS).forEach { event ->
            append(dateFormat.format(Date(event.receivedAt)))
            append(" | transit ${event.transitMillis} ms")
            append(if (event.highPriority) " | HIGH" else " | normal")
            if (event.deprioritized) append(" | DEPRIORITIZED")
            if (event.deviceIdle) append(" | idle")
            append(if (event.screenOn) " | screen on" else " | screen off")
            if (event.coldStart) append(" | cold start")
            if (!event.maskKnown) append(" | unknown mask")
            event.outcome?.let { outcome ->
                append(" | ${outcome.name} +${event.outcomeDelayMillis} ms")
            }
            appendLine()
        }
    }
}

