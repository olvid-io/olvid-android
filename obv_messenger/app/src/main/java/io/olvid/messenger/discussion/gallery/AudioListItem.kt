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

package io.olvid.messenger.discussion.gallery

import android.app.Activity
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.media.AudioManager
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding.AudioInfo
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding.timeFromMs
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.gallery.DiscussionMediaGalleryActivity.AudioServiceBindable
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.services.MediaPlayerService.AudioOutput
import io.olvid.messenger.services.MediaPlayerService.AudioOutput.PHONE
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioListItem(
    modifier: Modifier = Modifier,
    fyleAndStatus: FyleAndStatus,
    activity: Activity?,
    onEnableMessageSwipe: ((Boolean) -> Unit)? = null,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?,
    discussionId: Long,
    onLongClick: () -> Unit,
    onIncompleteClick: (() -> Unit)? = null,
    contextMenu: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    var playtime by remember {
        mutableLongStateOf(0)
    }
    var duration by remember {
        mutableLongStateOf(0)
    }
    var isPlaying by remember {
        mutableStateOf(false)
    }
    var audioOutputResource by remember {
        mutableIntStateOf(R.drawable.ic_speaker_light_grey)
    }
    var playbackSpeedState by remember {
        mutableFloatStateOf(0f)
    }
    val playable = remember(fyleAndStatus.fyleMessageJoinWithStatus.status) {
        when (fyleAndStatus.fyleMessageJoinWithStatus.status) {
            FyleMessageJoinWithStatus.STATUS_UPLOADING,
            FyleMessageJoinWithStatus.STATUS_COPYING,
            FyleMessageJoinWithStatus.STATUS_DRAFT,
            FyleMessageJoinWithStatus.STATUS_COMPLETE -> {
                true
            }

            FyleMessageJoinWithStatus.STATUS_DOWNLOADING,
            FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE,
            FyleMessageJoinWithStatus.STATUS_FAILED -> {
                false
            }
            else -> false
        }
    }

    val audioServiceBindableViewHolder = remember {
        object : AudioServiceBindable() {
            override fun updatePlayTimeMs(
                audioInfo: AudioInfo?,
                playTimeMs: Long,
                playing: Boolean
            ) {
                playtime = playTimeMs
                isPlaying = playing
            }

            override fun bindAudioInfo(
                audioInfo: AudioInfo?,
                audioOutput: AudioOutput?,
                playbackSpeed: Float
            ) {
                if (audioInfo == null || audioInfo.failed) {
                    duration = 0
                    playtime = 0
                } else {
                    duration = audioInfo.durationMs ?: 0
                    playtime = audioInfo.seekTimeMs
                }
                isPlaying = false
                audioOutputResource = audioOutput.getResource()
                playbackSpeedState = playbackSpeed
            }

            override fun setFailed(failed: Boolean) {
                duration = 0
                playtime = 0
                isPlaying = false
            }

            override fun setAudioOutput(
                audioOutput: AudioOutput?,
                somethingPlaying: Boolean
            ) {
                audioOutputResource = audioOutput.getResource()
                activity?.let {
                    if ((somethingPlaying && (audioOutput == PHONE)) != (activity.volumeControlStream == AudioManager.STREAM_VOICE_CALL)) {
                        activity.volumeControlStream =
                            if ((somethingPlaying && (audioOutput == PHONE))) AudioManager.STREAM_VOICE_CALL else AudioManager.USE_DEFAULT_STREAM_TYPE
                    }
                }
            }

            override fun setPlaybackSpeed(playbackSpeed: Float) {
                playbackSpeedState = playbackSpeed
            }

            override fun getFyleAndStatus(): FyleAndStatus? {
                return fns
            }
        }
    }

    var playbackSpeedMenuOpened by remember { mutableStateOf(false) }

    LaunchedEffect(fyleAndStatus.fyle.id, playable) {
        if (playable) {
            audioServiceBindableViewHolder.setFyleAndStatus(fyleAndStatus)
            audioAttachmentServiceBinding?.loadAudioAttachment(
                fyleAndStatus,
                audioServiceBindableViewHolder
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                onClick = {
                    if (playable) {
                        audioAttachmentServiceBinding?.playPause(
                            fyleAndStatus,
                            discussionId
                        )
                        fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened()
                    } else {
                        onIncompleteClick?.invoke()
                    }
                },
                onLongClick = {
                    onLongClick()
                }
            )
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        contextMenu?.invoke()
        Box(contentAlignment = Alignment.TopEnd) {
            Image(
                modifier = Modifier
                    .size(64.dp),
                painter = painterResource(id = if (playable) if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play else R.drawable.mime_type_icon_audio),
                contentDescription = fyleAndStatus.fyleMessageJoinWithStatus.fileName
            )
            if (!fyleAndStatus.fyleMessageJoinWithStatus.wasOpened && playable) {
                Text(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .offset(x = 4.dp)
                        .background(
                            colorResource(id = R.color.green),
                            shape = CircleShape
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    text = stringResource(id = R.string.label_new).uppercase(),
                    fontSize = constantSp(value = 10),
                    lineHeight = constantSp(value = 10),
                    color = Color.White
                )
            }
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Row {
                Box(
                    modifier = Modifier.weight(1f, true),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        text = fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                        fontSize = constantSp(value = 14),
                        lineHeight = constantSp(value = 16),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (playbackSpeedState > 0.1f && isPlaying) {
                        Box(
                            modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                        ) {
                            DropdownMenu(
                                expanded = playbackSpeedMenuOpened,
                                onDismissRequest = {
                                    playbackSpeedMenuOpened = false
                                }) {
                                DropdownMenuItem(onClick = {
                                    audioAttachmentServiceBinding?.setPlaybackSpeed(
                                        1f
                                    )
                                    playbackSpeedMenuOpened = false
                                }) {
                                    Text(text = stringResource(id = R.string.menu_action_play_at_1x))
                                }
                                DropdownMenuItem(onClick = {
                                    audioAttachmentServiceBinding?.setPlaybackSpeed(
                                        1.5f
                                    )
                                    playbackSpeedMenuOpened = false
                                }) {
                                    Text(text = stringResource(id = R.string.menu_action_play_at_1_5x))
                                }
                                DropdownMenuItem(onClick = {
                                    audioAttachmentServiceBinding?.setPlaybackSpeed(
                                        2f
                                    )
                                    playbackSpeedMenuOpened = false
                                }) {
                                    Text(text = stringResource(id = R.string.menu_action_play_at_2x))
                                }
                            }
                            Text(
                                modifier = Modifier
                                    .clickable {
                                        playbackSpeedMenuOpened = true
                                    }
                                    .background(
                                        color = colorResource(id = R.color.lightGrey),
                                        shape = CircleShape
                                    )
                                    .requiredWidth(36.dp)
                                    .padding(vertical = 1.dp),
                                text = stringResource(
                                    id = when {
                                        playbackSpeedState < 1.1 -> R.string.text_speed_1x
                                        playbackSpeedState < 1.6 -> R.string.text_speed_1_5x
                                        else -> R.string.text_speed_2x
                                    }
                                ),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = colorResource(id = R.color.grey),
                                fontSize = constantSp(12),
                                lineHeight = constantSp(12)
                            )
                        }
                    }
                }
                if (playable) {
                    Image(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        ) {
                            audioAttachmentServiceBinding?.toggleSpeakerOutput()
                        },
                        painter = painterResource(id = audioOutputResource),
                        contentDescription = ""
                    )
                }
            }
            Row(
                modifier = Modifier.height(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playable) {
                    Text(
                        text = timeFromMs(playtime),
                        style = OlvidTypography.subtitle1.copy(fontSize = constantSp(value = 10)),
                        color = if (isPlaying) colorResource(id = R.color.olvid_gradient_light) else colorResource(
                            id = R.color.greyTint
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Slider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        value = playtime / (duration.takeIf { it > 0 }
                            ?: 1).toFloat(),
                        onValueChange = { progress ->
                            onEnableMessageSwipe?.invoke(false)
                            audioAttachmentServiceBinding?.seekAudioAttachment(
                                fyleAndStatus,
                                (progress * 1000).roundToInt()
                            )
                        },
                        onValueChangeFinished = {
                            onEnableMessageSwipe?.invoke(true)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = colorResource(id = R.color.olvid_gradient_light),
                            activeTrackColor = colorResource(id = R.color.olvid_gradient_light)
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timeFromMs(duration),
                        style = OlvidTypography.subtitle1.copy(fontSize = constantSp(value = 10)),
                        color = colorResource(
                            id = R.color.greyTint
                        )
                    )
                } else {
                    Text(
                        text = Formatter.formatShortFileSize(
                            context,
                            fyleAndStatus.fyleMessageJoinWithStatus.size
                        ),
                        style = OlvidTypography.subtitle1.copy(fontSize = constantSp(value = 12)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorResource(
                            id = R.color.greyTint
                        )
                    )
                }
            }
        }
    }
}


@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL, widthDp = 320)
@Preview(widthDp = 240)
@Composable
fun AudioListItemPreview() {
    AppCompatTheme {
        Column {
            AudioListItem(modifier = Modifier.background(colorResource(id = R.color.almostWhite)),
                fyleAndStatus = FyleAndStatus().apply {
                    fyle = Fyle()
                    fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                        0,
                        0,
                        byteArrayOf(),
                        "",
                        "Name of the file which is long and wraps.mp3",
                        "audio/",
                        FyleMessageJoinWithStatus.STATUS_COMPLETE,
                        1024,
                        byteArrayOf(),
                        0,
                        null
                    )
                },
                activity = null,
                audioAttachmentServiceBinding = null,
                discussionId = 0,
                onLongClick = { })

            Spacer(modifier = Modifier.height(8.dp))

            AudioListItem(modifier = Modifier.background(colorResource(id = R.color.almostWhite)),
                fyleAndStatus = FyleAndStatus().apply {
                    fyle = Fyle()
                    fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                        0,
                        0,
                        byteArrayOf(),
                        "",
                        "Name of the file",
                        false,
                        null,
                        "audio/mp3",
                        FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE,
                        1024*1024,
                        byteArrayOf(),
                        0,
                        null,
                        null,
                        false,
                        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED
                    )
                },
                activity = null,
                audioAttachmentServiceBinding = null,
                discussionId = 0,
                onLongClick = { })
        }
    }
}