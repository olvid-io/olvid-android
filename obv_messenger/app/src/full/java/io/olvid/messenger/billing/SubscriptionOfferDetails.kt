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

import com.android.billingclient.api.ProductDetails
import io.olvid.messenger.App
import io.olvid.messenger.R


data class SubscriptionOfferDetails(
    val title: String,
    val description: String?,
    val details: ProductDetails,
    val offerToken: String,
    val pricingPhase: String,
    val price: String,
    val priceMicros: Long
)

fun SubscriptionOfferDetails.formattedPrice(): String {
    return when (pricingPhase) {
        "P1Y" -> App.getContext().getString(R.string.button_label_price_per_year, price)
        else -> App.getContext().getString(R.string.button_label_price_per_month, price)
    }
}
