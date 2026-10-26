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

import io.olvid.messenger.troubleshooting.PushDiagnostics.Outcome
import io.olvid.messenger.troubleshooting.PushDiagnostics.PushEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDiagnosticsTest {
    private val now = 1_800_000_000_000L

    private fun push(ageMillis: Long, high: Boolean = true, outcome: Outcome? = null, delayMillis: Long? = null) = PushEvent(
        receivedAt = now - ageMillis,
        transitMillis = 100,
        highPriority = high,
        deprioritized = false,
        deviceIdle = false,
        screenOn = false,
        coldStart = false,
        maskKnown = true,
        outcome = outcome,
        outcomeDelayMillis = delayMillis,
    )

    @Test
    fun outcomeGoesToLatestPendingHighPriorityPush() {
        val events = mutableListOf(push(5_000), push(3_000), push(1_000, high = false))
        assertTrue(PushDiagnostics.attachOutcome(events, Outcome.NOTIFIED, now))
        assertNull(events[0].outcome)
        assertEquals(Outcome.NOTIFIED, events[1].outcome)
        assertEquals(3_000L, events[1].outcomeDelayMillis)
        assertNull(events[2].outcome)

        // a second notification goes to the older push still waiting for one
        assertTrue(PushDiagnostics.attachOutcome(events, Outcome.DISCUSSION_MUTED, now))
        assertEquals(Outcome.DISCUSSION_MUTED, events[0].outcome)
    }

    @Test
    fun outcomeIgnoresPushesOutsideTheWindow() {
        val events = mutableListOf(push(PushDiagnostics.OUTCOME_WINDOW_MILLIS + 1))
        assertFalse(PushDiagnostics.attachOutcome(events, Outcome.NOTIFIED, now))
        assertNull(events[0].outcome)
    }

    @Test
    fun summaryCountsDelaysAndMissingNotifications() {
        val events = listOf(
            push(8 * 86_400_000L, outcome = Outcome.NOTIFIED, delayMillis = 99_000), // older than 7 days
            push(600_000, outcome = Outcome.NOTIFIED, delayMillis = 400),
            push(500_000, outcome = Outcome.NOTIFIED, delayMillis = 2_000),
            push(400_000, outcome = Outcome.NEUTRAL_NOTIFICATION, delayMillis = 30_000),
            push(300_000, outcome = Outcome.DISCUSSION_OPEN, delayMillis = 100),
            push(200_000), // high priority, nothing displayed
            push(10_000), // high priority, still within the window
            push(5_000, high = false),
        )
        val summary = PushDiagnostics.summarize(events, now)
        assertEquals(7, summary.pushCount)
        assertEquals(6, summary.highPriorityCount)
        assertEquals(2_000L, summary.medianDelayMillis)
        assertEquals(30_000L, summary.maxDelayMillis)
        assertEquals(1, summary.suppressedCount)
        assertEquals(1, summary.withoutNotificationCount)
        assertEquals(now - 5_000, summary.lastPushTimestamp)
    }
}
