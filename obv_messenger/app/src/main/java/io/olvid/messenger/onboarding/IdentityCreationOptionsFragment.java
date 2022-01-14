/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.onboarding;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.fragments.SubscriptionStatusFragment;

public class IdentityCreationOptionsFragment extends Fragment implements View.OnClickListener, EngineNotificationListener {
    private OnboardingViewModel viewModel;
    private FragmentActivity activity;
    private boolean started = false;

    private TextInputEditText serverEditText;
    private TextInputEditText apiKeyEditText;

    private Button validateServerButton;
    private Button continueButton;

    private ViewGroup licenseStatusLoader;
    private View licenseStatusSpinner;
    private View licenseStatusMessage;
    private View licenseStatusError;
    private ViewGroup licenseStatusPlaceholder;

    private ImageView serverStatusImageView;
    private View serverStatusSpinner;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(OnboardingViewModel.class);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_FAILED, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_identity_creation_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                if (activity.getWindow().getStatusBarColor() == 0xff000000) {
                    ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.almostWhite)).start();
                } else {
                    activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
                }
            } else {
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_light)).start();
            }
        }

        view.findViewById(R.id.focus_hugger).requestFocus();

        continueButton = view.findViewById(R.id.button_continue);
        continueButton.setOnClickListener(this);

        view.findViewById(R.id.back_button).setOnClickListener(this);

        validateServerButton = view.findViewById(R.id.button_validate_configuration);
        validateServerButton.setOnClickListener(this);

        serverEditText = view.findViewById(R.id.server_edit_text);
        apiKeyEditText = view.findViewById(R.id.api_key_edit_text);

        serverEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (started) {
                    viewModel.setServer(s == null ? null : s.toString());
                }
            }
        });
        apiKeyEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (started) {
                    viewModel.setApiKey(s == null ? null : s.toString());
                }
            }
        });

        licenseStatusLoader = view.findViewById(R.id.license_status_loader);
        licenseStatusSpinner = view.findViewById(R.id.query_license_status_spinner);
        licenseStatusMessage = view.findViewById(R.id.query_license_status_text_view);
        licenseStatusError = view.findViewById(R.id.query_license_status_error_view);
        licenseStatusPlaceholder = view.findViewById(R.id.license_status_placeholder);

        serverStatusImageView = view.findViewById(R.id.query_server_status);
        serverStatusSpinner = view.findViewById(R.id.query_server_spinner);

        viewModel.getValidatedStatus().observe(getViewLifecycleOwner(), validatedStatus -> {
            switch (validatedStatus.first) { // server status
                case UNCHECKED:
                    validateServerButton.setEnabled(true);
                    continueButton.setEnabled(false);
                    serverStatusSpinner.setVisibility(View.GONE);
                    serverStatusImageView.setVisibility(View.GONE);
                    break;
                case CHECKING:
                    validateServerButton.setEnabled(false);
                    continueButton.setEnabled(false);
                    serverStatusSpinner.setVisibility(View.VISIBLE);
                    serverStatusImageView.setVisibility(View.GONE);
                    break;
                case VALID:
                    validateServerButton.setEnabled(validatedStatus.second != OnboardingViewModel.VALIDATED_STATUS.VALID && (apiKeyEditText.getText() != null && apiKeyEditText.getText().toString().length() > 0));
                    continueButton.setEnabled(true);
                    serverStatusSpinner.setVisibility(View.GONE);
                    serverStatusImageView.setVisibility(View.VISIBLE);
                    serverStatusImageView.setImageResource(R.drawable.ic_ok_green);
                    break;
                case INVALID:
                    validateServerButton.setEnabled(true);
                    continueButton.setEnabled(false);
                    serverStatusSpinner.setVisibility(View.GONE);
                    serverStatusImageView.setVisibility(View.VISIBLE);
                    serverStatusImageView.setImageResource(R.drawable.ic_remove);
                    break;
            }
            switch (validatedStatus.second) {
                case UNCHECKED:
                    licenseStatusPlaceholder.setVisibility(View.GONE);
                    licenseStatusLoader.setVisibility(View.GONE);
                    break;
                case CHECKING:
                    licenseStatusPlaceholder.setVisibility(View.GONE);
                    licenseStatusLoader.setVisibility(View.VISIBLE);
                    licenseStatusSpinner.setVisibility(View.VISIBLE);
                    licenseStatusMessage.setVisibility(View.VISIBLE);
                    licenseStatusError.setVisibility(View.GONE);
                    break;
                case VALID:
                    licenseStatusPlaceholder.setVisibility(View.VISIBLE);
                    licenseStatusLoader.setVisibility(View.GONE);
                    break;
                case INVALID:
                    licenseStatusPlaceholder.setVisibility(View.GONE);
                    licenseStatusLoader.setVisibility(View.VISIBLE);
                    licenseStatusSpinner.setVisibility(View.GONE);
                    licenseStatusMessage.setVisibility(View.GONE);
                    licenseStatusError.setVisibility(View.VISIBLE);
                    break;
            }
        });
        if (viewModel.isDeepLinked()) {
            viewModel.setDeepLinked(false);
            viewModel.checkServerAndApiKey();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (viewModel != null) {
            serverEditText.setText(viewModel.getUnvalidatedServer());
            apiKeyEditText.setText(viewModel.getUnformattedApiKey());
        }
        started = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        started = false;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_FAILED, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, this);
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.back_button) {
            activity.onBackPressed();
        } else if (v.getId() == R.id.button_continue) {
            Navigation.findNavController(v).navigate(IdentityCreationOptionsFragmentDirections.actionIdentityCreation());
        } else if (v.getId() == R.id.button_validate_configuration) {
            // first hide the keyboard
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            viewModel.checkServerAndApiKey();
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY);
                UUID apiKey = (UUID) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY);

                EngineAPI.ApiKeyStatus apiKeyStatus = (EngineAPI.ApiKeyStatus) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY);
                @SuppressWarnings("unchecked")
                List<EngineAPI.ApiKeyPermission> permissions = (List<EngineAPI.ApiKeyPermission>) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY);
                final Long apiKeyExpirationTimestamp;
                if (userInfo.containsKey(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY)) {
                    //noinspection ConstantConditions
                    apiKeyExpirationTimestamp = (long) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY);
                } else {
                    apiKeyExpirationTimestamp = null;
                }
                if (apiKey != null) {
                    viewModel.apiKeyValidationFinished(apiKey, true);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        licenseStatusLoader.setVisibility(View.GONE);
                        licenseStatusPlaceholder.setVisibility(View.VISIBLE);

                        SubscriptionStatusFragment newSubscriptionStatusFragment = SubscriptionStatusFragment.newInstance(bytesOwnedIdentity, apiKeyStatus, apiKeyExpirationTimestamp, permissions, true, false);
                        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
                        transaction.replace(R.id.license_status_placeholder, newSubscriptionStatusFragment);
                        transaction.commit();
                    });
                }
                break;
            }
            case EngineNotifications.API_KEY_STATUS_QUERY_FAILED: {
                UUID apiKey = (UUID) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY);
                if (apiKey != null) {
                    viewModel.apiKeyValidationFinished(apiKey, false);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        licenseStatusSpinner.setVisibility(View.GONE);
                        licenseStatusMessage.setVisibility(View.GONE);
                        licenseStatusError.setVisibility(View.VISIBLE);
                    });
                }
                break;
            }
            case EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED: {
                String server = (String) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY);
                if (server != null) {
                    viewModel.serverValidationFinished(server, false);
                }
                break;
            }
            case EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS: {
                String server = (String) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY);
                if (server != null) {
                    viewModel.serverValidationFinished(server, true);
                }
                break;
            }
        }
    }

    private long engineNotificationRegistrationNumber = -1;
    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        this.engineNotificationRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber != -1;
    }
}
