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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffoldState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.contacts.ContactListViewModel
import io.olvid.messenger.webrtc.WebrtcCallService
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.PHONE
import io.olvid.messenger.webrtc.WebrtcCallService.CallParticipantPojo
import io.olvid.messenger.webrtc.components.CallAction.AddParticipant
import io.olvid.messenger.webrtc.components.CallAction.GoToDiscussion

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
fun CallBottomSheetContent(
    addingParticipant: Boolean,
    statusBarHeight: Dp,
    bottomSheetScaffoldState: BottomSheetScaffoldState,
    contactListViewModel: ContactListViewModel,
    webrtcCallService: WebrtcCallService?,
    onCallAction: (CallAction) -> Unit,
    microphoneMuted: State<Boolean?>?,
    @Suppress("unused") callState: State<WebrtcCallService.State?>?,
    contact: Contact?,
    callDuration: State<Int?>?,
    callParticipants: State<List<CallParticipantPojo>?>?,
    navigationBarHeight: Dp,
    iAmTheCaller: Boolean,
    callButtonSize: Float
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
    ) {

        if (addingParticipant) {
            Spacer(modifier = Modifier.height(statusBarHeight))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(color = Color(0x80D9D9D9), shape = RoundedCornerShape(50))
        )
        LaunchedEffect(addingParticipant) {
            if (addingParticipant) {
                bottomSheetScaffoldState.bottomSheetState.expand()
            } else {
                bottomSheetScaffoldState.bottomSheetState.collapse()
                contactListViewModel.selectedContacts.clear()
            }
        }
        if (addingParticipant) {
            AddParticipantScreen(contactListViewModel) {
                webrtcCallService?.callerAddCallParticipants(contactListViewModel.selectedContacts)
                onCallAction(AddParticipant(false))
            }
        } else {
            val callMediaState =
                CallMediaState(
                    isMicrophoneEnabled = microphoneMuted?.value?.not() ?: true,
                    isCameraEnabled = webrtcCallService?.cameraEnabled ?: false,
                    isScreenShareEnabled = webrtcCallService?.screenShareActive ?: false,
                    selectedAudioOutput = webrtcCallService?.selectedAudioOutput ?: PHONE,
                    audioOutputs = webrtcCallService?.availableAudioOutputs.orEmpty()
                )
            CallControls(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                actions = buildOngoingCallControlActions(callMediaState = callMediaState),
                onToggleSpeaker = { webrtcCallService?.selectAudioOutput(it) },
                callMediaState = callMediaState,
                onCallAction = onCallAction,
                callButtonSize = callButtonSize
            )

            Spacer(modifier = Modifier.height(navigationBarHeight))

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = contact?.getCustomDisplayName().orEmpty(),
                    style = OlvidTypography.body1,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_phone_outgoing),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            id = R.string.text_ongoing_call,
                            formatDuration(callDuration?.value ?: 0)
                        ),
                        // Body2
                        style = OlvidTypography.body2,
                        color = Color(0xFF8B8D97),
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .height(1.dp)
                    .background(
                        Color(0xFF29282D)
                    )
            )
            LazyColumn {
                if (iAmTheCaller) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCallAction(AddParticipant(true)) }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF29282D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    modifier = Modifier
                                        .size(24.dp),
                                    painter = painterResource(id = R.drawable.ic_add_member),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(id = R.string.webrtc_add_participants),
                                style = OlvidTypography.h3,
                                color = Color.White
                            )
                        }
                    }
                }
                callParticipants?.value?.let { callParticipants ->
                    items(callParticipants) { callParticipant ->
                        var kickParticipant by remember {
                            mutableStateOf(false)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (callParticipant.contact?.oneToOne == true) {
                                            onCallAction(GoToDiscussion(callParticipant.contact))
                                        }
                                    },
                                    onLongClick = {
                                        if (webrtcCallService?.isCaller == true && callParticipants.size > 1) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            kickParticipant = true
                                        }
                                    }
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (webrtcCallService?.isCaller == true) {
                                OlvidDropdownMenu(
                                    expanded = kickParticipant,
                                    onDismissRequest = { kickParticipant = false }) {
                                    OlvidDropdownMenuItem(
                                        onClick = {
                                            kickParticipant = false
                                            webrtcCallService.callerKickParticipant(
                                                callParticipant.bytesContactIdentity
                                            )
                                        },
                                        text = stringResource(id = R.string.dialog_title_webrtc_kick_participant)
                                    )
                                }
                            }
                            Box {
                                Avatar(
                                    modifier = Modifier.requiredSize(56.dp),
                                    target = callParticipant.contact)
                                if (callParticipant.peerIsMuted) {
                                    Icon(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(4.dp)
                                            .background(Color.Black, shape = CircleShape)
                                            .align(Alignment.BottomEnd),
                                        painter = painterResource(id = R.drawable.ic_microphone_off),
                                        tint = Color.White,
                                        contentDescription = "muted"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = callParticipant.displayName ?: "",
                                    style = OlvidTypography.h3,
                                    color = Color.White
                                )
                                val peerStatus = getPeerStateText(
                                    peerState = callParticipant.peerState,
                                    singleContact = callParticipants.size == 1
                                )
                                AnimatedVisibility(visible = peerStatus != null) {
                                    Text(
                                        text = peerStatus ?: "",
                                        style = OlvidTypography.h3,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (callParticipant.contact?.oneToOne == true) {
                                IconButton(onClick = {
                                    onCallAction(
                                        GoToDiscussion(
                                            callParticipant.contact
                                        )
                                    )
                                }) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_chat_circle),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(navigationBarHeight))
    }
}