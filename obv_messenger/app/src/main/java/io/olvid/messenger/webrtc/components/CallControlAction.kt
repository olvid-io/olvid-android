/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.Contact

sealed class CallAction {
  data class ToggleMicrophone(
    val isEnabled: Boolean
  ) : CallAction()

  data object ToggleCamera : CallAction()

  data object ToggleSpeaker : CallAction()
  data class GoToDiscussion(val contact: Contact? = null) : CallAction()
  data object ShareScreen : CallAction()
  data object FlipCamera : CallAction()
  data class AddParticipant(val open: Boolean) : CallAction()

  data object EndCall : CallAction()
}

data class CallControlAction(
  val icon: Painter,
  val background: Color,
  val callAction: CallAction
)

private fun background(enabled : Boolean) : Color = if (enabled) Color(0xFF29282D) else Color.White
@Composable
fun buildPreCallControlActions(
  callMediaState: CallMediaState
): List<CallControlAction> {
  val microphoneIcon =
    painterResource(
      id = if (callMediaState.isMicrophoneEnabled) {
        R.drawable.ic_microphone_on
      } else {
        R.drawable.ic_microphone_off
      }
    )
  val cameraIcon = painterResource(
    id = if (callMediaState.isCameraEnabled) {
      R.drawable.ic_video_on
    } else {
      R.drawable.ic_video_off
    }
  )

  return listOf(
    CallControlAction(
      icon = microphoneIcon,
      background = background(callMediaState.isMicrophoneEnabled),
      callAction = CallAction.ToggleMicrophone(callMediaState.isMicrophoneEnabled)
    ),
    CallControlAction(
      icon = painterResource(id = callMediaState.selectedAudioOutput.drawableResource()),
      background = background(true),
      callAction = CallAction.ToggleSpeaker
    ),
    CallControlAction(
      icon = painterResource(id = R.drawable.ic_chat),
      background = background(true),
      callAction = CallAction.GoToDiscussion()
    ),
    CallControlAction(
      icon = cameraIcon,
      background = background(callMediaState.isCameraEnabled),
      callAction = CallAction.ToggleCamera
    ),
    CallControlAction(
      icon = painterResource(id = R.drawable.button_end_call),
      background = Color.Transparent,
      callAction = CallAction.EndCall
    )
    )
}


@Composable
fun buildOngoingCallControlActions(
  callMediaState: CallMediaState
): List<CallControlAction> {
  val microphoneIcon =
    painterResource(
      id = if (callMediaState.isMicrophoneEnabled) {
        R.drawable.ic_microphone_on
      } else {
        R.drawable.ic_microphone_off
      }
    )

  val cameraIcon = painterResource(
    id = if (callMediaState.isCameraEnabled) {
      R.drawable.ic_video_on
    } else {
      R.drawable.ic_video_off
    }
  )

  val screenShareIcon = painterResource(
    id = if (callMediaState.isScreenShareEnabled) {
      R.drawable.ic_screen_share_on
    } else {
      R.drawable.ic_screen_share_off
    }
  )

  return listOf(
    CallControlAction(
      icon = painterResource(id = R.drawable.ic_camera_flip),
      background = Color(0xFF29282D),
      callAction = CallAction.FlipCamera
    ),
    CallControlAction(
      icon = cameraIcon,
      background = background(callMediaState.isCameraEnabled),
      callAction = CallAction.ToggleCamera
    ),
    CallControlAction(
      icon = screenShareIcon,
      background = background(callMediaState.isScreenShareEnabled.not()),
      callAction = CallAction.ShareScreen
    ),
    CallControlAction(
      icon = microphoneIcon,
      background = background(callMediaState.isMicrophoneEnabled),
      callAction = CallAction.ToggleMicrophone(callMediaState.isMicrophoneEnabled)
    ),
    CallControlAction(
      icon = painterResource(id = R.drawable.button_end_call),
      background = Color.Transparent, 
      callAction = CallAction.EndCall
    )
  )
}
