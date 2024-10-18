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

package io.olvid.messenger.discussion.message

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ContextualFlowRowOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.map
import coil.compose.AsyncImage
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.App
import io.olvid.messenger.App.imageLoader
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.ImageResolution
import io.olvid.messenger.customClasses.MessageAttachmentAdapter.Visibility
import io.olvid.messenger.customClasses.MessageAttachmentAdapter.Visibility.HIDDEN
import io.olvid.messenger.customClasses.MessageAttachmentAdapter.Visibility.VISIBLE
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.PreviewUtilsWithDrawables
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.databases.tasks.InboundEphemeralMessageClicked
import io.olvid.messenger.databases.tasks.StartAttachmentDownloadTask
import io.olvid.messenger.databases.tasks.StopAttachmentDownloadTask
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.gallery.AudioListItem
import io.olvid.messenger.discussion.gallery.FyleListItem
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale


@Composable
fun constantSp(value: Int): TextUnit = with(LocalDensity.current) { (value / fontScale).sp }

data class Attachment(val fyle: Fyle, val fyleMessageJoinWithStatus: FyleMessageJoinWithStatus) {
    val deterministicContentUriForGallery: Uri by lazy {
        fyleAndStatus.deterministicContentUriForGallery
    }

    val contentUriForExternalSharing: Uri by lazy {
        fyleAndStatus.contentUriForExternalSharing
    }

    val fyleAndStatus: FyleAndStatus by lazy {
        FyleAndStatus().apply {
            fyle = this@Attachment.fyle
            fyleMessageJoinWithStatus = this@Attachment.fyleMessageJoinWithStatus
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun Attachments(
    modifier: Modifier = Modifier,
    message: Message,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?,
    onAttachmentLongClick: (FyleAndStatus) -> Unit,
    maxWidth: Dp,
    openOnClick: Boolean = true,
    onLocationClicked: (() -> Unit)? = null,
    openViewerCallback: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val attachmentFyles by AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
        .getFylesAndStatusForMessage(message.id).map { fyleAndStatuses -> fyleAndStatuses.map { fyleAndStatus -> Attachment(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus) }}.observeAsState()
    val attachments =
        attachmentFyles?.sortedByDescending { PreviewUtils.mimeTypeIsSupportedImageOrVideo(it.fyleMessageJoinWithStatus.getNonNullMimeType()) }
    val expiration: JsonExpiration? = message.jsonMessage.jsonExpiration
    val readOnce = expiration?.readOnce == true
    val imageCount =
        attachments?.count { PreviewUtils.mimeTypeIsSupportedImageOrVideo(it.fyleMessageJoinWithStatus.getNonNullMimeType()) }
            ?: 0

    var imageResolutions: Array<ImageResolution>? by remember { mutableStateOf(null) }

    LaunchedEffect(message.imageResolutions) {
        imageResolutions = runCatching {
            ImageResolution.parseMultiple(message.imageResolutions)
        }.getOrNull()
    }

    ContextualFlowRow(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(
                getMinimumHeight(
                    message,
                    maxWidth,
                    4.dp,
                    64.dp
                )
            )
            .clip(RoundedCornerShape(6.dp)),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalArrangement = Arrangement.SpaceBetween,
        itemCount = message.totalAttachmentCount,
        maxItemsInEachRow = 2,
        maxLines = 2,
        overflow = ContextualFlowRowOverflow.Visible
    ) { index ->
        attachments?.getOrNull(index)?.let { attachment ->
            val upload by derivedStateOf {
                if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_UPLOADING) true else if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DOWNLOADING) false else null
            }
            var speed: String? by remember {
                mutableStateOf(null)
            }
            var eta: String? by remember {
                mutableStateOf(null)
            }
            var progress: Float by remember {
                mutableFloatStateOf(0f)
            }

            val downloadAwareClick = { completeClick: () -> Unit ->
                when (attachment.fyleMessageJoinWithStatus.status) {
                    FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE ->
                        App.runThread(
                            StartAttachmentDownloadTask(
                                attachment.fyleMessageJoinWithStatus
                            )
                        )

                    FyleMessageJoinWithStatus.STATUS_DOWNLOADING ->
                        App.runThread(
                            StopAttachmentDownloadTask(
                                attachment.fyleMessageJoinWithStatus
                            )
                        )

                    FyleMessageJoinWithStatus.STATUS_DRAFT,
                    FyleMessageJoinWithStatus.STATUS_UPLOADING,
                    FyleMessageJoinWithStatus.STATUS_COMPLETE,
                        -> {
                        completeClick()
                    }
                }
            }

            DownloadListener(
                fyleAndStatus = attachment,
                upload = upload
            ) { attachmentDownloadData ->
                if (attachmentDownloadData?.speed != null && attachmentDownloadData.eta != null) {
                    val finalSpeed = attachmentDownloadData.speed
                    val finalEta = attachmentDownloadData.eta
                    progress = attachmentDownloadData.progress
                    speed = if (finalSpeed >= 10000000000f) {
                        context.getString(
                            R.string.xx_gbps,
                            String.format(
                                Locale.ENGLISH,
                                "%d",
                                (finalSpeed / 1000000000f).toInt()
                            )
                        )
                    } else if (finalSpeed >= 1000000000f) {
                        context.getString(
                            R.string.xx_gbps,
                            String.format(
                                Locale.ENGLISH,
                                "%1.1f",
                                finalSpeed / 1000000000f
                            )
                        )
                    } else if (finalSpeed >= 10000000f) {
                        context.getString(
                            R.string.xx_mbps,
                            String.format(
                                Locale.ENGLISH,
                                "%d",
                                (finalSpeed / 1000000f).toInt()
                            )
                        )
                    } else if (finalSpeed >= 1000000f) {
                        context.getString(
                            R.string.xx_mbps,
                            String.format(
                                Locale.ENGLISH,
                                "%1.1f",
                                finalSpeed / 1000000f
                            )
                        )
                    } else if (finalSpeed >= 10000f) {
                        context.getString(
                            R.string.xx_kbps,
                            String.format(
                                Locale.ENGLISH,
                                "%d",
                                (finalSpeed / 1000f).toInt()
                            )
                        )
                    } else if (finalSpeed >= 1000f) {
                        context.getString(
                            R.string.xx_kbps,
                            String.format(Locale.ENGLISH, "%1.1f", finalSpeed / 1000f)
                        )
                    } else {
                        context.getString(
                            R.string.xx_bps,
                            String.format(
                                Locale.ENGLISH,
                                "%d",
                                Math.round(finalSpeed)
                            )
                        )
                    }
                    eta = if (finalEta > 5940) {
                        context.getString(
                            R.string.text_timer_h,
                            finalEta / 3600
                        )
                    } else if (finalEta > 99) {
                        context.getString(
                            R.string.text_timer_m,
                            finalEta / 60
                        )
                    } else if (finalEta > 0) {
                        context.getString(R.string.text_timer_s, finalEta)
                    } else {
                        "-"
                    }
                } else {
                    progress = attachmentDownloadData?.progress ?: 0f
                }
            }
            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(attachment.fyleMessageJoinWithStatus.getNonNullMimeType())) {
                Box(
                    Modifier.background(colorResource(id = R.color.almostWhite))
                ) {
                    var attachmentContextMenuOpened by remember { mutableStateOf(false) }
                    AttachmentContextMenu(
                        menuOpened = attachmentContextMenuOpened,
                        onDismiss = { attachmentContextMenuOpened = false },
                        message = message,
                        attachment = attachment,
                        visibility = VISIBLE,
                        readOnce = false,
                        multipleAttachment = attachments.size > 1
                    )
                    val wide =
                        imageCount == 2 || (imageCount > 2 && index == imageCount - 1 && (imageCount and 1) != 0)

                    if (message.isContentHidden) {
                        Image(
                            modifier = Modifier
                                .width(if (wide || imageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
                                .requiredHeight(if (message.imageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
                                .border(
                                    width = 1.dp,
                                    color = colorResource(id = R.color.attachmentBorder),
                                    shape = RoundedCornerShape(
                                        if (index == 0) 6.dp else 0.dp,
                                        if ((index == 0 && imageCount < 3) || (index == 1 && imageCount >= 3)) 6.dp else 0.dp,
                                        if ((message.totalAttachmentCount == imageCount) && (index == imageCount - 1)) 6.dp else 0.dp,
                                        if ((message.totalAttachmentCount == imageCount) &&
                                            ((index == 1 && imageCount == 2)
                                                    || (index == imageCount - 1 && ((imageCount and 1) != 0))
                                                    || (index == imageCount - 2 && imageCount > 2 && ((imageCount and 1) == 0)))
                                        ) 6.dp else 0.dp
                                    )
                                )
                                .then(if (openOnClick) {
                                    Modifier.clickable {
                                        downloadAwareClick {
                                            App.runThread(
                                                InboundEphemeralMessageClicked(
                                                    attachment.fyleMessageJoinWithStatus.bytesOwnedIdentity,
                                                    attachment.fyleMessageJoinWithStatus.messageId
                                                )
                                            )
                                        }
                                    }
                                } else Modifier),
                            painter = painterResource(id = R.drawable.ic_incognito),
                            contentScale = ContentScale.Fit,
                            contentDescription = null
                        )
                        EphemeralVisibilityExplanation(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            duration = expiration?.visibilityDuration,
                            readOnce = readOnce
                        )
                    } else {
                        var imageUri: Any? by remember {
                            mutableStateOf(attachment.fyleMessageJoinWithStatus.miniPreview)
                        }
                        LaunchedEffect(attachment.fyle.filePath) {
                            if (attachment.fyle.isComplete) {
                                imageUri = attachment.deterministicContentUriForGallery
                            }
                        }
                        LaunchedEffect(attachment.fyle.id, attachment.fyle.filePath) {
                            launch(Dispatchers.IO) {
                                // TODO: only get the image resolution and store it in the fyleMessageJoinWithStatus
                                if (VERSION.SDK_INT < VERSION_CODES.P || !attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith(
                                        "image/"
                                    )
                                ) {
                                    PreviewUtils.getBitmapPreview(
                                        attachment.fyle,
                                        attachment.fyleMessageJoinWithStatus,
                                        1
                                    )
                                } else {
                                    runCatching {
                                        PreviewUtilsWithDrawables.getDrawablePreview(
                                            attachment.fyle,
                                            attachment.fyleMessageJoinWithStatus,
                                            1
                                        )
                                    }.onFailure {
                                        imageUri = R.drawable.ic_broken_image
                                    }
                                }
                            }
                        }

                        AsyncImage(
                            modifier = Modifier
                                .width(if (wide || imageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
                                .requiredHeight(
                                    imageResolutions
                                        ?.getOrNull(index)
                                        ?.getPreferredHeight(
                                            if (wide || imageCount == 1) maxWidth else (maxWidth / 2 - 2.dp),
                                            wide,
                                            2.dp
                                        )
                                        ?: if (imageCount == 1) maxWidth else (maxWidth / 2 - 2.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = colorResource(id = R.color.attachmentBorder),
                                    shape = RoundedCornerShape(
                                        if (index == 0) 6.dp else 0.dp,
                                        if ((index == 0 && imageCount < 3) || (index == 1 && imageCount >= 3)) 6.dp else 0.dp,
                                        if ((message.totalAttachmentCount == imageCount) && (index == imageCount - 1)) 6.dp else 0.dp,
                                        if ((message.totalAttachmentCount == imageCount) &&
                                            ((index == 1 && imageCount == 2)
                                                    || (index == imageCount - 1 && ((imageCount and 1) != 0))
                                                    || (index == imageCount - 2 && imageCount > 2 && ((imageCount and 1) == 0)))
                                        ) 6.dp else 0.dp
                                    )
                                )
                                .combinedClickable(
                                    onClick = {
                                        downloadAwareClick {
                                            if (message.isLocationMessage) {
                                                onLocationClicked?.invoke()
                                            } else if (attachment.fyleMessageJoinWithStatus.mimeType != "image/svg+xml") {
                                                openViewerCallback?.invoke()
                                                App.openDiscussionGalleryActivity(
                                                    context,
                                                    message.discussionId,
                                                    attachment.fyleMessageJoinWithStatus.messageId,
                                                    attachment.fyle.id,
                                                    true
                                                )
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        onAttachmentLongClick(attachment.fyleAndStatus)
                                        if (message.isLocationMessage.not()) {
                                            attachmentContextMenuOpened = true
                                        }
                                    }
                                ),
                            model = imageUri,
                            imageLoader = imageLoader,
                            contentScale = if (attachment.fyleMessageJoinWithStatus.nonNullMimeType != "image/gif" && (attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith(
                                    "image/"
                                ) || attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith(
                                    "video/"
                                ))
                            ) ContentScale.Crop else ContentScale.Fit,
                            contentDescription = attachment.fyleMessageJoinWithStatus.fileName,
                        )
                    }
                    if (attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/")) {
                        Image(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(64.dp),
                            painter = painterResource(id = R.drawable.overlay_video_small),
                            contentDescription = "video"
                        )
                    }
                    when (attachment.fyleMessageJoinWithStatus.receptionStatus) {
                        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED -> {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(64.dp),
                                painter = painterResource(id = R.drawable.ic_attachment_status_delivered_for_image),
                                contentDescription = null
                            )
                        }

                        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED_AND_READ -> {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(64.dp),
                                painter = painterResource(id = R.drawable.ic_attachment_status_read_for_image),
                                contentDescription = null
                            )
                        }
                    }
                    getProgressLabel(status = attachment.fyleMessageJoinWithStatus.status)?.let {
                        Text(
                            modifier = Modifier
                                .width(if (wide || imageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
                                .padding(1.dp)
                                .requiredHeight(18.dp)
                                .background(
                                    color = colorResource(
                                        id = R.color.primary400_90
                                    )
                                )
                                .wrapContentHeight(Alignment.CenterVertically),
                            text = it,
                            fontSize = constantSp(12),
                            textAlign = TextAlign.Center,
                            style = OlvidTypography.subtitle1,
                            color = colorResource(
                                id = R.color.almostWhite
                            )
                        )
                    }
                    when (attachment.fyleMessageJoinWithStatus.status) {
                        FyleMessageJoinWithStatus.STATUS_DOWNLOADING,
                        FyleMessageJoinWithStatus.STATUS_UPLOADING,
                        FyleMessageJoinWithStatus.STATUS_COPYING -> {
                            AttachmentDownloadProgress(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                speed = speed,
                                eta = eta,
                                progress = progress,
                                large = imageCount == 1
                            )
                        }

                        FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE -> {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(if (imageCount == 1) 0.dp else 4.dp)
                                    .size(if (imageCount == 1) 64.dp else 32.dp),
                                painter = painterResource(id = R.drawable.ic_file_download),
                                contentDescription = null
                            )
                        }

                        FyleMessageJoinWithStatus.STATUS_DRAFT, FyleMessageJoinWithStatus.STATUS_COMPLETE -> {

                        }

                        FyleMessageJoinWithStatus.STATUS_FAILED -> {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(if (imageCount == 1) 0.dp else 4.dp)
                                    .size(if (imageCount == 1) 64.dp else 32.dp),
                                painter = painterResource(id = R.drawable.ic_attachment_status_failed),
                                contentDescription = null
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = colorResource(id = R.color.attachmentBorder),
                            shape = RoundedCornerShape(
                                if (imageCount == 0 && index == 0) 6.dp else 0.dp,
                                if (imageCount == 0 && index == 0) 6.dp else 0.dp,
                                if (index == (message.totalAttachmentCount - 1)) 6.dp else 0.dp,
                                if (index == (message.totalAttachmentCount - 1)) 6.dp else 0.dp,
                            )
                        )
                ) {
                    if (attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("audio/")) {
                        var menuOpened by remember { mutableStateOf(false) }
                        AudioListItem(
                            modifier = Modifier
                                .background(color = colorResource(id = R.color.almostWhite)),
                            fyleAndStatus = attachment.fyleAndStatus,
                            activity = context as AppCompatActivity,
                            audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                            discussionId = message.discussionId,
                            onLongClick = {
                                onAttachmentLongClick(attachment.fyleAndStatus)
                                menuOpened = true
                            },
                            onIncompleteClick = {
                                downloadAwareClick {}
                            },
                            contextMenu = {
                                AttachmentContextMenu(
                                    menuOpened = menuOpened,
                                    message = message,
                                    attachment = attachment,
                                    visibility = VISIBLE,
                                    readOnce = false,
                                    multipleAttachment = false,
                                    onDismiss = { menuOpened = false },
                                )
                            }
                        )
                    } else {
                        var menuOpened by remember { mutableStateOf(false) }
                        FyleListItem(
                            modifier = Modifier
                                .background(colorResource(id = R.color.almostWhite)),
                            fyleAndStatus = attachment.fyleAndStatus,
                            fileName = AnnotatedString(attachment.fyleMessageJoinWithStatus.fileName),
                            onClick = {
                                downloadAwareClick {
                                    App.openFyleInExternalViewer(
                                        context,
                                        attachment.fyleAndStatus
                                    ) {
                                        openViewerCallback?.invoke()
                                        attachment.fyleMessageJoinWithStatus.markAsOpened()
                                    }
                                }
                            },
                            onLongClick = {
                                onAttachmentLongClick(attachment.fyleAndStatus)
                                menuOpened = true
                            },
                            contextMenu = {
                                AttachmentContextMenu(
                                    menuOpened = menuOpened,
                                    message = message,
                                    attachment = attachment,
                                    visibility = VISIBLE,
                                    readOnce = false,
                                    multipleAttachment = false,
                                    onDismiss = { menuOpened = false },
                                )
                            },
                            previewBorder = false
                        )
                    }

                    getProgressLabel(status = attachment.fyleMessageJoinWithStatus.status)?.let {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(1.dp)
                                .requiredHeight(18.dp)
                                .background(
                                    color = colorResource(
                                        id = R.color.primary400_90
                                    )
                                )
                                .wrapContentHeight(Alignment.CenterVertically),
                            text = it,
                            fontSize = constantSp(12),
                            textAlign = TextAlign.Center,
                            style = OlvidTypography.subtitle1,
                            color = colorResource(
                                id = R.color.almostWhite
                            )
                        )
                    }
                    when (attachment.fyleMessageJoinWithStatus.status) {
                        FyleMessageJoinWithStatus.STATUS_DOWNLOADING,
                        FyleMessageJoinWithStatus.STATUS_UPLOADING,
                        FyleMessageJoinWithStatus.STATUS_COPYING -> {
                            AttachmentDownloadProgress(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                speed = speed,
                                eta = eta,
                                progress = progress,
                                large = false
                            )
                        }

                        FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE -> {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(32.dp),
                                painter = painterResource(id = R.drawable.ic_file_download),
                                contentDescription = null
                            )
                        }

                        FyleMessageJoinWithStatus.STATUS_DRAFT, FyleMessageJoinWithStatus.STATUS_COMPLETE -> {

                        }

                        FyleMessageJoinWithStatus.STATUS_FAILED -> {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(32.dp),
                                painter = painterResource(id = R.drawable.ic_attachment_status_failed),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getMinimumHeight(
    message: Message,
    displayWidth: Dp,
    attachmentSpace: Dp,
    attachmentFileHeight: Dp
): Dp {
    var attachmentsHeight = 0.dp
    val imageResolutions = runCatching {
        ImageResolution.parseMultiple(message.imageResolutions)
    }.getOrNull()

    if (!imageResolutions.isNullOrEmpty() && imageResolutions.size == message.imageCount) {
        when (imageResolutions.size) {
            1 -> {
                attachmentsHeight += imageResolutions[0].getPreferredHeight(
                    displayWidth,
                    false,
                    0.dp
                )
            }

            2 -> {
                attachmentsHeight += imageResolutions[0].getPreferredHeight(
                    displayWidth,
                    true,
                    attachmentSpace / 2
                )
                attachmentsHeight += imageResolutions[1].getPreferredHeight(
                    displayWidth,
                    true,
                    attachmentSpace / 2
                )
                attachmentsHeight += attachmentSpace
            }

            else -> {
                var i = 0
                while (i < imageResolutions.size - 1) {
                    attachmentsHeight +=
                        imageResolutions[i].getPreferredHeight(
                            (displayWidth - attachmentSpace) / 2,
                            false,
                            0.dp
                        ).coerceAtLeast(
                            imageResolutions[i + 1].getPreferredHeight(
                                (displayWidth - attachmentSpace) / 2,
                                false,
                                0.dp
                            )
                        )
                    attachmentsHeight += attachmentSpace
                    i += 2
                }
                if ((imageResolutions.size and 1) != 0) {
                    attachmentsHeight += imageResolutions[imageResolutions.size - 1].getPreferredHeight(
                        displayWidth,
                        true,
                        attachmentSpace / 2
                    )
                } else {
                    attachmentsHeight -= attachmentSpace
                }
            }
        }
    } else {
        // images
        if (message.imageCount == 1) {
            attachmentsHeight += displayWidth
        } else if (message.imageCount == 2) {
            attachmentsHeight += displayWidth
        } else if (message.imageCount > 2) {
            if ((message.imageCount and 1) != 0) {
                attachmentsHeight += ((displayWidth + attachmentSpace) / 2) * (message.imageCount / 2)
                attachmentsHeight += (displayWidth - attachmentSpace) / 2
            } else {
                attachmentsHeight += ((displayWidth + attachmentSpace) / 2) * (message.imageCount / 2)
                attachmentsHeight -= attachmentSpace
            }
        }
    }

    // files
    attachmentsHeight += (attachmentFileHeight + attachmentSpace) * (message.totalAttachmentCount - message.imageCount)
    if (message.imageCount == 0) {
        attachmentsHeight -= attachmentSpace
    }

    return attachmentsHeight
}

@Composable
fun getProgressLabel(status: Int): String? =
    when (status) {
        FyleMessageJoinWithStatus.STATUS_UPLOADING -> stringResource(id = R.string.label_upload)
        FyleMessageJoinWithStatus.STATUS_DOWNLOADING -> stringResource(id = R.string.label_download)
        FyleMessageJoinWithStatus.STATUS_COPYING -> stringResource(id = R.string.label_copy)
        FyleMessageJoinWithStatus.STATUS_FAILED -> stringResource(id = R.string.label_no_longer_available)
        else -> null
    }

@Composable
fun AttachmentContextMenu(
    menuOpened: Boolean,
    onDismiss: () -> Unit,
    message: Message,
    attachment: Attachment,
    visibility: Visibility,
    readOnce: Boolean,
    multipleAttachment: Boolean,
    openViewerCallback: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // delete
    val delete = @Composable {
        DropdownMenuItem(
            onClick = {
                SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_attachment)
                    .setMessage(
                        context.getString(
                            R.string.dialog_message_delete_attachment,
                            attachment.fyleMessageJoinWithStatus.fileName
                        )
                    )
                    .setPositiveButton(R.string.button_label_ok) { _: DialogInterface?, _: Int ->
                        App.runThread(
                            DeleteAttachmentTask(attachment.fyleAndStatus)
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .create()
                    .show()
                onDismiss()
            },
            text = {
                Text(
                    text = stringResource(id = R.string.menu_action_delete),
                    color = colorResource(id = R.color.almostBlack)
                )
            }
        )
    }
    // delete
    val open = @Composable {
        DropdownMenuItem(
            onClick = {
                if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(
                        PreviewUtils.getNonNullMimeType(
                            attachment.fyleMessageJoinWithStatus.mimeType,
                            attachment.fyleMessageJoinWithStatus.fileName
                        )
                    ) && SettingsActivity.useInternalImageViewer()
                ) {
                    // we do not mark as opened here as this is done in the gallery activity
                    openViewerCallback?.invoke()
                    App.openDiscussionGalleryActivity(
                        context,
                        message.discussionId,
                        attachment.fyleMessageJoinWithStatus.messageId,
                        attachment.fyleMessageJoinWithStatus.fyleId,
                        true
                    )
                } else {
                    App.openFyleInExternalViewer(context, attachment.fyleAndStatus) {
                        openViewerCallback?.invoke()
                        attachment.fyleMessageJoinWithStatus.markAsOpened()
                    }
                }
                onDismiss()
            },
            text = {
                Text(
                    text = stringResource(id = R.string.menu_action_open),
                    color = colorResource(id = R.color.almostBlack)
                )
            }
        )
    }

    DropdownMenu(
        modifier = Modifier
            .background(
                color = colorResource(id = R.color.dialogBackground)
            )
            .clip(RoundedCornerShape(8.dp)), expanded = menuOpened, onDismissRequest = onDismiss
    ) {
        if (visibility == HIDDEN || readOnce) {
            delete()
        } else if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DRAFT) {
            open()
            delete()
        } else if (attachment.fyle.isComplete) {
            open()
            // save
            DropdownMenuItem(
                onClick = {
                    if (attachment.fyle.isComplete) {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(attachment.fyleMessageJoinWithStatus.nonNullMimeType)
                            .putExtra(
                                Intent.EXTRA_TITLE,
                                attachment.fyleMessageJoinWithStatus.fileName
                            )
                        App.startActivityForResult(
                            context as AppCompatActivity, intent,
                            DiscussionActivity.REQUEST_CODE_SAVE_ATTACHMENT
                        )
                    }
                    onDismiss()
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.menu_action_save),
                        color = colorResource(id = R.color.almostBlack)
                    )
                }
            )
            if (multipleAttachment) {
                // saveAll
                DropdownMenuItem(
                    onClick = {
                        SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_save_all_attachments)
                            .setMessage(R.string.dialog_message_save_all_attachments)
                            .setPositiveButton(R.string.button_label_ok) { _: DialogInterface?, _: Int ->
                                App.startActivityForResult(
                                    context as AppCompatActivity,
                                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                                    DiscussionActivity.REQUEST_CODE_SAVE_ALL_ATTACHMENTS
                                )
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .create()
                            .show()
                        onDismiss()
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.menu_action_save_all),
                            color = colorResource(
                                id = R.color.almostBlack
                            )
                        )
                    }
                )
            }
            // share
            DropdownMenuItem(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.putExtra(
                        Intent.EXTRA_STREAM,
                        attachment.contentUriForExternalSharing
                    )
                    intent.setType(attachment.fyleMessageJoinWithStatus.nonNullMimeType)
                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            context.getString(R.string.title_sharing_chooser)
                        )
                    )
                    onDismiss()
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.menu_action_share),
                        color = colorResource(
                            id = R.color.almostBlack
                        )
                    )
                }
            )
            if (message.status != Message.STATUS_UNPROCESSED && message.status != Message.STATUS_COMPUTING_PREVIEW && message.status != Message.STATUS_PROCESSING) {
                delete()
            }
        } else {
            if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED) {
                delete()
            } else {
                open()
                delete()
            }
        }
    }
}

@Composable
fun AttachmentDownloadProgress(
    modifier: Modifier = Modifier,
    speed: String?,
    eta: String?,
    progress: Float,
    large: Boolean = true
) {
    val scale = if (large) 2f else 1f
    Row(
        modifier = modifier
            .widthIn(
                min = (scale * 24).dp,
                max = (scale * 64).dp
            )
            .height((scale * 24).dp)
            .background(
                color = colorResource(id = R.color.whiteOverlay),
                shape = CircleShape
            )
            .border(
                width = scale.dp,
                color = colorResource(id = R.color.olvid_gradient_dark),
                shape = CircleShape
            ),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
       if (speed != null || eta != null) {
            Column(
                modifier = Modifier
                    .width((scale * 40).dp)
                    .padding(end = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    modifier = Modifier.offset(y = (.5 * scale).dp),
                    text = speed ?: "-",
                    fontSize = constantSp(if (large) 16 else 9),
                    color = colorResource(id = R.color.olvid_gradient_dark)
                )
                Text(
                    modifier = Modifier.offset(y = (-.5 * scale).dp),
                    text = eta ?: "-",
                    fontSize = constantSp(if (large) 16 else 9),
                    color = colorResource(id = R.color.olvid_gradient_dark)
                )
            }
        }


        if (progress < .01f) {
            val infiniteTransition = rememberInfiniteTransition(label = "rotationTransition")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "rotation"
            )
            val size = with(LocalDensity.current) { (scale * 12).dp.toPx() }
            val start = Offset(size, size)
            val end = Offset(size, with(LocalDensity.current) { (scale * 3).dp.toPx() })
            val color = colorResource(id = R.color.olvid_gradient_dark)
            Box(
                modifier = Modifier
                    .size((scale * 24).dp)
                    .border(
                        width = scale.dp,
                        color = colorResource(id = R.color.olvid_gradient_dark),
                        shape = CircleShape
                    )
                    .drawWithContent {
                        rotate(rotation) {
                            drawLine(
                                color = color,
                                start = start,
                                end = end,
                                strokeWidth = scale.dp.toPx()
                            )
                        }
                    },
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .size((scale * 24).dp)
                    .border(
                        width = scale.dp,
                        color = colorResource(id = R.color.olvid_gradient_dark),
                        shape = CircleShape
                    )
                    .padding((scale * 2).dp),
                progress = { progress },
                color = colorResource(id = R.color.olvid_gradient_dark),
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                strokeWidth = (scale * 10).dp
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AttachmentDownloadProgressPreview() {
    AppCompatTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentDownloadProgress(speed = "352k/s", eta = "45s", progress = .37f)
            AttachmentDownloadProgress(speed = null, eta = null, progress = 0f)
            AttachmentDownloadProgress(
                speed = "3.4M/s",
                eta = "57s",
                progress = .72f,
                large = false
            )
            AttachmentDownloadProgress(speed = null, eta = "12s", progress = 0f, large = false)
        }
    }
}

data class AttachmentDownloadData(val speed: Float?, val eta: Int?, val progress: Float)

@Composable
fun DownloadListener(
    fyleAndStatus: Attachment,
    upload: Boolean?,
    onUpdate: (AttachmentDownloadData?) -> Unit
) {
    val downloadListener = remember {
        object : EngineNotificationListener {
            var registrationNumber: Long = 0
            override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
                var ownedIdentity: ByteArray? = null
                var messageIdentifier: ByteArray? = null
                var attachmentNumber: Int? = null
                var speed: Float? = null
                var eta: Int? = null
                var progress: Float? = null
                if (notificationName == EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS) {
                    ownedIdentity =
                        userInfo[EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                    messageIdentifier =
                        userInfo[EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY] as ByteArray?
                    attachmentNumber =
                        userInfo[EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY] as Int?
                    speed =
                        userInfo[EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_SPEED_BPS_KEY] as Float?
                    eta =
                        userInfo[EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ETA_SECONDS_KEY] as Int?
                    progress =
                        userInfo[EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY] as Float?
                } else if (notificationName == EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS) {
                    ownedIdentity =
                        userInfo[EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                    messageIdentifier =
                        userInfo[EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY] as ByteArray?
                    attachmentNumber =
                        userInfo[EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY] as Int?
                    speed =
                        userInfo[EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_SPEED_BPS_KEY] as Float?
                    eta =
                        userInfo[EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ETA_SECONDS_KEY] as Int?
                    progress =
                        userInfo[EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY] as Float?
                }
                if (fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity.contentEquals(ownedIdentity)
                    && fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier.contentEquals(messageIdentifier)
                    && fyleAndStatus.fyleMessageJoinWithStatus.engineNumber == attachmentNumber
                ) {
                    onUpdate(
                        AttachmentDownloadData(
                            speed = speed,
                            eta = eta,
                            progress = progress ?: 0f
                        )
                    )
                }
            }

            override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
                this.registrationNumber = registrationNumber
            }

            override fun getEngineNotificationListenerRegistrationNumber(): Long {
                return this.registrationNumber
            }

            override fun hasEngineNotificationListenerRegistrationNumber(): Boolean =
                registrationNumber != 0L
        }
    }

    DisposableEffect(upload) {
        if (upload != null && downloadListener.hasEngineNotificationListenerRegistrationNumber()
                .not()
        ) {
            onUpdate(null)
            AppSingleton.getEngine().addNotificationListener(
                if (upload == true) {
                    EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS
                } else {
                    EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS
                },
                downloadListener
            )
        }
        onDispose {
            if (downloadListener.hasEngineNotificationListenerRegistrationNumber()) {
                AppSingleton.getEngine().removeNotificationListener(
                    if (upload == true) {
                        EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS
                    } else {
                        EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS
                    },
                    downloadListener
                )
            }
        }
    }
}