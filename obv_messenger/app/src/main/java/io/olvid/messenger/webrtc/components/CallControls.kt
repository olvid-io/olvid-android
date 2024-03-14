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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput
import io.olvid.messenger.webrtc.components.CallAction.ToggleSpeaker

@Composable
fun CallControls(
    modifier: Modifier = Modifier,
    callMediaState: CallMediaState = CallMediaState(
        isMicrophoneEnabled = true,
        isCameraEnabled = true
    ),
    onToggleSpeaker: (AudioOutput) -> Unit,
    actions: List<CallControlAction> = buildOngoingCallControlActions(callMediaState = callMediaState),
    onCallAction: (CallAction) -> Unit,
    callButtonSize: Float = 56f
) {
    LazyRow(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(actions) { action ->
            if (action.callAction == ToggleSpeaker) {
                SpeakerToggle(
                    audioOutputs = callMediaState.audioOutputs,
                    onToggleSpeaker = onToggleSpeaker
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(action.background)
                            .clickable { it() }
                    ) {
                        Icon(
                            modifier = Modifier.align(Alignment.Center),
                            painter = action.icon,
                            tint = Color.White,
                            contentDescription = null
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(callButtonSize.dp)
                        .clip(CircleShape)
                        .background(action.background)
                        .clickable { onCallAction(action.callAction) }
                ) {
                    Image(
                        modifier = Modifier.align(Alignment.Center),
                        painter = action.icon,
                        contentDescription = null
                    )
                }
            }
        }
    }
}


@Preview
@Composable
private fun CallControlsPreview() {
    AppCompatTheme {
        Column {
            CallControls(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                onToggleSpeaker = {},
                actions = buildPreCallControlActions(CallMediaState()),
                onCallAction = {}
            )
            CallControls(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                callMediaState = CallMediaState(
                    isMicrophoneEnabled = true,
                    isCameraEnabled = true
                ),
                onToggleSpeaker = {},
                onCallAction = {},
                callButtonSize = 40f
            )
            CallControls(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                callMediaState = CallMediaState(
                    isMicrophoneEnabled = false,
                    isCameraEnabled = false,
                    isScreenShareEnabled = true,
                ),
                onToggleSpeaker = {},
                onCallAction = {}
            )
        }
    }
}