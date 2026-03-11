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

package io.olvid.messenger.history_transfer.steps

import io.olvid.engine.Logger
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import java.util.UUID


fun Discussion.computeMessageRanges(db: AppDatabase): Map<ObvBytesKey, Map<String, List<List<Long>>>> {
    val rangesByThreadAndSender = mutableMapOf<ObvBytesKey, Map<String, List<List<Long>>>>()

    var currentSenderIdentifier : ByteArray? = null
    var currentSenderMap: MutableMap<String, List<List<Long>>>? = null
    var currentSenderThreadIdentifier : UUID? = null
    var currentThreadList: MutableList<List<Long>>? = null
    var currentSequenceNumberStride: MutableList<Long>? = null
    db.messageDao().getAllTransferableForDiscussion(this.id).forEach { message ->
        if (message.senderIdentifier.contentEquals(currentSenderIdentifier).not()) {
            currentSenderIdentifier = message.senderIdentifier
            currentSenderMap = mutableMapOf()
            rangesByThreadAndSender[ObvBytesKey(currentSenderIdentifier)] = currentSenderMap
            currentSenderThreadIdentifier = null
        }
        if (message.senderThreadIdentifier != currentSenderThreadIdentifier) {
            currentSenderThreadIdentifier = message.senderThreadIdentifier
            currentThreadList = mutableListOf()
            currentSenderMap?.put(Logger.getUuidString(currentSenderThreadIdentifier), currentThreadList)
            currentSequenceNumberStride = null
        }
        if (currentSequenceNumberStride != null && (message.senderSequenceNumber - 1 < currentSequenceNumberStride[1])) {
            // this only happens if messages were not properly sorted by the DB query, or if we have duplicate sequence numbers
            // ==> simply ignore this message by doing nothing
        } else if (message.senderSequenceNumber - 1 == currentSequenceNumberStride?.get(1)) {
            currentSequenceNumberStride[1]++
        } else {
            currentSequenceNumberStride = mutableListOf(message.senderSequenceNumber, message.senderSequenceNumber)
            currentThreadList?.add(currentSequenceNumberStride)
        }
    }
    return rangesByThreadAndSender
}

fun computeDiscussionRangesDiff(src: Map<ObvBytesKey, Map<String, List<List<Long>>>>, known: Map<ObvBytesKey, Map<String, List<List<Long>>>>): Map<ObvBytesKey, Map<String, List<List<Long>>>> {
    val rangesByThreadAndSender = mutableMapOf<ObvBytesKey, Map<String, List<List<Long>>>>()

    src.entries.forEach { (obvBytesKey, threadsMap) ->
        val knownThreadMap = known[obvBytesKey] ?: run {
            rangesByThreadAndSender[obvBytesKey] = threadsMap
            return@forEach
        }

        val rangesByThread = mutableMapOf<String, List<List<Long>>>()
        rangesByThreadAndSender[obvBytesKey] = rangesByThread

        threadsMap.entries.forEach { (threadId, ranges) ->
            val knownRanges = knownThreadMap[threadId] ?: run {
                rangesByThread[threadId] = ranges
                return@forEach
            }

            val rangesList = mutableListOf<List<Long>>()
            rangesByThread[threadId] = rangesList

            val knownIterator = knownRanges.iterator()
            var currentKnown = if (knownIterator.hasNext()) knownIterator.next() else null

            ranges.forEach { range ->
                var currentRange: MutableList<Long>? = range.toMutableList()
                while (currentRange != null && currentKnown != null) {
                    if (currentRange[1] < currentKnown[0]) {
                        // the range we are looking at is entirely before our known range
                        break
                    }
                    if (currentRange[0] < currentKnown[0]) {
                        // part of the range we are looking at is already known
                        // --> add the beginning unknown part
                        rangesList.add(listOf(currentRange[0], currentKnown[0] - 1))
                        // --> see what we need to keep of this range
                        if (currentRange[1] > currentKnown[1]) {
                            // the range we are looking at goes further than our known range
                            // --> update the current range
                            currentRange[0] = currentKnown[1] + 1
                            // --> go to the next knownRange
                            currentKnown = if (knownIterator.hasNext()) knownIterator.next() else null
                            continue
                        } else {
                            // the rest of the range we are looking at is entirely known
                            // --> skip the rest
                            currentRange = null
                            break
                        }
                    }

                    // we know the start of the current known range is before (or equal) to the start of the range we are looking at
                    if (currentKnown[1] < currentRange[0]) {
                        // our known range is entirely before the range we are looking at
                        // --> go to the next known range
                        currentKnown = if (knownIterator.hasNext()) knownIterator.next() else null
                        continue
                    }
                    if (currentKnown[1] < currentRange[1]) {
                        // part of the range we are looking at is already known
                        // --> update the start of the range
                        currentRange[0] = currentKnown[1] + 1
                        // --> go to the next known range
                        currentKnown = if (knownIterator.hasNext()) knownIterator.next() else null
                        continue
                    }

                    // the range we are looking at is entirely inside
                    currentRange = null
                }

                if (currentRange != null) {
                    rangesList.add(currentRange)
                }
            }
        }
    }

    return rangesByThreadAndSender
}

fun countMessagesInRanges(rangesByThreadAndSender:  Map<ObvBytesKey, Map<String, List<List<Long>>>>): Int {
    return rangesByThreadAndSender.values.sumOf { rangesByThread ->
        rangesByThread.values.sumOf { ranges ->
            ranges.sumOf { range -> (range[1] - range[0] + 1).toInt() }
        }
    }
}