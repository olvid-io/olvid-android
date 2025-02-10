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

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.webrtc.CallData

@Composable
fun CallNotification(
    modifier: Modifier = Modifier,
    callData: CallData
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 360.dp)
            .clickable {
                callData.fullScreenIntent?.let {
                    context.startActivity(it)
                }
            }
            .shadow(elevation = 12.dp)
            .background(
                color = colorResource(id = R.color.almostWhite),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp)),
        verticalAlignment = CenterVertically
    ) {
        InitialView(
            modifier = Modifier.size(48.dp),
            initialViewSetup = callData.initialViewSetup,
            onCall = true
        ) {
            callData.fullScreenIntent?.let {
                context.startActivity(it)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f, true), verticalArrangement = Arrangement.Center) {
            Text(
                text = callData.title,
                color = colorResource(id = R.color.primary700),
                style = OlvidTypography.h3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            callData.subtitle?.let {
                Text(
                    text = it,
                    color = colorResource(id = R.color.greyTint),
                    style = OlvidTypography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        callData.rejectCall?.let {
            IconButton(
                onClick = it,
                colors = IconButtonDefaults.iconButtonColors(colorResource(R.color.red))
            ) {
                if (callData.isDoubleCall) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.notification_action_reject),
                        tint = Color.White
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_end_call),
                        contentDescription = stringResource(R.string.notification_action_reject),
                        tint = Color.White
                    )
                }
            }
        }
        callData.acceptCall?.let { onClick ->
            Spacer(Modifier.width(12.dp))
            if (callData.isDoubleCall) {
                EndAndAcceptCall(
                    backgroundColor = colorResource(R.color.almostWhite),
                    onClick = onClick
                )
            } else {
                IconButton(
                    onClick = onClick,
                    colors = IconButtonDefaults.iconButtonColors(colorResource(R.color.green))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_answer_call),
                        contentDescription = stringResource(R.string.notification_action_accept),
                        tint = Color.White
                    )
                }
            }
        }
    }
}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=640px,height=2340px,dpi=440")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CallNotificationPreview() {
    AppCompatTheme {
        CallNotification(
            callData = CallData(
                initialViewSetup = { it.setUnknown() },
                title = "John Doe",
                subtitle = "Incoming call",
                rejectCall = {},
                acceptCall = {})
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DoubleCallNotificationPreview() {
    AppCompatTheme {
        CallNotification(
            callData = CallData(
                initialViewSetup = { it.setUnknown() },
                title = "John Doe",
                subtitle = "Incoming call",
                rejectCall = {},
                acceptCall = {},
                isDoubleCall = true
            )
        )
    }
}


@Preview
@Composable
fun OngoingCallPreview() {
    AppCompatTheme {
        CallNotification(
            callData = CallData(
                initialViewSetup = { it.setUnknown() },
                title = "John Doe",
                subtitle = "Ongoing call",
                rejectCall = {},
                isDoubleCall = false
            )
        )
    }
}

@Composable
fun EndAndAcceptCall(backgroundColor: Color, onClick: () -> Unit = {}) {
    val offset = with(LocalDensity.current) { 8.dp.roundToPx() }
    Box(
        Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Button
            ) {
                onClick()
            }
            .size(48.dp)
            .background(backgroundColor), contentAlignment = Center
    ) {
        Icon(
            modifier = Modifier
                .offset { IntOffset(-offset, -offset) }
                .size(28.dp)
                .background(colorResource(R.color.red), CircleShape)
                .padding(6.dp),
            painter = painterResource(R.drawable.ic_end_call),
            contentDescription = stringResource(R.string.notification_action_reject),
            tint = Color.White
        )
        Icon(
            modifier = Modifier
                .offset { IntOffset(offset, offset) }
                .size(32.dp)
                .background(colorResource(R.color.green), CircleShape)
                .border(
                    2.dp, backgroundColor, CircleShape
                )
                .padding(6.dp),
            painter = painterResource(R.drawable.ic_answer_call),
            contentDescription = stringResource(R.string.notification_action_accept),
            tint = Color.White
        )
    }
}

@Preview
@Composable
fun EndAndAcceptCallPreview() {
    AppCompatTheme {
        EndAndAcceptCall(Color.White)
    }
}
