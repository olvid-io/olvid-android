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

package io.olvid.messenger.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.avatar.Avatar

/**
 * Rounded card row for an [OwnedIdentity] — avatar + name + (position @ company) + trailing slot.
 *
 * Used by the share-extension profile picker and the history-transfer "pick profile" screen.
 * The avatar goes through the canonical [Avatar] composable, so keycloak certification badges,
 * custom photos and initial fallbacks are all handled the same way as everywhere else in the app.
 *
 * @param backgroundColor card background. Use [R.color.lighterGrey] when the parent surface is
 *   `almostWhite` (e.g. a bottom sheet), [R.color.almostWhite] when the parent is `lightGrey`
 *   (e.g. a settings-style screen).
 * @param title overrides the displayed name. Default is [OwnedIdentity.displayName], which already
 *   respects `customDisplayName`. Override when the caller needs a different formatting
 *   (e.g. pure first+last without nickname).
 * @param subtitle overrides the second line. Default is `position @ company` from the identity
 *   details, hidden when empty. Pass `null` explicitly to suppress the subtitle entirely.
 * @param avatarCornerRadius corner radius applied to the [Avatar]; `null` keeps the default
 *   circular shape. Pass a [Dp] (e.g. `12.dp`) for a rounded-square avatar instead.
 * @param trailing slot rendered on the right — defaults to the standard chevron-right glyph.
 */
@Composable
fun OwnedIdentityCard(
    identity: OwnedIdentity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorResource(R.color.lighterGrey),
    title: String? = null,
    subtitle: String? = rememberDefaultSubtitle(identity),
    avatarCornerRadius: Dp? = null,
    trailing: @Composable RowScope.() -> Unit = { ChevronRight() },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            target = identity,
            modifier = Modifier.size(40.dp),
            shape = avatarCornerRadius?.let { RoundedCornerShape(it) } ?: CircleShape,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title ?: identity.displayName,
                style = OlvidTypography.body1.copy(
                    color = colorResource(R.color.almostBlack),
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = OlvidTypography.body2.copy(color = colorResource(R.color.greyTint)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/**
 * Default subtitle resolver: `position @ company` if non-empty, otherwise null. Memoised on
 * [OwnedIdentity.identityDetails] so the Jackson JSON parse runs once per identity-details
 * change instead of once per recomposition.
 */
@Composable
private fun rememberDefaultSubtitle(identity: OwnedIdentity): String? =
    remember(identity.identityDetails) {
        identity.getIdentityDetails()
            ?.formatPositionAndCompany(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY)
            ?.takeIf { it.isNotEmpty() }
    }

@Composable
private fun ChevronRight() {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(R.drawable.ic_chevron_right),
        tint = colorResource(R.color.greyTint),
        contentDescription = null,
    )
}
