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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.avatar.AvatarTarget
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTED

@Composable
fun AudioCallParticipant(
    modifier: Modifier = Modifier,
    avatar: AvatarTarget?,
    name: String,
    isMute: Boolean,
    state: PeerState,
    audioLevel: Double?
) {
    val speakingColor = colorResource(id = R.color.olvid_gradient_light)
    val notSpeakingColor = Color(0xFF29282D)
    val borderColor by animateColorAsState(
        if ((audioLevel ?: 0.0) > 0.1) speakingColor else notSpeakingColor,
        label = "borderColor",
        animationSpec = tween(durationMillis = 1000, easing = EaseOutExpo)
    )
    Row(
        modifier = modifier
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(size = 20.dp)
            )
            .background(color = Color(0xCC000000), shape = RoundedCornerShape(size = 20.dp))
            .clip(RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            modifier = Modifier.requiredSize(56.dp),
            target = avatar
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f, true)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = OlvidTypography.body1,
                color = Color.White
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = state.humanReadable(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = OlvidTypography.body2,
                color = Color.LightGray
            )
        }
        if (isMute) {
            Icon(
                modifier = Modifier
                    .size(32.dp)
                    .background(colorResource(id = R.color.red), CircleShape)
                    .padding(4.dp),
                painter = painterResource(id = R.drawable.ic_microphone_off),
                tint = Color.White,
                contentDescription = "muted"
            )
        } else {
            AudioLevel(
                modifier = Modifier.size(32.dp),
                audioLevel = audioLevel
            )
        }
    }
}

@Preview
@Composable
fun AudioCallParticipantPreview() {
    AudioCallParticipant(
        avatar = AvatarSource.initial(byteArrayOf(0, 12, 24), "A"),
        name = "Alic B.",
        isMute = false,
        state = CONNECTED,
        audioLevel = 0.7
    )
}
