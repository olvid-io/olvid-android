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

import android.content.Context
import android.os.PowerManager
import io.olvid.engine.Logger
import io.olvid.messenger.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

// Keeps, across process restarts, a timeline of the received pushes and of what each high priority push led to
object PushDiagnostics {
    private const val FILE_NAME = "push_diagnostics.json"
    private const val MAX_EVENTS = 200
    // a high priority push not followed by a notification decision within this delay counts as "no notification"
    const val OUTCOME_WINDOW_MILLIS = 120_000L
    private const val COLD_START_UPTIME_MILLIS = 30_000L
    private const val SUMMARY_PERIOD_MILLIS = 7 * 86_400_000L

    enum class Outcome(val displayed: Boolean) {
        NOTIFIED(true),
        NEUTRAL_NOTIFICATION(true),
        DISCUSSION_OPEN(false),
        DISCUSSION_MUTED(false),
        PROFILE_MUTED(false),
    }

    data class PushEvent(
        val receivedAt: Long,
        // from FCM accepting the push to its reception
        val transitMillis: Long,
        val highPriority: Boolean,
        val deprioritized: Boolean,
        val deviceIdle: Boolean,
        val screenOn: Boolean,
        val coldStart: Boolean,
        val maskKnown: Boolean,
        val outcome: Outcome? = null,
        val outcomeDelayMillis: Long? = null,
    )

    data class Summary(
        val lastPushTimestamp: Long?,
        val pushCount: Int,
        val highPriorityCount: Int,
        val deprioritizedCount: Int,
        val dozeCount: Int,
        val coldStartCount: Int,
        val medianDelayMillis: Long?,
        val maxDelayMillis: Long?,
        val suppressedCount: Int,
        val withoutNotificationCount: Int,
    )

    // single thread that exits when idle: pushes are rare
    private val executor = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { Thread(it, "PushDiagnostics") }
        .apply { allowCoreThreadTimeOut(true) }
    private val events = ArrayList<PushEvent>()
    private var loaded = false
    private var pushReceivedSinceProcessStart = false

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary.asStateFlow()

    @JvmStatic
    fun recordPush(sentAt: Long, highPriority: Boolean, deprioritized: Boolean, maskKnown: Boolean) {
        val receivedAt = System.currentTimeMillis()
        val powerManager = App.getContext().getSystemService(Context.POWER_SERVICE) as? PowerManager
        val coldStart = !pushReceivedSinceProcessStart && receivedAt - App.appStartTimestamp < COLD_START_UPTIME_MILLIS
        pushReceivedSinceProcessStart = true
        val event = PushEvent(
            receivedAt = receivedAt,
            transitMillis = receivedAt - sentAt,
            highPriority = highPriority,
            deprioritized = deprioritized,
            deviceIdle = powerManager?.isDeviceIdleMode == true,
            screenOn = powerManager?.isInteractive == true,
            coldStart = coldStart,
            maskKnown = maskKnown,
        )
        executor.execute {
            ensureLoaded()
            events.add(event)
            while (events.size > MAX_EVENTS) {
                events.removeAt(0)
            }
            save()
        }
    }

    // attach the decision to the latest high priority push still waiting for one
    @JvmStatic
    fun recordMessageNotification(outcome: Outcome) {
        val now = System.currentTimeMillis()
        executor.execute {
            ensureLoaded()
            if (attachOutcome(events, outcome, now)) {
                save()
            }
        }
    }

    // loads the timeline if needed and publishes its summary
    fun refresh() {
        executor.execute {
            ensureLoaded()
            _summary.value = summarize(events, System.currentTimeMillis())
        }
    }

    // blocking: call from a background thread
    fun getEvents(): List<PushEvent> {
        return executor.submit<List<PushEvent>> {
            ensureLoaded()
            events.toList()
        }.get()
    }

    internal fun attachOutcome(events: MutableList<PushEvent>, outcome: Outcome, now: Long): Boolean {
        val index = events.indexOfLast { it.highPriority && it.outcome == null && now - it.receivedAt < OUTCOME_WINDOW_MILLIS }
        if (index == -1) {
            return false
        }
        events[index] = events[index].copy(outcome = outcome, outcomeDelayMillis = now - events[index].receivedAt)
        return true
    }

    internal fun summarize(events: List<PushEvent>, now: Long): Summary {
        val recent = events.filter { now - it.receivedAt < SUMMARY_PERIOD_MILLIS }
        val high = recent.filter { it.highPriority }
        val delays = high.mapNotNull { event -> event.outcomeDelayMillis.takeIf { event.outcome?.displayed == true } }.sorted()
        return Summary(
            lastPushTimestamp = events.lastOrNull()?.receivedAt,
            pushCount = recent.size,
            highPriorityCount = high.size,
            deprioritizedCount = high.count { it.deprioritized },
            dozeCount = recent.count { it.deviceIdle },
            coldStartCount = recent.count { it.coldStart },
            medianDelayMillis = delays.getOrNull(delays.size / 2),
            maxDelayMillis = delays.lastOrNull(),
            suppressedCount = high.count { it.outcome?.displayed == false },
            withoutNotificationCount = high.count { it.outcome == null && now - it.receivedAt >= OUTCOME_WINDOW_MILLIS },
        )
    }

    private fun file() = File(App.getContext().noBackupFilesDir, FILE_NAME)

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        loaded = true
        runCatching {
            val file = file()
            if (file.exists()) {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    events.add(array.getJSONObject(i).toPushEvent())
                }
            }
        }.onFailure { Logger.x(it) }
    }

    private fun save() {
        runCatching {
            val array = JSONArray()
            events.forEach { array.put(it.toJson()) }
            file().writeText(array.toString())
        }.onFailure { Logger.x(it) }
        _summary.value = summarize(events, System.currentTimeMillis())
    }

    private fun PushEvent.toJson() = JSONObject().apply {
        put("received", receivedAt)
        put("transit", transitMillis)
        put("high", highPriority)
        put("deprioritized", deprioritized)
        put("idle", deviceIdle)
        put("screen", screenOn)
        put("cold", coldStart)
        put("mask", maskKnown)
        outcome?.let { put("outcome", it.name) }
        outcomeDelayMillis?.let { put("outcomeDelay", it) }
    }

    private fun JSONObject.toPushEvent() = PushEvent(
        receivedAt = getLong("received"),
        transitMillis = optLong("transit"),
        highPriority = optBoolean("high"),
        deprioritized = optBoolean("deprioritized"),
        deviceIdle = optBoolean("idle"),
        screenOn = optBoolean("screen"),
        coldStart = optBoolean("cold"),
        maskKnown = optBoolean("mask", true),
        outcome = optString("outcome").takeIf { it.isNotEmpty() }?.let { name -> Outcome.entries.firstOrNull { it.name == name } },
        outcomeDelayMillis = if (has("outcomeDelay")) getLong("outcomeDelay") else null,
    )
}
