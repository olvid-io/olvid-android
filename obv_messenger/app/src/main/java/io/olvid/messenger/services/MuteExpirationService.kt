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

package io.olvid.messenger.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.notifications.AndroidNotificationManager
import java.util.Timer
import java.util.TimerTask
import kotlin.collections.mutableListOf

class MuteExpirationService : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == MUTE_EXPIRED_ACTION) {
            App.runThread {
                dispatchAllExpiredMutes()
                scheduleNextExpiration()
            }
        }
    }

    companion object {
        const val MUTE_EXPIRED_ACTION: String = "mute_expired"

        const val PER_DISCUSSION_MESSAGE_NOTIFICATION_LIMIT = 5

        private var scheduledAlarmTimestamp: Long? = null
        private var expireTimer: Timer? = null
        private var expireTimerTask: TimerTask? = null

        // Walks every owned identity that still has a non-null mute start_timestamp and tries to
        // claim the recap dispatch atomically. Used by both the scheduled-alarm path and the
        // app-startup catch-up.
        @JvmStatic
        fun dispatchAllExpiredMutes() {
            val now = System.currentTimeMillis()
            for (ownedIdentity in AppDatabase.getInstance().ownedIdentityDao().getAllWithFiniteMute()) {
                val endTimestamp = ownedIdentity.prefMuteNotificationsTimestamp ?: continue
                if (endTimestamp > now) continue
                claimAndEmit(ownedIdentity.bytesOwnedIdentity, endTimestamp, requireExpired = true)
            }
        }

        // Called when the user manually unmutes their profile. Always clears the mute (so older
        // mutes set before this feature existed still get cleared); only emits a recap when there
        // is a captured start window.
        @JvmStatic
        fun clearAndEmitForManualUnmute(bytesOwnedIdentity: ByteArray) {
            claimAndEmit(bytesOwnedIdentity, System.currentTimeMillis(), requireExpired = false, alwaysClear = true)
        }

        // Atomically reads-and-clears the mute (start_timestamp + flag) in a single Room
        // transaction. Only one caller can observe a non-null start_timestamp; concurrent races
        // (e.g. alarm firing at the same moment as a manual unmute) collapse to a single recap.
        private fun claimAndEmit(bytesOwnedIdentity: ByteArray, endTimestamp: Long, requireExpired: Boolean, alwaysClear: Boolean = false) {
            val db = AppDatabase.getInstance()
            val now = System.currentTimeMillis()
            val claim: Pair<Long, OwnedIdentity>? = db.runInTransaction<Pair<Long, OwnedIdentity>?> {
                val fresh = db.ownedIdentityDao().get(bytesOwnedIdentity) ?: return@runInTransaction null
                val startTimestamp = fresh.prefMuteNotificationsStartTimestamp
                // for the alarm path, require the mute to actually have expired; otherwise we'd
                // wipe a still-active mute when this runs after the user extended it
                if (requireExpired) {
                    val end = fresh.prefMuteNotificationsTimestamp ?: return@runInTransaction null
                    if (end > now) return@runInTransaction null
                }
                // clear the mute when leaving it for good: explicitly (manual unmute, alwaysClear) or
                // because it has expired (requireExpired passed above). This also self-heals legacy mutes
                // set before this feature existed (start_timestamp == null): we clear the stale flag so
                // getAllWithFiniteMute() stops re-scanning them, and simply emit no recap (no captured window).
                val shouldClear = alwaysClear || requireExpired
                if (startTimestamp == null && !shouldClear) {
                    return@runInTransaction null
                }
                db.ownedIdentityDao().clearMuteNotifications(bytesOwnedIdentity)
                // reflect the cleared state on the snapshot we'll pass to displayReceivedMessageNotification
                fresh.prefMuteNotifications = false
                fresh.prefMuteNotificationsTimestamp = null
                fresh.prefMuteNotificationsStartTimestamp = null
                if (startTimestamp == null) null else (startTimestamp to fresh)
            }
            if (claim != null) {
                emitCatchUp(claim.second, claim.first, endTimestamp)
            }
        }

        private fun emitCatchUp(ownedIdentity: OwnedIdentity, startTimestamp: Long, endTimestamp: Long) {
            if (startTimestamp >= endTimestamp) return
            val db = AppDatabase.getInstance()
            val messages = db.messageDao()
                .getInboundMessagesReceivedInWindow(ownedIdentity.bytesOwnedIdentity, startTimestamp, endTimestamp)

            val messageMap: MutableMap<Long, MutableList<Message>> = mutableMapOf()

            // first split messages by discussion
            for (message in messages) {
                // do not re-notify for messages where I am mentioned if mentioned messages were not muted
                if (message.mentioned && ownedIdentity.prefMuteNotificationsExceptMentioned) continue
                messageMap.getOrPut(message.discussionId) { mutableListOf() }
                    .add(message)
            }

            messageMap.forEach { (discussionId, messages) ->
                val discussion = db.discussionDao().getById(discussionId) ?: return@forEach
                // skip discussions that are still muted at their own level — those weren't "missed
                // because of profile mute", they would have been silenced regardless
                val discussionCustomization = db.discussionCustomizationDao().get(discussion.id)

                // if all notifications should be muted (even for mentioned messages), do not notify
                if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications(true)) {
                    return@forEach
                }

                // if only mentioned messages should be notified, filter the messages
                val filteredMessages = if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications(false))
                    messages.filter { it.mentioned }
                else
                    messages

                // now take the last 5 received messages and show a notification
                filteredMessages.takeLast(PER_DISCUSSION_MESSAGE_NOTIFICATION_LIMIT).forEach { message ->
                    val contact = db.contactDao().get(ownedIdentity.bytesOwnedIdentity, message.senderIdentifier)
                    AndroidNotificationManager.displayReceivedMessageNotification(discussion, message, contact, ownedIdentity)
                }
            }
        }

        @JvmStatic
        fun scheduleNextExpiration() {
            try {
                val now = System.currentTimeMillis()
                val nextExpirationTimestamp = AppDatabase.getInstance().ownedIdentityDao().getNextMuteExpirationAfter(now)
                if (scheduledAlarmTimestamp == nextExpirationTimestamp) {
                    return
                }
                scheduledAlarmTimestamp = nextExpirationTimestamp

                scheduledAlarmTimestamp?.let { timestamp ->
                    if (expireTimer == null) {
                        expireTimer = Timer("MuteExpirationServiceTimer")
                    }
                    expireTimerTask?.cancel()
                    val task = object : TimerTask() {
                        override fun run() {
                            dispatchAllExpiredMutes()
                            scheduleNextExpiration()
                        }
                    }
                    expireTimerTask = task
                    // +10ms so the comparison `timestamp <= now` is true when we fire
                    val delay = (timestamp - System.currentTimeMillis() + 10).coerceAtLeast(0)
                    expireTimer!!.schedule(task, delay)
                }

                val intent = Intent(MUTE_EXPIRED_ACTION, null, App.getContext(), MuteExpirationService::class.java)
                val pendingIntent = PendingIntent.getBroadcast(App.getContext(), 0, intent, PendingIntent.FLAG_MUTABLE)
                val alarmManager = App.getContext().getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

                alarmManager.cancel(pendingIntent)
                scheduledAlarmTimestamp?.let { ts ->
                    Logger.d("MuteExpirationService - Scheduling mute-end recap at $ts")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, ts, pendingIntent)
                    } else {
                        Logger.e("Missing exact alarm permission - Using approximate alarm")
                        alarmManager.set(AlarmManager.RTC_WAKEUP, ts, pendingIntent)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
