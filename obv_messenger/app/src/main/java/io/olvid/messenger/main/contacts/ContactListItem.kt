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

package io.olvid.messenger.main.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.main.EstablishingChannel
import io.olvid.messenger.main.InitialView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactListItem(
    title: AnnotatedString,
    body: AnnotatedString?,
    onClick: () -> Unit,
    initialViewSetup: (initialView: InitialView) -> Unit,
    publishedDetails: Boolean = false,
    publishedDetailsNotification: Boolean = false,
    endContent : (@Composable () -> Unit)? = null,
    shouldAnimateChannel: Boolean = false,
    onRenameContact: (() -> Unit)? = null,
    onCallContact: (() -> Unit)? = null,
    onDeleteContact: (() -> Unit)? = null,
    useDialogBackgroundColor: Boolean = false,
    additionalHorizontalPadding: Dp = 0.dp,
) {
    Box(modifier = Modifier.background(colorResource(id = if (useDialogBackgroundColor) R.color.dialogBackground else R.color.almostWhite))) {

        // menu
        var menuOpened by remember { mutableStateOf(false) }
        if (onRenameContact != null && onCallContact != null && onDeleteContact != null) {
             ContactMenu(
                menuOpened = menuOpened,
                onDismissRequest = { menuOpened = false },
                onRenameContact = onRenameContact,
                onCallContact = onCallContact,
                onDeleteContact = onDeleteContact,
            )
        }

        Row(
            modifier = Modifier
                .height(Min)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpened = true },
                )
                .padding(horizontal = 8.dp + additionalHorizontalPadding),
            verticalAlignment = CenterVertically
        ) {

            // InitialView
            InitialView(
                modifier = Modifier
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    )
                    .requiredSize(40.dp),
                initialViewSetup = initialViewSetup,
            )

            // content
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .padding(start = 8.dp)
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
                body?.let {
                    Text(
                        text = body,
                        color = colorResource(id = R.color.greyTint),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = CenterVertically) {

                endContent?.let {
                    it.invoke()
                } ?: kotlin.run {
                    AnimatedVisibility(visible = publishedDetails) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(width = 24.dp, height = 18.dp)

                        ) {
                            Image(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart),
                                painter = painterResource(id = drawable.ic_olvid_card),
                                contentDescription = ""
                            )
                            if (publishedDetailsNotification) {
                                Image(
                                    modifier = Modifier
                                        .size(maxWidth.times(0.4f))
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp),
                                    painter = painterResource(id = drawable.ic_dot_white_bordered),
                                    contentDescription = stringResource(
                                        id = string.content_description_message_status
                                    )
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = shouldAnimateChannel) {
                        EstablishingChannel()
                    }

                }
            }
        }
    }
}

@Composable
fun ContactMenu(
    menuOpened: Boolean,
    onDismissRequest: () -> Unit,
    onRenameContact: () -> Unit,
    onCallContact: () -> Unit,
    onDeleteContact: () -> Unit,
) {
    DropdownMenu(expanded = menuOpened, onDismissRequest = onDismissRequest) {
        // rename
        DropdownMenuItem(onClick = {
            onRenameContact()
            onDismissRequest()
        }) {
            Text(text = stringResource(id = string.menu_action_rename_contact))
        }
        // call
        DropdownMenuItem(onClick = {
            onCallContact()
            onDismissRequest()
        }) {
            Text(text = stringResource(id = string.menu_action_call_contact))
        }
        // delete
        DropdownMenuItem(onClick = {
            onDeleteContact()
            onDismissRequest()
        }) {
            Text(
                text = stringResource(id = string.menu_action_delete_contact),
                color = colorResource(id = R.color.red)
            )
        }
    }
}

@Preview
@Composable
private fun ContactListItemPreview() {
    AppCompatTheme {
        ContactListItem(
            title = AnnotatedString("Contact"),
            body = AnnotatedString("Description"),
            publishedDetails = true,
            publishedDetailsNotification = true,
            shouldAnimateChannel = false,
            onClick = {},
            initialViewSetup = {},
        )
    }
}