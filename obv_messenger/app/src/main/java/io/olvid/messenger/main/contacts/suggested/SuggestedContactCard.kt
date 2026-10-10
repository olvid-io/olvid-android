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

package io.olvid.messenger.main.contacts.suggested

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.map
import io.olvid.engine.engine.types.ObvDialog.Category
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.avatar.AvatarTarget
import io.olvid.messenger.designsystem.components.OlvidOutlinedActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.contacts.DisplayableContact

/** A common group, wrapped so its identity is compared by content (id/title/photo) rather than
 *  by reference — this lets the frequent, identical DB re-emissions de-duplicate. */
@Immutable
class MutualGroupUi(val discussion: Discussion) {
    override fun equals(other: Any?): Boolean =
        other is MutualGroupUi &&
            other.discussion.id == discussion.id &&
            other.discussion.title == discussion.title &&
            other.discussion.photoUrl == discussion.photoUrl
    override fun hashCode(): Int = discussion.id.hashCode()
}

/**
 * The fully-resolved, value-stable model a card renders. Being [Immutable] (and value-equal) lets
 * [SuggestedContactCard] be skipped on unchanged data, so the constant DB churn behind the swipe
 * stack no longer recomposes the cards. [avatar] is hoisted in so the card is agnostic to
 * whether it is backed by a [Contact] or an [Invitation].
 */
@Immutable
data class SuggestedContactUiState(
    val bytesOwnedIdentity: ByteArray,
    val bytesContactIdentity: ByteArray,
    val name: String,
    val description: String?,
    val mutualGroups: List<MutualGroupUi>,
    val oneToOne: Boolean,
    val receivedInvitation: Invitation?,
    val sentInvitation: Invitation?,
    val avatar: AvatarTarget?,
)

/** Build the card model for either source. The stack keys by item identity, so a given key is
 *  always the same kind — the branch is consistent across recompositions. */
@Composable
fun rememberSuggestedContactUiState(item: SuggestedContactItem): SuggestedContactUiState {
    val liveContact by remember(item.contact) {
        AppDatabase.getInstance().contactDao().getAsync(item.contact.bytesOwnedIdentity, item.contact.bytesContactIdentity)
    }.map { contact: Contact? -> contact?.let { DisplayableContact(contact) } }.observeAsState()

    val fallback = remember(item.contact) { DisplayableContact(item.contact) }
    val mutualGroups = rememberMutualGroups(item.contact.bytesContactIdentity, item.contact.bytesOwnedIdentity)
    val pendingInvitation by remember(item.contact) {
        AppDatabase.getInstance().invitationDao()
            .getContactOneToOneInvitation(item.contact.bytesOwnedIdentity, item.contact.bytesContactIdentity)
    }.observeAsState()

    return remember(fallback, liveContact, mutualGroups, pendingInvitation) {
        SuggestedContactUiState(
            bytesOwnedIdentity = item.contact.bytesOwnedIdentity,
            bytesContactIdentity = item.contact.bytesContactIdentity,
            name = (liveContact ?: fallback).name,
            description = (liveContact ?: fallback).description,
            mutualGroups = mutualGroups,
            oneToOne = (liveContact ?: fallback).oneToOne,
            receivedInvitation = pendingInvitation?.takeIf { it.categoryId == Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY },
            sentInvitation = pendingInvitation?.takeIf { it.categoryId == Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY },
            avatar = (liveContact ?: fallback).avatarSource(),
        )
    }
}


/** Shared mutual-groups observer with retain-last-non-empty (see class doc above). */
@Composable
private fun rememberMutualGroups(
    bytesContactIdentity: ByteArray,
    bytesOwnedIdentity: ByteArray,
): List<MutualGroupUi> {
    val liveGroups by remember(bytesContactIdentity, bytesOwnedIdentity) {
        AppDatabase.getInstance().discussionDao()
            .getContactNotLockedGroupDiscussionsWithGroupMembersNames(
                bytesContactIdentity,
                bytesOwnedIdentity,
            )
    }.observeAsState()
    var mutualGroups by remember(bytesContactIdentity) { mutableStateOf(emptyList<MutualGroupUi>()) }
    LaunchedEffect(liveGroups) {
        val groups = liveGroups
        if (!groups.isNullOrEmpty()) {
            // structural-equality state: an identical re-emission is a no-op (no recomposition)
            mutualGroups = groups.map { MutualGroupUi(it.discussion) }
        }
    }
    return mutualGroups
}

/**
 * A single suggested-contact card: large avatar, name, position/company, the mutual-groups row,
 * and a "See details" button. Pure UI over a [SuggestedContactUiState] — no observers — so it is
 * skippable. Used both in the live swipe stack and in the onboarding sample card.
 */
@Composable
fun SuggestedContactCard(
    state: SuggestedContactUiState,
    modifier: Modifier = Modifier,
    onMoreInfo: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .sizeIn(maxWidth = CARD_WIDTH.dp, maxHeight = CARD_HEIGHT.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(colorResource(R.color.dialogBackground)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(32.dp))
            // the invitation status chip straddles the avatar's bottom edge (overlaps it ~halfway)
            Box {
                Avatar(
                    modifier = Modifier.size(144.dp),
                    target = state.avatar,
                )
                if (state.receivedInvitation != null || state.sentInvitation != null || state.oneToOne) {
                    InvitationBadge(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 12.dp),
                        oneToOne = state.oneToOne,
                        received = state.receivedInvitation != null,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = state.name,
                textAlign = TextAlign.Center,
                color = colorResource(R.color.almostBlack),
                style = OlvidTypography.h2,
                fontWeight = FontWeight.Bold,
            )
            state.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.greyTint),
                    style = OlvidTypography.body1,
                )
            }
            MutualGroupsRow(state.mutualGroups)
            Spacer(modifier = Modifier.weight(1f))
            OlvidOutlinedActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.button_label_see_details),
                contentColor = colorResource(R.color.almostBlack),
                outlinedColor = Color(0x66999999),
                onClick = onMoreInfo,
            )
        }
    }
}

/** Invitation status pill: blue "received" or neutral grey "sent and still pending". */
@Composable
private fun InvitationBadge(received: Boolean, modifier: Modifier = Modifier, oneToOne: Boolean) {
    Text(
        modifier = modifier
            .clip(CircleShape)
            .background(
                colorResource(if (oneToOne) R.color.green else if (received) R.color.olvid_gradient_light else R.color.greyTint),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        text = stringResource(
            if (oneToOne) R.string.label_suggested_contact_already_a_contact
            else if (received) R.string.label_suggested_contact_invitation_received
            else R.string.label_suggested_contact_invitation_sent,
        ),
        color = colorResource(R.color.alwaysWhite),
        style = OlvidTypography.body2,
        fontWeight = FontWeight.Medium,
    )
}

/**
 * The avatars + "«group» and N other groups in common" line shared with [io.olvid.messenger
 * .main.contacts.ContactInvitationPopup].
 */
@Composable
fun MutualGroupsRow(groups: List<MutualGroupUi>?) {
    groups?.takeIf { it.isNotEmpty() }?.let { list ->
        Spacer(modifier = Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy((-10).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            list.forEach { group ->
                Avatar(
                    modifier = Modifier.size(24.dp),
                    target = group.discussion,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (list.size == 1) {
                stringResource(R.string.text_common_group, list.first().discussion.title.orEmpty())
            } else {
                pluralStringResource(
                    R.plurals.text_common_groups,
                    list.size - 1,
                    list.first().discussion.title.orEmpty(),
                    list.size - 1,
                )
            },
            textAlign = TextAlign.Center,
            color = colorResource(R.color.greyTint),
            style = OlvidTypography.body1,
        )
    }
}
