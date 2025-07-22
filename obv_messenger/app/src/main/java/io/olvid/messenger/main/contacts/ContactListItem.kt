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

package io.olvid.messenger.main.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material.ripple
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.EstablishingChannel
import io.olvid.messenger.main.InitialView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactListItem(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
    title: AnnotatedString,
    body: AnnotatedString?,
    onClick: () -> Unit,
    initialViewSetup: (initialView: InitialView) -> Unit,
    publishedDetails: Boolean = false,
    publishedDetailsNotification: Boolean = false,
    startContent: (@Composable () -> Unit)? = null,
    endContent : (@Composable () -> Unit)? = null,
    shouldAnimateChannel: Boolean = false,
    onRenameContact: (() -> Unit)? = null,
    onCallContact: (() -> Unit)? = null,
    onDeleteContact: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.cutoutHorizontalPadding()
    ) {
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
                .then(
                    if (onRenameContact != null && onCallContact != null && onDeleteContact != null)
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = colorResource(R.color.greyOverlay)),
                            onClick = onClick,
                            onLongClick = { menuOpened = true },
                        )
                    else
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = colorResource(R.color.greyOverlay)),
                            onClick = onClick,
                        )
                )
                .padding(padding)
                .height(Min)
                .fillMaxWidth(),
            verticalAlignment = CenterVertically
        ) {
            // start content
            startContent?.invoke()

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
                    style = OlvidTypography.h3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Subtitle
                body?.let {
                    Text(
                        text = body,
                        color = colorResource(id = R.color.greyTint),
                        style = OlvidTypography.subtitle1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = CenterVertically) {

                AnimatedVisibility(visible = publishedDetails) {
                    PublishedDetails(
                        modifier = Modifier.padding(end = 8.dp),
                        notification = publishedDetailsNotification)
                }

                AnimatedVisibility(visible = shouldAnimateChannel) {
                    EstablishingChannel()
                }

                endContent?.invoke()
            }
        }
    }
}

@Composable
fun PublishedDetails(
    modifier: Modifier = Modifier,
    notification: Boolean = false) {
    BoxWithConstraints(
        modifier = modifier
            .size(width = 24.dp, height = 18.dp)
    ) {
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            painter = painterResource(id = drawable.ic_olvid_card),
            contentDescription = ""
        )
        if (notification) {
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

@Composable
fun ContactMenu(
    menuOpened: Boolean,
    onDismissRequest: () -> Unit,
    onRenameContact: () -> Unit,
    onCallContact: () -> Unit,
    onDeleteContact: () -> Unit,
) {
    OlvidDropdownMenu(expanded = menuOpened, onDismissRequest = onDismissRequest) {
        // rename
        OlvidDropdownMenuItem(
            onClick = {
                onRenameContact()
                onDismissRequest()
            },
            text = stringResource(id = string.menu_action_rename_contact)
        )
        // call
        OlvidDropdownMenuItem(
            onClick = {
                onCallContact()
                onDismissRequest()
            },
            text = stringResource(id = string.menu_action_call_contact)
        )
        // delete
        OlvidDropdownMenuItem(
            onClick = {
                onDeleteContact()
                onDismissRequest()
            },
            text = stringResource(id = string.menu_action_delete_contact),
            textColor = colorResource(id = R.color.red)
        )
    }
}

@Preview
@Composable
private fun ContactListItemPreview() {
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