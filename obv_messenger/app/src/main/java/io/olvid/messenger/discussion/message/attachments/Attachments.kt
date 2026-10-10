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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.map
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.content.Context
import coil.memory.MemoryCache
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
import io.olvid.messenger.customClasses.attachShareUri
import io.olvid.messenger.customClasses.formatBytesSpeed
import io.olvid.messenger.customClasses.formatEtaSeconds
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
import io.olvid.messenger.designsystem.components.OverflowIndicator
import io.olvid.messenger.designsystem.components.OverflowItems
import io.olvid.messenger.designsystem.constantSp
import io.olvid.messenger.designsystem.dampened
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.gallery.AudioListItem
import io.olvid.messenger.discussion.gallery.FyleListItem
import io.olvid.messenger.discussion.message.EphemeralVisibilityExplanation
import io.olvid.messenger.discussion.message.attachments.Visibility.HIDDEN
import io.olvid.messenger.discussion.message.attachments.Visibility.VISIBLE
import io.olvid.messenger.discussion.search.DiscussionSearchViewModel
import io.olvid.messenger.owneddetails.UseImageAsProfilePictureActivity
import io.olvid.messenger.owneddetails.canBeUsedAsProfilePicture
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Number of image/video tiles composed in a message bubble; the rest is folded into a "+N" overlay. */
private const val MAX_VISIBLE_IMAGE_ATTACHMENTS = 4

private fun visibleImageTileCount(imageCount: Int) = minOf(imageCount, MAX_VISIBLE_IMAGE_ATTACHMENTS)

enum class Visibility {
    VISIBLE,
    HIDDEN,
}

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

fun FyleAndStatus.attachment() : Attachment {
    return Attachment(fyle, fyleMessageJoinWithStatus)
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
    scale: Float = 1f,
    onEnableMessageSwipe: ((Boolean) -> Unit)? = null,
    showStatuses: Boolean = true,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?,
    onAttachmentLongClick: (FyleAndStatus) -> Unit,
    onCancelAttachmentUpload: (FyleAndStatus) -> Unit,
    maxWidth: Dp,
    openOnClick: Boolean = true,
    onLocationClicked: (() -> Unit)? = null,
    openViewerCallback: (() -> Unit)? = null,
    /** Host-provided opener for image/video tiles (in-discussion viewer or album); null falls back to GalleryActivity. */
    openImageAttachment: ((message: Message, fyleAndStatus: FyleAndStatus, showTextBlocks: Boolean, wholeMessage: Boolean) -> Unit)? = null,
    /** Wraps each image/video tile so a host can attach visibility and shared-element modifiers to it. */
    imageTileModifier: @Composable (fyleAndStatus: FyleAndStatus, hiddenCount: Int) -> Modifier = { _, _ -> Modifier },
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
    // images and videos are laid out first, files after
    val (images, files) = remember(attachmentFyles) {
        attachmentFyles.orEmpty().partition { PreviewUtils.mimeTypeIsSupportedImageOrVideo(it.fyleMessageJoinWithStatus.getNonNullMimeType()) }
    }
    val expiration: JsonExpiration? = message.jsonMessage.jsonExpiration
    val readOnce = expiration?.readOnce == true
    val imageCount = images.size
    val visibleImageCount = visibleImageTileCount(imageCount)
    val noFileAttachments = files.isEmpty()
    val multipleAttachments = images.size + files.size > 1
    val minimumHeight = remember(message.imageResolutions, message.imageAndVideoCount, message.totalAttachmentCount, maxWidth, scale) {
        getMinimumHeight(message, maxWidth, 4.dp, 64.dp * scale.dampened())
    }

    var imageResolutions: Array<ImageResolution>? by remember { mutableStateOf(null) }

    LaunchedEffect(message.imageResolutions) {
        imageResolutions = runCatching {
            ImageResolution.parseMultiple(message.imageResolutions)
        }.getOrNull()
    }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(minimumHeight)
            .clip(RoundedCornerShape(6.dp)),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalArrangement = Arrangement.SpaceBetween,
        maxItemsInEachRow = 2,
    ) {
        // only the first MAX_VISIBLE_IMAGE_ATTACHMENTS images are composed; the last visible tile
        // carries a "+N" overlay for the hidden ones, and every tile then opens the album
        OverflowItems(items = images, maxVisible = MAX_VISIBLE_IMAGE_ATTACHMENTS) { index, attachment, hiddenCount ->
            var textBlocks by remember { mutableStateOf(emptyList<TextBlock>()) }
            val attachmentProgress by rememberAttachmentProgress(attachment.fyleMessageJoinWithStatus)
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
                    multipleAttachment = multipleAttachments,
                    onCancelAttachmentUpload = onCancelAttachmentUpload,
                    saveAttachment = saveAttachment,
                    saveAllAttachments = saveAllAttachments
                )
                val wide =
                    visibleImageCount == 2 || (visibleImageCount > 2 && index == visibleImageCount - 1 && (visibleImageCount and 1) != 0)

                if (message.isContentHidden) {
                    Image(
                        modifier = Modifier
                            .width(if (wide || visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
                            .requiredHeight(if (visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
                            .border(
                                width = 1.dp,
                                color = colorResource(id = R.color.attachmentBorder),
                                shape = RoundedCornerShape(
                                    if (index == 0) 6.dp else 0.dp,
                                    if ((index == 0 && visibleImageCount < 3) || (index == 1 && visibleImageCount >= 3)) 6.dp else 0.dp,
                                    if ((noFileAttachments) && (index == visibleImageCount - 1)) 6.dp else 0.dp,
                                    if ((noFileAttachments) &&
                                        ((index == 1 && visibleImageCount == 2)
                                                || (index == visibleImageCount - 1 && ((visibleImageCount and 1) != 0))
                                                || (index == visibleImageCount - 2 && visibleImageCount > 2 && ((visibleImageCount and 1) == 0)))
                                    ) 6.dp else 0.dp
                                )
                            )
                            .then(if (blockClicks.not() && openOnClick) {
                                Modifier.clickable(
                                    indication = ripple(),
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    attachment.fyleAndStatus.downloadAwareClick {
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
                            .requiredSizeIn(
                                maxWidth = if (wide || visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp),
                                maxHeight = if (visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp)
                            )
                            .padding(top = 8.dp),
                        duration = expiration?.visibilityDuration,
                        readOnce = readOnce
                    )
                } else {
                    var imageUri: ImageRequest? by remember {
                        mutableStateOf(null)
                    }
                    val cacheKey = attachment.fyleAndStatus.thumbnailPlaceholderCacheKey()
                    LaunchedEffect(
                        attachment.fyleMessageJoinWithStatus.miniPreview != null,
                        attachment.fyle.filePath
                    ) {
                        imageUri = attachment.fyleAndStatus.thumbnailRequest(context)
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

                    val tileWidth = if (wide || visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp)
                    val tileHeight = imageResolutions
                        ?.getOrNull(index)
                        ?.getPreferredHeight(tileWidth, wide, 2.dp)
                        ?: if (visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp)
                    Box(
                        modifier = Modifier
                            .width(tileWidth)
                            .requiredHeight(tileHeight)
                            .border(
                                width = 1.dp,
                                color = colorResource(id = R.color.attachmentBorder),
                                shape = RoundedCornerShape(
                                    if (index == 0) 6.dp else 0.dp,
                                    if ((index == 0 && visibleImageCount < 3) || (index == 1 && visibleImageCount >= 3)) 6.dp else 0.dp,
                                    if ((noFileAttachments) && (index == visibleImageCount - 1)) 6.dp else 0.dp,
                                    if ((noFileAttachments) &&
                                        ((index == 1 && visibleImageCount == 2)
                                                || (index == visibleImageCount - 1 && ((visibleImageCount and 1) != 0))
                                                || (index == visibleImageCount - 2 && visibleImageCount > 2 && ((visibleImageCount and 1) == 0)))
                                    ) 6.dp else 0.dp
                                )
                            )
                            .then(
                                if (blockClicks)
                                    Modifier
                                else
                                    Modifier.combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = {
                                            val onClick: () -> Unit = {
                                                if (message.isLocationMessage) {
                                                    onLocationClicked?.invoke()
                                                } else if (attachment.fyleMessageJoinWithStatus.mimeType != "image/svg+xml") {
                                                    val showTextBlocksInGallery = textBlocks.isNotEmpty()
                                                            && (discussionSearchViewModel?.matchedMessageAndFyleIds?.any {
                                                        it.first == message.id
                                                                && it.second.first == attachment.fyle.id
                                                    } == true)

                                                    if (openImageAttachment != null) {
                                                        openImageAttachment(message, attachment.fyleAndStatus, showTextBlocksInGallery, imageCount > visibleImageCount)
                                                    } else {
                                                        openViewerCallback?.invoke()
                                                        App.openDiscussionGalleryActivity(
                                                            context,
                                                            message.discussionId,
                                                            attachment.fyleMessageJoinWithStatus.messageId,
                                                            attachment.fyle.id,
                                                            null,
                                                            true,
                                                            showTextBlocksInGallery
                                                        )
                                                    }
                                                }
                                            }
                                            if (hiddenCount == 0) {
                                                attachment.fyleAndStatus.downloadAwareClick (onClick)
                                            } else {
                                                onClick()
                                            }
                                        },
                                        onLongClick = {
                                            onAttachmentLongClick(attachment.fyleAndStatus)
                                            if (message.isLocationMessage.not()) {
                                                attachmentContextMenuOpened = true
                                            }
                                        }
                                    ))
                    ) {
                        // the "+N" and play overlays hide with the picture while the viewer or the album close flight draws it
                        Box(modifier = imageTileModifier(attachment.fyleAndStatus, hiddenCount).fillMaxSize()) {
                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxSize()
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
                                },
                                model = imageUri,
                                imageLoader = imageLoader,
                                contentScale = if ((imageCount > 1 || attachment.fyleMessageJoinWithStatus.nonNullMimeType != "image/gif") && (attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith(
                                        "image/"
                                    ) || attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith(
                                        "video/"
                                    ))
                                ) ContentScale.Crop else ContentScale.Fit,
                                contentDescription = attachment.fyleMessageJoinWithStatus.fileName,
                            )
                            // the "+N" overlay replaces the play icon on the last tile
                            if (hiddenCount > 0) {
                                OverflowIndicator(hiddenCount = hiddenCount, textStyle = OlvidTypography.h2)
                            } else if (attachment.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/")) {
                                Image(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .requiredSize(64.dp),
                                    painter = painterResource(id = R.drawable.overlay_video_small),
                                    contentDescription = "video"
                                )
                            }
                        }
                    }
                    if (discussionSearchViewModel?.filterRegexes?.takeIf { it.isNotEmpty() }
                            ?.all {
                                StringUtils.unAccent(attachment.fyleMessageJoinWithStatus.fileName)
                                    .contains(it)
                            } == true) {
                        Text(
                            modifier = Modifier
                                .widthIn(max = if (wide || visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
                                .padding(3.dp)
                                .background(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colorResource(R.color.whiteOpaqueOverlay)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            color = colorResource(R.color.almostBlack),
                            text = discussionSearchViewModel.highlight(
                                context,
                                AnnotatedString(attachment.fyleMessageJoinWithStatus.fileName)
                            ),
                            fontSize = 14.sp * scale.dampened(),
                            maxLines = 2,
                        )
                    }
                }
                LaunchedEffect(discussionSearchViewModel?.matchedMessageAndFyleIds) {
                    if (discussionSearchViewModel?.matchedMessageAndFyleIds?.any {it.first == attachment.fyleMessageJoinWithStatus.messageId && it.second.first == attachment.fyle.id } == true) {
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
                            .width(if (wide || visibleImageCount == 1) maxWidth else (maxWidth / 2 - 2.dp))
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
                            speed = attachmentProgress.speed,
                            eta = attachmentProgress.eta,
                            progress = attachmentProgress.progress,
                            large = visibleImageCount == 1
                        )
                    }

                    FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE -> {
                        Image(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(if (visibleImageCount == 1) 0.dp else 4.dp)
                                .size(if (visibleImageCount == 1) 64.dp else 32.dp),
                            painter = painterResource(id = R.drawable.ic_file_download),
                            contentDescription = null
                        )
                    }

                    FyleMessageJoinWithStatus.STATUS_DRAFT, FyleMessageJoinWithStatus.STATUS_COMPLETE -> { }

                    FyleMessageJoinWithStatus.STATUS_FAILED -> {
                        Image(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(if (visibleImageCount == 1) 0.dp else 4.dp)
                                .size(if (visibleImageCount == 1) 64.dp else 32.dp),
                            painter = painterResource(id = R.drawable.ic_attachment_status_failed),
                            contentDescription = null
                        )
                    }

                    FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED -> {
                        Icon(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(24.dp),
                            painter = painterResource(id = R.drawable.ic_transfer),
                            tint = colorResource(R.color.red),
                            contentDescription = null
                        )
                    }
                }
            }
        }
        files.forEachIndexed { fileIndex, attachment ->
            val attachmentProgress by rememberAttachmentProgress(attachment.fyleMessageJoinWithStatus)
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = colorResource(id = R.color.attachmentBorder),
                        shape = RoundedCornerShape(
                            if (imageCount == 0 && fileIndex == 0) 6.dp else 0.dp,
                            if (imageCount == 0 && fileIndex == 0) 6.dp else 0.dp,
                            if (fileIndex == files.lastIndex) 6.dp else 0.dp,
                            if (fileIndex == files.lastIndex) 6.dp else 0.dp,
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
                                    attachment.fyleAndStatus.downloadAwareClick {
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
                            scale = scale,
                            activity = context as AppCompatActivity,
                            discussionSearchViewModel = discussionSearchViewModel,
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
                                    attachment.fyleAndStatus.downloadAwareClick {}
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
                                        multipleAttachment = multipleAttachments,
                                        onDismiss = { menuOpened = false },
                                        onCancelAttachmentUpload = onCancelAttachmentUpload,
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
                            scale = scale,
                            fileName = AnnotatedString(attachment.fyleMessageJoinWithStatus.fileName).let {
                                discussionSearchViewModel?.highlight(context, it) ?: it
                            },
                            onClick = {
                                if (blockClicks.not()) {
                                    attachment.fyleAndStatus.downloadAwareClick {
                                        App.openFyleViewer(
                                            context,
                                            attachment.fyleAndStatus,
                                            { attachment.fyleMessageJoinWithStatus.markAsOpened() },
                                            { openViewerCallback?.invoke() }
                                        )
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
                                        multipleAttachment = multipleAttachments,
                                        onDismiss = { menuOpened = false },
                                        onCancelAttachmentUpload = onCancelAttachmentUpload,
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
                            speed = attachmentProgress.speed,
                            eta = attachmentProgress.eta,
                            progress = attachmentProgress.progress,
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

                    FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED -> {
                        Icon(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(32.dp)
                                .padding(4.dp),
                            painter = painterResource(id = R.drawable.ic_transfer),
                            tint = colorResource(R.color.red),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

/** Shared-element key of an image/video attachment, common to bubble tiles, album cells and viewer pages. */
fun attachmentSharedElementKey(messageId: Long, fyleId: Long) = "attachment-$messageId-$fyleId"

fun attachmentSharedElementKey(fyleAndStatus: FyleAndStatus) =
    attachmentSharedElementKey(fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyle.id)

/** Coil key under which a tile keeps its mini preview until the file is decoded. */
fun FyleAndStatus.thumbnailPlaceholderCacheKey() =
    "${fyleMessageJoinWithStatus.fyleId}-${fyleMessageJoinWithStatus.messageId}"

/**
 * Coil key under which the decoded thumbnail of a complete attachment ends up: Coil's default key for
 * its content Uri, as [thumbnailRequest] sets none. Read back by the viewer's placeholder and morph.
 */
fun FyleAndStatus.decodedThumbnailCacheKey(): MemoryCache.Key? =
    deterministicContentUriForGallery?.let { MemoryCache.Key(it.toString()) }

/** Thumbnail request shared by bubble tiles and album cells: the file once complete, else the mini preview. */
fun FyleAndStatus.thumbnailRequest(context: Context): ImageRequest? = when {
    fyle.isComplete -> ImageRequest.Builder(context)
        .data(deterministicContentUriForGallery)
        .placeholderMemoryCacheKey(thumbnailPlaceholderCacheKey())
        .build()

    fyleMessageJoinWithStatus.miniPreview != null -> ImageRequest.Builder(context)
        .data(fyleMessageJoinWithStatus.miniPreview)
        .memoryCacheKey(thumbnailPlaceholderCacheKey())
        .build()

    else -> null
}

/**
 * Runs [completeClick] for complete/draft/uploading attachments, starts or stops the download for
 * downloadable/downloading ones, and ignores failed, untransferred and copying ones.
 */
fun FyleAndStatus.downloadAwareClick(completeClick: () -> Unit) {
    when (fyleMessageJoinWithStatus.status) {
        FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE ->
            App.runThread(StartAttachmentDownloadTask(fyleMessageJoinWithStatus))

        FyleMessageJoinWithStatus.STATUS_DOWNLOADING ->
            App.runThread(StopAttachmentDownloadTask(fyleMessageJoinWithStatus))

        FyleMessageJoinWithStatus.STATUS_DRAFT,
        FyleMessageJoinWithStatus.STATUS_UPLOADING,
        FyleMessageJoinWithStatus.STATUS_COMPLETE,
            -> completeClick()
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
        FyleMessageJoinWithStatus.RECEPTION_STATUS_CANCELLED -> R.drawable.ic_message_status_undelivered
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
    attachmentFileHeight: Dp,
): Dp {
    var attachmentsHeight = 0.dp
    // only the first MAX_VISIBLE_IMAGE_ATTACHMENTS images are laid out, the others hide behind a "+N" overlay
    val visibleImageCount = visibleImageTileCount(message.imageAndVideoCount)
    val imageResolutions = runCatching {
        ImageResolution.parseMultiple(message.imageResolutions)
    }.getOrNull()
        ?.takeIf { it.size == message.imageAndVideoCount }
        ?.take(visibleImageCount)

    if (!imageResolutions.isNullOrEmpty()) {
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
        if (visibleImageCount == 1) {
            attachmentsHeight += displayWidth
        } else if (visibleImageCount == 2) {
            attachmentsHeight += displayWidth
        } else if (visibleImageCount > 2) {
            if ((visibleImageCount and 1) != 0) {
                attachmentsHeight += ((displayWidth + attachmentSpace) / 2) * (visibleImageCount / 2)
                attachmentsHeight += (displayWidth - attachmentSpace) / 2
            } else {
                attachmentsHeight += ((displayWidth + attachmentSpace) / 2) * (visibleImageCount / 2)
                attachmentsHeight -= attachmentSpace
            }
        }
    }

    // files
    attachmentsHeight += (attachmentFileHeight + attachmentSpace) * (message.totalAttachmentCount - message.imageAndVideoCount)
    if (message.imageAndVideoCount == 0) {
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
        FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED -> stringResource(id = R.string.label_untransferred)
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
    onOpen: (() -> Unit)? = null,
    onCancelAttachmentUpload: (FyleAndStatus) -> Unit,
    saveAttachment: () -> Unit,
    saveAllAttachments: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
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
                            resources.getString(
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
                if (onOpen != null) {
                    onOpen()
                } else if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(
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
                        null,
                        true,
                        false
                    )
                } else {
                    App.openFyleViewer(
                        context,
                        attachment.fyleAndStatus,
                        { attachment.fyleMessageJoinWithStatus.markAsOpened() },
                        { openViewerCallback?.invoke() }
                    )
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
                    val uri = attachment.contentUriForExternalSharing
                    val mime = attachment.fyleMessageJoinWithStatus.nonNullMimeType
                    val intent = Intent(Intent.ACTION_SEND).attachShareUri(uri, mime)
                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            resources.getString(R.string.title_sharing_chooser)
                        )
                    )
                    onDismiss()
                },
                text = stringResource(id = R.string.menu_action_share),
            )
            if (attachment.fyleAndStatus.canBeUsedAsProfilePicture) {
                // use as profile picture
                OlvidDropdownMenuItem(
                    onClick = {
                        UseImageAsProfilePictureActivity.launch(context, attachment.fyleAndStatus)
                        onDismiss()
                    },
                    text = stringResource(id = R.string.menu_action_use_image_as),
                )
            }
            if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_UPLOADING) {
                // cancel attachment upload
                OlvidDropdownMenuItem(
                    onClick = {
                        onCancelAttachmentUpload(attachment.fyleAndStatus)
                        onDismiss()
                    },
                    textColor = colorResource(R.color.red),
                    text = stringResource(id = R.string.menu_action_cancel_upload),
                )
            }
            if (message.status != Message.STATUS_UNPROCESSED && message.status != Message.STATUS_COMPUTING_PREVIEW && message.status != Message.STATUS_PROCESSING) {
                delete()
            }
        } else {
            if (attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED
                || attachment.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED) {
                delete()
            } else {
                open()
                delete()
            }
        }
    }
}

data class AttachmentProgress(
    val progress: Float = 0f,
    val speed: String? = null,
    val eta: String? = null,
)

@Composable
fun rememberAttachmentProgress(join: FyleMessageJoinWithStatus): State<AttachmentProgress> {
    val context = LocalContext.current
    val progressStatus: ProgressStatus? by remember(join.fyleId, join.messageId) {
        FyleProgressSingleton.getProgress(join.fyleId, join.messageId)
    }.observeAsState()
    return remember(context) {
        derivedStateOf {
            when (val status = progressStatus) {
                is ProgressStatus.InProgress -> AttachmentProgress(
                    progress = status.progress,
                    speed = status.speedAndEta?.speedBps?.formatBytesSpeed(context),
                    eta = status.speedAndEta?.etaSeconds?.formatEtaSeconds(context)
                )

                ProgressStatus.Finished -> AttachmentProgress(progress = 1f)
                else -> AttachmentProgress()
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
                max = (scale * 74).dp
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
                    .width((if (scale < 1.5f) 50 else 85).dp)
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
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AttachmentDownloadProgress(speed = 352000f.formatBytesSpeed(context), eta = 45.formatEtaSeconds(context), progress = .37f)
        AttachmentDownloadProgress(speed = null, eta = null, progress = 0f)
        AttachmentDownloadProgress(
            speed = 398420000f.formatBytesSpeed(context),
            eta = 57.formatEtaSeconds(context),
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