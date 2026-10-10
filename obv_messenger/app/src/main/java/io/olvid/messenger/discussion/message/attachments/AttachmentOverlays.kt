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

import android.app.Activity
import android.view.WindowManager.LayoutParams
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.SharedContentConfig
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.customClasses.StringUtils
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.map
import io.olvid.messenger.customClasses.ImageResolution
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.designsystem.components.OverflowScrimColor
import io.olvid.messenger.designsystem.stableSystemBars
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.AttachmentAnchor
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.GalleryRequest
import io.olvid.messenger.discussion.message.attachments.Visibility.VISIBLE
import io.olvid.messenger.gallery.GalleryScreen
import io.olvid.messenger.gallery.GalleryViewModel
import io.olvid.messenger.gallery.isVideo
import io.olvid.messenger.gallery.rememberSaveAttachmentAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

// Album cells keep the media's aspect ratio within these bounds so panoramas and tall screenshots
// neither collapse to a strip nor take the whole column
private const val MIN_ALBUM_CELL_RATIO = 0.6f
private const val MAX_ALBUM_CELL_RATIO = 1.8f
private const val ALBUM_COLUMNS = 2
private val ALBUM_COLUMN_STAGGER = 56.dp
private const val ALBUM_CELL_FADE_IN_DELAY_MS = 120
private const val ALBUM_CELL_FADE_IN_MS = 250

/**
 * Full-screen album of every image and video of one message, opened from the "+N" tile of a
 * bubble. Rows are laid out from the message's stored resolutions, so nothing is decoded until a
 * row scrolls into view. The bubble tiles morph into their cells as shared elements and fly back
 * drawn by [AlbumCloseFlight]; a cell morphs into the viewer page when tapped.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.MessageAlbumOverlay(
    discussionViewModel: DiscussionViewModel,
    onCancelAttachmentUpload: (FyleAndStatus) -> Unit,
    saveAttachment: () -> Unit,
    saveAllAttachments: () -> Unit,
) {
    val albumMessageId = discussionViewModel.albumMessageId
    // keep the last message around while the exit animation plays
    var shownMessageId by remember { mutableStateOf(albumMessageId) }
    if (albumMessageId != null) {
        shownMessageId = albumMessageId
    }
    // Tiles morph into their cells as shared elements, but the way back is drawn by AlbumCloseFlight:
    // as a shared-element target, a tile in the lazy message list reports stale bounds, so the
    // flight started from a wrong place, or did not show at all.
    val flyingKeys = discussionViewModel.albumFlyingKeys
    // built in the frame the album closes, while its cells are still placed and hide
    val closeFlights = remember(flyingKeys) {
        flyingKeys.mapNotNull { key ->
            val cell = discussionViewModel.albumCellAnchors[key] ?: return@mapNotNull null
            val tile = discussionViewModel.tileAnchors[key] ?: return@mapNotNull null
            AlbumFlight(key, MorphTarget(cell.fyleAndStatus, cell), tile)
        }
    }
    val closeProgress = remember(flyingKeys) { Animatable(0f) }
    LaunchedEffect(flyingKeys) {
        if (flyingKeys.isNotEmpty()) {
            closeProgress.animateTo(1f, tween(MORPH_DURATION_MS, easing = FastOutSlowInEasing))
            // reveal the tiles in the same frame the flights leave
            discussionViewModel.albumFlyingKeys = emptySet()
        }
    }
    // reopened mid-flight
    LaunchedEffect(albumMessageId) {
        if (albumMessageId != null) {
            discussionViewModel.albumFlyingKeys = emptySet()
        }
    }
    DisposableEffect(Unit) {
        onDispose { discussionViewModel.albumFlyingKeys = emptySet() }
    }
    AnimatedVisibility(
        visible = albumMessageId != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val messageId = shownMessageId ?: return@AnimatedVisibility
        val message by remember(messageId) {
            AppDatabase.getInstance().messageDao().getLive(messageId)
        }.observeAsState()
        val fyles by remember(messageId) {
            AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFylesAndStatusForMessage(messageId).map { list -> list.map { it.attachment() } }
        }.observeAsState()
        val medias = remember(fyles) {
            fyles.orEmpty().filter {
                PreviewUtils.mimeTypeIsSupportedImageOrVideo(it.fyleMessageJoinWithStatus.nonNullMimeType)
            }
        }
        // the last media was deleted from the album
        LaunchedEffect(fyles, medias) {
            if (fyles != null && medias.isEmpty()) {
                discussionViewModel.closeAlbum()
            }
        }
        // same index space as the bubble tiles: image/video attachments in DB order
        val resolutions = remember(message?.imageResolutions) {
            runCatching { ImageResolution.parseMultiple(message?.imageResolutions) }.getOrNull().orEmpty()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.stableSystemBars)
        ) {
            AlbumHeader(
                message = message,
                medias = medias,
                onBack = discussionViewModel::closeAlbum,
            )
            // Staggered grid, two columns: each image goes to the currently shorter column, with its
            // aspect ratio clamped so a cell is never much taller than it is wide. Not a lazy layout:
            // lazy items inside a SharedTransitionLayout report wrong bounds once a transition has
            // run (the item offset is counted twice), which sends every morph after the first one
            // to the wrong place.
            val columns = remember(medias, resolutions) {
                val columns = List(ALBUM_COLUMNS) { mutableListOf<Pair<Attachment, Float>>() }
                val heights = FloatArray(ALBUM_COLUMNS)  // in cell widths; odd columns start lower, see below
                medias.forEachIndexed { index, fyleAndStatus ->
                    val ratio = resolutions.getOrNull(index)
                        ?.takeIf { it.width > 0 && it.height > 0 }
                        ?.let { (it.width.toFloat() / it.height).coerceIn(MIN_ALBUM_CELL_RATIO, MAX_ALBUM_CELL_RATIO) }
                        ?: 1f
                    val shortest = heights.indices.minBy { heights[it] }
                    columns[shortest].add(fyleAndStatus to ratio)
                    heights[shortest] += 1f / ratio
                }
                columns
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                columns.forEachIndexed { columnIndex, column ->
                    Column(
                        // odd columns start lower, so the first row reads as staggered too
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = if (columnIndex % 2 == 1) ALBUM_COLUMN_STAGGER else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        column.forEach { (attachment, ratio) ->
                            val onOpen = {
                                discussionViewModel.galleryRequest = GalleryRequest(messageId, attachment.fyle.id)
                            }
                            var menuOpened by remember { mutableStateOf(false) }
                            AlbumCell(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio),
                                sharedTransitionScope = this@MessageAlbumOverlay,
                                albumVisibilityScope = this@AnimatedVisibility,
                                discussionViewModel = discussionViewModel,
                                attachment = attachment,
                                onOpen = onOpen,
                                onLongClick = {
                                    // save / save all read the long-clicked attachment from the view model
                                    discussionViewModel.longClickedFyleAndStatus = attachment.fyleAndStatus
                                    menuOpened = true
                                },
                            ) {
                                message?.let { message ->
                                    AttachmentContextMenu(
                                        menuOpened = menuOpened,
                                        onDismiss = { menuOpened = false },
                                        message = message,
                                        attachment = Attachment(attachment.fyle, attachment.fyleMessageJoinWithStatus),
                                        visibility = VISIBLE,
                                        readOnce = false,
                                        multipleAttachment = message.totalAttachmentCount > 1,
                                        onOpen = { attachment.fyleAndStatus.downloadAwareClick(onOpen) },
                                        onCancelAttachmentUpload = onCancelAttachmentUpload,
                                        saveAttachment = saveAttachment,
                                        saveAllAttachments = saveAllAttachments,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    closeFlights.forEach { flight ->
        key(flight.key) {
            AlbumCloseFlight(flight, closeProgress)
        }
    }
}

/** A cell flying back to its bubble tile at album close: where the cell was, and the live tile anchor. */
private class AlbumFlight(val key: String, val from: MorphTarget, val tile: AttachmentAnchor)

/**
 * The cell's picture drawn from the cell rectangle to its tile's (read every frame, so the flight
 * follows the list), cropped like both and cut to what is visible at each end. The "+N" overlay of
 * the last tile fades in on the way. Without a visible cell, the picture fades in on its tile.
 */
@Composable
private fun AlbumCloseFlight(flight: AlbumFlight, progress: Animatable<Float, AnimationVector1D>) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = remember { flight.from.fyleAndStatus?.thumbnailRequest(context) },
        imageLoader = App.imageLoader,
    )
    val cornerRadiusPx = with(LocalDensity.current) { ATTACHMENT_CORNER_RADIUS.toPx() }
    val clip = remember { Path() }
    val overflowLayout = flight.tile.hiddenCount.takeIf { it > 0 }?.let { hiddenCount ->
        val textMeasurer = rememberTextMeasurer()
        val text = stringResource(R.string.plus_count, hiddenCount)
        val style = OlvidTypography.h2.copy(color = colorResource(R.color.alwaysWhite))
        remember(text, style) { textMeasurer.measure(text, style) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val to = flight.tile.bounds ?: return@Canvas
        val toVisible = flight.tile.visibleBounds ?: return@Canvas
        val p = progress.value
        val from = flight.from.bounds
        val rect = from?.let { lerp(it, to, p) } ?: to
        val alpha = if (from == null) p else 1f
        drawMorphedPicture(
            painter = painter,
            // Coil only starts loading once the painter is drawn at some size
            intrinsic = painter.intrinsicSize.takeIf { it.isSpecified && it.width > 0f && it.height > 0f } ?: rect.size,
            rect = rect,
            visible = flight.from.visibleBounds?.let { lerp(it, toVisible, p) } ?: toVisible,
            clip = clip,
            cornerRadius = cornerRadiusPx,
            alpha = alpha,
        ) {
            overflowLayout?.let { layout ->
                drawRect(OverflowScrimColor.copy(alpha = OverflowScrimColor.alpha * p * alpha), topLeft = rect.topLeft, size = rect.size)
                drawText(
                    textLayoutResult = layout,
                    topLeft = rect.center - Offset(layout.size.width / 2f, layout.size.height / 2f),
                    alpha = p,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumCell(
    modifier: Modifier,
    sharedTransitionScope: SharedTransitionScope,
    albumVisibilityScope: AnimatedVisibilityScope,
    discussionViewModel: DiscussionViewModel,
    attachment: Attachment,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    contextMenu: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // cells without a bubble tile to morph from fade in once the morphing ones are on their way
    val morphsFromTile = remember { attachmentSharedElementKey(attachment.fyleAndStatus) in discussionViewModel.tileAnchors }
    Box(
        modifier = modifier
            .then(
                if (morphsFromTile) Modifier else with(albumVisibilityScope) {
                    Modifier.animateEnterExit(
                        enter = fadeIn(tween(ALBUM_CELL_FADE_IN_MS, delayMillis = ALBUM_CELL_FADE_IN_DELAY_MS)),
                        exit = ExitTransition.None,
                    )
                }
            )
            .clip(RoundedCornerShape(ATTACHMENT_CORNER_RADIUS))
            .combinedClickable(
                onClick = { attachment.fyleAndStatus.downloadAwareClick(onOpen) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
    ) {
        contextMenu()
        AttachmentThumbnail(
            attachment = attachment,
            modifier = with(sharedTransitionScope) {
                attachmentSharedElement(
                    discussionViewModel = discussionViewModel,
                    anchors = discussionViewModel.albumCellAnchors,
                    fyleAndStatus = attachment.fyleAndStatus,
                    visible = albumVisibilityScope.transition.targetState == EnterExitState.Visible,
                )
            }.fillMaxSize()
        )
        if (attachment.fyleAndStatus.isVideo()) {
            Image(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                painter = painterResource(R.drawable.overlay_video_small),
                contentDescription = null
            )
        }
        AlbumCellStatus(attachment.fyleMessageJoinWithStatus)
    }
}

/**
 * Modifier for an image tile or album cell: records it as an anchor for the morphs, hides it while
 * the viewer or the album close flight draws its media, and tags it as a shared element so a tile
 * morphs into its album cell. Hiding is done in the draw phase because caller-managed shared-element
 * visibility only hides when a match exists, and the viewer is not a shared element.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.attachmentSharedElement(
    discussionViewModel: DiscussionViewModel,
    anchors: MutableMap<String, AttachmentAnchor>,
    fyleAndStatus: FyleAndStatus,
    visible: Boolean,
    hiddenCount: Int = 0,
): Modifier {
    val key = attachmentSharedElementKey(fyleAndStatus)
    DisposableEffect(key) {
        onDispose { anchors.remove(key) }
    }
    val sharedContentState = rememberSharedContentState(key, remember(discussionViewModel, key) { NotFlyingBack(discussionViewModel, key) })
    return Modifier
        .onPlaced { coordinates ->
            val anchor = anchors[key]
            if (anchor?.coordinates !== coordinates || anchor.hiddenCount != hiddenCount) {
                anchors[key] = AttachmentAnchor(fyleAndStatus, coordinates, hiddenCount)
            }
        }
        .drawWithContent {
            if (discussionViewModel.galleryCurrentKey != key && key !in discussionViewModel.albumFlyingKeys) drawContent()
        }
        // only album cells are ever a target (tiles fly back with AlbumCloseFlight): in the overlay,
        // they are not faded with the album appearing under them
        .sharedElementWithCallerManagedVisibility(
            sharedContentState = sharedContentState,
            visible = visible,
        )
}

/** Keeps a tile and its cell out of the shared transition while [AlbumCloseFlight] draws them. */
@OptIn(ExperimentalSharedTransitionApi::class)
private class NotFlyingBack(private val discussionViewModel: DiscussionViewModel, private val key: String) : SharedContentConfig {
    override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
        get() = key !in discussionViewModel.albumFlyingKeys
    override val shouldKeepEnabledForOngoingAnimation: Boolean
        get() = false
}

/** [attachmentSharedElement] for a bubble tile, which hides under its own album. */
@Composable
fun SharedTransitionScope.attachmentTileModifier(discussionViewModel: DiscussionViewModel, fyleAndStatus: FyleAndStatus, hiddenCount: Int): Modifier =
    attachmentSharedElement(
        discussionViewModel = discussionViewModel,
        anchors = discussionViewModel.tileAnchors,
        fyleAndStatus = fyleAndStatus,
        visible = discussionViewModel.albumMessageId != fyleAndStatus.fyleMessageJoinWithStatus.messageId,
        hiddenCount = hiddenCount,
    )

/** Back button, sender, then media count and date. */
@Composable
private fun AlbumHeader(message: Message?, medias: List<Attachment>, onBack: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                tint = Color.White,
                contentDescription = stringResource(R.string.content_description_back_button)
            )
        }
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(
                text = when {
                    message == null -> ""
                    message.isInbound -> ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier) ?: ""
                    else -> stringResource(R.string.text_you)
                },
                style = OlvidTypography.h3,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    albumMediaCount(medias),
                    message?.let { StringUtils.getLongNiceDateString(context, it.timestamp).toString() },
                ).joinToString(" · "),
                style = OlvidTypography.body2,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun albumMediaCount(medias: List<Attachment>): String? {
    val videoCount = medias.count { it.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/") }
    val imageCount = medias.size - videoCount
    return listOfNotNull(
        imageCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.text_images, it, it) },
        videoCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.text_videos, it, it) },
    ).joinToString(stringResource(R.string.attachments_joiner)).ifEmpty { null }
}

/** Cropped thumbnail of an image/video attachment: the file when complete, the mini preview otherwise. */
@Composable
private fun AttachmentThumbnail(attachment: Attachment, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val join = attachment.fyleMessageJoinWithStatus
    val request = remember(attachment.fyle.filePath, join.miniPreview != null) {
        attachment.fyleAndStatus.thumbnailRequest(context)
    }
    AsyncImage(
        modifier = modifier,
        model = request,
        imageLoader = App.imageLoader,
        contentScale = if (join.nonNullMimeType == "image/gif") ContentScale.Fit else ContentScale.Crop,
        contentDescription = join.fileName,
    )
}

@Composable
private fun BoxScope.AlbumCellStatus(join: FyleMessageJoinWithStatus) {
    when (join.status) {
        FyleMessageJoinWithStatus.STATUS_DOWNLOADING -> {
            val progress by rememberAttachmentProgress(join)
            AttachmentDownloadProgress(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                speed = progress.speed,
                eta = progress.eta,
                progress = progress.progress,
                large = false
            )
        }

        FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE -> Image(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(32.dp),
            painter = painterResource(R.drawable.ic_file_download),
            contentDescription = null
        )

        FyleMessageJoinWithStatus.STATUS_FAILED -> Image(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(32.dp),
            painter = painterResource(R.drawable.ic_attachment_status_failed),
            contentDescription = null
        )
    }
}

/**
 * Hosts [GalleryScreen] over the discussion instead of in its own activity and animates it in and
 * out of the tapped tile or album cell.
 *
 * The morph is not a Compose shared element (those proved unreliable here: lazy-layout endpoints,
 * repeated sessions over the same source, three nodes per key). A dedicated layer is drawn at a
 * rectangle interpolated between the anchor ([DiscussionViewModel.viewerAnchor]) and the full
 * overlay, showing the picture from the tile's crop to the page's fit, while the pager is kept
 * invisible until it shows the requested media. Both ends coincide with what the tile and the page
 * draw, so the handovers are seamless in every session.
 */
@Composable
fun DiscussionGalleryOverlay(
    discussionViewModel: DiscussionViewModel,
    galleryViewModel: GalleryViewModel,
    applyFlagSecure: () -> Unit,
) {
    val request = discussionViewModel.galleryRequest ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val systemBars = WindowInsets.systemBars
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    // what the morph layer shows while it is on screen (entering or exiting), null once the pager took over
    var morph by remember { mutableStateOf<MorphTarget?>(null) }
    // the pager is only shown between the two morphs; before the first one it would paint black
    var pagerVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val currentFyle by galleryViewModel.currentFyleAndStatus.observeAsState()
    val mediaList by galleryViewModel.imageAndVideoFyleAndStatusList.observeAsState()
    // the pager shows the requested media, so the layer can hand over to it
    val pagerReady by remember(request) {
        derivedStateOf { mediaList != null && currentFyle?.fyle?.id == request.fyleId }
    }

    LaunchedEffect(request) {
        galleryViewModel.resetForNewRequest()
        if (request.draft) {
            galleryViewModel.setMessageId(request.messageId, draft = true)
        } else {
            discussionViewModel.discussionId?.let { galleryViewModel.setDiscussionId(it, null, true) }
        }
        val key = attachmentSharedElementKey(request.messageId, request.fyleId)
        val anchor = discussionViewModel.viewerAnchor(key)
        val target = MorphTarget(anchor?.fyleAndStatus, anchor)
        morph = target
        progress.snapTo(0f)
        target.awaitPicture()
        // the layer is drawn over the tile or cell before that one turns transparent
        withFrameNanos { }
        discussionViewModel.galleryCurrentKey = key
        progress.animateTo(1f, tween(MORPH_DURATION_MS, easing = FastOutSlowInEasing))
        // hold the landed layer until the page exists (list load), bounded so a failure never sticks
        withTimeoutOrNull(1_500.milliseconds) { snapshotFlow { pagerReady }.first { it } }
        pagerVisible = true
        morph = null
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            val current = currentFyle
            val key = current?.let { attachmentSharedElementKey(it) }
            // Bring the system bars back while the viewer still covers everything and, if they were
            // hidden, wait until the status bar inset has settled: it animates in over a few frames
            // and, without stable insets (API < 30), the layout underneath follows it.
            if (systemBars.getTop(density) == 0) {
                (view.context as? Activity)?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
                }
                withTimeoutOrNull(400.milliseconds) {
                    var last = 0
                    var stableFrames = 0
                    while (stableFrames < 2) {
                        withFrameNanos { }
                        val top = systemBars.getTop(density)
                        if (top > 0 && top == last) stableFrames++ else stableFrames = 0
                        last = top
                    }
                }
            }
            withFrameNanos { }
            val target = MorphTarget(current, key?.let { discussionViewModel.viewerAnchor(it) })
            morph = target
            // until then, the layer coincides with the page it covers
            target.awaitPicture()
            pagerVisible = false
            discussionViewModel.galleryCurrentKey = key
            progress.snapTo(1f)
            progress.animateTo(0f, tween(MORPH_DURATION_MS, easing = FastOutSlowInEasing))
            // reveal the tile or cell in the same frame the overlay leaves
            discussionViewModel.galleryCurrentKey = null
            discussionViewModel.galleryRequest = null
        }
    }

    BackHandler { close() }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
            // GalleryScreen hides the system bars while its controls are hidden
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
            discussionViewModel.galleryCurrentKey = null
            galleryViewModel.resetForNewRequest()
        }
    }

    val saveAttachment = rememberSaveAttachmentAction()
    Box(Modifier.fillMaxSize()) {
        if (!pagerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black, alpha = progress.value) }
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (pagerVisible) 1f else 0f }
        ) {
            GalleryScreen(
                viewModel = galleryViewModel,
                initialMessageId = request.messageId,
                initialFyleId = request.fyleId,
                showTextBlocksInitially = request.showTextBlocks,
                onBack = ::close,
                onSave = saveAttachment,
                onOpen = { fyleAndStatus ->
                    App.openFyleViewer(context, fyleAndStatus) {
                        fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened()
                    }
                },
                onFinish = ::close,
                onFinishUp = ::close,
                applyFlagSecure = applyFlagSecure,
            )
        }
        morph?.let { MorphLayer(target = it, progress = progress) }
    }
}

/** The media and where it sits, snapshotted when a morph starts; no bounds when the anchor is off screen. */
private class MorphTarget(val fyleAndStatus: FyleAndStatus?, anchor: AttachmentAnchor?) {
    val visibleBounds: Rect? = anchor?.visibleBounds
    val bounds: Rect? = visibleBounds?.let { anchor?.bounds }

    /** Set by [MorphLayer] once it has something to draw. */
    var pictureReady by mutableStateOf(false)

    /**
     * Waits, bounded, until the layer can draw the picture. A still image starts from the thumbnail in
     * Coil's memory cache, but an animated one (GIF) is never cached there and must first be decoded.
     */
    suspend fun awaitPicture() {
        withTimeoutOrNull(MORPH_PICTURE_TIMEOUT_MS.milliseconds) { snapshotFlow { pictureReady }.first { it } }
    }
}

private const val MORPH_DURATION_MS = 300
private const val MORPH_PICTURE_TIMEOUT_MS = 300
private val ATTACHMENT_CORNER_RADIUS = 6.dp

/**
 * The picture of the morph target drawn inside a rectangle interpolated between its anchor and the
 * full overlay, black behind it, scaled from the tile's crop to the page's fit so it coincides with
 * the tile at one end and with the viewer page at the other. Starts from the thumbnail Coil already
 * decoded for the tile or cell. Everything animated is read in the draw phase.
 */
@Composable
private fun MorphLayer(target: MorphTarget, progress: Animatable<Float, AnimationVector1D>) {
    val context = LocalContext.current
    val media = target.fyleAndStatus
    val painter = media?.deterministicContentUriForGallery?.let { uri ->
        rememberAsyncImagePainter(
            model = remember(uri) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .placeholderMemoryCacheKey(media.decodedThumbnailCacheKey())
                    .build()
            },
            imageLoader = App.imageLoader,
        )
    }
    LaunchedEffect(target, painter) {
        snapshotFlow { painter?.intrinsicSize?.let { it.isSpecified && it.width > 0f && it.height > 0f } ?: true }.first { it }
        target.pictureReady = true
    }
    val cornerRadiusPx = with(LocalDensity.current) { ATTACHMENT_CORNER_RADIUS.toPx() }
    val clip = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val intrinsic = painter?.intrinsicSize
            ?.takeIf { it.isSpecified && it.width > 0f && it.height > 0f }
            ?: run {
                // Coil only starts loading once the painter is drawn at some size (nothing shows until then)
                painter?.let { with(it) { draw(size) } }
                return@Canvas
            }
        val p = progress.value
        val full = Rect(Offset.Zero, size)
        // no anchor on screen: grow from / shrink to a centered rectangle while fading
        val anchorRect = target.bounds
            ?: Rect(full.center - Offset(full.width * 0.2f, full.height * 0.2f), Size(full.width * 0.4f, full.height * 0.4f))
        val rect = lerp(anchorRect, full, p)
        // a tile partly scrolled out keeps its full size and position, only cut to what was visible
        val visible = lerp(target.visibleBounds ?: anchorRect, full, p)
        drawMorphedPicture(
            painter = painter,
            intrinsic = intrinsic,
            rect = rect,
            visible = visible,
            clip = clip,
            cornerRadius = cornerRadiusPx * (1f - p),
            fitFraction = p,
            alpha = if (target.bounds == null) p else 1f,
            background = Color.Black,
        )
    }
}

/**
 * Draws [painter] centered in [rect], scaled from crop ([fitFraction] 0) to fit (1), over an optional
 * [background], clipped to [rect] with rounded corners and to [visible]; [overlay] is drawn inside
 * the same clip. Shared by the viewer morph and the album close flight so both land exactly on the
 * tile's own crop.
 */
private fun DrawScope.drawMorphedPicture(
    painter: Painter,
    intrinsic: Size,
    rect: Rect,
    visible: Rect,
    clip: Path,
    cornerRadius: Float,
    fitFraction: Float = 0f,
    alpha: Float = 1f,
    background: Color? = null,
    overlay: DrawScope.() -> Unit = {},
) {
    clip.rewind()
    clip.addRoundRect(RoundRect(rect, CornerRadius(cornerRadius)))
    clipRect(visible.left, visible.top, visible.right, visible.bottom) {
        clipPath(clip) {
            background?.let { drawRect(it, topLeft = rect.topLeft, size = rect.size, alpha = alpha) }
            val crop = max(rect.width / intrinsic.width, rect.height / intrinsic.height)
            val fit = min(rect.width / intrinsic.width, rect.height / intrinsic.height)
            val scale = crop + (fit - crop) * fitFraction
            val drawSize = Size(intrinsic.width * scale, intrinsic.height * scale)
            val topLeft = rect.center - Offset(drawSize.width / 2f, drawSize.height / 2f)
            translate(topLeft.x, topLeft.y) { with(painter) { draw(drawSize, alpha = alpha) } }
            overlay()
        }
    }
}
