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
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.billing.SubscriptionRepository.FreeTrialEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SubscriptionOfferViewModel : ViewModel() {

    var loading by mutableStateOf(true)
        private set

    var purchasing by mutableStateOf(false)
        private set

    var subscriptionsOffers by mutableStateOf<List<SubscriptionOfferDetails>?>(null)
        private set

    var selectedOfferToken by mutableStateOf<String?>(null)

    val selectedOfferPrice by derivedStateOf {
        subscriptionsOffers?.find { it.offerToken == selectedOfferToken }?.formattedPrice().orEmpty()
    }

    var dismissDialog by mutableStateOf(false)
        private set

    var purchaseSuccessful by mutableStateOf(false)
        private set

    // Free Trial Properties
    var bytesOwnedIdentity: ByteArray? by mutableStateOf(null)
        private set

    var freeTrialResults: Boolean? by mutableStateOf(null)
    var freeTrialFailed by mutableStateOf(false)
    var freeTrialButtonEnabled by mutableStateOf(true)

    var freeTrialJob: Job? = null

    init {
        SubscriptionRepository.initialize(App.getContext())
        fetchOffers()

        viewModelScope.launch {
            SubscriptionRepository.purchasesUpdated.collect { result ->
                if (purchasing) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        App.toast(
                            R.string.toast_message_in_app_purchase_cancelled,
                            Toast.LENGTH_SHORT
                        )
                        purchasing = false
                    } else {
                        App.toast(
                            R.string.toast_message_in_app_purchase_successful,
                            Toast.LENGTH_LONG
                        )
                        purchaseSuccessful = true
                        dismissDialog = true
                        SubscriptionRepository.refreshSubscriptions()
                    }
                }
            }
        }
    }

    fun fetchOffers(addDelay: Boolean = false) {
        loading = addDelay
        viewModelScope.launch {
            if (addDelay) {
                delay(1000)
            }
            val details = SubscriptionRepository.getSubscriptionPlans(
                listOf(
                    SubscriptionRepository.MONTHLY_SUBSCRIPTION_SKUTYPE
                )
            )
            loading = false

            if (details != null) {
                subscriptionsOffers = details.flatMap { productDetails ->
                    productDetails.subscriptionOfferDetails?.flatMap { offerDetails ->
                        offerDetails.pricingPhases.pricingPhaseList.map { pricingPhase ->
                            SubscriptionOfferDetails(
                                title = productDetails.title,
                                description = productDetails.description,
                                details = productDetails,
                                offerToken = offerDetails.offerToken,
                                pricingPhase = pricingPhase.billingPeriod,
                                price = pricingPhase.formattedPrice,
                                priceMicros = pricingPhase.priceAmountMicros
                            )
                        }
                    } ?: emptyList()
                }.sortedBy { it.priceMicros }
                selectedOfferToken = subscriptionsOffers?.firstOrNull()?.offerToken
            }
        }
    }

    fun updateBytesOwnedIdentity(bytesOwnedIdentity: ByteArray?) {
        if (this.bytesOwnedIdentity?.contentEquals(bytesOwnedIdentity) != true) {
            this.bytesOwnedIdentity = bytesOwnedIdentity
            // identity changed --> reset all properties
            freeTrialResults = null
            freeTrialFailed = false
            freeTrialButtonEnabled = true
            
            freeTrialJob?.cancel()
        }
    }

    fun initiateFreeTrialQuery() {
        val identity = bytesOwnedIdentity ?: return
        
        freeTrialJob?.cancel()
        freeTrialJob = viewModelScope.launch {
            // Timeout
            val timeoutJob = launch {
                delay(SubscriptionRepository.QUERY_TIMEOUT)
                if (freeTrialResults == null) {
                    freeTrialFailed = true
                }
            }

            launch {
                SubscriptionRepository.freeTrialEvents(identity).collect { event ->
                    when (event) {
                        is FreeTrialEvent.QuerySuccess -> {
                            timeoutJob.cancel()
                            freeTrialResults = event.available
                        }
                        is FreeTrialEvent.QueryFailed -> {
                            timeoutJob.cancel()
                            freeTrialFailed = true
                        }
                        is FreeTrialEvent.RetrieveSuccess -> {
                            App.toast(R.string.toast_message_free_trial_started, Toast.LENGTH_LONG)
                            updateBytesOwnedIdentity(null)
                            dismissDialog = true
                        }
                        is FreeTrialEvent.RetrieveFailed -> {
                            freeTrialButtonEnabled = true
                            App.toast(R.string.toast_message_failed_to_start_free_trial, Toast.LENGTH_LONG)
                        }
                    }
                }
            }
            
            SubscriptionRepository.queryFreeTrial(identity)
        }
    }

    fun startFreeTrial() {
        val identity = bytesOwnedIdentity ?: return
        freeTrialButtonEnabled = false
        SubscriptionRepository.startFreeTrial(identity)
    }

    fun launchBillingFlow(activity: Activity) {
        val offerDetails =
            subscriptionsOffers?.find { it.offerToken == selectedOfferToken } ?: return
        purchasing = true

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(offerDetails.details)
                        .setOfferToken(offerDetails.offerToken)
                        .build()
                )
            )
            .build()

        val result = SubscriptionRepository.launchBillingFlow(activity, params)
        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
            purchasing = false
            App.toast(
                R.string.toast_message_error_launching_in_app_purchase,
                Toast.LENGTH_LONG
            )
        }
    }

    fun getDiscountString(context: Context, offer: SubscriptionOfferDetails): String? {
        if (offer.pricingPhase == "P1Y") {
            val monthlyOffer = subscriptionsOffers?.find { it.pricingPhase == "P1M" }
            if (monthlyOffer != null && monthlyOffer.priceMicros > 0) {
                val monthlyPrice = monthlyOffer.priceMicros.toDouble()
                val yearlyPrice = offer.priceMicros.toDouble()
                val diff = 12 * monthlyPrice - yearlyPrice
                if (diff > 0) {
                    val freeMonthsCount = diff / monthlyPrice
                    if (freeMonthsCount >= 1.0) {
                        val months = freeMonthsCount.roundToInt()
                        return context.resources.getQuantityString(
                            R.plurals.subscription_offer_month_free,
                            months, months
                        )
                    } else {
                        val d = 1.0 - yearlyPrice / (12 * monthlyPrice)
                        if (d > 0) {
                            val percent = (d * 100).toInt()
                            return context.getString(
                                R.string.label_subscription_offer_save_percent,
                                percent
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    fun scheduleSubscriptionReminder(delayMs: Long) {
        SubscriptionReminderWorker.scheduleNotification(delayMs)
    }
}
