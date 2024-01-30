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

package io.olvid.messenger.billing;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.SimpleEngineNotificationListener;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class BillingUtils {

    public static final String MONTHLY_SUBSCRIPTION_SKUTYPE = "premium_2020_monthly";

    private static BillingClient billingClientInstance = null;
    private static SimpleEngineNotificationListener engineNotificationListener = null;
    private static final Queue<Runnable> tasksAwaitingBillingClientConnection = new ArrayDeque<>();
    private static final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("BillingUtils");
    private static final Set<PurchasesUpdatedListener> externalPurchaseListeners = new HashSet();

    private static boolean billingUnavailable = false;
    private static List<Purchase> purchaseList = null;

    public static void initializeBillingClient(Context context) {
        executor.execute(() -> {
            if (billingClientInstance == null) {
                billingClientInstance = BillingClient.newBuilder(context)
                        .setListener((@NonNull BillingResult billingResult, @Nullable List<Purchase> list) -> {
                            Logger.i("💲 onPurchase: new subscription?");
                            synchronized (externalPurchaseListeners) {
                                for (PurchasesUpdatedListener purchasesUpdatedListener : externalPurchaseListeners) {
                                    purchasesUpdatedListener.onPurchasesUpdated(billingResult, list);
                                }
                            }
                            refreshPurchases(null);
                        })
                        .enablePendingPurchases()
                        .build();
                engineNotificationListener = new SimpleEngineNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS) {
                    @Override
                    public void callback(HashMap<String, Object> userInfo) {
                        Logger.d("💲 received verify receipt success notification!");

                        String storeToken = (String) userInfo.get(EngineNotifications.VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY);
                        if (storeToken != null) {
                            connectAndRun(() -> {
                                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(storeToken)
                                        .build();

                                billingClientInstance.acknowledgePurchase(acknowledgePurchaseParams, purchaseBillingResult -> {
                                    if (purchaseBillingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                        Logger.e("💲 Error acknowledging store purchase " + purchaseBillingResult.getDebugMessage());
                                    } else {
                                        Logger.d("💲 receipt acknowledged");
                                    }
                                });
                            });
                        }
                    }
                };

                AppSingleton.getEngine().addNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, engineNotificationListener);
            }

            connectAndRun(() -> refreshPurchases(null));
        });
    }

    public static void reconnect() {
        connectAndRun(null);
    }

    public static void refreshSubscriptions() {
        connectAndRun(() -> refreshPurchases(null));
    }

    public static void newIdentityAvailableForSubscription(byte[] bytesOwnedIdentity) {
        executor.execute(() -> {
            if (purchaseList == null) {
                connectAndRun(() -> refreshPurchases(null));
            } else {
                for (Purchase purchase : purchaseList) {
                    Logger.d("💲 requesting verifyReceipt for newIdentityAvailableForSubscription");
                    AppSingleton.getEngine().verifyReceipt(bytesOwnedIdentity, purchase.getPurchaseToken());
                    break;
                }
            }
        });
    }



    // must always be called from the executor
    private static void connectAndRun(@Nullable Runnable runnable) {
        executor.execute(() -> {
            if (billingUnavailable || billingClientInstance == null) {
                return;
            }
            if (runnable != null) {
                tasksAwaitingBillingClientConnection.offer(runnable);
            }

            if (!billingClientInstance.isReady()) {
                billingClientInstance.startConnection(new BillingClientStateListener() {
                    @Override
                    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                        switch (billingResult.getResponseCode()) {
                            case BillingClient.BillingResponseCode.OK:
                                Logger.d("💲 billing client connected");
                                runPendingTasks();
                                break;
                            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
                                executor.execute(() -> {
                                    Logger.d("💲 billing client unavailable");
                                    billingUnavailable = true;
                                    tasksAwaitingBillingClientConnection.clear();
                                });
                                break;
                        }
                    }

                    @Override
                    public void onBillingServiceDisconnected() {
                        // do nothing, we'll query again at next startup
                    }
                });
            } else {
                runPendingTasks();
            }
        });
    }

    private static void runPendingTasks() {
        executor.execute(() -> {
            Runnable task = tasksAwaitingBillingClientConnection.poll();
            if (task != null) {
                task.run();
                runPendingTasks();
            }
        });
    }


    private static void refreshPurchases(@Nullable Runnable runAfterPurchaseListRefresh) {
        purchaseList = null;
        QueryPurchasesParams purchasesParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClientInstance.queryPurchasesAsync(purchasesParams, (BillingResult billingResult, List<Purchase> list) -> executor.execute(() -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                purchaseList = list;
                Logger.d("💲 refreshPurchase: successfully refreshed purchase list. Purchases found: " + list.size());
                String purchaseTokenIfSomeIdentitiesDoNotHaveASubscription = null;
                for (Purchase purchase : list) {
                    if (purchase.isAcknowledged()) {
                        purchaseTokenIfSomeIdentitiesDoNotHaveASubscription = purchase.getPurchaseToken();
                    } else {
                        Logger.d("💲 refreshPurchase: found a purchase not yet acknowledged!");
                        purchaseTokenIfSomeIdentitiesDoNotHaveASubscription = null;

                        for (OwnedIdentity ownedIdentity: AppDatabase.getInstance().ownedIdentityDao().getAll()) {
                            if (!ownedIdentity.active || ownedIdentity.keycloakManaged) {
                                continue;
                            }
                            Logger.d("💲 requesting verifyReceipt");
                            AppSingleton.getEngine().verifyReceipt(ownedIdentity.bytesOwnedIdentity, purchase.getPurchaseToken());
                        }
                        break;
                    }
                }
                if (purchaseTokenIfSomeIdentitiesDoNotHaveASubscription != null) {
                    for (OwnedIdentity ownedIdentity: AppDatabase.getInstance().ownedIdentityDao().getAll()) {
                        if (!ownedIdentity.active || ownedIdentity.keycloakManaged) {
                            continue;
                        }
                        if (ownedIdentity.apiKeyStatus != OwnedIdentity.API_KEY_STATUS_VALID
                                && ownedIdentity.apiKeyStatus != OwnedIdentity.API_KEY_STATUS_OPEN_BETA_KEY) {
                            Logger.d("💲 requesting verifyReceipt");
                            AppSingleton.getEngine().verifyReceipt(ownedIdentity.bytesOwnedIdentity, purchaseTokenIfSomeIdentitiesDoNotHaveASubscription);
                        }
                    }
                }
            }
            if (runAfterPurchaseListRefresh != null) {
                executor.execute(runAfterPurchaseListRefresh);
            }
        }));
    }


    public static void loadSubscriptionSettingsHeader(FragmentActivity activity, PreferenceScreen preferenceScreen) {
        Runnable loadPreference = () -> {
            try {
                Preference subscriptionPref = new Preference(activity);
                subscriptionPref.setIcon(R.drawable.ic_pref_subscription);
                subscriptionPref.setTitle(R.string.pref_title_subscription);
                subscriptionPref.setOnPreferenceClickListener((preference) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=premium_2020_monthly&package=io.olvid.messenger")));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                });
                preferenceScreen.addPreference(subscriptionPref);
            } catch (Exception ignored) { }
        };

        if (purchaseList != null) {
            if (!purchaseList.isEmpty()) {
                new Handler(Looper.getMainLooper()).post(loadPreference);
            }
        } else if (billingClientInstance != null) {
            refreshPurchases(() -> {
                if (purchaseList != null && !purchaseList.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(loadPreference);
                }
            });
        }
    }

    public static void getSubscriptionProducts(@NonNull SubscriptionProductsCallback subscriptionProductsCallback) {
        executor.execute(() -> {
            if (billingUnavailable || billingClientInstance == null) {
                subscriptionProductsCallback.callback(null);
                return;
            }
            connectAndRun(() -> {
                QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(MONTHLY_SUBSCRIPTION_SKUTYPE)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()))
                        .build();

                billingClientInstance.queryProductDetailsAsync(queryProductDetailsParams, (@NonNull BillingResult billingResult, @NonNull List<ProductDetails> productDetailsList) -> {
                    if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        subscriptionProductsCallback.callback(null);
                    } else {
                        List<SubscriptionOfferDetails> subscriptionOfferDetails = new ArrayList<>();
                        for (ProductDetails productDetails: productDetailsList) {
                            if (productDetails.getSubscriptionOfferDetails() == null) {
                                continue;
                            }
                            for (ProductDetails.SubscriptionOfferDetails offerDetails : productDetails.getSubscriptionOfferDetails()) {
                                for (ProductDetails.PricingPhase pricingPhase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                                    subscriptionOfferDetails.add(new SubscriptionOfferDetails(
                                            productDetails.getTitle(),
                                            productDetails.getDescription(),
                                            productDetails,
                                            offerDetails.getOfferToken(),
                                            pricingPhase.getBillingPeriod(),
                                            pricingPhase.getFormattedPrice()
                                    ));
                                }
                            }
                        }
                        subscriptionProductsCallback.callback(subscriptionOfferDetails);
                    }
                });
            });
        });
    }

    interface SubscriptionProductsCallback {
        void callback(List<SubscriptionOfferDetails> subscriptionOfferDetails);
    }


    public static void launchSubscriptionPurchase(@NonNull Activity activity, @NonNull ProductDetails productDetails, @NonNull String offerToken) {
        executor.execute(() -> {
            if (billingClientInstance != null) {

                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .setOfferToken(offerToken)
                                .build()))
                        .build();

                BillingResult launchResult = billingClientInstance.launchBillingFlow(activity, billingFlowParams);
                if (launchResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    return;
                }
                Logger.e("💲 Error showing purchase panel " + launchResult.getDebugMessage());
            }

            App.toast(R.string.toast_message_error_launching_in_app_purchase, Toast.LENGTH_LONG);
        });
    }

    public static void addPurchasesUpdatedListener(PurchasesUpdatedListener listener) {
        synchronized (externalPurchaseListeners) {
            externalPurchaseListeners.add(listener);
        }
    }

    public static void removePurchasesUpdatedListener(PurchasesUpdatedListener listener) {
        synchronized (externalPurchaseListeners) {
            externalPurchaseListeners.remove(listener);
        }
    }
}