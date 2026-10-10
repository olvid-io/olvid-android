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

package io.olvid.messenger.billing

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidOutlinedActionButton
import io.olvid.messenger.olvid_plus.OLVID_STORE_URL
import io.olvid.messenger.olvid_plus.OlvidPlusOfferCard
import io.olvid.messenger.olvid_plus.OlvidPlusOfferDialogScaffold

/**
 * Without the Play Billing library, the only way to subscribe is through the Olvid Store:
 * show the store's plans (informational) and a link to it.
 */
@Composable
fun SubscriptionOfferDialog(activity: Activity?, onDismissCallback: () -> Unit, onPurchaseCallback: () -> Unit) {
    val context = LocalContext.current
    val storeOffers = listOf(
        R.string.label_store_offer_individual to R.string.label_store_offer_individual_description,
        R.string.label_store_offer_family to R.string.label_store_offer_family_description,
        R.string.label_store_offer_team to R.string.label_store_offer_team_description,
    )

    OlvidPlusOfferDialogScaffold(
        onDismiss = onDismissCallback,
        showStoreLink = false,
    ) { maxWidth ->
        // narrower than half so the third card peeks out and hints at horizontal scrolling
        val width = ((maxWidth - 32.dp) * 0.42f).coerceAtMost(200.dp)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = storeOffers, key = { it.first }) { (title, description) ->
                OlvidPlusOfferCard(
                    modifier = Modifier.width(width),
                    title = stringResource(title),
                    description = stringResource(description),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OlvidOutlinedActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.button_label_check_all_offers_olvid_store),
            trailingIcon = R.drawable.ic_open_in_new_small,
        ) {
            App.openLink(context, OLVID_STORE_URL.toUri())
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SubscriptionPreview() {
    SubscriptionOfferDialog(activity = null, onDismissCallback = {}, onPurchaseCallback = {})
}
