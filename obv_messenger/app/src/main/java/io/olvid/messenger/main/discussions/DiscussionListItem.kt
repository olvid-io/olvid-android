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

package io.olvid.messenger.main.discussions

import androidx.annotation.ColorInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import sh.calvin.reorderable.ReorderableCollectionItemScope

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscussionListItem(
    modifier: Modifier = Modifier,
    title: AnnotatedString,
    body: AnnotatedString,
    date: AnnotatedString?,
    initialViewSetup: (initialView: InitialView) -> Unit,
    @ColorInt customColor: Int = 0x00ffffff,
    backgroundImageUrl: String? = null,
    unread: Boolean,
    unreadCount: Int,
    muted: Boolean,
    locked: Boolean,
    mentioned: Boolean,
    pinned: Boolean,
    locationsShared: Boolean,
    attachmentCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    reorderableScope: ReorderableCollectionItemScope? = null,
    onDragStopped: () -> Unit,
    lastOutboundMessageStatus: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
    ) {
        // custom background
        backgroundImageUrl?.let { model ->
            AsyncImage(
                modifier = Modifier.matchParentSize(),
                model = model,
                alpha = 0.15f,
                alignment = Center,
                contentScale = ContentScale.Crop,
                contentDescription = "backgroundImage"
            )
        }

        Row(
            modifier = Modifier
                .height(Min)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = ripple().takeIf { selected.not() }
                ), verticalAlignment = CenterVertically
        ) {
            // custom color
            Box(
                modifier = Modifier
                    .requiredWidth(8.dp)
                    .fillMaxHeight()
                    .background(color = Color(customColor))
            )

            // InitialView
            Box {
                val alpha: Float by animateFloatAsState(
                    targetValue = if (selected) 0f else 1f,
                    animationSpec = tween(), label = "alpha"
                )
                InitialView(
                    modifier = Modifier
                        .padding(
                            top = 16.dp,
                            start = 8.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        )
                        .alpha(alpha)
                        .requiredSize(56.dp),
                    initialViewSetup = initialViewSetup,
                    unreadMessages = unreadCount > 0 || unread,
                    muted = muted,
                    locked = locked,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Image(
                        modifier = Modifier
                            .padding(
                                top = 16.dp,
                                start = 8.dp,
                                end = 16.dp,
                                bottom = 16.dp
                            )
                            .background(Color.Gray, CircleShape)
                            .padding(8.dp)
                            .requiredSize(40.dp),
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "selected"
                    )
                }
            }

            // content
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .weight(1f)
            ) {
                // Title
                Text(
                    text = title,
                    color = colorResource(id = R.color.primary700),
                    style = OlvidTypography.h3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Subtitle
                Row(verticalAlignment = CenterVertically) {
                    lastOutboundMessageStatus?.invoke()
                    Text(
                        text = body,
                        color = colorResource(id = R.color.greyTint),
                        style = OlvidTypography.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Date
                date?.let {
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = date,
                        color = colorResource(id = R.color.grey),
                        style = OlvidTypography.subtitle1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // information
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {

                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = CenterVertically
                ) {

                    AnimatedVisibility(visible = pinned) {
                        Image(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(id = R.drawable.ic_pinned),
                            contentDescription = "pinned"
                        )
                    }

                    AnimatedVisibility(visible = locationsShared) {
                        Image(
                            modifier = Modifier.size(22.dp),
                            painter = painterResource(id = R.drawable.ic_attach_location),
                            contentDescription = "location",
                            colorFilter = ColorFilter.tint(colorResource(id = R.color.olvid_gradient_contrasted))
                        )
                    }
                    AnimatedVisibility(visible = mentioned) {
                        Image(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(id = R.drawable.ic_mentioned),
                            contentDescription = "mentioned"
                        )
                    }
                    AnimatedVisibility(visible = unreadCount > 0) {
                        Text(
                            modifier = Modifier
                                .background(
                                    color = colorResource(id = R.color.red),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            text = "$unreadCount",
                            style = OlvidTypography.body2,
                            color = colorResource(id = R.color.alwaysWhite)
                        )
                    }
                    AnimatedVisibility(
                        visible = reorderableScope != null,
                        enter = slideInHorizontally { it / 2 },
                        exit = slideOutHorizontally { -it / 2 }) {
                        reorderableScope?.let {
                            IconButton(
                                modifier = with(reorderableScope) {
                                    Modifier.draggableHandle(onDragStopped = {
                                        onDragStopped()
                                    })
                                },
                                onClick = {},
                            ) {
                                Icon(
                                    modifier = Modifier.requiredSize(24.dp),
                                    painter = painterResource(id = R.drawable.ic_drag_handle),
                                    contentDescription = "Reorder"
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = attachmentCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                width = 1.dp,
                                color = colorResource(id = R.color.grey),
                                shape = RoundedCornerShape(4.dp)
                            )
                    ) {
                        Text(
                            text = pluralStringResource(
                                id = R.plurals.text_reply_attachment_count,
                                attachmentCount,
                                attachmentCount
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = R.color.grey)
                        )
                    }
                }

            }
        }
    }
}

@Preview
@Composable
private fun DiscussionListItemPreview() {
    AppCompatTheme {
        DiscussionListItem(
            modifier = Modifier.background(colorResource(R.color.almostWhite)),
            title = AnnotatedString("Discussion Title"),
            body = AnnotatedString("Latest Message"),
            date = AnnotatedString("timestamp"),
            initialViewSetup = {},
            unread = true,
            unreadCount = 120,
            muted = true,
            mentioned = true,
            locked = false,
            pinned = true,
            locationsShared = true,
            attachmentCount = 3,
            onClick = {},
            onLongClick = {},
            selected = true,
            onDragStopped = {},
        )
    }
}