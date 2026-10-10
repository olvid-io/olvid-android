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

package io.olvid.messenger.webrtc.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.webrtc.WebrtcCallService.CallParticipantPojo
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.VideoTrack

@Composable
fun CallParticipant(
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier.fillMaxSize(),
    callParticipant: CallParticipantPojo? = null,
    bytesOwnedIdentity: ByteArray? = null,
    mirror: Boolean = false,
    videoTrack: VideoTrack?,
    screenTrack: VideoTrack? = null,
    zoomable: Boolean = false,
    peekHeight: Dp = 0.dp,
    audioLevel: Double?,
    isPip: Boolean = false,
    pipAspectCallback: ((Context, Int, Int) -> Unit)? = null,
    fitVideo: Boolean = false
) {

    BoxWithConstraints(modifier = modifier) {
        val largeLayout = maxWidth > 200.dp
        val hasVideo = callParticipant?.peerVideoSharing != false && videoTrack.isEnabledSafe()
        val hasScreenShare =
            callParticipant?.peerScreenSharing != false && screenTrack.isEnabledSafe()

        if (hasVideo || hasScreenShare) {
            if (hasScreenShare) {
                VideoRenderer(
                    modifier = Modifier.fillMaxSize(),
                    videoTrack = screenTrack!!,
                    zoomable = zoomable,
                    mirror = false,
                    pipAspectCallback = pipAspectCallback,
                    fitVideo = true
                )
                if (hasVideo) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Start))
                            .then(
                                if (isPip)
                                    Modifier
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .sizeIn(maxWidth = 60.dp, maxHeight = 60.dp)
                                else
                                    Modifier
                                        .offset(x = 10.dp, y = -(peekHeight + 10.dp))
                                        .sizeIn(maxWidth = 120.dp, maxHeight = 120.dp)
                            ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        VideoRenderer(
                            videoTrack = videoTrack!!,
                            mirror = mirror,
                            matchVideoAspectRatio = true
                        )
                    }
                }
            } else {
                VideoRenderer(
                    videoTrack = videoTrack!!,
                    zoomable = zoomable,
                    mirror = mirror,
                    matchVideoAspectRatio = !largeLayout,
                    pipAspectCallback = pipAspectCallback,
                    fitVideo = fitVideo
                )
            }
            callParticipant?.peerState.takeUnless { it == CONNECTED }?.let {
                Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = if (isPip) 0.dp else peekHeight)
                        .background(
                            colorResource(id = R.color.blackOverlay),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    text = getPeerStateText(peerState = it, singleContact = false) ?: "",
                    textAlign = TextAlign.Center,
                    style = OlvidTypography.body2.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
            }
        } else {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                pipAspectCallback?.invoke(context, 9, 16)
            }
            val radius by animateFloatAsState(
                targetValue = audioLevel?.toFloat() ?: 0f,
                animationSpec = tween(durationMillis = 600),
                label = "waveRadius"
            )
            val alpha by animateFloatAsState(
                targetValue = audioLevel?.toFloat() ?: 0f,
                animationSpec = tween(durationMillis = 600),
                label = "waveAlpha"
            )
            val ownedIdentity by produceState<OwnedIdentity?>(
                initialValue = null,
                bytesOwnedIdentity
            ) {
                value = bytesOwnedIdentity?.let { bytes ->
                    withContext(Dispatchers.IO) {
                        AppDatabase.getInstance().ownedIdentityDao().get(bytes)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isPip) 0.dp else peekHeight)
            ) {
                Avatar(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .sizeIn(maxWidth = 200.dp)
                        .fillMaxSize(.5f)
                        .drawBehind {
                            if (radius > .1f) {
                                drawCircle(
                                    color = Color.White,
                                    radius = size.minDimension / 2 * (1 + radius),
                                    alpha = alpha,
                                    style = Fill
                                )
                            }
                        },
                    target = if (bytesOwnedIdentity != null) {
                        ownedIdentity
                    } else {
                        callParticipant?.avatar()
                    }
                )
                callParticipant?.peerState.takeUnless { it == CONNECTED }?.let {
                    Text(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 130.dp),
                        text = getPeerStateText(peerState = it, singleContact = false) ?: "",
                        textAlign = TextAlign.Center,
                        style = OlvidTypography.body2.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
                }
            }
        }
        if (callParticipant?.peerIsMuted == true) {
            Icon(
                modifier = Modifier
                    .padding(end = 6.dp, bottom = if (!isPip) (peekHeight + 6.dp) else 6.dp)
                    .size(if (largeLayout) 32.dp else 16.dp)
                    .align(Alignment.BottomEnd)
                    .background(colorResource(id = R.color.red), CircleShape)
                    .padding(if (largeLayout) 4.dp else 2.dp),
                painter = painterResource(id = R.drawable.ic_microphone_off),
                tint = Color.White,
                contentDescription = "muted"
            )
        } else if (largeLayout) {
            AudioLevel(
                modifier = Modifier
                    .padding(end = 6.dp, bottom = peekHeight)
                    .padding(bottom = 6.dp)
                    .size(32.dp)
                    .align(Alignment.BottomEnd),
                audioLevel
            )
        }
        if (largeLayout) {
            (callParticipant?.displayName
                ?: ContactCacheSingleton.getContactCustomDisplayName(
                    bytesOwnedIdentity ?: byteArrayOf()
                ))?.let { name ->
                Text(
                    modifier = Modifier
                        .then(
                            if (hasVideo && hasScreenShare)
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.End
                                        )
                                    )
                            else
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Start
                                        )
                                    )
                        )
                        .padding(
                            start = 6.dp,
                            bottom = peekHeight + 6.dp,
                            end = 6.dp
                        )
                        .background(
                            colorResource(id = R.color.blackOverlay),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    style = OlvidTypography.body2.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    text = name,
                    color = Color.White
                )
            }
        }
    }
}
