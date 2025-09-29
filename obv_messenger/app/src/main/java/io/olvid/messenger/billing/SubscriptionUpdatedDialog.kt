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
package io.olvid.messenger.billing

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.engine.types.EngineAPI
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun SubscriptionUpdatedDialog(
    onDismissRequest: () -> Unit,
    viewModel: SubscriptionPurchaseViewModel,
    ownedIdentity: OwnedIdentity,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.dialogBackground))
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(id = R.string.dialog_title_subscription_updated),
                style = OlvidTypography.h2,
                color = colorResource(R.color.accent)
            )

            SubscriptionStatusScreen(
                modifier = Modifier.padding(8.dp),
                contentPadding = 16.dp,
                viewModel = viewModel,
                apiKeyStatus = ownedIdentity.getApiKeyStatus(),
                apiKeyExpirationTimestamp = ownedIdentity.apiKeyExpirationTimestamp,
                apiKeyPermissions = ownedIdentity.getApiKeyPermissions(),
                licenseQuery = false,
                showInAppPurchase = true,
                anotherIdentityHasCallsPermission = AppSingleton.getOtherProfileHasCallsPermission(),
            )

            OlvidTextButton(
                modifier = Modifier.align(Alignment.End),
                text = stringResource(id = R.string.button_label_ok),
                onClick = onDismissRequest,
            )
        }
    }
}
