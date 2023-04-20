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

package io.olvid.messenger.fragments;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.messenger.R;
import io.olvid.messenger.billing.SubscriptionPurchaseFragment;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class SubscriptionStatusFragment extends Fragment {
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String API_KEY_STATUS = "status";
    public static final String API_KEY_PERMISSIONS = "permissions";
    public static final String API_KEY_EXPIRATION = "expiration";
    public static final String LICENSE_QUERY = "licenseQuery";
    public static final String SHOW_IN_APP_PURCHASE = "showInAppPurchase";
    public static final String ANOTHER_IDENTITY_HAS_CALLS_PERMISSION = "anotherIdentityHasCallsPermission";

    private AppCompatActivity activity;

    byte[] bytesOwnedIdentity;
    EngineAPI.ApiKeyStatus apiKeyStatus;
    List<EngineAPI.ApiKeyPermission> apiKeyPermissions;
    Long apiKeyExpirationTimestamp;
    boolean licenseQuery;
    boolean showInAppPurchase;
    boolean anotherIdentityHasCallsPermission;

    Button subscribeButton;
    ViewGroup subscriptionPurchasePlaceholder;
    SubscriptionPurchaseFragment.SubscriptionPurchaseViewModel viewModel;

    public static SubscriptionStatusFragment newInstance(byte[] bytesOwnedIdentity, EngineAPI.ApiKeyStatus apiKeyStatus, @Nullable Long apiKeyExpirationTimestamp, List<EngineAPI.ApiKeyPermission> apiKeyPermissions, boolean licenseQuery, boolean showInAppPurchase, boolean anotherIdentityHasCallsPermission) {
        SubscriptionStatusFragment fragment = new SubscriptionStatusFragment();
        Bundle args = new Bundle();
        args.putByteArray(BYTES_OWNED_IDENTITY, bytesOwnedIdentity);
        args.putInt(API_KEY_STATUS, OwnedIdentity.serializeApiKeyStatus(apiKeyStatus));
        if (apiKeyExpirationTimestamp != null) {
            args.putLong(API_KEY_EXPIRATION, apiKeyExpirationTimestamp);
        }
        args.putLong(API_KEY_PERMISSIONS, OwnedIdentity.serializeApiKeyPermissions(apiKeyPermissions));
        args.putBoolean(LICENSE_QUERY, licenseQuery);
        args.putBoolean(SHOW_IN_APP_PURCHASE, showInAppPurchase);
        args.putBoolean(ANOTHER_IDENTITY_HAS_CALLS_PERMISSION, anotherIdentityHasCallsPermission);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
        Bundle arguments = getArguments();
        if (arguments != null) {
            bytesOwnedIdentity = arguments.getByteArray(BYTES_OWNED_IDENTITY);
            apiKeyStatus = OwnedIdentity.deserializeApiKeyStatus(arguments.getInt(API_KEY_STATUS));
            if (arguments.containsKey(API_KEY_EXPIRATION)) {
               apiKeyExpirationTimestamp = arguments.getLong(API_KEY_EXPIRATION);
            } else {
                apiKeyExpirationTimestamp = null;
            }
            apiKeyPermissions = OwnedIdentity.deserializeApiKeyPermissions(arguments.getLong(API_KEY_PERMISSIONS));
            licenseQuery = arguments.getBoolean(LICENSE_QUERY);
            showInAppPurchase = arguments.getBoolean(SHOW_IN_APP_PURCHASE);
            anotherIdentityHasCallsPermission = arguments.getBoolean(ANOTHER_IDENTITY_HAS_CALLS_PERMISSION);
        }
        viewModel = new ViewModelProvider(activity).get(SubscriptionPurchaseFragment.SubscriptionPurchaseViewModel.class);
        viewModel.setBytesOwnedIdentity(bytesOwnedIdentity);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscription_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView apiKeyStatusTextView = view.findViewById(R.id.api_key_status_text_view);
        TextView apiKeyExpirationTextView = view.findViewById(R.id.api_key_expiration_text_view);
        TextView premiumFeaturesTitleTextView = view.findViewById(R.id.premium_features_title_text_view);
        TextView permissionCallTextView = view.findViewById(R.id.permission_call_text_view);
        subscribeButton = view.findViewById(R.id.subscribe_button);
        subscribeButton.setOnClickListener(this::subscribeButtonClicked);
        subscriptionPurchasePlaceholder = view.findViewById(R.id.subscription_purchase_placeholder);
        ViewGroup fixPaymentGroup = view.findViewById(R.id.fix_payment_method_group);
        fixPaymentGroup.setVisibility(View.GONE);

        if (licenseQuery) {
            view.findViewById(R.id.free_features_layout).setVisibility(View.GONE);
            subscribeButton.setVisibility(View.GONE);
        }

        ////////////
        // API key status
        /////////////
        switch (apiKeyStatus) {
            case UNKNOWN:
                if (licenseQuery) {
                    apiKeyStatusTextView.setText(R.string.text_unknown_license);
                    apiKeyExpirationTextView.setVisibility(View.GONE);
                } else {
                    apiKeyStatusTextView.setText(R.string.text_no_subscription);
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    apiKeyExpirationTextView.setText(R.string.text_free_features_available);
                    if (showInAppPurchase) {
                        subscribeButton.setVisibility(View.VISIBLE);
                    } else {
                        subscribeButton.setVisibility(View.GONE);
                    }
                }
                break;
            case VALID:
                if (licenseQuery) {
                    apiKeyStatusTextView.setText(R.string.text_valid_license);
                } else {
                    apiKeyStatusTextView.setText(R.string.text_subscription_active);
                    subscribeButton.setVisibility(View.GONE);
                }
                if (apiKeyExpirationTimestamp == null) {
                    apiKeyExpirationTextView.setVisibility(View.GONE);
                } else {
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    apiKeyExpirationTextView.setText(getString(R.string.text_will_expire, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                }
                break;
            case LICENSES_EXHAUSTED:
                if (licenseQuery) {
                    apiKeyStatusTextView.setText(R.string.text_unable_to_activate_license);
                } else {
                    apiKeyStatusTextView.setText(R.string.text_invalid_subscription);
                    if (showInAppPurchase) {
                        subscribeButton.setVisibility(View.VISIBLE);
                    } else {
                        subscribeButton.setVisibility(View.GONE);
                    }
                }
                apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                apiKeyExpirationTextView.setText(R.string.text_subscription_used_by_another_user);
                break;
            case EXPIRED:
                if (licenseQuery) {
                    apiKeyStatusTextView.setText(R.string.text_expired_license);
                } else {
                    apiKeyStatusTextView.setText(R.string.text_subscription_expired);
                    if (showInAppPurchase) {
                        subscribeButton.setVisibility(View.VISIBLE);
                    } else {
                        subscribeButton.setVisibility(View.GONE);
                    }
                }
                if (apiKeyExpirationTimestamp == null) {
                    apiKeyExpirationTextView.setVisibility(View.GONE);
                } else {
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    apiKeyExpirationTextView.setText(getString(R.string.text_expired_since, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                }
                break;
            case OPEN_BETA_KEY:
                apiKeyStatusTextView.setText(R.string.text_beta_feature_tryout);
                if (apiKeyExpirationTimestamp == null) {
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    apiKeyExpirationTextView.setText(R.string.text_premium_features_available);
                } else {
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    apiKeyExpirationTextView.setText(getString(R.string.text_premium_features_available_for_free_until, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                }
                if (!licenseQuery) {
                    if (showInAppPurchase) {
                        subscribeButton.setVisibility(View.VISIBLE);
                    } else {
                        subscribeButton.setVisibility(View.GONE);
                    }
                }
                break;
            case FREE_TRIAL_KEY:
                apiKeyStatusTextView.setText(R.string.text_premium_features_free_trial);
                if (apiKeyExpirationTimestamp == null) {
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    apiKeyExpirationTextView.setText(R.string.text_premium_features_available);
                } else {
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    apiKeyExpirationTextView.setText(getString(R.string.text_premium_features_available_until, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                }
                break;
            case AWAITING_PAYMENT_GRACE_PERIOD:
            case AWAITING_PAYMENT_ON_HOLD: { // this case should never occur in a license query
                apiKeyStatusTextView.setText(R.string.text_awaiting_subscription_payment);
                if (apiKeyStatus == EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD) {
                    apiKeyExpirationTextView.setText(getString(R.string.text_subscription_on_hold_since, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                } else {
                    apiKeyExpirationTextView.setText(getString(R.string.text_subscription_in_grace_period_until, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                }
                subscribeButton.setVisibility(View.GONE);
                fixPaymentGroup.setVisibility(View.VISIBLE);
                fixPaymentGroup
                        .findViewById(R.id.fix_payment_method_button)
                        .setOnClickListener(v -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=premium_2020_monthly&package=io.olvid.messenger")));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                break;
            }
            case FREE_TRIAL_KEY_EXPIRED: {
                if (licenseQuery) {
                    apiKeyStatusTextView.setText(R.string.text_free_trial_expired);
                } else {
                    apiKeyStatusTextView.setText(R.string.text_free_trial_ended);
                    if (showInAppPurchase) {
                        subscribeButton.setVisibility(View.VISIBLE);
                    } else {
                        subscribeButton.setVisibility(View.GONE);
                    }
                }
                if (apiKeyExpirationTimestamp == null) {
                    apiKeyExpirationTextView.setVisibility(View.GONE);
                } else {
                    apiKeyExpirationTextView.setVisibility(View.VISIBLE);
                    if (licenseQuery) {
                        apiKeyExpirationTextView.setText(getString(R.string.text_expired_since, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                    } else {
                        apiKeyExpirationTextView.setText(getString(R.string.text_ended_on, DateUtils.formatDateTime(getContext(), apiKeyExpirationTimestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));
                    }
                }
                break;
            }
        }

        if (apiKeyPermissions.contains(EngineAPI.ApiKeyPermission.CALL)) {
            premiumFeaturesTitleTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_activated_green, 0);
            permissionCallTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_phone_failed_out, 0, 0, 0);
            permissionCallTextView.setTextColor(ContextCompat.getColor(view.getContext(), R.color.almostBlack));
            permissionCallTextView.setText(R.string.text_feature_initiate_secure_calls);
        } else if (anotherIdentityHasCallsPermission) {
            premiumFeaturesTitleTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_activated_green, 0);
            permissionCallTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_phone_failed_out, 0, 0, 0);
            permissionCallTextView.setTextColor(ContextCompat.getColor(view.getContext(), R.color.almostBlack));
            permissionCallTextView.setText(R.string.text_feature_initiate_secure_calls_from_another_profile);
        } else {
            premiumFeaturesTitleTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_deactivated_grey, 0);
            permissionCallTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_phone_outgoing_grey, 0, 0, 0);
            permissionCallTextView.setTextColor(ContextCompat.getColor(view.getContext(), R.color.grey));
            permissionCallTextView.setText(R.string.text_feature_initiate_secure_calls);
        }

        if (viewModel.showSubscriptionPlans) {
            showSubscriptionPlans();
        }
    }

    private void subscribeButtonClicked(View view) {
        viewModel.showSubscriptionPlans = true;
        showSubscriptionPlans();
    }

    boolean fragmentLoaded = false;
    private void showSubscriptionPlans() {
        if (!fragmentLoaded) {
            fragmentLoaded = true;

            subscribeButton.setVisibility(View.GONE);
            SubscriptionPurchaseFragment subscriptionPurchaseFragment = SubscriptionPurchaseFragment.newInstance();

            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.replace(R.id.subscription_purchase_placeholder, subscriptionPurchaseFragment);
            transaction.commit();
            subscriptionPurchasePlaceholder.setVisibility(View.VISIBLE);
        }
    }
}
