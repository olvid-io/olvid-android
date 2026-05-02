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

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.olvid.messenger.App
import io.olvid.messenger.FyleProgressSingleton
import io.olvid.messenger.ProgressStatus
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.constantSp
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.Attachment
import io.olvid.messenger.services.AudioOutput
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DraftAttachments(
    modifier: Modifier = Modifier,
    attachments: List<Attachment>,
    discussionId: Long?,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?,
    onAttachmentClick: (Attachment) -> Unit,
    onDeleteClick: (Attachment) -> Unit
) {
    val listState = rememberLazyListState()
    var previousSize by remember { mutableIntStateOf(0) }
    LaunchedEffect(attachments.size) {
        if (attachments.size > previousSize && attachments.isNotEmpty()) {
            listState.animateScrollToItem(attachments.size - 1)
        }
        previousSize = attachments.size
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments, key = { it.fyle.id }) { attachment ->
            DraftAttachmentItem(
                modifier = Modifier.animateItem(),
                attachment = attachment,
                discussionId = discussionId,
                audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                onClick = { onAttachmentClick(attachment) },
                onDeleteClick = { onDeleteClick(attachment) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DraftAttachmentItem(
    modifier: Modifier = Modifier,
    attachment: Attachment,
    discussionId: Long?,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val previewSize = dimensionResource(id = R.dimen.attachment_small_preview_size)
    val mimeType = attachment.fyleMessageJoinWithStatus.nonNullMimeType
    val isAudio = mimeType.startsWith("audio/")
    val isImage = mimeType.startsWith("image/")
    val hasPreview =
        PreviewUtils.canGetPreview(attachment.fyle, attachment.fyleMessageJoinWithStatus)
    val isImageMode = hasPreview && isImage

    val progressStatus by remember(
        attachment.fyleMessageJoinWithStatus.fyleId,
        attachment.fyleMessageJoinWithStatus.messageId
    ) {
        FyleProgressSingleton.getProgress(
            attachment.fyleMessageJoinWithStatus.fyleId,
            attachment.fyleMessageJoinWithStatus.messageId
        )
    }.observeAsState()

    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.padding(top = 4.dp, end = 4.dp)) {
        Surface(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        if (isAudio && audioAttachmentServiceBinding != null && discussionId != null) {
                            audioAttachmentServiceBinding.playPause(attachment.fyleAndStatus, discussionId)
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { showMenu = true },
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(top = 8.dp, bottom = 4.dp)
                .border(
                    width = 1.dp,
                    color = colorResource(R.color.lightGrey),
                    shape = RoundedCornerShape(8.dp)
                ),
            shape = RoundedCornerShape(8.dp),
            color = colorResource(id = R.color.almostWhite),
        ) {
            Box {
                Row {
                    Box(
                        modifier = Modifier
                            .size(previewSize)
                    ) {
                        if (hasPreview) {
                            DraftPreview(attachment, previewSize.value.toInt(), isImageMode)
                        } else {
                            if (isAudio && audioAttachmentServiceBinding != null) {
                                DraftAudioIcon(attachment, audioAttachmentServiceBinding)
                            } else {
                                Image(
                                    painter = painterResource(
                                        id = PreviewUtils.getDrawableResourceForMimeType(
                                            mimeType
                                        )
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    // Text Area (if not image mode)
                    if (!isImageMode) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 100.dp)
                                .height(previewSize)
                                .padding(4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = attachment.fyleMessageJoinWithStatus.fileName,
                                color = colorResource(R.color.primary700),
                                fontSize = 12.sp,
                                maxLines = if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DRAFT) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Column {
                                Text(
                                    text = attachment.fyleMessageJoinWithStatus.nonNullMimeType,
                                    color = colorResource(R.color.greyTint),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                DraftAttachmentSizeOrDuration(
                                    attachment,
                                    audioAttachmentServiceBinding
                                )
                            }
                        }
                    }
                }

                // Progress Label (overlay at top)
                if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_COPYING) {
                    Text(
                        text = stringResource(R.string.label_copy),
                        color = colorResource(R.color.almostWhite),
                        fontSize = constantSp(12),
                        textAlign = TextAlign.Center,
                        style = OlvidTypography.subtitle1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorResource(R.color.primary400_90))
                    )
                }

                // Progress Bar (overlay over image if copying)
                if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_COPYING) {
                    Box(
                        modifier = Modifier
                            .size(previewSize) // Cover the image area
                            .background(Color.Black.copy(alpha = 0.1f)), // Slight dim
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        CircularProgressIndicator(
                            progress = {
                                if (progressStatus is ProgressStatus.InProgress) {
                                    (progressStatus as ProgressStatus.InProgress).progress
                                } else 0f
                            },
                            modifier = Modifier
                                .padding(4.dp)
                                .size(24.dp),
                            color = colorResource(R.color.olvid_gradient_dark),
                        )
                    }
                }
            }
        }

        if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_COPYING ||
            attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DRAFT
        ) {
            Image(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .offset(x = 12.dp, y = (-4).dp)
                    .clickable(
                        indication = ripple(false, 16.dp),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDeleteClick
                    )
                    .padding(4.dp)
                    .background(colorResource(id = R.color.lightGrey), shape = CircleShape)
                    .padding(4.dp),
                painter = painterResource(id = R.drawable.ic_close),
                colorFilter = ColorFilter.tint(colorResource(R.color.almostBlack)),
                contentDescription = stringResource(id = R.string.dialog_title_delete_attachment),
            )
        }

        DraftAttachmentContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            attachment = attachment,
            onDeleteClick = onDeleteClick
        )
    }
}

@Composable
fun DraftPreview(
    attachment: Attachment,
    sizePx: Int,
    isImageMode: Boolean
) {
    var model by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(attachment, attachment.fyleMessageJoinWithStatus.status) {
        launch(Dispatchers.IO) {
            val bitmap = PreviewUtils.getBitmapPreview(
                attachment.fyle,
                attachment.fyleMessageJoinWithStatus,
                sizePx
            )
            model = bitmap
                ?: PreviewUtils.getDrawableResourceForMimeType(attachment.fyleMessageJoinWithStatus.nonNullMimeType)
        }
    }

    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = if (isImageMode || attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith(
                "video/"
            )
        ) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun DraftAudioIcon(
    attachment: Attachment,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?
) {
    var isPlaying by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    if (audioAttachmentServiceBinding != null) {
        val viewHolder = remember {
            object : AudioAttachmentServiceBinding.AudioServiceBindableViewHolder {
                override fun updatePlayTimeMs(
                    audioInfo: AudioAttachmentServiceBinding.AudioInfo,
                    playTimeMs: Long,
                    playing: Boolean
                ) {
                    isPlaying = playing
                }

                override fun bindAudioInfo(
                    audioInfo: AudioAttachmentServiceBinding.AudioInfo,
                    audioOutput: AudioOutput,
                    playbackSpeed: Float
                ) {
                    failed = audioInfo.failed
                }

                override fun setFailed(f: Boolean) {
                    failed = f
                }

                override fun setAudioOutput(
                    audioOutput: AudioOutput,
                    somethingPlaying: Boolean
                ) {
                }

                override fun getFyleAndStatus(): FyleMessageJoinWithStatusDao.FyleAndStatus {
                    return attachment.fyleAndStatus
                }
            }
        }

        LaunchedEffect(attachment) {
            audioAttachmentServiceBinding.loadAudioAttachment(attachment.fyleAndStatus, viewHolder)
        }
    }

    if (failed) {
        Image(
            painter = painterResource(id = R.drawable.mime_type_icon_audio_failed),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun DraftAttachmentSizeOrDuration(
    attachment: Attachment,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?
) {
    var playTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    val isAudio = attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("audio/")

    if (isAudio && audioAttachmentServiceBinding != null) {
        val viewHolder = remember {
            object : AudioAttachmentServiceBinding.AudioServiceBindableViewHolder {
                override fun updatePlayTimeMs(
                    audioInfo: AudioAttachmentServiceBinding.AudioInfo,
                    playTimeMs: Long,
                    playing: Boolean
                ) {
                    playTime = playTimeMs
                    duration = audioInfo.durationMs
                    isPlaying = playing
                }

                override fun bindAudioInfo(
                    audioInfo: AudioAttachmentServiceBinding.AudioInfo,
                    audioOutput: AudioOutput,
                    playbackSpeed: Float
                ) {
                }

                override fun setFailed(f: Boolean) {}
                override fun setAudioOutput(
                    audioOutput: AudioOutput,
                    somethingPlaying: Boolean
                ) {
                }

                override fun getFyleAndStatus(): FyleMessageJoinWithStatusDao.FyleAndStatus =
                    attachment.fyleAndStatus
            }
        }
        LaunchedEffect(attachment) {
            audioAttachmentServiceBinding.loadAudioAttachment(attachment.fyleAndStatus, viewHolder)
        }
    }

    if (isPlaying || (isAudio && playTime > 0)) {
        Text(
            text = AudioAttachmentServiceBinding.timeFromMs(playTime) + if (duration != null) "/" + AudioAttachmentServiceBinding.timeFromMs(
                duration!!
            ) else "",
            color = colorResource(R.color.greyTint),
            fontSize = 12.sp
        )
    } else {
        Text(
            text = Formatter.formatShortFileSize(
                LocalContext.current,
                attachment.fyleMessageJoinWithStatus.size
            ),
            color = colorResource(R.color.greyTint),
            fontSize = 12.sp
        )
    }
}

@Composable
fun DraftAttachmentContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    attachment: Attachment,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    OlvidDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        OlvidDropdownMenuItem(
            text = stringResource(R.string.menu_action_open),
            onClick = {
                onDismiss()
                if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(
                        PreviewUtils.getNonNullMimeType(
                            attachment.fyleMessageJoinWithStatus.mimeType,
                            attachment.fyleMessageJoinWithStatus.fileName
                        )
                    ) && SettingsActivity.useInternalImageViewer()
                ) {
                    App.openDraftGalleryActivity(
                        context,
                        attachment.fyleMessageJoinWithStatus.messageId,
                        attachment.fyleMessageJoinWithStatus.fyleId
                    )
                } else {
                    App.openFyleViewer(context, attachment.fyleAndStatus, null)
                }
            }
        )
        OlvidDropdownMenuItem(
            text = stringResource(R.string.menu_action_delete),
            textColor = colorResource(R.color.red),
            onClick = {
                onDismiss()
                SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_attachment)
                    .setMessage(
                        resources.getString(
                            R.string.dialog_message_delete_attachment,
                            attachment.fyleMessageJoinWithStatus.fileName
                        )
                    )
                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                        onDeleteClick()
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .create()
                    .show()
            }
        )
    }
}