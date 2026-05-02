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

package io.olvid.messenger.discussion.compose

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.collections.orEmpty
import kotlin.math.sqrt

object WaveformExtractor {
    fun serializeAmplitudes(amplitudes: List<Float>): ByteArray? {
        return runCatching {
            AppSingleton.getJsonObjectMapper().writeValueAsBytes(amplitudes.map { (it * 100f).toInt() })
        }.getOrNull()
    }

    fun deserializeAmplitudes(serialized: ByteArray): List<Float> {
        return runCatching {
            AppSingleton.getJsonObjectMapper().readValue(serialized, IntArray::class.java)
                .map { it / 100f }
        }.getOrDefault(emptyList())
    }

    suspend fun getCachedAmplitudesOrExtracted(fyleAndStatus: FyleMessageJoinWithStatusDao.FyleAndStatus): List<Float> {
        return if (fyleAndStatus.fyleMessageJoinWithStatus.miniPreview != null) {
            deserializeAmplitudes(fyleAndStatus.fyleMessageJoinWithStatus.miniPreview!!)
        } else {
            fyleAndStatus.fyle.filePath?.let {
                val amplitudes = extractWaveform(
                    File(
                        App.absolutePathFromRelative(
                            it
                        )!!
                    ), 50
                )
                if (amplitudes.isNotEmpty()) {
                    fyleAndStatus.fyleMessageJoinWithStatus.miniPreview = serializeAmplitudes(amplitudes)
                    App.runThread {
                        AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                            .updateMiniPreview(fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyleMessageJoinWithStatus.fyleId, fyleAndStatus.fyleMessageJoinWithStatus.miniPreview)
                    }
                }
                amplitudes
            }.orEmpty()
        }
    }

    suspend fun extractWaveform(audioFile: File, expectedPoints: Int): List<Float> =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                runCatching {
                    extractor.setDataSource(audioFile.absolutePath)
                }.onFailure {
                    return@withContext emptyList()
                }

                var trackIndex = -1
                var format: MediaFormat? = null
                var mime = ""

                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                    if (trackMime.startsWith("audio/")) {
                        trackIndex = i
                        format = trackFormat
                        mime = trackMime
                        break
                    }
                }

                if (trackIndex < 0) {
                    return@withContext emptyList()
                }

                extractor.selectTrack(trackIndex)

                val codec = runCatching {
                    MediaCodec.createDecoderByType(mime)
                }.getOrElse {
                    return@withContext emptyList()
                }

                try {
                    runCatching {
                        codec.configure(format, null, null, 0)
                        codec.start()
                    }.onFailure {
                        return@withContext emptyList()
                    }

                    // we want to compute expectedPoints samples of .5s each,
                    // or, if the media stream is too short, split it in expectedPoints shorter intervals
                    val durationUs = runCatching {
                        format!!.getLong(MediaFormat.KEY_DURATION)
                    }.getOrElse {
                        return@withContext emptyList()
                    }

                    val halfGap: Long = ((durationUs - expectedPoints * 500_000L) / expectedPoints / 2).coerceAtLeast(0L)
                    // we pick an interval in the center of each 1/50th of the total duration
                    val sampleTimeIntervalsUs: List<Pair<Long, Long>> = List(expectedPoints) { i ->
                        (i * durationUs / expectedPoints + halfGap) to ((i + 1) * durationUs / expectedPoints - halfGap)
                    }
                    val samples = mutableListOf<Float>()

                    val bufferInfo = MediaCodec.BufferInfo()
                    sampleTimeIntervalsUs.forEach { (startTimeUs, endTimeUs) ->
                        var reachedEndOfInput = false
                        var endTimeReached = false

                        // we accumulate a sum of squares in this
                        var sampleAmplitudeAccumulator = 0L
                        var sampleCount = 0

                        runCatching {
                            var retryCount = 0
                            val maxRetries = 50 // Avoid infinite loops if codec is stuck

                            runCatching {
                                // flush to discard any previously enqueued input samples
                                codec.flush()
                            }
                            // seek to startTime
                            extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                            // extract data until the endTime is reached
                            while (!endTimeReached) {
                                if (!reachedEndOfInput) {
                                    val inputBufferIndex = codec.dequeueInputBuffer(1_000L)
                                    if (inputBufferIndex >= 0) {
                                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                                        if (inputBuffer != null) {
                                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                            if (sampleSize < 0) {
                                                codec.queueInputBuffer(
                                                    inputBufferIndex,
                                                    0,
                                                    0,
                                                    0L,
                                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                                )
                                                reachedEndOfInput = true
                                            } else {
                                                codec.queueInputBuffer(
                                                    inputBufferIndex,
                                                    0,
                                                    sampleSize,
                                                    extractor.sampleTime,
                                                    0
                                                )
                                                extractor.advance()
                                            }
                                        }
                                    }
                                }

                                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                                if (outputBufferIndex >= 0) {
                                    retryCount = 0 // Reset retry on successful output

                                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                        endTimeReached = true
                                    }
                                    if (bufferInfo.size > 0) {
                                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                                        if (outputBuffer != null) {
                                            if (bufferInfo.presentationTimeUs in startTimeUs until endTimeUs) {
                                                outputBuffer.position(bufferInfo.offset)
                                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                                while (outputBuffer.remaining() >= 2) {
                                                    val sampleVal = outputBuffer.short

                                                    sampleAmplitudeAccumulator += sampleVal * sampleVal
                                                    sampleCount++
                                                }
                                            }
                                        }
                                    }
                                    codec.releaseOutputBuffer(outputBufferIndex, false)

                                    if (bufferInfo.presentationTimeUs > endTimeUs) {
                                        endTimeReached = true
                                    }
                                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    if (reachedEndOfInput) {
                                        retryCount++
                                        if (retryCount > maxRetries) {
                                            break
                                        }
                                    }
                                }
                            }

                            samples.add(
                                if (sampleCount == 0)
                                    0f
                                else
                                    sqrt(sampleAmplitudeAccumulator.toFloat() / sampleCount)
                            )
                        }.onFailure { e ->
                            Logger.x(e)
                        }
                    }

                    // Release resources
                    runCatching {
                        codec.stop()
                    }

                    // normalize the samples
                    val max = samples.max()
                    return@withContext samples.map {
                        it / max
                    }
                } finally {
                    codec.release()
                }
            } finally {
                extractor.release()
            }
        }

    fun resample(source: List<Float>, targetCount: Int): List<Float> {
        if (source.isEmpty()) return emptyList()

        val result = ArrayList<Float>(targetCount)
        val step = source.size.toFloat() / targetCount

        for (i in 0 until targetCount) {
            val start = (i * step).toInt()
            val end = ((i + 1) * step).toInt().coerceAtMost(source.size)

            if (start >= end) {
                // Upsampling / stretching case: range is empty, so we pick the nearest index
                val index = (i * step).toInt().coerceIn(0, source.lastIndex)
                result.add(source[index])
                continue
            }

            var maxVal = 0f
            for (j in start until end) {
                if (source[j] > maxVal) maxVal = source[j]
            }
            result.add(maxVal)
        }
        return result
    }
}
