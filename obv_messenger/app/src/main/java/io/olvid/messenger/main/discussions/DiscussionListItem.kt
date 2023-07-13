/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import io.olvid.messenger.main.InitialView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscussionListItem(
    title: AnnotatedString,
    body: AnnotatedString,
    date: AnnotatedString,
    initialViewSetup: (initialView: InitialView) -> Unit,
    @ColorInt customColor: Int = 0x00ffffff,
    backgroundImageUrl: String? = null,
    unread: Boolean,
    unreadCount: Int,
    muted: Boolean,
    locked: Boolean,
    mentioned: Boolean,
    pinned: Boolean,
    attachmentCount: Int,
    onClick: () -> Unit,
    isPreDiscussion :Boolean,
    onMarkAllDiscussionMessagesRead: () -> Unit,
    onMarkDiscussionAsUnread: () -> Unit,
    onPinDiscussion: (Boolean) -> Unit,
    onMuteDiscussion: (Boolean) -> Unit,
    onDeleteDiscussion: () -> Unit,
    renameActionName: String,
    onRenameDiscussion: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.background(colorResource(id = R.color.almostWhite))) {
        // menu
        var menuOpened by remember { mutableStateOf(false) }
        if (!isPreDiscussion) {
            DiscussionMenu(
                menuOpened = menuOpened,
                onDismissRequest = { menuOpened = false },
                unread = unread,
                unreadCount = unreadCount,
                pinned = pinned,
                muted = muted,
                onOpenSettings = onOpenSettings,
                renameActionName = renameActionName,
                onRenameDiscussion = onRenameDiscussion,
                onDeleteDiscussion = onDeleteDiscussion,
                onMarkAllDiscussionMessagesRead = onMarkAllDiscussionMessagesRead,
                onMarkDiscussionAsUnread = onMarkDiscussionAsUnread,
                onPinDiscussion = onPinDiscussion,
                onMuteDiscussion = onMuteDiscussion,
            )
        }

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
                    onLongClick = { menuOpened = !isPreDiscussion }, // never open the menu for a preDiscussion, otherwise after the invitation is accepted, the menu is shown
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
            InitialView(
                modifier = Modifier
                    .padding(
                        top = 16.dp,
                        start = 8.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    )
                    .requiredSize(56.dp),
                initialViewSetup = initialViewSetup,
                unreadMessages = unreadCount > 0 || unread,
                muted = muted,
                locked = locked,
                onClick = onClick,
            )

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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Subtitle
                Text(
                    text = body,
                    color = colorResource(id = R.color.greyTint),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Date
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = date,
                    color = colorResource(id = R.color.grey),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                            fontSize = 14.sp,
                            color = colorResource(id = R.color.alwaysWhite)
                        )
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
                            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(id = R.color.grey)
                        )
                    }
                }

            }
        }
    }
}

@Composable
fun DiscussionMenu(
    menuOpened: Boolean,
    onDismissRequest: () -> Unit,
    pinned: Boolean,
    muted: Boolean,
    unread: Boolean,
    unreadCount: Int,
    onMarkAllDiscussionMessagesRead: () -> Unit,
    onMarkDiscussionAsUnread: () -> Unit,
    onPinDiscussion: (Boolean) -> Unit,
    onMuteDiscussion: (Boolean) -> Unit,
    onDeleteDiscussion: () -> Unit,
    renameActionName: String,
    onRenameDiscussion: () -> Unit,
    onOpenSettings: () -> Unit,) {
    DropdownMenu(expanded = menuOpened, onDismissRequest = onDismissRequest) {
        // pin
        DropdownMenuItem(onClick = {
            onPinDiscussion(
                pinned.not()
            )
            onDismissRequest()
        }) {
            Text(text = stringResource(id = if (pinned) R.string.menu_action_discussion_unpin else R.string.menu_action_discussion_pin))
        }
        // mute
        DropdownMenuItem(onClick = {
            onMuteDiscussion(
                muted.not()
            )
            onDismissRequest()
        }) {
            Text(text = stringResource(id = if (muted) R.string.menu_action_unmute_notifications else R.string.menu_action_mute_notifications))
        }
        // mark read/unread
        if (unread || unreadCount > 0) {
            DropdownMenuItem(onClick = {
                onMarkAllDiscussionMessagesRead()
                onDismissRequest()
            }) {
                Text(text = stringResource(id = R.string.menu_action_discussion_mark_as_read))
            }
        } else {
            DropdownMenuItem(onClick = {
                onMarkDiscussionAsUnread()
                onDismissRequest()
            }) {
                Text(text = stringResource(id = R.string.menu_action_discussion_mark_as_unread))
            }
        }

        // rename
        DropdownMenuItem(onClick = {
            onRenameDiscussion()
            onDismissRequest()
        }) {
            Text(text = renameActionName)
        }

        // settings
        DropdownMenuItem(onClick = {
            onOpenSettings()
            onDismissRequest()
        }) {
            Text(text = stringResource(id = R.string.menu_action_discussion_settings))
        }

        //delete
        DropdownMenuItem(onClick = {
            onDeleteDiscussion()
            onDismissRequest()
        }) {
            Text(
                text = stringResource(id = R.string.menu_action_delete_discussion),
                color = colorResource(
                    id = R.color.red
                )
            )
        }
    }
}

@Preview
@Composable
private fun DiscussionListItemPreview() {
    AppCompatTheme {
        DiscussionListItem(
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
            isPreDiscussion = false,
            attachmentCount = 3,
            onClick = {},
            onPinDiscussion = {},
            onMuteDiscussion = {},
            onMarkDiscussionAsUnread = {},
            onMarkAllDiscussionMessagesRead = {},
            onDeleteDiscussion = {},
            onOpenSettings = {},
            renameActionName = "Rename",
            onRenameDiscussion = {},
        )
    }
}