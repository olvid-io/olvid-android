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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.designsystem.avatar.AvatarTarget
import io.olvid.messenger.webrtc.WebrtcCallService
import io.olvid.messenger.webrtc.WebrtcCallService.CallParticipantPojo
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.CALL_INITIATION_NOT_SUPPORTED
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.CONTACT_NOT_FOUND
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.COULD_NOT_SEND
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.ICE_CONNECTION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.ICE_SERVER_CREDENTIALS_CREATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.INTERNAL_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.KICKED
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.NONE
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.PEER_CONNECTION_CREATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.PERMISSION_DENIED
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.SERVER_AUTHENTICATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.SERVER_UNREACHABLE
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CALL_REJECTED
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTED
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTING_TO_PEER
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.HANGED_UP
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.RECONNECTING
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.START_CALL_MESSAGE_SENT
import io.olvid.messenger.webrtc.WebrtcCallService.State.BUSY
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_ENDED
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_IN_PROGRESS
import io.olvid.messenger.webrtc.WebrtcCallService.State.CONNECTING
import io.olvid.messenger.webrtc.WebrtcCallService.State.FAILED
import io.olvid.messenger.webrtc.WebrtcCallService.State.GETTING_TURN_CREDENTIALS
import io.olvid.messenger.webrtc.WebrtcCallService.State.INITIAL
import io.olvid.messenger.webrtc.WebrtcCallService.State.INITIALIZING_CALL
import io.olvid.messenger.webrtc.WebrtcCallService.State.RINGING
import io.olvid.messenger.webrtc.WebrtcCallService.State.WAITING_FOR_AUDIO_PERMISSION
import org.webrtc.VideoTrack
import java.util.Locale

fun WebrtcCallService.State.humanReadable(failReason: FailReason): String {
    val context = App.getContext()
    return when (this) {
        INITIAL -> context.getString(R.string.webrtc_status_initial)
        INITIALIZING_CALL -> context.getString(R.string.webrtc_status_initializing_call)
        WAITING_FOR_AUDIO_PERMISSION -> context.getString(R.string.webrtc_status_waiting_for_permission)
        GETTING_TURN_CREDENTIALS -> context.getString(R.string.webrtc_status_verifying_credentials)
        RINGING -> context.getString(R.string.webrtc_status_ringing)
        CONNECTING -> context.getString(R.string.webrtc_status_connecting_to_peer)
        BUSY -> context.getString(R.string.webrtc_status_contact_busy)
        CALL_IN_PROGRESS -> context.getString(R.string.webrtc_status_call_in_progress)
        CALL_ENDED -> context.getString(R.string.webrtc_status_ending_call)
        FAILED ->
            when (failReason) {
                NONE, CONTACT_NOT_FOUND, INTERNAL_ERROR, ICE_SERVER_CREDENTIALS_CREATION_ERROR, COULD_NOT_SEND ->
                    context.getString(R.string.webrtc_failed_internal)

                SERVER_UNREACHABLE, PEER_CONNECTION_CREATION_ERROR, SERVER_AUTHENTICATION_ERROR ->
                    context.getString(R.string.webrtc_failed_network_error)

                PERMISSION_DENIED, CALL_INITIATION_NOT_SUPPORTED -> context.getString(R.string.webrtc_failed_no_call_permission)
                ICE_CONNECTION_ERROR -> context.getString(R.string.webrtc_failed_connection_to_contact_lost)
                KICKED -> context.getString(R.string.webrtc_failed_kicked)
                FailReason.INITIALIZATION_TIMEOUT -> context.getString(R.string.webrtc_status_start_call_timeout)
            }
    }
}

@Composable
fun PeerState.humanReadable(): String {
    return when (this) {
        PeerState.INITIAL -> stringResource(id = R.string.webrtc_status_initial)
        START_CALL_MESSAGE_SENT -> stringResource(id = R.string.webrtc_status_initializing_call)
        PeerState.START_CALL_TIME_OUT -> stringResource(id = R.string.webrtc_status_start_call_timeout)
        CONNECTING_TO_PEER -> stringResource(id = R.string.webrtc_status_connecting_to_peer)
        PeerState.RINGING -> stringResource(id = R.string.webrtc_status_ringing)
        PeerState.BUSY -> stringResource(id = R.string.webrtc_status_contact_busy)
        CALL_REJECTED -> stringResource(id = R.string.webrtc_status_call_rejected)
        CONNECTED -> stringResource(id = R.string.webrtc_status_call_in_progress)
        RECONNECTING -> stringResource(id = R.string.webrtc_status_reconnecting)
        HANGED_UP -> stringResource(id = R.string.webrtc_status_contact_hanged_up)
        PeerState.KICKED -> stringResource(id = R.string.webrtc_status_contact_kicked)
        PeerState.FAILED -> stringResource(id = R.string.webrtc_status_contact_failed)
        PeerState.ENDING_CALL -> stringResource(R.string.webrtc_status_ending_call)
    }
}

@Composable
fun getPeerStateText(
    peerState: PeerState,
    singleContact: Boolean
): String? =
    when (peerState) {
        PeerState.BUSY -> if (singleContact) {
            null
        } else {
            stringResource(id = R.string.webrtc_status_contact_busy)
        }

        CALL_REJECTED -> stringResource(id = R.string.webrtc_status_call_rejected)
        CONNECTING_TO_PEER -> if (singleContact) {
            null
        } else {
            stringResource(id = R.string.webrtc_status_connecting_to_peer)
        }

        CONNECTED -> stringResource(id = R.string.webrtc_status_call_in_progress)
        RECONNECTING -> stringResource(id = R.string.webrtc_status_reconnecting)
        PeerState.RINGING -> if (singleContact) {
            null
        } else {
            stringResource(id = R.string.webrtc_status_ringing)
        }

        HANGED_UP -> stringResource(id = R.string.webrtc_status_contact_hanged_up)
        PeerState.KICKED -> stringResource(id = R.string.webrtc_status_contact_kicked)
        PeerState.ENDING_CALL -> stringResource(R.string.webrtc_status_ending_call)
        PeerState.FAILED -> stringResource(id = R.string.webrtc_status_contact_failed)
        PeerState.INITIAL -> if (singleContact) null else stringResource(id = R.string.webrtc_status_initial)
        START_CALL_MESSAGE_SENT -> if (singleContact) null else stringResource(id = R.string.webrtc_status_initializing_call)
        PeerState.START_CALL_TIME_OUT -> if (singleContact) null else stringResource(id = R.string.webrtc_status_start_call_timeout)
    }


@Composable
fun formatDuration(duration: Int): String {
    val hours = duration / 3600
    return if (hours == 0) String.format(
        Locale.ENGLISH,
        "%02d:%02d",
        duration / 60,
        duration % 60
    ) else String.format(
        Locale.ENGLISH,
        "%d:%02d:%02d",
        hours,
        (duration / 60) % 60,
        duration % 60
    )
}


fun VideoTrack?.isEnabledSafe() = try {
    this?.enabled() == true
} catch (_: Exception) {
    false
}

fun CallParticipantPojo.avatar(): AvatarTarget =
    contact ?: AvatarSource.initial(bytesContactIdentity, StringUtils.getInitial(displayName))
