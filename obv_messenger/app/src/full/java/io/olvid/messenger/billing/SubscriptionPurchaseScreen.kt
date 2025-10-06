package io.olvid.messenger.billing

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MONTHLY_SUBSCRIPTION_SKUTYPE = "premium_2020_monthly"
const val QUERY_TIMEOUT = 10_000L

@Composable
fun SubscriptionPurchaseScreen(
    viewModel: SubscriptionPurchaseViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var freeTrialAvailable by remember { mutableStateOf<Boolean?>(null) }
    var freeTrialFailed by remember { mutableStateOf(false) }
    var freeTrialButtonEnabled by remember { mutableStateOf(true) }
    var disabledSubscriptionButton by remember { mutableStateOf<ProductDetails?>(null) }

    var freeTrialTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var subscriptionTimeoutJob by remember { mutableStateOf<Job?>(null) }

    val billingClient = remember {
        BillingClient.newBuilder(context)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    App.toast(R.string.toast_message_in_app_purchase_cancelled, Toast.LENGTH_SHORT)
                    disabledSubscriptionButton = null
                    return@setListener
                }
                App.toast(R.string.toast_message_in_app_purchase_successful, Toast.LENGTH_LONG)
                viewModel.showSubscriptionPlans = false
                BillingUtils.refreshSubscriptions()
            }
            .build()
    }

    fun querySkuDetails() {
        subscriptionTimeoutJob?.cancel()
        subscriptionTimeoutJob = coroutineScope.launch {
            delay(QUERY_TIMEOUT)
            if (viewModel.subscriptionDetails == null) {
                viewModel.subscriptionFailed = true
            }
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MONTHLY_SUBSCRIPTION_SKUTYPE)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            subscriptionTimeoutJob?.cancel()
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                viewModel.subscriptionDetails = productDetailsList?.productDetailsList
            } else {
                viewModel.subscriptionFailed = true
            }
        }
    }

    fun initiateQuery() {
        freeTrialAvailable = null
        freeTrialFailed = false
        viewModel.subscriptionDetails = null
        viewModel.subscriptionFailed = false

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (viewModel.subscriptionDetails == null) {
                        querySkuDetails()
                    }
                } else {
                    viewModel.subscriptionFailed = true
                }
            }

            override fun onBillingServiceDisconnected() {
                // do nothing, a timeout will probably occur...
            }
        })

        if (viewModel.freeTrialResults == null) {
            freeTrialTimeoutJob?.cancel()
            freeTrialTimeoutJob = coroutineScope.launch {
                delay(QUERY_TIMEOUT)
                if (freeTrialAvailable == null) {
                    freeTrialFailed = true
                }
            }
            AppSingleton.getEngine().queryFreeTrial(viewModel.bytesOwnedIdentity)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : EngineNotificationListener {
            var engineNumber : Long = -1L

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

                if (bytesOwnedIdentity.contentEquals(viewModel.bytesOwnedIdentity)) {
                    when (notificationName) {
                        EngineNotifications.FREE_TRIAL_QUERY_SUCCESS -> {
                            freeTrialTimeoutJob?.cancel()
                            freeTrialAvailable =
                                userInfo[EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY] as? Boolean
                        }

                        EngineNotifications.FREE_TRIAL_QUERY_FAILED -> {
                            freeTrialTimeoutJob?.cancel()
                            freeTrialFailed = true
                        }

                        EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS -> {
                            App.toast(R.string.toast_message_free_trial_started, Toast.LENGTH_LONG)
                            viewModel.updateBytesOwnedIdentity(null)
                        }

                        EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED -> {
                            freeTrialButtonEnabled = true
                            App.toast(
                                R.string.toast_message_failed_to_start_free_trial,
                                Toast.LENGTH_LONG
                            )
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

        onDispose {
            engine.removeNotificationListener(
                EngineNotifications.FREE_TRIAL_QUERY_SUCCESS,
                listener
            )
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, listener)
            engine.removeNotificationListener(
                EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS,
                listener
            )
            engine.removeNotificationListener(
                EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED,
                listener
            )
            freeTrialTimeoutJob?.cancel()
            subscriptionTimeoutJob?.cancel()
            billingClient.endConnection()
        }
    }

    LaunchedEffect(viewModel.bytesOwnedIdentity) {
        if (viewModel.bytesOwnedIdentity != null) {
            initiateQuery()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (freeTrialAvailable != true && viewModel.subscriptionDetails == null) {
            if ((freeTrialFailed || freeTrialAvailable == false) && viewModel.subscriptionFailed) {
                SubscriptionFailedView(onRetry = { initiateQuery() })
            } else {
                OlvidCircularProgress(modifier = Modifier.padding(8.dp), size = 32.dp)
                Text(
                    textAlign = TextAlign.Center,
                    text = stringResource(id = R.string.text_loading_subscription_plans),
                    style = OlvidTypography.subtitle1,
                    color = colorResource(R.color.almostBlack)
                )
            }
        } else {
            if (freeTrialAvailable == true) {
                FreeTrialView(
                    enabled = freeTrialButtonEnabled,
                    onClick = {
                        freeTrialButtonEnabled = false
                        AppSingleton.getEngine().startFreeTrial(viewModel.bytesOwnedIdentity)
                    }
                )
            }
            viewModel.subscriptionDetails?.forEach { productDetails ->
                productDetails.subscriptionOfferDetails?.forEach { offerDetails ->
                    offerDetails.pricingPhases.pricingPhaseList.forEach { pricingPhase ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colorResource(R.color.lightGrey))
                        SubscriptionDetailsView(
                            title = productDetails.title,
                            description = productDetails.description,
                            price = pricingPhase.formattedPrice,
                            billingPeriod = pricingPhase.billingPeriod,
                            enabled = disabledSubscriptionButton != productDetails,
                            onClick = {
                                val params = BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(
                                        listOf(
                                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                                .setProductDetails(productDetails)
                                                .setOfferToken(offerDetails.offerToken)
                                                .build()
                                        )
                                    )
                                    .build()
                                val result = (context as? Activity)?.let {
                                    billingClient.launchBillingFlow(context, params)
                                }
                                if (result == null || result.responseCode != BillingClient.BillingResponseCode.OK) {
                                    App.toast(
                                        R.string.toast_message_error_launching_in_app_purchase,
                                        Toast.LENGTH_LONG
                                    )
                                } else {
                                    disabledSubscriptionButton = productDetails
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionDetailsView(
    title: String,
    description: String,
    price: String,
    billingPeriod: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack)
            )
            Text(
                text = description,
                style = OlvidTypography.body2,
                color = colorResource(R.color.grey)
            )
        }
        OlvidTextButton(
            text = when (billingPeriod) {
                "P1M" -> stringResource(id = R.string.button_label_price_per_month, price)
                "P1Y" -> stringResource(id = R.string.button_label_price_per_year, price)
                else -> price
            },
            onClick = onClick,
            enabled = enabled
        )
    }
}

@Composable
fun FreeTrialView(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = R.string.label_free_trial_available),
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack)
            )
            Text(
                text = stringResource(id = R.string.label_free_trial_explanation),
                style = OlvidTypography.body2,
                color = colorResource(R.color.grey)
            )
        }
        OlvidTextButton(
            text = stringResource(id = R.string.button_label_start_free_trial),
            onClick = onClick,
            enabled = enabled
        )
    }
}

@Composable
fun SubscriptionFailedView(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.label_failed_to_query_subscription),
            modifier = Modifier.weight(1f),
            style = OlvidTypography.body1,
            color = colorResource(R.color.almostBlack)
        )
        OlvidTextButton(
            text = stringResource(id = R.string.button_label_retry),
            onClick = onRetry
        )
    }
}
