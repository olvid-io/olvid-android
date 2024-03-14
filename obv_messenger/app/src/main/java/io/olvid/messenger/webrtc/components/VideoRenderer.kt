/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.webrtc.components

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.olvid.engine.Logger
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

/**
 * Renders a single video track based on the call state.
 *
 * @param videoTrack The track containing the video stream for a given participant.
 * @param modifier Modifier for styling.
 */
@Composable
fun VideoRenderer(
    modifier: Modifier = Modifier,
    videoTrack: VideoTrack,
    zoomable: Boolean = false,
    mirror: Boolean = false
) {
    val density = LocalDensity.current
    val trackState: MutableState<VideoTrack?> = remember { mutableStateOf(null) }
    var view: VideoTextureViewRenderer? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            cleanTrack(view, trackState)
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier) {
        val width = with(density) {
            maxWidth.toPx()
        }
        val height = with(density) {
            maxHeight.toPx()
        }

        val maxX = (scale - 1) * width / 2
        val maxY = (scale - 1) * height / 2

        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 3f)
            offset = Offset(
                x = (offset.x + offsetChange.x * scale).coerceIn(-maxX, maxX),
                y = (offset.y + offsetChange.y * scale).coerceIn(-maxY, maxY)
            )
        }
        AndroidView(
            factory = { context ->
                VideoTextureViewRenderer(context).apply {
                    WebrtcPeerConnectionHolder.eglBase?.eglBaseContext?.let {
                        init(
                            it,
                            mirror,
                            object : RendererCommon.RendererEvents {
                                override fun onFirstFrameRendered() = Unit

                                override fun onFrameResolutionChanged(p0: Int, p1: Int, p2: Int) {
                                    // TODO: adjust min scale with this
                                    Logger.d("onFrameResolutionChanged ${p0}x${p1} - $p2")
                                }
                            }
                        )
                    }
                    setupVideo(trackState, videoTrack, this)
                    view = this
                }
            },
            update = { v ->
                setupVideo(trackState, videoTrack, v)
                v.setMirror(mirror)
            },
            modifier = modifier.then(
                if (zoomable) {
                    Modifier
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = state)
                } else Modifier
            )
        )
    }
}

private fun cleanTrack(
    view: VideoTextureViewRenderer?,
    trackState: MutableState<VideoTrack?>
) {
    view?.let {
        try {
            trackState.value?.removeSink(it)
        } catch (ignored: Throwable) { }
    }
    trackState.value = null
}

private fun setupVideo(
    trackState: MutableState<VideoTrack?>,
    track: VideoTrack,
    renderer: VideoTextureViewRenderer
) {
    if (trackState.value == track) {
        return
    }

    cleanTrack(renderer, trackState)

    trackState.value = track
    try {
        track.addSink(renderer)
    } catch (ignored: Throwable) {
    }
}
