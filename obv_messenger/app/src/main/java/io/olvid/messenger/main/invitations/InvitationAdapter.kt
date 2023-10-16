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

package io.olvid.messenger.main.invitations

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.notifications.AndroidNotificationManager

class InvitationAdapter(private val invitationListViewModel: InvitationListViewModel) : ListAdapter<Invitation, InvitationViewHolder>(InvitationDiffCallback) {
    init {
        setHasStableIds(true)
    }

    object InvitationDiffCallback : DiffUtil.ItemCallback<Invitation>() {
        override fun areItemsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
            return oldItem.dialogUuid == newItem.dialogUuid
        }

        override fun areContentsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
            return oldItem.invitationTimestamp == newItem.invitationTimestamp
                    && oldItem.associatedDialog.category.id == newItem.associatedDialog.category.id
                    && oldItem.associatedDialog.category.serverTimestamp == newItem.associatedDialog.category.serverTimestamp
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): InvitationViewHolder {
        return InvitationViewHolder(ComposeView(parent.context))
    }

    override fun onBindViewHolder(holder: InvitationViewHolder, position: Int) {
        holder.bind(getItem(position), invitationListViewModel)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).dialogUuid.leastSignificantBits;
    }
}

class InvitationViewHolder(
    private val composeView: ComposeView
) : ViewHolder(composeView) {
    fun bind(invitation: Invitation, invitationListViewModel: InvitationListViewModel) {
        AndroidNotificationManager.clearInvitationNotification(invitation.dialogUuid)

        composeView.setContent {
            AppCompatTheme {
                InvitationListItem(
                    invitationListViewModel = invitationListViewModel,
                    invitation = invitation,
                    title = invitation.getAnnotatedTitle(composeView.context),
                    body = AnnotatedString(invitation.statusText),
                    date = invitation.getAnnotatedDate(composeView.context),
                    initialViewSetup = { initialView ->
                        invitationListViewModel.initialViewSetup(
                        initialView,
                        invitation
                    )},
                    onClick = { action, invitation, lastSAS -> invitationListViewModel.invitationClicked(action, invitation, lastSAS, composeView.context)}
                )
            }
        }
    }
}