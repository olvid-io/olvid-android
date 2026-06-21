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

package io.olvid.messenger.contact

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.engine.engine.types.ObvDialog
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
internal fun NotOneToOneCard(
    displayName: String,
    invitation: Invitation?,
    onInvite: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onAbort: () -> Unit
) {
    val titleRes = when {
        invitation == null -> R.string.label_contact_not_one_to_one
        invitation.categoryId == ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY -> R.string.invitation_status_one_to_one_invitation
        invitation.categoryId == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> R.string.invitation_status_one_to_one_invitation
        else -> R.string.label_contact_not_one_to_one
    }

    val explanationText = when {
        invitation == null -> stringResource(
            R.string.explanation_contact_not_one_to_one,
            displayName
        )

        invitation.categoryId == ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY -> stringResource(
            R.string.invitation_status_description_one_to_one_invitation_sent,
            displayName
        )

        invitation.categoryId == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> stringResource(
            R.string.invitation_status_description_one_to_one_invitation,
            displayName
        )

        else -> ""
    }

    Column(
        modifier = Modifier
            .background(
                color = colorResource(R.color.lighterGrey),
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .padding(start = 8.dp, top = 8.dp, end = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(32.dp),
                painter = painterResource(id = R.drawable.ic_contact_introduction),
                contentDescription = null,
                tint = colorResource(R.color.pink)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(titleRes),
                style = OlvidTypography.h3,
                color = colorResource(R.color.almostBlack)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.padding(start = 40.dp),
            text = explanationText,
            style = OlvidTypography.body2,
            color = colorResource(R.color.greyTint)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            when {
                invitation == null -> {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_invite),
                        onClick = onInvite
                    )
                }

                invitation.categoryId == ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY -> {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_abort),
                        onClick = onAbort
                    )
                }

                invitation.categoryId == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_reject),
                        onClick = onReject
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_accept),
                        onClick = onAccept
                    )
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotOneToOneCardPreview() {
    NotOneToOneCard(
        displayName = "John Doe",
        invitation = null,
        onInvite = {},
        onAccept = {},
        onReject = {},
        onAbort = {}
    )
}
