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
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

object SubscriptionRepository {

    const val MONTHLY_SUBSCRIPTION_SKUTYPE = "premium_2020_monthly"
    const val QUERY_TIMEOUT = 10_000L

    private var billingClient: BillingClient? = null
    private var billingUnavailable = false
    private var purchaseList: List<Purchase>? = null

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _purchasesUpdated = MutableSharedFlow<BillingResult>(extraBufferCapacity = 1)
    val purchasesUpdated = _purchasesUpdated.asSharedFlow()

    fun initialize(context: Context) {
        repositoryScope.launch {
            if (billingClient == null) {
                billingClient = BillingClient.newBuilder(context)
                    .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                    )
                    .setListener { billingResult, _ ->
                        Logger.i("💲 onPurchase: new subscription?")
                        _purchasesUpdated.tryEmit(billingResult)
                        refreshSubscriptions()
                    }
                    .build()

                val engineNotificationListener = object : SimpleEngineNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS) {
                    override fun callback(userInfo: HashMap<String, Any>) {
                        Logger.d("💲 received verify receipt success notification!")
                        val storeToken = userInfo[EngineNotifications.VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY] as? String
                        if (storeToken != null) {
                            repositoryScope.launch {
                                if (ensureConnection()) {
                                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(storeToken)
                                        .build()

                                    billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { purchaseBillingResult ->
                                        if (purchaseBillingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                                            Logger.e("💲 Error acknowledging store purchase ${purchaseBillingResult.debugMessage}")
                                        } else {
                                            Logger.d("💲 receipt acknowledged")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AppSingleton.getEngine().addNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, engineNotificationListener)
            }
            refreshSubscriptions()
        }
    }

    fun reconnect() {
        refreshSubscriptions()
    }

    private suspend fun ensureConnection(): Boolean {
        if (billingUnavailable) return false
        val client = billingClient ?: return false
        if (client.isReady) return true

        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> {
                            Logger.d("💲 billing client connected")
                            if (continuation.isActive) continuation.resume(true)
                        }
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                            Logger.d("💲 billing client unavailable")
                            billingUnavailable = true
                            if (continuation.isActive) continuation.resume(false)
                        }
                        else -> {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Connection lost
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    fun refreshSubscriptions(runAfterPurchaseListRefresh: () -> Unit = {}) {
        repositoryScope.launch {
            if (!ensureConnection()) return@launch
            val client = billingClient ?: return@launch

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            client.queryPurchasesAsync(params) { billingResult, list ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchaseList = list
                    Logger.d("💲 refreshPurchase: successfully refreshed purchase list. Purchases found: " + list.size)

                    var purchaseTokenIfSomeIdentitiesDoNotHaveASubscription: String? = null
                    
                    for (purchase in list) {
                        if (purchase.isAcknowledged) {
                            purchaseTokenIfSomeIdentitiesDoNotHaveASubscription = purchase.purchaseToken
                        } else {
                            Logger.d("💲 refreshPurchase: found a purchase not yet acknowledged!")
                            purchaseTokenIfSomeIdentitiesDoNotHaveASubscription = null

                            for (ownedIdentity in AppDatabase.getInstance().ownedIdentityDao().all) {
                                if (!ownedIdentity.active || ownedIdentity.keycloakManaged) {
                                    continue
                                }
                                Logger.d("💲 requesting verifyReceipt")
                                AppSingleton.getEngine().verifyReceipt(ownedIdentity.bytesOwnedIdentity, purchase.purchaseToken)
                            }
                            break
                        }
                    }

                    if (purchaseTokenIfSomeIdentitiesDoNotHaveASubscription != null) {
                        for (ownedIdentity in AppDatabase.getInstance().ownedIdentityDao().all) {
                            if (!ownedIdentity.active || ownedIdentity.keycloakManaged) {
                                continue
                            }
                            if (ownedIdentity.apiKeyStatus != OwnedIdentity.API_KEY_STATUS_VALID
                                && ownedIdentity.apiKeyStatus != OwnedIdentity.API_KEY_STATUS_OPEN_BETA_KEY) {
                                Logger.d("💲 requesting verifyReceipt")
                                AppSingleton.getEngine().verifyReceipt(ownedIdentity.bytesOwnedIdentity, purchaseTokenIfSomeIdentitiesDoNotHaveASubscription)
                            }
                        }
                    }
                }
                runAfterPurchaseListRefresh()
            }
        }
    }

    fun newIdentityAvailableForSubscription(bytesOwnedIdentity: ByteArray) {
        repositoryScope.launch {
            val currentList = purchaseList
            if (currentList == null) {
                refreshSubscriptions()
            } else {
                for (purchase in currentList) {
                    Logger.d("💲 requesting verifyReceipt for newIdentityAvailableForSubscription")
                    AppSingleton.getEngine().verifyReceipt(bytesOwnedIdentity, purchase.purchaseToken)
                    break
                }
            }
        }
    }

    fun loadSubscriptionSettingsHeader(activity: FragmentActivity, preferenceScreen: PreferenceScreen) {
        val loadPreference = Runnable {
            runCatching {
                val subscriptionPref = Preference(activity)
                subscriptionPref.setIcon(R.drawable.ic_pref_subscription)
                subscriptionPref.setTitle(R.string.pref_title_subscription)
                subscriptionPref.widgetLayoutResource = R.layout.preference_widget_header_chevron
                subscriptionPref.setOnPreferenceClickListener {
                    runCatching {
                        activity.startActivity(Intent(Intent.ACTION_VIEW,
                            "https://play.google.com/store/account/subscriptions?sku=$MONTHLY_SUBSCRIPTION_SKUTYPE&package=io.olvid.messenger".toUri()))
                    }.onFailure { Logger.x(it) }
                    true
                }
                preferenceScreen.addPreference(subscriptionPref)
            }
        }

        if (purchaseList?.isNotEmpty() == true) {
            Handler(Looper.getMainLooper()).post(loadPreference)
        } else if (billingClient != null) {
             repositoryScope.launch {
                 refreshSubscriptions {
                     if (purchaseList.isNullOrEmpty().not()) {
                         Handler(Looper.getMainLooper()).post(loadPreference)
                     }
                 }
             }
        }
    }

    suspend fun getSubscriptionPlans(productIds: List<String>): List<ProductDetails>? {
        return withTimeoutOrNull(QUERY_TIMEOUT.milliseconds) {
            if (!ensureConnection()) return@withTimeoutOrNull null
            val client = billingClient ?: return@withTimeoutOrNull null

            val productList = productIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            suspendCancellableCoroutine { continuation ->
                client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (continuation.isActive) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            continuation.resume(productDetailsList.productDetailsList)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult? {
        return billingClient?.launchBillingFlow(activity, params)
    }

    // Engine / Free Trial Logic

    fun queryFreeTrial(identity: ByteArray) {
        AppSingleton.getEngine().queryFreeTrial(identity)
    }

    fun startFreeTrial(identity: ByteArray) {
        AppSingleton.getEngine().startFreeTrial(identity)
    }

    fun freeTrialEvents(identity: ByteArray): Flow<FreeTrialEvent> = callbackFlow {
        val listener = object : EngineNotificationListener {
            var engineNumber: Long = -1L

            override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
                val bytesOwnedIdentity = userInfo[
                    when (notificationName) {
                        EngineNotifications.FREE_TRIAL_QUERY_SUCCESS -> EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY
                        EngineNotifications.FREE_TRIAL_QUERY_FAILED -> EngineNotifications.FREE_TRIAL_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY
                        EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS -> EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS_BYTES_OWNED_IDENTITY_KEY
                        EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED -> EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED_BYTES_OWNED_IDENTITY_KEY
                        else -> ""
                    }
                ] as? ByteArray

                if (bytesOwnedIdentity?.contentEquals(identity) == true) {
                    when (notificationName) {
                        EngineNotifications.FREE_TRIAL_QUERY_SUCCESS -> {
                            val available = userInfo[EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY] as? Boolean
                            trySend(FreeTrialEvent.QuerySuccess(available ?: false))
                        }
                        EngineNotifications.FREE_TRIAL_QUERY_FAILED -> {
                            trySend(FreeTrialEvent.QueryFailed)
                        }
                        EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS -> {
                            trySend(FreeTrialEvent.RetrieveSuccess)
                        }
                        EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED -> {
                            trySend(FreeTrialEvent.RetrieveFailed)
                        }
                    }
                }
            }

            override fun setEngineNotificationListenerRegistrationNumber(number: Long) {
                engineNumber = number
            }

            override fun getEngineNotificationListenerRegistrationNumber(): Long {
                return engineNumber
            }

            override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                return engineNumber != -1L
            }
        }

        val engine = AppSingleton.getEngine()
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, listener)
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, listener)
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, listener)
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, listener)

        awaitClose {
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, listener)
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, listener)
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, listener)
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, listener)
        }
    }

    sealed class FreeTrialEvent {
        data class QuerySuccess(val available: Boolean) : FreeTrialEvent()
        object QueryFailed : FreeTrialEvent()
        object RetrieveSuccess : FreeTrialEvent()
        object RetrieveFailed : FreeTrialEvent()
    }
}