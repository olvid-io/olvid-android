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
package io.olvid.messenger.gallery

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updateLayoutParams
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.tasks.StartAttachmentDownloadTask
import io.olvid.messenger.databases.tasks.StopAttachmentDownloadTask
import io.olvid.messenger.discussion.message.attachments.AttachmentDownloadProgress
import io.olvid.messenger.discussion.message.attachments.rememberAttachmentProgress
import kotlin.math.min
import androidx.media3.ui.R as Media3UiR

private const val DOUBLE_TAP_ZOOM_SCALE = 3f

/**
 * Clamp the pan offset so the video content never reveals empty space beyond its FIT bounds.
 * [contentSizePx] is the size of the video content as displayed at FIT scale (letterboxing
 * excluded); on the letterboxed axis the content stays centered until it overflows the view.
 * At scale <= 1 the offset is forced to 0.
 */
private fun clampOffset(offset: Float, scale: Float, viewSizePx: Int, contentSizePx: Float): Float {
    if (scale <= 1f || viewSizePx == 0) return 0f
    val maxOffset = ((contentSizePx * scale - viewSizePx) / 2f).coerceAtLeast(0f)
    return offset.coerceIn(-maxOffset, maxOffset)
}

/**
 * Width/height of the video content as displayed at FIT scale inside [viewSize].
 * Falls back to the full view when the video size is not known yet.
 */
private fun fitContentWidth(viewSize: IntSize, videoAspectRatio: Float): Float =
    if (videoAspectRatio <= 0f || viewSize.height == 0) viewSize.width.toFloat()
    else min(viewSize.width.toFloat(), viewSize.height * videoAspectRatio)

private fun fitContentHeight(viewSize: IntSize, videoAspectRatio: Float): Float =
    if (videoAspectRatio <= 0f || viewSize.width == 0) viewSize.height.toFloat()
    else min(viewSize.height.toFloat(), viewSize.width / videoAspectRatio)

@OptIn(UnstableApi::class)
@Composable
fun GalleryVideoPlayer(
    modifier: Modifier = Modifier,
    mediaPlayer: ExoPlayer?,
    fyleAndStatus: FyleAndStatus,
    isCurrentPage: Boolean,
    initialScrollDone: Boolean,
    onSingleTap: () -> Unit = {},
    onZoomedChanged: (Boolean) -> Unit = {},
    overlayBottomPadding: Dp = 0.dp,
) {
    val join = fyleAndStatus.fyleMessageJoinWithStatus
    val isFailed = join.status == FyleMessageJoinWithStatus.STATUS_FAILED
            || join.status == FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED

    // Live download progress (only observed while the attachment is not yet complete)
    val attachmentProgress = if (!fyleAndStatus.fyle.isComplete) {
        rememberAttachmentProgress(join).value
    } else {
        null
    }

    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnZoomedChanged by rememberUpdatedState(onZoomedChanged)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isFailed) {
            if (initialScrollDone) {
                Text(
                    text = stringResource(R.string.label_attachment_download_failed),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            var playerView by remember { mutableStateOf<PlayerView?>(null) }
            var viewSize by remember { mutableStateOf(IntSize.Zero) }

            // Aspect ratio of the playing video, used to clamp panning to the actual content
            // (excluding letterbox bars). 0 until the player reports it.
            var videoAspectRatio by remember { mutableFloatStateOf(0f) }

            // Zoom/pan transform applied to the PlayerView. scale == 1 is FIT mode.
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            // Animate towards a target (scale, offsetX, offsetY) transform
            var targetTransform by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }

            // Report zoom state to the parent so the pager + vertical-fling are disabled while zoomed
            LaunchedEffect(Unit) {
                snapshotFlow { scale > 1f }.collect { currentOnZoomedChanged(it) }
            }

            // Reset zoom whenever the displayed media changes or we leave this page
            LaunchedEffect(isCurrentPage, fyleAndStatus) {
                targetTransform = null
                scale = 1f
                offsetX = 0f
                offsetY = 0f
                videoAspectRatio = 0f
            }

            LaunchedEffect(targetTransform) {
                targetTransform?.let { target ->
                    animateZoomTransform(scale, offsetX, offsetY, target) { s, x, y ->
                        scale = s
                        offsetX = x
                        offsetY = y
                    }
                    targetTransform = null
                }
            }

            DisposableEffect(mediaPlayer, isCurrentPage) {
                if (mediaPlayer == null || !isCurrentPage) {
                    return@DisposableEffect onDispose {}
                }
                val listener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        videoAspectRatio = if (videoSize.height == 0) 0f
                        else videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    }
                }
                mediaPlayer.addListener(listener)
                listener.onVideoSizeChanged(mediaPlayer.videoSize)
                onDispose { mediaPlayer.removeListener(listener) }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { pv ->
                        playerView = pv
                        // External controls are rendered in Compose (bottom overlay); drop the built-in ones.
                        pv.useController = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewSize = it }
                    // Gestures must be detected BEFORE the graphicsLayer so pointer coordinates
                    // stay in stable screen space instead of being fed back through the very
                    // transform they drive (which damped panning and zooming).
                    .pointerInput(Unit) {
                        var dragStartOffsetX = 0f
                        var dragStartOffsetY = 0f
                        var zoomInitialScale = 1f
                        var zoomInitialOffsetX = 0f
                        var zoomInitialOffsetY = 0f
                        var zoomFocusX = 0f
                        var zoomFocusY = 0f

                        fun clampX(offset: Float, atScale: Float) = clampOffset(
                            offset, atScale, viewSize.width,
                            fitContentWidth(viewSize, videoAspectRatio)
                        )

                        fun clampY(offset: Float, atScale: Float) = clampOffset(
                            offset, atScale, viewSize.height,
                            fitContentHeight(viewSize, videoAspectRatio)
                        )

                        detectZoomPanGestures(
                            isDraggable = { scale > 1f },
                            onPanStart = {
                                dragStartOffsetX = offsetX
                                dragStartOffsetY = offsetY
                            },
                            onPan = { totalDeltaX, totalDeltaY ->
                                offsetX = clampX(dragStartOffsetX + totalDeltaX, scale)
                                offsetY = clampY(dragStartOffsetY + totalDeltaY, scale)
                            },
                            onFlingBy = { deltaX, deltaY ->
                                offsetX = clampX(offsetX + deltaX, scale)
                                offsetY = clampY(offsetY + deltaY, scale)
                            },
                            onZoomStart = { focusX, focusY ->
                                zoomInitialScale = scale
                                zoomInitialOffsetX = offsetX
                                zoomInitialOffsetY = offsetY
                                zoomFocusX = focusX - viewSize.width / 2f
                                zoomFocusY = focusY - viewSize.height / 2f
                            },
                            onZoom = { scaleRatio ->
                                // No zoom-out below FIT (scale floor = 1)
                                val newScale = (zoomInitialScale * scaleRatio).coerceAtLeast(1f)
                                // Keep the pinch focus point stationary on screen
                                val ratio = newScale / zoomInitialScale
                                offsetX = clampX(
                                    zoomFocusX + (zoomInitialOffsetX - zoomFocusX) * ratio,
                                    newScale
                                )
                                offsetY = clampY(
                                    zoomFocusY + (zoomInitialOffsetY - zoomFocusY) * ratio,
                                    newScale
                                )
                                scale = newScale
                            },
                            onDoubleTap = { x, y ->
                                targetTransform = if (scale > 1f) {
                                    // Double tap while zoomed: restore default zoom (FIT)
                                    Triple(1f, 0f, 0f)
                                } else {
                                    // Double tap while fitted: zoom in, centered on the tapped point
                                    Triple(
                                        DOUBLE_TAP_ZOOM_SCALE,
                                        clampX(
                                            -(x - viewSize.width / 2f) * DOUBLE_TAP_ZOOM_SCALE,
                                            DOUBLE_TAP_ZOOM_SCALE
                                        ),
                                        clampY(
                                            -(y - viewSize.height / 2f) * DOUBLE_TAP_ZOOM_SCALE,
                                            DOUBLE_TAP_ZOOM_SCALE
                                        ),
                                    )
                                }
                            },
                            onSingleTap = { currentOnSingleTap() },
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )

            LaunchedEffect(isCurrentPage, fyleAndStatus, join.status, playerView) {
                val pv = playerView ?: return@LaunchedEffect
                if (isCurrentPage && mediaPlayer != null) {
                    mediaPlayer.stop()
                    mediaPlayer.clearMediaItems()
                    pv.player = mediaPlayer
                    var filePath = App.absolutePathFromRelative(fyleAndStatus.fyle.filePath)
                    if (filePath == null) {
                        filePath = join.absoluteFilePath
                    }
                    val mediaItem = MediaItem.Builder()
                        .setUri(filePath)
                        .build()
                    mediaPlayer.setMediaItem(mediaItem)
                    mediaPlayer.playWhenReady = true
                    mediaPlayer.prepare()
                } else {
                    pv.player = null
                }
            }
        }

        // Download overlay for images that are not yet downloaded. Composed after the
        // gesture surface so it is topmost in hit-testing and taps reliably trigger /
        // cancel the download instead of being consumed by the gesture surface.
        when (join.status) {
            FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE -> {
                Image(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = overlayBottomPadding)
                        .padding(8.dp)
                        .size(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                        ) {
                            App.runThread(StartAttachmentDownloadTask(join))
                        },
                    painter = painterResource(id = R.drawable.ic_file_download),
                    contentDescription = stringResource(R.string.label_download)
                )
            }

            FyleMessageJoinWithStatus.STATUS_DOWNLOADING -> {
                AttachmentDownloadProgress(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = overlayBottomPadding)
                        .padding(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                        ) {
                            App.runThread(StopAttachmentDownloadTask(join))
                        },
                    speed = attachmentProgress?.speed,
                    eta = attachmentProgress?.eta,
                    progress = attachmentProgress?.progress ?: 0f,
                    large = true
                )
            }
        }
    }
}

/**
 * Media3's built-in player controller (play/pause, seek back/forward, previous/next, settings
 * with playback speed and audio track selection, time bar with buffered progress), hosted as a
 * standalone view so it lives outside the zoomed PlayerView layer and never conflicts with the
 * pager / dismiss gestures. Visibility is driven by the Compose overlay, so the internal
 * auto-hide is disabled.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoControls(player: ExoPlayer) {
    Box(
        modifier = Modifier
            .height(128.dp)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerControlView(ctx).apply {
                    showTimeoutMs = 0
                    // The controls float directly over the video, right above the gallery bottom
                    // bar: drop the built-in scrim and bottom strip backgrounds
                    findViewById<View>(Media3UiR.id.exo_controls_background)?.background = null
                    findViewById<View>(Media3UiR.id.exo_bottom_bar)?.background = null
                    // Lower the time bar (default 52dp bottom margin) to open a gap between it
                    // and the transport buttons, which otherwise touch it
                    findViewById<View>(Media3UiR.id.exo_progress)?.updateLayoutParams<MarginLayoutParams> {
                        bottomMargin = (36 * ctx.resources.displayMetrics.density).toInt()
                    }
                }
            },
            update = { controlView ->
                controlView.player = player
                controlView.show()
            },
            onRelease = { it.player = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 64.dp)
                // Tall enough to stay above PlayerControlView's minimal-mode threshold
                // (center controls + 2x bottom bar), so all buttons remain visible
                .requiredHeight(200.dp)
        )
    }
}
