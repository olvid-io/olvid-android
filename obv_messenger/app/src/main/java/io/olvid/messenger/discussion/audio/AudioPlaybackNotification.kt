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

package io.olvid.messenger.discussion.audio

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.services.AudioPlaybackNotificationManager
import io.olvid.messenger.services.AudioTrackData

@Composable
fun AudioPlaybackNotification(
    modifier: Modifier = Modifier,
    track: AudioTrackData,
    onClick: ((AudioTrackData) -> Unit)? = null,
) {
    val context = LocalContext.current
    val (title, subtitle) = remember(track) {
        if (track.isVoiceMessage) {
            (track.senderDisplayName
                ?: ContactCacheSingleton.getContactCustomDisplayName(track.senderIdentity)
                ?: context.getString(R.string.text_unknown_sender)) to
                context.getString(R.string.label_voice_message)
        } else {
            (track.fileName ?: context.getString(R.string.label_voice_message)) to
                (track.senderDisplayName
                    ?: ContactCacheSingleton.getContactCustomDisplayName(track.senderIdentity))
        }
    }

    Card(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 360.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = {
                    if (onClick != null) onClick(track)
                    else openPlayingAudioMessage(context, track)
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.newDialogBackground),
            contentColor = colorResource(R.color.almostBlack),
        ),
        border = BorderStroke(1.dp, colorResource(R.color.newDialogBorder)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = CenterVertically,
        ) {
            InitialView(
                modifier = Modifier.size(48.dp),
                initialViewSetup = { iv ->
                    val sender = track.senderIdentity
                    if (sender != null) {
                        iv.setFromCache(sender)
                    } else {
                        iv.setUnknown()
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f, true),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    color = colorResource(id = R.color.primary700),
                    style = OlvidTypography.h3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it,
                        color = colorResource(id = R.color.greyTint),
                        style = OlvidTypography.subtitle1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                modifier = Modifier.width(40.dp) ,
                onClick = { AudioPlaybackNotificationManager.previous() },
                enabled = track.hasPrevious,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = colorResource(R.color.almostBlack),
                    disabledContentColor = colorResource(R.color.greyTint),
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = stringResource(R.string.content_description_skip_previous),
                )
            }
            IconButton(
                modifier = Modifier.width(40.dp) ,
                onClick = { AudioPlaybackNotificationManager.playPause() },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = colorResource(R.color.almostBlack),
                ),
            ) {
                Icon(
                    painter = painterResource(
                        if (track.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = stringResource(
                        if (track.isPlaying) R.string.content_description_audio_pause
                        else R.string.content_description_audio_play
                    ),
                )
            }
            IconButton(
                modifier = Modifier.width(40.dp) ,
                onClick = { AudioPlaybackNotificationManager.next() },
                enabled = track.hasNext,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = colorResource(R.color.almostBlack),
                    disabledContentColor = colorResource(R.color.greyTint),
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = stringResource(R.string.content_description_skip_next),
                )
            }
        }
    }
}

fun openPlayingAudioMessage(
    context: android.content.Context,
    track: AudioTrackData,
) {
    val discussionId = track.discussionId
    val messageId = track.messageId
    val bytesOwnedIdentity = track.bytesOwnedIdentity
    if (discussionId == null || messageId == null || bytesOwnedIdentity == null) return
    val intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.FORWARD_ACTION
        putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity::class.java.name)
        putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, bytesOwnedIdentity)
        putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId)
        putExtra(DiscussionActivity.MESSAGE_ID_INTENT_EXTRA, messageId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    context.startActivity(intent)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AudioPlaybackNotificationVoicePreview() {
    AudioPlaybackNotification(
        track = AudioTrackData(
            sha256 = ByteArray(0),
            discussionId = 1L,
            messageId = 1L,
            bytesOwnedIdentity = ByteArray(0),
            senderIdentity = null,
            fileName = "2026-02-12_14-08.olvidaudio.m4a",
            senderDisplayName = "Jane Doe",
            isVoiceMessage = true,
            isPlaying = true,
            hasPrevious = true,
            hasNext = false,
        )
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AudioPlaybackNotificationFilePreview() {
    AudioPlaybackNotification(
        track = AudioTrackData(
            sha256 = ByteArray(0),
            discussionId = 1L,
            messageId = 1L,
            bytesOwnedIdentity = ByteArray(0),
            senderIdentity = null,
            fileName = "Snowy Mountains.mp3",
            senderDisplayName = "Jane Doe",
            isVoiceMessage = false,
            isPlaying = false,
            hasPrevious = false,
            hasNext = true,
        )
    )
}
