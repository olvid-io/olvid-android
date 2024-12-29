/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.discussion.message

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity.CLIPBOARD_SERVICE
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.Attachments
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService
import io.olvid.messenger.settings.SettingsActivity


@Composable
fun LocationSharing(
    messages: List<Message>,
    isDiscussionSharingLocation: Boolean,
    onGotoMessage: (messageId: Long) -> Unit,
    onStopSharingLocation: () -> Unit,
    onOpenMap: () -> Unit
) {
    var menuMessageOpened by remember { mutableStateOf(false) }
    var menuOpened by remember { mutableStateOf(false) }
    fun locationGoToMessageOrShowPopup() {
        when (SettingsActivity.locationIntegration) {
            SettingsActivity.LocationIntegrationEnum.OSM, SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM, SettingsActivity.LocationIntegrationEnum.MAPS -> {
                onOpenMap()
            }

            else -> {
                if (messages.size == 1) {
                    onGotoMessage(messages.first().id)
                } else {
                    menuMessageOpened = true
                }
            }
        }
    }

    Surface(
        modifier = Modifier.clickable(onClick = { locationGoToMessageOrShowPopup() }),
        border =
        BorderStroke(
            width = 1.dp,
            color = colorResource(id = R.color.red)
        ).takeIf { isDiscussionSharingLocation },
        shape = RoundedCornerShape(12.dp),
        color = colorResource(id = R.color.dialogBackground),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_location_sharing_grey),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                color = colorResource(id = R.color.greyTint),
                text = if (isDiscussionSharingLocation) {
                    if (messages.size == 1) {
                        stringResource(id = R.string.label_sharing_your_location)
                    } else {
                        pluralStringResource(
                            R.plurals.label_you_and_xxx_contacts_sharing_their_location,
                            messages.size - 1,
                            messages.size - 1
                        )
                    }
                } else {
                    pluralStringResource(
                        R.plurals.label_xxx_contacts_sharing_their_position,
                        messages.size,
                        messages.size
                    )

                },
                style = OlvidTypography.body2
            )
            Box {
                IconButton(onClick = { menuOpened = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_three_dots_grey),
                        tint = colorResource(
                            id = R.color.greyTint
                        ),
                        contentDescription = null
                    )
                }
                DropdownMenu(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            color = colorResource(id = R.color.dialogBackground)
                        )
                        .clip(RoundedCornerShape(8.dp)),
                    expanded = menuMessageOpened,
                    onDismissRequest = { menuMessageOpened = false }) {
                    messages.find { it.messageType == Message.TYPE_OUTBOUND_MESSAGE }?.let {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.text_you),
                                    color = colorResource(id = R.color.almostBlack)
                                )
                            },
                            onClick = { onGotoMessage(it.id) })
                    }
                    messages.filter { it.messageType != Message.TYPE_OUTBOUND_MESSAGE }.forEach {
                        DropdownMenuItem(text = {
                            Text(
                                text = AppSingleton.getContactCustomDisplayName(it.senderIdentifier)
                                    ?: stringResource(
                                        id = R.string.text_unknown_sender
                                    ),
                                color = colorResource(id = R.color.almostBlack)
                            )
                        }, onClick = { onGotoMessage(it.id) })
                    }
                }
                DropdownMenu(
                    modifier = Modifier
                        .background(
                            color = colorResource(id = R.color.dialogBackground)
                        )
                        .clip(RoundedCornerShape(8.dp)),
                    expanded = menuOpened, onDismissRequest = { menuOpened = false }) {
                    DropdownMenuItem(text = {
                        Text(
                            text = stringResource(id = R.string.menu_action_go_to_message),
                            color = colorResource(id = R.color.almostBlack)
                        )
                    }, onClick = {
                        if (messages.size == 1) {
                            onGotoMessage(messages.first().id)
                        } else {
                            menuMessageOpened = true
                        }
                        menuOpened = false
                    })
                    if (isDiscussionSharingLocation) {
                        DropdownMenuItem(text = {
                            Text(
                                text = stringResource(id = R.string.menu_action_location_message_stop_sharing),
                                color = colorResource(id = R.color.almostBlack)
                            )
                        }, onClick = {
                            onStopSharingLocation()
                            menuOpened = false
                        })
                    }
                    if (listOf(
                            SettingsActivity.LocationIntegrationEnum.OSM,
                            SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM,
                            SettingsActivity.LocationIntegrationEnum.MAPS
                        ).contains(SettingsActivity.locationIntegration)
                    ) {
                        DropdownMenuItem(text = {
                            Text(
                                text = stringResource(id = R.string.menu_action_open_map),
                                color = colorResource(id = R.color.almostBlack)
                            )
                        }, onClick = {
                            onOpenMap()
                            menuOpened = false
                        })
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun LocationSharingPreview() {
    AppCompatTheme {
        LocationSharing(
            messages = emptyList(),
            isDiscussionSharingLocation = true,
            onGotoMessage = {},
            onStopSharingLocation = {},
            onOpenMap = {}
        )
    }
}

@Composable
fun LocationMessage(
    message: Message,
    discussionId: Long?,
    scale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    highlighter: ((Context, AnnotatedString) -> AnnotatedString)? = null,
    ) {
    val context = LocalContext.current
    val jsonMessage = message.jsonMessage

    if (jsonMessage.jsonLocation == null) {
        return
    }
    LaunchedEffect(message.jsonMessage.jsonLocation.type) {
        if (message.locationType == Message.LOCATION_TYPE_SHARE || message.locationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
            // update location type if needed (will update locationType )
            if (message.isSharingExpired) { // isSharingExpired only return true if locationType == LOCATION_TYPE_SHARE
                // if outbound: tell service to stop sharing
                if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    LocationSharingSubService.stopSharingInDiscussion(
                        message.discussionId,
                        false
                    )
                } else if (message.messageType == Message.TYPE_INBOUND_MESSAGE) {
                    // if inbound just mark as finished in database
                    App.runThread {
                        AppDatabase.getInstance().messageDao().updateLocationType(
                            message.id,
                            Message.LOCATION_TYPE_SHARE_FINISHED
                        )
                    }
                }
            }
        }
    }
    // set location
    val truncatedLatitudeString = jsonMessage.getJsonLocation().truncatedLatitudeString
    val truncatedLongitudeString = jsonMessage.getJsonLocation().truncatedLongitudeString
    val title = stringResource(
        R.string.label_location_message_content_position,
        truncatedLatitudeString,
        truncatedLongitudeString
    )
    val altitude = stringResource(
        R.string.label_location_message_content_altitude,
        jsonMessage.jsonLocation.getTruncatedAltitudeString(
            context
        )
    )
    val precision = stringResource(
        R.string.label_location_message_content_precision,
        jsonMessage.jsonLocation.getTruncatedPrecisionString(
            context
        )
    )
    LocationMessageContent(
        message = message,
        title = title,
        precision = precision,
        altitude = altitude,
        explanation = when (message.locationType) {
            Message.LOCATION_TYPE_SHARE_FINISHED -> stringResource(id = R.string.label_location_sharing_ended)
            Message.LOCATION_TYPE_SHARE -> jsonMessage.getJsonLocation().getSharingExpiration()?.let {
                stringResource(
                    R.string.label_sharing_location_until, DateUtils.formatDateTime(
                        context,
                        it,
                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                    )
                )
            }

            else -> null
        },
        lastUpdated = stringResource(
            R.string.label_share_location_latest_update, StringUtils.getLongNiceDateString(
                context, jsonMessage.getJsonLocation().getTimestamp()
            )
        ).takeIf { message.locationType == Message.LOCATION_TYPE_SHARE || message.locationType == Message.LOCATION_TYPE_SHARE_FINISHED },
        address = message.jsonMessage.jsonLocation.address,
        scale = scale,
        onStopSharingLocation = {
            discussionId?.let {
                LocationSharingSubService.stopSharingInDiscussion(
                    it, false
                )
            }
        },
        onCopyCoordinates = {
            context.copyLocationToClipboard(
                truncatedLatitudeString = truncatedLatitudeString,
                truncatedLongitudeString = truncatedLongitudeString
            )
        },
        onClick = onClick,
        onLongClick = onLongClick,
        highlighter = highlighter,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocationMessageContent(
    message: Message,
    title: String,
    precision: String,
    altitude: String,
    explanation: String?,
    lastUpdated: String?,
    address: String?,
    scale: Float,
    onStopSharingLocation: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    highlighter: ((Context, AnnotatedString) -> AnnotatedString)?,
) {
    if (message.hasAttachments()) {
        BoxWithConstraints {
            Attachments(message = message,
                audioAttachmentServiceBinding = null,
                onAttachmentLongClick = { _ -> onLongClick() },
                maxWidth = maxWidth,
                onLocationClicked = onClick,
                discussionSearchViewModel = null
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onLongClick = onLongClick, onClick = onClick)
                .border(
                    width = 1.dp, color = colorResource(
                        id = R.color.locationBorder
                    ), shape = RoundedCornerShape(6.dp)
                )
                .background(
                    color = colorResource(id = R.color.almostWhite),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(4.dp)
        ) {
            Row {
                Box(contentAlignment = Companion.TopEnd) {
                    Image(
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Fit,
                        painter = painterResource(id = R.drawable.ic_map_and_pin_5),
                        contentDescription = null
                    )
                    if (message.locationType == Message.LOCATION_TYPE_SHARE) {
                        Text(
                            modifier = Modifier
                                .offset(x = 6.dp)
                                .background(
                                    colorResource(id = R.color.red),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            text = stringResource(id = R.string.label_live).uppercase(),
                            fontSize = constantSp(value = 10),
                            color = Color.White
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = OlvidTypography.body2,
                        color = colorResource(id = R.color.primary700),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = precision,
                        style = OlvidTypography.subtitle1,
                        color = colorResource(id = R.color.greyTint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = altitude,
                        style = OlvidTypography.subtitle1,
                        color = colorResource(id = R.color.greyTint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    lastUpdated?.let {
                        Text(
                            text = it,
                            style = OlvidTypography.subtitle1,
                            color = colorResource(id = R.color.greyTint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (explanation == null) {
                        IconButton(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 4.dp)
                                .size(16.dp),
                            onClick = onCopyCoordinates
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_swipe_copy),
                                tint = colorResource(
                                    id = R.color.greyTint
                                ),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
            explanation?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ic_location_blue_16dp),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        modifier = Modifier.weight(1f),
                        text = it,
                        style = OlvidTypography.subtitle1,
                        color = colorResource(
                            id = R.color.greyTint
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp),
                        onClick = onCopyCoordinates
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_swipe_copy),
                            tint = colorResource(
                                id = R.color.greyTint
                            ),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
    if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE && message.locationType == Message.LOCATION_TYPE_SHARE && message.status != Message.STATUS_SENT_FROM_ANOTHER_DEVICE) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = ripple()
                ) { onStopSharingLocation() },
            text = stringResource(id = R.string.title_stop_sharing_location),
            color = colorResource(
                id = R.color.red
            ),
            textAlign = TextAlign.Center
        )
    }
    address?.takeIf { it.isNotEmpty() }?.let {
        Text(
            modifier = Modifier,
            text = highlighter?.invoke(LocalContext.current, AnnotatedString(address)) ?: AnnotatedString(address),
            color = if (message.isInbound) colorResource(id = R.color.inboundMessageBody) else colorResource(
                id = R.color.primary700
            ),
            style = OlvidTypography.body1.copy(
                fontSize = (16 * scale).sp,
                lineHeight = (16 * scale).sp
            )
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview
@Composable
fun LocationMessageContentPreview() {
    AppCompatTheme {
        Column(
            Modifier
                .background(colorResource(id = R.color.almostWhite))
                .padding(8.dp)
        ) {
            LocationMessageContent(
                message = messageOutboundLocation,
                title = "Location: 42.424242, -21.212121",
                precision = "Precision: 5m",
                altitude = "Altitude: 12m",
                lastUpdated = "Latest update: 01/04/2021 12:20",
                explanation = "Sharing location until 16:55",
                scale = 1f,
                address = "2, rue de la Paix, 75000 Paris, France",
                onClick = {},
                onLongClick = {},
                onStopSharingLocation = {},
                onCopyCoordinates = {},
                highlighter = null
            )
        }
    }
}

fun Context.copyLocationToClipboard(
    truncatedLatitudeString: String,
    truncatedLongitudeString: String
) {
    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(
        getString(R.string.label_gps_coordinates),
        String.format("%s,%s", truncatedLatitudeString, truncatedLongitudeString)
    )
    clipboard.setPrimaryClip(clipData)

    App.toast(
        R.string.toast_location_coordinates_copied_to_clipboard,
        Toast.LENGTH_SHORT,
        Gravity.BOTTOM
    )
}

