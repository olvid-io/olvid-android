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
package io.olvid.messenger.webrtc

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioAttributes.Builder
import android.media.MediaPlayer
import io.olvid.engine.Logger
import io.olvid.messenger.R.raw
import io.olvid.messenger.webrtc.OutgoingCallRinger.Type.BUSY
import io.olvid.messenger.webrtc.OutgoingCallRinger.Type.RING
import java.io.IOException

class OutgoingCallRinger(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var afd: AssetFileDescriptor? = null

    enum class Type {
        RING,
        BUSY
    }

    fun ring(ringType: Type) {
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            mediaPlayer = MediaPlayer()
            val resourceId = when (ringType) {
                RING -> raw.ringing
                BUSY -> raw.busy
            }
            afd?.close()
            afd = null
            afd = context.resources.openRawResourceFd(resourceId)
            if (afd == null) {
                mediaPlayer = null
                return
            }
            mediaPlayer?.setDataSource(afd!!.fileDescriptor, afd!!.startOffset, afd!!.length)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setAudioAttributes(
                Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .build()
            )
            mediaPlayer?.setVolume(.5f, .5f)
        } catch (e: IOException) {
            e.printStackTrace()
            Logger.w("☎ Error initializing media player for outgoing call ringing")
            mediaPlayer = null
        }
        mediaPlayer?.let {
            try {
                if (mediaPlayer?.isPlaying == false) {
                    it.prepare()
                    it.start()
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                mediaPlayer = null
            } catch (e: IOException) {
                e.printStackTrace()
                mediaPlayer = null
            }
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            afd?.close()
        } catch (ignored: Exception) {
        }
        afd = null
    }
}
