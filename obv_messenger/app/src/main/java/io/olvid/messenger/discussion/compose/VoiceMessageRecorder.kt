/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioEncoder
import android.media.MediaRecorder.AudioSource
import android.media.MediaRecorder.OutputFormat
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager.LayoutParams
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import io.olvid.messenger.App
import io.olvid.messenger.R.id
import io.olvid.messenger.R.string
import io.olvid.messenger.R.style
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

internal class VoiceMessageRecorder(
    private val activity: FragmentActivity,
    private val recordingOverlay: View,
    private val requestAudioPermissionDelegate: RequestAudioPermissionDelegate
) : OnTouchListener {
    private val recordingInitializationSpinner: ImageView
    private val recordingRedButton: View
    private val recordingStopTextView: TextView
    private val composeMessageViewModel: ComposeMessageViewModel
    private var recordPermission: Boolean
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recording = false
    private val timer: Timer
    private var startRecordTask: TimerTask? = null
    private var sampleTask: TimerTask? = null
    private var wakeLock: WakeLock? = null

    init {
        recordingInitializationSpinner =
            recordingOverlay.findViewById(id.recording_initialization_spinner)
        recordingRedButton = recordingOverlay.findViewById(id.recording_red_button)
        recordingStopTextView = recordingOverlay.findViewById(id.recording_stop_label)
        composeMessageViewModel = ViewModelProvider(activity).get(
            ComposeMessageViewModel::class.java
        )
        recordPermission = ContextCompat.checkSelfPermission(
            activity,
            permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        setRecording(false)
        timer = Timer("DiscussionActivity-VoiceMessageTimer")
    }

    fun setRecordPermission(recordPermission: Boolean) {
        this.recordPermission = recordPermission
    }

    // return false if permissions are wrong
    fun startRecord(): Boolean {
        if (!recordPermission) {
            return false
        }
        startRecordTask = object : TimerTask() {
            @SuppressLint("WakelockTimeout")
            override fun run() {
                if (recording) {
                    return
                }
                activity.runOnUiThread {
                    recordingOverlay.visibility = View.VISIBLE
                    recordingRedButton.visibility = View.GONE
                    recordingInitializationSpinner.visibility = View.VISIBLE
                    val spinner = recordingInitializationSpinner.drawable
                    if (spinner is AnimatedVectorDrawable) {
                        spinner.start()
                    }
                }
                setRecording(true)
                if (mediaRecorder != null) {
                    mediaRecorder?.release()
                    mediaRecorder = null
                }
                val cacheDir = File(activity.cacheDir, App.CAMERA_PICTURE_FOLDER)
                cacheDir.mkdirs()
                audioFile = File(
                    cacheDir,
                    SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(
                        Date()
                    ) + ".m4a"
                )
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(AudioSource.DEFAULT)
                    setOutputFormat(OutputFormat.MPEG_4)
                    setAudioEncoder(AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioEncodingBitRate(48000)
                    setAudioSamplingRate(44100)
                    setOutputFile(audioFile!!.path)
                }

                try {
                    mediaRecorder?.prepare()
                    mediaRecorder?.start()
                } catch (e: Exception) {
                    App.toast(
                        string.toast_message_voice_message_recording_failed,
                        Toast.LENGTH_SHORT
                    )
                    mediaRecorder?.release()
                    mediaRecorder = null
                    activity.runOnUiThread { recordingOverlay.visibility = View.GONE }
                    return
                }

                // prevent app from sleeping and screen from turning off
                Handler(Looper.getMainLooper()).post {
                    activity.window?.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.let {
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "Olvid:VoiceMessageRecording"
                    ).apply { acquire() }
                }
                sampleTask = object : TimerTask() {
                    var started = false
                    override fun run() {
                        if (mediaRecorder == null || !recording) {
                            cancel()
                        }
                        try {
                            val amplitude = mediaRecorder!!.maxAmplitude
                            if (!started && amplitude > 0) {
                                started = true
                                activity.runOnUiThread {
                                    recordingInitializationSpinner.visibility = View.GONE
                                    recordingRedButton.visibility = View.VISIBLE
                                }
                            }
                            var log = (Math.log(amplitude.toDouble()) / Math.log(2.0)).toFloat()
                            log = Math.max(0f, log - 8)
                            val scale = 0.5f + log / 4
                            activity.runOnUiThread {
                                recordingRedButton.scaleX = scale
                                recordingRedButton.scaleY = scale
                            }
                        } catch (e: Exception) {
                            cancel()
                        }
                    }
                }
                timer.scheduleAtFixedRate(sampleTask, 0, 100)
            }
        }
        timer.schedule(startRecordTask, 250)
        return true
    }

    fun stopRecord(discard: Boolean) {
        // release wake locks
        Handler(Looper.getMainLooper()).post {
            val window = activity.window
            window?.clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (wakeLock != null) {
            wakeLock!!.release()
            wakeLock = null
        }
        if (startRecordTask != null) {
            startRecordTask!!.cancel()
            startRecordTask = null
        }
        if (!recording) {
            activity.runOnUiThread { recordingOverlay.visibility = View.GONE }
        } else {
            val stopTask: TimerTask = object : TimerTask() {
                override fun run() {
                    if (!recording) {
                        return
                    }
                    setRecording(false)
                    activity.runOnUiThread { recordingOverlay.visibility = View.GONE }
                    if (mediaRecorder != null) {
                        try {
                            mediaRecorder!!.stop()
                            mediaRecorder!!.release()
                            mediaRecorder = null
                            if (!discard && audioFile!!.length() > 0) {
                                val discussionId = composeMessageViewModel.discussionId
                                if (discussionId != null) {
                                    AddFyleToDraftFromUriTask(
                                        audioFile!!,
                                        audioFile!!.name,
                                        "audio/x-m4a",
                                        discussionId
                                    ).run()
                                }
                            }
                        } catch (e: Exception) {
                            // do nothing
                        }
                    }
                }
            }
            timer.schedule(stopTask, 300)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRecord()
                recordingStopTextView.setText(string.label_stop_recording_release)
            }

            MotionEvent.ACTION_UP -> {
                if (recording) {
                    stopRecord(false)
                } else {
                    if (!startRecord()) {
                        if (VERSION.SDK_INT < VERSION_CODES.M || activity.shouldShowRequestPermissionRationale(
                                permission.RECORD_AUDIO
                            )
                        ) {
                            val builder = SecureAlertDialogBuilder(
                                activity, style.CustomAlertDialog
                            )
                                .setTitle(string.dialog_title_voice_message_explanation)
                                .setMessage(string.dialog_message_voice_message_explanation)
                                .setPositiveButton(string.button_label_ok, null)
                                .setOnDismissListener { dialog: DialogInterface? ->
                                    requestAudioPermissionDelegate.requestAudioPermission(
                                        true
                                    )
                                }
                            builder.create().show()
                        } else {
                            requestAudioPermissionDelegate.requestAudioPermission(false)
                        }
                    } else {
                        // recording start success
                        recordingStopTextView.setText(string.label_stop_recording_tap)
                        v.performClick()
                    }
                }
            }
        }
        return true
    }

    fun isRecording() : Boolean {
        return recording
    }
     fun setRecording(recording: Boolean) {
        this.recording = recording
        composeMessageViewModel.setRecording(recording)
    }

    internal interface RequestAudioPermissionDelegate {
        fun requestAudioPermission(rationaleWasShown: Boolean)
    }
}