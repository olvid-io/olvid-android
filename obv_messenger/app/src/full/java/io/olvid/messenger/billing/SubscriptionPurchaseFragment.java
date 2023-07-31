/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.billing;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;

public class SubscriptionPurchaseFragment extends Fragment implements EngineNotificationListener {
    private AppCompatActivity activity;

    public static final long QUERY_TIMEOUT = 10_000;

    BillingClient billingClient;
    LinearLayout availablePlansLinearLayout;
    View loadingView;
    SubscriptionPurchaseViewModel viewModel;
    final Timer timeoutTimer = new Timer();
    TimerTask timeoutFreeTrialTimerTask = null;
    TimerTask timeoutSubscriptionTimerTask = null;
    Button freeTrialButton = null;
    Button disabledSubscriptionButton = null;

    public static final String MONTHLY_SUBSCRIPTION_SKUTYPE = "premium_2020_monthly";

    public static SubscriptionPurchaseFragment newInstance() {
        return new SubscriptionPurchaseFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
        viewModel = new ViewModelProvider(activity).get(SubscriptionPurchaseViewModel.class);
        Engine engine = AppSingleton.getEngine();
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, this);
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, this);
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, this);
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, this);
        engine.addNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Engine engine = AppSingleton.getEngine();
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, this);
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, this);
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, this);
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, this);
        engine.removeNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, this);
        if (timeoutFreeTrialTimerTask != null) {
            timeoutFreeTrialTimerTask.cancel();
        }
        if (timeoutSubscriptionTimerTask != null) {
            timeoutSubscriptionTimerTask.cancel();
        }
        if (billingClient != null) {
            billingClient.endConnection();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscription_purchase, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        availablePlansLinearLayout = view.findViewById(R.id.available_plans_linear_layout);
        loadingView = view.findViewById(R.id.subscription_plans_loading_view);
        initiateQuery();
    }

    private void initiateQuery() {
        billingClient = BillingClient.newBuilder(activity)
                .enablePendingPurchases()
                .setListener(this::purchaseUpdated)
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (viewModel.subscriptionDetails == null) {
                        // query for in-app subscriptions
                        querySkuDetails(billingClient);
                    }
                } else {
                    Logger.w("Failed initialization of billing client: " + billingResult.getDebugMessage());
                    viewModel.subscriptionDetails = new ArrayList<>();
                    viewModel.subscriptionFailed = true;
                    refreshStatus();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // do nothing, a timeout will probably occur...
                Logger.w("Billing service disconnected");
            }
        });

        // query for free trial
        if (viewModel.freeTrialResults == null) {
            AppSingleton.getEngine().queryFreeTrial(viewModel.bytesOwnedIdentity);

            timeoutFreeTrialTimerTask = new TimerTask() {
                @Override
                public void run() {
                    timeoutFreeTrialTimerTask = null;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Logger.d("Timeout free trial");
                        if (viewModel.freeTrialResults == null) {
                            viewModel.freeTrialResults = false;
                            viewModel.freeTrialFailed = true;
                            refreshStatus();
                        }
                    });
                }
            };
            try {
                timeoutTimer.schedule(timeoutFreeTrialTimerTask, QUERY_TIMEOUT);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }


    @UiThread
    private void refreshStatus() {
        if (viewModel.freeTrialResults != null && viewModel.subscriptionDetails != null) {
            if (viewModel.freeTrialFailed && viewModel.subscriptionFailed) {
                View separator = getLayoutInflater().inflate(R.layout.view_subscriptions_separator, availablePlansLinearLayout, false);
                availablePlansLinearLayout.addView(separator, 0);
                View failed = getLayoutInflater().inflate(R.layout.view_subscriptions_failed, availablePlansLinearLayout, false);
                availablePlansLinearLayout.addView(failed, 1);
                failed.findViewById(R.id.retry_button).setOnClickListener(v -> {
                    availablePlansLinearLayout.removeAllViews();
                    loadingView.setVisibility(View.VISIBLE);

                    viewModel.freeTrialResults = null;
                    viewModel.freeTrialFailed = false;
                    viewModel.subscriptionDetails = null;
                    viewModel.subscriptionFailed = false;

                    if (timeoutFreeTrialTimerTask != null) {
                        timeoutFreeTrialTimerTask.cancel();
                        timeoutFreeTrialTimerTask = null;
                    }
                    if (timeoutSubscriptionTimerTask != null) {
                        timeoutSubscriptionTimerTask.cancel();
                        timeoutSubscriptionTimerTask = null;
                    }
                    if (billingClient != null) {
                        billingClient.endConnection();
                        billingClient = null;
                    }

                    initiateQuery();
                });
            }
            loadingView.setVisibility(View.GONE);
        }
    }


    private void querySkuDetails(BillingClient billingClient) {
        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MONTHLY_SUBSCRIPTION_SKUTYPE)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()))
                .build();

        billingClient.queryProductDetailsAsync(queryProductDetailsParams, this::productDetailsReceived);

        timeoutSubscriptionTimerTask = new TimerTask() {
            @Override
            public void run() {
                timeoutSubscriptionTimerTask = null;
                new Handler(Looper.getMainLooper()).post(() -> {
                    Logger.d("Timeout subscriptions");
                    if (viewModel.subscriptionDetails == null) {
                        viewModel.subscriptionDetails = new ArrayList<>();
                        viewModel.subscriptionFailed = true;
                        refreshStatus();
                    }
                });
            }
        };
        try {
            timeoutTimer.schedule(timeoutSubscriptionTimerTask, QUERY_TIMEOUT);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void productDetailsReceived(BillingResult billingResult, List<ProductDetails> productDetailsList) {
        Logger.d("💲 Product details received:\n" + productDetailsList);

        if (timeoutSubscriptionTimerTask != null) {
            timeoutSubscriptionTimerTask.cancel();
        }

        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            viewModel.subscriptionDetails = new ArrayList<>();
            viewModel.subscriptionFailed = true;
            // billing query failed
            new Handler(Looper.getMainLooper()).post(this::refreshStatus);
            return;
        }

        viewModel.subscriptionDetails = productDetailsList;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (productDetailsList.size() > 0) {
                View separator = getLayoutInflater().inflate(R.layout.view_subscriptions_separator, availablePlansLinearLayout, false);
                availablePlansLinearLayout.addView(separator, 0);
            }

            for (ProductDetails productDetails: productDetailsList) {
                if (productDetails.getSubscriptionOfferDetails() == null) {
                    continue;
                }
                for (ProductDetails.SubscriptionOfferDetails offerDetails: productDetails.getSubscriptionOfferDetails()) {
                    for (ProductDetails.PricingPhase pricingPhase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                        View subscriptionView = getLayoutInflater().inflate(R.layout.view_subscription_details, availablePlansLinearLayout, false);
                        Button subscriptionButton = subscriptionView.findViewById(R.id.subscription_button);
                        TextView subscriptionTitle = subscriptionView.findViewById(R.id.subscription_title_textview);
                        TextView subscriptionExplanation = subscriptionView.findViewById(R.id.subscription_explanation_textview);
                        subscriptionTitle.setText(productDetails.getTitle());
                        subscriptionExplanation.setText(productDetails.getDescription());
                        switch (pricingPhase.getBillingPeriod()) {
                            case "P1M":
                                subscriptionButton.setText(getString(R.string.button_label_price_per_month, pricingPhase.getFormattedPrice()));
                                break;
                            case "P1Y":
                                subscriptionButton.setText(getString(R.string.button_label_price_per_year, pricingPhase.getFormattedPrice()));
                                break;
                        }

                        subscriptionButton.setOnClickListener((view) -> {
                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                                    .setProductDetails(productDetails)
                                                    .setOfferToken(offerDetails.getOfferToken())
                                                    .build()))
                                    .build();

                            BillingResult launchResult = billingClient.launchBillingFlow(activity, billingFlowParams);
                            if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                Logger.e("💲 Error showing purchase panel " + launchResult.getDebugMessage());
                                App.toast(R.string.toast_message_error_launching_in_app_purchase, Toast.LENGTH_LONG);
                                return;
                            }
                            subscriptionButton.setEnabled(false);
                            this.disabledSubscriptionButton = subscriptionButton;
                        });
                        availablePlansLinearLayout.addView(subscriptionView, 1);
                    }
                }
            }

            refreshStatus();
        });
    }


    // called when a subscription is purchased
    private void purchaseUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            App.toast(R.string.toast_message_in_app_purchase_cancelled, Toast.LENGTH_SHORT);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (disabledSubscriptionButton != null) {
                    disabledSubscriptionButton.setEnabled(true);
                    disabledSubscriptionButton = null;
                }
            });
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> availablePlansLinearLayout.removeAllViews());
        App.toast(R.string.toast_message_in_app_purchase_successful, Toast.LENGTH_LONG);
        viewModel.showSubscriptionPlans = false;

        // refresh the subscriptions --> this will automatically add the subscription to any new identity
        BillingUtils.refreshSubscriptions();
    }



    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.FREE_TRIAL_QUERY_SUCCESS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY);
                Boolean available = (Boolean) userInfo.get(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY);
                if (Arrays.equals(bytesOwnedIdentity, viewModel.bytesOwnedIdentity) && available != null) {
                    viewModel.freeTrialResults = available;
                    if (timeoutFreeTrialTimerTask != null) {
                        timeoutFreeTrialTimerTask.cancel();
                    }
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (available) {
                            View separator = getLayoutInflater().inflate(R.layout.view_subscriptions_separator, availablePlansLinearLayout, false);
                            availablePlansLinearLayout.addView(separator);

                            View freeTrialView = getLayoutInflater().inflate(R.layout.view_subscription_free_trial, availablePlansLinearLayout, false);
                            freeTrialButton = freeTrialView.findViewById(R.id.start_free_trial_button);
                            freeTrialButton.setOnClickListener((view) -> {
                                view.setEnabled(false);
                                AppSingleton.getEngine().startFreeTrial(bytesOwnedIdentity);
                            });
                            availablePlansLinearLayout.addView(freeTrialView);
                        }
                        refreshStatus();
                    });
                }
                break;
            }
            case EngineNotifications.FREE_TRIAL_QUERY_FAILED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.FREE_TRIAL_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY);
                if (Arrays.equals(bytesOwnedIdentity, viewModel.bytesOwnedIdentity)) {
                    viewModel.freeTrialResults = false;
                    viewModel.freeTrialFailed = true;
                    if (timeoutFreeTrialTimerTask != null) {
                        timeoutFreeTrialTimerTask.cancel();
                    }
                    new Handler(Looper.getMainLooper()).post(this::refreshStatus);
                }
                break;
            }
            case EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS_BYTES_OWNED_IDENTITY_KEY);
                if (Arrays.equals(bytesOwnedIdentity, viewModel.bytesOwnedIdentity)) {
                    new Handler(Looper.getMainLooper()).post(() -> availablePlansLinearLayout.removeAllViews());
                    App.toast(R.string.toast_message_free_trial_started, Toast.LENGTH_LONG);
                    viewModel.setBytesOwnedIdentity(null);
                }
                break;
            }
            case EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED_BYTES_OWNED_IDENTITY_KEY);
                if (Arrays.equals(bytesOwnedIdentity, viewModel.bytesOwnedIdentity)) {
                    if (freeTrialButton != null) {
                        new Handler(Looper.getMainLooper()).post(() -> freeTrialButton.setEnabled(true));
                    }
                    App.toast(R.string.toast_message_failed_to_start_free_trial, Toast.LENGTH_LONG);
                }
                break;
            }
        }
    }







    private Long registrationNumber;

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        if (registrationNumber == null) {
            return 0;
        }
        return registrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return registrationNumber != null;
    }




    public static class SubscriptionPurchaseViewModel extends ViewModel {
        public byte[] bytesOwnedIdentity = null;
        public boolean showSubscriptionPlans = false;

        public Boolean freeTrialResults = null;
        public boolean freeTrialFailed = false;
        public List<ProductDetails> subscriptionDetails = null;
        public boolean subscriptionFailed = false;

        public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
            if (!Arrays.equals(bytesOwnedIdentity, this.bytesOwnedIdentity)) {
                this.bytesOwnedIdentity = bytesOwnedIdentity;

                // identity changed --> reset all properties
                this.showSubscriptionPlans = false;
                this.freeTrialResults = null;
                this.freeTrialFailed = false;
                this.subscriptionDetails = null;
                this.subscriptionFailed = false;
            }
        }
    }
}
