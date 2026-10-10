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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.icons.OlvidLogo
import io.olvid.messenger.designsystem.icons.OlvidLogoSize
import io.olvid.messenger.webrtc.WebrtcCallService
import io.olvid.messenger.webrtc.WebrtcCallService.CallParticipantPojo
import io.olvid.messenger.webrtc.components.CallAction.GoToDiscussion

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AudioCallContent(
    participants: List<CallParticipantPojo>,
    webrtcCallService: WebrtcCallService,
    onCallAction: (CallAction) -> Unit,
    isPip: Boolean = false
) {
    if (isPip) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OlvidLogo(size = OlvidLogoSize.MEDIUM)
            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically)
            {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(id = R.drawable.ic_phone_outgoing),
                    contentDescription = stringResource(id = R.string.text_ongoing_call)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = participants.size.toString(),
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    } else {
        val haptics = LocalHapticFeedback.current
        LazyColumn(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(participants) { audioParticipant ->
                var menu by remember {
                    mutableStateOf(false)
                }
                Box {
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false }) {
                        DropdownMenuItem(onClick = {
                            menu = false
                            webrtcCallService.callerKickParticipant(
                                audioParticipant.bytesContactIdentity
                            )
                        }) {
                            Text(text = stringResource(id = R.string.dialog_title_webrtc_kick_participant))
                        }
                    }
                    AudioCallParticipant(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    if (audioParticipant.contact?.oneToOne == true) {
                                        onCallAction(GoToDiscussion(audioParticipant.contact))
                                    }
                                },
                                onLongClick = {
                                    if (webrtcCallService.isCaller && participants.size > 1) {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                        menu = true
                                    }
                                }
                            ),
                        avatar = audioParticipant.avatar(),
                        name = audioParticipant.displayName ?: "",
                        isMute = audioParticipant.peerIsMuted,
                        state = audioParticipant.peerState,
                        audioLevel = webrtcCallService.getAudioLevel(audioParticipant.bytesContactIdentity))
                }
            }
        }
    }
}