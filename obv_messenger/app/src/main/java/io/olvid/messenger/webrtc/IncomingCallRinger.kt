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
package io.olvid.messenger.webrtc

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioAttributes.Builder
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.MediaSession.Callback
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import io.olvid.engine.Logger
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.settings.SettingsActivity
import java.io.IOException
import java.util.UUID

class IncomingCallRinger(private val context: Context) {
    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var cameraManager: CameraManager? = null
    private val cameraIdsToFlash: MutableSet<String> = HashSet()
    private val cameraIdsFlashThreads: HashMap<String, Thread> = HashMap()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null

    init {
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
            cameraManager?.let {
                try {
                    for (cameraId in it.cameraIdList) {
                        val cameraCharacteristics = cameraManager?.getCameraCharacteristics(cameraId)
                        val hasFlash =
                            cameraCharacteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                        if (hasFlash != null && hasFlash) {
                            cameraIdsToFlash.add(cameraId)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun ring(call: WebrtcCallService.Call) {
        var ringerMode = AudioManager.RINGER_MODE_NORMAL
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        if (audioManager != null) {
            ringerMode = audioManager.ringerMode
        }
        cleanup()
        val ringtone: Uri
        val vibrationPattern: LongArray
        val useFlash: Boolean
        if (call.discussionCustomization == null || !call.discussionCustomization.prefUseCustomCallNotification) {
            ringtone = SettingsActivity.callRingtone
            vibrationPattern = SettingsActivity.callVibrationPattern
            useFlash = SettingsActivity.useFlashOnIncomingCall()
        } else {
            ringtone =
                if (call.discussionCustomization.prefCallNotificationRingtone == null) Uri.EMPTY else Uri.parse(
                    call.discussionCustomization.prefCallNotificationRingtone
                )
            vibrationPattern =
                if (call.discussionCustomization.prefCallNotificationVibrationPattern == null) LongArray(
                    0
                ) else SettingsActivity.intToVibrationPattern(
                    call.discussionCustomization.prefCallNotificationVibrationPattern!!.toInt()
                )
            useFlash = call.discussionCustomization.prefCallNotificationUseFlash
        }
        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer!!.setDataSource(context, ringtone)
            mediaPlayer!!.isLooping = true
            mediaPlayer!!.setAudioAttributes(
                Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.w("☎ Error initializing media player for incoming call ringing")
            mediaPlayer = null
        }


        // play sound
        if (mediaPlayer != null) {
            mediaSession = MediaSession(context, "Olvid incoming ringer")
            mediaSession!!.setCallback(object : Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent =
                        mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && (keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
                        val answerCallIntent = Intent(context, WebrtcCallService::class.java)
                        answerCallIntent.setAction(WebrtcCallService.ACTION_ANSWER_CALL)
                        answerCallIntent.putExtra(WebrtcCallService.CALL_IDENTIFIER_INTENT_EXTRA, Logger.getUuidString(call.callIdentifier))
                        answerCallIntent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, call.bytesOwnedIdentity)
                        context.startService(answerCallIntent)
                        return true
                    }
                    return false
                }
            }, Handler(Looper.getMainLooper()))
            mediaSession!!.isActive = true
            try {
                if (!mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.prepare()
                    mediaPlayer!!.start()
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                mediaPlayer = null
            } catch (e: IOException) {
                e.printStackTrace()
                mediaPlayer = null
            }
        }

        // vibrate
        if (vibrator != null && vibrationPattern.isNotEmpty() && ringerMode != AudioManager.RINGER_MODE_SILENT) {
            if (VERSION.SDK_INT >= VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, 1))
            } else {
                vibrator.vibrate(vibrationPattern, 1)
            }
        }

        // flash
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            for (thread in cameraIdsFlashThreads.values) {
                thread.interrupt()
            }
            cameraIdsFlashThreads.clear()
            if (useFlash) {
                for (cameraId in cameraIdsToFlash) {
                    val cameraFlashThread = CameraFlashThread(cameraId)
                    cameraFlashThread.start()
                    cameraIdsFlashThreads[cameraId] = cameraFlashThread
                }
            }
        }
    }

    fun stop() {
        vibrator?.cancel()
        for (thread in cameraIdsFlashThreads.values) {
            thread.interrupt()
        }
        cleanup()
    }

    private fun cleanup() {
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
            mediaPlayer = null
        }
        if (mediaSession != null) {
            mediaSession!!.release()
            mediaSession = null
        }
    }

    @RequiresApi(api = VERSION_CODES.M)
    private inner class CameraFlashThread(val cameraId: String) : Thread() {
        override fun run() {
            while (true) {
                torch(true)
                try {
                    sleep(50)
                } catch (e: InterruptedException) {
                    torch(false)
                    return
                }
                torch(false)
                try {
                    sleep(100)
                } catch (e: InterruptedException) {
                    return
                }
                torch(true)
                try {
                    sleep(50)
                } catch (e: InterruptedException) {
                    torch(false)
                    return
                }
                torch(false)
                try {
                    sleep(1000)
                } catch (e: InterruptedException) {
                    return
                }
            }
        }

        private fun torch(enabled: Boolean) {
            try {
                cameraManager!!.setTorchMode(cameraId, enabled)
            } catch (e: CameraAccessException) {
                // Nothing special to do here
            }
        }
    }
}
