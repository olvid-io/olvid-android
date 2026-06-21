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

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun GalleryVideoPlayer(
    modifier: Modifier = Modifier,
    mediaPlayer: ExoPlayer?,
    fyleAndStatus: FyleAndStatus,
    isCurrentPage: Boolean,
    initialScrollDone: Boolean,
    onFlingDown: () -> Unit = {},
    onFlingUp: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
) {
    val isFailed = fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED
            || fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED

    // Keep the latest onDoubleTap reachable from the GestureDetector created in factory
    val currentOnDoubleTap = rememberUpdatedState(onDoubleTap)
    val currentOnFlingUp = rememberUpdatedState(onFlingUp)
    val currentOnFlingDown = rememberUpdatedState(onFlingDown)

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

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { pv ->
                        playerView = pv
                        pv.controllerAutoShow = false
                        // Attach a GestureDetector directly on the PlayerView so double-tap
                        // is detected without blocking single taps from reaching the player controls.
                        val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDoubleTap(e: MotionEvent): Boolean {
                                currentOnDoubleTap.value()
                                pv.hideController()
                                return true
                            }

                            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                // check this is a vertical fling
                                if (abs(velocityY) > abs(2*velocityX)) {
                                    if (velocityY > 0) {
                                        currentOnFlingDown.value()
                                    } else {
                                        currentOnFlingUp.value()
                                    }
                                    return true;
                                }
                                return false;
                            }
                        })

                        @SuppressLint("ClickableViewAccessibility")
                        pv.setOnTouchListener { _, event ->
                            if (detector.onTouchEvent(event)) {
                                return@setOnTouchListener true
                            }
                            false // let PlayerView handle the event too
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(isCurrentPage, fyleAndStatus, playerView) {
                val pv = playerView ?: return@LaunchedEffect
                if (isCurrentPage && mediaPlayer != null) {
                    mediaPlayer.stop()
                    mediaPlayer.clearMediaItems()
                    pv.player = mediaPlayer
                    var filePath = App.absolutePathFromRelative(fyleAndStatus.fyle.filePath)
                    if (filePath == null) {
                        filePath = fyleAndStatus.fyleMessageJoinWithStatus.absoluteFilePath
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
    }
}
