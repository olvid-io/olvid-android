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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.avatar.AvatarTarget
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.designsystem.avatar.Avatar
import org.webrtc.VideoTrack

@Composable
fun BoxScope.PreCallScreen(
    name: String = "",
    personalNote: String = "",
    status: String = "",
    cameraEnabled: Boolean = false,
    videoTrack: VideoTrack?,
    mirror: Boolean = true,
    avatar: AvatarTarget? = null,
) {
    videoTrack?.let {
        if (cameraEnabled) {
            VideoRenderer(
                modifier = Modifier.fillMaxSize(),
                videoTrack = it,
                zoomable = true,
                mirror = mirror
            )
        }
    }

    if (cameraEnabled.not()) {
        EncryptedCallNotice(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            color = Color(0xFF8B8D97)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Avatar(
                modifier = Modifier
                    .size(100.dp), target = avatar
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = name,
                textAlign = TextAlign.Center,
                style = OlvidTypography.h3,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                textAlign = TextAlign.Center,
                style = OlvidTypography.body2,
                color = Color(0xFF8B8D97),
            )
            Box {
                Spacer(Modifier.height(100.dp))
                ExpandablePersonalNote(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .align(Alignment.TopCenter),
                    personalNote = personalNote
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EncryptedCallNotice(
                modifier = Modifier
                    .padding(top = 48.dp),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))
            Avatar(
                modifier = Modifier
                    .size(100.dp), target = avatar
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = name,
                style = OlvidTypography.h3,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                modifier = Modifier
                    .background(
                        colorResource(id = R.color.blackOverlay),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                text = status,
                textAlign = TextAlign.Center,
                style = OlvidTypography.body2,
                color = Color.White
            )
            ExpandablePersonalNote(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .background(
                        colorResource(id = R.color.blackOverlay),
                        RoundedCornerShape(12.dp)
                    )
                    .align(Alignment.CenterHorizontally),
                personalNote = personalNote
            )
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
fun PreCallScreenScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color.Black)
    ) {
        PreCallScreen(
            "Alice Border",
            LoremIpsum(120).values.joinToString(" "),
            "Connexion...",
            false,
            null,
            avatar = AvatarSource.initial(byteArrayOf(0, 1, 35), "A"),
        )
    }
}