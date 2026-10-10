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

package io.olvid.messenger.webrtc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.avatar.AvatarTarget
import io.olvid.messenger.designsystem.constantSp
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.webrtc.components.EncryptedCallNotice
import io.olvid.messenger.webrtc.components.ExpandablePersonalNote


@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@Composable
fun IncomingCallScreen(
    name: String = "",
    avatar: AvatarTarget? = null,
    callRejected: Boolean,
    personalNote: String = "",
    participantCount: Int,
    color: Int? = null,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        val variablePadding = ((maxHeight - 300.dp) / 4f).coerceIn(0.dp, 48.dp)
        val borderColor = color?.let { Color(-0x1000000 + color) }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(variablePadding))
            EncryptedCallNotice(color = Color(0xFF8B8D97))

            Spacer(modifier = Modifier.height((variablePadding * 2).coerceAtLeast(16.dp)))
            Text(
                text = if (callRejected)
                    stringResource(R.string.label_hanged_up)
                else
                    stringResource(R.string.text_incoming_call_from),
                style = OlvidTypography.h3,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .then(
                            if (borderColor != null) {
                                Modifier.background(borderColor, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(4.dp)

                ) {
                    Avatar(
                        modifier = Modifier.fillMaxSize(),
                        target = avatar
                    )
                    if (participantCount > 1) {
                        Text(
                            modifier = Modifier.offset(x = 112.dp, y = 20.dp),
                            text = stringResource(R.string.plus_count, participantCount - 1),
                            style = OlvidTypography.h3.copy(fontSize = constantSp(48)),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = name,
                style = OlvidTypography.h3,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            if (participantCount > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.text_and_x_other,
                        participantCount - 1,
                        participantCount - 1
                    ),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint)
                )
            }

            if (personalNote.isNotEmpty()) {
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

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = variablePadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                onClick = onReject,
                enabled = callRejected.not(),
            ) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    alpha = if (callRejected) .5f else 1f,
                    painter = painterResource(id = R.drawable.button_end_call),
                    contentDescription = stringResource(R.string.content_description_reject_call_button)
                )
            }

            Spacer(Modifier.weight(2f))

            IconButton(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                onClick = onAccept,
                enabled = callRejected.not(),
            ) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    alpha = if (callRejected) .5f else 1f,
                    painter = painterResource(id = R.drawable.button_answer_call),
                    contentDescription = stringResource(R.string.content_description_accept_call_button)
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}


@Preview
@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun IncomingCallPreview() {
    IncomingCallScreen(
        name = "John Doe",
        avatar = AvatarSource.initial(byteArrayOf(0), "A"),
        callRejected = true,
        color = Color.Red.toArgb(),
        personalNote = "This is a personal note with a long text to see how it goes",
        participantCount = 3,
        onAccept = {},
        onReject = {}
    )
}