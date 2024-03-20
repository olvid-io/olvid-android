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

import android.content.Context
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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
    mirror: Boolean = false,
    matchVideoAspectRatio: Boolean = false,
    pipAspectCallback: ((Context, Int, Int) -> Unit)? = null,
    fitVideo: Boolean = false
) {
    val trackState: MutableState<VideoTrack?> = remember { mutableStateOf(null) }
    var view: VideoTextureViewRenderer? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            cleanTrack(view, trackState)
        }
    }

    val scaleAndOffsetControl = remember { ScaleAndOffsetControl() }
    var videoAspectRatio by remember { mutableFloatStateOf(1f) }

    Box(modifier = modifier) {
        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
            scaleAndOffsetControl.applyTransformation(zoomChange = zoomChange, offsetChange = offsetChange)
        }

        AndroidView(
            factory = { context ->
                VideoTextureViewRenderer(context, scaleAndOffsetControl).apply {
                    WebrtcPeerConnectionHolder.eglBase?.eglBaseContext?.let {
                        init(
                            it,
                            mirror,
                            object : RendererCommon.RendererEvents {
                                override fun onFirstFrameRendered() = Unit

                                override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                                    videoAspectRatio = width / height.toFloat()
                                    pipAspectCallback?.invoke(context, width, height)
                                }
                            }
                        )
                    }
                    setupVideo(trackState, videoTrack, this)
                    if (fitVideo) {
                        scaleAndOffsetControl.setFit()
                    } else {
                        scaleAndOffsetControl.setFill()
                    }
                    view = this
                }
            },
            update = { v ->
                setupVideo(trackState, videoTrack, v)
                v.setMirror(mirror)
            },
            modifier = modifier
                .then(
                    if (matchVideoAspectRatio) {
                        Modifier.aspectRatio(videoAspectRatio)
                    } else Modifier
                ).then(
                    if (zoomable) {
                        Modifier.transformable(state = state)
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
