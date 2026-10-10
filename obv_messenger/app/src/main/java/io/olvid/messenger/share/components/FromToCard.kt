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

package io.olvid.messenger.share.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion

@Composable
fun FromToCard(
    modifier: Modifier = Modifier,
    ownedIdentity: OwnedIdentity?,
    selectedDiscussions: List<SearchableDiscussion>,
    onPickIdentity: () -> Unit,
    onPickIdentityLongClick: () -> Unit,
    onPickRecipients: () -> Unit,
    hasOtherIdentities: Boolean,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.lighterGrey)),
    ) {
        FromRow(
            ownedIdentity = ownedIdentity,
            onClick = if (hasOtherIdentities) onPickIdentity else null,
            onLongClick = onPickIdentityLongClick,
        )
        ToRow(
            selectedDiscussions = selectedDiscussions,
            onClick = onPickRecipients,
        )
    }
}

/** Width reserved for the "De :" / "À :" prefix so the avatar column lines up across rows. */
private val labelWidth = 40.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FromRow(
    ownedIdentity: OwnedIdentity?,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier.widthIn(min = labelWidth),
            text = stringResource(R.string.share_label_from),
            style = OlvidTypography.body2.copy(color = colorResource(R.color.greyTint)),
        )
        Avatar(
            target = ownedIdentity ?: AvatarSource.unknown(),
            modifier = Modifier.size(32.dp),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = ownedIdentity?.displayName.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = OlvidTypography.body1.copy(
                color = colorResource(R.color.almostBlack),
                fontWeight = FontWeight.Medium,
            ),
        )
        if (onClick != null) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_chevron_right),
                tint = colorResource(R.color.greyTint),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun ToRow(
    selectedDiscussions: List<SearchableDiscussion>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier.widthIn(min = labelWidth),
            text = stringResource(R.string.share_label_to),
            style = OlvidTypography.body2.copy(color = colorResource(R.color.greyTint)),
        )
        // if there is a single recipient, show their profile picture, otherwise show a generic icon
        if (selectedDiscussions.size == 1) {
            Avatar(
                target = selectedDiscussions.firstOrNull() ?: AvatarSource.unknown(),
                modifier = Modifier.size(32.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = colorResource(R.color.mediumGrey),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(R.drawable.ic_add_member),
                    tint = colorResource(R.color.greyTint),
                    contentDescription = null,
                )
            }
        }
        val label = when {
            selectedDiscussions.isEmpty() ->
                stringResource(R.string.share_label_choose_recipients)

            selectedDiscussions.size == 1 ->
                selectedDiscussions.first().title.takeIf { it.isNotEmpty() } ?: stringResource(R.string.text_unnamed_discussion)

            else -> pluralStringResource(
                R.plurals.label_share_destinations_count,
                selectedDiscussions.size,
                selectedDiscussions.size,
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = OlvidTypography.body1.copy(
                color = if (selectedDiscussions.isEmpty())
                    colorResource(R.color.greyTint)
                else colorResource(R.color.almostBlack),
                fontWeight = FontWeight.Medium,
            ),
        )
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_chevron_right),
            tint = colorResource(R.color.greyTint),
            contentDescription = null,
        )
    }
}

