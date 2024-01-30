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

package io.olvid.messenger.onboarding;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputEditText;

import net.openid.appauth.AuthState;

import org.jose4j.jwk.JsonWebKeySet;

import java.util.HashMap;

import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment;
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.openid.KeycloakTasks;
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff;
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff;

public class KeycloakSelectionFragment extends Fragment implements View.OnClickListener, EngineNotificationListener {
    private OnboardingViewModel viewModel;
    private FragmentActivity activity;
    private boolean started = false;

    private TextInputEditText keycloakServerEditText;
    private TextInputEditText keycloakClientIdEditText;
    private TextInputEditText keycloakClientSecretEditText;

    private Button validateButton;
    private Button authenticateButton;
    private ImageButton authenticationBrowserButton;

    private ImageView serverStatusImageView;
    private View serverStatusSpinner;
    private View focusHugger;

    private ViewGroup manualViewGroup;
    private ViewGroup autoViewGroup;
    private ImageView autoLogoImageView;
    private TextView autoExplanationTextView;
    private Button restoreBackupButton;

    private KeycloakAuthenticationStartFragment keycloakAuthenticationStartFragment;

    private static final int VERSION_OUTDATED = 1343657;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(OnboardingViewModel.class);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_onboarding_keycloak_selection, container, false);
        activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                viewModel.setKeycloakServer(null);
                viewModel.setDeepLinked(false);
                remove();
                activity.getOnBackPressedDispatcher().onBackPressed();
            }
        });
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
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_light)).start();
            }
        }

        focusHugger = view.findViewById(R.id.focus_hugger);


        keycloakAuthenticationStartFragment = new KeycloakAuthenticationStartFragment();
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.authentication_fragment_placeholder, keycloakAuthenticationStartFragment);
        transaction.commit();

        ImageView backButton = view.findViewById(R.id.back_button);
        backButton.setOnClickListener(this);

        manualViewGroup = view.findViewById(R.id.keycloak_manual_configuration);
        autoViewGroup = view.findViewById(R.id.keycloak_auto_configuration);
        autoLogoImageView = view.findViewById(R.id.keycloak_auto_successful_image_view);
        autoExplanationTextView = view.findViewById(R.id.keycloak_explanation_text_view);

        view.findViewById(R.id.keycloak_manual_configuration_switch).setOnClickListener(v -> autoViewGroup.animate().alpha(0).setDuration(200).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                autoViewGroup.setVisibility(View.GONE);
                manualViewGroup.setVisibility(View.VISIBLE);
                manualViewGroup.setAlpha(0);
                manualViewGroup.animate().alpha(1).setDuration(200);
            }
        }));

        validateButton = view.findViewById(R.id.button_validate_configuration);
        validateButton.setOnClickListener(this);

        authenticateButton = view.findViewById(R.id.button_authenticate);
        authenticateButton.setOnClickListener(this);

        authenticationBrowserButton = view.findViewById(R.id.button_authentication_browser);
        authenticationBrowserButton.setOnClickListener(this);

        keycloakServerEditText = view.findViewById(R.id.keycloak_server_edit_text);
        keycloakClientIdEditText = view.findViewById(R.id.keycloak_client_id_edit_text);
        keycloakClientSecretEditText = view.findViewById(R.id.keycloak_client_secret_edit_text);

        keycloakServerEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (started) {
                    viewModel.setKeycloakServer(s == null ? null : s.toString());
                }
            }
        });

        keycloakClientIdEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (started) {
                    viewModel.setKeycloakClientId(s == null ? null : s.toString());
                }
                authenticateButton.setEnabled(s != null && s.toString().length() != 0);
            }
        });

        keycloakClientSecretEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (started) {
                    viewModel.setKeycloakClientSecret(s == null ? null : s.toString());
                }
            }
        });

        view.findViewById(R.id.show_password_button).setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    int pos = keycloakClientSecretEditText.getSelectionStart();
                    keycloakClientSecretEditText.setTransformationMethod(null);
                    keycloakClientSecretEditText.setSelection(pos);
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    int pos = keycloakClientSecretEditText.getSelectionStart();
                    keycloakClientSecretEditText.setTransformationMethod(new PasswordTransformationMethod());
                    keycloakClientSecretEditText.setSelection(pos);
                    v.performClick();
                    break;
                }
            }
            return true;
        });

        serverStatusImageView = view.findViewById(R.id.query_server_status);
        serverStatusSpinner = view.findViewById(R.id.query_server_spinner);

        viewModel.getKeycloakValidatedStatus().observe(getViewLifecycleOwner(), validatedStatus -> {
            switch (validatedStatus) {
                case UNCHECKED:
                    validateButton.setVisibility(View.VISIBLE);
                    validateButton.setEnabled(true);
                    authenticateButton.setVisibility(View.GONE);
                    authenticationBrowserButton.setVisibility(View.GONE);
                    serverStatusSpinner.setVisibility(View.GONE);
                    serverStatusImageView.setVisibility(View.GONE);
                    break;
                case CHECKING:
                    validateButton.setVisibility(View.VISIBLE);
                    validateButton.setEnabled(false);
                    authenticateButton.setVisibility(View.GONE);
                    authenticationBrowserButton.setVisibility(View.GONE);
                    serverStatusSpinner.setVisibility(View.VISIBLE);
                    serverStatusImageView.setVisibility(View.GONE);
                    break;
                case VALID:
                    validateButton.setVisibility(View.GONE);
                    authenticateButton.setVisibility(View.VISIBLE);
                    authenticationBrowserButton.setVisibility(View.VISIBLE);
                    serverStatusSpinner.setVisibility(View.GONE);
                    serverStatusImageView.setVisibility(View.VISIBLE);
                    serverStatusImageView.setImageResource(R.drawable.ic_ok_green);
                    break;
                case INVALID:
                    validateButton.setVisibility(View.VISIBLE);
                    validateButton.setEnabled(true);
                    authenticateButton.setVisibility(View.GONE);
                    authenticationBrowserButton.setVisibility(View.GONE);
                    serverStatusSpinner.setVisibility(View.GONE);
                    serverStatusImageView.setVisibility(View.VISIBLE);
                    serverStatusImageView.setImageResource(R.drawable.ic_remove);
                    break;
            }
        });

        if (viewModel.isConfiguredFromMdm()) {
            keycloakServerEditText.setEnabled(false);
            keycloakClientIdEditText.setEnabled(false);
            keycloakClientSecretEditText.setEnabled(false);
            backButton.setImageResource(R.drawable.ic_close_blue_or_white);
        } else {
            keycloakServerEditText.setEnabled(true);
            keycloakClientIdEditText.setEnabled(true);
            keycloakClientSecretEditText.setEnabled(true);
            backButton.setImageResource(R.drawable.ic_arrow_back_blue_or_white);
        }

        if (viewModel.isDeepLinked()) {
            manualViewGroup.setVisibility(View.GONE);
            validateKeycloakServer();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (viewModel != null) {
            keycloakServerEditText.setText(viewModel.getKeycloakServer());
            keycloakClientIdEditText.setText(viewModel.getKeycloakClientId());
            keycloakClientSecretEditText.setText(viewModel.getKeycloakClientSecret());
        }
        started = true;
        if (focusHugger != null) {
            focusHugger.requestFocus();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        started = false;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, this);
    }


    private void validateKeycloakServer() {
        final String keycloakServerUrl = viewModel.getKeycloakServer();
        if (keycloakServerUrl == null) {
            return;
        }

        KeycloakTasks.discoverKeycloakServer(keycloakServerUrl, new KeycloakTasks.DiscoverKeycloakServerCallback() {
            @Override
            public void success(@NonNull String serverUrl, @NonNull AuthState authState, @NonNull JsonWebKeySet jwks) {
                activity.runOnUiThread(() -> {
                    keycloakServerEditText.setText(serverUrl);
                    keycloakServerEditText.setSelection(serverUrl.length());
                    viewModel.keycloakValidationSuccess(keycloakServerUrl, serverUrl, authState.jsonSerializeString(), jwks);
                    if (manualViewGroup.getVisibility() != View.VISIBLE) {
                        if (viewModel.isConfiguredFromMdm()) {
                            autoLogoImageView.setImageResource(R.drawable.olvid_blue_or_white);
                            autoExplanationTextView.setText(R.string.text_explanation_onboarding_keycloak_mdm);
                        } else {
                            autoLogoImageView.setImageResource(R.drawable.ic_ok_green);
                            autoExplanationTextView.setText(R.string.text_explanation_onboarding_keycloak_parsed_configuration);
                        }
                        autoViewGroup.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void failed() {
                activity.runOnUiThread(() -> {
                    viewModel.keycloakValidationFailed(keycloakServerUrl);
                    manualViewGroup.setVisibility(View.VISIBLE);
                });
            }
        });
    }


    @Override
    public void onClick(final View v) {
        if (v.getId() == R.id.back_button) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            activity.onBackPressed();
        } else if (v.getId() == R.id.button_validate_configuration) {
            if (viewModel.getKeycloakServer() == null) {
                return;
            }
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            validateKeycloakServer();
        } else if (v.getId() == R.id.button_authentication_browser) {
            KeycloakBrowserChooserDialog.openBrowserChoiceDialog(v);
        } else if (v.getId() == R.id.button_authenticate) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            String serializedAuthState = viewModel.getKeycloakSerializedAuthState();
            String clientId = viewModel.getKeycloakClientId();
            String clientSecret = viewModel.getKeycloakClientSecret();
            if (serializedAuthState != null && clientId != null) {
                keycloakAuthenticationStartFragment.authenticate(serializedAuthState, clientId, clientSecret, new KeycloakTasks.AuthenticateCallback() {
                    @Override
                    public void success(@NonNull AuthState authState) {
                        viewModel.setKeycloakSerializedAuthState(authState.jsonSerializeString());
                        String keycloakServer = viewModel.getKeycloakServer();
                        JsonWebKeySet jwks = viewModel.getKeycloakJwks();
                        if (keycloakServer == null || jwks == null) {
                            failed(KeycloakTasks.RFC_USER_NOT_AUTHENTICATED);
                            return;
                        }
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.VISIBLE);
                        }
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerText != null) {
                            keycloakAuthenticationStartFragment.authenticationSpinnerText.setText(R.string.label_retrieving_user_details);
                        }

                        KeycloakTasks.getOwnDetails(activity, keycloakServer, authState, clientSecret, jwks, null, new KeycloakManager.KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>>() {
                            @Override
                            public void success(Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff> userDetailsAndApiKeyAndRevocationAllowed) {
                                if (userDetailsAndApiKeyAndRevocationAllowed == null || userDetailsAndApiKeyAndRevocationAllowed.first == null || userDetailsAndApiKeyAndRevocationAllowed.second == null) {
                                    failed(0);
                                    return;
                                }

                                Integer minimumBuildVersion = userDetailsAndApiKeyAndRevocationAllowed.second.minimumBuildVersions != null ? userDetailsAndApiKeyAndRevocationAllowed.second.minimumBuildVersions.get("android") : null;
                                if (minimumBuildVersion != null && minimumBuildVersion > BuildConfig.VERSION_CODE) {
                                    failed(VERSION_OUTDATED);
                                    return;
                                }

                                viewModel.setKeycloakUserDetails(userDetailsAndApiKeyAndRevocationAllowed.first.userDetails);
                                viewModel.setKeycloakRevocationAllowed(userDetailsAndApiKeyAndRevocationAllowed.second.revocationAllowed);
                                viewModel.setKeycloakSignatureKey(userDetailsAndApiKeyAndRevocationAllowed.first.signatureKey);
                                viewModel.setApiKey(null);

                                activity.runOnUiThread(() -> {
                                    if (userDetailsAndApiKeyAndRevocationAllowed.first.server != null) {
                                        viewModel.setServer(userDetailsAndApiKeyAndRevocationAllowed.first.server);
                                        if (keycloakAuthenticationStartFragment.authenticationSpinnerText != null) {
                                            keycloakAuthenticationStartFragment.authenticationSpinnerText.setText(R.string.label_checking_server);
                                        }
                                        AppSingleton.getEngine().queryServerWellKnown(userDetailsAndApiKeyAndRevocationAllowed.first.server);
                                    } else {
                                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE);
                                        }
                                        Navigation.findNavController(v).navigate(KeycloakSelectionFragmentDirections.actionIdentityCreation());
                                    }
                                });
                            }

                            @Override
                            public void failed(int rfc) {
                                if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                    activity.runOnUiThread(() -> keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE));
                                }
                                if (rfc == VERSION_OUTDATED) {
                                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                            .setTitle(R.string.dialog_title_outdated_version)
                                            .setMessage(R.string.explanation_keycloak_olvid_version_outdated)
                                            .setPositiveButton(R.string.button_label_update, (dialog, which) -> {
                                                final String appPackageName = activity.getPackageName();
                                                try {
                                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                                                } catch (ActivityNotFoundException e) {
                                                    try {
                                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                                                    } catch (Exception ee) {
                                                        ee.printStackTrace();
                                                    }
                                                }
                                            })
                                            .setNegativeButton(R.string.button_label_cancel, null);
                                    activity.runOnUiThread(() -> builder.create().show());
                                } else {
                                    App.toast(R.string.toast_message_unable_to_retrieve_details, Toast.LENGTH_SHORT, Gravity.CENTER);
                                }
                            }
                        });
                    }

                    @Override
                    public void failed(int rfc) { }
                });
            }
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED: {
                String server = (String) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY);
                if (server != null) {
                    viewModel.serverValidationFinished(server, false);
                    activity.runOnUiThread(() -> {
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE);
                        }
                        App.toast(R.string.toast_message_unable_to_connect_to_server, Toast.LENGTH_SHORT, Gravity.CENTER);
                    });
                }
                break;
            }
            case EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS: {
                String server = (String) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY);
                if (server != null) {
                    viewModel.serverValidationFinished(server, true);
                    activity.runOnUiThread(() -> {
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE);
                        }
                        Navigation.findNavController(authenticateButton).navigate(KeycloakSelectionFragmentDirections.actionIdentityCreation());
                    });
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
