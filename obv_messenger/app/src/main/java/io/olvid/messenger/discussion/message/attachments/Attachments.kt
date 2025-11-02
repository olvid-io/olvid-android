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

package io.olvid.messenger.discussion.message.attachments

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ContextualFlowRowOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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
import coil.request.ImageRequest
import io.olvid.messenger.App
import io.olvid.messenger.App.imageLoader
import io.olvid.messenger.FyleProgressSingleton
import io.olvid.messenger.ProgressStatus
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.ImageResolution
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.PreviewUtilsWithDrawables
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.TextBlock
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.databases.tasks.InboundEphemeralMessageClicked
import io.olvid.messenger.databases.tasks.StartAttachmentDownloadTask
import io.olvid.messenger.databases.tasks.StopAttachmentDownloadTask
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.gallery.AudioListItem
import io.olvid.messenger.discussion.gallery.FyleListItem
import io.olvid.messenger.discussion.message.EphemeralVisibilityExplanation
import io.olvid.messenger.discussion.message.attachments.Visibility.HIDDEN
import io.olvid.messenger.discussion.message.attachments.Visibility.VISIBLE
import io.olvid.messenger.discussion.search.DiscussionSearchViewModel
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

enum class Visibility {
    VISIBLE,
    HIDDEN,
}

@Composable
fun constantSp(value: Int): TextUnit = with(LocalDensity.current) { (value / fontScale).sp }
@Composable
fun constantSp(value: Float): TextUnit = with(LocalDensity.current) { (value / fontScale).sp }
@Composable
fun scaledDp(value: Int): Dp = with(LocalDensity.current) { (value * fontScale).dp }

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

data class SelectedImage(
    val uri: Uri?,
    val imageResolution: ImageResolution?,
    val cacheKey: String
)

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun Attachments(
    modifier: Modifier = Modifier,
    message: Message,
    onEnableMessageSwipe: ((Boolean) -> Unit)? = null,
    showStatuses: Boolean = true,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?,
    onAttachmentLongClick: (FyleAndStatus) -> Unit,
    maxWidth: Dp,
    openOnClick: Boolean = true,
    onLocationClicked: (() -> Unit)? = null,
    openViewerCallback: (() -> Unit)? = null,
    discussionSearchViewModel: DiscussionSearchViewModel?,
    saveAttachment: () -> Unit,
    saveAllAttachments: () -> Unit,
    blockClicks: Boolean,
) {
    val context = LocalContext.current
    val highlightColor = colorResource(R.color.searchHighlightColor)
    val attachmentFyles by AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
        .getFylesAndStatusForMessage(message.id).map { fyleAndStatuses ->
            fyleAndStatuses.map { fyleAndStatus ->
                Attachment(
                    fyleAndStatus.fyle,
                    fyleAndStatus.fyleMessageJoinWithStatus
                )
            }
        }.observeAsState()
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
            var textBlocks by remember { mutableStateOf(emptyList<TextBlock>()) }
            val progressStatus: ProgressStatus? by remember(
                attachment.fyleMessageJoinWithStatus.fyleId,
                attachment.fyleMessageJoinWithStatus.messageId
            ) {
                FyleProgressSingleton.getProgress(
                    attachment.fyleMessageJoinWithStatus.fyleId,
                    attachment.fyleMessageJoinWithStatus.messageId
                )
            }.observeAsState()

            val speed: String? by remember {
                derivedStateOf {
                    (progressStatus as? ProgressStatus.InProgress)?.speedAndEta?.speedBps?.let {
                        if (it >= 10000000000f) {
                            context.getString(
                                R.string.xx_gbps,
                                String.format(
                                    Locale.ENGLISH,
                                    "%d",
                                    (it / 1000000000f).toInt()
                                )
                            )
                        } else if (it >= 1000000000f) {
                            context.getString(
                                R.string.xx_gbps,
                                String.format(
                                    Locale.ENGLISH,
                                    "%1.1f",
                                    it / 1000000000f
                                )
                            )
                        } else if (it >= 10000000f) {
                            context.getString(
                                R.string.xx_mbps,
                                String.format(
                                    Locale.ENGLISH,
                                    "%d",
                                    (it / 1000000f).toInt()
                                )
                            )
                        } else if (it >= 1000000f) {
                            context.getString(
                                R.string.xx_mbps,
                                String.format(
                                    Locale.ENGLISH,
                                    "%1.1f",
                                    it / 1000000f
                                )
                            )
                        } else if (it >= 10000f) {
                            context.getString(
                                R.string.xx_kbps,
                                String.format(
                                    Locale.ENGLISH,
                                    "%d",
                                    (it / 1000f).toInt()
                                )
                            )
                        } else if (it >= 1000f) {
                            context.getString(
                                R.string.xx_kbps,
                                String.format(Locale.ENGLISH, "%1.1f", it / 1000f)
                            )
                        } else {
                            context.getString(
                                R.string.xx_bps,
                                String.format(
                                    Locale.ENGLISH,
                                    "%d",
                                    it.roundToInt()
                                )
                            )
                        }
                    }
                }
            }

            val eta: String? by remember {
                derivedStateOf {
                    (progressStatus as? ProgressStatus.InProgress)?.speedAndEta?.etaSeconds?.let {
                        if (it > 5940) {
                            context.getString(
                                R.string.text_timer_h,
                                it / 3600
                            )
                        } else if (it > 99) {
                            context.getString(
                                R.string.text_timer_m,
                                it / 60
                            )
                        } else if (it > 0) {
                            context.getString(R.string.text_timer_s, it)
                        } else {
                            "-"
                        }
                    }
                }
            }

            val progress: Float by remember {
                derivedStateOf {
                    when (progressStatus) {
                        ProgressStatus.Finished -> 1f
                        is ProgressStatus.InProgress -> (progressStatus as ProgressStatus.InProgress).progress
                        ProgressStatus.Unknown -> 0f
                        null -> 0f
                    }
                }
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
                        multipleAttachment = attachments.size > 1,
                        saveAttachment = saveAttachment,
                        saveAllAttachments = saveAllAttachments
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
                                .then(if (blockClicks.not() && openOnClick) {
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
                        var imageUri: ImageRequest? by remember {
                            mutableStateOf(null)
                        }
                        val cacheKey =
                            "${attachment.fyleMessageJoinWithStatus.fyleId}-${attachment.fyleMessageJoinWithStatus.messageId}"
                        LaunchedEffect(
                            attachment.fyleMessageJoinWithStatus.miniPreview != null,
                            attachment.fyle.filePath
                        ) {
                            imageUri = if (attachment.fyle.isComplete) {
                                ImageRequest.Builder(context)
                                    .data(attachment.deterministicContentUriForGallery)
                                    .placeholderMemoryCacheKey(cacheKey)
                                    .build()
                            } else if (attachment.fyleMessageJoinWithStatus.miniPreview != null) {
                                ImageRequest.Builder(context)
                                    .data(attachment.fyleMessageJoinWithStatus.miniPreview)
                                    .memoryCacheKey(cacheKey)
                                    .build()
                            } else {
                                null
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
                                        imageUri = ImageRequest.Builder(context)
                                            .data(R.drawable.ic_broken_image)
                                            .build()
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
                                .drawWithContent {
                                    drawContent()
                                    highlightImageTextBlock(
                                        textBlocks = textBlocks,
                                        discussionSearchViewModel = discussionSearchViewModel,
                                        selectedImage = SelectedImage(
                                            imageResolution = imageResolutions?.getOrNull(index),
                                            uri = null,
                                            cacheKey = cacheKey,
                                        ),
                                        textElementColor = highlightColor
                                    )
                                }
                                .then(
                                    if (blockClicks)
                                        Modifier
                                    else
                                        Modifier.combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            onClick = {
                                                downloadAwareClick {
                                                    if (message.isLocationMessage) {
                                                        onLocationClicked?.invoke()
                                                    } else if (attachment.fyleMessageJoinWithStatus.mimeType != "image/svg+xml") {
                                                        val showTextBlocksInGallery = textBlocks.isNotEmpty()
                                                                && (discussionSearchViewModel?.matchedMessageAndFyleIds?.any {
                                                            it.first == message.id
                                                                    && it.second == attachment.fyle.id
                                                        } == true)

                                                        openViewerCallback?.invoke()
                                                        App.openDiscussionGalleryActivity(
                                                            context,
                                                            message.discussionId,
                                                            attachment.fyleMessageJoinWithStatus.messageId,
                                                            attachment.fyle.id,
                                                            true,
                                                            showTextBlocksInGallery
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
                                        )),
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

                        if (attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/")) {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .requiredSize(64.dp),
                                painter = painterResource(id = R.drawable.overlay_video_small),
                                contentDescription = "video"
                            )
                        }
                        if (discussionSearchViewModel?.filterRegexes?.takeIf { it.isNotEmpty() }
                                ?.all {
                                    StringUtils.unAccent(attachment.fyleMessageJoinWithStatus.fileName)
                                        .contains(it)
                                } == true) {
                            Text(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .background(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White.copy(alpha = .4f)
                                    )
                                    .padding(2.dp)
                                    .width(if (wide || imageCount == 1) maxWidth else (maxWidth / 2 - 10.dp)),
                                text = discussionSearchViewModel.highlight(
                                    context,
                                    AnnotatedString(attachment.fyleMessageJoinWithStatus.fileName)
                                ),
                                maxLines = 2,
                            )
                        }
                    }
                    LaunchedEffect(discussionSearchViewModel?.matchedMessageAndFyleIds) {
                        if (discussionSearchViewModel?.matchedMessageAndFyleIds?.contains(attachment.fyleMessageJoinWithStatus.messageId to attachment.fyle.id) == true) {
                            launch(Dispatchers.IO) {
                                textBlocks =
                                    AppDatabase.getInstance().fyleMessageTextBlockDao().getAll(
                                        messageId = attachment.fyleMessageJoinWithStatus.messageId,
                                        fyleId = attachment.fyleMessageJoinWithStatus.fyleId
                                    )
                            }
                        } else {
                            textBlocks = emptyList()
                        }
                    }

                    if (showStatuses) {
                        AttachmentReceptionStatusIcon(attachment.fyleMessageJoinWithStatus.receptionStatus)
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
                        .then(
                            if (message.isContentHidden) {
                                Modifier.background(colorResource(R.color.almostWhite))
                            } else Modifier
                        )
                ) {
                    if (message.isContentHidden) {
                        Image(
                            modifier = Modifier
                                .width(64.dp)
                                .height(64.dp),
                            painter = painterResource(id = R.drawable.ic_incognito),
                            contentScale = ContentScale.Fit,
                            contentDescription = null
                        )
                        EphemeralVisibilityExplanation(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 64.dp)
                                .align(Alignment.Center),
                            duration = expiration?.visibilityDuration,
                            readOnce = readOnce
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .then(if (blockClicks.not() && openOnClick) {
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
                                } else Modifier)
                        )
                    } else {
                        if (attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("audio/")) {
                            var menuOpened by remember { mutableStateOf(false) }
                            AudioListItem(
                                modifier = Modifier
                                    .background(color = colorResource(id = R.color.almostWhite)),
                                fyleAndStatus = attachment.fyleAndStatus,
                                activity = context as AppCompatActivity,
                                onEnableMessageSwipe = onEnableMessageSwipe,
                                audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                                discussionId = message.discussionId,
                                onLongClick = {
                                    if (blockClicks.not()) {
                                        onAttachmentLongClick(attachment.fyleAndStatus)
                                        menuOpened = true
                                    }
                                },
                                onIncompleteClick = {
                                    if (blockClicks.not()) {
                                        downloadAwareClick {}
                                    }
                                },
                                contextMenu = {
                                    if (blockClicks.not()) {
                                        AttachmentContextMenu(
                                            menuOpened = menuOpened,
                                            message = message,
                                            attachment = attachment,
                                            visibility = VISIBLE,
                                            readOnce = false,
                                            multipleAttachment = attachments.size > 1,
                                            onDismiss = { menuOpened = false },
                                            saveAttachment = saveAttachment,
                                            saveAllAttachments = saveAllAttachments
                                        )
                                    }
                                }
                            )
                        } else {
                            var menuOpened by remember { mutableStateOf(false) }
                            FyleListItem(
                                modifier = Modifier
                                    .background(colorResource(id = R.color.almostWhite)),
                                fyleAndStatus = attachment.fyleAndStatus,
                                fileName = discussionSearchViewModel?.highlight(
                                    context,
                                    AnnotatedString(attachment.fyleMessageJoinWithStatus.fileName)
                                )
                                    ?: AnnotatedString(attachment.fyleMessageJoinWithStatus.fileName),
                                onClick = {
                                    if (blockClicks.not()) {
                                        downloadAwareClick {
                                            App.openFyleViewer(
                                                context,
                                                attachment.fyleAndStatus
                                            ) {
                                                openViewerCallback?.invoke()
                                                attachment.fyleMessageJoinWithStatus.markAsOpened()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (blockClicks.not()) {
                                        onAttachmentLongClick(attachment.fyleAndStatus)
                                        menuOpened = true
                                    }
                                },
                                contextMenu = {
                                    if (blockClicks.not()) {
                                        AttachmentContextMenu(
                                            menuOpened = menuOpened,
                                            message = message,
                                            attachment = attachment,
                                            visibility = VISIBLE,
                                            readOnce = false,
                                            multipleAttachment = attachments.size > 1,
                                            onDismiss = { menuOpened = false },
                                            saveAttachment = saveAttachment,
                                            saveAllAttachments = saveAllAttachments
                                        )
                                    }
                                },
                                previewBorder = false
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.TopStart)
                    ) {
                        AttachmentReceptionStatusIcon(attachment.fyleMessageJoinWithStatus.receptionStatus)
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

private fun ContentDrawScope.highlightImageTextBlock(
    textBlocks: List<TextBlock>,
    discussionSearchViewModel: DiscussionSearchViewModel?,
    selectedImage: SelectedImage?,
    textBlockColor: Color? = null,
    textElementColor: Color? = null,
) {
    textBlocks
        .takeIf { it.isNotEmpty() }
        ?.let {
            textBlocks.forEach { textBlock ->
                if (discussionSearchViewModel?.filterRegexes?.takeIf { it.isNotEmpty() }?.any {
                        it.containsMatchIn(textBlock.text)
                    } == true) {
                    textBlock.boundingBox
                        ?.let { rect ->
                            val adjustedRect =
                                rect.adjustToImageRect(
                                    viewWidth = size.width,
                                    viewHeight = size.height,
                                    imageWidth = selectedImage?.imageResolution?.width?.toFloat()
                                        ?: size.width,
                                    imageHeight = selectedImage?.imageResolution?.height?.toFloat()
                                        ?: size.height,
                                ).apply {
                                    // crop the bounding boxes inside the image viewport
                                    left = left.coerceIn(0f, size.width)
                                    right = right.coerceIn(0f, size.width)
                                    top = top.coerceIn(0f, size.height)
                                    bottom = bottom.coerceIn(0f, size.height)
                                }
                            if (adjustedRect.bottom - adjustedRect.top < 1f || adjustedRect.right - adjustedRect.left < 1f) {
                                // do not draw small bounding boxes (corresponds to matches outside the viewport)
                                return@forEach
                            }
                            val color = textBlockColor?.takeIf { textBlock.isBlock }
                                ?: textElementColor?.takeIf { textBlock.isBlock.not() }
                            color?.let {
                                drawRoundRect(
                                    color = color.copy(alpha = 0.5f),
                                    topLeft = Offset(
                                        x = adjustedRect.left,
                                        y = adjustedRect.top
                                    ),
                                    size = Size(
                                        width = adjustedRect.width(),
                                        height = adjustedRect.height()
                                    ),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                }
            }
        }
}

@Composable
private fun BoxScope.AttachmentReceptionStatusIcon(receptionStatus: Int) {
    when (receptionStatus) {
        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED -> R.drawable.ic_message_status_delivered_one
        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED_AND_READ -> R.drawable.ic_message_status_delivered_and_read_one
        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED_ALL -> R.drawable.ic_message_status_delivered_all
        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED_ALL_READ_ONE -> R.drawable.ic_message_status_delivered_all_read_one
        FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED_ALL_READ_ALL -> R.drawable.ic_message_status_delivered_all_read_all
        else -> null
    }?.apply {
        Image(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(
                    shape = CircleShape,
                    color = colorResource(R.color.whiteOverlay)
                )
                .padding(2.dp),
            painter = painterResource(id = this),
            contentDescription = null
        )
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
    saveAttachment: () -> Unit,
    saveAllAttachments: () -> Unit
) {
    val context = LocalContext.current
    // delete
    val delete = @Composable {
        OlvidDropdownMenuItem(
            onClick = {
                if (message.isWithoutText && multipleAttachment.not()) {
                    // we are deleting the last attachment, show the delete message dialog
                    SecureDeleteEverywhereDialogBuilder.openForSingleMessage(context, message)
                } else {
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
                }
                onDismiss()
            },
            textColor = colorResource(R.color.red),
            text = stringResource(id = R.string.menu_action_delete),
        )
    }
    // open
    val open = @Composable {
        OlvidDropdownMenuItem(
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
                        true,
                        false
                    )
                } else {
                    App.openFyleViewer(context, attachment.fyleAndStatus) {
                        openViewerCallback?.invoke()
                        attachment.fyleMessageJoinWithStatus.markAsOpened()
                    }
                }
                onDismiss()
            },
            text = stringResource(id = R.string.menu_action_open),
        )
    }

    OlvidDropdownMenu(
        expanded = menuOpened,
        onDismissRequest = onDismiss
    ) {
        if (visibility == HIDDEN || readOnce) {
            delete()
        } else if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DRAFT) {
            open()
            delete()
        } else if (attachment.fyle.isComplete) {
            open()
            // save
            OlvidDropdownMenuItem(
                onClick = {
                    saveAttachment()
                    onDismiss()
                },
                text = stringResource(id = R.string.menu_action_save),
            )
            if (multipleAttachment) {
                // saveAll
                OlvidDropdownMenuItem(
                    onClick = {
                        saveAllAttachments()
                        onDismiss()
                    },
                    text = stringResource(id = R.string.menu_action_save_all),
                )
            }
            // share
            OlvidDropdownMenuItem(
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
                text = stringResource(id = R.string.menu_action_share),
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

fun Rect.adjustToImageRect(
    viewWidth: Float,
    viewHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
): RectF {
    val viewAspectRatio = viewWidth / viewHeight
    val imageAspectRatio = imageWidth / imageHeight
    val scaleFactor: Float
    var postScaleWidthOffset = 0f
    var postScaleHeightOffset = 0f
    if (viewAspectRatio > imageAspectRatio) {
        // The image needs to be vertically cropped to be displayed in this view.
        scaleFactor = viewWidth / imageWidth
        postScaleHeightOffset =
            (viewWidth / imageAspectRatio - viewHeight) / 2
    } else {
        // The image needs to be horizontally cropped to be displayed in this view.
        scaleFactor = viewHeight / imageHeight
        postScaleWidthOffset =
            (viewHeight * imageAspectRatio - viewWidth) / 2
    }
    return RectF(
        left * scaleFactor - postScaleWidthOffset,
        top * scaleFactor - postScaleHeightOffset,
        right * scaleFactor - postScaleWidthOffset,
        bottom * scaleFactor - postScaleHeightOffset
    )
}