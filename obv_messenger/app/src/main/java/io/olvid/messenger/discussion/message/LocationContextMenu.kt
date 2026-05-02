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

package io.olvid.messenger.discussion.message

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.LocationMessageHandler



data class LocationContextMenuState(
    val message: Message,
    val truncatedLatitude: String,
    val truncatedLongitude: String,
)

@Composable
fun LocationContextMenu(
    discussionViewModel: DiscussionViewModel,
    locationMessageHandler: LocationMessageHandler
) {
    val state = discussionViewModel.locationContextMenuState ?: return
    val activity = LocalActivity.current

    OlvidDropdownMenu(
        expanded = true,
        onDismissRequest = { discussionViewModel.locationContextMenuState = null }
    ) {
        // Open
        OlvidDropdownMenuItem(
            text = stringResource(R.string.menu_action_location_message_open),
            onClick = {
                App.openLocationInMapApplication(
                    activity,
                    state.truncatedLatitude,
                    state.truncatedLongitude,
                    state.message.contentBody
                ) { discussionViewModel.markAsReadOnPause = false }
                discussionViewModel.locationContextMenuState = null
            }
        )

        // Copy coordinates
        OlvidDropdownMenuItem(
            text = stringResource(R.string.menu_action_location_message_copy),
            onClick = {
                App.getContext()
                    .copyLocationToClipboard(state.truncatedLatitude, state.truncatedLongitude)
                discussionViewModel.locationContextMenuState = null
            }
        )

        // Open preview
        if (state.message.totalAttachmentCount > 0) {
            OlvidDropdownMenuItem(
                text = stringResource(R.string.menu_action_location_message_open_preview),
                onClick = {
                    App.runThread {
                        val message = AppDatabase.getInstance().messageDao()[state.message.id]
                        if (message != null) {
                            locationMessageHandler.openLocationPreviewInGallery(message)
                        }
                    }
                    discussionViewModel.locationContextMenuState = null
                }
            )
        }

        // Stop sharing
        if (state.message.isCurrentSharingOutboundLocationMessage) {
            OlvidDropdownMenuItem(
                text = stringResource(R.string.menu_action_location_message_stop_sharing),
                onClick = {
                    locationMessageHandler.stopSharingLocation()
                    discussionViewModel.locationContextMenuState = null
                },
                textColor = colorResource(R.color.red)
            )
        }

        // Change integration
        OlvidDropdownMenuItem(
            text = stringResource(R.string.menu_action_location_message_change_integration),
            onClick = {
                locationMessageHandler.changeIntegration()
                discussionViewModel.locationContextMenuState = null
            }
        )
    }
}
