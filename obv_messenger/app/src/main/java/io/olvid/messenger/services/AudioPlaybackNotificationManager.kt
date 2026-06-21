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

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.olvid.messenger.discussion.compose.VoiceMessageRecorder

data class AudioTrackData(
    val sha256: ByteArray,
    val discussionId: Long?,
    val messageId: Long?,
    val bytesOwnedIdentity: ByteArray?,
    val senderIdentity: ByteArray?,
    val fileName: String?,
    val senderDisplayName: String?,
    val isVoiceMessage: Boolean,
    val isPlaying: Boolean,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioTrackData) return false
        return sha256.contentEquals(other.sha256)
            && discussionId == other.discussionId
            && messageId == other.messageId
            && fileName == other.fileName
            && senderDisplayName == other.senderDisplayName
            && isPlaying == other.isPlaying
            && hasPrevious == other.hasPrevious
            && hasNext == other.hasNext
    }

    override fun hashCode(): Int {
        var result = sha256.contentHashCode()
        result = 31 * result + (discussionId?.hashCode() ?: 0)
        result = 31 * result + (messageId?.hashCode() ?: 0)
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (senderDisplayName?.hashCode() ?: 0)
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + hasPrevious.hashCode()
        result = 31 * result + hasNext.hashCode()
        return result
    }
}

object AudioPlaybackNotificationManager {
    private const val AUTO_HIDE_DELAY_MS = 5_000L

    var currentTrack: AudioTrackData? by mutableStateOf(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { currentTrack = null }

    private var controller: MediaController? = null
    private var pendingFuture: ListenableFuture<MediaController>? = null
    private var bindingCount = 0

    @MainThread
    fun bind(context: Context) {
        bindingCount++
        if (controller != null || pendingFuture != null) return
        val appContext = context.applicationContext
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, MediaPlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        pendingFuture = future
        future.addListener({
            try {
                val ctrl = future.get()
                if (pendingFuture !== future) {
                    // unbind happened while we were waiting
                    ctrl.release()
                    return@addListener
                }
                controller = ctrl
                pendingFuture = null
                ctrl.addListener(PlayerListener)
                refreshState()
            } catch (_: Throwable) {
                if (pendingFuture === future) pendingFuture = null
            }
        }, MoreExecutors.directExecutor())
    }

    @MainThread
    fun unbind() {
        bindingCount = (bindingCount - 1).coerceAtLeast(0)
        if (bindingCount > 0) return
        mainHandler.removeCallbacks(autoHideRunnable)
        pendingFuture = null
        controller?.let {
            it.removeListener(PlayerListener)
            it.release()
        }
        controller = null
        currentTrack = null
    }

    @MainThread
    fun playPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    @MainThread
    fun next() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    @MainThread
    fun previous() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }

    private fun refreshState() {
        val ctrl = controller ?: return
        val item = ctrl.currentMediaItem
        val extras = item?.mediaMetadata?.extras
        val sha256 = extras?.getByteArray(MediaPlaybackService.EXTRAS_SHA256)
        if (item == null || sha256 == null) {
            mainHandler.removeCallbacks(autoHideRunnable)
            currentTrack = null
            return
        }
        val fileName = item.mediaMetadata.artist?.toString()
        val isVoice = fileName?.contains(VoiceMessageRecorder.AUDIO_FILE_NAME_SUFFIX) == true
        val senderName = item.mediaMetadata.displayTitle?.toString()
        val discussionId =
            extras.getLong(MediaPlaybackService.EXTRAS_DISCUSSION_ID, -1L).takeIf { it != -1L }
        val messageId =
            extras.getLong(MediaPlaybackService.EXTRAS_MESSAGE_ID, -1L).takeIf { it != -1L }
        val bytesOwnedIdentity =
            extras.getByteArray(MediaPlaybackService.EXTRAS_OWNED_IDENTITY)
        val senderIdentity =
            extras.getByteArray(MediaPlaybackService.EXTRAS_SENDER_IDENTITY)
        val isPlaying = ctrl.isPlaying
        // do not set the track if we are not playing. without this check, a reconfiguration (screen rotation) displays the AudioPlayerNotification even if the session is paused
        if (currentTrack != null || isPlaying) {
            currentTrack = AudioTrackData(
                sha256 = sha256,
                discussionId = discussionId,
                messageId = messageId,
                bytesOwnedIdentity = bytesOwnedIdentity,
                senderIdentity = senderIdentity,
                fileName = fileName,
                senderDisplayName = senderName,
                isVoiceMessage = isVoice,
                isPlaying = isPlaying,
                hasPrevious = ctrl.hasPreviousMediaItem(),
                hasNext = ctrl.hasNextMediaItem(),
            )
            scheduleAutoHide(isPlaying)
        }
    }

    private fun scheduleAutoHide(isPlaying: Boolean) {
        mainHandler.removeCallbacks(autoHideRunnable)
        if (!isPlaying && currentTrack != null) {
            mainHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
        }
    }

    private object PlayerListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                refreshState()
            }
        }
    }
}
