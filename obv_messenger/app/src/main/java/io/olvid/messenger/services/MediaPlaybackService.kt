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

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.olvid.messenger.App
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.settings.SettingsActivity

class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    // Audio output state
    private var useSpeakerOutput = false

    companion object {
        const val CUSTOM_COMMAND_TOGGLE_OUTPUT = "io.olvid.messenger.services.TOGGLE_OUTPUT"
        const val EVENT_OUTPUT_CHANGED = "io.olvid.messenger.services.EVENT_OUTPUT_CHANGED"
        const val EXTRAS_USE_SPEAKER = "use_speaker"
        const val EXTRAS_SHA256 = "sha256"
        const val EXTRAS_DISCUSSION_ID = "discussion_id"
        const val EXTRAS_MESSAGE_ID = "olvid_message_id"
        const val EXTRAS_OWNED_IDENTITY = "owned_identity"

        // Helper to build media item with necessary metadata for notification
        fun createMediaItem(
            sha256: ByteArray?,
            filePath: String,
            fileName: String,
            discussionId: Long?,
            messageId: Long?,
            bytesOwnedIdentity: ByteArray?,
            senderIdentity: ByteArray?
        ): MediaItem {
            val extras = Bundle().apply {
                if (sha256 != null) putByteArray(EXTRAS_SHA256, sha256)
                if (discussionId != null) putLong(EXTRAS_DISCUSSION_ID, discussionId)
                if (messageId != null) putLong(EXTRAS_MESSAGE_ID, messageId)
                if (bytesOwnedIdentity != null) putByteArray(EXTRAS_OWNED_IDENTITY, bytesOwnedIdentity)
            }

            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setDisplayTitle(ContactCacheSingleton.getContactCustomDisplayName(senderIdentity))
                .setArtist(fileName)
                .setExtras(extras)
                .build()

            return MediaItem.Builder()
                .setUri(App.absolutePathFromRelative(filePath))
                .setMediaMetadata(metadata)
                .build()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        useSpeakerOutput = SettingsActivity.useSpeakerOutputForMediaPlayer
        val playbackSpeed = SettingsActivity.playbackSpeedForMediaPlayer

        exoPlayer = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                setPlaybackSpeed(playbackSpeed)
                updateAudioAttributes(this)
            }

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setCallback(MediaSessionCallback())
            .setSessionActivity(getSingleTopActivity())
            .build()

        // Add listener to persist playback speed and update session activity
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                SettingsActivity.playbackSpeedForMediaPlayer = playbackParameters.speed
            }

            @OptIn(UnstableApi::class)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaSession?.setSessionActivity(getSingleTopActivity())
            }

        })
    }

    private fun getSingleTopActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val currentMediaItem = exoPlayer?.currentMediaItem
        val extras = currentMediaItem?.mediaMetadata?.extras
        if (extras != null) {
            val discussionId = extras.getLong(EXTRAS_DISCUSSION_ID, -1L)
            val messageId = extras.getLong(EXTRAS_MESSAGE_ID, -1L)
            val bytesOwnedIdentity = extras.getByteArray(EXTRAS_OWNED_IDENTITY)
            if (discussionId != -1L && messageId != -1L && bytesOwnedIdentity != null) {
                intent.action = MainActivity.FORWARD_ACTION
                intent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, io.olvid.messenger.discussion.DiscussionActivity::class.java.name)
                intent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, bytesOwnedIdentity)
                intent.putExtra(io.olvid.messenger.discussion.DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId)
                intent.putExtra(io.olvid.messenger.discussion.DiscussionActivity.MESSAGE_ID_INTENT_EXTRA, messageId)
            }
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateAudioAttributes(player: Player) {
        if (useSpeakerOutput) {
            val attributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            player.setAudioAttributes(attributes, true)
        } else {
            val attributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_VOICE_COMMUNICATION)
                .build()
            player.setAudioAttributes(attributes, false)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    @OptIn(UnstableApi::class) // For custom commands
    internal inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val validCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_TOGGLE_OUTPUT, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(validCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CUSTOM_COMMAND_TOGGLE_OUTPUT) {
                toggleAudioOutput()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun toggleAudioOutput() {
        useSpeakerOutput = !useSpeakerOutput
        SettingsActivity.useSpeakerOutputForMediaPlayer = useSpeakerOutput
        exoPlayer?.let { updateAudioAttributes(it) }

        // Broadcast the change to connected controllers via a custom event (or just rely on them polling/querying if needed, but events are better)
        // Actually, Media3 doesn't have a direct "arbitrary event" to valid controllers easily accessible without custom commands back.
        // But the previous service used a listener.
        // We can just send a fast "session event".
        val extras = Bundle().apply {
            putBoolean(EXTRAS_USE_SPEAKER, useSpeakerOutput)
        }
        mediaSession?.broadcastCustomCommand(
            SessionCommand(EVENT_OUTPUT_CHANGED, Bundle.EMPTY),
            extras
        )
    }
}