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


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;


import java.util.Arrays;
import java.util.HashMap;
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

    LinearLayout availablePlansLinearLayout;
    View loadingView;
    SubscriptionPurchaseViewModel viewModel;
    final Timer timeoutTimer = new Timer();
    TimerTask timeoutFreeTrialTimerTask = null;
    Button freeTrialButton = null;

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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Engine engine = AppSingleton.getEngine();
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, this);
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, this);
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, this);
        engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, this);

        if (timeoutFreeTrialTimerTask != null) {
            timeoutFreeTrialTimerTask.cancel();
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
        if (viewModel.freeTrialResults != null) {
            if (viewModel.freeTrialFailed) {
                View separator = getLayoutInflater().inflate(R.layout.view_subscriptions_separator, availablePlansLinearLayout, false);
                availablePlansLinearLayout.addView(separator, 0);
                View failed = getLayoutInflater().inflate(R.layout.view_subscriptions_failed, availablePlansLinearLayout, false);
                availablePlansLinearLayout.addView(failed, 1);
                failed.findViewById(R.id.retry_button).setOnClickListener(v -> {
                    availablePlansLinearLayout.removeAllViews();
                    loadingView.setVisibility(View.VISIBLE);

                    viewModel.freeTrialResults = null;
                    viewModel.freeTrialFailed = false;

                    if (timeoutFreeTrialTimerTask != null) {
                        timeoutFreeTrialTimerTask.cancel();
                        timeoutFreeTrialTimerTask = null;
                    }

                    initiateQuery();
                });
            }
            loadingView.setVisibility(View.GONE);
        }
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

        public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
            if (!Arrays.equals(bytesOwnedIdentity, this.bytesOwnedIdentity)) {
                this.bytesOwnedIdentity = bytesOwnedIdentity;

                // identity changed --> reset all properties
                this.showSubscriptionPlans = false;
                this.freeTrialResults = null;
                this.freeTrialFailed = false;
            }
        }
    }
}
