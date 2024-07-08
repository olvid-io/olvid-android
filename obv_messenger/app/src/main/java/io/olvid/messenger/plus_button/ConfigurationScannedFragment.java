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

package io.olvid.messenger.plus_button;

import android.animation.ObjectAnimator;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import net.openid.appauth.AuthState;

import org.jose4j.jwk.JsonWebKeySet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;

import io.olvid.engine.datatypes.ObvBase64;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.customClasses.ConfigurationKeycloakPojo;
import io.olvid.messenger.customClasses.ConfigurationPojo;
import io.olvid.messenger.customClasses.ConfigurationSettingsPojo;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.SubscriptionStatusFragment;
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment;
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.openid.KeycloakTasks;
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff;
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff;

public class ConfigurationScannedFragment extends Fragment implements View.OnClickListener, EngineNotificationListener {
    AppCompatActivity activity;
    PlusButtonViewModel viewModel;

    ConfigurationPojo configurationPojo = null;
    UUID configurationApiKeyUuid = null;
    boolean statusQueried = false;
    boolean callbackCalled = false;

    TextView titleTextView;

    CardView invalidCardView;
    LinearLayout invalidMalformedLayout;
    TextView invalidMalformedExplanationTextView;
    LinearLayout invalidBadServerLayout;
    TextView licenseServerUrlTextView;
    TextView ownServerUrlTextView;
    Button okButton;

    CardView newCardView;
    Button cancelButton;
    Button activateButton;
    ProgressBar activationSpinner;

    CardView currentCardView;

    FrameLayout newLicenseStatusPlaceholder;

    CardView settingsCardView;
    TextView settingsDetailsTextView;
    Button settingsUpdateButton;
    Button settingsCancelButton;

    CardView keycloakCardView;
    TextView keycloakExplanationTextView;
    TextView keycloakDetailsTextView;
    Button keycloakAuthenticateButton;
    ImageButton keycloakAuthenticationBrowserButton;
    Button keycloakCancelButton;
    KeycloakAuthenticationStartFragment keycloakAuthenticationStartFragment;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
        viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_FAILED, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_plus_button_configuration_scanned, container, false);

        if (!viewModel.isDeepLinked()) {
            requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    viewModel.setKeycloakData(null, null, null, null, null);
                    Navigation.findNavController(rootView).popBackStack();
                }
            });
        }

        return rootView;
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
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_dark)).start();
            }
        }


        view.findViewById(R.id.back_button).setOnClickListener(this);

        titleTextView = view.findViewById(R.id.title_text_view);

        invalidCardView = view.findViewById(R.id.invalid_license_card_view);
        invalidMalformedLayout = view.findViewById(R.id.bad_configuration_linear_layout);
        invalidMalformedExplanationTextView = view.findViewById(R.id.bad_configuration_explanation_text_view);
        invalidBadServerLayout = view.findViewById(R.id.bad_server_linear_layout);
        licenseServerUrlTextView = view.findViewById(R.id.license_server_url_text_view);
        ownServerUrlTextView = view.findViewById(R.id.owned_identity_server_url_text_view);
        okButton = view.findViewById(R.id.ok_button);

        newCardView = view.findViewById(R.id.new_license_card_view);
        cancelButton = view.findViewById(R.id.cancel_button);
        activateButton = view.findViewById(R.id.activate_button);
        activationSpinner = view.findViewById(R.id.activation_spinner);

        currentCardView = view.findViewById(R.id.current_license_card_view);
        newLicenseStatusPlaceholder = view.findViewById(R.id.new_license_status_placeholder);

        settingsCardView = view.findViewById(R.id.settings_update_card_view);
        settingsDetailsTextView = view.findViewById(R.id.settings_update_details_text_view);
        settingsUpdateButton = view.findViewById(R.id.settings_update_button);
        settingsCancelButton = view.findViewById(R.id.settings_cancel_button);

        keycloakCardView = view.findViewById(R.id.keycloak_update_card_view);
        keycloakExplanationTextView = view.findViewById(R.id.keycloak_explanation_text_view);
        keycloakDetailsTextView = view.findViewById(R.id.keycloak_update_details_text_view);
        keycloakAuthenticateButton = view.findViewById(R.id.keycloak_authenticate_button);
        keycloakAuthenticationBrowserButton = view.findViewById(R.id.button_authentication_browser);
        keycloakCancelButton = view.findViewById(R.id.keycloak_cancel_button);
        keycloakAuthenticationStartFragment = new KeycloakAuthenticationStartFragment();

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.authentication_fragment_placeholder, keycloakAuthenticationStartFragment);
        transaction.commit();



        okButton.setOnClickListener(this);
        cancelButton.setOnClickListener(this);
        activateButton.setOnClickListener(this);
        settingsUpdateButton.setOnClickListener(this);
        settingsCancelButton.setOnClickListener(this);
        keycloakAuthenticateButton.setOnClickListener(this);
        keycloakAuthenticationBrowserButton.setOnClickListener(this);
        keycloakCancelButton.setOnClickListener(this);

        String uri = viewModel.getScannedUri();
        if (uri == null) {
            activity.finish();
            return;
        }

        ConfigurationPojo configurationPojo = null;
        Matcher matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri);
        if (matcher.find()) {
            try {
                configurationPojo = AppSingleton.getJsonObjectMapper().readValue(ObvBase64.decode(matcher.group(2)), ConfigurationPojo.class);
            } catch (Exception e) {
                // nothing to do
                e.printStackTrace();
            }
        }
        if (configurationPojo == null) {
            activity.finish();
            return;
        }

        this.configurationPojo = configurationPojo;
        if (configurationPojo.server != null && configurationPojo.apikey != null) {
            displayLicense(viewModel.getCurrentIdentity(), this.configurationPojo);
        } else if (configurationPojo.settings != null) {
            displaySettings(this.configurationPojo.settings);
        } else if (configurationPojo.keycloak != null) {
            displayKeycloak(this.configurationPojo.keycloak);
        } else {
            activity.finish();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_FAILED, this);
    }

    private void displayLicense(OwnedIdentity ownedIdentity, @NonNull ConfigurationPojo configurationPojo) {
        // set correct title
        titleTextView.setText(R.string.activity_title_license_activation);

        if (ownedIdentity == null) {
            activity.finish();
            return;
        }

        if (ownedIdentity.keycloakManaged) {
            invalidCardView.setVisibility(View.VISIBLE);
            invalidMalformedLayout.setVisibility(View.VISIBLE);
            invalidMalformedExplanationTextView.setText(R.string.text_explanation_keycloak_license_activation_impossible);
            return;
        }

        if (!ownedIdentity.active) {
            invalidCardView.setVisibility(View.VISIBLE);
            invalidMalformedLayout.setVisibility(View.VISIBLE);
            invalidMalformedExplanationTextView.setText(R.string.text_explanation_inactive_identity_activation_link);
            return;
        }

        if (configurationPojo.server == null || configurationPojo.apikey == null) {
            invalidCardView.setVisibility(View.VISIBLE);
            invalidMalformedLayout.setVisibility(View.VISIBLE);
            invalidMalformedExplanationTextView.setText(R.string.text_explanation_malformed_activation_link);
            return;
        }

        try {
            configurationApiKeyUuid = UUID.fromString(configurationPojo.apikey);
        } catch (Exception e) {
            invalidCardView.setVisibility(View.VISIBLE);
            invalidMalformedLayout.setVisibility(View.VISIBLE);
            invalidMalformedExplanationTextView.setText(R.string.text_explanation_malformed_activation_link);
            return;
        }

        String ownServer = AppSingleton.getEngine().getServerOfIdentity(ownedIdentity.bytesOwnedIdentity);
        if (ownServer== null) {
            activity.finish();
            return;
        }

        if (!configurationPojo.server.equals(ownServer)) {
            invalidCardView.setVisibility(View.VISIBLE);
            invalidBadServerLayout.setVisibility(View.VISIBLE);
            licenseServerUrlTextView.setText(configurationPojo.server);
            ownServerUrlTextView.setText(ownServer);
            return;
        }

        // show current status
        currentCardView.setVisibility(View.VISIBLE);
        SubscriptionStatusFragment currentSubscriptionStatusFragment = SubscriptionStatusFragment.newInstance(ownedIdentity.bytesOwnedIdentity, ownedIdentity.getApiKeyStatus(), ownedIdentity.apiKeyExpirationTimestamp, ownedIdentity.getApiKeyPermissions(), false, false, AppSingleton.getOtherProfileHasCallsPermission());
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.current_license_status_placeholder, currentSubscriptionStatusFragment);
        transaction.commit();

        // wait for new status from engine
        newCardView.setVisibility(View.VISIBLE);
        activateButton.setEnabled(false);

        // query new engine for status
        statusQueried = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (callbackCalled) {
                return;
            }
            statusQueryFailed();
        }, 5000);
        AppSingleton.getEngine().queryApiKeyStatus(ownedIdentity.bytesOwnedIdentity, configurationApiKeyUuid);
    }

    private void displaySettings(ConfigurationSettingsPojo settingsPojo) {
        // set correct title
        titleTextView.setText(R.string.activity_title_settings_update);

        settingsCardView.setVisibility(View.VISIBLE);
        try {
            settingsDetailsTextView.setText(settingsPojo.prettyPrint(activity));
        } catch (Exception e) {
            e.printStackTrace();
            App.toast(R.string.toast_message_error_parsing_settings_update_link, Toast.LENGTH_SHORT);
            activity.onBackPressed();
        }
    }

    private void updateSettings() {
        if (configurationPojo.settings != null) {
            configurationPojo.settings.toBackupPojo().restore();
        }
    }

    private void displayKeycloak(ConfigurationKeycloakPojo keycloakPojo) {
        // set correct title
        titleTextView.setText(R.string.activity_title_identity_provider);

        App.runThread(() -> {
            final boolean alreadyManaged;
            final boolean sameServer;

            // check if current identity is already managed by keycloak (and if yes, by the same server)
            OwnedIdentity currentIdentity = viewModel.getCurrentIdentity();
            if (currentIdentity == null) {
                activity.finish();
                return;
            }
            viewModel.setCurrentIdentityServer(AppSingleton.getEngine().getServerOfIdentity(currentIdentity.bytesOwnedIdentity));

            if (currentIdentity.keycloakManaged) {
                alreadyManaged = true;
                ObvKeycloakState keycloakState = null;
                try {
                    keycloakState = AppSingleton.getEngine().getOwnedIdentityKeycloakState(currentIdentity.bytesOwnedIdentity);
                } catch (Exception e) {
                    // nothing
                }
                // we have the same server
                sameServer = keycloakState != null && keycloakState.keycloakServer.startsWith(keycloakPojo.getServer());
            } else {
                sameServer = false;
                alreadyManaged = false;
            }

            activity.runOnUiThread(() -> {
                // set message text
                keycloakCardView.setVisibility(View.VISIBLE);
                keycloakAuthenticateButton.setEnabled(false);
                keycloakAuthenticationBrowserButton.setEnabled(false);
                if (alreadyManaged) {
                    if (sameServer) {
                        keycloakExplanationTextView.setText(R.string.explanation_keycloak_update_same_server);
                    } else {
                        keycloakExplanationTextView.setText(R.string.explanation_keycloak_update_change_server);
                        discoverKeycloak(keycloakPojo);
                    }
                } else {
                    keycloakExplanationTextView.setText(R.string.explanation_keycloak_update_new);
                    discoverKeycloak(keycloakPojo);
                }
                keycloakDetailsTextView.setText(getString(R.string.text_option_identity_provider, keycloakPojo.getServer()));
            });
        });
    }

    private void discoverKeycloak(ConfigurationKeycloakPojo keycloakPojo) {
        KeycloakTasks.discoverKeycloakServer(keycloakPojo.getServer(), new KeycloakTasks.DiscoverKeycloakServerCallback() {
            @Override
            public void success(@NonNull String serverUrl, @NonNull AuthState authState, @NonNull JsonWebKeySet jwks) {
                activity.runOnUiThread(() -> {
                    keycloakAuthenticateButton.setEnabled(true);
                    keycloakAuthenticationBrowserButton.setEnabled(true);
                });
                viewModel.setKeycloakData(serverUrl, authState.jsonSerializeString(), jwks, keycloakPojo.getClientId(), keycloakPojo.getClientSecret());
            }

            @Override
            public void failed() {
                activity.runOnUiThread(() -> keycloakExplanationTextView.setText(R.string.explanation_keycloak_unable_to_contact_server));
            }
        });
    }

    private void authenticateKeycloak() {
        if (viewModel.getKeycloakSerializedAuthState() == null || viewModel.getKeycloakJwks() == null || viewModel.getKeycloakServerUrl() == null) {
            return;
        }
        keycloakAuthenticationStartFragment.authenticate(viewModel.getKeycloakSerializedAuthState(), viewModel.getKeycloakClientId(), viewModel.getKeycloakClientSecret(), new KeycloakTasks.AuthenticateCallback() {
            @Override
            public void success(@NonNull AuthState authState) {
                viewModel.setKeycloakSerializedAuthState(authState.jsonSerializeString());
                if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                    keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.VISIBLE);
                }
                if (keycloakAuthenticationStartFragment.authenticationSpinnerText != null) {
                    keycloakAuthenticationStartFragment.authenticationSpinnerText.setText(R.string.label_retrieving_user_details);
                }

                KeycloakTasks.getOwnDetails(activity, viewModel.getKeycloakServerUrl(), authState, viewModel.getKeycloakClientSecret(), viewModel.getKeycloakJwks(), null, new KeycloakManager.KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>>() {
                    @Override
                    public void success(Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff> result) {
                        KeycloakUserDetailsAndStuff keycloakUserDetailsAndStuff = result.first;
                        boolean revocationAllowed = result.second != null && result.second.revocationAllowed;
                        Integer minimumBuildVersion = result.second != null && result.second.minimumBuildVersions != null ? result.second.minimumBuildVersions.get("android") : null;

                        if (keycloakUserDetailsAndStuff == null || !Objects.equals(keycloakUserDetailsAndStuff.server, viewModel.getCurrentIdentityServer())) {
                            activity.runOnUiThread(() -> {
                                if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                    keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE);
                                }
                                keycloakAuthenticateButton.setEnabled(false);
                                keycloakAuthenticationBrowserButton.setEnabled(false);
                                keycloakExplanationTextView.setText(R.string.explanation_keycloak_update_bad_server);
                            });
                            return;
                        } else if (minimumBuildVersion != null && minimumBuildVersion > BuildConfig.VERSION_CODE) {
                            activity.runOnUiThread(() -> {
                                if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                    keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE);
                                }
                                keycloakAuthenticateButton.setEnabled(false);
                                keycloakAuthenticationBrowserButton.setEnabled(false);
                                keycloakExplanationTextView.setText(R.string.explanation_keycloak_olvid_version_outdated);
                            });
                            return;
                        }
                        viewModel.setKeycloakUserDetails(keycloakUserDetailsAndStuff);
                        viewModel.setKeycloakRevocationAllowed(revocationAllowed);

                        activity.runOnUiThread(() -> {
                            if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE);
                            }
                            Navigation.findNavController(keycloakAuthenticateButton).navigate(R.id.action_keycloak_bind);
                        });
                    }

                    @Override
                    public void failed(int rfc) {
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                            activity.runOnUiThread(() -> keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE));
                        }
                        App.toast(R.string.toast_message_unable_to_retrieve_details, Toast.LENGTH_SHORT, Gravity.CENTER);
                    }
                });
            }

            @Override
            public void failed(int rfc) {
                // do nothing, the user may try to authenticate again
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (viewModel.getCurrentIdentity() == null) {
            return;
        }
        int id = v.getId();
        if (id == R.id.ok_button || id == R.id.cancel_button || id == R.id.settings_cancel_button || id == R.id.back_button || id == R.id.keycloak_cancel_button) {
            activity.onBackPressed();
        } else if (id == R.id.activate_button) {
            activateButton.setEnabled(false);
            activationSpinner.setVisibility(View.VISIBLE);
            App.runThread(() -> {
                switch (AppSingleton.getEngine().registerOwnedIdentityApiKeyOnServer(viewModel.getCurrentIdentity().bytesOwnedIdentity, configurationApiKeyUuid)) {
                    case SUCCESS:
                        activity.finish();
                        break;
                    case INVALID_KEY:
                        new Handler(Looper.getMainLooper()).post(() -> {
                            activationSpinner.setVisibility(View.GONE);
                            App.toast(R.string.toast_message_license_rejected_by_server, Toast.LENGTH_LONG);
                        });
                        break;
                    case FAILED:
                    case WAIT_FOR_SERVER_SESSION:
                        new Handler(Looper.getMainLooper()).post(() -> {
                            activateButton.setEnabled(true);
                            activationSpinner.setVisibility(View.GONE);
                            App.toast(R.string.toast_message_error_retry, Toast.LENGTH_LONG);
                        });
                        break;
                }
            });
        } else if (id == R.id.settings_update_button) {
            updateSettings();
            activity.finish();
        } else if (id == R.id.keycloak_authenticate_button) {
            authenticateKeycloak();
        } else if (id == R.id.button_authentication_browser) {
            KeycloakBrowserChooserDialog.openBrowserChoiceDialog(v);
        }
    }

    // to be called on main thread
    private void statusQueryFailed() {
        View spinner = newLicenseStatusPlaceholder.findViewById(R.id.query_license_status_spinner);
        if (spinner != null) {
            newLicenseStatusPlaceholder.removeView(spinner);
        }
        TextView queryLicenseTextView = newLicenseStatusPlaceholder.findViewById(R.id.query_license_status_text_view);
        queryLicenseTextView.setText(R.string.label_unable_to_check_license_status);
        queryLicenseTextView.setTextColor(ContextCompat.getColor(activity, R.color.red));

        activateButton.setEnabled(true);
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS: {
                if (!statusQueried) {
                    break;
                }
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY);
                UUID apiKey = (UUID) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY);
                if (viewModel.getCurrentIdentity() == null || !Arrays.equals(viewModel.getCurrentIdentity().bytesOwnedIdentity, bytesOwnedIdentity) || !configurationApiKeyUuid.equals(apiKey)) {
                    // notification for another query... ignore it
                    break;
                } else {
                    callbackCalled = true;
                }
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

                new Handler(Looper.getMainLooper()).post(() -> {
                    newLicenseStatusPlaceholder.removeAllViews();

                    SubscriptionStatusFragment newSubscriptionStatusFragment = SubscriptionStatusFragment.newInstance(bytesOwnedIdentity, apiKeyStatus, apiKeyExpirationTimestamp, permissions, true, false, AppSingleton.getOtherProfileHasCallsPermission());
                    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
                    transaction.replace(R.id.new_license_status_placeholder, newSubscriptionStatusFragment);
                    transaction.commit();
                    activateButton.setEnabled(apiKeyStatus != EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED);
                });
                break;
            }
            case EngineNotifications.API_KEY_STATUS_QUERY_FAILED: {
                if (!statusQueried) {
                    break;
                }
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY);
                UUID apiKey = (UUID) userInfo.get(EngineNotifications.API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY);
                if (viewModel.getCurrentIdentity() == null || !Arrays.equals(viewModel.getCurrentIdentity().bytesOwnedIdentity, bytesOwnedIdentity) || !configurationApiKeyUuid.equals(apiKey)) {
                    // notification for another query... ignore it
                    break;
                } else {
                    callbackCalled = true;
                }
                new Handler(Looper.getMainLooper()).post(this::statusQueryFailed);
                break;
            }
        }
    }

    Long engineNotificationRegistrationNumber = null;

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        engineNotificationRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber != null;
    }
}
