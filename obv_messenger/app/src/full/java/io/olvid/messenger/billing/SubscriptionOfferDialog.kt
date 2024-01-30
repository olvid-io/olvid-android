/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R


@Composable
fun SubscriptionOfferDialog(activity: Activity?, onDismissCallback: () -> Unit, onPurchaseCallback: () -> Unit) {
    Dialog(
        onDismissRequest = onDismissCallback,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .padding(16.dp)
                .sizeIn(minWidth = 200.dp, maxWidth = 560.dp),
            propagateMinConstraints = true
        ) {
            var loading : Boolean by remember { mutableStateOf(true) }
            var purchasing : Boolean by remember { mutableStateOf(false) }
            var subscriptionsOffers : List<SubscriptionOfferDetails>? by remember { mutableStateOf(null) }
            val purchasesListener = remember {
                object : PurchasesUpdatedListener {
                    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
                        if (purchasing) {
                            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                                App.toast(R.string.toast_message_in_app_purchase_cancelled, Toast.LENGTH_SHORT)
                                purchasing = false
                                return
                            }

                            App.toast(R.string.toast_message_in_app_purchase_successful, Toast.LENGTH_LONG)
                            onDismissCallback()
                            onPurchaseCallback()
                        }
                    }
                }
            }

            DisposableEffect(Unit) {
                BillingUtils.addPurchasesUpdatedListener(purchasesListener)
                onDispose {
                    BillingUtils.removePurchasesUpdatedListener(purchasesListener)
                }
            }

            fun fetchSubscriptionOptions() {
                BillingUtils.getSubscriptionProducts({ subscriptionOfferDetails : List<SubscriptionOfferDetails>? ->
                    loading = false
                    subscriptionsOffers = subscriptionOfferDetails
                })
            }

            LaunchedEffect(Unit) {
                fetchSubscriptionOptions()
            }


            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(id = R.color.dialogBackground))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row {
                    Spacer(modifier = Modifier.weight(1f, true))
                    Icon(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = false)
                            ) { onDismissCallback() }
                            .padding(4.dp),
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = colorResource(id = R.color.almostBlack),
                        contentDescription = "close"
                    )
                }

                if (loading || purchasing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.CenterHorizontally),
                            color = colorResource(id = R.color.olvid_gradient_light),
                            strokeWidth = 5.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = stringResource(id = R.string.text_loading_subscription_plans))
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (subscriptionsOffers == null) {
                            Icon(
                                modifier = Modifier.size(48.dp),
                                painter = painterResource(id = R.drawable.ic_error_outline),
                                tint = colorResource(id = R.color.red),
                                contentDescription = ""
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(text = stringResource(id = R.string.label_failed_to_query_subscription))
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { fetchSubscriptionOptions() }) {
                                Text(
                                    text = stringResource(id = R.string.button_label_retry),
                                    color = colorResource(id = R.color.olvid_gradient_light)
                                )
                            }
                        } else {
                            subscriptionsOffers?.forEach { offerDetails ->
                                Row {
                                    Column(
                                        modifier = Modifier.weight(1f, true)
                                    ) {
                                        Text(
                                            text = offerDetails.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        offerDetails.description?.let {
                                            Text(
                                                text = it,
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when(offerDetails.pricingPhase) {
                                            "P1Y" -> stringResource(id = R.string.button_label_price_per_year_expanded, offerDetails.price)
                                            else -> stringResource(id = R.string.button_label_price_per_month_expanded, offerDetails.price)
                                        },
                                        textAlign = TextAlign.Center,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = stringResource(id = R.string.text_premium_features),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(id = R.drawable.ic_phone_failed_out),
                                        tint = colorResource(id = R.color.red),
                                        contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        modifier = Modifier.weight(1f, true),
                                        text = stringResource(id = R.string.text_feature_initiate_secure_calls),
                                        fontSize = 14.sp,
                                    )
                                    Image(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(id = R.drawable.ic_activated_green),
                                        contentDescription = null)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(id = R.drawable.ic_multi_device),
                                        tint = colorResource(id = R.color.green),
                                        contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        modifier = Modifier.weight(1f, true),
                                        text = stringResource(id = R.string.text_feature_multi_device),
                                        fontSize = 14.sp,
                                    )
                                    Image(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(id = R.drawable.ic_activated_green),
                                        contentDescription = null)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    modifier = Modifier.weight(weight = 1f, fill = false),
                                    elevation = null,
                                    onClick = {
                                        activity?.let {
                                            purchasing = true
                                            BillingUtils.launchSubscriptionPurchase(activity, offerDetails.details, offerDetails.offerToken)
                                        }
                                    },
                                    enabled = !purchasing
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.button_label_subscribe_now),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = "spec:width=1080px,height=2340px,dpi=400")
@Composable
fun SubPreview() {
    AppCompatTheme {
        SubscriptionOfferDialog(activity = null, onDismissCallback = {}, onPurchaseCallback = {})
    }
}
