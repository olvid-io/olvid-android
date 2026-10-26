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

private const val ENGINE_THREAD_PREFIX = "Engine-"
private const val REPORTED_FRAMES = 8

// The engine names its threads after their operation queues. An uncaught Error in one of them crashes the whole
// process, so a running app never has a dead one: what can go wrong is a thread stuck inside an operation (lock,
// network read without timeout), which the report shows with its stack.
object EngineThreads {
    // busyFrames empty: waiting for work in its loop
    data class ThreadStatus(val name: String, val state: Thread.State, val busyFrames: List<StackTraceElement>)

    fun check(): List<ThreadStatus> =
        Thread.getAllStackTraces()
            .filterKeys { it.name.startsWith(ENGINE_THREAD_PREFIX) }
            .map { (thread, stack) ->
                ThreadStatus(
                    name = thread.name,
                    state = thread.state,
                    busyFrames = stack.takeUnless { it.isIdleLoop() }?.take(REPORTED_FRAMES).orEmpty(),
                )
            }
            .sortedBy { it.name }

    // an idle thread only has its own loop on the stack, a busy one is inside an operation or listener
    private fun Array<StackTraceElement>.isIdleLoop() = count { it.className.startsWith("io.olvid.") && !it.className.contains("\$\$") } <= 1
}
