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
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DiscussionInputEditText
import io.olvid.messenger.designsystem.theme.OlvidTypography

// Declaration order is display order; consecutive items sharing a group are separated from the next group by a divider.
enum class AttachMenuitem(
    @StringRes val stringRes: Int,
    @DrawableRes val drawableRes: Int,
    @ColorRes val tileColorRes: Int,
    val group: Int,
) {
    ATTACH_CAMERA(R.string.label_attach_camera, R.drawable.ic_attach_camera, R.color.attachMenuCamera, 0),
    ATTACH_VIDEO(R.string.label_attach_video, R.drawable.ic_attach_video, R.color.attachMenuVideo, 0),
    ATTACH_SCAN(R.string.label_attach_scan, R.drawable.ic_attach_scan, R.color.attachMenuScan, 0),
    ATTACH_IMAGE(R.string.label_attach_image, R.drawable.ic_attach_image, R.color.attachMenuImage, 1),
    ATTACH_FILE(R.string.label_attach_file, R.drawable.ic_attach_file, R.color.attachMenuFile, 1),
    ATTACH_POLL(R.string.label_poll_create, R.drawable.ic_attach_poll, R.color.attachMenuPoll, 2),
    ATTACH_LOCATION(R.string.label_send_your_location, R.drawable.ic_attach_location, R.color.attachMenuLocation, 2),
    ATTACH_TIMER(R.string.label_attach_timer, R.drawable.ic_ephemeral, R.color.attachMenuEphemeral, 3),
    ATTACH_INTRODUCE(R.string.button_label_introduce, R.drawable.ic_attach_introduce, R.color.attachMenuIntroduce, 4),
    ATTACH_EMOJI(R.string.label_attach_emoji, R.drawable.ic_attach_emoji, R.color.greyTint, 5),
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
    // Keep the popup mounted while the exit animation plays.
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!expanded && visibleState.isIdle) return

    if (expanded) {
        BackHandler(onBack = onDismiss)
    }

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
                        ((bounds.right - buttonBounds.right).toDp() + 8.dp).coerceAtLeast(0.dp)
                    else
                        ((buttonBounds.left - bounds.left).toDp() + 8.dp).coerceAtLeast(0.dp)
                }
                val menuBottomPadding = with(density) {
                    ((bounds.bottom - buttonBounds.top).toDp() + 4.dp).coerceAtLeast(0.dp)
                }
                val items = AttachMenuitem.entries.filter { action ->
                    when (action) {
                        AttachMenuitem.ATTACH_CAMERA,
                        AttachMenuitem.ATTACH_VIDEO -> hasCamera

                        AttachMenuitem.ATTACH_SCAN -> hasCamera

                        AttachMenuitem.ATTACH_INTRODUCE -> showContactIntroduction
                        AttachMenuitem.ATTACH_EMOJI -> false
                        else -> true // since emoji keyboard toggle is always shown, remove completely if validated
                    }
                }
                // Same springs as material3 1.4 DropdownMenu (standard motion scheme fastSpatial / fastEffects)
                val scaleSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 1400f)
                val alphaSpec = spring<Float>(dampingRatio = 1f, stiffness = 3800f)
                val transformOrigin = TransformOrigin(
                    pivotFractionX = if (layoutDirection == LayoutDirection.Rtl) 1f else 0f,
                    pivotFractionY = 1f,
                )
                AnimatedVisibility(
                    visibleState = visibleState,
                    modifier = Modifier
                        .padding(start = menuStartPadding, bottom = menuBottomPadding)
                        .align(Alignment.BottomStart),
                    enter = fadeIn(alphaSpec) + scaleIn(scaleSpec, initialScale = 0.8f, transformOrigin = transformOrigin),
                    exit = fadeOut(alphaSpec) + scaleOut(scaleSpec, targetScale = 0.8f, transformOrigin = transformOrigin),
                ) {
                    Surface(
                        shadowElevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        color = colorResource(R.color.newDialogBackground),
                        border = BorderStroke(width = 1.dp, color = colorResource(R.color.newDialogBorder)),
                        contentColor = colorResource(R.color.almostBlack),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items.forEachIndexed { index, item ->
                                if (index > 0 && items[index - 1].group != item.group) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = colorResource(R.color.itemBackgroundBorder),
                                    )
                                }
                                AttachMenuRow(
                                    item = item,
                                    onClick = {
                                        when (item) {
                                            AttachMenuitem.ATTACH_TIMER -> controller.onAttachTimer()
                                            AttachMenuitem.ATTACH_IMAGE -> controller.onAttachImage()
                                            AttachMenuitem.ATTACH_FILE -> controller.onAttachFile()
                                            AttachMenuitem.ATTACH_VIDEO -> controller.onAttachVideo(hasCamera)
                                            AttachMenuitem.ATTACH_POLL -> controller.onAttachPoll()
                                            AttachMenuitem.ATTACH_CAMERA -> controller.onAttachCamera(hasCamera)
                                            AttachMenuitem.ATTACH_SCAN -> controller.onAttachScan()
                                            AttachMenuitem.ATTACH_EMOJI -> controller.onAttachEmoji(inputEditText)
                                            AttachMenuitem.ATTACH_LOCATION -> controller.onAttachLocation()
                                            AttachMenuitem.ATTACH_INTRODUCE -> controller.onAttachIntroduce()
                                        }
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachMenuRow(item: AttachMenuitem, onClick: () -> Unit) {
    val color = colorResource(item.tileColorRes)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                indication = ripple({ color }),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            modifier = Modifier
                .size(32.dp)
                .background(color, CircleShape)
                .padding(6.dp),
            painter = painterResource(item.drawableRes),
            tint = colorResource(R.color.alwaysWhite),
            contentDescription = null,
        )
        Text(
            text = stringResource(item.stringRes),
            style = OlvidTypography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
