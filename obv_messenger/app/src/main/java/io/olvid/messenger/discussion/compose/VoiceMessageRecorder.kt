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
package io.olvid.messenger.discussion.compose

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioEncoder
import android.media.MediaRecorder.AudioSource
import android.media.MediaRecorder.OutputFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.R.string
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.discussion.message.attachments.Attachment
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.math.log2

class VoiceMessageRecorder(
    private val activity: FragmentActivity,
    private val composeMessageViewModel: ComposeMessageViewModel
) {
    enum class State {
        IDLE,
        RECORDING,
        PAUSED,
        SAVING
    }

    var state by mutableStateOf(State.IDLE)
        private set

    val isOpened: Boolean
        get() = state != State.IDLE

    val isPaused: Boolean
        get() = state == State.PAUSED

    val isSaving: Boolean
        get() = state == State.SAVING

    val isRecording: Boolean
        get() = state == State.RECORDING


    private var recordPermission: Boolean
    private var mediaRecorder: MediaRecorder? = null
    var draftAudioFile by mutableStateOf<File?>(null)
        private set
    private var currentlyRecordingAudioFile: File? = null
    private var ignoreNullDraft = false

    private val timer: Timer
    private var startRecordTask: TimerTask? = null
    private var sampleTask: TimerTask? = null

    private var wakeLock: WakeLock? = null
    private var discardRequested = false

    var soundWave by mutableStateOf(SampleAndTicker())

    companion object {
        const val SAMPLE_INTERVAL: Long = 102
        const val AUDIO_FILE_NAME_SUFFIX = ".olvidaudio"
    }

    init {
        recordPermission = ContextCompat.checkSelfPermission(
            activity,
            permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        timer = Timer("DiscussionActivity-VoiceMessageTimer")
    }

    fun setRecordPermission(recordPermission: Boolean) {
        this.recordPermission = recordPermission
    }

    suspend fun loadFromDraft(attachment: Attachment?) {
        if (attachment?.fyle?.filePath == null) {
            if (!ignoreNullDraft) {
                soundWave = SampleAndTicker()
                draftAudioFile = null
                state = State.IDLE
            }
            return
        }

        val draftFile = File(App.absolutePathFromRelative(attachment.fyle.filePath)!!)
        val amplitudes = WaveformExtractor.getCachedAmplitudesOrExtracted(
            FyleMessageJoinWithStatusDao.FyleAndStatus().apply {
                fyle = attachment.fyle
                fyleMessageJoinWithStatus = attachment.fyleMessageJoinWithStatus
            })
        draftAudioFile = draftFile
        soundWave = SampleAndTicker(amplitudes, 0)
        ignoreNullDraft = false
        state = State.PAUSED
    }

    // return false if permissions are wrong
    fun startRecord(): Boolean {
        if (!recordPermission || state != State.IDLE || draftAudioFile != null) {
            return false
        }
        Logger.d("🔊 Start recording voice message")
        state = State.RECORDING // Set immediately to disable Mic button
        startRecordTask = object : TimerTask() {
            @SuppressLint("WakelockTimeout")
            override fun run() {
                if (state != State.RECORDING) return

                soundWave = SampleAndTicker()
                discardRequested = false
                composeMessageViewModel.discussionId?.let {
                    App.runThread {
                        deleteVoiceRecordingDraftSync(it)
                    }
                }

                val cacheDir = File(activity.cacheDir, App.CAMERA_PICTURE_FOLDER)
                cacheDir.mkdirs()
                currentlyRecordingAudioFile = getRandomTmpFile()
                mediaRecorder?.release()
                mediaRecorder = createMediaRecorder(currentlyRecordingAudioFile!!)

                runCatching {
                    mediaRecorder?.prepare()
                    mediaRecorder?.start()
                }.onFailure {
                    App.toast(
                        string.toast_message_voice_message_recording_failed,
                        Toast.LENGTH_SHORT
                    )
                    mediaRecorder?.release()
                    mediaRecorder = null
                    state = State.IDLE
                    return
                }

                sampleTask = acquireLockAndCreateSampleTask()
                timer.schedule(sampleTask, 0, SAMPLE_INTERVAL / SampleAndTicker.TICKS_PER_SAMPLE)
            }
        }
        timer.schedule(startRecordTask, 250)
        return true
    }

    private fun acquireLockAndCreateSampleTask() : TimerTask {
        // prevent app from sleeping and screen from turning off
        Handler(Looper.getMainLooper()).post {
            activity.window?.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (wakeLock == null) {
            (activity.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.let { powerManager ->
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Olvid:VoiceMessageRecording"
                ).apply { acquire() }
            }
        }

        // create the sampling timertask
        return object : TimerTask() {
            var started = false
            override fun run() {
                if (mediaRecorder == null || !isRecording) {
                    cancel()
                }
                soundWave = soundWave.inc()
                if (soundWave.ticker >= SampleAndTicker.TICKS_PER_SAMPLE) {
                    runCatching {
                        val amplitude =
                            (log2(mediaRecorder!!.maxAmplitude.toDouble()).toFloat() - 9).coerceAtLeast(
                                0f
                            ) / 6
                        if (!started && amplitude > 0) {
                            started = true
                        }
                        soundWave = soundWave.add(amplitude)
                    }.onFailure {
                        cancel()
                    }
                }
            }
        }
    }



    fun resumeRecord() {
        if (state == State.RECORDING || state == State.SAVING) return
        state = State.RECORDING
        Logger.d("🔊 Resume recording voice message")

        currentlyRecordingAudioFile = getRandomTmpFile()

        mediaRecorder?.release()
        mediaRecorder = createMediaRecorder(currentlyRecordingAudioFile!!)
        runCatching {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
        }.onFailure {
            App.toast(
                string.toast_message_voice_message_recording_failed,
                Toast.LENGTH_SHORT
            )
            mediaRecorder?.release()
            mediaRecorder = null
            state = State.PAUSED
            return
        }

        sampleTask = acquireLockAndCreateSampleTask()
        timer.schedule(sampleTask, 0, SAMPLE_INTERVAL / SampleAndTicker.TICKS_PER_SAMPLE)
    }

    fun stopRecord(discard: Boolean, async: Boolean = true) {
        // release wake locks
        Handler(Looper.getMainLooper()).post {
            activity.window?.clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        wakeLock?.let {
            it.release()
            wakeLock = null
        }
        sampleTask?.cancel()
        sampleTask = null
        startRecordTask?.cancel()
        startRecordTask = null

        if (discard) {
            discardRequested = true
        }
        if (isSaving && !discard) {
            // Already saving and not a discard request, ignore
            return
        }
        if (isRecording || isPaused || isSaving) {
            Logger.d("🔊 Saving recorded voice message")
            state = State.SAVING
            if (async) {
                App.runThread {
                    stopTask(discard)
                }
            } else {
                stopTask(discard)
            }
        }
    }

    private fun stopTask(discard: Boolean) {
        if (!isSaving) {
            return
        }
        var returnToIdle = discard
        try {
            runCatching {
                mediaRecorder?.stop()
            }
            mediaRecorder?.release()
            mediaRecorder = null

            val discussionId = composeMessageViewModel.discussionId ?: return

            val newSegment: File
            val oldSegment: File?
            if (discard) {
                runCatching {
                    currentlyRecordingAudioFile?.delete()
                }
                deleteVoiceRecordingDraftSync(discussionId)
                return
            } else {
                // if new segement it empty, there is nothing special to do
                newSegment =
                    currentlyRecordingAudioFile?.takeIf { it.exists() && it.length() > 0 } ?: run {
                        returnToIdle = draftAudioFile == null
                        runCatching {
                            currentlyRecordingAudioFile?.delete()
                        }
                        return
                    }
                oldSegment = draftAudioFile?.takeIf { it.exists() && it.length() > 0 }
            }

            if (oldSegment == null) {
                // easy case, there was no draft, so no need to merge anything
                AddFyleToDraftFromUriTask(
                    newSegment,
                    getAudioFileName(),
                    "audio/x-m4a",
                    discussionId
                ).apply {
                    setMiniPreview(WaveformExtractor.serializeAmplitudes(soundWave.samples))
                }.run()
            } else {
                // merge draft and new segment
                runCatching {
                    mergeSegments(listOf(oldSegment, newSegment))
                }.getOrNull()?.let { mergedFile ->
                    runCatching {
                        // delete the new segement
                        newSegment.delete()
                    }
                    if (discardRequested) return

                    // delete any draft
                    ignoreNullDraft = true
                    deleteVoiceRecordingDraftSync(discussionId)

                    AddFyleToDraftFromUriTask(
                        mergedFile,
                        getAudioFileName(),
                        "audio/x-m4a",
                        discussionId
                    ).apply {
                        setMiniPreview(WaveformExtractor.serializeAmplitudes(soundWave.samples))
                    }.run()
                }
            }
        } finally {
            if (discard) {
                discardRequested = false
            }
            currentlyRecordingAudioFile = null
            state = if (returnToIdle) State.IDLE else State.PAUSED
        }
    }

    private fun getRandomTmpFile() : File {
        val cacheDir = File(activity.cacheDir, App.CAMERA_PICTURE_FOLDER)
        return File(
            cacheDir,
            Logger.getUuidString(UUID.randomUUID()) + ".m4a"
        )
    }

    private fun getAudioFileName() : String {
        return SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH)
            .format(Date()) + "$AUDIO_FILE_NAME_SUFFIX.m4a"
    }

    @SuppressLint("WrongConstant")
    private fun mergeSegments(segments: List<File>): File {
        Logger.d("🔊 Merging voice message parts")

        val outputFile = getRandomTmpFile()

        val muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        var offsetUs = 0L

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        for (segment in segments) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(segment.path)

                var trackIndex = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        trackIndex = i
                        if (audioTrackIndex == -1) {
                            audioTrackIndex = muxer.addTrack(format)
                            muxer.start()
                        }
                        break
                    }
                }

                if (trackIndex == -1) continue

                extractor.selectTrack(trackIndex)
                var lastPresentationTimeUs = 0L

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = offsetUs + extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                    lastPresentationTimeUs = extractor.sampleTime
                    extractor.advance()
                }

                offsetUs += lastPresentationTimeUs + 1000 // Add 1ms gap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                extractor.release()
            }
        }

        try {
            muxer.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            muxer.release()
        }
        return outputFile
    }

    private fun createMediaRecorder(file: File): MediaRecorder {
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(activity)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(AudioSource.DEFAULT)
            setOutputFormat(OutputFormat.MPEG_4)
            setAudioEncoder(AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioEncodingBitRate(48000)
            setAudioSamplingRate(44100)
            setOutputFile(file.path)
        }
    }

    private fun deleteVoiceRecordingDraftSync(discussionId: Long) {
        val db = AppDatabase.getInstance()
        db.messageDao().getDiscussionDraftMessageSync(discussionId)?.let { draftMessage ->
            val existingFyles =
                db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(draftMessage.id)
            val voiceAttachments = existingFyles.filter { fyleAndStatus ->
                fyleAndStatus.fyleMessageJoinWithStatus.fileName.contains(AUDIO_FILE_NAME_SUFFIX) &&
                        fyleAndStatus.fyleMessageJoinWithStatus.mimeType?.startsWith("audio/") == true
            }
            voiceAttachments.forEach { oldVoiceRecording ->
                DeleteAttachmentTask(oldVoiceRecording).run()
            }
        }
    }
}

data class SampleAndTicker(val samples: List<Float> = listOf(), val ticker: Int = 0) {
    companion object {
        const val TICKS_PER_SAMPLE = 6
    }

    fun add(v: Float): SampleAndTicker {
        return copy(samples = listOf(v) + samples, ticker = 0)
    }

    fun inc(): SampleAndTicker {
        return copy(samples = samples, ticker = ticker + 1)
    }

    val size: Int
        get() {
            return samples.size
        }
}
