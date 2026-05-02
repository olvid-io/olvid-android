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
package io.olvid.messenger.customClasses

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.services.AudioOutput
import io.olvid.messenger.services.MediaPlaybackService
import io.olvid.messenger.settings.SettingsActivity.Companion.useSpeakerOutputForMediaPlayer
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ExecutionException

class AudioAttachmentServiceBinding(private val activity: AppCompatActivity) {
    private val uiThreadHandler = Handler(Looper.getMainLooper())
    private val loadingAttachments = HashSet<BytesKey>()
    private val viewHolderAssociation: MutableMap<BytesKey, MutableList<WeakReference<AudioServiceBindableViewHolder>>> = mutableMapOf()
    private val loadedAttachments: MutableMap<BytesKey, AudioInfo> = mutableMapOf()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var nowPlaying: BytesKey? = null
    private var playing = false
    private var playTimeMs: Long = 0
    private var audioOutput: AudioOutput = AudioOutput.LOUDSPEAKER

    private val audioManager: AudioManager
    private val audioDeviceCallback: AudioDeviceCallback
    private val audioPlaybackCallback: Any?


    // Polling for progress
    private val progressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (mediaController != null && playing) {
                val currentPosition = mediaController!!.getCurrentPosition()
                updatePlayTimeMs(currentPosition, true)
                uiThreadHandler.postDelayed(this, 33)
            }
        }
    }

    init {
        this.audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        this.audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo?>?) {
                updateAudioOutput()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo?>?) {
                updateAudioOutput()
            }
        }
        this.audioManager.registerAudioDeviceCallback(audioDeviceCallback, uiThreadHandler)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.audioPlaybackCallback = object : AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration?>?) {
                    updateAudioOutput()
                }
            }
            this.audioManager.registerAudioPlaybackCallback(
                audioPlaybackCallback as AudioPlaybackCallback,
                uiThreadHandler
            )
        } else {
            this.audioPlaybackCallback = null
        }

        initializeMediaController()
    }

    private fun initializeMediaController() {
        val sessionToken =
            SessionToken(activity, ComponentName(activity, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(activity, sessionToken).buildAsync().apply {
            addListener({
                try {
                    mediaController = get()
                    setupControllerListener()
                    restoreState()
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun setupControllerListener() {
        mediaController?.addListener(ControllerListener())
    }

    private inner class ControllerListener : Player.Listener, MediaController.Listener {
        var buffering = false
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (buffering) return

            playing = isPlaying
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
            mediaController?.let {
                updatePlayTimeMs(it.getCurrentPosition(), isPlaying)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            buffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_ENDED) {
                playing = false
                stopProgressPolling()
                updatePlayTimeMs(0, false)
                nowPlaying = null
                mediaController?.let {
                    it.seekTo(0)
                    it.pause()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNowPlaying(mediaItem)
        }

        @OptIn(markerClass = [UnstableApi::class])
        override fun onPlayerError(error: PlaybackException) {
            // Try to recover SHA from current item if possible
            if (nowPlaying != null && loadedAttachments.containsKey(nowPlaying)) {
                onFail(nowPlaying)
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            onPlaybackSpeedChangeInternal(playbackParameters.speed)
        }

        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (MediaPlaybackService.EVENT_OUTPUT_CHANGED == command.customAction) {
                val useSpeaker = args.getBoolean(
                    MediaPlaybackService.EXTRAS_USE_SPEAKER,
                    useSpeakerOutputForMediaPlayer
                )
                useSpeakerOutputForMediaPlayer = useSpeaker
                updateAudioOutput()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(controller, command, args)
        }
    }

    private fun restoreState() {
        val controller = mediaController ?: return

        // Restore playing state
        updateNowPlaying(controller.getCurrentMediaItem())
        playing = controller.isPlaying()
        if (playing) {
            startProgressPolling()
        }
        updatePlayTimeMs(controller.getCurrentPosition(), playing)
        onPlaybackSpeedChangeInternal(controller.getPlaybackParameters().speed)

        updateAudioOutput()
    }

    private fun updateNowPlaying(mediaItem: MediaItem?) {
        if (mediaItem == null || mediaItem.mediaMetadata.extras == null) {
            pauseOldPlaying()
            nowPlaying = null
            return
        }
        val sha256 = mediaItem.mediaMetadata.extras?.getByteArray(MediaPlaybackService.EXTRAS_SHA256)
        if (sha256 != null) {
            val newKey = BytesKey(sha256)
            if (newKey != nowPlaying) {
                pauseOldPlaying()
                nowPlaying = newKey
                mediaItem.mediaMetadata.extras?.getLong(MediaPlaybackService.EXTRAS_MESSAGE_ID)?.let { messageId ->
                    App.runThread {
                        AppDatabase.getInstance()
                            .fyleMessageJoinWithStatusDao()
                            .getByMessageIdAndSha256(messageId, sha256)
                            ?.markAsOpened()
                    }
                }
            }
        } else {
            pauseOldPlaying()
            nowPlaying = null
        }
    }

    private fun pauseOldPlaying() {
        nowPlaying?.let {
            // Fake a pause update for the old item so the UI resets
            val oldTimeMs = playTimeMs
            nowPlaying = null // Temporarily null to force update on old key
            updatePlayTimeMs(it, oldTimeMs, false)
        }
    }

    private fun startProgressPolling() {
        uiThreadHandler.removeCallbacks(progressRunnable)
        uiThreadHandler.post(progressRunnable)
    }

    private fun stopProgressPolling() {
        uiThreadHandler.removeCallbacks(progressRunnable)
    }

    fun release() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioPlaybackCallback != null) {
            audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback as AudioPlaybackCallback)
        }
        nowPlaying = null
        stopProgressPolling()
        mediaController?.release()
        mediaController = null

        controllerFuture = null
        viewHolderAssociation.clear()
        loadedAttachments.clear()
    }

    fun loadAudioAttachment(
        fyleAndStatus: FyleAndStatus,
        viewHolder: AudioServiceBindableViewHolder
    ) {
        val key = BytesKey(fyleAndStatus.fyle.sha256)
        var viewHolders = viewHolderAssociation[key]
        if (viewHolders == null) {
            viewHolders = ArrayList()
            viewHolders.add(WeakReference<AudioServiceBindableViewHolder>(viewHolder))
            viewHolderAssociation[key] = viewHolders
        } else {
            // existing the list -> remove any expired WeakReference, check this ViewHolder is not already present
            val refreshedViewHolders: MutableList<WeakReference<AudioServiceBindableViewHolder>> =
                ArrayList()
            refreshedViewHolders.add(WeakReference<AudioServiceBindableViewHolder>(viewHolder))
            for (weakReference in viewHolders) {
                val vh = weakReference.get()
                if (vh == null || vh == viewHolder) {
                    continue
                }
                refreshedViewHolders.add(weakReference)
            }
            viewHolderAssociation[key] = refreshedViewHolders
        }

        val audioInfo = loadedAttachments[key]
        if (audioInfo != null) {
            viewHolder.bindAudioInfo(
                audioInfo, audioOutput,
                mediaController?.getPlaybackParameters()?.speed ?: 0f
            )
            if (key == nowPlaying) {
                viewHolder.updatePlayTimeMs(audioInfo, playTimeMs, playing)
            }
        } else {
            if (!loadingAttachments.contains(key)) {
                // in case this was never loaded
                loadingAttachments.add(key)
                App.runThread(LoadAudioAttachmentTask(key, fyleAndStatus))
            }
        }
    }

    fun seekAudioAttachment(fyleAndStatus: FyleAndStatus, progress: Int) {
        val key = BytesKey(fyleAndStatus.fyle.sha256)
        val audioInfo = loadedAttachments[key]
        if (audioInfo == null || audioInfo.durationMs == null) {
            return
        }
        val timeMs = (progress * (audioInfo.durationMs ?: 0L)) / 1000L
        if (key == nowPlaying) {
            mediaController?.seekTo(timeMs)
            updatePlayTimeMs(timeMs, playing)
        } else {
            updatePlayTimeMs(key, timeMs, false)
        }
    }

    fun playPause(fyleAndStatus: FyleAndStatus, discussionId: Long?) {
        if (mediaController == null) return

        val key = BytesKey(fyleAndStatus.fyle.sha256)

        if (key == nowPlaying) {
            if (playing) {
                mediaController?.pause()
            } else {
                mediaController?.play()
            }
        } else {
            // Load new media
            if (discussionId != null) {
                App.runThread {
                    val playlist = AppDatabase.getInstance()
                        .fyleMessageJoinWithStatusDao()
                        .getAudioFyleAndOriginForDiscussionDateAscSync(discussionId)
                    val mediaItems: MutableList<MediaItem> = ArrayList()
                    var startIndex = -1
                    var startSeekTime: Long = 0

                    for (item in playlist) {
                        item.fyleAndStatus.fyle.filePath?.let { filepath ->
                            mediaItems.add(
                                MediaPlaybackService.createMediaItem(
                                    item.fyleAndStatus.fyle.sha256,
                                    filepath,
                                    item.fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                                    discussionId,
                                    item.message.id,
                                    item.fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity,
                                    item.message.senderIdentifier
                                )
                            )

                            if (BytesKey(item.fyleAndStatus.fyle.sha256) == key) {
                                startIndex = mediaItems.size - 1
                                // Helper to get seek time
                                val audioInfo = loadedAttachments[key]
                                if (audioInfo != null) {
                                    startSeekTime = audioInfo.seekTimeMs
                                }
                            }
                        }
                    }

                    if (startIndex != -1) {
                        val finalStartIndex = startIndex
                        val finalStartSeekTime = startSeekTime
                        uiThreadHandler.post {
                            mediaController?.let {
                                it.setMediaItems(
                                    mediaItems,
                                    finalStartIndex,
                                    finalStartSeekTime
                                )
                                it.prepare()
                                it.play()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onFail(key: BytesKey?) {
        val audioInfo = loadedAttachments[key]
        if (audioInfo != null) {
            audioInfo.failed = true
            App.toast(
                activity.getString(R.string.toast_message_unable_to_play_audio),
                Toast.LENGTH_SHORT,
                Gravity.BOTTOM
            )

            val associatedViewHolders = viewHolderAssociation[key] ?: return
            for (weakReference in associatedViewHolders) {
                val vh = weakReference.get()
                if (vh != null) {
                    vh.setFailed(true)
                    vh.bindAudioInfo(audioInfo, audioOutput, 0f) // Speed 0 on fail
                }
            }
        }
    }

    private fun onPlaybackSpeedChangeInternal(playbackSpeed: Float) {
        // notify all view holders of output change
        for (viewHolderWeakReferences in viewHolderAssociation
            .values) {
            viewHolderWeakReferences.onEach { setPlaybackSpeed(playbackSpeed) }
        }
    }

    private fun updateAudioOutput() {
        val useSpeaker = useSpeakerOutputForMediaPlayer
        var newOutput = this.audioOutputFromActivePlayback

        if (newOutput == null) {
            // Fallback to connection-based logic
            newOutput = AudioOutput.PHONE
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            var bluetoothConnected = false
            var headsetConnected = false

            for (device in devices) {
                val type = device.type
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                    headsetConnected = true
                } else if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    bluetoothConnected = true
                }
            }

            if (headsetConnected) {
                newOutput = AudioOutput.HEADSET
            } else if (bluetoothConnected) {
                newOutput = AudioOutput.BLUETOOTH
            } else if (useSpeaker) {
                newOutput = AudioOutput.LOUDSPEAKER
            }
        }

        if (this.audioOutput != newOutput) {
            onSpeakerOutputChangeInternal(newOutput)
        }
    }

    private val audioOutputFromActivePlayback: AudioOutput?
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return null
            }
            val configs =
                audioManager.activePlaybackConfigurations
            for (config in configs) {
                val deviceInfo = config.audioDeviceInfo
                if (deviceInfo != null) {
                    val type = deviceInfo.type
                    when (type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                            return AudioOutput.LOUDSPEAKER
                        }
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                            return AudioOutput.PHONE
                        }
                        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> {
                            return AudioOutput.HEADSET
                        }
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                            return AudioOutput.BLUETOOTH
                        }
                        else -> {
                        }
                    }
                }
            }
            return null
        }

    fun toggleSpeakerOutput() {
        if (mediaController != null) {
            val toggleCommand = SessionCommand(
                MediaPlaybackService.CUSTOM_COMMAND_TOGGLE_OUTPUT,
                Bundle.EMPTY
            )
            mediaController?.sendCustomCommand(toggleCommand, Bundle.EMPTY)
        }
        updateAudioOutput()
    }

    fun onSpeakerOutputChangeInternal(audioOutput: AudioOutput) {
        this.audioOutput = audioOutput
        for (viewHolderWeakReferences in viewHolderAssociation
            .values) {
            for (viewHolderWeakReference in viewHolderWeakReferences) {
                viewHolderWeakReference.get()
                    ?.setAudioOutput(this.audioOutput, nowPlaying != null && this.playing)
            }
        }
    }

    fun setPlaybackSpeed(playbackSpeed: Float) {
        mediaController?.setPlaybackSpeed(playbackSpeed)
    }

    private inner class LoadAudioAttachmentTask(
        private val key: BytesKey,
        private val fyleAndStatus: FyleAndStatus
    ) : Runnable {
        override fun run() {
            val audioInfo = AudioInfo()
            runCatching {
                // Do not use "close with resource" as this only works on API 29 or higher...
                // noinspection resource
                val mediaMetadataRetriever = MediaMetadataRetriever()
                mediaMetadataRetriever.setDataSource(App.absolutePathFromRelative(fyleAndStatus.fyle.filePath))
                val durationString = mediaMetadataRetriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                mediaMetadataRetriever.release()
                if (durationString != null) {
                    audioInfo.durationMs = durationString.toLong()
                }
            }
            uiThreadHandler.post(Runnable {
                loadingAttachments.remove(key)
                loadedAttachments[key] = audioInfo
                val associatedViewHolders = viewHolderAssociation[key] ?: return@Runnable
                for (weakReference in associatedViewHolders) {
                    val vh = weakReference.get()
                    if (vh != null) {
                        vh.bindAudioInfo(
                            audioInfo, audioOutput,
                            mediaController?.getPlaybackParameters()?.speed ?: 0f
                        )
                        if (key == nowPlaying) {
                            vh.updatePlayTimeMs(audioInfo, playTimeMs, playing)
                        }
                    }
                }
            })
        }
    }

    // should always be called from executor
    private fun updatePlayTimeMs(playTimeMs: Long, playing: Boolean) {
        nowPlaying?.let {
            this.playTimeMs = playTimeMs
            if (this.playing != playing) {
                // refresh the output each time the media play/pause changes --> this allows updating the default volume control stream
                this.playing = playing
                onSpeakerOutputChangeInternal(audioOutput)
            }
            updatePlayTimeMs(it, playTimeMs, playing)
        }
    }

    private fun updatePlayTimeMs(key: BytesKey, playTimeMs: Long, playing: Boolean) {
        val audioInfo = loadedAttachments[key]
        val weakReferences = viewHolderAssociation[key]
        if (weakReferences == null || audioInfo == null || audioInfo.failed) {
            return
        }
        audioInfo.seekTimeMs = playTimeMs

        val viewHolders: MutableList<AudioServiceBindableViewHolder> =
            ArrayList()
        for (weakReference in weakReferences) {
            val viewHolder = weakReference.get()
            if (viewHolder != null) {
                if (viewHolder.getFyleAndStatus() != null
                    && BytesKey(viewHolder.getFyleAndStatus()?.fyle?.sha256) == key
                ) {
                    viewHolders.add(viewHolder)
                }
            }
        }
        if (viewHolders.isEmpty()) {
            viewHolderAssociation.remove(key)
        } else {
            for (viewHolder in viewHolders) {
                viewHolder.updatePlayTimeMs(audioInfo, playTimeMs, playing)
            }
        }
    }

    class AudioInfo {
        var durationMs: Long? = null
        var seekTimeMs: Long = 0

        @JvmField
        var failed: Boolean = false
    }

    interface AudioServiceBindableViewHolder {
        fun updatePlayTimeMs(audioInfo: AudioInfo, playTimeMs: Long, playing: Boolean)

        // when called with playbackSpeed == 0, this means the current mediaplayer does not support playback speed change
        fun bindAudioInfo(audioInfo: AudioInfo, audioOutput: AudioOutput, playbackSpeed: Float)
        fun setFailed(failed: Boolean)
        fun setAudioOutput(audioOutput: AudioOutput, somethingPlaying: Boolean)

        fun getFyleAndStatus() : FyleAndStatus?
    }

    companion object {
        fun timeFromMs(timeMs: Long): String {
            var timeMs = timeMs
            val hours = timeMs / 3600000L
            timeMs -= hours * 3600000L
            val minutes = timeMs / 60000L
            timeMs -= minutes * 60000L
            val seconds = timeMs / 1000L

            return if (hours == 0L) {
                String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds)
            } else {
                String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds)
            }
        }
    }
}
