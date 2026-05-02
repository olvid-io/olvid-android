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

package io.olvid.messenger.discussion.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DiscussionInputEditText
import io.olvid.messenger.designsystem.theme.OlvidTypography

enum class AttachMenuitem(val stringRes: Int, val drawableRes: Int) {
    ATTACH_POLL(R.string.label_poll_create, R.drawable.ic_attach_poll),
    ATTACH_TIMER(R.string.label_attach_timer, R.drawable.ic_ephemeral),
    ATTACH_LOCATION(R.string.label_send_your_location, R.drawable.ic_attach_location),
    ATTACH_EMOJI(R.string.label_attach_emoji, R.drawable.ic_attach_emoji),
    ATTACH_INTRODUCE(R.string.button_label_introduce, R.drawable.ic_attach_introduce),
    ATTACH_VIDEO(R.string.label_attach_video, R.drawable.ic_attach_video),
    ATTACH_CAMERA(R.string.label_attach_camera, R.drawable.ic_attach_camera),
    ATTACH_IMAGE(R.string.label_attach_image, R.drawable.ic_attach_image),
    ATTACH_FILE(R.string.label_attach_file, R.drawable.ic_attach_file),
}

@Composable
fun AttachMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hasCamera: Boolean,
    showContactIntroduction: Boolean,
    inputEditText: DiscussionInputEditText?,
    controller: ComposeMessageController,
    buttonBounds: Rect,
) {
    if (!expanded) return

    BackHandler(onBack = onDismiss)

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var popupBounds: Rect? by remember { mutableStateOf(null) }

    Popup(properties = PopupProperties(focusable = false, usePlatformDefaultWidth = true)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .onGloballyPositioned { layoutCoordinates ->
                    val offset = layoutCoordinates.positionOnScreen()
                    popupBounds = Rect(
                        left = offset.x,
                        top = offset.y,
                        right = offset.x + layoutCoordinates.size.width,
                        bottom = offset.y + layoutCoordinates.size.height
                    )
                }
        ) {
            popupBounds?.let { bounds ->
                val menuStartPadding = with(density) {
                    if (layoutDirection == LayoutDirection.Rtl)
                        (bounds.right - buttonBounds.right).toDp() + 8.dp
                    else
                        (buttonBounds.left - bounds.left).toDp() + 8.dp
                }
                val menuBottomPadding = with(density) {
                    (bounds.bottom - buttonBounds.top).toDp() + 4.dp
                }
                Surface(
                    modifier = Modifier
                        .padding(start = menuStartPadding, bottom = menuBottomPadding)
                        .align(Alignment.BottomStart),
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    color = colorResource(R.color.dialogBackground),
                ) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AttachMenuitem.entries.filter { action ->
                            when (action) {
                                AttachMenuitem.ATTACH_CAMERA,
                                AttachMenuitem.ATTACH_VIDEO -> hasCamera

                                AttachMenuitem.ATTACH_INTRODUCE -> showContactIntroduction
                                AttachMenuitem.ATTACH_EMOJI -> false
                                else -> true // since emoji keyboard toggle is always shown, remove completely if validated
                            }
                        }.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(
                                            bounded = true,
                                            color = colorResource(R.color.almostBlack)
                                        ),
                                        onClick = {
                                            when (item) {
                                                AttachMenuitem.ATTACH_TIMER -> controller.onAttachTimer()
                                                AttachMenuitem.ATTACH_IMAGE -> controller.onAttachImage()
                                                AttachMenuitem.ATTACH_FILE -> controller.onAttachFile()
                                                AttachMenuitem.ATTACH_VIDEO -> controller.onAttachVideo(
                                                    hasCamera
                                                )

                                                AttachMenuitem.ATTACH_POLL -> controller.onAttachPoll()
                                                AttachMenuitem.ATTACH_CAMERA -> controller.onAttachCamera(
                                                    hasCamera
                                                )

                                                AttachMenuitem.ATTACH_EMOJI -> controller.onAttachEmoji(
                                                    inputEditText
                                                )

                                                AttachMenuitem.ATTACH_LOCATION -> controller.onAttachLocation()
                                                AttachMenuitem.ATTACH_INTRODUCE -> controller.onAttachIntroduce()
                                            }
                                            onDismiss()
                                        }
                                    )
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = stringResource(item.stringRes),
                                    style = OlvidTypography.body1,
                                    color = colorResource(R.color.almostBlack),
                                )
                                Spacer(Modifier.width(16.dp))
                                Icon(
                                    modifier = Modifier.size(28.dp),
                                    painter = painterResource(item.drawableRes),
                                    tint = colorResource(R.color.almostBlack),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
