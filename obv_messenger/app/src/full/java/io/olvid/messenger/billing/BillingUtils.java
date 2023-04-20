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


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;

public class BillingUtils {
    public static void verifyPurchases(byte[] bytesOwnedIdentity, Context context) {
        try {
            final BillingClient billingClient = BillingClient.newBuilder(context)
                    .enablePendingPurchases()
                    .setListener((billingResult, list) -> Logger.e("Purchase updated " + list))
                    .build();

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult setupBillingResult) {
                    billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, (BillingResult queryBillingResult, List<Purchase> list) -> {
                        if (list.size() > 0) {
                            for (Purchase purchase : list) {
                                if (!purchase.isAcknowledged()) {
                                    Logger.d("Found not acknowledged purchase --> sending it to server");
                                    AppSingleton.getEngine().verifyReceipt(bytesOwnedIdentity, purchase.getPurchaseToken());
                                    final EngineNotificationListener notificationListener = new EngineNotificationListener() {
                                        private Long regNumber;

                                        @Override
                                        public void callback(String notificationName, HashMap<String, Object> userInfo) {
                                            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, this);
                                            AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                                    .setPurchaseToken(purchase.getPurchaseToken())
                                                    .build();

                                            billingClient.acknowledgePurchase(acknowledgePurchaseParams, purchaseBillingResult -> {
                                                if (purchaseBillingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                                    Logger.e("Error acknowledging store purchase " + purchaseBillingResult.getDebugMessage());
                                                }
                                            });
                                        }

                                        @Override
                                        public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
                                            regNumber = registrationNumber;
                                        }

                                        @Override
                                        public long getEngineNotificationListenerRegistrationNumber() {
                                            return regNumber;
                                        }

                                        @Override
                                        public boolean hasEngineNotificationListenerRegistrationNumber() {
                                            return regNumber != null;
                                        }
                                    };
                                    AppSingleton.getEngine().addNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, notificationListener);
                                    // auto remove the listener after 20s
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> AppSingleton.getEngine().removeNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, notificationListener), 20_000);
                                }
                            }
                        } else {
                            billingClient.endConnection();
                        }
                    });
                }

                @Override
                public void onBillingServiceDisconnected() {
                    // do nothing, we'll query again at next startup
                }
            });

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    billingClient.endConnection();
                } catch (Exception e) {
                    // nothing
                }
            }, 30_000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadSubscriptionSettingsHeader(FragmentActivity activity, PreferenceScreen preferenceScreen) {
        final BillingClient billingClient = BillingClient.newBuilder(activity)
                .enablePendingPurchases()
                .setListener((billingResult, list) -> Logger.d("Purchase updated " + list))
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult setupBillingResult) {
                billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, (BillingResult queryBillingResult, List<Purchase> list) -> {
                    if (list.size() > 0) {
                        // there are some subscriptions, add a link
                        new Handler(Looper.getMainLooper()).post(() -> {
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
                        });
                    }
                    billingClient.endConnection();
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                // nothing to do
            }
        });
    }
}